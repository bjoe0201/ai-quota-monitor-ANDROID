package com.example.ai_quota_monitor_android.service

/**
 * Decides when background WebViews may be reloaded after the shared renderer process died.
 *
 * All background WebViews share one renderer process, so a single renderer death takes every
 * page down together. Reloading them all at once recreates the memory pressure that killed the
 * renderer, so the returned schedule staggers the reloads, and repeated deaths back off.
 */
class RendererRecoveryPolicy(private val now: () -> Long) {

    private var consecutiveDeaths = 0
    private var lastDeathAt = 0L

    /** Delay per service, in the order the services should be reloaded. */
    fun scheduleFor(serviceCount: Int): List<Long> {
        val at = now()
        consecutiveDeaths =
            if (consecutiveDeaths > 0 && at - lastDeathAt <= RESET_WINDOW_MS) consecutiveDeaths + 1 else 1
        lastDeathAt = at

        val base = BACKOFF_MS[(consecutiveDeaths - 1).coerceAtMost(BACKOFF_MS.lastIndex)]
        return (0 until serviceCount).map { base + it * STAGGER_MS }
    }

    companion object {
        /** Base delay before the first reload; the last value is the cap. */
        private val BACKOFF_MS = listOf(5_000L, 30_000L, 120_000L, 300_000L)

        /** Gap between two consecutive reloads. */
        const val STAGGER_MS = 20_000L

        /** A renderer that survived this long counts as healthy, so backoff starts over. */
        private const val RESET_WINDOW_MS = 15 * 60_000L
    }
}
