# docs

本目錄存放**技術文件**：疑難排解記錄、根因分析、以及給 agent 用的工作規範。

- 版本歷程請看根目錄 `CHANGELOG.md`
- 尚未執行的變更計畫請看 `PLANS/`
- 專案總覽與建置／發佈流程請看根目錄 `README.md` 與 `CLAUDE.md`

> 目錄名稱在版控中一律為小寫 `docs/`。Windows 檔案系統不分大小寫，另建 `DOCS/` 會造成 git 路徑大小寫衝突。

## 技術文件索引

依診斷／撰寫時間順序編號，編號一經指定不再變更。

| 編號 | 文件 | 主題 | 狀態 |
| --- | --- | --- | --- |
| 01 | [WebView Renderer OOM 導致 App 整個關閉](01-webview-renderer-oom-crash.md) | 背景 WebView 共用的 renderer 記憶體耗盡而死亡，且未實作 `onRenderProcessGone()`，導致整個 App 被終止。含完整證據、修復內容、可重現的驗證步驟與診斷指令 | 韌性缺陷已修復；記憶體模型缺陷待處理 |

## 命名規則

- 檔名格式：`NN-kebab-case-主題.md`，`NN` 為兩位數流水號（`01`、`02`…）
- 一份文件只談一個問題或一個主題
- 新增文件後，務必在上表補一列

### 建議結構

疑難排解類文件請盡量包含以下段落，讓後續的人能重現而不只是閱讀：

1. 開頭摘要表（日期、影響版本、症狀、根因、狀態）
2. TL;DR
3. 症狀與測試環境
4. 證據（實際指令輸出、數據、時間線）
5. 根因（區分不同缺陷層次）
6. 修復內容（檔案清單與行為）
7. 驗證方法（可重現步驟 + 修復前後對照）
8. 尚未修復的部分與候選方案
9. 附帶發現
10. 診斷指令參考

## 公開儲存庫注意事項

本儲存庫是公開的。撰寫文件時不得寫入 API key、token、cookie、session 資料、憑證、裝置序號，或含使用者名稱的本機絕對路徑。指令範例請使用相對路徑或 `<PLACEHOLDER>` 佔位符。

## Agent 工作規範

| 文件 | 用途 |
| --- | --- |
| [agents/issue-tracker.md](agents/issue-tracker.md) | Issue 與 PRD 追蹤方式（GitHub Issues + `gh` CLI） |
| [agents/triage-labels.md](agents/triage-labels.md) | 五個預設 triage 標籤的定義 |
| [agents/domain.md](agents/domain.md) | 單一 context 儲存庫的領域說明 |

## 其他資產

| 檔案 | 用途 |
| --- | --- |
| `app-icon.svg` | App 圖示原始向量檔 |
