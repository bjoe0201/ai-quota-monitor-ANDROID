# Changelog

## v2.1 (2026-07-21)

### Features — Claude.ai Fable 週限額

- **Claude.ai 卡片新增 Fable 週限額進度條** — Anthropic 於 usage API 新增 `limits` 陣列，將每週額度拆分為「全部模型」（`weekly_all`）與單一模型 scoped 限額（`weekly_scoped`，例如 Fable）。卡片現在於「每週限額（全部模型）」下方額外顯示 Fable 週用量與重設時間
- **動態模型名稱** — Fable 進度條標籤取自 API 回傳的 `scope.model.display_name`，未來若 scoped 模型更名或替換可自動跟進
- **前向相容 `limits` 解析** — 注入腳本改為在頂層 `five_hour` / `seven_day` 之外，另行解析 `limits` 陣列補齊 session / weekly 並新增 scoped 模型；舊格式仍優先，沒有 scoped 限額的帳號不會顯示多餘進度條
- **PC 端 Tampermonkey 腳本同步** — `scripts/ai-monitor-client-v4.4.js` 加入相同的 `limits` 解析邏輯，PC 推送與 App 內 WebView 行為一致

### Documentation

- 更新 README 的 Claude.ai 使用教學（Fable / 全部模型週限額說明）
- 更新 CLAUDE.md / AGENTS.md 的版本資訊與 Claude.ai 資料來源說明

---

## v2.0 (2026-07-17)

### Features — ChatGPT 額度監控

- **新增 ChatGPT Usage Card** — 以單一進度條顯示每週剩餘額度，並顯示完整的下次重設日期與時間
- **新增 ChatGPT 服務帳號登入** — 可從 App 的「設定 → 服務帳號」登入 ChatGPT，並重用持久化 Cookie 進行背景更新
- **新增 ChatGPT Usage DOM 擷取器** — 從 `chatgpt.com/#settings/Usage` 擷取每週額度與可用點數
- **既有安裝自動升級設定** — 啟動時自動將新服務合併至既有 `config.json`，保留原有服務設定與排列順序
- **背景服務依 Card 順序啟動** — WebView Collector 依使用者設定的 Card 排列順序啟動，停用的 Card 不再建立背景 WebView

### Documentation

- 更新 README 的監控服務清單與 ChatGPT 使用教學
- 更新 CLAUDE.md / AGENTS.md 的架構、服務清單與版本資訊

---

## v1.9 (2026-06-05)

### Bug Fixes — Claude.ai 新路由相容

- **修復 Claude.ai 用量資料無法載入** — Anthropic 將 Usage 頁面路由從 `/settings/usage` 遷移至 `/new#settings/usage`（SPA hash routing），更新 WebView 載入 URL 以跟進新路由
- **新增 `isClaudeOnUsagePage()` 守衛** — Android WebView 注入腳本（`ai-monitor-android.js`）新增 SPA hash 檢查，避免在 claude.ai 聊天頁面攔截 streaming fetch，防止 ERR_QUIC_PROTOCOL_ERROR
- **Tampermonkey 腳本升至 v4.4.3** — 新增 `@match https://claude.ai/new*`，`isOnExpectedPage()` 加入 hash 判斷，SPA 導航偵測補建 UI；fetch hook 加入早期退出守衛

---

## v1.8 (2026-05-27)

### Features — GitHub Copilot 預算監控

- **新增 "All Premium Request SKUs" 預算 BAR** — GitHub Copilot 卡片現在同時顯示：
  - 原有的 Premium Requests 免費額度進度條（1500 次）
  - 新的付費預算進度條（`billing/budgets` 頁面）：顯示已使用金額 / 預算上限（如 $0.21 / $50.00）
- **同時開啟兩個 WebView** — GitHub Copilot 同步載入兩頁：
  - `github.com/settings/copilot/features`（免費額度）
  - `github.com/settings/billing/budgets`（付費預算）
  - 兩者資料自動合併至同一張卡片
- **Tampermonkey 腳本升至 v4.4.2** — 新增 `@match https://github.com/settings/billing/budgets*`，支援從 PC 瀏覽器推送預算資料

### UI

- **StatusBar 顯示版本號** — 底部狀態列中間新增版本號（如 `v1.8`），方便確認 App 已更新

---

## v1.7 (2026-05-17)

### Bug Fixes — Google SSO Login for Claude API
- **Fix Google OAuth blocked in WebView** — suppress `X-Requested-With` header via
  AndroidX WebKit `setRequestedWithHeaderOriginAllowList(emptySet())` so Google does not
  detect the embedded WebView and block the OAuth flow.
- **Fix premature Google SSO banner** — only trigger the "Google 登入不支援" banner when
  the WebView reaches the actual dead-end `/gsi/` endpoint, not during the normal
  `/v3/signin/` or `/signin/oauth/` OAuth steps that work fine in WebView.
- **Auto-redirect after /gsi/ failure** — when the GSI callback fails, navigate back to
  the login page so the user can use the email login option instead.
- **Banner no longer hides WebView** — the warning banner now appears above the WebView
  instead of replacing it, allowing users to scroll down and use alternative login methods.
- **Updated banner guidance** — banner now says "請往下捲動，改用 Email 登入" to guide
  users toward the email login that works in WebView.

### Improvements
- Update Chrome User-Agent from v125 to v136 for better compatibility with service pages.
- Add "在外部瀏覽器開啟" button on the SSO blocked banner as a fallback.
- Clear the SSO banner automatically if the user successfully logs in via email.

---

## v1.6 (2026-05-15)

### Features

- Add `scripts/ai-monitor-client-v4.4.js` (latest upstream Tampermonkey HTTP push client)
- Keep Android WebView script (`app/src/main/assets/ai-monitor-android.js`) and Tampermonkey script separated by usage

### Maintenance

- Ignore IDE metadata by default via `.idea/` in `.gitignore`
- Remove tracked `.idea` files from repository index to avoid uploading editor-local state

---

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
