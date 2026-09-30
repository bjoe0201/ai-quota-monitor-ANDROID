package com.example.ai_quota_monitor_android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cloudflare rejects a UA that contradicts the rest of the WebView fingerprint
 * (`navigator.platform`, `userAgentData`), so the UA must claim Android and the real
 * Chromium major version — while still hiding `wv` (Google SSO) and `Mobile` (mobile layouts).
 */
class BrowserUserAgentTest {

    private val webViewDefault =
        "Mozilla/5.0 (Linux; Android 16; 24075RP89G Build/BP2A.250605.031.A3; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/154.0.8037.57 Safari/537.36"

    @Test
    fun `claims tablet Chrome with the WebView's real major version`() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/154.0.0.0 Safari/537.36",
            BrowserUserAgent.fromWebViewDefault(webViewDefault),
        )
    }

    @Test
    fun `hides the WebView and mobile markers`() {
        val ua = BrowserUserAgent.fromWebViewDefault(webViewDefault)
        assertFalse(ua.contains("wv"))
        assertFalse(ua.contains("Mobile"))
        assertFalse(ua.contains("Version/4.0"))
    }

    @Test
    fun `follows WebView updates`() {
        val ua = BrowserUserAgent.fromWebViewDefault(webViewDefault.replace("Chrome/154.0.8037.57", "Chrome/161.0.1.2"))
        assertTrue(ua.contains("Chrome/161.0.0.0 "))
    }

    @Test
    fun `falls back to a known version when the default UA is unavailable`() {
        assertTrue(BrowserUserAgent.fromWebViewDefault(null).contains("Chrome/154.0.0.0 "))
        assertTrue(BrowserUserAgent.fromWebViewDefault("garbage").contains("Chrome/154.0.0.0 "))
    }
}
