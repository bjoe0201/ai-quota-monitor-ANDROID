<p align="center">
  <img src="docs/app-icon.svg" width="108" height="108" alt="AI Quota Monitor icon">
</p>

<h1 align="center">AI Quota Monitor — Android</h1>

<p align="center">
  一鍵掌握所有 AI 服務餘額，隨時掌握用量不超支<br>
  Monitor your AI service quotas at a glance — ChatGPT, Claude, OpenAI, GitHub Copilot & OpenRouter
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white" alt="Android 12+">
  <img src="https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Jetpack%20Compose-2026.02.01-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose">
  <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License">
</p>

---

## 📱 Screenshots

<table>
  <tr>
    <td><img src="PICS/Screenshot_20260515_143459.png" width="180"/></td>
    <td><img src="PICS/Screenshot_20260515_143601.png" width="180"/></td>
    <td><img src="PICS/Screenshot_20260515_143613.png" width="180"/></td>
    <td><img src="PICS/Screenshot_20260515_143621.png" width="180"/></td>
  </tr>
  <tr>
    <td align="center">儀表板總覽</td>
    <td align="center">服務用量卡片</td>
    <td align="center">進度條 / KV 列</td>
    <td align="center">卡片展開∕收合</td>
  </tr>
  <tr>
    <td><img src="PICS/Screenshot_20260515_143643.png" width="180"/></td>
    <td><img src="PICS/Screenshot_20260515_143701.png" width="180"/></td>
    <td><img src="PICS/Screenshot_20260515_143718.png" width="180"/></td>
    <td></td>
  </tr>
  <tr>
    <td align="center">設定頁</td>
    <td align="center">服務登入 (WebView)</td>
    <td align="center">翻頁時鐘卡片</td>
    <td></td>
  </tr>
</table>

---

## ✨ Features

| 功能 | 說明 |
|------|------|
| 🔍 **6 大 AI 服務監控** | ChatGPT（Plus / Pro）、Claude.ai、GitHub Copilot、OpenAI、Claude API、OpenRouter |
| 🃏 **卡片式儀表板** | Home Assistant 風格，支援多欄佈局，可展開 / 收合，狀態持久化 |
| 🌐 **雙資料來源** | ① App 內 WebView + JS 注入（背景自動刷新）② PC 端 Tampermonkey 腳本推送 (port 7890) |
| 🕐 **翻頁時鐘** | 仿機械翻頁風格的即時時鐘卡片 |
| 🌑 **深色主題** | Linear / Raycast 風格深色配色，每個服務有專屬強調色 |
| 🍪 **Cookie 持久化** | 登入一次即可，重啟 App 免重新登入 |
| 🔔 **登入偵測** | 偵測 session 過期或 Google SSO 限制，即時提示重新登入；需連續兩次判定才標記登出，避免暫時性導向造成誤判 |
| ↕️ **依 Card 排序收集** | 背景監控依設定中的 Card 順序**逐一**收集；停用或未登入的 Card 不建立背景 WebView |
| 🧱 **記憶體安全的背景收集** | 同時只保留一個服務頁面，收到資料後立即釋放；每輪結束再回收 WebView renderer，可長時間常駐不累積記憶體 |
| 🛡️ **Cloudflare 驗證提示** | 服務要求「驗證您是人類」時，Card 顯示提示，點一下即開啟該服務的登入畫面完成驗證 |

---

## 🚀 Quick Start

### 1. 安裝 APK

從 [Releases](../../releases) 下載最新 `ai-quota-monitor-vX.Y-release.apk`，安裝至 Android 12+ 手機。

### 2. 登入 AI 服務

進入 **設定 → 服務帳號**，依序對需要監控的服務點選「登入」，在內建 WebView 完成帳號登入後返回即可。

> **Claude API (platform.claude.com)** 使用 Google SSO 登入，Google 會封鎖 App 內 WebView。請改用頁面下方的 **Email 登入**，或使用 PC 端 Tampermonkey 腳本推送。

#### Claude.ai 使用教學

1. 在 **設定 → 服務帳號 → Claude.ai** 點選「登入」，於內建 WebView 完成登入。
2. App 會在背景開啟 `claude.ai/new#settings/usage`，擷取用量資料。
3. Claude.ai Card 依方案顯示多條進度條：
   - **本次工作階段** — 5 小時滾動額度與重設倒數
   - **每週限額（全部模型）** — 每週全模型合計用量
   - **Fable 週限額** — 若方案含單一模型（例如 Fable）的獨立週額度，會額外顯示該模型用量；標籤取自 API 回傳的模型名稱，未來換模型可自動跟進
   - **額外用量** — 若已開啟 usage credits，顯示已花費 / 上限與餘額

> 進度條會依帳號方案而異；沒有單一模型週限額的帳號不會顯示 Fable 進度條。

#### ChatGPT 使用教學

1. 在 **設定 → 服務帳號 → ChatGPT** 點選「登入」。
2. 於內建 WebView 完成 ChatGPT 登入，確認已進入 ChatGPT 後按右上角勾勾完成。
3. App 會在背景開啟 `chatgpt.com/#settings/Usage`，擷取每週剩餘額度與重設時間；Plus 的絕對日期與 Pro 的相對倒數格式皆支援。
4. 返回儀表板即可查看 ChatGPT Card；若「使用量限制重設」區塊提供可用重置，Card 會依項目數量逐筆顯示帶框的到期資訊。
5. 若方案提供點數資訊，Card 也會一併顯示。
6. 若 ChatGPT Card 上方出現「需要通過 Cloudflare 驗證」，點一下開啟登入畫面，完成「驗證您是人類」並進到 ChatGPT 後按右上角勾勾。下一輪收集就會恢復，提示也會自動消失。

> ChatGPT Usage 頁面及欄位會依帳號方案而異；沒有 Usage 權限或目前沒有可用「使用量限制重設」的帳號，不會顯示相應資料列。
>
> **Cloudflare 驗證**：chatgpt.com 位於 Cloudflare 之後。背景收集的頁面無法自行通過「驗證您是人類」，所以 Cloudflare 要求驗證時，需要在登入畫面手動通過一次；通過後的通行 cookie 可長期使用，平常不需要再處理。

### 3. 查看儀表板

返回主畫面，系統會自動在背景載入各服務頁面並更新用量資訊。標題右側會顯示目前 App 版本。

> **收集方式**：為了不讓多個服務頁面同時佔用記憶體，App 會**一次只載入一個服務頁面**，收到資料後立即關閉再換下一個。因此各張 Card 不會同時更新，而是依 Card 順序陸續更新；走完全部服務約需數分鐘，之後等待「自動刷新間隔」（設定中可調 1–10 分鐘）再跑下一輪。每張 Card 都會顯示自己的資料時間，資料過舊時會出現提示。

### （選用）PC 端 Tampermonkey 推送

若偏好從電腦瀏覽器取得資料，可在電腦安裝 [Tampermonkey](https://www.tampermonkey.net/) 並載入 `scripts/ai-monitor-client-v4.4.js`，將資料推送至手機 `IP:7890`。

---

## 🏗️ Architecture

```
資料來源                            記憶體儲存                   UI 層
┌─────────────────────┐
│  WebView + JS 注入   │──┐
│ （一次一個頁面，收完即拆）│  │    ┌──────────────────────┐   ┌─────────────────┐
└─────────────────────┘  ├──▶ │  DataStoreRepository  │──▶│  DashboardScreen │
┌─────────────────────┐  │    │  （StateFlow / 記憶體）  │   │  ServiceCard     │
│  HTTP Server :7890   │──┘    └──────────────────────┘   │  ClockCard       │
│  （Tampermonkey）    │                                    └─────────────────┘
└─────────────────────┘
```

App 內所有 WebView 共用同一個 renderer 進程，讓 6 個服務頁面同時常駐會把它撐到 1.5 GB 並被系統終止，連帶終止整個 App。因此背景收集改為序列化：`collectionSteps(config)` 產生本輪要走訪的頁面，`DashboardViewModel` 逐一載入、收到資料後立即釋放。頁面拆掉後 renderer 仍會留下記憶體，所以每輪最後一頁回報後再以 `WebViewRenderProcess.terminate()` 結束 renderer，下一輪自動起新的。詳見 [`docs/01-webview-renderer-oom-crash.md`](docs/01-webview-renderer-oom-crash.md)。

---

## 📋 Monitored Services

| Service | Source Key | 資料來源頁面 |
|---------|-----------|------------|
| Claude.ai | `claude_usage` | claude.ai/new#settings/usage |
| GitHub Copilot | `github_copilot` | github.com/settings/copilot + /settings/billing/budgets |
| OpenAI | `openai_billing` | platform.openai.com/settings/organization/billing/overview |
| Claude API | `claude_billing` | platform.claude.com/settings/billing |
| OpenRouter | `openrouter` | openrouter.ai/settings/credits + /activity |
| ChatGPT | `chatgpt_usage` | chatgpt.com/#settings/Usage |

---

## 🔨 Build from Source

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease

# Unit tests (JVM, no device needed)
./gradlew test
```

> Windows：請使用 `gradlew.bat` 取代 `./gradlew`

### Requirements

- Android Studio Meerkat (2024.3) 或更新版本
- JDK 17+
- Android 12+ 裝置或模擬器 (minSdk 31)

---

## 🛠️ Tech Stack

- **Kotlin 2.2.10** + **Jetpack Compose** (BOM 2026.02.01) + **Material3**
- **NanoHTTPD** — 嵌入式 HTTP Server
- **kotlinx.serialization** — 設定檔 JSON 序列化
- **AndroidX Navigation Compose** — 頁面導航
- AGP 9.1.1 | targetSdk 36 | minSdk 31

---

## 🤝 Credits

Android port of [ai-quota-monitor](https://github.com/bjoe0201/ai-quota-monitor) by [@bjoe0201](https://github.com/bjoe0201).  
Original desktop app: Python + tkinter.

---

## 📄 License

[MIT](LICENSE)
