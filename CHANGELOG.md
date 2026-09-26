# Changelog

## v2.3 (2026-09-27)

### Fixes — 長時間執行後整個 App 關閉

- **renderer 死亡不再拖垮整個 App** — 背景 WebView 共用的 renderer 進程記憶體耗盡而被系統終止時，App 過去會被 WebView 層一起打死（放置 14–61 分鐘後憑空消失）。現在 `onRenderProcessGone()` 會接手並回傳 `true`，App 存活、該頁面稍後重新載入
- **一次只載入一個服務頁面，收完即拆** — 原本 6 個服務頁面全部常駐在同一個 renderer，總量會衝到 1.5 GB；改為依序走訪，每個頁面收到資料後立即釋放（先 `about:blank` 再 `destroy`），讓 renderer 丟掉 document、JS heap 與計時器
- **輪詢改為一輪跑完才計時** — 舊版固定每 5 分鐘無條件重載，上一輪未結束就會疊加頁面
- **背景頁面不再載入圖片** — 這些頁面只被解析、從不繪製，解碼後的圖片是純浪費
- **登入狀態誤判防護** — 需連續 2 次偵測到登入頁才判定為登出，避免 token refresh、bot 檢查或慢速 SPA 中間態造成誤判（誤判會停掉該服務的背景收集直到手動重新登入）
- **登入畫面 renderer 死亡後自動重建** — 不再留下無法互動的白畫面
- **修正重複的 App 實例** — MainActivity 改為 `launchMode="singleTask"`。先前未指定 launchMode，App 已執行時再次啟動（安裝後點「開啟」、部分 launcher intent）會疊出第二個 Activity，連帶產生第二個 ViewModel 與第二條背景收集迴圈，頁面數與記憶體直接翻倍
- **登入畫面離開後釋放 WebView** — 先前每進入一次服務登入畫面，就會留下一個活著的頁面在 renderer 裡直到 App 重啟。實測進入登入畫面時 renderer 從約 700 MB 跳到 1,413 MB
- **修正 OpenRouter 的登入偵測** — 登入頁比對清單只有 `/signin`，比對不到 OpenRouter 實際使用的 `/sign-in`，導致 session 過期時卡片不會提示重新登入，而且每輪白燒 90 秒逾時

實機量測（Redmi 平板、4 GB RAM、3 輪完整收集週期、44 個樣本）：同時存活的頁面數從 5–6 降到 **1**（從未出現 2），renderer 峰值從 1.5–1.68 GB 降到 707–1,022 MB，renderer 死亡從每 6–7 分鐘一次降到量測期間 **0 次**，裝置可用記憶體從 408 MB 回到 1.0–1.4 GB，系統也不再為了騰出記憶體而終止其他 app。

> 已知限制：renderer 記憶體仍會每輪累積約 **190 MB** 且五輪內未收斂，約 23 分鐘撞上 1.5 GB 後被系統終止（原本是每 6–7 分鐘）。App 能存活該次死亡、資料照常收集，使用者端表現正常，但累積本身尚未解決 — 目前是**延後**而非消除 OOM。後續追查方向與完整證據見 [`docs/01-webview-renderer-oom-crash.md`](docs/01-webview-renderer-oom-crash.md)，進行中的實驗與交接見 [`DEVELOP.md`](DEVELOP.md)。

### Features

- **主畫面標題顯示版號** — A/B/C/D 四種 layout 的標題旁都顯示目前版本
- **修正三處硬編碼版號** — 標題列、底部狀態列（原本寫死 `v1.8`）與設定→關於（原本寫死 `2.0`）改為讀取 `BuildConfig`，不會再與實際 build 脫節

### Docs

- 新增 `docs/01-webview-renderer-oom-crash.md` — 完整根因分析、證據、可重現的驗證步驟與診斷指令
- 新增 `docs/README.md` — 技術文件索引與撰寫規範

### Testing

- 新增 15 個 JVM 單元測試：`RendererRecoveryPolicyTest`（錯開排程、退避、退避歸零）、`SessionExpiryGuardTest`（登出證據累積）、`CollectionPlanTest`（收集順序、停用／未登入／無 URL 的排除、GitHub 第二頁面）


## v2.2 (2026-09-09)

### Features — ChatGPT Pro 與使用量限制重設

- **支援 ChatGPT Pro 週額度重設倒數** — 新增「6 天 6 小時 後重設」等中文相對時間格式，同時保留 ChatGPT Plus 的完整日期與英文重設時間解析方式
- **顯示可用的使用量限制重設** — 從 ChatGPT Usage 頁面的「使用量限制重設」區塊擷取到期資訊；有幾筆可用重置，Card 就顯示幾筆
- **重置到期日框** — 每筆可用重置以獨立紫色外框顯示到期文字，多筆項目會自動編號
- **支援分段渲染** — 額度百分比、週重設時間與使用量限制重設區塊尚未全部載入時持續觀察 DOM，避免提早停止造成資料遺漏

### Testing

- 新增 ChatGPT Plus、Pro、Pro 分段渲染、零筆重置與多筆重置解析 fixture
- Android JVM 單元測試與 JavaScript 注入腳本語法檢查通過

### Documentation

- 更新 README 的 ChatGPT 使用教學與方案相容說明
- 更新 CLAUDE.md / AGENTS.md 的版本與 ChatGPT 資料格式說明

---

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
