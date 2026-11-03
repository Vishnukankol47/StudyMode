package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusInsightsTest {

    @Test
    fun testWeeklyImprovementCalculationPositiveGrowth() {
        val currentSessions = listOf(
            FocusSessionRecord(id = 1L, startedAtMillis = 1000L, elapsedSeconds = 120 * 60L, targetMinutes = 120, goal = "S1", completed = true),
            FocusSessionRecord(id = 2L, startedAtMillis = 2000L, elapsedSeconds = 152 * 60L, targetMinutes = 152, goal = "S2", completed = true)
        ) // Total = 272 min (4h 32m)

        val prevSessions = listOf(
            FocusSessionRecord(id = 3L, startedAtMillis = 100L, elapsedSeconds = 137 * 60L, targetMinutes = 137, goal = "P1", completed = true)
        ) // Total = 137 min (2h 17m)

        val data = calculateWeeklyImprovement(currentSessions, prevSessions)

        assertEquals(272, data.currentPeriodMinutes)
        assertEquals("4h 32m", data.formattedCurrentTime)
        assertEquals(137, data.previousPeriodMinutes)
        assertEquals("2h 17m", data.formattedPreviousTime)
        assertEquals(135, data.deltaMinutes)
        assertEquals("2h 15m", data.formattedDeltaTime)
        assertTrue(data.isIncrease)
        assertEquals(2, data.currentCompletedCount)
        assertEquals(1, data.previousCompletedCount)
        assertTrue(data.percentChange in 95..100)
        assertEquals("+${data.percentChange}%", data.badgeText)
        assertTrue(data.summaryTitle.contains("2h 15m more"))
    }

    @Test
    fun testWeeklyImprovementCalculationZeroPreviousHandledSafely() {
        val currentSessions = listOf(
            FocusSessionRecord(id = 1L, startedAtMillis = 1000L, elapsedSeconds = 60 * 60L, targetMinutes = 60, goal = "S1", completed = true)
        )
        val prevSessions = emptyList<FocusSessionRecord>()

        val data = calculateWeeklyImprovement(currentSessions, prevSessions)

        assertEquals(60, data.currentPeriodMinutes)
        assertEquals(0, data.previousPeriodMinutes)
        assertEquals(60, data.deltaMinutes)
        assertTrue(data.isIncrease)
        assertEquals(100, data.percentChange)
        assertEquals("+100%", data.badgeText)

        // Empty current and empty prev
        val emptyData = calculateWeeklyImprovement(emptyList(), emptyList())
        assertEquals(0, emptyData.currentPeriodMinutes)
        assertEquals(0, emptyData.previousPeriodMinutes)
        assertEquals(0, emptyData.deltaMinutes)
        assertEquals(0, emptyData.percentChange)
    }

    @Test
    fun testWeeklyImprovementCalculationDecline() {
        val currentSessions = listOf(
            FocusSessionRecord(id = 1L, startedAtMillis = 1000L, elapsedSeconds = 60 * 60L, targetMinutes = 60, goal = "S1", completed = true)
        ) // 60 min
        val prevSessions = listOf(
            FocusSessionRecord(id = 2L, startedAtMillis = 100L, elapsedSeconds = 100 * 60L, targetMinutes = 100, goal = "P1", completed = true)
        ) // 100 min

        val data = calculateWeeklyImprovement(currentSessions, prevSessions)

        assertEquals(60, data.currentPeriodMinutes)
        assertEquals(100, data.previousPeriodMinutes)
        assertEquals(-40, data.deltaMinutes)
        assertEquals("40 min", data.formattedDeltaTime)
        assertFalse(data.isIncrease)
        assertEquals(-40, data.percentChange)
        assertEquals("-40%", data.badgeText)
        assertTrue(data.summaryTitle.contains("40 min less"))
    }

    @Test
    fun testBestFocusTimePeakMorningWindow() {
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 8, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseMillis = cal.timeInMillis

        // Sessions at:
        // 6:30 AM (30 min) -> 6 AM - 8 AM
        // 8:30 AM (60 min) -> 8 AM - 11 AM
        // 9:30 AM (60 min) -> 8 AM - 11 AM
        // 10:15 AM (50 min) -> 8 AM - 11 AM
        // 12:00 PM (30 min) -> 11 AM - 2 PM
        // 15:00 PM (40 min) -> 2 PM - 5 PM
        val sessions = listOf(
            FocusSessionRecord(id = 1L, startedAtMillis = baseMillis + 6 * 3600_000L + 30 * 60_000L, elapsedSeconds = 30 * 60L, targetMinutes = 30, goal = "Early", completed = true),
            FocusSessionRecord(id = 2L, startedAtMillis = baseMillis + 8 * 3600_000L + 30 * 60_000L, elapsedSeconds = 60 * 60L, targetMinutes = 60, goal = "Math", completed = true),
            FocusSessionRecord(id = 3L, startedAtMillis = baseMillis + 9 * 3600_000L + 30 * 60_000L, elapsedSeconds = 60 * 60L, targetMinutes = 60, goal = "Physics", completed = true),
            FocusSessionRecord(id = 4L, startedAtMillis = baseMillis + 10 * 3600_000L + 15 * 60_000L, elapsedSeconds = 50 * 60L, targetMinutes = 50, goal = "Code", completed = true),
            FocusSessionRecord(id = 5L, startedAtMillis = baseMillis + 12 * 3600_000L, elapsedSeconds = 30 * 60L, targetMinutes = 30, goal = "Lunch review", completed = true),
            FocusSessionRecord(id = 6L, startedAtMillis = baseMillis + 15 * 3600_000L, elapsedSeconds = 40 * 60L, targetMinutes = 40, goal = "Afternoon", completed = true)
        )

        val insight = calculateBestFocusTimeInsight(sessions)

        assertEquals("morning", insight.peakPeriodName)
        assertEquals("8 AM – 11 AM", insight.peakTimeRangeLabel)
        assertEquals(8, insight.peakStartHour)
        assertEquals(10, insight.peakEndHour)
        assertEquals(170, insight.peakDurationMinutes) // 60 + 60 + 50 = 170 min
        assertEquals("2h 50m", insight.formattedPeakDuration)
        assertTrue(insight.summaryTitle.contains("morning"))
        assertTrue(insight.tipText.contains("8 AM – 11 AM"))

        // Verify standard breakdown rows
        val slot8_11 = insight.timeRanges.find { it.label == "8 AM – 11 AM" }
        assertTrue(slot8_11 != null)
        assertEquals(170, slot8_11!!.durationMinutes)
        assertEquals(3, slot8_11.sessionCount)
        assertTrue(slot8_11.isPeak)

        val slot6_8 = insight.timeRanges.find { it.label == "6 AM – 8 AM" }
        assertTrue(slot6_8 != null)
        assertEquals(30, slot6_8!!.durationMinutes)
        assertEquals(1, slot6_8.sessionCount)
    }

    @Test
    fun testBestFocusTimeEmptyDataHandledGracefully() {
        val insight = calculateBestFocusTimeInsight(emptyList())

        assertEquals("No focus data yet", insight.summaryTitle)
        assertTrue(insight.summarySubtitle.contains("Start a session"))
        assertTrue(insight.timeRanges.isNotEmpty())
    }
}
