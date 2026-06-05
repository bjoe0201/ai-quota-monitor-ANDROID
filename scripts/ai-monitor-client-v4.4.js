// ==UserScript==
// @name         AI Quota Monitor Client v4.4
// @namespace    https://github.com/ai-quota-monitor
// @version      4.4.3
// @description  v4.1 + OpenRouter 支援（API 攔截版，零 DOM 依賴）
// @author       AI Quota Monitor
// @updated      2026-06-05 — Claude.ai 新路由 /new#settings/usage 相容（v4.4.3）
// @match        https://platform.openai.com/settings/organization/billing/overview*
// @match        https://claude.ai/settings/usage*
// @match        https://claude.ai/new*
// @match        https://platform.claude.com/settings/billing*
// @match        https://github.com/settings/copilot/features*
// @match        https://github.com/settings/billing/budgets*
// @match        https://openrouter.ai/activity*
// @match        https://openrouter.ai/settings/credits*
// @run-at       document-start
// @noframes
// @grant        unsafeWindow
// @grant        GM_xmlhttpRequest
// @grant        GM_getValue
// @grant        GM_setValue
// @connect      localhost
// @connect      127.0.0.1
// ==/UserScript==

(function () {
    'use strict';

    // ─────────────────────────────────────────────
    //  PAGE IDENTIFICATION
    // ─────────────────────────────────────────────
    const PAGE_MAP = {
        'platform.openai.com': {
            key: 'openai_billing',
            label: 'OpenAI',
            expectedPath: '/settings/organization/billing',
            refreshInterval: 5 * 60 * 1000,   // 5 分鐘
        },
        'claude.ai': {
            key: 'claude_usage',
            label: 'Claude Usage',
            expectedPath: ['/settings/usage', '/new'],
            expectedHash: 'settings/usage',
            refreshInterval: 1 * 60 * 1000,
        },
        'platform.claude.com': {
            key: 'claude_billing',
            label: 'Claude Billing',
            expectedPath: '/settings/billing',
            refreshInterval: 5 * 60 * 1000,
        },
        'github.com': {
            key: 'github_copilot',
            label: 'Copilot',
            expectedPath: ['/settings/copilot/features', '/settings/billing/budgets'],
            refreshInterval: 2 * 60 * 1000,  // 2 分鐘
        },
        'openrouter.ai': {
            key: 'openrouter',
            label: 'OpenRouter',
            expectedPath: ['/activity', '/settings/credits'],   // 兩個頁面共用同一 source
            refreshInterval: 5 * 60 * 1000,
        },
    };

    const PAGE = PAGE_MAP[location.hostname];
    if (!PAGE) return;

    function isOnExpectedPage() {
        const paths = Array.isArray(PAGE.expectedPath) ? PAGE.expectedPath : [PAGE.expectedPath];
        const pathMatch = paths.some(p => location.pathname.startsWith(p));
        if (!pathMatch) return false;
        if (PAGE.expectedHash && location.pathname === '/new') {
            const hash = location.hash.replace(/^#/, '');
            return hash.startsWith(PAGE.expectedHash);
        }
        return true;
    }

    // ─────────────────────────────────────────────
    //  CONFIG
    // ─────────────────────────────────────────────
    const config = {
        server_url: GM_getValue('aimon_server', 'http://localhost:7890'),
        debug: GM_getValue('aimon_debug', false),   // 預設關閉；透過 __aimon.debug(true) 開啟
    };

    // ─────────────────────────────────────────────
    //  STATE
    // ─────────────────────────────────────────────
    const COLORS = {
        idle:      '#6c7086',
        listening: '#89b4fa',  // 藍：攔截器已安裝，等待 API
        success:   '#a6e3a1',
        error:     '#f38ba8',
    };

    const pendingData = {};    // { source: { ...fields } }
    const mergeTimers = {};    // { source: timeoutId }
    const lastData = {};       // { source: { ...fields } }  change detection
    let lastSuccessTime = 0;
    let interceptCount = 0;    // 已攔截的匹配 API 數量
    let _dot = null;
    let _domObserver = null;
    let domParseSuccess = false;
    const MERGE_WINDOW = 2000; // 2 秒合併視窗

    const LOG_PREFIX = '[AI Monitor v4.4.1]';

    // ─────────────────────────────────────────────
    //  DEBUG LOGGER
    // ─────────────────────────────────────────────
    function dbg(...args) {
        if (config.debug) console.log(LOG_PREFIX, ...args);
    }
    function dbgGroup(label) {
        if (config.debug) console.group(LOG_PREFIX + ' ' + label);
    }
    function dbgGroupEnd() {
        if (config.debug) console.groupEnd();
    }

    // ─────────────────────────────────────────────
    //  URL PATTERN RULES（每個 source 一組）
    //
    //  ⚠️ patterns 為推測值，用 debug 模式確認後微調
    //  transformer 收到 (url, json)，回傳要 merge 的欄位 dict
    // ─────────────────────────────────────────────

    // ── OpenAI ───────────────────────────────────
    function transformOpenAI(url, json) {
        const d = {};

        // /v1/dashboard/billing/credit_grants 或類似
        if (json.total_available !== undefined) d.balance_usd = parseFloat(json.total_available) || 0;
        if (json.total_granted !== undefined && json.total_used !== undefined) {
            d.credits_total_usd = parseFloat(json.total_granted) || 0;
            d.credits_used_usd  = parseFloat(json.total_used) || 0;
        }

        // /v1/dashboard/billing/subscription 或 /organization
        if (json.hard_limit_usd !== undefined) d.hard_limit_usd = parseFloat(json.hard_limit_usd) || 0;
        if (json.soft_limit_usd !== undefined) d.soft_limit_usd = parseFloat(json.soft_limit_usd) || 0;
        if (json.access_until !== undefined) d.access_until = json.access_until;
        if (json.plan) {
            if (typeof json.plan === 'object' && json.plan.title) d.tier = json.plan.title;
            else if (typeof json.plan === 'string') d.tier = json.plan;
        }
        if (json.account_name && !d.tier) d.tier = json.account_name;  // fallback only

        // /v1/dashboard/billing/usage 或 /organization/costs
        if (json.total_usage !== undefined) d.month_usage_usd = parseFloat(json.total_usage) / 100 || 0;  // cents → USD
        if (json.daily_costs && Array.isArray(json.daily_costs)) {
            let total = 0;
            for (const day of json.daily_costs) {
                if (day.line_items) {
                    for (const item of day.line_items) total += (item.cost || 0);
                }
            }
            if (total > 0) d.month_usage_usd = total / 100;
        }

        // Costs API (newer)
        if (json.object === 'page' && json.data && Array.isArray(json.data)) {
            let totalAmount = 0;
            for (const entry of json.data) {
                if (entry.amount) totalAmount += (entry.amount.value || 0);
                if (entry.results && Array.isArray(entry.results)) {
                    for (const r of entry.results) {
                        if (r.amount) totalAmount += (r.amount.value || 0);
                    }
                }
            }
            if (totalAmount > 0) d.month_usage_usd = totalAmount;
        }

        // Subscription info
        if (json.auto_recharge_enabled !== undefined) d.auto_recharge = !!json.auto_recharge_enabled;
        else if (json.has_payment_method !== undefined) d.auto_recharge = !!json.has_payment_method;

        return d;
    }

    // ── Claude.ai Usage ──────────────────────────
    function transformClaudeUsage(url, json) {
        const d = {};

        // ====== 真實 API 格式：/api/organizations/.../usage ======
        // { five_hour: { utilization: 16, resets_at: "..." },
        //   seven_day: { utilization: 40, resets_at: "..." },
        //   extra_usage: { is_enabled, monthly_limit, used_credits, utilization } }
        if (json.five_hour && typeof json.five_hour === 'object') {
            if (json.five_hour.utilization !== undefined) d.session_percent = Math.round(json.five_hour.utilization);
            if (json.five_hour.resets_at) {
                const ms = new Date(json.five_hour.resets_at) - Date.now();
                if (ms > 0) {
                    const mins = Math.ceil(ms / 60000);
                    d.session_reset = mins >= 60 ? `${Math.floor(mins / 60)} hrs ${mins % 60} mins` : `${mins} mins`;
                }
            }
        }

        if (json.seven_day && typeof json.seven_day === 'object') {
            if (json.seven_day.utilization !== undefined) d.weekly_percent = Math.round(json.seven_day.utilization);
            if (json.seven_day.resets_at) {
                const ms = new Date(json.seven_day.resets_at) - Date.now();
                if (ms > 0) {
                    const days = Math.ceil(ms / 86400000);
                    const hrs = Math.ceil((ms % 86400000) / 3600000);
                    d.weekly_reset = days > 0 ? `${days} days ${hrs} hrs` : `${hrs} hrs`;
                }
            }
        }

        // Extra usage (overage) — API 值為 cents，需 ÷100 轉 USD
        const ex = json.extra_usage || json.extra || json.overages;
        if (ex && typeof ex === 'object') {
            d.extra_enabled = !!(ex.is_enabled !== undefined ? ex.is_enabled : true);
            // 真實欄位：used_credits, monthly_limit, utilization
            if (ex.used_credits !== undefined) d.extra_spent = parseFloat(ex.used_credits) / 100;
            if (ex.monthly_limit !== undefined) d.extra_limit = parseFloat(ex.monthly_limit) / 100;
            if (ex.utilization !== undefined) d.extra_percent = Math.round(ex.utilization);
            // 相容備用欄位
            if (ex.amount_spent !== undefined && d.extra_spent === undefined) d.extra_spent = parseFloat(ex.amount_spent) || 0;
            if (ex.spent !== undefined && d.extra_spent === undefined) d.extra_spent = parseFloat(ex.spent) || 0;
            if (ex.spend_limit !== undefined && d.extra_limit === undefined) d.extra_limit = parseFloat(ex.spend_limit) || 0;
            if (ex.limit !== undefined && d.extra_limit === undefined) d.extra_limit = parseFloat(ex.limit) || 0;
            if (ex.balance !== undefined) d.extra_balance = parseFloat(ex.balance) || 0;
            if (ex.percent_used !== undefined && d.extra_percent === undefined) d.extra_percent = Math.round(ex.percent_used);
            if (ex.resets) d.extra_resets = ex.resets;
            if (ex.resets_at) {
                const dt = new Date(ex.resets_at);
                d.extra_resets = dt.toLocaleDateString('en-US', { month: 'long', day: 'numeric' });
            }
            if (ex.auto_reload !== undefined) d.auto_reload = !!ex.auto_reload;
        }

        // ====== /api/organizations/.../prepaid/credits ======
        // { amount: 840, currency: "USD", auto_reload_settings: null }
        if (json.amount !== undefined && json.currency) {
            d.extra_balance = parseFloat(json.amount) / 100;  // cents → USD
        }
        if (json.auto_reload_settings !== undefined) {
            d.auto_reload = !!json.auto_reload_settings;
        }

        // ====== /api/organizations/.../prepaid/bundles ======
        // { purchases_reset_at: "2026-04-01T00:00:00Z" }
        if (json.purchases_reset_at) {
            const dt = new Date(json.purchases_reset_at);
            d.extra_resets = dt.toLocaleDateString('en-US', { month: 'long', day: 'numeric' });
        }

        // ====== 備用：舊版/推測格式 ======
        // Session / rate limit info (flat)
        if (json.session_percent !== undefined) d.session_percent = json.session_percent;
        if (json.session && typeof json.session === 'object') {
            if (json.session.percent_used !== undefined) d.session_percent = Math.round(json.session.percent_used);
            if (json.session.reset_in) d.session_reset = json.session.reset_in;
            if (json.session.resets_at) {
                const ms = new Date(json.session.resets_at) - Date.now();
                if (ms > 0) {
                    const mins = Math.ceil(ms / 60000);
                    d.session_reset = mins >= 60 ? `${Math.floor(mins / 60)} hrs ${mins % 60} mins` : `${mins} mins`;
                }
            }
        }

        if (json.weekly_percent !== undefined && d.weekly_percent === undefined) d.weekly_percent = json.weekly_percent;
        if (json.weekly && typeof json.weekly === 'object') {
            if (json.weekly.percent_used !== undefined && d.weekly_percent === undefined) d.weekly_percent = Math.round(json.weekly.percent_used);
            if (json.weekly.reset_in && !d.weekly_reset) d.weekly_reset = json.weekly.reset_in;
            if (json.weekly.resets_at && !d.weekly_reset) {
                const ms = new Date(json.weekly.resets_at) - Date.now();
                if (ms > 0) {
                    const days = Math.ceil(ms / 86400000);
                    d.weekly_reset = `${days} days`;
                }
            }
        }

        // Message limit style
        if (json.messageLimit || json.message_limit) {
            const ml = json.messageLimit || json.message_limit;
            if (ml.remaining !== undefined && ml.limit !== undefined) {
                const used = ml.limit - ml.remaining;
                d.session_percent = Math.round(used / ml.limit * 100);
            }
        }

        // Flat structure variant
        if (json.extra_enabled !== undefined && d.extra_enabled === undefined) d.extra_enabled = json.extra_enabled;
        if (json.extra_spent !== undefined && d.extra_spent === undefined) d.extra_spent = parseFloat(json.extra_spent) || 0;
        if (json.extra_limit !== undefined && d.extra_limit === undefined) d.extra_limit = parseFloat(json.extra_limit) || 0;
        if (json.extra_balance !== undefined && d.extra_balance === undefined) d.extra_balance = parseFloat(json.extra_balance) || 0;

        // Rate limit headers style
        if (json.rate_limit) {
            const rl = json.rate_limit;
            if (rl.remaining !== undefined && rl.limit !== undefined && d.session_percent === undefined) {
                d.session_percent = Math.round((1 - rl.remaining / rl.limit) * 100);
            }
            if (rl.resets_at && !d.session_reset) {
                const ms = new Date(rl.resets_at) - Date.now();
                if (ms > 0) {
                    const mins = Math.ceil(ms / 60000);
                    d.session_reset = mins >= 60 ? `${Math.floor(mins / 60)} hrs ${mins % 60} mins` : `${mins} mins`;
                }
            }
        }

        return d;
    }

    // ── Claude API Billing ───────────────────────
    function transformClaudeBilling(url, json) {
        if (!json || typeof json !== 'object') return {};  // null guard (e.g. credit_expiry)
        const d = {};

        // ====== 真實 API：/api/organizations/.../rate_limits ======
        // { rate_limit_tier: "auto_prepaid_tier_0", tier_model_rate_limiters: [...] }
        if (json.rate_limit_tier) {
            // 美化 tier 名稱：auto_prepaid_tier_0 → Auto Prepaid Tier 0
            d.plan = json.rate_limit_tier.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
        }

        // ====== 真實 API：/api/organizations/.../prepaid/credits ======
        // { amount: 138, currency: "USD", auto_reload_settings: null, pending_invoice_amount_cents: null }
        if (json.amount !== undefined && json.currency) {
            d.balance_usd = parseFloat(json.amount) / 100;  // cents → USD
        }
        if (json.auto_reload_settings !== undefined) {
            d.auto_recharge = !!json.auto_reload_settings;
        }

        // ====== 真實 API：/api/organizations/.../current_spend 或 invoiced_balance ======
        // { amount: 0, resets_at: "2026-04-01T00:00:00Z" }
        if (json.amount !== undefined && json.resets_at && !json.currency) {
            d.this_month_usd = parseFloat(json.amount) / 100;  // cents → USD
            const dt = new Date(json.resets_at);
            d.next_billing = dt.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
        }

        // ====== 真實 API：/api/organizations/.../invoices?limit=25 ======
        // { invoices: [{ type, invoice_status, effective_at, amount, service_period, ... }] }
        if (json.invoices && Array.isArray(json.invoices) && json.invoices.length > 0) {
            // 找最新的 usage_invoice 作為本月用量
            const usageInv = json.invoices.find(inv => inv.type === 'usage_invoice');
            if (usageInv && usageInv.amount !== undefined) {
                d.this_month_usd = parseFloat(usageInv.amount) / 100;
            }
        }

        // ====== 真實 API：/api/organizations/.../invoices/overdue ======
        // { overdue_invoices: [], total_overdue_amount: 0 }
        if (json.total_overdue_amount !== undefined && json.total_overdue_amount > 0) {
            d.overdue_usd = parseFloat(json.total_overdue_amount) / 100;
        }

        // ====== 真實 API：/api/organizations/.../prepaid/auto_recharge ======
        // { status: "disabled", target_amount: 1500, threshold_amount: 500 }
        if (json.status !== undefined && json.target_amount !== undefined) {
            d.auto_recharge = json.status === 'enabled';
        }

        // ====== 備用：舊版/推測格式 ======
        if (json.plan) {
            if (typeof json.plan === 'object') {
                if (json.plan.name && !d.plan) d.plan = json.plan.name;
                if (json.plan.amount) d.monthly_usd = parseFloat(json.plan.amount) || 0;
            } else if (typeof json.plan === 'string' && !d.plan) {
                d.plan = json.plan;
            }
        }
        if (json.plan_type && !d.plan) d.plan = json.plan_type;

        if (json.next_billing_date && !d.next_billing) d.next_billing = json.next_billing_date;
        if (json.current_period_end && !d.next_billing) {
            const dt = new Date(json.current_period_end);
            d.next_billing = dt.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
        }

        if (json.current_period_usage !== undefined && d.this_month_usd === undefined) d.this_month_usd = parseFloat(json.current_period_usage) || 0;
        if (json.credit_balance !== undefined && d.balance_usd === undefined) d.balance_usd = parseFloat(json.credit_balance) || 0;
        if (json.remaining_balance !== undefined && d.balance_usd === undefined) d.balance_usd = parseFloat(json.remaining_balance) || 0;
        if (json.spend_limit !== undefined && d.spend_limit_usd === undefined) d.spend_limit_usd = parseFloat(json.spend_limit) || 0;
        if (json.monthly_limit !== undefined && d.spend_limit_usd === undefined) d.spend_limit_usd = parseFloat(json.monthly_limit) || 0;
        if (json.monthly_amount !== undefined && !d.monthly_usd) d.monthly_usd = parseFloat(json.monthly_amount) || 0;

        return d;
    }

    // ── GitHub Copilot ───────────────────────────
    function transformGitHubCopilot(url, json) {
        const d = {};

        // ====== 真實 API：/settings/billing/copilot_usage_card ======
        // { netBilledAmount: 0, netQuantity: 0, discountQuantity: 1298.64,
        //   userPremiumRequestEntitlement: 1500, filteredUserPremiumRequestEntitlement: 0 }
        if (json.userPremiumRequestEntitlement !== undefined) {
            d.included_total = parseFloat(json.userPremiumRequestEntitlement) || 0;
        }
        if (json.discountQuantity !== undefined) {
            d.included_consumed = parseFloat(json.discountQuantity) || 0;
        }
        if (json.netBilledAmount !== undefined) {
            d.billed_usd = parseFloat(json.netBilledAmount) || 0;
        }

        // ====== 真實 API：/settings/billing/copilot_usage_table ======
        // { table: { columns: [...], rows: [{ id: "Model", cells: [{value, sortValue}] }] } }
        if (json.table && json.table.rows && Array.isArray(json.table.rows)) {
            let totalIncluded = 0;
            let totalBilled = 0;
            for (const row of json.table.rows) {
                if (!row.cells || row.cells.length < 5) continue;
                const included = parseFloat(row.cells[1]?.value) || 0;   // "Included requests"
                const billed   = parseFloat(row.cells[2]?.value) || 0;   // "Billed requests"
                totalIncluded += included;
                totalBilled   += billed;
            }
            // Only use table totals as fallback if card didn't provide them
            if (totalIncluded > 0 && d.included_consumed === undefined) d.included_consumed = totalIncluded;
            if (totalBilled > 0 && d.billed_usd === undefined) {
                // Sum billed amounts from column 4 (index 4)
                let billedUsd = 0;
                for (const row of json.table.rows) {
                    if (!row.cells || row.cells.length < 5) continue;
                    const val = row.cells[4]?.value || '';   // "$0.00"
                    const num = parseFloat(val.replace(/[^0-9.\-]/g, ''));
                    if (!isNaN(num)) billedUsd += num;
                }
                if (billedUsd > 0) d.billed_usd = billedUsd;
            }
        }

        // ====== 真實 API：/settings/billing/copilot_usage_chart ======
        // { usage: [{ name: "Model", data: [{ x: ts, y: amount }] }] }
        // 圖表資料主要用於趨勢，不提取主要指標（card 已有）

        // Compute percent
        if (d.included_consumed !== undefined && d.included_total > 0) {
            d.included_percent = Math.round(d.included_consumed / d.included_total * 1000) / 10;
        }

        // ====== 備用：舊版/推測格式 ======
        if (json.included_premium_requests !== undefined || json.premium_requests !== undefined) {
            const pr = json.premium_requests || json.included_premium_requests || {};
            if (typeof pr === 'object') {
                if (pr.consumed !== undefined && d.included_consumed === undefined) d.included_consumed = parseFloat(pr.consumed) || 0;
                if (pr.used !== undefined && d.included_consumed === undefined) d.included_consumed = parseFloat(pr.used) || 0;
                if (pr.entitled !== undefined && d.included_total === undefined) d.included_total = parseFloat(pr.entitled) || 0;
                if (pr.total !== undefined && d.included_total === undefined) d.included_total = parseFloat(pr.total) || 0;
            }
        }

        if (json.consumed !== undefined && d.included_consumed === undefined) d.included_consumed = parseFloat(json.consumed) || 0;
        if (json.entitled !== undefined && d.included_total === undefined) d.included_total = parseFloat(json.entitled) || 0;

        // Billed amount fallback
        if (json.total_billed_amount !== undefined && d.billed_usd === undefined) d.billed_usd = parseFloat(json.total_billed_amount) || 0;
        if (json.billed_amount !== undefined && d.billed_usd === undefined) d.billed_usd = parseFloat(json.billed_amount) || 0;

        // Reset info
        if (json.days_until_reset !== undefined) d.resets_in_days = parseInt(json.days_until_reset) || 0;
        if (json.next_billing_date) d.next_billing = json.next_billing_date;
        if (json.billing_cycle_end) {
            const dt = new Date(json.billing_cycle_end);
            const now = new Date();
            d.resets_in_days = Math.max(0, Math.ceil((dt - now) / 86400000));
            d.next_billing = dt.toLocaleDateString('zh-TW');
        }

        return d;
    }

    // ─────────────────────────────────────────────
    //  OpenRouter DOM Parser
    //
    //  OpenRouter 是 Next.js SSR + RSC，數字直接渲染進 HTML，不發 JSON API。
    //  因此改用 DOM 解析。
    //
    //  - /settings/credits：
    //      ⭐ 新版頁面：容器上有 aria-label="Remaining credits: 39.486"，最可靠。
    //      舊版頁面：餘額用「翻頁動畫」結構，每位數字靠 transform: translateY(Npx) 偏移
    //               露出 0–9 中的某格。每格高 40px，digit = translateY / 40。
    //               flex-row-reverse 讓 HTML 順序與視覺相反，需反讀。
    //  - /activity：Spend / Requests / Tokens 直接是 textContent。
    // ─────────────────────────────────────────────

    function _parseFlipDigits(rootEl) {
        // 解析翻頁時鐘式的數字容器，回傳組合後的字串（含小數點）
        // 結構：<span class="flex-row-reverse"><div translateY=...>...</div>...<div>.</div>...</span>
        if (!rootEl) return null;
        const flipper = rootEl.querySelector('.flex-row-reverse');
        if (!flipper) return null;

        const segments = [];
        for (const child of flipper.children) {
            // 數字格：包含 absolute bottom-0 h-[1000%] 的子 div 帶 translateY
            const inner = child.querySelector('div.absolute');
            if (inner && inner.style && inner.style.transform) {
                const m = inner.style.transform.match(/translateY\((-?[\d.]+)px\)/);
                if (m) {
                    const px = parseFloat(m[1]);
                    const digit = Math.round(px / 40);
                    if (digit >= 0 && digit <= 9) {
                        segments.push(String(digit));
                        continue;
                    }
                }
                segments.push('?');
            } else {
                // 小數點 / 其他文字
                const txt = (child.textContent || '').trim();
                if (txt) segments.push(txt);
            }
        }
        if (!segments.length) return null;
        // flex-row-reverse 視覺反轉，需反讀
        return segments.reverse().join('');
    }

    function _parseTokenAbbrev(text) {
        // "537K" → 537000, "1.2M" → 1200000, "1234" → 1234
        const m = text.replace(/,/g, '').match(/^([\d.]+)\s*([KMB])?\s*$/i);
        if (!m) return null;
        const n = parseFloat(m[1]);
        if (!isFinite(n)) return null;
        const mul = { K: 1e3, M: 1e6, B: 1e9 }[(m[2] || '').toUpperCase()] || 1;
        return Math.round(n * mul);
    }

    function parseDOMOpenRouter() {
        if (PAGE.key !== 'openrouter') return false;
        if (!isOnExpectedPage()) return false;

        const fields = {};
        const path = location.pathname;

        if (path.startsWith('/settings/credits')) {
            let balance = null;

            // 方法 A（最可靠）：直接讀 aria-label="Remaining credits: 39.486"
            // 新版頁面把實際數值放在容器的 aria-label 上，不受翻頁動畫影響
            const ariaEls = document.querySelectorAll('[aria-label^="Remaining credits"]');
            for (const el of ariaEls) {
                const m = (el.getAttribute('aria-label') || '').match(/Remaining credits:\s*([\d,]+\.?\d*)/i);
                if (m) {
                    const val = parseFloat(m[1].replace(/,/g, ''));
                    if (isFinite(val) && val >= 0 && val < 100000) {
                        balance = val;
                        break;
                    }
                }
            }

            // 方法 B：翻頁動畫格式（flex-row-reverse + translateY）
            if (balance === null) {
                const candidates = document.querySelectorAll('span.text-4xl');
                for (const span of candidates) {
                    const flipped = _parseFlipDigits(span);
                    if (flipped !== null && /^\d+\.\d+$/.test(flipped)) {
                        balance = parseFloat(flipped);
                        break;
                    }
                }
            }

            // 方法 C：靜態大字體文字 fallback
            if (balance === null) {
                const bigEls = document.querySelectorAll(
                    'p.text-4xl, span.text-4xl, p.text-3xl, span.text-3xl, p.text-5xl, span.text-5xl'
                );
                for (const el of bigEls) {
                    const text = (el.textContent || '').trim().replace(/[$,\s]/g, '');
                    const val = parseFloat(text);
                    if (isFinite(val) && val >= 0 && val < 100000) {
                        balance = val;
                        break;
                    }
                }
            }

            // 方法 D：全頁搜尋 "$ XX.XX" 文字節點（最後手段）
            if (balance === null && document.body) {
                const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
                let node;
                while ((node = walker.nextNode())) {
                    const text = (node.textContent || '').trim();
                    const m = text.match(/^\$\s*([\d,]+\.\d{2})$/);
                    if (m) {
                        const val = parseFloat(m[1].replace(/,/g, ''));
                        if (isFinite(val) && val >= 0 && val < 100000) {
                            balance = val;
                            break;
                        }
                    }
                }
            }

            if (balance !== null && isFinite(balance)) {
                fields.balance_usd = balance;
            } else {
                fields.parse_error = '無法解析 OpenRouter 餘額（DOM selector 失效，請檢查網頁更新）';
            }
        } else if (path.startsWith('/activity')) {
            // Spend / Requests / Tokens：每張卡片有 <span>Spend</span> 標題 + <span>$0.494</span> 數值
            // 用標題文字定位，找最近的兄弟數值節點
            const titleNodes = document.querySelectorAll('span.text-sm.text-slate-11, span.text-slate-11');
            let foundAny = false;
            for (const titleSpan of titleNodes) {
                const label = (titleSpan.textContent || '').trim();
                // 數值在同一個 .flex.flex-col 容器內的 .text-2xl 兄弟節點
                const container = titleSpan.parentElement;
                if (!container) continue;
                const valueSpan = container.querySelector('span.text-2xl');
                if (!valueSpan) continue;
                const valueText = (valueSpan.textContent || '').trim();
                if (!valueText) continue;

                if (/^Spend$/i.test(label)) {
                    const m = valueText.match(/([\d.]+)/);
                    if (m) { fields.month_spend_usd = parseFloat(m[1]); foundAny = true; }
                } else if (/^Requests?$/i.test(label)) {
                    const n = _parseTokenAbbrev(valueText);
                    if (n !== null) { fields.month_requests = n; foundAny = true; }
                } else if (/^Tokens?$/i.test(label)) {
                    const n = _parseTokenAbbrev(valueText);
                    if (n !== null) { fields.month_tokens = n; foundAny = true; }
                }
            }

            // 主要模型：找第一個 .text-slate-12.truncate
            const modelEl = document.querySelector('div.flex span.text-slate-12.truncate');
            if (modelEl) {
                const name = (modelEl.textContent || '').trim();
                if (name) fields.top_model = name;
            }

            if (!foundAny) {
                fields.parse_error = '無法解析 OpenRouter Activity（DOM selector 失效，請檢查網頁更新）';
            }
        }

        if (Object.keys(fields).length === 0) {
            return false;
        }

        dbg('parseDOMOpenRouter: 解析結果', fields);
        merge('openrouter', fields);
        domParseSuccess = !fields.parse_error;
        return true;
    }

    function installDOMObserverOpenRouter() {
        if (PAGE.key !== 'openrouter') return;

        // 立即嘗試一次（SSR 通常已渲染完成）
        if (parseDOMOpenRouter()) {
            // 再延遲解析一次，等翻頁動畫穩定（translateY 動畫約 1–2 秒）
            setTimeout(parseDOMOpenRouter, 2500);
            return;
        }

        // 沒抓到 → 用 MutationObserver 等元素出現
        dbg('installDOMObserverOpenRouter: 啟動 MutationObserver');
        let observer = new MutationObserver(() => {
            if (parseDOMOpenRouter()) {
                setTimeout(parseDOMOpenRouter, 2500);  // 動畫穩定後再讀一次
                observer.disconnect();
                observer = null;
            }
        });
        observer.observe(document.body || document.documentElement, { childList: true, subtree: true });

        setTimeout(() => {
            if (observer) {
                observer.disconnect();
                observer = null;
                dbg('installDOMObserverOpenRouter: 30s 逾時');
                // 逾時也送一筆錯誤狀態
                merge('openrouter', { parse_error: '30 秒內未偵測到 OpenRouter 數值節點' });
            }
        }, 30000);
    }

    // ── GitHub Copilot DOM Parsing ─────────────────
    function parseDOMGitHubCopilot() {
        if (PAGE.key !== 'github_copilot') return false;
        if (!isOnExpectedPage()) return false;

        // ── /settings/billing/budgets 頁面 ─────────────
        if (location.pathname.startsWith('/settings/billing/budgets')) {
            return parseDOMGitHubBudgets();
        }

        // ── /settings/copilot/features 頁面 ─────────────
        const container = document.getElementById('copilot-overages-usage');
        if (!container) {
            dbg('parseDOMGitHubCopilot: #copilot-overages-usage 不存在');
            return false;
        }

        // 方法 A：讀進度條 style.width（最可靠）
        let percent = null;
        const progressItem = container.querySelector('#copilot_overages_progress_bar .Progress-item');
        if (progressItem) {
            const m = (progressItem.style.width || '').match(/([\d.]+)%/);
            if (m) percent = parseFloat(m[1]);
        }

        // 方法 B：讀百分比文字 fallback
        if (percent === null) {
            for (const div of container.querySelectorAll('div')) {
                if (div.children.length === 0) {
                    const m = div.textContent.trim().match(/^([\d.]+)%$/);
                    if (m) { percent = parseFloat(m[1]); break; }
                }
            }
        }

        if (percent === null) {
            dbg('parseDOMGitHubCopilot: 無法提取百分比');
            return false;
        }

        const INCLUDED_TOTAL = 1500;
        const fields = {
            included_percent:  Math.round(percent * 10) / 10,
            included_total:    INCLUDED_TOTAL,
            included_consumed: Math.round(percent / 100 * INCLUDED_TOTAL * 10) / 10,
            billed_usd:        0,
        };
        dbg('parseDOMGitHubCopilot: 解析成功', fields);
        merge('github_copilot', fields);
        domParseSuccess = true;
        return true;
    }

    // ── GitHub Billing Budgets DOM Parsing ─────────
    function parseDOMGitHubBudgets() {
        const fields = {};

        // 找含 "All Premium Request SKUs" 的 tr
        let targetRow = null;
        for (const tr of document.querySelectorAll('tr')) {
            if ((tr.textContent || '').includes('All Premium Request SKUs')) {
                targetRow = tr;
                break;
            }
        }

        if (!targetRow) {
            dbg('parseDOMGitHubBudgets: 找不到 All Premium Request SKUs 列');
            return false;
        }

        // aria-valuenow 百分比
        const progressBar = targetRow.querySelector('[role="progressbar"]');
        if (progressBar) {
            const now = parseFloat(progressBar.getAttribute('aria-valuenow'));
            if (isFinite(now)) fields.budget_percent = now;
        }

        // LinkText span：第一個 = spent，第二個 = budget
        const amounts = [];
        for (const el of targetRow.querySelectorAll('[class*="LinkText"]')) {
            const val = parseFloat((el.textContent || '').replace(/[$,]/g, ''));
            if (isFinite(val)) amounts.push(val);
        }
        if (amounts.length >= 2) {
            fields.budget_spent_usd = amounts[0];
            fields.budget_limit_usd = amounts[1];
            if (fields.budget_limit_usd > 0) {
                fields.budget_percent = Math.round(fields.budget_spent_usd / fields.budget_limit_usd * 1000) / 10;
            }
        } else if (amounts.length === 0) {
            dbg('parseDOMGitHubBudgets: 找不到金額');
            return false;
        }

        dbg('parseDOMGitHubBudgets: 解析成功', fields);
        merge('github_copilot', fields);
        domParseSuccess = true;
        return true;
    }

    function installDOMObserver() {
        if (PAGE.key !== 'github_copilot') return;
        if (_domObserver) return;
        if (parseDOMGitHubCopilot()) return;  // SSR 已有資料直接解析

        const isBudgetsPage = location.pathname.startsWith('/settings/billing/budgets');
        dbg('installDOMObserver: 啟動 MutationObserver，頁面:', isBudgetsPage ? 'budgets' : 'copilot/features');

        _domObserver = new MutationObserver((mutations, obs) => {
            for (const mutation of mutations) {
                if (mutation.type !== 'childList') continue;
                for (const node of mutation.addedNodes) {
                    if (node.nodeType !== Node.ELEMENT_NODE) continue;
                    if (!isBudgetsPage) {
                        if (node.id === 'copilot-overages-usage' ||
                            (node.querySelector && node.querySelector('#copilot-overages-usage'))) {
                            obs.disconnect();
                            _domObserver = null;
                            parseDOMGitHubCopilot();
                            return;
                        }
                    } else {
                        if ((node.textContent || '').includes('All Premium Request SKUs') ||
                            (node.querySelector && node.querySelector('tr'))) {
                            obs.disconnect();
                            _domObserver = null;
                            parseDOMGitHubCopilot();
                            return;
                        }
                    }
                }
            }
        });
        _domObserver.observe(document.body || document.documentElement, { childList: true, subtree: true });

        setTimeout(() => {
            if (_domObserver) { _domObserver.disconnect(); _domObserver = null; dbg('installDOMObserver: 30s 逾時'); }
        }, 30000);
    }

    // ─────────────────────────────────────────────
    //  INTERCEPT RULES
    //
    //  ⚠️ 用 debug 模式確認真正的 URL pattern 後再縮窄
    //  寬鬆 pattern 確保不會漏
    // ─────────────────────────────────────────────
    const INTERCEPT_RULES = {
        openai_billing: [
            { p: /\/dashboard\/billing\/credit_grants/i,   t: transformOpenAI },
            { p: /\/dashboard\/billing\/usage/i,           t: transformOpenAI },
            { p: /\/dashboard\/billing\/subscription/i,    t: transformOpenAI },
            { p: /\/dashboard\/billing/i,                  t: transformOpenAI },
            { p: /\/organization\/costs/i,                 t: transformOpenAI },
            { p: /\/organization\/billing/i,               t: transformOpenAI },
            { p: /\/organization\/subscription/i,          t: transformOpenAI },
        ],
        claude_usage: [
            { p: /\/api\/organizations\/[^/]+\/usage/i,            t: transformClaudeUsage },
            { p: /\/api\/organizations\/[^/]+\/prepaid\/credits/i, t: transformClaudeUsage },
            { p: /\/api\/organizations\/[^/]+\/prepaid\/bundles/i, t: transformClaudeUsage },
            { p: /\/api\/organizations\/[^/]+\/rate_limit/i,       t: transformClaudeUsage },
            { p: /\/api\/organizations\/[^/]+\/settings/i,         t: transformClaudeUsage },
            { p: /\/api\/usage/i,                                  t: transformClaudeUsage },
            { p: /\/api\/auth\/session/i,                          t: transformClaudeUsage },
            { p: /\/api\/settings/i,                               t: transformClaudeUsage },
            { p: /\/api\/bootstrap/i,                              t: transformClaudeUsage },
        ],
        claude_billing: [
            { p: /\/api\/organizations\/[^/]+\/billing/i,          t: transformClaudeBilling },
            { p: /\/api\/organizations\/[^/]+\/subscription/i,     t: transformClaudeBilling },
            { p: /\/api\/organizations\/[^/]+\/invoices/i,         t: transformClaudeBilling },
            { p: /\/api\/organizations\/[^/]+\/prepaid/i,          t: transformClaudeBilling },
            { p: /\/api\/organizations\/[^/]+\/rate_limits/i,      t: transformClaudeBilling },
            { p: /\/api\/organizations\/[^/]+\/current_spend/i,    t: transformClaudeBilling },
            { p: /\/api\/organizations\/[^/]+\/invoiced_balance/i, t: transformClaudeBilling },
            { p: /\/v1\/organizations\/[^/]+\/api_billing/i,       t: transformClaudeBilling },
            { p: /\/api\/billing/i,                                t: transformClaudeBilling },
        ],
        github_copilot: [
            { p: /copilot_billing\/.*usage/i,                    t: transformGitHubCopilot },
            { p: /\/billing\/premium_requests/i,                 t: transformGitHubCopilot },
            { p: /\/copilot\/usage/i,                            t: transformGitHubCopilot },
            { p: /copilot.*metered.*usage/i,                     t: transformGitHubCopilot },
            { p: /\/settings\/billing.*copilot/i,                t: transformGitHubCopilot },
            { p: /\/settings\/copilot.*usage/i,                  t: transformGitHubCopilot },
            { p: /\/settings\/copilot.*billing/i,                t: transformGitHubCopilot },
            { p: /copilot.*features.*usage/i,                    t: transformGitHubCopilot },
            { p: /copilot.*premium.*request/i,                   t: transformGitHubCopilot },
        ],
        // OpenRouter 不走 fetch/XHR JSON（SSR + RSC 字串流），改由 parseDOMOpenRouter 處理
        openrouter: [],
    };

    // Only activate rules for current page
    const activeRules = INTERCEPT_RULES[PAGE.key] || [];

    // ─────────────────────────────────────────────
    //  DATA MERGE & FLUSH
    // ─────────────────────────────────────────────
    function merge(source, fields) {
        // Strip undefined/null values
        const clean = {};
        for (const [k, v] of Object.entries(fields)) {
            if (v !== undefined && v !== null) clean[k] = v;
        }
        if (Object.keys(clean).length === 0) return;

        pendingData[source] = { ...(pendingData[source] || {}), ...clean };

        // Reset debounce timer
        if (mergeTimers[source]) clearTimeout(mergeTimers[source]);
        mergeTimers[source] = setTimeout(() => flushSource(source), MERGE_WINDOW);
    }

    function flushSource(source) {
        const data = pendingData[source];
        if (!data || Object.keys(data).length === 0) return;

        data.source    = source;
        data.timestamp = new Date().toISOString();
        data.page_url  = location.href;

        // Change detection (same as V3)
        const SKIP = new Set(['source', 'timestamp', 'page_url']);
        const prev = lastData[source];
        const changed = !prev || Object.keys(data).some(k => !SKIP.has(k) && data[k] !== prev[k]);
        lastData[source] = { ...data };

        if (changed) {
            dbg('📤 送出資料:', source, data);
            sendToServer(data);
        } else {
            dbg('⏭️ 資料無變化，略過:', source);
            setStatus('success');
        }

        delete pendingData[source];
        delete mergeTimers[source];
    }

    // ─────────────────────────────────────────────
    //  URL PRE-FILTER（效能關鍵：在 clone/parse 之前過濾）
    // ─────────────────────────────────────────────
    function isUrlRelevant(url) {
        // 1. 匹配任何 active rule → 一定相關
        if (activeRules.some(rule => rule.p.test(url))) return true;
        // 2. debug 模式 → 額外放行含有相關關鍵字的 URL（用於發現新 API endpoint）
        if (!config.debug) return false;
        const lower = url.toLowerCase();
        return ['billing', 'usage', 'credit', 'cost', 'limit', 'quota', 'balance', 'invoice']
            .some(kw => lower.includes(kw));
    }

    // ─────────────────────────────────────────────
    //  INTERCEPT HANDLER
    // ─────────────────────────────────────────────
    function handleInterceptedResponse(url, json) {
        if (!isOnExpectedPage()) return;

        let matched = false;
        for (const rule of activeRules) {
            if (rule.p.test(url)) {
                matched = true;
                interceptCount++;

                dbgGroup('✅ 匹配 API: ' + url);
                if (config.debug) {
                    // 直接印出物件，瀏覽器 Console 原生支援展開；避免 JSON.stringify 同步序列化大物件
                    console.log('JSON preview:', json);
                }

                try {
                    const fields = rule.t(url, json);
                    if (config.debug) console.log('提取欄位:', fields);
                    merge(PAGE.key, fields);
                } catch (err) {
                    console.error(LOG_PREFIX, '轉換錯誤:', err);
                }

                dbgGroupEnd();
                break;  // 一個 URL 只匹配第一個 rule
            }
        }

        if (!matched && config.debug) {
            // isUrlRelevant 在 debug 模式下已過濾關鍵字，能走到這裡代表 URL 含有相關關鍵字
            dbg('🔍 可能相關但未匹配:', url);
            console.log('   JSON preview:', json);
        }
    }

    // ─────────────────────────────────────────────
    //  FETCH INTERCEPTOR
    // ─────────────────────────────────────────────
    function installFetchHook() {
        const win = unsafeWindow;
        const _realFetch = win.fetch;
        if (!_realFetch) {
            console.warn(LOG_PREFIX, 'window.fetch 不存在，跳過 fetch hook');
            return;
        }

        win.fetch = function (...args) {
            if (!isOnExpectedPage()) return _realFetch.apply(this, args);
            let url;
            try {
                url = args[0] instanceof Request ? args[0].url : String(args[0]);
            } catch (e) {
                return _realFetch.apply(this, args);
            }

            return _realFetch.apply(this, args).then(response => {
                // 【效能修正】先檢查 URL 是否相關，不相關的完全跳過 clone + parse
                if (response.ok && isUrlRelevant(url)) {
                    const ct = response.headers.get('content-type') || '';
                    if (ct.includes('json')) {
                        try {
                            response.clone().json().then(json => {
                                handleInterceptedResponse(url, json);
                            }).catch(() => {});
                        } catch (e) {}
                    }
                }
                return response;  // 原封不動回傳
            }).catch(err => {
                throw err;  // 不吞錯誤
            });
        };

        // Preserve toString to avoid detection
        try {
            win.fetch.toString = function () { return 'function fetch() { [native code] }'; };
        } catch (e) {}

        dbg('✓ fetch hook 已安裝');
    }

    // ─────────────────────────────────────────────
    //  XMLHttpRequest INTERCEPTOR
    // ─────────────────────────────────────────────
    function installXHRHook() {
        const win = unsafeWindow;
        const XHR = win.XMLHttpRequest;
        if (!XHR) {
            console.warn(LOG_PREFIX, 'XMLHttpRequest 不存在，跳過 XHR hook');
            return;
        }

        const _origOpen = XHR.prototype.open;
        XHR.prototype.open = function (method, url, ...rest) {
            this._aimon_url = typeof url === 'string' ? url : String(url);
            this._aimon_method = method;
            return _origOpen.call(this, method, url, ...rest);
        };

        const _origSend = XHR.prototype.send;
        XHR.prototype.send = function (...args) {
            this.addEventListener('load', function () {
                const url = this._aimon_url || '';
                // 【效能修正】先檢查 URL，不相關就跳過同步 JSON.parse
                if (isUrlRelevant(url) && this.status >= 200 && this.status < 300) {
                    const ct = (this.getResponseHeader('content-type') || '');
                    if (ct.includes('json') && this.responseText) {
                        try {
                            const json = JSON.parse(this.responseText);
                            handleInterceptedResponse(url, json);
                        } catch (e) {}
                    }
                }
            });
            return _origSend.apply(this, args);
        };

        dbg('✓ XHR hook 已安裝');
    }

    // ─────────────────────────────────────────────
    //  SERVER COMMUNICATION
    // ─────────────────────────────────────────────
    function sendToServer(data) {
        setStatus('listening');  // 傳送中
        GM_xmlhttpRequest({
            method:  'POST',
            url:     config.server_url + '/update',
            headers: { 'Content-Type': 'application/json', 'X-AI-Monitor-Client': '1' },
            data:    JSON.stringify(data),
            timeout: 5000,
            onload(resp) {
                if (resp.status >= 200 && resp.status < 300) {
                    lastSuccessTime = Date.now();
                    setStatus('success');
                    dbg('✓ 伺服器已接收');
                } else {
                    setStatus('error', '伺服器 ' + resp.status);
                }
            },
            onerror()   { setStatus('error', '無法連線'); },
            ontimeout() { setStatus('error', '連線逾時'); },
        });
    }

    // ─────────────────────────────────────────────
    //  UI — 極簡色點（同 V3 方案）
    // ─────────────────────────────────────────────
    function buildUI() {
        if (_dot) return;
        _dot = document.createElement('div');
        _dot.style.cssText = [
            'position:fixed',
            'bottom:16px',
            'right:16px',
            'z-index:2147483647',
            'width:36px',
            'height:36px',
            'border-radius:50%',
            'cursor:pointer',
            'background-color:' + COLORS.listening,
            'box-shadow:0 2px 8px rgba(0,0,0,0.5)',
            'border:2px solid rgba(255,255,255,0.15)',
            'transition:background-color 0.3s',
            'display:flex',
            'align-items:center',
            'justify-content:center',
            'font-size:14px',
            'user-select:none',
        ].join(';');
        _dot.textContent = '⚡';
        _dot.title = PAGE.label + ' v4.4.1 — 攔截模式\n點擊重新載入頁面';
        _dot.addEventListener('click', () => {
            location.reload();
        });
        document.body.appendChild(_dot);
        dbg('✓ UI 色點已建立');
    }

    function setStatus(status, msg) {
        if (!_dot) return;
        _dot.style.backgroundColor = COLORS[status] || COLORS.idle;
        if (status === 'success' && lastSuccessTime) {
            const t = new Date(lastSuccessTime).toLocaleTimeString('zh-TW');
            _dot.title = PAGE.label + ' ✓ ' + t + '\n已攔截 ' + interceptCount + ' 個 API\n點擊重新載入';
        } else if (status === 'error') {
            _dot.title = PAGE.label + ' ✗ ' + (msg || '') + '\n點擊重試';
        } else if (status === 'listening') {
            _dot.title = PAGE.label + ' — 監聽中...\n已攔截 ' + interceptCount + ' 個 API';
        } else {
            _dot.title = PAGE.label + ' — 等待 API 回應\n點擊重新載入';
        }
    }

    // ─────────────────────────────────────────────
    //  SPA NAVIGATION DETECTION
    // ─────────────────────────────────────────────
    function setupSPADetection() {
        let lastHref = location.href;
        setInterval(() => {
            if (location.href === lastHref) return;
            lastHref = location.href;
            dbg('🔄 SPA 導航偵測:', lastHref);
            // Hook 已在 document-start 安裝，不需要重裝
            // 但清空 pending / 重置 UI 狀態
            interceptCount = 0;
            setStatus('listening');
            if (isOnExpectedPage() && PAGE.key === 'claude_usage' && !_dot) {
                buildUI();
                setupPeriodicRefresh();
                setupTimeoutWarning();
            }
            if (isOnExpectedPage() && PAGE.key === 'github_copilot') {
                if (_domObserver) { _domObserver.disconnect(); _domObserver = null; }
                domParseSuccess = false;
                installDOMObserver();
                // budgets 頁面額外再解析一次（等 React 渲染）
                if (location.pathname.startsWith('/settings/billing/budgets')) {
                    setTimeout(parseDOMGitHubCopilot, 2000);
                }
            }
            if (isOnExpectedPage() && PAGE.key === 'openrouter') {
                domParseSuccess = false;
                installDOMObserverOpenRouter();
            }
        }, 2000);
    }

    // ─────────────────────────────────────────────
    //  PERIODIC REFRESH
    // ─────────────────────────────────────────────
    function setupPeriodicRefresh() {
        const interval = PAGE.refreshInterval;
        setInterval(() => {
            if (lastSuccessTime && (Date.now() - lastSuccessTime) > interval) {
                dbg('⏰ 資料過期，重新載入頁面');
                location.reload();
            }
        }, 60000);  // 每分鐘檢查一次
    }

    // ─────────────────────────────────────────────
    //  TIMEOUT WARNING
    // ─────────────────────────────────────────────
    function setupTimeoutWarning() {
        setTimeout(() => {
            if (interceptCount === 0 && !domParseSuccess) {
                dbg('⚠️ 15 秒內未攔截到任何匹配 API');
                setStatus('idle');
                if (_dot) {
                    _dot.title = PAGE.label + ' ⚠ 未偵測到 API 回應\n可能需要調整 URL pattern\n點擊重新載入';
                }
            }
        }, 15000);
    }

    // ─────────────────────────────────────────────
    //  DEBUG CONSOLE COMMANDS
    // ─────────────────────────────────────────────
    function exposeDebugAPI() {
        const api = {
            /** Toggle debug mode */
            debug(on) {
                config.debug = on !== undefined ? !!on : !config.debug;
                GM_setValue('aimon_debug', config.debug);
                console.log(LOG_PREFIX, 'Debug:', config.debug ? 'ON' : 'OFF');
            },
            /** Show current state */
            status() {
                console.table({
                    page: PAGE.key,
                    interceptCount,
                    lastSuccess: lastSuccessTime ? new Date(lastSuccessTime).toLocaleString() : 'never',
                    pendingKeys: Object.keys(pendingData).join(', ') || 'none',
                    lastDataKeys: Object.keys(lastData).join(', ') || 'none',
                    debug: config.debug,
                    serverUrl: config.server_url,
                });
            },
            /** Show last captured data */
            data() {
                console.log(LOG_PREFIX, 'Last data:');
                console.dir(lastData);
            },
            /** Force flush pending data */
            flush() {
                for (const source of Object.keys(pendingData)) flushSource(source);
            },
            /** Set server URL */
            server(url) {
                if (url) { config.server_url = url; GM_setValue('aimon_server', url); }
                console.log(LOG_PREFIX, 'Server:', config.server_url);
            },
        };

        try {
            unsafeWindow.__aimon = api;
            dbg('✓ Debug API 已暴露至 window.__aimon');
            dbg('  可用指令: __aimon.debug(), __aimon.status(), __aimon.data(), __aimon.flush(), __aimon.server(url)');
        } catch (e) {}
    }

    // ─────────────────────────────────────────────
    //  BOOT
    // ─────────────────────────────────────────────

    // Phase 1: 在 document-start 立刻安裝 hook（此時 DOM 未就緒）
    dbg('=== AI Quota Monitor v4.4.3 啟動 ===');
    dbg('頁面:', PAGE.label, '(' + PAGE.key + ')');
    dbg('規則數:', activeRules.length);

    installFetchHook();
    installXHRHook();
    exposeDebugAPI();

    // Phase 2: DOM Ready 後建立 UI 與生命週期
    function onDomReady() {
        if (!isOnExpectedPage()) {
            dbg('⏭️ 不在預期路徑，跳過 UI');
            return;
        }
        buildUI();
        setupSPADetection();
        setupPeriodicRefresh();
        setupTimeoutWarning();
        installDOMObserver();
        installDOMObserverOpenRouter();
        document.addEventListener('turbo:load', () => {
            if (!isOnExpectedPage() || PAGE.key !== 'github_copilot') return;
            if (_domObserver) { _domObserver.disconnect(); _domObserver = null; }
            domParseSuccess = false;
            installDOMObserver();
            if (location.pathname.startsWith('/settings/billing/budgets')) {
                setTimeout(parseDOMGitHubCopilot, 2000);
            }
        }, { passive: true });
        dbg('✓ 所有模組已初始化');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', onDomReady);
    } else {
        onDomReady();
    }

})();
