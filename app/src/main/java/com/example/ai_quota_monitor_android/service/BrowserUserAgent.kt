package com.example.ai_quota_monitor_android.service

import android.content.Context
import android.webkit.WebSettings

/**
 * The UA every WebView in the app presents — login pages and background pages alike, because
 * Cloudflare's clearance cookie is bound to the UA that earned it.
 *
 * It claims tablet Chrome on Android with the WebView's real Chromium major version, so it
 * agrees with the rest of the fingerprint (`navigator.platform`, `userAgentData`); a desktop
 * UA contradicted both and left chatgpt.com stuck in an endless Turnstile loop. The WebView
 * `wv` marker is dropped because Google SSO refuses embedded WebViews, and there is no
 * `Mobile` token so services keep serving their desktop layout.
 */
object BrowserUserAgent {

    /** Used when the WebView's own UA can't be read or parsed. */
    private const val FALLBACK_CHROME_MAJOR = "154"

    private val chromeMajor = Regex("""Chrome/(\d+)""")

    @Volatile private var cached: String? = null

    fun forDevice(context: Context): String = cached ?: fromWebViewDefault(
        runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull(),
    ).also { cached = it }

    fun fromWebViewDefault(defaultUa: String?): String {
        val major = defaultUa?.let { chromeMajor.find(it)?.groupValues?.get(1) } ?: FALLBACK_CHROME_MAJOR
        return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$major.0.0.0 Safari/537.36"
    }
}
