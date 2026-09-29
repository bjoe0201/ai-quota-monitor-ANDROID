# 02 — WebView renderer 記憶體累積：外部資料與判讀

| 項目 | 內容 |
| --- | --- |
| 整理日期 | 2026-09-28 |
| 相關文件 | [`01-webview-renderer-oom-crash.md`](01-webview-renderer-oom-crash.md)（根因與證據）、[`PLANS/03`](../PLANS/03-webview-memory-test-plan.md)（測試計畫與結果） |
| 用途 | 記錄查到的外部資料，以及它們對「renderer 跨輪累積」這個問題能說明什麼、不能說明什麼，供之後選方案時參考 |
| 狀態 | 參考資料。除非另外註明，這裡的內容**都不是**本專案實測過的結論 |

## TL;DR

- 查不到「claude.ai 網站本身有記憶體洩漏」的公開回報。搜尋 claude.ai memory leak 找到的幾乎都是 **Claude Code**（CLI 工具）的問題，和網站無關。
- 就算某個網站的 JS 有洩漏，也只會在**頁面存活期間**變大。本專案每輪收集完都會銷毀頁面（DevTools page target 回到 0），網站的 JS heap 理應隨之釋放，因此網站層級的洩漏**不足以解釋跨輪累積**。
- 較可能的方向是 **renderer 程序層級**的保留：Chromium 配置器（PartitionAlloc）的高水位與碎片，或程序層級的快取。這與 `dumpsys meminfo` 顯示增長幾乎都落在 `Unknown`（匿名 mmap）、`Native Heap` 只有數 MB 的觀察一致。
- Android 官方文件明說 `destroy()` 之後 RSS 沒回到原本水準是正常現象，只保證清掉 Activity 參照，不保證 renderer 歸還記憶體。

## 觸發這次查詢的觀察

T5（逐服務隔離）第一段只收集 Claude.ai：

| 項目 | 數值 |
| --- | --- |
| 時間 | 2026-09-28 22:33–23:03，App 冷啟動、前景、螢幕常亮 |
| 輪數 | 12 輪，每輪都有回報資料（`data=true`，約 27 秒） |
| 空檔底線（RSS） | 324→350→401→451→516→549→597→641→686→733→784→806 MB |
| 斜率 | 平均 **+44 MB／輪**，後半段 +42 MB／輪，近乎直線、不收斂 |

五個服務一起跑時為 +190 MB／輪。Claude.ai 單獨約佔 1/4。

**2026-09-29 補完其餘服務**（細節見 `PLANS/03` §8.5）：ChatGPT +83、Claude.ai +44、OpenRouter +37.5、Claude API +24、OpenAI +17 MB／輪，加總 +205，與一起跑的 +190 同量級。每個服務都直線累積、沒有一個收斂。這與下面「網站洩漏無法跨輪存活、較可能是 renderer 程序層級保留」的判讀一致。

## 外部資料

### 1. claude.ai 網站：無相關回報

- 以 "claude.ai memory leak browser tab" 等關鍵字搜尋，結果集中在 Claude Code 的高記憶體 issue，例如 [anthropics/claude-code #33735](https://github.com/anthropics/claude-code/issues/33735)、[#11315](https://github.com/anthropics/claude-code/issues/11315)。
- 這些是 CLI 程序本身的問題，與瀏覽器載入 claude.ai 網頁無關，**不適用**於本專案。
- 沒有找到 claude.ai 網頁分頁隨時間變肥的公開回報。「查不到」不代表不存在，但目前沒有證據指向網站。

### 2. Android 官方：destroy 後 RSS 不回落屬正常

來源：[Manage and diagnose WebView memory](https://developer.android.com/develop/ui/views/layout/webapps/manage-webview-memory)

- Android 8.0 起 WebView 內容跑在獨立的 sandboxed renderer 程序（`SandboxedProcessService`）。
- 官方建議的銷毀流程是：從 view hierarchy 移除、`stopLoading()`、`clearHistory()`、`destroy()`，再清掉參照。本專案的 `WebViewDataCollector.destroyService()` 已經這樣做。
- 文件指出 `destroy()` 之後 RSS 可能不會立刻回到載入 WebView 之前的水準，並稱這是正常行為（"This behavior is normal."）。`destroy()` 的主要目的是避免 Activity 洩漏，不是回收 renderer 記憶體。
- 文件建議用 `dumpsys meminfo`、Perfetto 或 Android Studio Profiler 評估總記憶體。

**判讀**：這支持「頁面確實銷毀了，但 renderer 程序留著記憶體」的解讀，也說明光靠正確的 destroy 流程無法解決跨輪累積。

### 3. Chromium PartitionAlloc：釋放後不一定還給系統

來源：[PartitionAlloc Design](https://chromium.googlesource.com/chromium/src/+/HEAD/base/allocator/partition_allocator/PartitionAlloc.md)、[Chromium memory docs](https://chromium.googlesource.com/chromium/src/+/master/docs/memory/README.md)、[Memory tools](https://chromium.googlesource.com/chromium/src.git/+/refs/heads/main/docs/memory/tools.md)

- renderer 的 native 配置大多經由 PartitionAlloc。
- 大型配置（約 1 MiB 以上）釋放後傾向較快歸還系統；小型配置釋放後多半保留給同一個 bucket 重用。
- 有第三方案例（[cypress-io/cypress #34226](https://github.com/cypress-io/cypress/issues/34226)）在大量導覽的長時間測試中耗盡 PartitionAlloc 的位址空間。那是 CDP 連線導致的特定回歸，情境不同，只能說明「多次導覽 + 同一程序」會把配置器推到極限。

**判讀**：可以解釋 renderer RSS 在頁面銷毀後停在高水位。但「每輪固定 +44 MB 而不是停在某個平台」代表還有東西在持續累積，碎片化本身未必足以解釋直線成長。依 `PLANS/03` §4，**不能**只憑這點就斷定是配置器碎片化。

### 4. 類似案例：小米平板 + Android 16 的 WebView 記憶體問題

來源：[shiaho777/web-to-app #1033](https://github.com/shiaho777/web-to-app/issues/1033)、[PR #1043](https://github.com/shiaho777/web-to-app/pull/1043)、[PR #1040](https://github.com/shiaho777/web-to-app/pull/1040)

- 環境：Xiaomi Pad 6S Pro、Android 16（HyperOS），與本專案的 Redmi 平板 + Android 16 相近。
- 症狀：長時間使用後系統變慢，最後 Launcher 因記憶體耗盡重啟。
- 他們判定的原因是 Activity 銷毀時沒有正確釋放 WebView。
- PR #1043 的做法：在 `onTrimMemory` 依等級分層處理——前景只修剪圖片快取；背景時清快取、清空 WebView pool；`TRIM_MEMORY_COMPLETE` 時整個拆掉 WebView，回到前景再重建。
- PR 內**沒有附量測數據**。

**判讀**：他們的原始缺陷（沒 destroy）本專案已排除。可借鏡的是「在系統要求時整個拆掉 WebView／renderer」這個方向，對應 `PLANS/03` 的 T6。

### 5. `setRendererPriorityPolicy`：影響誰先被砍，不影響累積

來源：[WebView.setRendererPriorityPolicy](https://developer.android.com/reference/android/webkit/WebView#setRendererPriorityPolicy(int,%20boolean))、[web-to-app PR #1040](https://github.com/shiaho777/web-to-app/pull/1040)

- 可設定 `RENDERER_PRIORITY_WAIVED`／`BOUND`／`IMPORTANT`；`waivedWhenNotVisible=true` 時，WebView 不可見就視同 `WAIVED`。
- 這決定 renderer 在記憶體壓力下是否優先被系統終止，**不會減少累積量**。
- 本專案的背景 WebView 不在畫面上，可能已經受預設策略影響，尚未確認。

**判讀**：可能和「過夜觀察：App 進背景後累積變慢」（`PLANS/03` §8.3）有關——renderer 優先度降低後，Chromium 或系統可能更積極回收記憶體。這只是假設，需要對照測試。

## 對方案選擇的意涵

| 候選 | 外部資料怎麼說 | 本專案現況 |
| --- | --- | --- |
| 正確 destroy | 官方標準做法，但不保證 renderer 歸還記憶體 | 已做，無法解決累積 |
| 清快取（`clearCache`） | 只涵蓋 HTTP 資源快取 | T1、T2 實測皆無效 |
| `onTrimMemory` 分級處理 | web-to-app 採用，無數據 | 未實作；可作為 T6 的觸發條件之一 |
| 計畫性結束 renderer（T6） | 與「整個拆掉 WebView」方向一致；`WebViewRenderProcess.terminate()` 自 API 29 提供 | 待 PoC |
| 同源輕量頁（T7） | 無外部資料 | 從源頭減少每輪載入量 |

## 待確認

- ~~T5 其餘服務單獨的斜率，以及加總是否接近 +190 MB／輪~~（2026-09-29：加總 +205，接近）
- ~~增量是與頁面工作量相關，還是集中在特定服務~~（2026-09-29：五個服務都累積，大小依頁面而異；同一 OpenAI 頁有資料時 +17、無資料時 +6.5 → 支持「每次載入都留下一點」）
- 保留下來的是 JS heap（V8）還是 native 配置器記憶體（T8 profile）。
- 背景／低優先度時累積變慢的現象是否可重現，與 renderer priority 是否有關。
