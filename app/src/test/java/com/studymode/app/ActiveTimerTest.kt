package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveTimerTest {
    @Test
    fun runningTimerTracksRemainingAndElapsedTime() {
        val timer = activeTimer()

        assertEquals(15_000L, timer.remainingAt(11_000L))
        assertEquals(10_000L, timer.elapsedAt(11_000L))
        assertEquals(0L, timer.remainingAt(26_000L))
    }

    @Test
    fun pausedTimerKeepsItsSavedRemainingTime() {
        val paused = activeTimer().copy(
            segmentTargetMillis = 15_000L,
            savedRemainingMillis = 15_000L,
            elapsedBeforeSegmentMillis = 10_000L,
            running = false,
        )

        assertEquals(15_000L, paused.remainingAt(90_000L))
        assertEquals(10_000L, paused.elapsedAt(90_000L))
    }

    @Test
    fun resumedTimerContinuesFromSavedElapsedTime() {
        val resumed = activeTimer().copy(
            segmentStartedAtMillis = 20_000L,
            segmentTargetMillis = 15_000L,
            savedRemainingMillis = 15_000L,
            elapsedBeforeSegmentMillis = 10_000L,
        )

        assertEquals(9_000L, resumed.remainingAt(26_000L))
        assertEquals(16_000L, resumed.elapsedAt(26_000L))
    }

    @Test
    fun focusOnlyBlockingRequiresAnActiveUnexpiredFocusTimer() {
        val timer = activeTimer()

        assertFalse(shouldBlockManuallyBlockedApp(true, true, null, 11_000L))
        assertTrue(shouldBlockManuallyBlockedApp(true, true, timer, 11_000L))
        assertFalse(shouldBlockManuallyBlockedApp(true, true, timer, 26_000L))
        assertTrue(shouldBlockManuallyBlockedApp(true, false, null, 11_000L))
        assertFalse(shouldBlockManuallyBlockedApp(false, false, timer, 11_000L))
    }

    @Test
    fun pausedFocusTimerDoesNotActivateFocusOnlyBlocking() {
        val paused = activeTimer().copy(running = false)

        assertFalse(shouldBlockManuallyBlockedApp(true, true, paused, 11_000L))
    }

    private fun activeTimer() = ActiveTimer(
        startedAtMillis = 1_000L,
        segmentStartedAtMillis = 1_000L,
        segmentTargetMillis = 25_000L,
        savedRemainingMillis = 25_000L,
        elapsedBeforeSegmentMillis = 0L,
        targetMinutes = 1,
        goal = "Read",
        running = true,
    )
}
