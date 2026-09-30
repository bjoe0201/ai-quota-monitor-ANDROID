# DEVELOP.md — 開發與診斷交接

最後更新：2026-10-01。本檔記錄「正在進行中的診斷」與「怎麼重現測試」，供下一次接手。
已完成的根因分析與證據不在此重複，見 [`docs/01-webview-renderer-oom-crash.md`](docs/01-webview-renderer-oom-crash.md)。

## 目前狀態

- 版本 **v2.3 / versionCode 14**，**尚未發佈 GitHub Release**
- 平板上安裝的是 **debug build**（與先前同一把 debug key，所以能覆蓋安裝而不掉登入），2026-09-30 23:36 安裝 `08505ff`，已含本檔提到的所有修復
- 所有變更已 commit 並 push 到 `main`（工作目錄裡 `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties` 的修改不是這幾次工作的一部分，刻意未 commit）

## 下一次的第一件事

1. **用新版（`08505ff`）再跑一次過夜長跑**，同時驗證 renderer 回收和 ChatGPT 的 Cloudflare 狀態：隔天讀 events buffer（`am_proc_start`／`am_proc_died`／`am_kill`）、crash buffer、App PID 是否不變、`AiQuotaMemExp` 的 `step` 行裡有沒有 `cloudflare-challenge`
2. **Claude API 的 Google 帳號登入在新 UA 下還沒實測**。UA 沒有帶 `wv`、`X-Requested-With` 也照樣抑制，理論上不影響，但要實際走一次登入流程才算數
3. 若都正常，就可以準備發 v2.3 Release（流程見 `CLAUDE.md`）

## 已修復並驗證

| 缺陷 | 修法 | 驗證方式 |
| --- | --- | --- |
| B：renderer 死亡連帶終止 App | `onRenderProcessGone` 回傳 `true` | 自然 OOM 時 app pid 不變（2026-09-27 02:38 實測） |
| A：6 個 SPA 常駐撐爆 renderer | 序列化收集、收完即拆 | DevTools page target 全程 0 或 1 |
| C：重複 MainActivity 實例 | `launchMode="singleTask"` | 連續啟動 3 次後 task 維持 `sz=1` |
| 登入畫面 WebView 洩漏 | `AndroidView` 的 `onRelease` | 進入登入畫面 pages 1→2，返回後→0 |
| OpenRouter 登入頁偵測不到 | 抽成 `isLoginUrl()`，補 `/sign-in` | `LoginPageDetectionTest`（含 6 個資料頁面的反向誤判測試） |
| 三處硬編碼版號 | 改讀 `BuildConfig` | 截圖確認標題／底部狀態列／設定→關於 |
| JS 重複注入與過度解析 | 安裝防護、host 白名單、XHR `{once:true}` | `app/src/test/js/injection-test.mjs` |
| renderer 記憶體每輪 +190 MB 不歸還 | 每輪最後一頁回報後 `WebViewRenderProcess.terminate()`（T6，預設開啟） | 2026-09-30 過夜約 19 小時：App PID 不變、crash buffer 空；renderer 234 代**全部**是計畫性回收（`am_kill … isolated not needed`），LMK 終止 0 次；回收當下 610–980 MB 持平 |
| ChatGPT 卡在 Cloudflare「驗證您是人類」，連登入畫面也過不了 | `BrowserUserAgent`：平板版 Chrome UA，版本取自實際 WebView，登入頁與背景頁共用（`f4f851d`） | 見下方「Cloudflare 驗證」一節 |
| 背景頁落在驗證頁時白等 90 秒、使用者看不出原因 | `CloudflareChallenge`：偵測驗證頁、15 秒後放棄、卡片顯示可點的提示（`08505ff`） | 真實驗證頁 15.6 秒結束該步；點提示開啟登入畫面；通過後下一輪有資料、提示消失 |
| dataSync 前景服務背景 6 小時逾時 → 整個 App crash（`ForegroundServiceDidNotStopInTimeException`） | `onTimeout` 內 `stopSelf()`；`startForeground` 被拒時自行結束；`MainActivity.onStart` 重啟服務 | `device_config put activity_manager data_sync_fgs_timeout_duration 60000` 後切背景：舊版約 70 秒 crash，新版服務停止、App pid 不變，回前景服務重啟（2026-09-28）。測完以 `device_config delete` 還原 |

## Cloudflare 驗證（2026-09-30 查到並修正）

**症狀**：ChatGPT 從 09-30 14:44 起每輪 90 秒逾時、卡片停在舊資料；使用者在 App 的登入畫面也一直被要求「驗證您是人類」。

**根因**：所有 WebView 都宣稱自己是 Windows 上的 Chrome 136，但同一頁的 `navigator.platform` 是 `Linux aarch64`、`userAgentData` 是 Android WebView 154、`maxTouchPoints` 是 5。Cloudflare 看到指紋自相矛盾，就一直要求驗證。

**證據**（都用 WebView DevTools，不需改程式）：

| 登入頁的 UA（`Network.setUserAgentOverride`） | 結果 |
| --- | --- |
| 原本的 Windows Chrome 136 | 一直重跳驗證 |
| WebView 原生 UA（帶 `wv`） | 11 秒通過 |
| 平板版 Chrome 154（不帶 `wv`、不帶 `Mobile`） | 6 秒通過 → 採用 |

**背景頁永遠無法自行通過驗證。** 14:44–20:49 之間背景頁沒有一次通過；背景頁是 0×0 的 viewport，但用 `Emulation.setDeviceMetricsOverride` 給 1280×800 也一樣過不了。只有真的顯示在畫面上的登入頁能通過；通過後 `cf_clearance` 效期一年，背景頁就能沿用。

**要注意的地方**：

- 背景頁的判定條件是 `_cf_chl_opt` 或 `chl_page` 腳本。正常的 chatgpt.com 頁面也會載入 `/cdn-cgi/challenge-platform/scripts/jsd/`，不能只比對路徑（已在實際頁面確認）
- 驗證頁的 `<title>` 會依語系變成「請稍候...」，不能拿標題判斷
- 桌面 UA 當初是為了避免被導到手機版頁面；新 UA 不帶 `Mobile`，六個服務的卡片內容都確認過正常
- UA 的版本號取自 `WebSettings.getDefaultUserAgent()`，WebView 更新後會自動跟上；不要再寫死版本號

**怎麼重現驗證頁**：

- 刪掉 `.chatgpt.com` 的 `cf_clearance`、`__cf_bm`（CDP `Network.deleteCookies`）**不一定**會馬上觸發；這次是刪掉後過了約 12 分鐘、第三輪才出現真的驗證頁
- 只想測 App 端流程時：在 ChatGPT 頁面出現的瞬間，用 CDP `Page.navigate` 把它導到 `data:text/html,<script>window._cf_chl_opt={}</script>`，約 15 秒後 log 會出現 `step browser_chatgpt_usage data=false … cloudflare-challenge`

## renderer 記憶體累積（已由 T6 解決）

**renderer 記憶體跨輪次累積，不收斂。**（以下是 T6 之前的量測，保留作為對照） 44 分鐘、兩代 renderer 的實測：

| 輪次 | 峰值 | 空檔底線 |
| --- | --- | --- |
| 1 | 698 MB | 567 MB |
| 2 | 889 MB | 752 MB |
| 3 | 1,119 MB | 921 MB |
| 4 | 1,257 MB | 1,125 MB |
| 5 | 1,500 MB | 1,323 MB |

空檔底線每輪 **+190 MB**，五輪內沒有趨緩。renderer 約 **23 分鐘**撞上 1.5 GB 後被系統終止，新 renderer 以相同斜率重來。App 全程存活、資料照常收集，所以使用者端表現正常。

A 把死亡間隔從 6–7 分鐘拉長到約 23 分鐘，**延後但未消除** OOM。

## 記憶體診斷歷程（T0–T6，已結案）

**T0 已完成（2026-09-28）：JS 修復沒有降低斜率。** 45 分鐘、兩代 renderer，空檔底線仍約每輪 +190 MB；第一代約 29 分鐘時在 1.45 GB 左右被終止。細節見 [`PLANS/03`](PLANS/03-webview-memory-test-plan.md) §8.1。

**T1／T2 也已完成（2026-09-28）：兩者都無效。** 每輪 `clearCache(false)` 為 +181 MB／輪，`clearCache(true)` 為 +188 MB／輪，與 T0 的 +190 相同；renderer 壽命三者都約 29 分鐘。WebView HTTP 資源快取（RAM 與磁碟）不是累積來源。細節見 [`PLANS/03`](PLANS/03-webview-memory-test-plan.md) §8.2、§8.4。開關仍保留（`-PcacheClearExperiment=ram|disk`，預設 `none`），之後的量測一律用預設值建置。

下一步依計畫進 **T5（逐服務隔離）**，找出哪個服務頁面貢獻了累積；需要短期控制時另做 **T6（計畫性回收 renderer）**。不要直接跳到「A′ 是唯一解」。

**T5 已完成（2026-09-28 22:33 起，2026-09-29 23:09 補完）。** 啟動時帶 `--es mem_isolate_service <key>` 只收集單一服務（僅 debug build）；驅動腳本與原始數據在被忽略的 `release/measurements/`（`t5run.sh` → `t5.sh`、`t5an.py`、`2026-09-2{8,9}-T5-*.csv`、`*-T5-events.log`）。每段：強制停止 → 冷啟動 → 每 15 秒取樣 30 分鐘（120 筆）。細節見 [`PLANS/03`](PLANS/03-webview-memory-test-plan.md) §8.5。

| 服務 | 日期 | 空檔底線（RSS，12 輪） | 平均／輪 | 後半段／輪 | 資料 |
| --- | --- | --- | --- | --- | --- |
| ChatGPT | 09-28 | 323→1,237 MB | **+83 MB** | +81 | 每輪有 |
| Claude.ai | 09-28 | 324→806 MB | **+44 MB** | +42 | 每輪有 |
| OpenRouter（credits + activity 兩頁） | 09-29 | 329→742 MB | **+37.5 MB** | +32 | 13／13 有 |
| Claude API | 09-29 | 274→534 MB | **+24 MB** | +18 | 12／12 有 |
| OpenAI（修正後） | 09-29 | 224→408 MB | **+17 MB** | +16 | 12／12 有 |
| ~~OpenAI（修正前）~~ | 09-28 | 211→263 MB（9 輪） | ~~+6.5 MB~~ | — | 全無，作廢 |

**結論：**

- 五項加總 **+205 MB／輪**，與全部一起跑的 +181～+190 MB／輪同一量級 → 累積就是各頁面各自留下的量相加，不是服務之間互相作用。
- **每個服務都在累積，而且都是直線、不收斂**（後半段斜率與全程相近），沒有任何一個是 0。移除最重的 ChatGPT 只能降約 40%，無法根治。
- 同一個 OpenAI 頁面：沒拿到資料時 +6.5，真的解析帳務資料後 +17 → 留下的量跟頁面實際做了多少事有關，不是每次載入固定一個量。
- 三段補跑 renderer 都沒換代（最高 RSS 896 MB），App PID 不變。

→ 單一服務的修正（改 ChatGPT、改注入）無法消除累積。下一步是 **T6（每輪結束計畫性回收 renderer）**，它對所有服務都有效；T7（同源輕量頁）則用於降低個別服務的單輪成本。

**T6 PoC 已完成（2026-09-30）：有效。** 每輪最後一頁回報後以 `WebViewRenderProcess.terminate()` 結束 renderer，下一輪自動起新的。45 分鐘、10 輪：

| 指標 | T0（不回收） | T6（每輪回收） |
| --- | --- | --- |
| 各代峰值（RSS） | 701→1,475 MB 逐輪升高 | 710～816 MB，10 代持平 |
| 空檔 | 每輪 +190 MB 不歸還 | 沒有 renderer 程序 |
| 非計畫死亡 | 約每 29 分鐘一次 | 0 |
| 代價 | — | 回收約 0.3 秒；每輪首步冷啟動約 +1.5 秒 |

2026-09-30 起**預設開啟**（debug 與 release）；只有以 `-ProutineRendererRecycle=false` 建置的 debug APK 會關閉，用於量未回收的對照組。預設開啟版 01:24–01:39 短跑確認：3 次回收皆 `DONE`（324–366 ms）、各代峰值 728–792 MB、17／17 步 `data=true`、0 非計畫死亡。驅動腳本 `release/measurements/t6run.sh`，細節見 [`PLANS/03`](PLANS/03-webview-memory-test-plan.md) §8.6。**過夜長跑（2026-09-30 01:24 起約 19 小時）已驗證**：見上方「已修復並驗證」表。回收當下的 renderer 大小：01–14 時約 770 MB；14:48 之後約 880 MB（最高 981 MB），是因為 ChatGPT 卡在 Cloudflare 驗證頁、每輪多等 60 秒，不是回收失效。**尚未驗證**：登入畫面開啟時跳過回收。

**T5 期間順帶查到並修正：**

- **OpenAI 每輪 90 秒逾時的根因**：`19fbeda` 的 host 白名單只接受 `platform.openai.com`，但帳務資料在 `api.openai.com`（`/v1/dashboard/billing/credit_grants`、`/subscription`）。已讓 `hostMatch` 支援多個 host，`injection-test.mjs` 新增兩項測試（修正前失敗、修正後 7／7 通過）。2026-09-29 00:10 安裝後實測 OpenAI `data=true`、27 秒完成，欄位齊全。
- **Claude API 卡片「沒資料」**：資料其實完整（`balance_usd`、`plan`、`this_month_usd`、`next_billing`），API 全在 `platform.claude.com`。卡片是被**收合**（▸），收合狀態存在 `collapsedCards`。待辦第 6 項應可結案，待使用者確認展開後正常。
- T5 期間其他卡片顯示「等待瀏覽器資料」是因為 DataStore 只在記憶體中、每段強制停止都會清空，不是故障。
- `TaskStop` 停不掉 `t5.sh`／`sample.sh`，結束時一定要用 Win32_Process 確認並終止，否則腳本會繼續強制停止 App。2026-09-29 的 `t5run.sh` 正常結束後，仍殘留一個 bash（pipeline 子程序）與 `adb logcat`（Git Bash 沒有 `pkill`），同樣要用 Win32_Process 收尾。

**建置環境陷阱（2026-09-28，09-30 再次遇到）：** 若 `JAVA_HOME` 指向的 Android Studio 內附 JBR 不完整（缺 `lib\jvm.cfg`，例如 Studio 更新中斷），`gradlew.bat` 會直接失敗。暫時在該次指令內把 `$env:JAVA_HOME` 指向另一份完整的 JDK 17+（09-30 用的是同一台機器上另一份 Android Studio 安裝 `Android Studio1\jbr`），或修復／重裝 Android Studio。

## 待辦（建議順序）

1. ~~量測 JS 修復後的斜率~~（2026-09-28 完成：無改善）→ ~~T1 `clearCache(false)`~~（無差異，PLANS/03 §8.2）→ ~~T2 `clearCache(true)`~~（無差異，§8.4）→ ~~T5 逐服務隔離~~（2026-09-29 完成：每個服務都累積，§8.5）→ ~~T6 計畫性回收 renderer PoC~~（2026-09-30 完成：有效，§8.6）→ ~~把回收改為預設開啟（含 release）~~（2026-09-30）→ **過夜長跑驗證**（不接 PC，隔天讀 events／crash buffer）
   - ~~OpenAI billing 步驟每輪都耗滿 90 秒逾時~~（已修正，`01927db`；補跑 12／12 輪有資料）
   - ~~過夜長跑~~（2026-09-30 完成：234 代全為計畫性回收）
2. **登入與背景收集互斥** — 目前進入登入畫面不會暫停收集迴圈，可能同時有 1 個背景頁 + 1 個登入頁。`MainActivity` 只切換 screen state，`DashboardViewModel` 的收集 job 不受影響
3. **endpoint 層級白名單** — 目前只做到 host 層級。要再收緊必須逐服務確認實際 API 路徑，否則可能悄悄停掉正常資料
4. **`WebViewCompat.addDocumentStartJavaScript`** — 比 `onPageStarted` 更有保證（官方承諾在頁面自身腳本前執行），但會改變注入語義，需保留「原站替換 fetch 後補注入」的能力，單獨處理並單獨量測
5. **若斜率不降 → A′ 概念驗證**：不啟動 SPA，載入同源輕量頁（如 `robots.txt`）再用 `fetch()` 取 JSON API、`DOMParser` 解析 HTML。先用 claude.ai 單一服務驗證可行性，需確認：API 是否只靠 Cookie 即可呼叫、Cloudflare 是否放行、純 DOM 資料是否存在於原始 HTML、token 過期的回復流程
6. ~~Claude API 卡片顯示不出數值~~ — 2026-09-30 截圖確認餘額、方案、本月用量、下次計費都正常顯示，可結案
7. **Claude.ai 每週重設時間多算將近一天** — `ai-monitor-android.js` 第 87–89 行（`weekly_reset`）與第 108–110 行（`limits` 裡的重設時間）把天數和小時都用 `Math.ceil`：2 天 23.8 小時會顯示成「3 days 24 hrs」。天數應該用 `Math.floor`，小時進位到 24 時要進位成天。改完補 `injection-test.mjs` 測試，PC 端的 `transformClaudeUsage` 也要看是否同樣寫法
8. **Cloudflare 驗證提示還沒在「自然發生」時截過圖**；這次是使用者看到提示直接點了。下次遇到時截一張，確認版面
9. 以上穩定後，才考慮發 v2.3 GitHub Release（流程見 `CLAUDE.md`）

## 測試方法

### 單元測試

```powershell
.\gradlew.bat testDebugUnitTest          # 38 個 JVM 測試
node app/src/test/js/injection-test.mjs  # 7 個注入腳本測試（不需裝置）
```

JS 測試可指定檔案來比對修復前後：`node app/src/test/js/injection-test.mjs /path/to/old.js`

### 實機量測

Debug build 會自動開啟 WebView DevTools socket，可直接數頁面：

```bash
pid=$(adb shell pidof com.example.ai_quota_monitor_android | tr -d '\r')
adb forward tcp:9333 localabstract:webview_devtools_remote_$pid
curl -s http://localhost:9333/json/list | grep -c '"type": "page"'   # 必須永遠 0 或 1
```

每分鐘取樣一次（renderer RSS、page 數、task sz、app pid）：

```bash
for i in $(seq 1 30); do
  p=$(adb shell pidof com.example.ai_quota_monitor_android | tr -d '\r')
  r=$(adb shell "ps -A -o PID,RSS,NAME | grep sandboxed_process0" | awk '{printf "%s:%d ", $1, int($2/1024)}')
  n=$(curl -s --max-time 4 http://localhost:9333/json/list | grep -c '"type": "page"')
  sz=$(adb shell "dumpsys activity activities | grep -aoE 'A=10228:com.example.ai_quota_monitor_android U=0[^}]*sz=[0-9]+'" | grep -oE "sz=[0-9]+" | head -1)
  echo "$(date +%H:%M:%S) app=$p rend=[$r] pages=$n $sz"; sleep 60
done
```

### renderer 歸屬判定（不要用猜的）

isolated renderer 的 UID 名稱會編碼宿主 App 的 UID：

```bash
adb shell "dumpsys activity processes | grep -aE 'ProcessRecord.*sandboxed_process0'"
# u0a228i29 → 10228 是本 App 的 UID，這個 renderer 才是我們的
# u0a144i27 → 別的 App 的，不可計入
```

### 誘發 renderer 死亡（驗證 B，不必等自然 OOM）

透過 CDP 對任一頁面 `Page.navigate` 到 `chrome://crash`（所有頁面共用 renderer，一發即全滅），然後確認 app pid 不變且 `adb logcat -b crash -d | grep -c ai_quota_monitor` 為 0。詳見 `docs/01-webview-renderer-oom-crash.md`。

## 環境陷阱

- **MIUI 安裝會靜默失敗**。`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user` 需要平板上「開發者選項 → 透過 USB 安裝」開啟，而且該開關會自行失效。**每次安裝後務必驗證**，本次診斷就因為沒驗證而白量了一輪：

  ```bash
  adb shell "dumpsys package com.example.ai_quota_monitor_android | grep lastUpdateTime"
  ```

- **release APK 無法覆蓋 debug 安裝**（簽章不同），強行安裝要先反安裝，會清掉所有服務登入 cookie。目前裝置上是 debug build，要維持用 debug key 覆蓋安裝
- **Git Bash 會轉換路徑**。對 `adb shell` 傳裝置路徑時要加 `MSYS_NO_PATHCONV=1`
- **`./gradlew test --tests` 不成立**，要用 `testDebugUnitTest --tests "..."`
- **`/release/` 已加入 `.gitignore`**。截圖含用量與餘額屬個人資料，不可進公開 repo
- **`run-as` 在此 ROM 被擋**，讀不到 App 內部的 `config.json`；狀態改用行為推斷（步驟耗時、DevTools URL）

## 量測紀律

- **一次只改一項**，否則無法歸因
- renderer PID 換代要**分段分析**，不可把死亡後的歸零當成穩定
- 至少觀察 10 個完整週期或 1 小時，取較長者
- `pages=0` 不等於所有 worker／native 資源都已釋放
- profile 或強制 GC 會改變執行狀況，放在另一次診斷，不要污染長跑數據

## 給接手 agent 的建議 skills

- `superpowers:systematic-debugging` — 先找根因再動手；本次三個缺陷都是靠證據而非猜測定位的
- `superpowers:test-driven-development` — 先看到紅燈再實作；JS 修復就是先對舊腳本跑出 3 項失敗才動手
- `superpowers:receiving-code-review` — 收到外部檢查報告時逐項驗證，不要照單全收也不要防衛
- `superpowers:verification-before-completion` — 宣稱修好之前先拿出指令輸出

## 參考

- [`docs/01-webview-renderer-oom-crash.md`](docs/01-webview-renderer-oom-crash.md) — 完整根因分析、證據、驗證步驟、診斷指令
- [`docs/README.md`](docs/README.md) — 技術文件索引與撰寫規範
- [`CHANGELOG.md`](CHANGELOG.md) — v2.3 變更與已知限制
- [`CLAUDE.md`](CLAUDE.md) — 專案規範與發佈流程（含公開 repo 安全規則）
