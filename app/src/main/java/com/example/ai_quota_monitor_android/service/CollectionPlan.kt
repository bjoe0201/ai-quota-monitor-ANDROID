package com.example.ai_quota_monitor_android.service

import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.effectiveServiceOrder

/** One page to load, collect from, and then tear down. */
data class CollectionStep(
    val serviceKey: String,
    val url: String,
    val sourceKey: String,
)

/** Second WebView backing the GitHub Copilot card; not a service of its own. */
const val GITHUB_BUDGETS_KEY = "browser_github_copilot_budgets"

private const val GITHUB_COPILOT_KEY = "browser_github_copilot"
private const val GITHUB_BUDGETS_URL = "https://github.com/settings/billing/budgets"

/**
 * The ordered plan for one collection cycle.
 *
 * Only one page is loaded at a time, so the cycle walks this list and tears each page down
 * before moving on. Cards in the user's display order are collected in that same order.
 *
 * [only] restricts the plan to one service key, for the per-service memory measurement
 * (PLANS/03 T5); that service is still skipped if it would not normally be collected.
 */
fun collectionSteps(config: DashboardConfig, only: String? = null): List<CollectionStep> {
    val steps = mutableListOf<CollectionStep>()
    for (key in config.effectiveServiceOrder()) {
        if (only != null && key != only) continue
        val svc = config.services[key] ?: continue
        if (!svc.enabled || svc.url.isEmpty()) continue
        if (config.authStatus[key]?.loggedIn != true) continue

        steps += CollectionStep(key, svc.url, svc.sourceKey)
        // GitHub Copilot needs a second page; both feed the same DataStore key.
        if (key == GITHUB_COPILOT_KEY) {
            steps += CollectionStep(GITHUB_BUDGETS_KEY, GITHUB_BUDGETS_URL, svc.sourceKey)
        }
    }
    return steps
}
