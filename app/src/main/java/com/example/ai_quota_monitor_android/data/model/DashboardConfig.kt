package com.example.ai_quota_monitor_android.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class DashboardLayout { A, B, C, D }

@Serializable
enum class ThemeMode { Dark, Light }

@Serializable
data class DashboardConfig(
    val autoRefreshMinutes: Int = 5,
    val serverPort: Int = 7890,
    val serverEnabled: Boolean = true,
    val sections: List<SectionConfig> = defaultSections(),
    val services: Map<String, ServiceConfig> = defaultServices(),
    val authStatus: Map<String, AuthStatus> = emptyMap(),
    val collapsedCards: Set<String> = emptySet(),
    val dashboardLayout: DashboardLayout = DashboardLayout.A,
    val themeMode: ThemeMode = ThemeMode.Dark,
    /** Explicit display order for service keys. Empty = use defaultServices() insertion order. */
    val serviceOrder: List<String> = emptyList(),
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
    val loginUrl: String = "",
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
            CardConfig(type = "service", id = "c4", serviceKey = "browser_openrouter", span = 1),
            CardConfig(type = "service", id = "c5", serviceKey = "browser_claude_billing", span = 2),
        ),
    ),
)

fun defaultServices(): Map<String, ServiceConfig> = mapOf(
    "browser_claude_usage" to ServiceConfig(
        displayName = "Claude.ai",
        sourceKey = "claude_usage",
        url = "https://claude.ai/new#settings/usage",
        loginUrl = "https://claude.ai/login",
    ),
    "browser_github_copilot" to ServiceConfig(
        displayName = "GitHub Copilot",
        sourceKey = "github_copilot",
        url = "https://github.com/settings/copilot",
        loginUrl = "https://github.com/login",
    ),
    "browser_openai" to ServiceConfig(
        displayName = "OpenAI",
        sourceKey = "openai_billing",
        url = "https://platform.openai.com/settings/organization/billing/overview",
        loginUrl = "https://platform.openai.com/login",
    ),
    "browser_claude_billing" to ServiceConfig(
        displayName = "Claude API",
        sourceKey = "claude_billing",
        url = "https://platform.claude.com/settings/billing",
        loginUrl = "https://platform.claude.com/login",
    ),
    "browser_openrouter" to ServiceConfig(
        displayName = "OpenRouter",
        sourceKey = "openrouter",
        url = "https://openrouter.ai/settings/credits",
        loginUrl = "https://openrouter.ai",
    ),
)

/**
 * Returns service keys in the user's preferred order.
 * Falls back to the map's insertion order if serviceOrder is empty.
 * Filters out any stale keys that no longer exist in services.
 */
fun DashboardConfig.effectiveServiceOrder(): List<String> {
    val base = if (serviceOrder.isEmpty()) services.keys.toList() else serviceOrder
    return base.filter { it in services }
}

/**
 * Returns only enabled service keys in the user's preferred order.
 */
fun DashboardConfig.enabledServiceKeys(): List<String> =
    effectiveServiceOrder().filter { services[it]?.enabled != false }
