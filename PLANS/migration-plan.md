# AI 額度監控 — Android 移植計畫

> 原始專案：Python 3.11 + tkinter 桌面小工具 + Tampermonkey 瀏覽器腳本
> 目標平台：Android (minSdk 31, targetSdk 36)
> 技術棧：Kotlin + Jetpack Compose + Material3

---

## 一、原始專案架構摘要

### 資料流

```
瀏覽器 (Tampermonkey JS)
    │
    │  POST /update (JSON)
    ▼
本地 HTTP 伺服器 (localhost:7890)
    │
    │  DATA_STORE (記憶體字典)
    ▼
桌面 UI (tkinter)
    ├── 翻頁時鐘 (FlipClock)
    ├── 5 張服務卡片 (CompactServiceCard)
    │     ├── Hero 數值 (大字)
    │     ├── 28-segment 進度條
    │     └── KV 鍵值對
    └── 系統匣 (pystray)
```

### 5 個監控服務

| 服務 | source_key | 資料來源頁面 |
|------|-----------|-------------|
| Claude.ai 用量 | `claude_usage` | claude.ai/settings/usage |
| GitHub Copilot | `github_copilot` | github.com/settings/billing |
| OpenAI 帳單 | `openai_billing` | platform.openai.com/billing |
| Claude API 帳單 | `claude_billing` | platform.claude.com/billing |
| OpenRouter | `openrouter` | openrouter.ai/settings/credits |

### JS 腳本機制

Tampermonkey userscript (`ai-monitor-client-v4.4.js`) 在各個 AI 服務網頁上：
1. Hook `fetch` 和 `XMLHttpRequest` 攔截 API 回應
2. 透過 transform 函式將回應轉成標準欄位
3. 用 `GM_xmlhttpRequest` POST 至 `localhost:7890/update`
4. 輪詢 `/poll?seq=N` 接收桌面程式的「請重新整理」指令

---

## 二、Android 移植架構設計

### 2.1 整體架構

```
┌─────────────────────────────────────────────────────┐
│                   Android App                        │
│                                                      │
│  ┌─────────────┐   ┌──────────────────────────────┐ │
│  │  WebView     │   │  Compose UI                  │ │
│  │  (背景載入)  │──▶│  Dashboard (卡片式佈局)      │ │
│  │  + JS 注入   │   │  ├─ 翻頁時鐘 Card           │ │
│  │              │   │  ├─ Claude.ai Card           │ │
│  └──────┬───────┘   │  ├─ Copilot Card             │ │
│         │           │  ├─ OpenAI Card              │ │
│  ┌──────▼───────┐   │  ├─ Claude API Card          │ │
│  │  DataStore    │   │  └─ OpenRouter Card          │ │
│  │  (Repository) │──▶│                              │ │
│  │              │   └──────────────────────────────┘ │
│  └──────┬───────┘                                    │
│         │           ┌──────────────────────────────┐ │
│  ┌──────▼───────┐   │  設定 (JSON Config)          │ │
│  │  HTTP Server  │   │  ├─ 卡片佈局 / 區域         │ │
│  │  (NanoHTTPD)  │   │  ├─ 服務開關 / 名稱         │ │
│  │  port 7890    │   │  └─ 顯示偏好                │ │
│  └───────────────┘   └──────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
          ▲
          │ POST /update (同一 WiFi 內)
          │
    PC 端瀏覽器 (Tampermonkey)
```

### 2.2 模組分層

```
app/src/main/java/com/example/ai_quota_monitor_android/
├── MainActivity.kt              # 唯一 Activity，Compose Host
├── QuotaMonitorApp.kt           # Application 類，初始化 DI
│
├── data/
│   ├── model/
│   │   ├── ServiceResult.kt     # 對應 Python ServiceResult
│   │   ├── ServiceConfig.kt     # 各服務設定
│   │   └── DashboardConfig.kt   # 卡片佈局設定 (區域、位置、大小)
│   ├── repository/
│   │   ├── DataStoreRepository.kt  # 記憶體資料庫 (對應 DATA_STORE)
│   │   └── ConfigRepository.kt     # JSON 設定讀寫
│   └── server/
│       └── LocalHttpServer.kt      # NanoHTTPD 伺服器 (對應 local_server.py)
│
├── service/
│   ├── BaseService.kt           # 對應 base.py
│   ├── BrowserDataService.kt    # 對應 browser_data.py (5 個服務類別)
│   └── WebViewDataCollector.kt  # WebView JS 注入管理
│
├── ui/
│   ├── theme/
│   │   ├── Theme.kt             # Catppuccin / Linear 深色主題
│   │   ├── Color.kt             # 對應 COLORS 字典
│   │   └── Type.kt              # 字型設定
│   ├── dashboard/
│   │   ├── DashboardScreen.kt   # 主畫面 (卡片網格佈局)
│   │   ├── DashboardViewModel.kt
│   │   └── CardGridLayout.kt    # 可設定的卡片網格系統
│   ├── cards/
│   │   ├── ServiceCard.kt       # 通用服務卡片 Composable
│   │   ├── ClockCard.kt         # 翻頁時鐘卡片
│   │   ├── HeroSection.kt       # Hero 大數值區塊
│   │   ├── ProgressBarSegmented.kt  # 28-segment 進度條
│   │   └── KvRow.kt             # 鍵值對列
│   ├── settings/
│   │   ├── SettingsScreen.kt    # 設定頁面
│   │   ├── CardLayoutEditor.kt  # 卡片佈局編輯器
│   │   └── ServiceLoginScreen.kt # 全螢幕 WebView 登入頁（使用者手動登入各服務）
│   └── components/
│       └── StatusBar.kt         # 底部狀態列
│
└── util/
    ├── FormatUtils.kt           # format_tokens 等工具函式
    └── ColorUtils.kt            # _mix_hex、header_tint 等
```

---

## 三、核心議題與解決方案

### 3.1 瀏覽器 JS 注入的替代方案

原始方案依賴 Tampermonkey + Chrome 瀏覽器腳本。Android 上有三種替代方案：

#### 方案 A：Android WebView + JS 注入（推薦）

```
使用者首次手動登入各服務 (WebView 全螢幕)
    │
    │  CookieManager 持久化 Cookie / Session
    ▼
後續自動載入 (背景 WebView)
    │
    │  evaluateJavascript() 注入 JS
    │  @JavascriptInterface 回傳資料
    ▼
DataStoreRepository
```

> **重要前提：** 所有 AI 服務頁面（OpenAI、Claude、GitHub、OpenRouter）都需要使用者認證。
> 使用者必須在 App 內的 WebView 中**手動登入**各服務，App 負責**持久保存登入憑證**，
> 後續自動刷新時直接使用已保存的 Cookie / Session，無需重複登入。

**做法：**
- App 在背景使用 `WebView` 載入各 AI 服務頁面
- 使用 `WebViewClient.onPageFinished()` 回呼時注入修改版的 JS 腳本
- JS 透過 `@JavascriptInterface` 標記的 Kotlin 方法，直接將資料傳回 App
- 不需要 HTTP POST，資料直接在 App 內流通

**優點：**
- 完全內建，不需外部瀏覽器
- Cookie 持久化後，使用者只需登入一次
- 可背景自動刷新
- 不需安裝 Tampermonkey

**限制：**
- WebView 不支援瀏覽器擴充套件，但 JS 注入可替代
- 部分網站可能偵測非標準瀏覽器環境（需設定合適的 User-Agent）
- Cookie / Session 有過期時間，過期後需重新登入

**認證流程設計：**

```
┌──────────────────────────────────────────────────────┐
│  設定頁 — 服務帳號管理                                 │
│                                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │  Claude.ai              [已登入 ✓]  [重新登入]  │ │
│  │  GitHub Copilot         [未登入 ✕]  [前往登入]  │ │
│  │  OpenAI                 [已登入 ✓]  [重新登入]  │ │
│  │  Claude API             [已登入 ✓]  [重新登入]  │ │
│  │  OpenRouter             [已過期 ⚠]  [重新登入]  │ │
│  └─────────────────────────────────────────────────┘ │
│                                                       │
│  點擊 [前往登入] / [重新登入] → 開啟全螢幕 WebView     │
│  使用者手動完成登入 → 偵測到登入成功 → 自動返回設定頁   │
└──────────────────────────────────────────────────────┘
```

每個服務的登入狀態存入設定檔：

```json
{
  "auth_status": {
    "browser_claude_usage": {
      "logged_in": true,
      "last_login": "2026-05-15T10:30:00",
      "last_success": "2026-05-15T14:22:00",
      "cookie_domain": "claude.ai"
    }
  }
}
```

**Cookie / Session 持久化實作：**

```kotlin
class WebViewDataCollector(context: Context) {
    private val webViews = mutableMapOf<String, WebView>()
    private val cookieManager = CookieManager.getInstance()

    init {
        // 啟用 Cookie 持久化 — 登入憑證會保存到磁碟
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(/* webView */, true)
    }

    /**
     * 開啟全螢幕登入 WebView — 使用者手動登入
     * 登入完成後呼叫 flush() 將 Cookie 寫入磁碟
     */
    fun openLoginWebView(serviceKey: String, url: String): WebView {
        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CHROME_MOBILE_UA  // 模擬正常 Chrome
            settings.databaseEnabled = true
            // Cookie 會自動儲存，登入完成後 flush 到磁碟
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    cookieManager.flush()  // 持久化 Cookie
                    // 偵測是否已登入成功（檢查 URL 或頁面內容）
                    checkLoginStatus(serviceKey, url)
                }
            }
            loadUrl(url)
        }
    }

    /**
     * 背景資料抓取 — 使用已保存的 Cookie 自動載入
     * 不需要使用者介入
     */
    fun loadService(serviceKey: String, url: String) {
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = CHROME_MOBILE_UA
            addJavascriptInterface(
                DataBridge(serviceKey), "AiMonitor"
            )
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // 檢查是否被導向到登入頁（Cookie 過期）
                    if (isLoginPage(serviceKey, url)) {
                        notifySessionExpired(serviceKey)
                        return
                    }
                    // Cookie 有效，注入 JS 抓取資料
                    view.evaluateJavascript(
                        getInjectionScript(serviceKey), null
                    )
                }
            }
        }
        wv.loadUrl(url)
        webViews[serviceKey] = wv
    }

    /**
     * 偵測各服務的登入頁 URL，判斷 Session 是否過期
     */
    private fun isLoginPage(serviceKey: String, currentUrl: String): Boolean {
        return when (serviceKey) {
            "browser_claude_usage" -> currentUrl.contains("/login")
            "browser_github_copilot" -> currentUrl.contains("/login") ||
                                        currentUrl.contains("/sessions")
            "browser_openai" -> currentUrl.contains("/auth/login")
            "browser_claude_billing" -> currentUrl.contains("/login")
            "browser_openrouter" -> currentUrl.contains("/auth")
            else -> false
        }
    }

    companion object {
        // 使用正常的 Chrome Mobile User-Agent，避免被網站拒絕
        const val CHROME_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }
}
```

**Session 過期處理流程：**

```
背景 WebView 載入服務頁面
    │
    ├── URL 正常 (非登入頁) → 注入 JS → 抓取資料 ✓
    │
    └── URL 被導向到登入頁 → Session 已過期
            │
            ├── 更新 auth_status 為 "expired"
            ├── 卡片顯示「Session 已過期，請重新登入」(橘色警告)
            └── 發送通知提醒使用者
```

#### 方案 B：保留 HTTP Server + PC 瀏覽器傳送

保持現有 Tampermonkey 腳本，但 Android App 啟動 HTTP 伺服器，PC 瀏覽器透過同一 WiFi 向手機推送。

**做法：**
- Android App 啟動 NanoHTTPD 伺服器
- 修改 JS 腳本的目標位址為手機 IP（或手機端顯示 QR Code）
- 資料流和原始版本完全一致

**優點：** 與原始版本一致，改動最小
**限制：** 仍需 PC 瀏覽器 + Tampermonkey，手機無法獨立運作

#### 最終決定：A + B 雙軌

- **預設使用方案 A**（WebView 內建），讓 App 可獨立運作
- **同時保留方案 B**（HTTP Server），讓已有 PC + Tampermonkey 的使用者也能推送資料到手機
- 兩條路徑的資料都匯入同一個 `DataStoreRepository`

### 3.2 Home Assistant 風格卡片系統

#### 佈局概念

仿 Home Assistant 的 Dashboard，畫面分為可設定的**區域 (Section)**，每個區域包含多張**卡片 (Card)**。

```
┌─────────────────────────────┐
│  [時鐘區域]                  │
│  ┌─────────────────────────┐│
│  │    FlipClock Card       ││
│  └─────────────────────────┘│
├─────────────────────────────┤
│  [主要監控]                  │
│  ┌────────────┬────────────┐│
│  │ Claude.ai  │ Copilot    ││
│  │   Card     │   Card     ││
│  └────────────┴────────────┘│
│  ┌────────────┬────────────┐│
│  │ OpenAI     │ Claude API ││
│  │   Card     │   Card     ││
│  └────────────┴────────────┘│
│  ┌─────────────────────────┐│
│  │    OpenRouter Card      ││
│  └─────────────────────────┘│
├─────────────────────────────┤
│  [狀態列]                    │
│  ● 就緒  ⟳ 重新整理  v1.0  │
└─────────────────────────────┘
```

#### 卡片設定結構

```json
{
  "sections": [
    {
      "id": "clock",
      "name": "時鐘",
      "columns": 1,
      "cards": [
        { "type": "clock", "id": "main_clock", "span": 1 }
      ]
    },
    {
      "id": "monitors",
      "name": "主要監控",
      "columns": 2,
      "cards": [
        { "type": "service", "id": "claude_usage", "service_key": "browser_claude_usage", "span": 1 },
        { "type": "service", "id": "github_copilot", "service_key": "browser_github_copilot", "span": 1 },
        { "type": "service", "id": "openai", "service_key": "browser_openai", "span": 1 },
        { "type": "service", "id": "claude_billing", "service_key": "browser_claude_billing", "span": 1 },
        { "type": "service", "id": "openrouter", "service_key": "browser_openrouter", "span": 2 }
      ]
    }
  ],
  "services": {
    "browser_claude_usage": {
      "enabled": true,
      "display_name": "Claude.ai",
      "source_key": "claude_usage",
      "url": "https://claude.ai/settings/usage"
    },
    "browser_github_copilot": {
      "enabled": true,
      "display_name": "GitHub Copilot",
      "source_key": "github_copilot",
      "url": "https://github.com/settings/billing/premium_requests_usage"
    },
    "browser_openai": {
      "enabled": true,
      "display_name": "OpenAI",
      "source_key": "openai_billing",
      "url": "https://platform.openai.com/settings/organization/billing/overview"
    },
    "browser_claude_billing": {
      "enabled": true,
      "display_name": "Claude API",
      "source_key": "claude_billing",
      "url": "https://platform.claude.com/settings/billing"
    },
    "browser_openrouter": {
      "enabled": true,
      "display_name": "OpenRouter",
      "source_key": "openrouter",
      "url": "https://openrouter.ai/settings/credits"
    }
  }
}
```

#### 卡片類型

| 卡片類型 | 對應元件 | 說明 |
|---------|---------|------|
| `clock` | ClockCard | 翻頁時鐘 + 日期 |
| `service` | ServiceCard | 通用服務監控卡片 (Hero + Bar + KV) |

使用者可在設定中：
- 調整 Section 的 `columns` 數 (1 或 2)
- 拖曳卡片調整順序
- 設定卡片的 `span` (佔幾個 column)
- 修改服務的 `display_name`
- 開關各服務

### 3.3 Compose 卡片元件對應

| Python 原始元件 | Android Compose 元件 | 說明 |
|----------------|---------------------|------|
| `CompactServiceCard` | `ServiceCard` | 可展開/收合的服務卡片 |
| `FlipCard` + `DigitalClock` | `ClockCard` | Canvas 繪製翻牌效果 |
| `ProgressBar` (28-segment) | `SegmentedProgressBar` | Canvas 繪製分段進度條 |
| `_add_hero()` | `HeroSection` | 大字數值 + 單位 + badge |
| `_add_row()` / `_add_pair_row()` | `KvRow` / `KvPairRow` | 鍵值對 |
| `_add_bar_row()` | `BarRow` | 標籤 + 百分比 + 進度條 + reset pill |

### 3.4 色彩主題

直接移植原始 COLORS 字典到 Compose Color 定義：

```kotlin
// Linear / Raycast 深色主題 (移植自 gui/widgets.py COLORS)
val Bg            = Color(0xFF0A0A0C)
val CardBg        = Color(0xFF111114)
val CardBorder    = Color(0xFF1F1F24)
val TextPrimary   = Color(0xFFFAFAFA)
val TextMuted     = Color(0xFFA1A1AA)
val TextDim       = Color(0xFF71717A)
val Success       = Color(0xFF34D399)
val Warning       = Color(0xFFFBBF24)
val Error         = Color(0xFFF87171)
val Info          = Color(0xFF60A5FA)
val Violet        = Color(0xFFA78BFA)
```

各服務的 accent 色（用於 header tint）：

```kotlin
val ServiceAccents = mapOf(
    "browser_openai"         to Color(0xFF60A5FA),
    "browser_claude_usage"   to Color(0xFFA78BFA),
    "browser_claude_billing" to Color(0xFFC084FC),
    "browser_github_copilot" to Color(0xFF34D399),
    "browser_openrouter"     to Color(0xFF818CF8),
)
```

---

## 四、實作步驟

### Phase 1：基礎架構（估計檔案 10 個）

1. **設定 Gradle 依賴**
   - 加入：NanoHTTPD, Kotlinx Serialization, ViewModel, Coroutines
   - `app/build.gradle.kts` 加入 `kotlinx-serialization` plugin

2. **建立 data model**
   - `ServiceResult.kt` — dataclass，對應 Python `ServiceResult`
   - `DashboardConfig.kt` — 卡片佈局設定 JSON 結構
   - `ServiceConfig.kt` — 各服務設定

3. **建立 Repository**
   - `DataStoreRepository.kt` — `StateFlow<Map<String, ServiceData>>`，記憶體內資料庫
   - `ConfigRepository.kt` — 讀寫 JSON 設定檔到 App 內部儲存

4. **建立 HTTP Server**
   - `LocalHttpServer.kt` — 移植 `local_server.py`，端點 `/update`, `/status`, `/poll`, `/health`

### Phase 2：服務層

5. **移植服務類別**
   - `BaseService.kt` — 抽象類別 `fetch(config) → ServiceResult`
   - `BrowserDataService.kt` — 5 個子類別，從 `DataStoreRepository` 讀取

6. **WebView JS 注入**
   - `WebViewDataCollector.kt` — 管理多個 WebView 實例
   - 修改 `ai-monitor-client-v4.4.js`：
     - 移除 Tampermonkey GM_* 函式呼叫
     - 改用 `AiMonitor.postData(jsonString)` 透過 `@JavascriptInterface` 回傳
     - 簡化為 Android 專用版 `ai-monitor-android.js`

### Phase 3：UI 元件

7. **主題與色彩**
   - `Color.kt` — 移植 COLORS 字典 + SERVICE_ACCENTS
   - `Theme.kt` — Material3 暗色主題覆寫

8. **卡片元件**
   - `ServiceCard.kt` — 可展開/收合、accent header、Hero/Bar/KV 渲染
   - `ClockCard.kt` — Compose Canvas 繪製翻頁時鐘
   - `SegmentedProgressBar.kt` — 28-segment 進度條
   - `HeroSection.kt`, `KvRow.kt`, `BarRow.kt` — 子元件

9. **Dashboard 畫面**
   - `DashboardScreen.kt` — LazyColumn + FlowRow 卡片網格
   - `DashboardViewModel.kt` — 整合 DataStoreRepository + ConfigRepository
   - `CardGridLayout.kt` — 根據 Section config 排列卡片

### Phase 4：設定與配置

10. **設定畫面**
    - `SettingsScreen.kt` — 服務開關、名稱編輯、伺服器 Port
    - `CardLayoutEditor.kt` — 卡片佈局拖拽編輯器（可後續迭代）

### Phase 5：背景服務

11. **Foreground Service**
    - HTTP Server 需在前景 Service 中執行，避免被系統殺掉
    - WebView 資料收集在 Service 中定時刷新
    - 通知列顯示連線狀態

---

## 五、JS 腳本 Android 適配

### 原始 JS 的 Tampermonkey 專用 API

| 原始 API | Android 替代 |
|---------|-------------|
| `GM_xmlhttpRequest` | `@JavascriptInterface` 方法呼叫 |
| `unsafeWindow` | WebView 直接執行，不需要 |
| `GM_getValue` / `GM_setValue` | `@JavascriptInterface` 轉接到 SharedPreferences |
| `@match` URL 過濾 | Kotlin 端控制 WebView 載入哪些 URL |
| `@run-at document-start` | `WebViewClient.onPageStarted()` + `evaluateJavascript()` |

### Android 專用 JS 修改要點

```javascript
// 原始 (Tampermonkey)：
GM_xmlhttpRequest({
    method: "POST",
    url: "http://localhost:7890/update",
    data: JSON.stringify(payload),
});

// Android 版：
AiMonitor.postData(JSON.stringify(payload));
```

核心 transform 函式 (`transformOpenAI`, `transformClaudeUsage` 等) 完全不需修改，
只需替換資料發送層。

---

## 六、設定檔格式

### 設定檔位置

Android 內部儲存：`/data/data/com.example.ai_quota_monitor_android/files/config.json`

### 完整設定檔範例

```json
{
  "auto_refresh_minutes": 30,
  "server_port": 7890,
  "server_enabled": true,
  "data_source": "webview",
  "sections": [
    {
      "id": "clock",
      "name": "時鐘",
      "columns": 1,
      "cards": [
        { "type": "clock", "id": "main_clock", "span": 1 }
      ]
    },
    {
      "id": "monitors",
      "name": "主要監控",
      "columns": 2,
      "cards": [
        { "type": "service", "id": "c1", "service_key": "browser_claude_usage", "span": 1 },
        { "type": "service", "id": "c2", "service_key": "browser_github_copilot", "span": 1 },
        { "type": "service", "id": "c3", "service_key": "browser_openai", "span": 1 },
        { "type": "service", "id": "c4", "service_key": "browser_claude_billing", "span": 1 },
        { "type": "service", "id": "c5", "service_key": "browser_openrouter", "span": 2 }
      ]
    }
  ],
  "services": {
    "browser_claude_usage": {
      "enabled": true,
      "display_name": "Claude.ai",
      "source_key": "claude_usage",
      "url": "https://claude.ai/settings/usage"
    },
    "browser_github_copilot": {
      "enabled": true,
      "display_name": "GitHub Copilot",
      "source_key": "github_copilot",
      "url": "https://github.com/settings/billing/premium_requests_usage"
    },
    "browser_openai": {
      "enabled": true,
      "display_name": "OpenAI",
      "source_key": "openai_billing",
      "url": "https://platform.openai.com/settings/organization/billing/overview"
    },
    "browser_claude_billing": {
      "enabled": true,
      "display_name": "Claude API",
      "source_key": "claude_billing",
      "url": "https://platform.claude.com/settings/billing"
    },
    "browser_openrouter": {
      "enabled": true,
      "display_name": "OpenRouter",
      "source_key": "openrouter",
      "url": "https://openrouter.ai/settings/credits"
    }
  }
}
```

### 設定可調整項目總覽

| 項目 | 路徑 | 說明 |
|------|------|------|
| 自動刷新間隔 | `auto_refresh_minutes` | 5 / 15 / 30 / 60 分鐘 |
| HTTP Server Port | `server_port` | 預設 7890 |
| HTTP Server 開關 | `server_enabled` | 是否啟用外部資料接收 |
| 資料來源 | `data_source` | `"webview"` 或 `"server"` 或 `"both"` |
| 區域名稱 | `sections[].name` | 如 "時鐘"、"主要監控" |
| 區域欄數 | `sections[].columns` | 1 或 2 |
| 卡片順序 | `sections[].cards[]` | 陣列順序即顯示順序 |
| 卡片跨欄 | `sections[].cards[].span` | 佔幾欄 |
| 服務開關 | `services.<key>.enabled` | 顯示 / 隱藏 |
| 服務顯示名稱 | `services.<key>.display_name` | 卡片標題 |
| 服務來源 URL | `services.<key>.url` | WebView 載入的頁面 |

---

## 七、Gradle 新增依賴

```toml
# gradle/libs.versions.toml 新增

[versions]
nanohttpd = "2.3.1"
kotlinxSerialization = "1.7.3"
kotlinxCoroutines = "1.9.0"
lifecycleViewmodel = "2.10.0"
navigationCompose = "2.9.0"

[libraries]
nanohttpd = { group = "org.nanohttpd", name = "nanohttpd", version.ref = "nanohttpd" }
kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodel" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }

[plugins]
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

### AndroidManifest 新增權限

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## 八、風險與注意事項

### WebView 認證與 Cookie 管理（關鍵）

> 所有 AI 服務頁面都需要使用者認證，這是 Android 版最核心的設計議題。

1. **首次登入流程**：使用者在設定頁點擊「前往登入」，App 開啟全螢幕 WebView 顯示該服務登入頁面，使用者手動完成帳號密碼 / OAuth 登入。登入完成後 `CookieManager.flush()` 將 Cookie 寫入磁碟持久保存。
2. **Cookie 持久化**：`CookieManager.getInstance()` 的 Cookie 預設存於 App 私有目錄，App 重啟後仍然有效。需確保 `setAcceptCookie(true)` 和 `setAcceptThirdPartyCookies(webView, true)` 已啟用。
3. **Session 過期偵測**：背景 WebView 載入服務頁面時，若被 302 重導到登入頁（URL 包含 `/login`、`/auth`、`/sessions` 等），代表 Cookie 已過期。此時更新該服務狀態為「已過期」，在卡片顯示橘色警告，同時發送通知提醒使用者重新登入。
4. **各服務 Session 壽命差異**：不同服務的 Session 有效期不同（有些數小時、有些數天），需要逐一觀察紀錄。設定檔中記錄 `last_login` 和 `last_success` 時間，方便使用者判斷何時需要重新登入。
5. **User-Agent**：WebView 需設定為正常的 Chrome Mobile User-Agent 字串，避免被服務端拒絕或導向不支援的頁面。
6. **雙因素認證 (2FA)**：部分服務可能需要 2FA，全螢幕 WebView 可正常完成 TOTP / 簡訊驗證，無需額外處理。

### WebView 其他限制

1. **API 攔截**：WebView 的 `fetch` / `XHR` hook 行為可能與桌面 Chrome 略有不同，需逐一測試各服務頁面。
2. **記憶體**：同時載入 5 個 WebView 可能消耗大量記憶體。建議實作佇列機制，一次只載入 1-2 個 WebView 進行資料抓取，完成後銷毀。

### HTTP Server

1. Android 不允許背景持續執行網路服務。必須使用 **Foreground Service** 搭配持久通知。
2. 手機防火牆 / 電池最佳化可能阻擋本地伺服器。需引導使用者關閉電池最佳化。
3. 若使用方案 B（PC 推送到手機），需顯示手機 IP 位址並確保同一 WiFi。

### 其他

1. 翻頁時鐘動畫在 Compose Canvas 上的效能需測試，低階裝置可能需簡化。
2. 設定檔需做版本遷移，新增欄位時要有預設值合併邏輯（同原始 `ConfigManager.load()`）。

---

## 九、不移植的項目

| 項目 | 原因 |
|------|------|
| 系統匣圖示 (pystray) | Android 無此概念，改用 Foreground Service 通知 |
| Win32 HWND 視窗管理 | Windows 專用，Android 不需要 |
| wm_overrideredirect 無邊框視窗 | Android 為全屏 App |
| Chrome/Firefox 一鍵開啟/關閉 | Android 無法控制外部瀏覽器視窗 |
| gui/app.py (MainApp 主視窗) | 僅移植 desktop_widget (小工具) 的 UI 風格 |
| API 類服務 (claude_api.py 等) | 原始版也未啟用，有需要時再加 |

---

## 十、交付物清單

完成後的檔案結構：

```
app/src/main/
├── AndroidManifest.xml                    (更新：權限、Service)
├── assets/
│   └── ai-monitor-android.js             (修改版 JS 腳本)
├── java/com/example/ai_quota_monitor_android/
│   ├── MainActivity.kt
│   ├── QuotaMonitorApp.kt
│   ├── data/
│   │   ├── model/ServiceResult.kt
│   │   ├── model/DashboardConfig.kt
│   │   ├── model/ServiceConfig.kt
│   │   ├── repository/DataStoreRepository.kt
│   │   ├── repository/ConfigRepository.kt
│   │   └── server/LocalHttpServer.kt
│   ├── service/
│   │   ├── BaseService.kt
│   │   ├── BrowserDataService.kt
│   │   ├── WebViewDataCollector.kt
│   │   └── MonitorForegroundService.kt
│   ├── ui/
│   │   ├── theme/Color.kt
│   │   ├── theme/Theme.kt
│   │   ├── theme/Type.kt
│   │   ├── dashboard/DashboardScreen.kt
│   │   ├── dashboard/DashboardViewModel.kt
│   │   ├── dashboard/CardGridLayout.kt
│   │   ├── cards/ServiceCard.kt
│   │   ├── cards/ClockCard.kt
│   │   ├── cards/HeroSection.kt
│   │   ├── cards/SegmentedProgressBar.kt
│   │   ├── cards/KvRow.kt
│   │   ├── cards/BarRow.kt
│   │   ├── settings/SettingsScreen.kt
│   │   ├── settings/ServiceLoginScreen.kt
│   │   └── components/StatusBar.kt
│   └── util/
│       ├── FormatUtils.kt
│       └── ColorUtils.kt
└── res/
    └── values/strings.xml                 (更新：中文字串)
```

共約 **26 個 Kotlin 檔案** + 1 個 JS 資產檔 + Gradle 設定更新。
