const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const injectionScript = fs.readFileSync(
    path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'ai-monitor-android.js'),
    'utf8',
);

function parseChatGptText(texts, limitResetSectionText = null) {
    const posts = [];
    const nodes = texts.map((textContent) => ({ textContent }));
    const resetSection = limitResetSectionText === null ? null : {
        innerText: limitResetSectionText,
        textContent: limitResetSectionText,
    };
    const headings = resetSection === null ? [] : [{
        textContent: '使用量限制重設',
        closest(selector) {
            return selector === 'section' ? resetSection : null;
        },
    }];
    const document = {
        body: {},
        documentElement: {},
        querySelectorAll(selector) {
            if (selector === 'div, span') return nodes;
            if (selector === 'h1, h2, h3, h4, h5, h6') return headings;
            return [];
        },
    };

    vm.runInNewContext(injectionScript, {
        AndroidBridge: {
            postData(source, json) {
                posts.push({ source, data: JSON.parse(json) });
            },
        },
        MutationObserver: class {
            observe() {}
            disconnect() {}
        },
        clearTimeout() {},
        document,
        location: {
            hash: '#settings/Usage',
            hostname: 'chatgpt.com',
            pathname: '/',
        },
        setTimeout(callback) {
            callback();
            return 1;
        },
        window: {},
    });

    assert.ok(posts.length > 0, 'ChatGPT usage parser did not post any data');
    return posts.at(-1).data;
}

function parseChatGptTextAfterMutation(initialTexts, addedTexts) {
    const posts = [];
    const nodes = initialTexts.map((textContent) => ({ textContent }));
    const timers = [];
    let observerCallback;
    const document = {
        body: {},
        documentElement: {},
        querySelectorAll(selector) {
            if (selector === 'div, span') return nodes;
            return [];
        },
    };

    const context = {
        AndroidBridge: {
            postData(source, json) {
                posts.push({ source, data: JSON.parse(json) });
            },
        },
        MutationObserver: class {
            constructor(callback) {
                observerCallback = callback;
            }
            observe() {}
            disconnect() {}
        },
        clearTimeout(id) {
            if (timers[id]) timers[id].active = false;
        },
        document,
        location: {
            hash: '#settings/Usage',
            hostname: 'chatgpt.com',
            pathname: '/',
        },
        setTimeout(callback) {
            const id = timers.length;
            timers.push({ active: true, callback });
            return id;
        },
        window: {},
    };

    vm.runInNewContext(injectionScript, context);
    timers[0].active = false;
    timers[0].callback();
    assert.equal(typeof observerCallback, 'function', 'Parser stopped before reset text rendered');

    nodes.push(...addedTexts.map((textContent) => ({ textContent })));
    observerCallback();
    for (const timer of timers) {
        if (timer.active) {
            timer.active = false;
            timer.callback();
        }
    }

    assert.ok(posts.length > 0, 'ChatGPT usage parser did not post data after mutation');
    return posts.at(-1).data;
}

const plus = parseChatGptText([
    '每週用量上限',
    '剩餘 71%',
    '於 2026年7月23日 下午2:28 重設',
], '使用量限制重設\n目前沒有可用的使用量限制重設。');
assert.equal(plus.weekly_remaining_percent, 71);
assert.equal(plus.weekly_reset, '於 2026年7月23日 下午2:28 重設');

const pro = parseChatGptText([
    '每週上限',
    '剩餘 89%',
    '6 天 6 小時 後重設',
], '使用量限制重設\n目前沒有可用的使用量限制重設。');
assert.equal(pro.weekly_remaining_percent, 89);
assert.equal(pro.weekly_reset, '6 天 6 小時 後重設');
assert.equal(pro.limit_reset_count, 0);

const proWithLimitResets = parseChatGptText([
    '每週上限',
    '剩餘 89%',
    '6 天 6 小時 後重設',
], [
    '使用量限制重設',
    '使用重置即可恢復 5 小時用量上限、每週用量上限，或兩者皆恢復。',
    '5 小時用量上限',
    '到期日：2026年9月30日',
    '使用重置',
    '每週用量上限',
    '有效期限',
    '2026年10月7日',
    '使用重置',
].join('\n'));
assert.equal(proWithLimitResets.limit_reset_count, 2);
assert.equal(proWithLimitResets.limit_reset_1_expiry, '到期日：2026年9月30日');
assert.equal(proWithLimitResets.limit_reset_2_expiry, '有效期限 2026年10月7日');

const progressivelyRenderedPro = parseChatGptTextAfterMutation(
    ['每週上限', '剩餘 89%'],
    ['6 天 6 小時 後重設'],
);
assert.equal(progressivelyRenderedPro.weekly_remaining_percent, 89);
assert.equal(progressivelyRenderedPro.weekly_reset, '6 天 6 小時 後重設');

console.log('ChatGPT Plus and Pro usage parser fixtures passed.');
