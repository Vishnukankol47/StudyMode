package com.studymode.app

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTimingTest {
    @Test
    fun nextOccurrenceUsesRequestedWeekdayAndTime() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 3, 9, 0)
        }

        val occurrence = Calendar.getInstance().apply {
            timeInMillis = nextScheduleOccurrenceMillis(
                Calendar.MONDAY,
                10 * 60 + 30,
                now.timeInMillis,
            )
        }

        assertTrue(occurrence.timeInMillis > now.timeInMillis)
        assertEquals(Calendar.MONDAY, occurrence.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, occurrence.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, occurrence.get(Calendar.MINUTE))
    }

    @Test
    fun pastOccurrenceMovesToFollowingWeek() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 3, 11, 0)
        }

        val occurrence = Calendar.getInstance().apply {
            timeInMillis = nextScheduleOccurrenceMillis(
                Calendar.MONDAY,
                10 * 60,
                now.timeInMillis,
            )
        }

        assertTrue(occurrence.timeInMillis > now.timeInMillis)
        assertEquals(Calendar.MONDAY, occurrence.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, occurrence.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, occurrence.get(Calendar.MINUTE))
    }

    @Test
    fun multiDayReminderSelectsTheNextConfiguredWeekday() {
        val now = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 3, 21, 0)
        }

        val occurrence = Calendar.getInstance().apply {
            timeInMillis = nextScheduleOccurrenceMillis(
                setOf(Calendar.MONDAY, Calendar.WEDNESDAY, Calendar.FRIDAY),
                8 * 60,
                now.timeInMillis,
            )
        }

        assertEquals(Calendar.WEDNESDAY, occurrence.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, occurrence.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun overnightScheduleRemainsActiveAfterMidnightOnItsPreviousSelectedDay() {
        val monday = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.MARCH, 3, 23, 0)
        }
        val schedule = StudySchedule(
            id = 1,
            title = "Night study",
            dayOfWeek = Calendar.MONDAY,
            startMinute = 22 * 60,
            durationMinutes = 180,
            enabled = true,
            activeDays = setOf(Calendar.MONDAY),
            endMinute = 60,
        )
        val tuesdayAt0030 = Calendar.getInstance().apply {
            timeInMillis = monday.timeInMillis
            add(Calendar.MINUTE, 90)
        }.timeInMillis

        assertTrue(isScheduleActiveAt(schedule, monday.timeInMillis))
        assertTrue(isScheduleActiveAt(schedule, tuesdayAt0030))
    }
}
