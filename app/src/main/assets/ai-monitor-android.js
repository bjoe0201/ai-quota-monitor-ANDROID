/**
 * AI Quota Monitor - Android WebView Injection Script
 *
 * Adapted from ai-monitor-client-v4.4.js for use with Android WebView.
 * Instead of GM_xmlhttpRequest / POST to localhost, data is passed via
 * AndroidBridge.postData(source, json) @JavascriptInterface.
 *
 * Injected by WebViewDataCollector.loadService() after page load.
 */
(function () {
    'use strict';

    const PAGE_MAP = {
        'platform.openai.com':    { key: 'openai_billing', label: 'OpenAI' },
        'claude.ai':              { key: 'claude_usage',   label: 'Claude Usage' },
        'platform.claude.com':    { key: 'claude_billing', label: 'Claude Billing' },
        'github.com':             { key: 'github_copilot', label: 'Copilot' },
        'openrouter.ai':          { key: 'openrouter',     label: 'OpenRouter' },
    };

    const PAGE = PAGE_MAP[location.hostname];
    if (!PAGE) return;

    const pendingData = {};
    let mergeTimer = null;
    const MERGE_WINDOW = 2000;

    function send(source, data) {
        if (typeof AndroidBridge !== 'undefined') {
            AndroidBridge.postData(source, JSON.stringify(data));
        }
    }

    function merge(source, fields) {
        Object.assign(pendingData, fields);
        if (mergeTimer) clearTimeout(mergeTimer);
        mergeTimer = setTimeout(function () {
            send(source, Object.assign({}, pendingData));
        }, MERGE_WINDOW);
    }

    // ── Transform functions ──────────────────────────

    function transformOpenAI(url, json) {
        var d = {};
        if (json.total_available !== undefined) d.balance_usd = parseFloat(json.total_available) || 0;
        if (json.total_granted !== undefined && json.total_used !== undefined) {
            d.credits_total_usd = parseFloat(json.total_granted) || 0;
            d.credits_used_usd = parseFloat(json.total_used) || 0;
        }
        if (json.hard_limit_usd !== undefined) d.hard_limit_usd = parseFloat(json.hard_limit_usd) || 0;
        if (json.plan) {
            if (typeof json.plan === 'object' && json.plan.title) d.tier = json.plan.title;
            else if (typeof json.plan === 'string') d.tier = json.plan;
        }
        if (json.total_usage !== undefined) d.month_usage_usd = parseFloat(json.total_usage) / 100 || 0;
        if (json.object === 'page' && json.data && Array.isArray(json.data)) {
            var totalAmount = 0;
            for (var i = 0; i < json.data.length; i++) {
                var entry = json.data[i];
                if (entry.amount) totalAmount += (entry.amount.value || 0);
            }
            if (totalAmount > 0) d.month_usage_usd = totalAmount;
        }
        if (json.auto_recharge_enabled !== undefined) d.auto_recharge = !!json.auto_recharge_enabled;
        return d;
    }

    function transformClaudeUsage(url, json) {
        var d = {};
        if (json.five_hour && typeof json.five_hour === 'object') {
            if (json.five_hour.utilization !== undefined) d.session_percent = Math.round(json.five_hour.utilization);
            if (json.five_hour.resets_at) {
                var ms = new Date(json.five_hour.resets_at) - Date.now();
                if (ms > 0) {
                    var mins = Math.ceil(ms / 60000);
                    d.session_reset = mins >= 60 ? Math.floor(mins / 60) + ' hrs ' + (mins % 60) + ' mins' : mins + ' mins';
                }
            }
        }
        if (json.seven_day && typeof json.seven_day === 'object') {
            if (json.seven_day.utilization !== undefined) d.weekly_percent = Math.round(json.seven_day.utilization);
            if (json.seven_day.resets_at) {
                var ms2 = new Date(json.seven_day.resets_at) - Date.now();
                if (ms2 > 0) {
                    var days = Math.ceil(ms2 / 86400000);
                    var hrs = Math.ceil((ms2 % 86400000) / 3600000);
                    d.weekly_reset = days > 0 ? days + ' days ' + hrs + ' hrs' : hrs + ' hrs';
                }
            }
        }
        var ex = json.extra_usage || json.extra || json.overages;
        if (ex && typeof ex === 'object') {
            d.extra_enabled = !!(ex.is_enabled !== undefined ? ex.is_enabled : true);
            if (ex.used_credits !== undefined) d.extra_spent = parseFloat(ex.used_credits) / 100;
            if (ex.monthly_limit !== undefined) d.extra_limit = parseFloat(ex.monthly_limit) / 100;
            if (ex.utilization !== undefined) d.extra_percent = Math.round(ex.utilization);
            if (ex.balance !== undefined) d.extra_balance = parseFloat(ex.balance) || 0;
            if (ex.resets_at) {
                var dt = new Date(ex.resets_at);
                d.extra_resets = dt.toLocaleDateString('en-US', { month: 'long', day: 'numeric' });
            }
            if (ex.auto_reload !== undefined) d.auto_reload = !!ex.auto_reload;
        }
        if (json.amount !== undefined && json.currency) {
            d.extra_balance = parseFloat(json.amount) / 100;
        }
        if (json.purchases_reset_at) {
            var dt2 = new Date(json.purchases_reset_at);
            d.extra_resets = dt2.toLocaleDateString('en-US', { month: 'long', day: 'numeric' });
        }
        if (json.session_percent !== undefined) d.session_percent = json.session_percent;
        if (json.weekly_percent !== undefined && d.weekly_percent === undefined) d.weekly_percent = json.weekly_percent;
        return d;
    }

    function transformClaudeBilling(url, json) {
        if (!json || typeof json !== 'object') return {};
        var d = {};
        if (json.rate_limit_tier) {
            d.plan = json.rate_limit_tier.replace(/_/g, ' ').replace(/\b\w/g, function(c) { return c.toUpperCase(); });
        }
        if (json.amount !== undefined && json.currency) {
            d.balance_usd = parseFloat(json.amount) / 100;
        }
        if (json.amount !== undefined && json.resets_at && !json.currency) {
            d.this_month_usd = parseFloat(json.amount) / 100;
            var dt = new Date(json.resets_at);
            d.next_billing = dt.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' });
        }
        if (json.invoices && Array.isArray(json.invoices) && json.invoices.length > 0) {
            var usageInv = json.invoices.find(function(inv) { return inv.type === 'usage_invoice'; });
            if (usageInv && usageInv.amount !== undefined) {
                d.this_month_usd = parseFloat(usageInv.amount) / 100;
            }
        }
        if (json.plan) {
            if (typeof json.plan === 'object' && json.plan.name && !d.plan) d.plan = json.plan.name;
            else if (typeof json.plan === 'string' && !d.plan) d.plan = json.plan;
            if (typeof json.plan === 'object' && json.plan.amount) d.monthly_usd = parseFloat(json.plan.amount) || 0;
        }
        if (json.credit_balance !== undefined && d.balance_usd === undefined) d.balance_usd = parseFloat(json.credit_balance) || 0;
        if (json.spend_limit !== undefined) d.spend_limit_usd = parseFloat(json.spend_limit) || 0;
        return d;
    }

    function transformGitHubCopilot(url, json) {
        var d = {};
        if (json.userPremiumRequestEntitlement !== undefined) {
            d.included_total = parseFloat(json.userPremiumRequestEntitlement) || 0;
        }
        if (json.discountQuantity !== undefined) {
            d.included_consumed = parseFloat(json.discountQuantity) || 0;
        }
        if (json.netBilledAmount !== undefined) {
            d.billed_usd = parseFloat(json.netBilledAmount) || 0;
        }
        if (d.included_consumed !== undefined && d.included_total > 0) {
            d.included_percent = Math.round(d.included_consumed / d.included_total * 1000) / 10;
        }
        if (json.days_until_reset !== undefined) d.resets_in_days = parseInt(json.days_until_reset) || 0;
        if (json.next_billing_date) d.next_billing = json.next_billing_date;
        if (json.billing_cycle_end) {
            var dt = new Date(json.billing_cycle_end);
            d.resets_in_days = Math.max(0, Math.ceil((dt - new Date()) / 86400000));
            d.next_billing = dt.toLocaleDateString('zh-TW');
        }
        return d;
    }

    // ── Fetch/XHR Interceptor ────────────────────────

    var RULES = {
        openai_billing:  { hostMatch: 'platform.openai.com', transform: transformOpenAI },
        claude_usage:    { hostMatch: 'claude.ai',           transform: transformClaudeUsage },
        claude_billing:  { hostMatch: 'platform.claude.com', transform: transformClaudeBilling },
        github_copilot:  { hostMatch: 'github.com',          transform: transformGitHubCopilot },
    };

    var rule = RULES[PAGE.key];

    if (rule) {
        // Intercept fetch
        var origFetch = window.fetch;
        window.fetch = function () {
            return origFetch.apply(this, arguments).then(function (resp) {
                try {
                    var url = (typeof arguments[0] === 'string') ? arguments[0] : (arguments[0] && arguments[0].url) || '';
                    if (resp.ok && resp.headers.get('content-type') && resp.headers.get('content-type').indexOf('json') >= 0) {
                        resp.clone().json().then(function (json) {
                            var fields = rule.transform(url, json);
                            if (fields && Object.keys(fields).length > 0) {
                                merge(PAGE.key, fields);
                            }
                        }).catch(function () {});
                    }
                } catch (e) {}
                return resp;
            });
        };

        // Intercept XHR
        var origOpen = XMLHttpRequest.prototype.open;
        var origSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function (method, url) {
            this._aimonUrl = url;
            return origOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function () {
            var xhr = this;
            xhr.addEventListener('load', function () {
                try {
                    if (xhr.status >= 200 && xhr.status < 300) {
                        var ct = xhr.getResponseHeader('content-type') || '';
                        if (ct.indexOf('json') >= 0) {
                            var json = JSON.parse(xhr.responseText);
                            var fields = rule.transform(xhr._aimonUrl || '', json);
                            if (fields && Object.keys(fields).length > 0) {
                                merge(PAGE.key, fields);
                            }
                        }
                    }
                } catch (e) {}
            });
            return origSend.apply(this, arguments);
        };
    }

    // ── OpenRouter DOM parsing ───────────────────────

    if (PAGE.key === 'openrouter') {
        function parseOpenRouterCredits() {
            var fields = {};
            // Primary: aria-label on the card div (animated counter stores value here)
            // e.g. aria-label="Remaining credits: 39.486"
            var cards = document.querySelectorAll('[aria-label*="credit" i], [aria-label*="balance" i]');
            for (var i = 0; i < cards.length; i++) {
                var label = cards[i].getAttribute('aria-label') || '';
                var m = label.match(/([\d,]+\.?\d*)/);
                if (m) {
                    var val = parseFloat(m[1].replace(/,/g, ''));
                    if (isFinite(val) && val >= 0 && val < 100000) {
                        fields.balance_usd = val;
                        break;
                    }
                }
            }
            return fields;
        }

        function parseOpenRouterActivity() {
            var fields = {};
            // Try broad selectors for activity stats
            var containers = document.querySelectorAll('[class*="text-sm"], span.text-sm, p.text-sm, label');
            for (var j = 0; j < containers.length; j++) {
                var label = (containers[j].textContent || '').trim();
                var parent = containers[j].parentElement;
                if (!parent) continue;
                var valueCandidates = parent.querySelectorAll('[class*="text-2xl"], [class*="text-3xl"], [class*="text-xl"]');
                if (valueCandidates.length === 0) continue;
                var valueText = (valueCandidates[0].textContent || '').trim();
                if (/^Spend$/i.test(label)) {
                    var m = valueText.match(/([\d.]+)/);
                    if (m) fields.month_spend_usd = parseFloat(m[1]);
                } else if (/^Requests?$/i.test(label)) {
                    var n = parseFloat(valueText.replace(/[,K]/g, ''));
                    if (isFinite(n)) fields.month_requests = Math.round(n);
                } else if (/^Tokens?$/i.test(label)) {
                    var t = parseFloat(valueText.replace(/[,K]/g, ''));
                    if (isFinite(t)) fields.month_tokens = Math.round(t);
                }
            }
            return fields;
        }

        function parseOpenRouterDOM() {
            var path = location.pathname;
            var fields = {};
            if (path.indexOf('/settings/credits') === 0) {
                fields = parseOpenRouterCredits();
            } else if (path.indexOf('/activity') === 0) {
                fields = parseOpenRouterActivity();
            }
            if (Object.keys(fields).length > 0) {
                merge('openrouter', fields);
                return true;
            }
            return false;
        }

        // Try immediately, then with observer, then give up
        var creditsPage = location.pathname.indexOf('/settings/credits') === 0;
        setTimeout(function () {
            if (!parseOpenRouterDOM()) {
                var obs = new MutationObserver(function () {
                    if (parseOpenRouterDOM()) {
                        obs.disconnect();
                        if (creditsPage) setTimeout(function () { window.location.href = '/activity'; }, 3000);
                    }
                });
                obs.observe(document.body || document.documentElement, { childList: true, subtree: true });
                // After 15s give up and navigate anyway
                setTimeout(function () {
                    obs.disconnect();
                    parseOpenRouterDOM(); // last attempt
                    if (creditsPage) {
                        merge('openrouter', { parse_error: 'Unable to parse OpenRouter balance' });
                        window.location.href = '/activity';
                    }
                }, 15000);
            } else {
                // Parsed successfully, navigate to activity after delay
                if (creditsPage) setTimeout(function () { window.location.href = '/activity'; }, 3000);
            }
        }, 2000);
    }

    // ── GitHub Copilot DOM fallback ──────────────────

    if (PAGE.key === 'github_copilot') {
        setTimeout(function () {
            var container = document.getElementById('copilot-overages-usage');
            if (!container) return;
            var progressItem = container.querySelector('.Progress-item');
            if (progressItem) {
                var m = (progressItem.style.width || '').match(/([\d.]+)%/);
                if (m) {
                    var pct = parseFloat(m[1]);
                    merge('github_copilot', {
                        included_percent: Math.round(pct * 10) / 10,
                        included_total: 1500,
                        included_consumed: Math.round(pct / 100 * 1500 * 10) / 10,
                    });
                }
            }
        }, 3000);
    }

})();
