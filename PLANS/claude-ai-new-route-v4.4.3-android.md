# Plan: Claude.ai 新路由相容修改 (Android v4.4.3 移植)

## Context
Anthropic 將 claude.ai Usage 頁面路由從 `/settings/usage` 遷移至 `/new#settings/usage`（SPA hash routing）。
Windows 版 v4.4.3 已修復，本次任務將相同修改移植至 Android 版本。

## Files to Modify (3 files)
1. `app/src/main/java/.../data/model/DashboardConfig.kt` (line 84) — URL 更新
2. `app/src/main/assets/ai-monitor-android.js` — fetch/XHR 早期退出守衛
3. `scripts/ai-monitor-client-v4.4.js` — Tampermonkey 腳本 v4.4.3 修訂

---

## Change 1 — DashboardConfig.kt
**File:** `app/src/main/java/com/example/ai_quota_monitor_android/data/model/DashboardConfig.kt`
**Line 84:**
```kotlin
// 修改前
url = "https://claude.ai/settings/usage",

// 修改後
url = "https://claude.ai/new#settings/usage",
```
WebViewDataCollector.loadService() 會將此 URL 傳給 WebView.loadUrl()。

---

## Change 2 — ai-monitor-android.js
**File:** `app/src/main/assets/ai-monitor-android.js`

在 RULES 區塊之後、fetch hook 之前，新增 helper 函式：

```js
// ── Claude Usage SPA hash check ─────────────────
function isClaudeOnUsagePage() {
    if (location.pathname === '/settings/usage') return true;
    if (location.pathname === '/new') {
        return location.hash.replace(/^#/, '').startsWith('settings/usage');
    }
    return false;
}
```

在 fetch hook 開頭加入早期退出：
```js
window.fetch = function () {
    // 早期退出：不在 Claude Usage 目標頁面時完全透通
    if (PAGE.key === 'claude_usage' && !isClaudeOnUsagePage())
        return origFetch.apply(this, arguments);
    return origFetch.apply(this, arguments).then(function (resp) { ... });
};
```

在 XHR load handler 第一行加入早期退出：
```js
xhr.addEventListener('load', function () {
    if (PAGE.key === 'claude_usage' && !isClaudeOnUsagePage()) return;
    try { ... }
});
```

**原因**：Android WebView 背景載入 `/new#settings/usage` 時，`onPageStarted` 回傳 URL 不含 fragment，腳本在 `/new` 路徑就被注入。若不加守衛，在聊天頁面的 fetch 攔截會嘗試 clone streaming response，可能引發 ERR_QUIC_PROTOCOL_ERROR。

---

## Change 3 — scripts/ai-monitor-client-v4.4.js

### 3a. 版本號與 @updated
```
// @version      4.4.3
// @updated      2026-06-05 — Claude.ai 新路由 /new#settings/usage 相容（v4.4.3）
```

### 3b. @match 新增 /new*
```
// @match        https://claude.ai/settings/usage*
// @match        https://claude.ai/new*
```

### 3c. PAGE_MAP['claude.ai'] 更新
```js
'claude.ai': {
    key: 'claude_usage',
    label: 'Claude Usage',
    expectedPath: ['/settings/usage', '/new'],
    expectedHash: 'settings/usage',
    refreshInterval: 1 * 60 * 1000,
},
```

### 3d. isOnExpectedPage() 加入 hash 判斷
```js
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
```

### 3e. installFetchHook() 加入早期退出
```js
win.fetch = function (...args) {
    if (!isOnExpectedPage()) return _realFetch.apply(this, args);
    let url;
    try { ... }
}
```

### 3f. setupSPADetection() 補建 UI
```js
if (isOnExpectedPage() && PAGE.key === 'claude_usage' && !_dot) {
    buildUI();
    setupPeriodicRefresh();
    setupTimeoutWarning();
}
```

---

## Verification
1. App 重新啟動後，背景 WebView 以 `https://claude.ai/new#settings/usage` 載入頁面
2. Claude.ai 卡片顯示用量資料（session_percent、weekly_percent 等），不再顯示「等待瀏覽器連線」
3. 若已登入 claude.ai（cookie 已存），資料應在 ~5 秒內到達
4. 若舊路由 `/settings/usage` 仍可用，也應正常運作（舊路由相容保留）
