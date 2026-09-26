package com.example.ai_quota_monitor_android.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The renderer process is shared by every background WebView, so one renderer death
 * takes all of them down at once. Reloading them all immediately recreates the memory
 * condition that killed the renderer, so the schedule must both stagger the reloads and
 * back off when deaths repeat.
 */
class RendererRecoveryPolicyTest {

    @Test
    fun `first renderer death reloads one service after the base delay`() {
        val policy = RendererRecoveryPolicy { 0L }

        assertEquals(listOf(5_000L), policy.scheduleFor(1))
    }

    @Test
    fun `services are staggered so they never reload at the same moment`() {
        val policy = RendererRecoveryPolicy { 0L }

        assertEquals(listOf(5_000L, 25_000L, 45_000L), policy.scheduleFor(3))
    }

    @Test
    fun `repeated renderer deaths back off`() {
        var now = 0L
        val policy = RendererRecoveryPolicy { now }

        val first = policy.scheduleFor(1).single()
        now = 10_000L
        val second = policy.scheduleFor(1).single()
        now = 20_000L
        val third = policy.scheduleFor(1).single()

        assertEquals(listOf(5_000L, 30_000L, 120_000L), listOf(first, second, third))
    }

    @Test
    fun `backoff is capped so recovery never stops being attempted`() {
        var now = 0L
        val policy = RendererRecoveryPolicy { now }

        val bases = (1..5).map {
            now += 10_000L
            policy.scheduleFor(1).single()
        }

        assertEquals(listOf(5_000L, 30_000L, 120_000L, 300_000L, 300_000L), bases)
    }

    @Test
    fun `backoff resets after a long healthy period`() {
        var now = 0L
        val policy = RendererRecoveryPolicy { now }
        policy.scheduleFor(1)
        policy.scheduleFor(1)

        now = 16 * 60_000L
        assertEquals(listOf(5_000L), policy.scheduleFor(1))
    }
}
