package com.example.ai_quota_monitor_android.service

import com.example.ai_quota_monitor_android.data.model.AuthStatus
import com.example.ai_quota_monitor_android.data.model.DashboardConfig
import com.example.ai_quota_monitor_android.data.model.ServiceConfig
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Background pages are collected one at a time, so the cycle needs an explicit ordered plan
 * instead of loading every logged-in service at once.
 */
class CollectionPlanTest {

    private fun config(
        services: Map<String, ServiceConfig>,
        loggedIn: List<String> = services.keys.toList(),
        order: List<String> = emptyList(),
    ) = DashboardConfig(
        services = services,
        authStatus = loggedIn.associateWith { AuthStatus(loggedIn = true) },
        serviceOrder = order,
    )

    private fun service(sourceKey: String, url: String = "https://example.test/$sourceKey") =
        ServiceConfig(sourceKey = sourceKey, url = url)

    @Test
    fun `each enabled logged-in service becomes one step in display order`() {
        val cfg = config(
            services = mapOf(
                "browser_openai" to service("openai_billing"),
                "browser_openrouter" to service("openrouter"),
            ),
            order = listOf("browser_openrouter", "browser_openai"),
        )

        assertEquals(
            listOf(
                CollectionStep("browser_openrouter", "https://example.test/openrouter", "openrouter"),
                CollectionStep("browser_openai", "https://example.test/openai_billing", "openai_billing"),
            ),
            collectionSteps(cfg),
        )
    }

    @Test
    fun `a disabled service is not collected`() {
        val cfg = config(
            services = mapOf(
                "browser_openai" to service("openai_billing").copy(enabled = false),
            ),
        )

        assertEquals(emptyList<CollectionStep>(), collectionSteps(cfg))
    }

    @Test
    fun `a logged-out service is not collected`() {
        val cfg = config(
            services = mapOf("browser_openai" to service("openai_billing")),
            loggedIn = emptyList(),
        )

        assertEquals(emptyList<CollectionStep>(), collectionSteps(cfg))
    }

    @Test
    fun `a service without a url is not collected`() {
        val cfg = config(
            services = mapOf("browser_openai" to service("openai_billing", url = "")),
        )

        assertEquals(emptyList<CollectionStep>(), collectionSteps(cfg))
    }

    @Test
    fun `github copilot also collects the budgets page under the same source key`() {
        val cfg = config(
            services = mapOf(
                "browser_github_copilot" to service("github_copilot", "https://github.com/settings/copilot"),
            ),
        )

        val steps = collectionSteps(cfg)

        assertEquals(2, steps.size)
        assertEquals(CollectionStep("browser_github_copilot", "https://github.com/settings/copilot", "github_copilot"), steps[0])
        assertEquals("github_copilot", steps[1].sourceKey)
        assertEquals("https://github.com/settings/billing/budgets", steps[1].url)
    }

    @Test
    fun `isolating one service collects only that service`() {
        val cfg = config(
            services = mapOf(
                "browser_openai" to service("openai_billing"),
                "browser_openrouter" to service("openrouter"),
            ),
        )

        assertEquals(
            listOf(CollectionStep("browser_openrouter", "https://example.test/openrouter", "openrouter")),
            collectionSteps(cfg, only = "browser_openrouter"),
        )
    }

    @Test
    fun `isolating github copilot keeps its budgets page`() {
        val cfg = config(
            services = mapOf(
                "browser_github_copilot" to service("github_copilot", "https://github.com/settings/copilot"),
                "browser_openai" to service("openai_billing"),
            ),
        )

        assertEquals(
            listOf("browser_github_copilot", GITHUB_BUDGETS_KEY),
            collectionSteps(cfg, only = "browser_github_copilot").map { it.serviceKey },
        )
    }

    @Test
    fun `isolating a service that would not be collected yields no steps`() {
        val cfg = config(
            services = mapOf("browser_openai" to service("openai_billing")),
            loggedIn = emptyList(),
        )

        assertEquals(emptyList<CollectionStep>(), collectionSteps(cfg, only = "browser_openai"))
    }
}
