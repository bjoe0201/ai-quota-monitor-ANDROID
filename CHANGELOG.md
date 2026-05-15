# Changelog

## v1.5 (2026-05-15)

### Features — 服務帳號設定頁新功能

- **開關顯示服務** — 每個服務帳號列加入 Switch，可關閉/開啟儀表板上對應的服務卡片。
  關閉的服務不會顯示在儀表板上（背景 WebView 也停止重載）。
- **調整顯示順序** — 每個服務帳號列加入 ▲/▼ 按鈕，可調整卡片在儀表板上的顯示次序。
  順序儲存於 `config.json`，重啟 App 後保留。
- **WebView 更新間隔設定** — 設定頁新增「自動更新設定」區塊，可透過 −/+ 按鈕調整
  WebView 自動重新整理間隔（1 ～ 10 分鐘），預設 5 分鐘。
- 提示文字說明三項功能的操作方式

### Architecture
- `DashboardConfig` 新增 `serviceOrder: List<String>` 欄位，用於儲存使用者自訂的服務顯示順序。
- 新增 `effectiveServiceOrder()` / `enabledServiceKeys()` 擴充函式，供 Dashboard 動態渲染卡片。
- `DashboardScreen` 改用動態順序渲染（`ServiceCardGrid`），所有 Layout (A/B/C/D) 均支援自訂順序與開關。
- `DashboardViewModel` 新增 `reorderService()` / `setServiceEnabled()` / `setAutoRefreshMinutes()` 方法。

---

## v1.4 (2026-05-15)

### Bug Fixes — Auto-Update Pipeline
- **Fix auto-update not working** — replace `StateFlow.collect` in `observeDataStore()` with
  `SharedFlow` (`dataUpdateFlow`) that fires on every `putData()` call.  
  `SharedFlow` has no equality-check conflation, so every incoming data payload — whether from
  WebView JS bridge or HTTP server (Tampermonkey) — immediately rebuilds the UI without requiring
  a manual refresh.
- **Fix JS injection timing** — inject the monitoring script in both `onPageStarted` (early,
  so hooks are set before the SPA's own JS fetches data) AND `onPageFinished` (safety net).
  Previously injecting only at `onPageFinished` could miss API calls that happened concurrently.
- **Fix `startPolling()` initial delay** — polling loop no longer delays before the first cycle;
  it waits `autoRefreshMinutes` AFTER a refresh, ensuring the interval is purely between reloads.
- **Add `onResume` refresh** — `MainActivity.onResume()` calls `viewModel.refreshFromStore()`
  to rebuild UI from current DataStore whenever the app returns to foreground.
- **Guard observer coroutine** — wrap `rebuildResults()` in try/catch inside the observer so an
  unexpected exception never silently kills the background observer coroutine.

---

## v1.3 (2026-05-15)

### Features
- Add Dark / Light theme toggle (淺色模式) in Settings
- New app icon: multi-color gauge meter with monochrome adaptive-icon layer

### UI
- README revamp with full screenshot gallery (7 screenshots)
- Add PICS directory with annotated screenshots for documentation

### Bug Fixes
- versionCode / versionName now properly synced with release tag

---

## v1.2 (2026-05-15)

### Redesign
- New app icon: multi-color gauge meter representing the 5 AI service monitors
  - Each arc segment uses the service's accent color (green/blue/violet/purple/indigo)
  - Gauge needle + AI sparkle accent on dark background
  - Dedicated monochrome layer for Android 13+ themed icons
  - Removed default Android robot webp fallbacks (minSdk 31 always uses adaptive icon)

## v1.1 (2026-05-15)

### Bug Fixes
- Fix Google SSO detection — only block on actual dead-end `/gsi/` URL, not normal OAuth flow
- Fix Claude API URLs — update from `console.anthropic.com` to `platform.claude.com`
- Fix Claude API loginUrl — use `/login` path for proper login page
- Fix OpenRouter balance parsing — use `aria-label` attribute instead of animated counter text
- Fix OpenRouter incomplete data — auto-navigate from `/settings/credits` to `/activity` for full data

### Features
- Persist card collapse state across app restarts (stored in `config.json`)
- Swap Claude API and OpenRouter card positions (OpenRouter beside OpenAI, Claude API at bottom)

### Infrastructure
- Bind HTTP server to `0.0.0.0` (was `127.0.0.1`) so PC browsers on same network can POST data
- Improve OpenRouter DOM parsing with MutationObserver retry and timeout fallback

## v1.0 (2026-05-15)

### Initial Release
- Full Android dashboard with 5 AI service monitors
- Home Assistant-style card layout (configurable sections, columns, spans)
- Flip-clock card ported from desktop version
- WebView + JS injection data pipeline
- HTTP server (port 7890) for Tampermonkey data
- Dark theme (Linear/Raycast style)
- Full-screen WebView login with cookie persistence
- Google SSO block detection with warning banner
- Foreground Service to keep HTTP server alive
- Desktop Chrome UA to avoid mobile redirects
