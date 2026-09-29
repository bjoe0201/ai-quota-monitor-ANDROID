# DEVELOP.md — 開發與診斷交接

最後更新：2026-09-29。本檔記錄「正在進行中的診斷」與「怎麼重現測試」，供下一次接手。
已完成的根因分析與證據不在此重複，見 [`docs/01-webview-renderer-oom-crash.md`](docs/01-webview-renderer-oom-crash.md)。

## 目前狀態

- 版本 **v2.3 / versionCode 14**，**尚未發佈 GitHub Release**
- 平板上安裝的是 **debug build**（與先前同一把 debug key，所以能覆蓋安裝而不掉登入），已含本檔提到的所有修復
- 所有變更已 commit 並 push 到 `main`

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
| dataSync 前景服務背景 6 小時逾時 → 整個 App crash（`ForegroundServiceDidNotStopInTimeException`） | `onTimeout` 內 `stopSelf()`；`startForeground` 被拒時自行結束；`MainActivity.onStart` 重啟服務 | `device_config put activity_manager data_sync_fgs_timeout_duration 60000` 後切背景：舊版約 70 秒 crash，新版服務停止、App pid 不變，回前景服務重啟（2026-09-28）。測完以 `device_config delete` 還原 |

## 尚未解決的核心問題

**renderer 記憶體跨輪次累積，不收斂。** 44 分鐘、兩代 renderer 的實測：

| 輪次 | 峰值 | 空檔底線 |
| --- | --- | --- |
| 1 | 698 MB | 567 MB |
| 2 | 889 MB | 752 MB |
| 3 | 1,119 MB | 921 MB |
| 4 | 1,257 MB | 1,125 MB |
| 5 | 1,500 MB | 1,323 MB |

空檔底線每輪 **+190 MB**，五輪內沒有趨緩。renderer 約 **23 分鐘**撞上 1.5 GB 後被系統終止，新 renderer 以相同斜率重來。App 全程存活、資料照常收集，所以使用者端表現正常。

A 把死亡間隔從 6–7 分鐘拉長到約 23 分鐘，**延後但未消除** OOM。

## 下一次的第一件事

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

**T5 期間順帶查到並修正：**

- **OpenAI 每輪 90 秒逾時的根因**：`19fbeda` 的 host 白名單只接受 `platform.openai.com`，但帳務資料在 `api.openai.com`（`/v1/dashboard/billing/credit_grants`、`/subscription`）。已讓 `hostMatch` 支援多個 host，`injection-test.mjs` 新增兩項測試（修正前失敗、修正後 7／7 通過）。2026-09-29 00:10 安裝後實測 OpenAI `data=true`、27 秒完成，欄位齊全。
- **Claude API 卡片「沒資料」**：資料其實完整（`balance_usd`、`plan`、`this_month_usd`、`next_billing`），API 全在 `platform.claude.com`。卡片是被**收合**（▸），收合狀態存在 `collapsedCards`。待辦第 6 項應可結案，待使用者確認展開後正常。
- T5 期間其他卡片顯示「等待瀏覽器資料」是因為 DataStore 只在記憶體中、每段強制停止都會清空，不是故障。
- `TaskStop` 停不掉 `t5.sh`／`sample.sh`，結束時一定要用 Win32_Process 確認並終止，否則腳本會繼續強制停止 App。2026-09-29 的 `t5run.sh` 正常結束後，仍殘留一個 bash（pipeline 子程序）與 `adb logcat`（Git Bash 沒有 `pkill`），同樣要用 Win32_Process 收尾。

**建置環境陷阱（2026-09-28）：** 若 `JAVA_HOME` 指向的 Android Studio 內附 JBR 不完整（缺 `lib\jvm.cfg`，例如 Studio 更新中斷），`gradlew.bat` 會直接失敗。暫時在該次指令內把 `$env:JAVA_HOME` 指向另一份完整的 JDK 17+，或修復／重裝 Android Studio。

## 待辦（建議順序）

1. ~~量測 JS 修復後的斜率~~（2026-09-28 完成：無改善）→ ~~T1 `clearCache(false)`~~（無差異，PLANS/03 §8.2）→ ~~T2 `clearCache(true)`~~（無差異，§8.4）→ ~~T5 逐服務隔離~~（2026-09-29 完成：每個服務都累積，§8.5）→ **T6 計畫性回收 renderer PoC**（步驟見 PLANS/03 §5 T6）
   - ~~OpenAI billing 步驟每輪都耗滿 90 秒逾時~~（已修正，`01927db`；補跑 12／12 輪有資料）
2. **登入與背景收集互斥** — 目前進入登入畫面不會暫停收集迴圈，可能同時有 1 個背景頁 + 1 個登入頁。`MainActivity` 只切換 screen state，`DashboardViewModel` 的收集 job 不受影響
3. **endpoint 層級白名單** — 目前只做到 host 層級。要再收緊必須逐服務確認實際 API 路徑，否則可能悄悄停掉正常資料
4. **`WebViewCompat.addDocumentStartJavaScript`** — 比 `onPageStarted` 更有保證（官方承諾在頁面自身腳本前執行），但會改變注入語義，需保留「原站替換 fetch 後補注入」的能力，單獨處理並單獨量測
5. **若斜率不降 → A′ 概念驗證**：不啟動 SPA，載入同源輕量頁（如 `robots.txt`）再用 `fetch()` 取 JSON API、`DOMParser` 解析 HTML。先用 claude.ai 單一服務驗證可行性，需確認：API 是否只靠 Cookie 即可呼叫、Cloudflare 是否放行、純 DOM 資料是否存在於原始 HTML、token 過期的回復流程
6. **Claude API 卡片顯示不出數值** — 步驟耗時顯示它有回報資料（約 30 秒結束，未燒滿 90 秒逾時），所以問題可能在欄位對應或 Card 渲染，不是抓不到
7. 以上穩定後，才考慮發 v2.3 GitHub Release（流程見 `CLAUDE.md`）

## 測試方法

### 單元測試

```powershell
.\gradlew.bat testDebugUnitTest          # 21 個 JVM 測試
node app/src/test/js/injection-test.mjs  # 5 個注入腳本測試（不需裝置）
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
