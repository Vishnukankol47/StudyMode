package com.studymode.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScheduleTest {

    private fun calculateDurationMinutes(bedtimeMinute: Int, wakeupMinute: Int): Int {
        return if (wakeupMinute >= bedtimeMinute) {
            wakeupMinute - bedtimeMinute
        } else {
            (1440 - bedtimeMinute) + wakeupMinute
        }
    }

    @Test
    fun testMidnightCrossingDuration() {
        // Weekday default: 11:00 PM (1380) -> 6:50 AM (410) = 470 minutes (7h 50m)
        val weekdayDuration = calculateDurationMinutes(1380, 410)
        assertEquals(470, weekdayDuration)
        assertEquals(7, weekdayDuration / 60)
        assertEquals(50, weekdayDuration % 60)

        // Weekend default: 11:30 PM (1410) -> 8:00 AM (480) = 510 minutes (8h 30m)
        val weekendDuration = calculateDurationMinutes(1410, 480)
        assertEquals(510, weekendDuration)
        assertEquals(8, weekendDuration / 60)
        assertEquals(30, weekendDuration % 60)

        // Cross midnight early: 10:00 PM (1320) -> 6:00 AM (360) = 480 minutes (8h 00m)
        val standard8h = calculateDurationMinutes(1320, 360)
        assertEquals(480, standard8h)

        // Same day schedule: 1:00 AM (60) -> 9:00 AM (540) = 480 minutes (8h 00m)
        val sameDay = calculateDurationMinutes(60, 540)
        assertEquals(480, sameDay)

        // Identical time explicitly handled
        val identical = calculateDurationMinutes(1380, 1380)
        assertEquals(0, identical)
    }

    @Test
    fun testTargetDurationComparison() {
        val targetMinutes = 480 // 8 hours
        val plannedMinutes = calculateDurationMinutes(1380, 410) // 470 minutes (7h 50m)
        val diff = plannedMinutes - targetMinutes
        assertEquals(-10, diff) // 10 minutes less than target

        val weekendPlanned = calculateDurationMinutes(1410, 480) // 510 minutes (8h 30m)
        val weekendDiff = weekendPlanned - targetMinutes
        assertEquals(30, weekendDiff) // 30 minutes more than target
    }

    @Test
    fun test12HourClockFormatting() {
        assertEquals("12:00 AM", formatClockTime12h(0))
        assertEquals("6:50 AM", formatClockTime12h(410))
        assertEquals("12:00 PM", formatClockTime12h(720))
        assertEquals("11:00 PM", formatClockTime12h(1380))
        assertEquals("11:30 PM", formatClockTime12h(1410))
    }

    @Test
    fun testLeadTimeCalculation() {
        // Bedtime 11:00 PM (1380), lead time 30 min -> Reminder at 10:30 PM (1350)
        val reminder30 = (1380 - 30 + 1440) % 1440
        assertEquals(1350, reminder30)
        assertEquals("10:30 PM", formatClockTime12h(reminder30))

        // Bedtime 12:15 AM (15), lead time 30 min -> Reminder at 11:45 PM (1425)
        val reminderAcrossMidnight = (15 - 30 + 1440) % 1440
        assertEquals(1425, reminderAcrossMidnight)
        assertEquals("11:45 PM", formatClockTime12h(reminderAcrossMidnight))
    }

    @Test
    fun testSafeNextOccurrenceWeekday() {
        val weekdays = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
        )

        // Monday at 9:00 AM
        val mondayMorning = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 3, 9, 0)
        }.timeInMillis

        // Next 11:00 PM bedtime (1380) on weekdays
        val nextBedtime = SleepAlarms.safeNextOccurrence(weekdays, 1380, mondayMorning)
        assertTrue(nextBedtime > mondayMorning)

        val cal = Calendar.getInstance().apply { timeInMillis = nextBedtime }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testSafeNextOccurrenceWeekend() {
        val weekends = setOf(Calendar.SATURDAY, Calendar.SUNDAY)

        // Monday at 9:00 AM
        val mondayMorning = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 3, 9, 0)
        }.timeInMillis

        // Next 8:00 AM wake-up (480) on weekends should be upcoming Saturday
        val nextWeekendWakeup = SleepAlarms.safeNextOccurrence(weekends, 480, mondayMorning)
        assertTrue(nextWeekendWakeup > mondayMorning)

        val cal = Calendar.getInstance().apply { timeInMillis = nextWeekendWakeup }
        assertEquals(Calendar.SATURDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }
}
