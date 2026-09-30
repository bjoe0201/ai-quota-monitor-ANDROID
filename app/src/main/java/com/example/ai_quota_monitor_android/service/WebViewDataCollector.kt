package com.example.ai_quota_monitor_android.service

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.ai_quota_monitor_android.data.repository.DataStoreRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages background WebViews that load AI service pages and inject JS
 * to intercept API responses. Data is passed back via @JavascriptInterface.
 *
 * Pages are expensive: every WebView of an app shares one renderer process, and a single
 * desktop SPA costs 150–250 MB there. The collection cycle therefore keeps at most one page
 * alive at a time and calls [destroyService] as soon as it has the data — see [collectionSteps].
 */
class WebViewDataCollector(private val context: Context) {

    private val webViews = mutableMapOf<String, WebView>()
    private var onSessionExpired: ((String) -> Unit)? = null
    private var onRendererGone: ((String) -> Unit)? = null
    private var onChallengePage: ((String) -> Unit)? = null
    private var jsScript: String? = null

    private val expiryGuard = SessionExpiryGuard()
    private val recycleTracker = PlannedRecycleTracker<WebView>()
    private var recycleDone: CompletableDeferred<Unit>? = null

    fun setOnSessionExpired(listener: (String) -> Unit) {
        onSessionExpired = listener
    }

    /**
     * Called when the renderer process died and took [serviceKey]'s page with it. The WebView
     * is already destroyed; the caller decides when to try that service again.
     */
    fun setOnRendererGone(listener: (String) -> Unit) {
        onRendererGone = listener
    }

    /** Called when [serviceKey]'s page landed on Cloudflare's challenge; see [CloudflareChallenge]. */
    fun setOnChallengePage(listener: (String) -> Unit) {
        onChallengePage = listener
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
            settings.userAgentString = BrowserUserAgent.forDevice(context)
            suppressRequestedWithHeader(this)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    cookieManager.flush()
                    // If we're back on the target URL (not login page), login succeeded
                    if (!isLoginUrl(loadedUrl) && loadedUrl.contains(getExpectedDomain(serviceKey))) {
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
        // Release any previous page for this service first
        destroyService(serviceKey)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            @Suppress("DEPRECATION")
            settings.databaseEnabled = true
            settings.userAgentString = BrowserUserAgent.forDevice(context)
            // Nothing is ever painted — these pages are only scraped — so decoded bitmaps
            // would be pure renderer memory cost.
            settings.loadsImagesAutomatically = false
            settings.blockNetworkImage = true
            suppressRequestedWithHeader(this)
            addJavascriptInterface(DataBridge(serviceKey), BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, loadedUrl: String, favicon: android.graphics.Bitmap?) {
                    // Inject early so hooks are set before page's own JS fetches data
                    if (!isLoginUrl(loadedUrl)) {
                        val script = getJsScript()
                        if (script.isNotEmpty()) {
                            view.evaluateJavascript(script, null)
                        }
                    }
                }

                override fun onPageFinished(view: WebView, loadedUrl: String) {
                    if (isLoginUrl(loadedUrl)) {
                        // One sighting is not enough: a token refresh or bot check can land
                        // here transiently, and marking a service logged out is sticky.
                        if (expiryGuard.onLoginPageSeen(serviceKey)) {
                            onSessionExpired?.invoke(serviceKey)
                        }
                        return
                    }
                    expiryGuard.onContentPageSeen(serviceKey)
                    view.evaluateJavascript(CloudflareChallenge.DETECT_JS) { result ->
                        if (CloudflareChallenge.isChallenge(result)) onChallengePage?.invoke(serviceKey)
                    }
                    // Re-inject on finish as safety net (some SPAs replace fetch after first inject)
                    val script = getJsScript()
                    if (script.isNotEmpty()) {
                        view.evaluateJavascript(script, null)
                    }
                }

                /**
                 * Handle the death of the renderer process shared by every WebView.
                 *
                 * Returning true is what keeps the app alive: without it the WebView layer
                 * deliberately kills the whole app process ("Render process ... crash wasn't
                 * handled by all associated webviews"). A WebView whose renderer is gone can
                 * never be reused, so it is destroyed and the caller is told.
                 */
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    if (webViews[serviceKey] === view) {
                        webViews.remove(serviceKey)
                    }
                    view.destroy()
                    if (recycleTracker.onGone(view)) {
                        // We terminated it on purpose: no backoff, no step to cancel.
                        recycleDone?.complete(Unit)
                    } else {
                        onRendererGone?.invoke(serviceKey)
                    }
                    return true
                }
            }
            @SuppressLint("JavascriptInterface")
            this.also { cookieManager.setAcceptThirdPartyCookies(it, true) }
        }
        wv.loadUrl(url)
        webViews[serviceKey] = wv
    }

    /**
     * Clear WebView's resource cache through a still-live page. The cache is shared by every
     * WebView in the app, so any live one will do — but it must not have been destroyed yet.
     * Returns false when [serviceKey] has no live page (e.g. its renderer died).
     */
    fun clearResourceCache(serviceKey: String, includeDiskFiles: Boolean): Boolean {
        val wv = webViews[serviceKey] ?: return false
        wv.clearCache(includeDiskFiles)
        return true
    }

    /**
     * End the shared renderer process through [serviceKey]'s still-live page, so the next page
     * starts in a fresh one (PLANS/03 T6). Every page ever loaded leaves memory behind in the
     * renderer even after it is destroyed, and nothing short of ending the process returns it.
     *
     * Skipped while a login page is open: it shares the renderer and would die with it. On
     * [RecycleOutcome.TIMED_OUT] the page is still registered; the caller destroys it as usual.
     */
    suspend fun recycleRenderer(serviceKey: String, timeoutMs: Long): RecycleOutcome {
        if (openLoginPages.get() > 0) return RecycleOutcome.LOGIN_OPEN
        val wv = webViews[serviceKey] ?: return RecycleOutcome.NO_PAGE
        val renderer = wv.webViewRenderProcess ?: return RecycleOutcome.NO_HANDLE
        val done = CompletableDeferred<Unit>()
        recycleDone = done
        recycleTracker.mark(wv)
        if (!renderer.terminate()) {
            recycleTracker.onGone(wv) // drop the mark: no callback is coming
            recycleDone = null
            return RecycleOutcome.REFUSED
        }
        val finished = withTimeoutOrNull(timeoutMs) { done.await() } != null
        recycleDone = null
        return if (finished) RecycleOutcome.DONE else RecycleOutcome.TIMED_OUT
    }

    fun destroyAll() {
        webViews.keys.toList().forEach { destroyService(it) }
    }

    /**
     * Stop and release a service page. Blanking the page first lets the renderer drop the
     * document, its JS heap and its timers before the WebView itself goes away.
     */
    fun destroyService(serviceKey: String) {
        val wv = webViews.remove(serviceKey) ?: return
        wv.stopLoading()
        wv.removeJavascriptInterface(BRIDGE_NAME)
        // No about:blank navigation first: loadUrl() is asynchronous, so destroying in the
        // same breath means the navigation never commits. destroy() is what actually tears
        // the document down; it does not promise the renderer returns the memory to the OS.
        wv.destroy()
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
    enum class RecycleOutcome {
        /** The renderer was terminated and our page reported it gone. */
        DONE,
        /** terminate() succeeded but no onRenderProcessGone arrived in time. */
        TIMED_OUT,
        /** terminate() returned false, e.g. the renderer had already ended. */
        REFUSED,
        /** WebView is not running in multiprocess mode, so there is no renderer handle. */
        NO_HANDLE,
        /** No live page to reach the renderer through. */
        NO_PAGE,
        /** A login page is open on the same renderer. */
        LOGIN_OPEN,
    }

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
        /** Name the injected script calls back through. */
        private const val BRIDGE_NAME = "AndroidBridge"

        /**
         * Login pages currently on screen. They run in the same renderer process as the
         * background pages, so [recycleRenderer] must leave it alone while any is open.
         */
        private val openLoginPages = AtomicInteger(0)

        fun onLoginPageOpened() {
            openLoginPages.incrementAndGet()
        }

        fun onLoginPageClosed() {
            openLoginPages.updateAndGet { (it - 1).coerceAtLeast(0) }
        }

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
