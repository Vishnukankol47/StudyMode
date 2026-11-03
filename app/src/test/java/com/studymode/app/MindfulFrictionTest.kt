package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MindfulFrictionTest {

    @Test
    fun breathingCycleDurationsArePositiveAndConsistent() {
        val inhaleMs = 4_000L
        val holdInMs = 2_000L
        val exhaleMs = 4_000L
        val restMs = 1_500L

        val singleCycleMs = inhaleMs + holdInMs + exhaleMs + restMs
        assertEquals(11_500L, singleCycleMs)

        // 2 cycles total ~23 seconds of mindful calming
        val twoCyclesMs = singleCycleMs * 2
        assertEquals(23_000L, twoCyclesMs)
    }

    @Test
    fun countdownProgressFractionCalculatesCorrectly() {
        val totalSecs = 10
        val halfWayElapsedSecs = 5

        val remaining = (totalSecs - halfWayElapsedSecs).coerceAtLeast(0)
        assertEquals(5, remaining)

        val fullElapsedSecs = 10
        val remainingAtEnd = (totalSecs - fullElapsedSecs).coerceAtLeast(0)
        assertEquals(0, remainingAtEnd)

        val overElapsedSecs = 12
        val remainingOver = (totalSecs - overElapsedSecs).coerceAtLeast(0)
        assertEquals(0, remainingOver)
    }

    @Test
    fun pauseDurationSettingsAreClampedSafely() {
        val clampPause = { seconds: Int -> seconds.coerceIn(5, 60) }
        assertEquals(5, clampPause(2))
        assertEquals(10, clampPause(10))
        assertEquals(15, clampPause(15))
        assertEquals(60, clampPause(120))
    }

    @Test
    fun breathCyclesTargetSettingsAreClampedSafely() {
        val clampBreaths = { breaths: Int -> breaths.coerceIn(1, 5) }
        assertEquals(1, clampBreaths(0))
        assertEquals(2, clampBreaths(2))
        assertEquals(3, clampBreaths(3))
        assertEquals(5, clampBreaths(10))
    }

    @Test
    fun unlockPassCalculationGrantsCorrectDuration() {
        val now = 1_700_000_000_000L
        val fiveMinutes = 5 * 60_000L
        val expiry = now + fiveMinutes
        assertEquals(300_000L, fiveMinutes)
        assertTrue(expiry > now)
        assertEquals(now + 300_000L, expiry)
    }
}
