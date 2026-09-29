package com.example.ai_quota_monitor_android.service

/**
 * Tells a renderer death we caused apart from one we did not.
 *
 * `WebViewRenderProcess.terminate()` reaches every WebView through the same
 * `onRenderProcessGone` as an OOM kill does, and the detail it carries does not say which it
 * was. So the page we terminate through is marked first; only its death counts as planned.
 * Anything else — a real crash, or a login page sharing the renderer — stays unplanned.
 *
 * Holds one mark at a time: there is never more than one recycle in flight.
 */
class PlannedRecycleTracker<T : Any> {

    private var planned: T? = null

    fun mark(page: T) {
        planned = page
    }

    /** True when [page] is the one marked for recycling; the mark is used up either way. */
    fun onGone(page: T): Boolean {
        if (planned !== page) return false
        planned = null
        return true
    }
}
