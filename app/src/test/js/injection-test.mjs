// Verifies the WebView injection script against the two things the collector does to it:
// it is injected twice per page load (onPageStarted + onPageFinished), and it runs on pages
// that issue a lot of unrelated JSON. Run: node app/src/test/js/injection-test.mjs
import { readFileSync } from 'node:fs';

const SRC = readFileSync(process.argv[2] ? process.argv[2] : new URL('../../main/assets/ai-monitor-android.js', import.meta.url), 'utf8');
const results = [];
function check(name, actual, expected) {
    const ok = actual === expected;
    results.push({ name, actual, expected, ok });
}

function newEnv(pageUrl = 'https://claude.ai/settings/usage', body = { five_hour: { utilization: 1 } }) {
    const counts = { clones: 0, transforms: 0 };
    const posts = [];
    const g = globalThis;
    g.window = g;
    const page = new URL(pageUrl);
    g.location = { hostname: page.hostname, pathname: page.pathname, hash: page.hash, href: page.href };
    g.document = { querySelectorAll: () => [], body: null, documentElement: {}, addEventListener() {} };
    g.MutationObserver = class { observe() {} disconnect() {} };
    g.AndroidBridge = { postData(source, json) { posts.push({ source, data: JSON.parse(json) }); } };
    g.__aiMonitorState = undefined;
    delete g.__aiMonitorState;

    g.fetch = async (url) => ({
        ok: true,
        url: String(url),
        headers: { get: () => 'application/json' },
        clone() { counts.clones++; return { json: async () => { counts.transforms++; return body; } }; },
    });
    class XHR {
        constructor() { this.listeners = []; this.status = 200; this.responseText = '{}'; }
        open(m, u) { this._url = u; }
        send() { this.fire(); }
        addEventListener(t, fn, opts) { this.listeners.push({ fn, once: !!(opts && opts.once) }); }
        getResponseHeader() { return 'application/json'; }
        fire() {
            const current = this.listeners.slice();
            this.listeners = this.listeners.filter((l) => !l.once);
            current.forEach((l) => { counts.transforms++; l.fn(); });
        }
    }
    g.XMLHttpRequest = XHR;
    return { counts, XHR, posts };
}

function inject(times) {
    for (let i = 0; i < times; i++) (0, eval)(SRC);
}

// 1 injection, one same-host JSON response
let env = newEnv();
inject(1);
await globalThis.fetch('https://claude.ai/api/organizations/x/usage');
await new Promise((r) => setTimeout(r, 0));
check('1 次注入 → clone 1 次', env.counts.clones, 1);

// 5 injections must not multiply the work
env = newEnv();
inject(5);
await globalThis.fetch('https://claude.ai/api/organizations/x/usage');
await new Promise((r) => setTimeout(r, 0));
check('5 次注入 → clone 仍是 1 次', env.counts.clones, 1);

// Unrelated host is not cloned or parsed at all
env = newEnv();
inject(2);
await globalThis.fetch('https://telemetry.example.com/v1/events');
await new Promise((r) => setTimeout(r, 0));
check('第三方 host → clone 0 次', env.counts.clones, 0);

// OpenAI's billing page loads its figures from api.openai.com, not from its own host
const OPENAI_PAGE = 'https://platform.openai.com/settings/organization/billing/overview';
env = newEnv(OPENAI_PAGE);
inject(1);
await globalThis.fetch('https://api.openai.com/v1/dashboard/billing/credit_grants');
await new Promise((r) => setTimeout(r, 0));
check('OpenAI 頁面的 api.openai.com 帳務回應 → clone 1 次', env.counts.clones, 1);

// ...while the same page's telemetry on other hosts is still skipped
env = newEnv(OPENAI_PAGE);
inject(1);
await globalThis.fetch('https://chatgpt.com/ces/v1/telemetry/intake');
await new Promise((r) => setTimeout(r, 0));
check('OpenAI 頁面的 chatgpt.com 遙測 → clone 0 次', env.counts.clones, 0);

// A reused XHR object must not accumulate load listeners
env = newEnv();
inject(1);
const xhr = new globalThis.XMLHttpRequest();
xhr.open('GET', 'https://claude.ai/api/organizations/x/usage');
xhr.send();
xhr.send();
check('重用 XHR 送兩次 → listener 觸發 2 次（非 4 次）', env.counts.transforms, 2);

// The page replacing window.fetch must still get re-wrapped by the next injection
env = newEnv();
inject(1);
const wrapped = globalThis.fetch;
globalThis.fetch = async (url) => ({ ok: true, url: String(url), headers: { get: () => 'application/json' }, clone() { env.counts.clones++; return { json: async () => ({}) }; } });
inject(1);
check('原站替換 fetch 後仍會重新包裝', globalThis.fetch === wrapped, false);

// Claude.ai reset countdowns: the remaining time is rounded up to whole hours once, then split
// into days and hours — never rounding days and hours up separately ("3 days 24 hrs").
const HOUR = 3600000;
const inFuture = (hours, minutes = 0) => new Date(Date.now() + hours * HOUR + minutes * 60000).toISOString();
env = newEnv('https://claude.ai/new#settings/usage', {
    seven_day: { utilization: 38, resets_at: inFuture(2 * 24 + 23, 48) },
    limits: [
        { kind: 'weekly_scoped', percent: 5, resets_at: inFuture(24 + 5, 10), scope: { model: { display_name: 'Fable' } } },
    ],
});
inject(1);
await globalThis.fetch('https://claude.ai/api/organizations/x/usage');
await new Promise((r) => setTimeout(r, 2100));
const claude = env.posts.filter((p) => p.source === 'claude_usage' && p.data.weekly_reset).pop()?.data ?? {};
check('每週重設：2 天 23 小時 48 分 → 3 days 0 hrs', claude.weekly_reset, '3 days 0 hrs');
check('單一模型重設：1 天 5 小時 10 分 → 1 days 6 hrs', claude.fable_reset, '1 days 6 hrs');

env = newEnv('https://claude.ai/new#settings/usage', { seven_day: { utilization: 1, resets_at: inFuture(5, 30) } });
inject(1);
await globalThis.fetch('https://claude.ai/api/organizations/x/usage');
await new Promise((r) => setTimeout(r, 2100));
const short = env.posts.filter((p) => p.source === 'claude_usage' && p.data.weekly_reset).pop()?.data ?? {};
check('每週重設：不到一天 5 小時 30 分 → 6 hrs', short.weekly_reset, '6 hrs');

let failed = 0;
for (const r of results) {
    if (!r.ok) failed++;
    console.log(`${r.ok ? 'PASS' : 'FAIL'}  ${r.name}  (實際 ${r.actual}, 預期 ${r.expected})`);
}
console.log(failed === 0 ? `\n全部 ${results.length} 項通過` : `\n${failed} 項失敗`);
process.exit(failed === 0 ? 0 : 1);
