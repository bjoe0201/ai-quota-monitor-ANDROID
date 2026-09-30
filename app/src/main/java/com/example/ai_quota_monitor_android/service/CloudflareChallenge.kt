package com.example.ai_quota_monitor_android.service

/**
 * Cloudflare's "verify you are human" interstitial. Background pages never get past it — only
 * the visible login screen can — so a service stuck behind it is flagged for the user instead
 * of waiting out the full page timeout every cycle.
 */
object CloudflareChallenge {

    /**
     * Evaluated when a page finishes loading; true only on the interstitial itself. Normal pages
     * may load `/cdn-cgi/challenge-platform/scripts/jsd/…` for bot scoring, so that path alone
     * is not proof — the challenge page's `chl_page` script and `_cf_chl_opt` are.
     */
    const val DETECT_JS = "(function(){return typeof window._cf_chl_opt!=='undefined'||" +
        "!!document.querySelector('script[src*=\"/challenge-platform/\"][src*=\"chl_page\"]')})()"

    /** How long a page may sit on the challenge, in case it clears on its own, before the step ends. */
    const val GRACE_MS = 15_000L

    /** [DETECT_JS] comes back JSON-encoded through evaluateJavascript. */
    fun isChallenge(jsResult: String?): Boolean = jsResult == "true"

    /** The flagged services after one collection step of [serviceKey]. */
    fun afterStep(flagged: Set<String>, serviceKey: String, challengeSeen: Boolean, gotData: Boolean): Set<String> =
        when {
            gotData -> flagged - serviceKey
            challengeSeen -> flagged + serviceKey
            else -> flagged
        }
}
