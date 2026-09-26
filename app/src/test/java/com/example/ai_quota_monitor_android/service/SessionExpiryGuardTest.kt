package com.example.ai_quota_monitor_android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Marking a service logged-out is sticky: it stops all background collection until the
 * user logs in by hand. A single login-page sighting is not enough evidence, because a
 * slow SPA, a token refresh or a bot check can transiently land on an auth URL.
 */
class SessionExpiryGuardTest {

    @Test
    fun `a single login page sighting is not reported as expiry`() {
        val guard = SessionExpiryGuard()

        assertFalse(guard.onLoginPageSeen("browser_claude_usage"))
    }

    @Test
    fun `two consecutive login page sightings report expiry`() {
        val guard = SessionExpiryGuard()
        guard.onLoginPageSeen("browser_claude_usage")

        assertTrue(guard.onLoginPageSeen("browser_claude_usage"))
    }

    @Test
    fun `a successful page load in between resets the evidence`() {
        val guard = SessionExpiryGuard()
        guard.onLoginPageSeen("browser_claude_usage")
        guard.onContentPageSeen("browser_claude_usage")

        assertFalse(guard.onLoginPageSeen("browser_claude_usage"))
    }

    @Test
    fun `evidence is tracked per service`() {
        val guard = SessionExpiryGuard()
        guard.onLoginPageSeen("browser_claude_usage")

        assertFalse(guard.onLoginPageSeen("browser_chatgpt_usage"))
    }

    @Test
    fun `expiry keeps being reported until a content page is seen`() {
        val guard = SessionExpiryGuard()
        guard.onLoginPageSeen("browser_openrouter")
        guard.onLoginPageSeen("browser_openrouter")

        assertTrue(guard.onLoginPageSeen("browser_openrouter"))
    }
}
