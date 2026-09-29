package com.example.ai_quota_monitor_android.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Terminating the renderer on purpose makes every WebView on it report the same
 * onRenderProcessGone as a real crash. Only the page we terminated through may be treated as
 * planned: an unexpected death has to keep triggering the recovery backoff, and a login page on
 * the same renderer is never ours to recycle.
 */
class PlannedRecycleTrackerTest {

    private val page = Any()
    private val otherPage = Any()

    @Test
    fun `a death with nothing marked is unplanned`() {
        val tracker = PlannedRecycleTracker<Any>()

        assertFalse(tracker.onGone(page))
    }

    @Test
    fun `the death of the marked page is planned`() {
        val tracker = PlannedRecycleTracker<Any>()
        tracker.mark(page)

        assertTrue(tracker.onGone(page))
    }

    @Test
    fun `another page dying while one is marked is unplanned`() {
        val tracker = PlannedRecycleTracker<Any>()
        tracker.mark(page)

        assertFalse(tracker.onGone(otherPage))
    }

    @Test
    fun `a mark is used up by the death it was set for`() {
        val tracker = PlannedRecycleTracker<Any>()
        tracker.mark(page)
        tracker.onGone(page)

        assertFalse(tracker.onGone(page))
    }

    @Test
    fun `a new mark replaces the previous one`() {
        val tracker = PlannedRecycleTracker<Any>()
        tracker.mark(page)
        tracker.mark(otherPage)

        assertFalse(tracker.onGone(page))
        assertTrue(tracker.onGone(otherPage))
    }
}
