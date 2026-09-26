package com.example.ai_quota_monitor_android.service

/**
 * Login paths seen across the monitored services. `sign-in` is spelled three different ways in
 * the wild — OpenRouter uses the hyphenated form, and missing it made that card look alive while
 * it silently never updated.
 */
private val LOGIN_PATH_MARKERS = listOf(
    "/login",
    "/signin",
    "/sign-in",
    "/sign_in",
    "/sessions",
    "/auth",
    "/sso",
)

/** Whether [url] is an authentication page rather than a service's data page. */
fun isLoginUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains("accounts.google.com")) return true
    return LOGIN_PATH_MARKERS.any { lower.contains(it) }
}
