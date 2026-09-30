package com.example.ai_quota_monitor_android.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A background page can never pass Cloudflare's challenge, so a service stuck behind it is
 * flagged for the user; the flag must clear as soon as the page gets through again.
 */
class CloudflareChallengeTest {

    private val chatgpt = "browser_chatgpt_usage"

    @Test
    fun `a step that ends on the challenge without data flags the service`() {
        assertEquals(setOf(chatgpt), CloudflareChallenge.afterStep(emptySet(), chatgpt, challengeSeen = true, gotData = false))
    }

    @Test
    fun `data clears the flag, even if the page passed through a challenge first`() {
        assertEquals(emptySet<String>(), CloudflareChallenge.afterStep(setOf(chatgpt), chatgpt, challengeSeen = true, gotData = true))
        assertEquals(emptySet<String>(), CloudflareChallenge.afterStep(setOf(chatgpt), chatgpt, challengeSeen = false, gotData = true))
    }

    @Test
    fun `a step that failed for another reason leaves the flag as it was`() {
        assertEquals(setOf(chatgpt), CloudflareChallenge.afterStep(setOf(chatgpt), chatgpt, challengeSeen = false, gotData = false))
        assertEquals(emptySet<String>(), CloudflareChallenge.afterStep(emptySet(), chatgpt, challengeSeen = false, gotData = false))
    }

    @Test
    fun `other services keep their flags`() {
        val other = "browser_claude_usage"
        assertEquals(setOf(other), CloudflareChallenge.afterStep(setOf(other, chatgpt), chatgpt, challengeSeen = false, gotData = true))
    }

    @Test
    fun `only the true result of the detector counts`() {
        assertEquals(true, CloudflareChallenge.isChallenge("true"))
        assertEquals(false, CloudflareChallenge.isChallenge("false"))
        assertEquals(false, CloudflareChallenge.isChallenge("null"))
        assertEquals(false, CloudflareChallenge.isChallenge(null))
    }
}
