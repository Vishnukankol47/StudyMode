package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1FeaturesTest {

    @Test
    fun timeSavedCalculationAndFormatting() {
        val interceptions = 12
        val minsFromInterceptions = interceptions * 8 // 96 mins
        val minsFromLimits = 50
        val minsFromFriction = 24
        val totalMins = minsFromInterceptions + minsFromLimits + minsFromFriction // 170 mins = 2h 50m

        val summary = TimeSavedSummary(
            todayMinutesSaved = totalMins,
            weekMinutesSaved = totalMins * 5,
            totalInterceptionsToday = interceptions,
            totalInterceptionsWeek = interceptions * 5,
            minutesFromInterceptions = minsFromInterceptions,
            minutesFromAppLimits = minsFromLimits,
            minutesFromFriction = minsFromFriction,
            equivalentBookPages = (totalMins / 2.5).toInt(),
            equivalentFocusSessions = totalMins / 45,
            realWorldDescription = "Sample"
        )

        assertEquals("2h 50m", summary.formattedToday)
        assertEquals(170, summary.todayMinutesSaved)
        assertEquals(96, summary.minutesFromInterceptions)
        assertEquals(68, summary.equivalentBookPages)
        assertEquals(3, summary.equivalentFocusSessions)
    }

    @Test
    fun strictDelaySecondsClamping() {
        val clampDelay = { secs: Int -> secs.coerceIn(10, 120) }

        assertEquals(10, clampDelay(5))
        assertEquals(30, clampDelay(30))
        assertEquals(60, clampDelay(60))
        assertEquals(120, clampDelay(300))
    }

    @Test
    fun strictDelayCooldownProgressAndLockState() {
        val totalSecs = 30
        val checkState = { remaining: Int ->
            val fraction = (remaining.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
            val isUnlocked = remaining <= 0
            Pair(fraction, isUnlocked)
        }

        // Mid-cooldown (15s remaining)
        val (progress1, unlocked1) = checkState(15)
        assertEquals(0.5f, progress1, 0.01f)
        assertFalse(unlocked1)

        // Expired cooldown (0s remaining)
        val (progress2, unlocked2) = checkState(0)
        assertEquals(0.0f, progress2, 0.01f)
        assertTrue(unlocked2)
    }
}
