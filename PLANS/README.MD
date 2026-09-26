# PLANS — 計畫索引

本目錄集中保存專案的設計、移植與診斷測試計畫。更新日期：2026-09-27。

## 檔案清單

| 編號 | 檔案 | 簡介 | 狀態 |
| --- | --- | --- | --- |
| 01 | [01-migration-plan.md](01-migration-plan.md) | 原始桌面額度監控工具移植至 Android 的架構、資料流、UI 與開發工作規劃。 | 既有計畫；本次僅改名，未重新核定完成度 |
| 02 | [02-claude-ai-new-route-v4.4.3-android.md](02-claude-ai-new-route-v4.4.3-android.md) | Claude.ai Usage 新 SPA hash 路由的 Android 相容修改與驗證項目。 | 既有計畫；本次僅改名，未重新核定完成度 |
| 03 | [03-webview-memory-test-plan.md](03-webview-memory-test-plan.md) | WebView RAM 累積的假說與對照測試：最新版基準、RAM／磁碟快取、腳本邊界、登入互斥、逐服務隔離、renderer 回收及輕量收集；另含 Claude API 卡片資料完整性驗證。 | 待執行；已知歷史量測與本次模擬結果在內文分開標示 |

## 閱讀順序

- 處理目前 RAM 問題：先讀 [03 測試計畫](03-webview-memory-test-plan.md)，再核對 [DEVELOP.md](../DEVELOP.md) 的最新交接及 [OOM 診斷紀錄](../docs/01-webview-renderer-oom-crash.md)。
- 理解整體設計：讀 [01 移植計畫](01-migration-plan.md)。
- 查 Claude.ai 路由相容：讀 [02 路由計畫](02-claude-ai-new-route-v4.4.3-android.md)。

## 命名與維護方式

- 計畫使用 `NN-short-description.md`，以至少兩位數編號，例如 `01-migration-plan.md`。索引檔固定為 `README.MD`，不編號。
- 新增計畫使用下一個未使用編號，並同步更新本表；既有編號保持穩定，不因狀態改變重新排序。
- 計畫內記錄基準版本、目的、步驟、驗收與結果；明確區分待測假說、既有證據及新量測。
- 重新命名時同步更新引用。原始量測、Cookie、token、帳號與帳務資料不得放進公開計畫。

## 本次名稱整理

| 原名稱 | 新名稱 |
| --- | --- |
| `migration-plan.md` | `01-migration-plan.md` |
| `claude-ai-new-route-v4.4.3-android.md` | `02-claude-ai-new-route-v4.4.3-android.md` |

本次保留兩份既有計畫內容，只新增編號及更新引用；新建 03 測試計畫與本索引。未執行計畫內的實機測試。
