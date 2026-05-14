package com.example.ai_quota_monitor_android.data.model

import kotlinx.serialization.Serializable

@Serializable
data class DashboardConfig(
    val autoRefreshMinutes: Int = 30,
    val serverPort: Int = 7890,
    val serverEnabled: Boolean = true,
    val sections: List<SectionConfig> = defaultSections(),
    val services: Map<String, ServiceConfig> = defaultServices(),
    val authStatus: Map<String, AuthStatus> = emptyMap(),
)

@Serializable
data class SectionConfig(
    val id: String,
    val name: String,
    val columns: Int = 1,
    val cards: List<CardConfig> = emptyList(),
)

@Serializable
data class CardConfig(
    val type: String, // "clock" or "service"
    val id: String,
    val serviceKey: String? = null,
    val span: Int = 1,
)

@Serializable
data class ServiceConfig(
    val enabled: Boolean = true,
    val displayName: String = "",
    val sourceKey: String = "",
    val url: String = "",
)

@Serializable
data class AuthStatus(
    val loggedIn: Boolean = false,
    val lastLogin: String? = null,
    val lastSuccess: String? = null,
    val cookieDomain: String? = null,
)

fun defaultSections(): List<SectionConfig> = listOf(
    SectionConfig(
        id = "clock",
        name = "時鐘",
        columns = 1,
        cards = listOf(CardConfig(type = "clock", id = "main_clock")),
    ),
    SectionConfig(
        id = "monitors",
        name = "主要監控",
        columns = 2,
        cards = listOf(
            CardConfig(type = "service", id = "c1", serviceKey = "browser_claude_usage", span = 1),
            CardConfig(type = "service", id = "c2", serviceKey = "browser_github_copilot", span = 1),
            CardConfig(type = "service", id = "c3", serviceKey = "browser_openai", span = 1),
            CardConfig(type = "service", id = "c4", serviceKey = "browser_claude_billing", span = 1),
            CardConfig(type = "service", id = "c5", serviceKey = "browser_openrouter", span = 2),
        ),
    ),
)

fun defaultServices(): Map<String, ServiceConfig> = mapOf(
    "browser_claude_usage" to ServiceConfig(
        displayName = "Claude.ai",
        sourceKey = "claude_usage",
        url = "https://claude.ai/settings/usage",
    ),
    "browser_github_copilot" to ServiceConfig(
        displayName = "GitHub Copilot",
        sourceKey = "github_copilot",
        url = "https://github.com/settings/billing/premium_requests_usage",
    ),
    "browser_openai" to ServiceConfig(
        displayName = "OpenAI",
        sourceKey = "openai_billing",
        url = "https://platform.openai.com/settings/organization/billing/overview",
    ),
    "browser_claude_billing" to ServiceConfig(
        displayName = "Claude API",
        sourceKey = "claude_billing",
        url = "https://platform.claude.com/settings/billing",
    ),
    "browser_openrouter" to ServiceConfig(
        displayName = "OpenRouter",
        sourceKey = "openrouter",
        url = "https://openrouter.ai/settings/credits",
    ),
)
