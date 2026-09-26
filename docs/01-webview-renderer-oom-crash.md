# 01 — WebView Renderer OOM 導致 App 整個關閉

| 項目 | 內容 |
| --- | --- |
| 診斷日期 | 2026-09-26 ~ 2026-09-27 |
| 影響版本 | v2.1 (versionCode 12) 起，v2.2 (versionCode 13) 同樣受影響 |
| 修復版本 | v2.3 (versionCode 14) |
| 症狀 | App 啟動後運作正常，放置 14–61 分鐘後整個關閉，不會自行重啟 |
| 根因 | 背景 WebView 共用的 renderer 進程記憶體耗盡而死亡，而 App 未實作 `onRenderProcessGone()`，WebView 層因此刻意終止整個 App 進程 |
| 狀態 | 缺陷 B（韌性）已修復並實機驗證；缺陷 A（記憶體模型）已改為序列化收集，實機記憶體量測待補 |

## TL;DR

兩層缺陷疊加：

- **A — renderer 記憶體撐爆**：6 個 desktop-UA 的重量級 SPA 全部常駐在**同一個** renderer 進程，從不暫停、抓完資料也不拆除。renderer RSS 7–10 分鐘就爬到 1.3–1.5 GB，在 4 GB RAM 的裝置上必然 OOM。
- **B — renderer 死亡未被接手**：兩個 `WebViewClient` 都沒有覆寫 `onRenderProcessGone()`。Android WebView 在 renderer 死亡而沒有任何 WebView 接手時，會**故意終止整個 App 進程**。

A 決定「多久死一次」，B 決定「死的是一張卡片還是整個 App」。

## 症狀

- 使用者操作時一切正常，放置一段時間後 App 直接消失
- 不會自行重啟；`MonitorForegroundService` 的 `START_STICKY` 也救不回來（整個進程被 SIGTRAP 終止）
- 沒有任何 Kotlin 例外、沒有 ANR、UI 層查不到問題

## 測試環境

- 裝置：Redmi 平板（型號 24075RP89G）
- 系統：HyperOS / Android 16
- RAM：3.9 GB（`MemTotal 3923064 kB`）
- 實際安裝版本：v2.1 debug build（`flags=[DEBUGGABLE TEST_ONLY]`，versionCode 12）

## 證據

### 1. Crash 簽名（決定性）

`dumpsys dropbox` 中 20+ 筆 `data_app_native_crash` 全部是同一個 abort message：

```
Abort message: '[FATAL:third_party/crashpad/crashpad/client/crashpad_client_linux.cc:744]
Render process (N)'s crash wasn't handled by all associated webviews,
triggering application crash.'
```

App process uptime 分布 859s–3666s（14–61 分鐘），與使用者描述的「放一陣子」完全一致。

這段訊息由 WebView 的 Chromium 層印出，意思是：renderer 死了，而沒有任何關聯的 WebView 回報「我處理了」，所以它終止宿主 App。

### 2. 記憶體成長

`ps -A -o PID,RSS,NAME` 對 renderer 取樣（同一次 App 生命週期內）：

| 時間 | renderer RSS |
| --- | --- |
| 23:41:43 | 1,314 MB |
| 23:42:13 | 1,446 MB |
| 23:43:14 | 1,398 MB |
| 23:43:44 | 1,532 MB ← 5 分鐘輪詢的 refresh 發生在此 |
| 23:44:44 | 1,538 MB |

重點觀察：

- 每分鐘成長約 60–130 MB
- **5 分鐘的定期 refresh（destroy + 重建 WebView）不會回收記憶體**，反而一次再加約 130 MB
- `dumpsys meminfo <renderer pid>` 顯示 1.06 GB 落在 `Unknown`（anonymous private dirty，即 V8 heap / Blink / partition alloc）
- 同一份 meminfo 中 **`Graphics: 0 KB`、EGL/GL mtrack 皆為 0** — 背景 WebView 從未 attach 到 window，根本沒有在繪製。記憶體全部花在「把 SPA 完整開機並維持存活」，與畫面無關

### 3. 一次完整的死亡時間線

| 時間 | 事件 |
| --- | --- |
| 23:44:44 | renderer RSS 1,538 MB；裝置 `MemAvailable` 僅剩 408 MB |
| 23:44:59 | renderer `CrRendererMain` 以 `SIGTRAP / TRAP_BRKPT` 死亡（Chromium OOM CHECK） |
| 23:45:00 | App 進程收到 `Fatal signal 5`，abort message 如上 |
| 23:45:03 | `lowmemorykiller: thrashing (41%)`，順帶殺掉 `com.android.camera` |

最後一列顯示這不只是 App 自己的問題 — 單一 renderer 佔掉裝置近 40% 的 RAM，已造成系統層級的記憶體 thrashing。

## 根因

### 缺陷 A — renderer 記憶體模型（未修復）

`DashboardViewModel.loadLoggedInServices()` 會為每個「已啟用且已登入」的服務各開一個背景 WebView，並且：

- 頁面永久存活，從不 `onPause()` / `pauseTimers()`
- 抓到資料後不拆除
- 每 `autoRefreshMinutes`（預設 5 分鐘）整批 destroy + 重建，但記憶體沒有回到系統

所有 WebView **共用同一個 renderer 進程**，所以成本是 6 個 SPA 的總和。

### 缺陷 B — renderer 死亡未被接手（已修復）

`WebViewDataCollector` 的背景 client 與 `ServiceLoginScreen` 的登入 client 都沒有覆寫
`WebViewClient.onRenderProcessGone(WebView, RenderProcessGoneDetail)`。該 API 自 API 26 起可用，回傳 `true` 表示「App 已處理」；沒有覆寫或回傳 `false` 時，App 進程會被終止。

## 修復 B（已完成）

### 新增檔案

| 檔案 | 職責 |
| --- | --- |
| `app/src/main/java/.../service/RendererRecoveryPolicy.kt` | 決定 renderer 死亡後何時、以什麼間隔重載 |
| `app/src/main/java/.../service/SessionExpiryGuard.kt` | 要求重複證據才判定服務登出 |

`RendererRecoveryPolicy` 行為：

- 退避 5s → 30s → 2m → 5m（封頂），連續死亡時逐步拉長
- renderer 健康存活滿 15 分鐘，退避歸零
- 錯開 20 秒重載（`scheduleFor(n)` 會把 n 個頁面排開）。修復 A 之後一次只有一個頁面存活，因此實際只用到退避部分，錯開能力保留給未來可能的並行載入

`SessionExpiryGuard` 行為：連續 **2 次**在 `onPageFinished` 看到登入頁才通報登出。因為「標記登出」是黏性的（會停掉該服務所有背景收集，直到使用者手動登入），而 token refresh、bot 檢查、慢速 SPA 中間態都可能短暫停在 auth URL。renderer 死亡後重載次數變多，誤判風險隨之上升。

### 修改檔案

| 檔案 | 修改 |
| --- | --- |
| `service/WebViewDataCollector.kt` | 背景與登入兩個 `WebViewClient` 都加上 `onRenderProcessGone` 回傳 `true`；死掉的 WebView 立即 `destroy()`（renderer 死亡後該物件永久不可用）並透過 `setOnRendererGone` 通報呼叫端 |
| `ui/dashboard/DashboardViewModel.kt` | 收到 renderer 死亡通報時立刻取消正在等待的步驟（不必白等逾時），並在進入下一個頁面前套用退避 |
| `ui/settings/ServiceLoginScreen.kt` | renderer 死亡時以 `key(generation)` 重建乾淨的登入 WebView，不留白畫面 |

### 測試

`app/src/test/java/.../service/` 下新增 `RendererRecoveryPolicyTest`、`SessionExpiryGuardTest` 共 10 個 JVM 單元測試，涵蓋錯開排程、退避、退避歸零、per-service 證據追蹤。WebView callback 本身無法在 JVM 測試，改以實機驗證。

```powershell
.\gradlew.bat testDebugUnitTest
```

## 驗證方法（可重現）

不必等 10–60 分鐘的自然 OOM — debug build 會自動開啟 WebView DevTools socket，可用 CDP 精準複製 renderer 死亡：

```bash
# 1. 找出 App pid 與 DevTools socket
adb shell pidof com.example.ai_quota_monitor_android
adb shell "cat /proc/net/unix | grep -i devtools"

# 2. 轉發並列出頁面
adb forward tcp:9333 localabstract:webview_devtools_remote_<APP_PID>
curl -s http://localhost:9333/json/list

# 3. 對任一頁面下 Page.navigate 到 chrome://crash（所有頁面共用 renderer，一發即全滅）
#    透過 webSocketDebuggerUrl 送出：
#    {"id":1,"method":"Page.navigate","params":{"url":"chrome://crash"}}

# 4. 驗收
adb shell pidof com.example.ai_quota_monitor_android   # pid 必須不變
adb logcat -b crash -d | grep -c ai_quota_monitor      # 必須為 0
```

驗證結果：

| | 修復前 | 修復後 |
| --- | --- | --- |
| renderer | 死亡（tombstone） | 死亡（tombstone） |
| **App 進程** | **被 SIGTRAP 終止** | **pid 不變，存活** |
| crash buffer 中的套件名 | 出現 abort message | 0 次 |
| 之後 | App 消失，不會自行回來 | 5 秒起錯開重載，約 100 秒後所有頁面回復 |
| WebView 洩漏 | — | 無（事後 DevTools 僅 5 個 `type: page`，其餘為頁面自己的 `type: worker`） |

### 40 分鐘連續觀察（只有 B、還沒有 A 的版本）

每分鐘記錄 App pid 與 renderer pid/RSS：

```
00:16  app=14060  renderer=15598:949MB
00:23  app=14060  renderer=15598:1524MB
00:25  app=14060  renderer=17033:876MB    ← 第 1 次 renderer 死亡
00:30  app=14060  renderer=17033:1564MB
00:32  app=14060  renderer=17882:708MB    ← 第 2 次
00:38  app=14060  renderer=18478:656MB    ← 第 3 次
00:45  app=14060  renderer=19460:1032MB   ← 第 4 次
00:51  app=14060  renderer=19991:767MB    ← 第 5 次
00:56  app=14060  renderer=19991:1554MB
```

- **renderer 換了 6 個世代 = 40 分鐘內死了 5 次**，約每 6–7 分鐘一次
- 每個新 renderer 從約 650–1030 MB 起跳，爬到 1.5–1.68 GB 後死亡
- **App pid 全程是 14060，一次都沒變** — 換成修復前，這 40 分鐘內 App 會消失 5 次

這組數據同時證明兩件事：B 的接手確實有效，而**只有 B 是不夠的** — renderer 仍在高頻死亡，每次死亡都要付出重新載入所有頁面的代價，裝置也持續被記憶體壓力波及。這正是必須做 A 的理由。

## 修復 A — 序列化收集（v2.3）

只有 B 的話 renderer 仍會衝到約 1.25 GB，每 10–60 分鐘死一次（只是不再拖垮 App），裝置層級的記憶體 thrashing 也還在 — 實測期間 lmkd 連續殺掉 `com.google.android.calendar`、`com.xiaomi.discover`、`com.miui.cloudservice` 等 app 來騰出記憶體。

修法：**一次只讓一個頁面存活**，把「6 個 SPA 常駐」換成「載入 → 收資料 → 拆掉 → 下一個」。

### 新增檔案

| 檔案 | 職責 |
| --- | --- |
| `app/src/main/java/.../service/CollectionPlan.kt` | 純函式 `collectionSteps(config)`，產生一輪要走訪的頁面清單（含 GitHub 的第二個 budgets 頁面），並排除停用／未登入／無 URL 的服務 |

### 修改內容

| 檔案 | 修改 |
| --- | --- |
| `ui/dashboard/DashboardViewModel.kt` | 以 `startCollectionLoop()` 取代原本的 `startPolling()` + `loadLoggedInServices()`：依序走訪 `collectionSteps()`，每個頁面收完立刻 `destroyService()` 再進入下一個 |
| `service/WebViewDataCollector.kt` | `destroyService()` 改為先 `stopLoading()` → 移除 JS bridge → `loadUrl("about:blank")` → `clearHistory()` → `destroy()`，讓 renderer 先丟掉 document、JS heap 與計時器；背景頁面關閉圖片載入（`loadsImagesAutomatically = false`、`blockNetworkImage = true`），因為這些頁面只被解析、從不繪製 |

### 每個步驟的等待條件

- 收到該 `sourceKey` 的第一筆資料後，再等 **20 秒安靜期**（`SETTLE_MS`）才拆頁面。分段回報的頁面需要這段時間 — 例如 OpenRouter 會先送餘額，再導向 `/activity` 取用量
- 整個步驟硬上限 **90 秒**（`PAGE_TIMEOUT_MS`），給那些始終不回報的頁面
- renderer 若在步驟中途死亡，立刻中止該步驟（不必白等逾時），並套用 `RendererRecoveryPolicy` 的退避後再進入下一個頁面

### 輪詢模型的改變

舊模型是固定每 5 分鐘無條件觸發 `refreshAll()`，上一輪沒跑完就會疊加。新模型改為**一輪跑完才開始計時**下一輪，因此頁面永遠不會互相堆疊。代價是單一服務的更新間隔變長：一輪約需「服務數 × (載入時間 + 20 秒安靜期)」，6 個服務約 3–8 分鐘，加上設定的間隔。

### 仍可繼續優化的方向

| 方案 | renderer 峰值 | 風險 |
| --- | --- | --- |
| 原始設計：6 個 SPA 常駐 | ~1.5 GB（必死） | — |
| **A（v2.3 採用）**：序列化 + 用完即拆，仍載入完整 SPA | 一次一個頁面 | 低；但單頁 30–60 秒的載入等待照付 |
| **A′**：載入同源輕量頁（如 `robots.txt`），再用 `fetch()` 取 JSON API、`DOMParser` 解析 HTML | 數十 MB | 中；每個服務需確認端點，但速度也快得多 |
| 純 HTTP：OkHttp + `CookieManager` 取 cookie，完全不用 WebView | 0 | 高；claude.ai / chatgpt.com 在 Cloudflare 後方，非瀏覽器 TLS 指紋可能被擋，token 自動續期也會失效 |

關鍵認知：**需要 WebView 的理由是那個 origin 的 cookie 環境與瀏覽器指紋，不是網頁畫面。** 因為背景 WebView 早已不繪製（`Graphics: 0 KB`），省記憶體必須從「不要把整個 SPA 開機」下手，而非關閉畫面。A′ 正是把這個認知推到底 — 連 SPA 都不開機，只借用那個 origin 的 fetch 環境。

## 附帶發現

1. **`isLoginPage()` 比對不到 OpenRouter 的登入頁** — 該服務登出後會停在 `openrouter.ai/sign-in?redirect_url=...`，而比對清單只有 `/signin`（無連字號），因此 session 過期永遠不會被通報，卡片只是靜靜地不再更新。
2. **`WebViewDataCollector.createLoginWebView()` 是死程式碼** — `ServiceLoginScreen` 自行建立 WebView，沒有任何呼叫端。
3. **`ServiceLoginScreen` 的登入 WebView 未在離開畫面時 `destroy()`** — 每次進入登入畫面會留下一個 WebView。修復 A 只處理背景收集用的 WebView，這個登入畫面的洩漏尚未處理。

## 診斷指令參考

```bash
# 是否是「renderer 死亡連帶終止 App」
adb shell "dumpsys dropbox --print data_app_native_crash" | grep -a "Abort message"

# renderer 記憶體（挑出 RSS 異常的 sandboxed_process）
adb shell "ps -A -o PID,RSS,NAME | grep sandboxed_process"

# renderer 記憶體組成（確認是 anonymous 而非 Graphics）
adb shell dumpsys meminfo <RENDERER_PID>

# 確認該 renderer 屬於本 App（ConnectionRecord）
adb shell "dumpsys activity processes | grep -A6 ai_quota_monitor"

# 裝置整體壓力與 lmkd 行為
adb shell "cat /proc/meminfo | head -5"
adb logcat -d | grep -i lowmemorykiller
```

## 參考

- `WebViewClient.onRenderProcessGone(WebView, RenderProcessGoneDetail)`（API 26+）：回傳 `true` 代表 App 已接手，否則 App 進程會被終止；renderer 已死的 WebView 不可重用，必須 `destroy()` 後改用新建的實例。
- 相關原始碼：`service/WebViewDataCollector.kt`、`service/RendererRecoveryPolicy.kt`、`service/SessionExpiryGuard.kt`、`ui/dashboard/DashboardViewModel.kt`、`ui/settings/ServiceLoginScreen.kt`
