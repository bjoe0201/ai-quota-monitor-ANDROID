package com.example.ai_quota_monitor_android

import android.app.Application
import android.webkit.CookieManager
import android.webkit.WebView

/**
 * Application class for early WebView and CookieManager initialization.
 * CookieManager must be configured before any WebView is created to ensure
 * cookies persist across app restarts.
 */
class AiQuotaMonitorApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize CookieManager early — must happen before any WebView creation
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
        // Enable WebView debugging for troubleshooting
        WebView.setWebContentsDebuggingEnabled(true)
    }
}
