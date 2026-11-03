package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusPatternsTest {

    @Test
    fun testFormatFocusDuration() {
        assertEquals("0 min", formatFocusDuration(0))
        assertEquals("0 min", formatFocusDuration(-5))
        assertEquals("25 min", formatFocusDuration(25))
        assertEquals("59 min", formatFocusDuration(59))
        assertEquals("1h", formatFocusDuration(60))
        assertEquals("1h 45m", formatFocusDuration(105))
        assertEquals("2h 30m", formatFocusDuration(150))
        assertEquals("11h 20m", formatFocusDuration(680))
    }

    @Test
    fun testSessionLengthBracketMatches() {
        // UNDER_15: < 15 min (0 to 14m 59s)
        assertTrue(SessionLengthBracket.UNDER_15.matches(0L))
        assertTrue(SessionLengthBracket.UNDER_15.matches(14 * 60L + 59L))
        assertFalse(SessionLengthBracket.UNDER_15.matches(15 * 60L))

        // BETWEEN_15_30: 15 to 30 min (15m to 29m 59s)
        assertTrue(SessionLengthBracket.BETWEEN_15_30.matches(15 * 60L))
        assertTrue(SessionLengthBracket.BETWEEN_15_30.matches(25 * 60L))
        assertTrue(SessionLengthBracket.BETWEEN_15_30.matches(30 * 60L - 1L))
        assertFalse(SessionLengthBracket.BETWEEN_15_30.matches(30 * 60L))

        // BETWEEN_30_60: 30 to 60 min (30m to 60m)
        assertTrue(SessionLengthBracket.BETWEEN_30_60.matches(30 * 60L))
        assertTrue(SessionLengthBracket.BETWEEN_30_60.matches(45 * 60L))
        assertTrue(SessionLengthBracket.BETWEEN_30_60.matches(60 * 60L))
        assertFalse(SessionLengthBracket.BETWEEN_30_60.matches(60 * 60L + 1L))

        // OVER_60: > 60 min (60m 01s and above)
        assertFalse(SessionLengthBracket.OVER_60.matches(60 * 60L))
        assertTrue(SessionLengthBracket.OVER_60.matches(60 * 60L + 1L))
        assertTrue(SessionLengthBracket.OVER_60.matches(80 * 60L))
    }

    @Test
    fun testHourlyPatternsCalculationAndPeakInsight() {
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 8, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val baseMillis = cal.timeInMillis

        // Create sessions at:
        // 6:30 AM (25m)
        // 8:15 AM (40m)
        // 9:30 AM (50m)
        // 10:15 AM (35m)
        // 14:00 PM (20m)
        val sessions = listOf(
            FocusSessionRecord(
                id = 1L,
                startedAtMillis = baseMillis + 6 * 3600_000L + 30 * 60_000L,
                elapsedSeconds = 25 * 60L,
                targetMinutes = 25,
                goal = "Early Study",
                completed = true
            ),
            FocusSessionRecord(
                id = 2L,
                startedAtMillis = baseMillis + 8 * 3600_000L + 15 * 60_000L,
                elapsedSeconds = 40 * 60L,
                targetMinutes = 40,
                goal = "Math",
                completed = true
            ),
            FocusSessionRecord(
                id = 3L,
                startedAtMillis = baseMillis + 9 * 3600_000L + 30 * 60_000L,
                elapsedSeconds = 50 * 60L,
                targetMinutes = 50,
                goal = "Physics",
                completed = true
            ),
            FocusSessionRecord(
                id = 4L,
                startedAtMillis = baseMillis + 10 * 3600_000L + 15 * 60_000L,
                elapsedSeconds = 35 * 60L,
                targetMinutes = 35,
                goal = "Chemistry",
                completed = true
            ),
            FocusSessionRecord(
                id = 5L,
                startedAtMillis = baseMillis + 14 * 3600_000L,
                elapsedSeconds = 20 * 60L,
                targetMinutes = 20,
                goal = "Afternoon review",
                completed = true
            )
        )

        val (buckets, insight) = calculateHourlyPatterns(sessions, BestTimesPeriod.DAY, selectedHour = 9)

        // Check hour 9 bucket
        val bucket9 = buckets.find { it.hour == 9 }
        assertTrue(bucket9 != null)
        assertEquals(50, bucket9!!.durationMinutes)
        assertEquals(1, bucket9.sessionCount)
        assertEquals("50 min", bucket9.formattedDuration)
        assertTrue(bucket9.isSelected)

        // Check hour 6 bucket
        val bucket6 = buckets.find { it.hour == 6 }
        assertTrue(bucket6 != null)
        assertEquals(25, bucket6!!.durationMinutes)

        // Peak insight should be Morning
        assertEquals("You focus best in the morning", insight.periodTitle)
        assertEquals("SUN", insight.peakPeriodType)
        assertTrue(insight.periodDescription.contains("8 AM") || insight.periodDescription.contains("9 AM"))
    }

    @Test
    fun testWeekdayPatternsCalculation() {
        val cal = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 8, 10, 0, 0) // Mon Jan 8, 2024
            set(Calendar.MILLISECOND, 0)
        }
        val monMillis = cal.timeInMillis

        // Monday: 80 min (1h 20m), 2 sessions
        // Saturday: 150 min (2h 30m), 3 sessions
        val sessions = listOf(
            FocusSessionRecord(
                id = 1L,
                startedAtMillis = monMillis,
                elapsedSeconds = 50 * 60L,
                targetMinutes = 50,
                goal = "Mon 1",
                completed = true
            ),
            FocusSessionRecord(
                id = 2L,
                startedAtMillis = monMillis + 2 * 3600_000L,
                elapsedSeconds = 30 * 60L,
                targetMinutes = 30,
                goal = "Mon 2",
                completed = true
            ),
            FocusSessionRecord(
                id = 3L,
                startedAtMillis = monMillis + 5 * 86_400_000L, // Saturday Jan 13
                elapsedSeconds = 60 * 60L,
                targetMinutes = 60,
                goal = "Sat 1",
                completed = true
            ),
            FocusSessionRecord(
                id = 4L,
                startedAtMillis = monMillis + 5 * 86_400_000L + 2 * 3600_000L,
                elapsedSeconds = 50 * 60L,
                targetMinutes = 50,
                goal = "Sat 2",
                completed = true
            ),
            FocusSessionRecord(
                id = 5L,
                startedAtMillis = monMillis + 5 * 86_400_000L + 4 * 3600_000L,
                elapsedSeconds = 40 * 60L,
                targetMinutes = 40,
                goal = "Sat 3",
                completed = true
            )
        )

        val (weekdayList, summary) = calculateWeekdayPatterns(sessions, selectedDayOfWeek = Calendar.SATURDAY)

        assertEquals(230, summary.totalMinutes)
        assertEquals("3h 50m", summary.formattedTotalTime)
        assertEquals(5, summary.totalSessions)
        // 2 active days (Monday & Saturday): daily avg = 230 / 2 = 115 min
        assertEquals(115, summary.dailyAverageMinutes)
        assertEquals("Saturday", summary.peakDayName)

        val monData = weekdayList.find { it.dayOfWeek == Calendar.MONDAY }
        assertTrue(monData != null)
        assertEquals(80, monData!!.durationMinutes)
        assertEquals(2, monData.sessionCount)
        assertEquals("1h 20m", monData.formattedDuration)

        val satData = weekdayList.find { it.dayOfWeek == Calendar.SATURDAY }
        assertTrue(satData != null)
        assertEquals(150, satData!!.durationMinutes)
        assertEquals(3, satData.sessionCount)
        assertEquals("2h 30m", satData.formattedDuration)
        assertTrue(satData.isSelected)
        // Saturday vs avg: (150 - 115) / 115 * 100 = +30%
        assertTrue(satData.vsAvgPercent > 0)
    }

    @Test
    fun testLengthPatternsCalculation() {
        // Sessions:
        // 1 session under 15m (10m)
        // 3 sessions 15-30m (20m, 25m, 25m)
        // 5 sessions 30-60m (35m, 40m, 45m, 50m, 55m)
        // 1 session over 60m (80m)
        // Total = 10 sessions
        val sessions = listOf(
            FocusSessionRecord(id = 1L, startedAtMillis = 1000L, elapsedSeconds = 10 * 60L, targetMinutes = 10, goal = "S1", completed = true),
            FocusSessionRecord(id = 2L, startedAtMillis = 2000L, elapsedSeconds = 20 * 60L, targetMinutes = 20, goal = "S2", completed = true),
            FocusSessionRecord(id = 3L, startedAtMillis = 3000L, elapsedSeconds = 25 * 60L, targetMinutes = 25, goal = "S3", completed = true),
            FocusSessionRecord(id = 4L, startedAtMillis = 4000L, elapsedSeconds = 25 * 60L, targetMinutes = 25, goal = "S4", completed = true),
            FocusSessionRecord(id = 5L, startedAtMillis = 5000L, elapsedSeconds = 35 * 60L, targetMinutes = 35, goal = "S5", completed = true),
            FocusSessionRecord(id = 6L, startedAtMillis = 6000L, elapsedSeconds = 40 * 60L, targetMinutes = 40, goal = "S6", completed = true),
            FocusSessionRecord(id = 7L, startedAtMillis = 7000L, elapsedSeconds = 45 * 60L, targetMinutes = 45, goal = "S7", completed = true),
            FocusSessionRecord(id = 8L, startedAtMillis = 8000L, elapsedSeconds = 50 * 60L, targetMinutes = 50, goal = "S8", completed = true),
            FocusSessionRecord(id = 9L, startedAtMillis = 9000L, elapsedSeconds = 55 * 60L, targetMinutes = 55, goal = "S9", completed = true),
            FocusSessionRecord(id = 10L, startedAtMillis = 10000L, elapsedSeconds = 80 * 60L, targetMinutes = 80, goal = "S10", completed = true)
        )

        // Previous sessions with avg duration 30 min
        val prevSessions = listOf(
            FocusSessionRecord(id = 11L, startedAtMillis = 100L, elapsedSeconds = 30 * 60L, targetMinutes = 30, goal = "P1", completed = true)
        )

        val (bracketList, summary) = calculateLengthPatterns(sessions, prevSessions)

        assertEquals(10, summary.totalSessions)
        assertEquals(SessionLengthBracket.BETWEEN_30_60, summary.mostCommonBracket)

        val u15 = bracketList.find { it.bracket == SessionLengthBracket.UNDER_15 }
        assertTrue(u15 != null)
        assertEquals(1, u15!!.count)
        assertEquals(10, u15.percentage) // 1/10 = 10%
        assertEquals("10 min", u15.formattedAvgDuration)

        val b15_30 = bracketList.find { it.bracket == SessionLengthBracket.BETWEEN_15_30 }
        assertTrue(b15_30 != null)
        assertEquals(3, b15_30!!.count)
        assertEquals(30, b15_30.percentage) // 3/10 = 30%

        val b30_60 = bracketList.find { it.bracket == SessionLengthBracket.BETWEEN_30_60 }
        assertTrue(b30_60 != null)
        assertEquals(5, b30_60!!.count)
        assertEquals(50, b30_60.percentage) // 5/10 = 50%

        val o60 = bracketList.find { it.bracket == SessionLengthBracket.OVER_60 }
        assertTrue(o60 != null)
        assertEquals(1, o60!!.count)
        assertEquals(10, o60.percentage) // 1/10 = 10%
        assertEquals("1h 20m", o60.formattedAvgDuration)
    }
}
