# 03 — WebView 記憶體累積原因與解法測試計畫

建立日期：2026-09-27
狀態：待執行；本次僅建立計畫，沒有改動 App 或執行實機測試。
檢查基準：`19fbeda`，v2.3 / versionCode 14。後續實作或測試前須重新確認 HEAD 與實際安裝版本。

## 1. 目的與驗收問題

辨識背景收集時 RAM 逐輪增加的來源，依證據選擇最小有效修正：

1. 最新 JS 修正是否已讓記憶體收斂？
2. 累積是否與 RAM 資源快取、磁碟快取、重複解析、網站背景工作、WebView/native 資源或登入並存有關？
3. 清理資源或改變收集方式後，資料完整度、登入保持與更新時間是否仍符合需求？
4. 若需要計畫性回收 renderer，能否在避免非計畫 OOM 的同時維持 App 與監控服務穩定？

正常 RAM 可因暖機、快取及垃圾回收上下波動；目標是固定工作量下每輪峰值和空檔底線趨於穩定，不要求 RAM 完全不增加。

本計畫不等於授權立即操作 OPUS 使用中的裝置。執行前確認當下沒有其他量測或收集測試，避免污染結果。維持目前登入資料；不得藉由清除 App 資料或反安裝來建立測試基準。

## 2. 已知證據與限制

來源：[開發交接](../DEVELOP.md)、[WebView OOM 診斷紀錄](../docs/01-webview-renderer-oom-crash.md)。

| 項目 | 已知狀態 |
| --- | --- |
| 背景逐頁收集、renderer 死亡處理、singleTask、離開登入頁銷毀 | 已實作；驗證結果見既有診斷紀錄 |
| 修正前五輪空檔底線 | 567、752、921、1,125、1,323 MB；約每輪 +190 MB |
| 修正前 renderer 換代 | 紀錄約 23 分鐘一次；App PID 不變 |
| `19fbeda` JS 修正 | 一般重複注入防護、host 篩選、XHR 成功回應 listener 清理 |
| 原有 JS 測試 | 獨立檢查時 5 項通過 |
| 最新 JS 修正後實機斜率 | **交接中尚未量測**；不可套用舊數據宣稱仍失敗或已修好 |
| renderer 記憶體下降 | 舊紀錄換代後約 1,381 → 182 MB；未以清除磁碟快取作為前提 |

Unknown／Private Other 高，不能單獨證明 allocator 碎片化；page=0 不能證明 worker 與 native 資源皆已釋放。最新修正無效，也不代表「輕量頁是唯一解」。

## 3. 共通測試紀律

### 3.1 環境與版本確認

- 記錄 commit、APK 類型、版本、安裝時間、Android／WebView 版本、裝置 RAM。
- 此專案多份測試 APK 可能仍標示 v2.3 / 14。不能只看版號；比對 lastUpdateTime，條件允許時比對已安裝 APK 或腳本資產雜湊。
- 先前 MIUI 安裝曾失敗；安裝輸出有錯誤即停止量測，直到確認正確版本。
- 保持相同簽章的覆蓋安裝；不要為測試改用不同簽章並反安裝。
- 固定啟用服務、帳號登入狀態、服務順序、輪詢間隔、網路、前景／背景及充電條件。每組都記錄例外。
- 每次只改一個變數；不把清快取、端點篩選及 renderer 回收混在同一組。
- 每组採相同啟動與暖機方式。磁碟快取組有冷快取成本，需另外標示，不直接與熱快取首輪混比。

### 3.2 採樣欄位

每 15–30 秒採样，並記錄各服務載入前、回報完成、頁面銷毀後固定延遲、整輪空檔等事件。低頻採樣可能漏掉瞬間峰值，結果中必須註明取樣間隔。

| 欄位 | 用途 |
| --- | --- |
| experiment、commit、cycle、step、時間／經過時間 | 對齊組別與收集事件 |
| App PID、renderer PID／generation、換代原因 | 分開主程序與網頁程序；區分計畫回收、非計畫死亡 |
| App 與 renderer 的 RSS、PSS、private dirty | 避免只看 RSS 或把共享記憶體重複相加 |
| 裝置 MemAvailable | 檢查是否仍造成系統壓力 |
| page／worker 數、Activity 數、登入頁是否存在 | 排查殘留與並存；worker 數不等於記憶體大小 |
| 磁碟資源快取大小／檔案數（可取得時） | 區分儲存空間與 RAM 成長 |
| 每服務耗時、完成欄位、錯誤、重新登入次數 | 防止省 RAM 是因為沒有收集到資料 |
| renderer 死亡、系統殺其他 App、ANR | 評估穩定性與裝置影響 |

renderer 必須透過 App 的程序連線關係確認歸屬，不可直接把所有 sandboxed_process0 都加總。既有交接指令只作參考，不可硬編碼舊 UID、PID、DevTools port 狀態。

裝置若禁止讀取快取目錄，記錄「不可取得」，改用可用的系統統計或另行評估最小測試版指標；不得把讀不到記為 0，也不為此清除資料或繞過裝置權限。

日誌只存欄位名稱、數量、大小、去識別化端點及狀態，不存 Cookie、token、帳號識別、帳務數值或完整回應。原始量測留在本機忽略目錄，計畫中只補匿名摘要。

### 3.3 時間與比較方式

- 每組先跑至少 5 個完整週期檢查趨勢；有希望的方案再跑至少 10 個週期且至少 1 小時，取較長者，最後安排多小時使用情境。
- 比較每輪峰值與固定空檔窗口的底線，計算 MB／輪，不能用不同服務載入階段的單點互相比較。
- 先將暖機輪次與分析範圍固定並寫入結果，再看斜率；不要事後挑選好看的輪次。
- renderer PID 改變必須分段。自然死亡後歸零不是收斂；計畫回收的驗收則應明確檢查各代峰值範圍。
- GC／heap snapshot／native profiler 會改變狀況，放在獨立診斷輪，不混入一般長跑。
- 出現明顯系統記憶體壓力、反覆非計畫死亡或影響其他 App 時停止該組，不以一定撞到 1.5 GB 作為測試要求。

## 4. 假說與測試矩陣

| 編號 | 假說 | 單一主要變數 | 支持假說的觀察 | 不能直接推論 |
| --- | --- | --- | --- | --- |
| T0 | 最新 JS 修正足以改善 | 最新已驗證安裝版本，正常快取 | 空檔斜率明顯下降或收斂 | 原測試通過不等於 RAM 已修好 |
| T1 | RAM 資源快取佔主要累積 | 每輪結束清 `clearCache(false)` | 對比 T0 底線／斜率可重複下降 | API 沒讓 RSS 下降不等於所有快取都無關 |
| T2 | 磁碟資源快取參與累積 | 對比 T1 改成 `clearCache(true)` | 磁碟與 RAM 指標／斜率有額外差異 | T2 同時清 RAM；不能只靠 T2 有效就斷言磁碟舊檔是唯一根因 |
| T3 | 攔截器邊界仍重複解析 | 一次補一個已重現的邊界 | 無關回應／同一回應處理次數降低；再量實機 | 模擬測試中的重複解析不代表全部 190 MB |
| T4 | 登入與背景頁並存造成峰值 | 登入與背景收集互斥 | 登入期間並存與峰值降低 | 不能解釋完全未登入操作時的跨輪成長 |
| T5 | 特定網站／worker 是主要來源 | 一次只啟用一項服務 | 成長跟隨某服務，移除後下降 | 單頁或單 worker 本身不能證明洩漏 |
| T6 | renderer 存活跨輪造成累積 | 每輪完成計畫性回收 | 各代峰值穩定、空檔釋放、沒有非計畫 OOM | 是資源生命週期控制，不是證明引擎內部缺陷已根治 |
| T7 | 完整 SPA 載入成本過高 | 單服務改同源輕量頁／API | 同樣有效資料、較低峰值及耗時 | 不保證所有服務都適用或只耗數 MB |
| T8 | WebView 版本或 native 資源行為 | 獨立裝置／環境的版本比較與 profile | 差異隨版本／allocation 保留路徑出現 | 不得由 T0 無改善直接判定 allocator 碎片化 |

## 5. 執行步驟

### T0 — 最新版正常基準

1. 確認版本、安裝及登入狀態，沿用現有逐頁收集，不加任何清快取或主動回收。
2. 跑共通採樣；比較五輪底線與舊 +190 MB／輪基準，並標示兩次環境是否一致。
3. 若已收斂，先長跑驗證，不立即導入其他修正。若仍累積，依序進入 T1／T2。

### T1／T2 — RAM 快取與磁碟快取對照

1. T1 在每輪資料已收齊後、下一輪開始前，於主執行緒透過**尚未 destroy 的 WebView** 呼叫 `clearCache(false)`，之後走正常清理；保留各階段取樣時間一致。
2. T2 使用同一流程，但換成 `clearCache(true)`；其他設定保持一致。兩者只做測試開關，先不定為正式產品策略。
3. 記錄呼叫前後固定延遲的 RAM、下一輪峰值、空檔底線、網路下載量／耗時及磁碟快取指標。資料取不到時如實註明。
4. API 清的是 App 共用 WebView 資源快取，會影響其他 WebView，必須避免登入或其他抓取同時執行。不能在已 destroy 的物件上呼叫。
5. 不清 Cookie、網站儲存或 App 資料。HTTP 資源快取、Service Worker Cache Storage、IndexedDB、JavaScript 物件不是同一層；`clearCache` 不應被當成「清除所有記憶體」。
6. T1 有效：優先驗證最小清理策略，評估是否增加下載／耗電。T2 額外有效：重複對照，排除冷啟動和下載時序差異，再追查磁碟快取關係。兩者都無效：進 T3／T5，仍保留其他快取層未排除的可能。

「只改 LOAD_NO_CACHE」不是磁碟快取清除測試：它改變資源取得方式、增加網路工作，不能用來直接證明舊檔已刪。不要先把每輪清磁碟快取當成正式修法。

### T3 — 收集腳本剩餘邊界

先以真實腳本與合成回應建立測試，再一項一項修正並量測：

- 同 host 的無關 endpoint 應解析 0 次；先確認服務實際必需端點再建立白名單。
- hostname 以精確規則比對，避免字串包含；`claude.ai.example.invalid` 不應通過 claude.ai 規則。解析 URL 失敗時略過。
- XHR abort／error／timeout 後重用：下一次成功應只解析一次。`{once:true}` 在 load 沒發生時不會自行移除；評估每 instance 一個 listener 或各結束路徑清理。
- 原站 wrapper 委派回舊 fetch，補注入後同一 Response 仍只處理一次；可評估 document 共用 WeakSet 去重，避免強參照持有回應。
- DOM observer／timer 分支在同一 document 重注入後數量應受控；既有攔截器防護不等於所有 DOM 分支已去重。

上述前四種情境在獨立模擬中已重現現有腳本行為，未證明實機出現頻率或 RAM 貢獻。測試需涵蓋資料完整度；不能靠略過真正的帳務端點取得漂亮的 RAM 數字。

### T4 — 登入互斥

1. 比較現行流程與互斥版本，固定服務及操作節奏。
2. 測試進入、返回、取消、完成登入及登入 renderer 異常。完成後不得留下登入 WebView 或啟動第二條收集迴圈。
3. 進入登入前取消並清理背景工作，登入 WebView 釋放後才恢復；收集與 renderer 回收使用同一個生命週期協調機制。
4. 數全部 page／WebView，不只看 collector 的 map。記錄峰值及返回後底線，至少反覆 10 次，再長跑。

### T5／T8 — 逐服務隔離與資源歸屬

1. 各服務單獨跑固定週期，記錄資料成功與否；GitHub 主頁／budgets、OpenRouter credits／activity 等步驟分開標示。
2. 從最明顯的服務開始，另跑診斷輪，對比 JS heap、DOM／worker 數與 renderer private memory。
3. JS heap／DOM 回收後仍增加：追查物件保留路徑。JS 指標穩定而 native 指標上升：再查 native 配置、資源快取、worker 或 WebView 版本。
4. WebView 版本比較只在不干擾 OPUS 的獨立環境進行，不直接切換或降級使用中的系統 WebView。先記錄原版本與恢復方式。

### T6 — 計畫性 renderer 回收 PoC

只在 T0 證明仍累積且快取／低成本修正不足時優先評估；不要用它掩蓋未完成的基準量測。

1. API 使用 `getWebViewRenderProcess()`／`WebViewRenderProcess.terminate()`；專案 minSdk 31，平台 API 自 29 提供。不要使用測試用 `chrome://crash` 作正式策略。
2. 僅在資料接收完成、沒有登入 WebView、沒有其他收集任務的邊界回收；持久化必要 Cookie，但不假設能保存所有頁面記憶體狀態。
3. PoC 可保留最後一個已完成頁面到回收階段，取得 handle、標記 generation 為計畫回收，呼叫 terminate，再由相符 generation 的 callback 清理與通知完成。
4. 所有共用 renderer 的 WebView 均需處理 `onRenderProcessGone`。區分計畫回收與意外死亡；前者不增加失敗退避、不判定登出。
5. terminate=false、handle=null、callback 逾時與晚到都要處理；清理只做一次，舊 callback 不能取消新 step。terminate=true 不等同清理完成。
6. 確認舊 renderer 退出、新輪正確建新 renderer、App PID 不變、HTTP 服務與卡片仍正常、登入不丟失。
7. 比較冷啟動耗時與耗電。若單輪也超出裝置能力，輪末回收不足，需縮小單步成本或進 T7；不直接宣稱設定固定 1.4 GB 上限即可解決。

### T7 — 同源輕量頁／混合收集

1. 先選單一服務，例如 Claude usage，比較完整 SPA 與同源輕量頁 fetch；相同資料欄位、帳號與更新頻率。
2. 驗證 Cookie、CSRF／token、Cloudflare、重導向、登入到期與回復，不能假設 robots.txt 一定可用。
3. 檢查所需數值是否存在原始 HTML；需要 SPA 執行後產生的 DOM，不能僅以 DOMParser 取代。
4. 可直接使用授權 API 的服務採 HTTP；需要瀏覽器環境者保留 WebView。不要因單一服務成功而一次改完全部服務。
5. 對照資料一致性、峰值、每輪底線、耗時與多小時穩定性，再決定是否擴展。

## 6. 資料正確性旁支：Claude API 卡片空白

此問題和 RAM 分開驗證，但 RAM 測試每組均須檢查它，避免把沒抓到資料誤當成省記憶體。

現有路徑允許只回報 `plan` 就讓 collectStep 開始 20 秒安靜期，而卡片只有存在 `balance_usd` 時才顯示 plan badge。合成的 rate_limit_tier-only 回應已驗證可產生「有更新、無帳務列」；平板實際原因仍待確認。

- 測試方案名稱先到、餘額延遲超過 20 秒，以及合法餘額為 0 的情境。
- 為服務／頁面定義必要欄位完成條件；0 必須算有效值，不使用 truthy 判斷。
- 區分部分資料、收集完成、逾時；記錄缺少的欄位名稱，不記錄私密帳務內容。
- 保留硬逾時，不能因等待某個不適用的欄位而永久停住。

## 7. 執行順序與完成條件

1. T0：確認新版本是否已穩定；若穩定，進長跑，停止不必要改動。
2. T1 → T2：先用最小快取對照回答「是否舊資源沒清」。
3. T3、T4：各自獨立補強；每項有自己的基準及回歸驗證。
4. T5／T8：隔離成長來源；需要短期控制時，另做 T6。
5. T7：依每服務證據逐步降低載入成本。

自然收斂方案：暖機後空檔底線不再逐輪持續墊高、峰值保持在經該裝置驗證的安全範圍、無非計畫 renderer 死亡、資料與登入正確。

計畫回收方案：明確承認 renderer 定期換代；各代峰值範圍穩定、舊程序退出、空檔資源釋放、沒有非計畫 OOM、App 主程序不累積、資料與登入正確。

測試前依 T0 的自然波動與裝置餘裕訂立具體容許範圍；本計畫不把歷史約 1.5 GB 死亡點視為通用上限，也不預先承諾任一方案固定只需數十 MB。

## 8. 待辦與結果紀錄

- [x] 確認目前 commit／已安裝 APK／WebView 版本。（2026-09-28：已安裝 APK 內注入腳本 SHA-256 與 `19fbeda` 一致；WebView 143.0.7499.192，Android 16）
- [x] 完成 T0 最新版基準。（結果：斜率未改善，見下表與 §8.1）
- [x] 完成 T1 RAM 快取、T2 含磁碟快取對照。（兩者皆無差異，見 §8.2、§8.4）
- [ ] 完成 T3 腳本邊界及資料正確性驗證。
- [ ] 完成 T4 登入互斥驗證。
- [x] 完成 T5 逐服務隔離。（2026-09-29：每個服務都直線累積、加總 ≈ 全部一起跑，見 §8.5）
- [ ] 視需要完成 T8 profile（JS heap／DOM／worker 與 native 的歸屬）。
- [ ] 視需要完成 T6 回收 PoC。
- [ ] 視需要完成 T7 單服務輕量收集。
- [ ] 完成長跑、回歸與最終方案判定。

| 測試 | 日期／commit | 實際安裝確認 | 輪數／時長 | 峰值／空檔斜率 | 計畫／非計畫換代 | 資料／登入 | 結論與下一步 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| T0 | 2026-09-28 02:09–02:54／`19fbeda` | 安裝腳本雜湊一致；lastUpdateTime 2026-09-27 03:03:59 | 45 分鐘、兩代 renderer、約 8 輪完整週期 | 空檔底線（RSS）第一代 525→693→865→1,100→1,284 MB，平均 **+190 MB／輪**；第二代 393→664→859 MB | 0 計畫／1 非計畫（第一代約 29 分鐘、RSS 約 1.45 GB 時終止；App PID 不變） | 收集迴圈持續運作；量測中未檢視卡片內容 | **JS 修正未降低斜率** → 進 T1／T2 |
| T1 | 2026-09-28 03:37–04:22／`a80885e` + `-PcacheClearExperiment=ram` | 建置後確認 `CACHE_CLEAR_EXPERIMENT="ram"`；lastUpdateTime 03:37:33；logcat 每輪一筆 `clearCache(includeDiskFiles=false) ... done`（8／8 輪） | 45 分鐘、兩代 renderer、約 8 輪 | 空檔底線（RSS）第一代 548→728→923→1,108→1,272 MB，平均 **+181 MB／輪**；第二代 389→667→840 MB | 0 計畫／1 非計畫（第一代約 29 分鐘、RSS 約 1.35 GB／PSS 約 1.5 GB 時終止；App PID 不變） | 收集迴圈持續運作；量測中未檢視卡片內容 | **與 T0 無差異**：RAM 資源快取不是累積來源 → T2 |
| T2 | 2026-09-28 21:23–22:09／`e2048de` + `-PcacheClearExperiment=disk` | 建置後確認 `CACHE_CLEAR_EXPERIMENT="disk"`；已安裝 base.apk SHA-256 與建置產物一致；lastUpdateTime 21:22:43；logcat 每輪一筆 `clearCache(includeDiskFiles=true) ... done`（8／8 輪） | 46 分鐘、兩代 renderer、約 8 輪 | 空檔底線（RSS）第一代 574→772→971→1,158→1,325 MB，平均 **+188 MB／輪**；第二代 384→609→812 MB | 0 計畫／1 非計畫（第一代 29 分 04 秒、RSS 約 1.44 GB／PSS 約 1.5 GB 時終止；App PID 不變） | 收集迴圈持續運作；量測中未檢視卡片內容 | **與 T0／T1 無差異**：WebView 資源快取（RAM 與磁碟）都不是累積來源 → T5／T6 |
| T5 | 2026-09-28 22:33–2026-09-29 00:03（Claude.ai、ChatGPT）、2026-09-29 21:38–23:09（Claude API、OpenRouter、OpenAI）／含隔離開關與 OpenAI host 修正的 debug build（兩者後於 `01927db`、`7f210c4` commit） | 已安裝 base.apk 內 `ai-monitor-android.js` SHA-256 與 HEAD 一致（Kotlin 部分未逐位比對）；lastUpdateTime 2026-09-29 00:10:41；logcat 每段 `isolated to <key>` | 每服務 30 分鐘、12 輪，各一代 renderer | 空檔底線平均：ChatGPT **+83**、Claude.ai **+44**、OpenRouter **+37.5**、Claude API **+24**、OpenAI **+17** MB／輪；加總 +205 | 0／0（最高 RSS 1,237 MB，ChatGPT） | 補跑三段 37／37 步 `data=true`；09-28 的 OpenAI 段全無資料，作廢 | **每個服務都累積、皆不收斂**，加總 ≈ 一起跑 → 非單一服務問題 → T6 |

### 8.1 T0 細節（2026-09-28）

- 取樣：每 15 秒一筆；renderer 以 `u0a228i*` 程序紀錄確認歸屬（本次 PID 5909 → 10581）。原始 CSV 與取樣腳本留在被 Git 忽略的 `release/measurements/`。
- 第一代逐輪（RSS）：峰值 ≥764、959、1,061、1,324、1,475 MB；空檔底線 525、693、865、1,100、1,284 MB（增幅 +168、+172、+235、+184）。PSS 底線 433→1,303 MB，趨勢相同。第六輪進行到 ChatGPT → OpenAI 步驟時 renderer 被終止（約 1.45–1.50 GB），新 renderer 起始約 260 MB。
- 第二代：空檔底線 393、664、859 MB（+271、+195），斜率與第一代相同。
- 空檔窗口內，RSS 在頁面銷毀後 30–60 秒降到平台就不再下降；閒置期間不會歸還記憶體。
- 峰值時裝置 MemAvailable 最低約 454 MB（總 RAM 3.9 GB）。
- 與修正前（+190 MB／輪、約 23 分鐘換代）無可見差異：「攔截器重複 clone／重複注入」不是這個斜率的主因。依 §4，這**不能**直接推論為 allocator 碎片化；下一步為 T1／T2。

量測中順帶觀察（尚未驗證，另案處理）：

- OpenAI billing 步驟每輪都停留約 90 秒，等於 `PAGE_TIMEOUT_MS`，可能每輪都逾時而沒有回報資料。
- 整段量測都沒出現 GitHub Copilot 頁面；可能該服務在設定中停用，因 `run-as` 被擋而未確認。
- crash buffer 有一筆 2026-09-27 09:32 的 `ForegroundServiceDidNotStopInTimeException`（dataSync 前景服務逾時；Android 15+ 有 6 小時上限）。與本次量測無關，但會讓 App 被系統終止。已於 2026-09-28 修正並實機驗證（見 `DEVELOP.md`「已修復並驗證」）。

### 8.2 T1 細節（2026-09-28）

- 條件：App 由安裝後冷啟動、全程在前景；清除時機是每輪最後一頁（OpenRouter）回報後、銷毀前。
- 第一代逐輪（RSS／PSS 空檔底線）：548／444、728／653、923／862、1,108／1,081、1,272／1,318 MB。T0 同位置為 525／433、693／633、865／846、1,100／1,098、1,284／1,303 MB。每輪增幅 T1 平均 +181（RSS）／+219（PSS），T0 為 +190／+218。
- 第二代：T1 389、667、840 MB；T0 393、664、859 MB。
- renderer 壽命兩者都約 29 分鐘。清除後的空檔下降幅度（約 140–200 MB）也與 T0 空檔自然下降相當，看不出 `clearCache(false)` 的額外效果。
- 結論：RAM 資源快取不是每輪 +190 MB 的來源。依 §5，下一步 T2（`clearCache(true)`）。
- 量測瑕疵：第一次 T1 被 Android Studio 的 Run／Stop 中斷（03:34–03:35 兩次外部 `am force-stop`），已作廢重跑。重跑時前一次的取樣腳本未被終止，03:37–04:17 有兩個取樣器同時執行（每 15 秒兩次 `dumpsys meminfo`）。兩組數據一致，但取樣負載比 T0 高；T2 起需在開始前確認沒有殘留的取樣程序。

### 8.3 過夜觀察（2026-09-27 夜 → 2026-09-28 16:40，非對照測試）

T1 量測結束後 App 未停止，整夜放著跑；隔天以 logcat（events buffer 回溯至 09-27、system buffer 只到 09-28 09:15、main buffer 只到 15:33）與當下取樣回推。**不是依 §3 紀律設計的測試**，數據只作為線索，不列入上表。

- 安裝版本：T1 build（`-PcacheClearExperiment=ram`），lastUpdateTime 03:37:33；App PID 17133 自 03:37:35 起全程不變，crash buffer 無本 App 紀錄。
- 螢幕：04:27 關閉，16:31 才重新開啟（中間僅 09:33 亮 20 秒）。
- renderer 歸屬以 `am_proc_start` 的 `{com.example.ai_quota_monitor_android/...SandboxedProcessService0:N}` 及 `u0a228iNN` 確認。

| 時段 | App 狀態 | renderer 世代 |
| --- | --- | --- |
| 03:37–10:04 | 前景服務執行中；04:27 後螢幕關閉 | `:0`→`:12` 共 12 次換代，間隔 29–34 分鐘，與 T0／T1 相同；09:30、10:04 兩次死亡後 App 叫起 WebView `CrashReceiverService`（更早的死因已超出 system buffer） |
| 10:26 | dataSync 前景服務達上限（`am_foreground_service_timed_out`，約 6.8 小時）→ `STOP_SERVICE`，**App 未 crash** | —（`ForegroundServiceDidNotStopInTimeException` 修正在過夜條件下再次驗證） |
| 10:26–16:32 | 無前景服務、螢幕關閉；UID idle，App 為 cached（renderer adj 900） | `:12`（10:04 起）存活 **4h10m**，14:14 被 MIUI `ScreenOffCPUCheckKill`（1 小時內用 CPU 6.85% > 2%）終止，當時 RSS 約 815 MB，**不是 OOM**；`:13`（14:14 起）至 16:37 存活 2h22m，RSS 約 530 MB、PSS 約 587 MB（`Unknown` 約 497 MB），VmSwap 僅 33 MB |
| 16:32 起 | 使用者開螢幕 → `MainActivity.onStart` 重啟前景服務 | 同一個 `:13` 在 16:37→16:40 由 531 → 880 MB |

- 收集迴圈在無前景服務期間仍持續：main buffer 可見 15:38–16:35 每約 9.5 分鐘一筆 `AiQuotaMemExp`。依此頻率，`:13` 在開螢幕前至少跑了十餘輪，若維持 +190 MB／輪早應超過 1.5 GB。
- **線索（未驗證）**：App／renderer 在背景（低優先度、螢幕關閉）時，跨輪累積明顯變慢；回到前景後恢復快速增長。可能與 Chromium 對背景 renderer 的節流或記憶體回收有關，也可能是背景時頁面根本沒有完整載入或渲染。
- **未確認**：背景期間各卡片是否真的收到資料（未檢視卡片、main buffer 未涵蓋 10:26–15:33）；螢幕關閉但有前景服務的 03:37–10:04 仍照常換代，所以單純「螢幕關閉」不足以解釋。
- 可能的後續對照（排在 T2 之後，一次只改一項）：前景服務執行中但 Activity 在背景 vs 前景；取樣同時記錄各步驟是否回報資料。

### 8.4 T2 細節（2026-09-28）

- 條件與 T1 相同：安裝後冷啟動、全程前景、螢幕常亮；清除時機是每輪最後一頁（OpenRouter）回報後、銷毀前。開跑前以 Win32_Process 確認沒有殘留取樣器，全程只有一個取樣器。
- 第一代逐輪（RSS／PSS 空檔底線）：574／468、772／675、971／889、1,158／1,099、1,325／1,322 MB。每輪增幅平均 +188（RSS）／+214（PSS）；T1 為 +181／+219，T0 為 +190／+218。
- 逐輪峰值（RSS）：813、987、1,195、1,360、1,487 MB；T1 為 766、995、1,125、1,311、1,474 MB，T0 為 701、959、1,061、1,313、1,465 MB。T2 第 1、3 輪峰值略高（比 T0 多 112、134 MB），可能是清磁碟快取後重新下載資源，但單次量測不足以判定；第 5 輪峰值三者相同。
- 第二代：T2 384、609、812 MB；T1 389、667、840 MB；T0 393、664、859 MB。
- renderer 壽命 29 分 04 秒（21:23:11→21:52:15），與 T0／T1 相同。全程 MemAvailable 最低 487 MB；21:43 lowmemorykiller 回收了其他 App 的背景程序，本 App 未受影響，crash buffer 無本 App 紀錄。
- 每輪週期約 5 分 33 秒，與 T1 相同；看不出清磁碟快取造成可量測的額外下載耗時。網路下載量未取得。
- 磁碟快取指標**取不到**：`run-as` 被擋，`dumpsys diskstats` 為系統定期快照，量測前後都是 133.6 MB，未反映本次清除，不能作為證據。
- 結論：`clearCache(true)` 與 `clearCache(false)`、不清除三者斜率一致。WebView HTTP 資源快取（RAM 與磁碟）不是每輪 +190 MB 的來源。依 §5 第 6 點進 T3／T5；需要短期控制時另做 T6。仍未排除的快取層：Service Worker Cache Storage、IndexedDB、JS 物件（`clearCache` 不涵蓋）。
- 建置環境備註：預設 `JAVA_HOME` 指向的 Android Studio 內附 JBR 不完整（缺 `lib\jvm.cfg`），本次以 `JAVA_HOME` 暫指另一份完整的 JBR（OpenJDK 25.0.3）建置。只影響建置工具，不影響 APK 的 WebView 行為。

### 8.5 T5 細節（2026-09-28～29）

- 方法：debug build 以 `am start ... --es mem_isolate_service <key>` 只收集單一服務；每段先 `am force-stop` 再冷啟動，每 15 秒取樣 120 筆（30 分鐘）。收集週期在隔離時約 2 分 30 秒（步驟 + 120 秒間隔），比五服務一起跑（約 5 分 33 秒）短，所以比較單位是「每輪」而不是「每分鐘」。
- 逐輪空檔底線（RSS，MB）：

  | 服務 | 逐輪 | 平均／輪 | 後半段 | PSS 首→末 |
  | --- | --- | --- | --- | --- |
  | ChatGPT | 323、416、505、598、682、757、830、919、1,001、1,090、1,169、1,237 | +83.1 | +81.4 | 227→1,179 |
  | Claude.ai | 324、350、401、451、516、549、597、641、686、733、784、806 | +43.8 | +41.8 | 225→723 |
  | OpenRouter | 329、372、417、464、500、536、580、611、653、691、725、742 | +37.5 | +32.4 | 207→660 |
  | Claude API | 274、303、335、364、392、420、446、469、469、483、510、534 | +23.6 | +17.6 | 155→442 |
  | OpenAI | 224、243、261、277、295、312、330、346、363、380、396、408 | +16.7 | +15.6 | 113→296 |

- 資料：Claude API 12／12（32–37 秒）、OpenRouter 13／13（27–30 秒，每步含 credits、activity 兩頁）、OpenAI 12／12（28–32 秒）步驟 `data=true`。09-28 的 OpenAI 段因 host 白名單缺 `api.openai.com` 每步逾時 90 秒、無資料（+6.5 MB／輪），修正（`01927db`）後重跑，該段數據作廢。
- renderer：每段一代，無非計畫換代；補跑三段最高 RSS 分別 625、896、445 MB，MemAvailable 最低 721 MB。App PID 各段內不變。
- 判讀：
  1. 五項加總 +205 MB／輪，與 T0／T1／T2 的 +181～+190 同量級。累積是各頁面增量相加，沒有看到服務間交互作用；加總略高可能是各段起點不同或隔離時週期較短，不再細究。
  2. 沒有任何服務的斜率為 0 或趨緩，後半段與全程接近。依 §4 T5 的判定，成長並非「跟隨某服務」，而是**每個頁面載入都在 renderer 留下與頁面工作量相關的量**（同一 OpenAI 頁面，無資料 +6.5、有資料 +17）。
  3. 這支持「renderer 程序跨頁面保留」而非「特定網站洩漏」（頁面每輪都被銷毀，網站 JS 洩漏無法跨輪存活）。但仍未分辨是 JS heap 類（V8 isolate 層級）還是 native 配置器保留，需要 T8 profile 才能歸因。
- 下一步：T6（計畫性 renderer 回收）對所有服務一體有效，列為優先；T7 可降低 ChatGPT、Claude.ai 等重頁面的單輪成本，作為 T6 的補充，不是替代。

其餘測試執行時追加列。失敗、未執行、指標不可取得與不適用必須分開記錄；不填推測數據。

## 9. 官方參考

- [WebView.clearCache](https://developer.android.com/reference/android/webkit/WebView#clearCache(boolean))：false 只清 RAM 資源快取；資源快取由 App 內 WebView 共用。
- [WebSettings 快取模式](https://developer.android.com/reference/android/webkit/WebSettings#LOAD_DEFAULT)：快取有效時可重用資源；LOAD_NO_CACHE 不等於刪除既有磁碟檔案。
- [WebView 記憶體指南](https://developer.android.com/topic/performance/memory/guide/webview-memory)：App 與 renderer 分別計量，JS／native 資源需區分。
- [WebViewRenderProcess](https://developer.android.com/reference/android/webkit/WebViewRenderProcess)：正式 renderer 終止 API 及全部 WebView 必須處理回呼的要求。
- [CookieManager.flush](https://developer.android.com/reference/android/webkit/CookieManager#flush())：Cookie 持久化及同步 I/O。

[回計畫索引](README.MD)
