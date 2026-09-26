package com.example.ai_quota_monitor_android.service

/**
 * Requires repeated evidence before a service is declared logged out.
 *
 * Declaring a service logged out is sticky — it stops all background collection until the user
 * logs in by hand — so one login-page sighting is not enough: a slow SPA, a token refresh or a
 * bot check can transiently land on an auth URL.
 */
class SessionExpiryGuard {

    private val sightings = mutableMapOf<String, Int>()

    /** @return true when the service should now be treated as logged out. */
    fun onLoginPageSeen(serviceKey: String): Boolean {
        val seen = (sightings[serviceKey] ?: 0) + 1
        sightings[serviceKey] = seen
        return seen >= REQUIRED_SIGHTINGS
    }

    fun onContentPageSeen(serviceKey: String) {
        sightings.remove(serviceKey)
    }

    companion object {
        private const val REQUIRED_SIGHTINGS = 2
    }
}
