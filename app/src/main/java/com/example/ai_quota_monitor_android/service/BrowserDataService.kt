package com.example.ai_quota_monitor_android.service

import com.example.ai_quota_monitor_android.data.model.ServiceResult
import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import java.time.Duration
import java.time.Instant

private const val STALE_THRESHOLD_SEC = 600L // 10 minutes

private fun staleWarning(receivedAt: String): String? {
    return try {
        val dt = Instant.parse(receivedAt)
        val age = Duration.between(dt, Instant.now()).seconds
        if (age > STALE_THRESHOLD_SEC) {
            "（資料已 ${age / 60} 分鐘未更新，請確認瀏覽器頁面仍開啟）"
        } else null
    } catch (_: Exception) {
        null
    }
}

private fun tsDisplay(receivedAt: String): String {
    return try {
        val dt = Instant.parse(receivedAt)
        val local = dt.atZone(java.time.ZoneId.systemDefault())
        "%02d:%02d:%02d".format(local.hour, local.minute, local.second)
    } catch (_: Exception) {
        receivedAt
    }
}

class BrowserOpenAIService : BaseService() {
    override val name = "OpenAI 帳單 (瀏覽器)"
    override val sourceKey = "openai_billing"
    override fun fetch() = fetchBrowserData(name, sourceKey)
}

class BrowserClaudeUsageService : BaseService() {
    override val name = "Claude.ai 用量 (瀏覽器)"
    override val sourceKey = "claude_usage"
    override fun fetch() = fetchBrowserData(name, sourceKey)
}

class BrowserClaudeBillingService : BaseService() {
    override val name = "Claude API 帳單 (瀏覽器)"
    override val sourceKey = "claude_billing"
    override fun fetch() = fetchBrowserData(name, sourceKey)
}

class BrowserGitHubCopilotService : BaseService() {
    override val name = "GitHub Copilot (瀏覽器)"
    override val sourceKey = "github_copilot"
    override fun fetch() = fetchBrowserData(name, sourceKey)
}

class BrowserOpenRouterService : BaseService() {
    override val name = "OpenRouter (瀏覽器)"
    override val sourceKey = "openrouter"
    override fun fetch() = fetchBrowserData(name, sourceKey)
}

class BrowserChatGptUsageService : BaseService() {
    override val name = "ChatGPT 用量 (瀏覽器)"
    override val sourceKey = "chatgpt_usage"
    override fun fetch() = fetchBrowserData(name, sourceKey)
}

private fun fetchBrowserData(serviceName: String, sourceKey: String): ServiceResult {
    val raw = DataStoreRepository.getData(sourceKey)
        ?: return ServiceResult(
            serviceName = serviceName,
            success = false,
            error = "等待瀏覽器連線...\n請在瀏覽器開啟對應頁面（已安裝 Tampermonkey 腳本）",
        )

    val data = raw.toMutableMap()
    val recv = data["received_at"]?.toString() ?: ""
    data["updated_at"] = tsDisplay(recv)
    staleWarning(recv)?.let { data["stale_warning"] = it }

    return ServiceResult(serviceName = serviceName, success = true, data = data)
}

/** All browser service instances, ordered for display. */
val ALL_BROWSER_SERVICES: List<Pair<String, BaseService>> = listOf(
    "browser_claude_usage" to BrowserClaudeUsageService(),
    "browser_github_copilot" to BrowserGitHubCopilotService(),
    "browser_openai" to BrowserOpenAIService(),
    "browser_claude_billing" to BrowserClaudeBillingService(),
    "browser_openrouter" to BrowserOpenRouterService(),
    "browser_chatgpt_usage" to BrowserChatGptUsageService(),
)

/** Maps service key to DATA_STORE source key. */
val BROWSER_SERVICE_SOURCES: Map<String, String> = mapOf(
    "browser_claude_usage" to "claude_usage",
    "browser_github_copilot" to "github_copilot",
    "browser_openai" to "openai_billing",
    "browser_claude_billing" to "claude_billing",
    "browser_openrouter" to "openrouter",
    "browser_chatgpt_usage" to "chatgpt_usage",
)
