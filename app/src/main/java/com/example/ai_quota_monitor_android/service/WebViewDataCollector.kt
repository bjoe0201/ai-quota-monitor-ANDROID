package com.example.ai_quota_monitor_android.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import org.json.JSONObject

/**
 * Manages background WebViews that load AI service pages and inject JS
 * to intercept API responses. Data is passed back via @JavascriptInterface.
 */
class WebViewDataCollector(private val context: Context) {

    private val webViews = mutableMapOf<String, WebView>()
    private val serviceUrls = mutableMapOf<String, String>()
    private var onSessionExpired: ((String) -> Unit)? = null
    private var jsScript: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val recoveryPolicy = RendererRecoveryPolicy { SystemClock.elapsedRealtime() }
    private val expiryGuard = SessionExpiryGuard()

    /** Services whose renderer died and that still have to be reloaded. */
    private val pendingRecovery = linkedSetOf<String>()
    private var recoveryScheduled = false

    /** Asked before a dead service is reloaded, so disabled/logged-out services stay down. */
    private var shouldReload: (String) -> Boolean = { true }

    fun setOnSessionExpired(listener: (String) -> Unit) {
        onSessionExpired = listener
    }

    fun setShouldReload(predicate: (String) -> Boolean) {
        shouldReload = predicate
    }

    private fun getJsScript(): String {
        if (jsScript == null) {
            jsScript = try {
                context.assets.open("ai-monitor-android.js").bufferedReader().readText()
            } catch (_: Exception) {
                // Fallback: minimal script that just calls the bridge
                ""
            }
        }
        return jsScript!!
    }

    /**
     * Create a full-screen WebView for user login.
     * Cookie will be persisted by CookieManager.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun createLoginWebView(serviceKey: String, url: String, onLoginDetected: () -> Unit): WebView {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        return WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.userAgentString = DESKTOP_UA
            suppressRequestedWithHeader(this)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    cookieManager.flush()
                    // If we're back on the target URL (not login page), login succeeded
                    if (!isLoginPage(serviceKey, loadedUrl) && loadedUrl.contains(getExpectedDomain(serviceKey))) {
                        onLoginDetected()
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // Returning true keeps the app alive; a WebView whose renderer is gone
                    // can never be reused, so it is only destroyed here.
                    view.destroy()
                    return true
                }
            }
            @SuppressLint("JavascriptInterface")
            this.also { cookieManager.setAcceptThirdPartyCookies(it, true) }
            loadUrl(url)
        }
    }

    /**
     * Load a service page in background WebView using saved cookies.
     * Injects JS to intercept API data.
     *
     * Injection strategy:
     *  - onPageStarted : inject early so fetch/XHR hooks are in place before SPA JS runs
     *  - onPageFinished: re-inject as a safety net (page may have replaced window.fetch)
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun loadService(serviceKey: String, url: String) {
        // Destroy previous WebView for this service
        webViews[serviceKey]?.destroy()

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.userAgentString = DESKTOP_UA
            suppressRequestedWithHeader(this)
            addJavascriptInterface(DataBridge(serviceKey), "AndroidBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, loadedUrl: String, favicon: android.graphics.Bitmap?) {
                    // Inject early so hooks are set before page's own JS fetches data
                    if (!isLoginPage(serviceKey, loadedUrl)) {
                        val script = getJsScript()
                        if (script.isNotEmpty()) {
                            view.evaluateJavascript(script, null)
                        }
                    }
                }

                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    if (isLoginPage(serviceKey, loadedUrl)) {
                        // One sighting is not enough: a token refresh or bot check can land
                        // here transiently, and marking a service logged out is sticky.
                        if (expiryGuard.onLoginPageSeen(serviceKey)) {
                            onSessionExpired?.invoke(serviceKey)
                        }
                        return
                    }
                    expiryGuard.onContentPageSeen(serviceKey)
                    // Re-inject on finish as safety net (some SPAs replace fetch after first inject)
                    val script = getJsScript()
                    if (script.isNotEmpty()) {
                        view.evaluateJavascript(script, null)
                    }
                }

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    handleRendererGone(serviceKey, view)
                    return true
                }
            }
            @SuppressLint("JavascriptInterface")
            this.also { cookieManager.setAcceptThirdPartyCookies(it, true) }
        }
        serviceUrls[serviceKey] = url
        wv.loadUrl(url)
        webViews[serviceKey] = wv
    }

    /**
     * Handle the death of the renderer process shared by every background WebView.
     *
     * Without this the WebView layer deliberately kills the whole app process
     * ("Render process ... crash wasn't handled by all associated webviews"). The dead
     * WebView is unusable and must be replaced, but reloading every page at once would
     * recreate the memory pressure that killed the renderer, so recovery is coalesced,
     * staggered and backed off by [RendererRecoveryPolicy].
     */
    private fun handleRendererGone(serviceKey: String, view: WebView) {
        if (webViews[serviceKey] === view) {
            webViews.remove(serviceKey)
        }
        view.destroy()

        if (serviceUrls.containsKey(serviceKey)) {
            pendingRecovery += serviceKey
        }
        if (!recoveryScheduled) {
            recoveryScheduled = true
            // Every WebView sharing the renderer reports separately; collect them first.
            mainHandler.postDelayed({ startRecovery() }, COALESCE_MS)
        }
    }

    private fun startRecovery() {
        recoveryScheduled = false
        val keys = pendingRecovery.filter { shouldReload(it) }
        pendingRecovery.clear()
        if (keys.isEmpty()) return

        val schedule = recoveryPolicy.scheduleFor(keys.size)
        keys.forEachIndexed { index, key ->
            mainHandler.postDelayed({
                val url = serviceUrls[key] ?: return@postDelayed
                if (shouldReload(key)) loadService(key, url)
            }, schedule[index])
        }
    }

    fun destroyAll() {
        mainHandler.removeCallbacksAndMessages(null)
        recoveryScheduled = false
        pendingRecovery.clear()
        serviceUrls.clear()
        webViews.values.forEach { it.destroy() }
        webViews.clear()
    }

    fun destroyService(serviceKey: String) {
        pendingRecovery.remove(serviceKey)
        serviceUrls.remove(serviceKey)
        webViews.remove(serviceKey)?.destroy()
    }

    private fun isLoginPage(serviceKey: String, currentUrl: String): Boolean {
        val url = currentUrl.lowercase()
        return when {
            url.contains("accounts.google.com") -> true
            url.contains("/login") -> true
            url.contains("/signin") -> true
            url.contains("/sessions") -> true
            url.contains("/auth") -> true
            url.contains("/sso") -> true
            else -> false
        }
    }

    private fun getExpectedDomain(serviceKey: String): String = when (serviceKey) {
        "browser_claude_usage" -> "claude.ai"
        "browser_github_copilot" -> "github.com"
        "browser_openai" -> "platform.openai.com"
        "browser_claude_billing" -> "platform.claude.com"
        "browser_openrouter" -> "openrouter.ai"
        "browser_chatgpt_usage" -> "chatgpt.com"
        else -> ""
    }

    /**
     * Bridge class exposed to JavaScript as window.AndroidBridge.
     * JS calls AndroidBridge.postData(source, jsonString) to send data back.
     */
    private class DataBridge(private val serviceKey: String) {
        @JavascriptInterface
        fun postData(source: String, jsonString: String) {
            try {
                val json = JSONObject(jsonString)
                val data = mutableMapOf<String, Any?>()
                for (key in json.keys()) {
                    data[key] = json.opt(key)
                }
                data["received_at"] = java.time.Instant.now().toString()
                val targetKey = source.ifEmpty { serviceKey }
                DataStoreRepository.putData(targetKey, data)
            } catch (_: Exception) {
                // Silently ignore parse errors from JS
            }
        }
    }

    companion object {
        /** Window for collecting the renderer-gone reports of all affected WebViews. */
        private const val COALESCE_MS = 1_000L

        /** Desktop Chrome UA — matches login WebView; avoids mobile redirects. */
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/136.0.0.0 Safari/537.36"

        /**
         * Suppress the X-Requested-With header that Android WebView adds automatically.
         * Google uses this header to detect embedded WebViews and block OAuth.
         */
        fun suppressRequestedWithHeader(webView: WebView) {
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                    // Empty allow-list → header is never sent to any origin
                    WebSettingsCompat.setRequestedWithHeaderOriginAllowList(
                        webView.settings,
                        emptySet(),
                    )
                }
            } catch (_: Exception) { /* old WebView APK */ }
        }
    }
}
