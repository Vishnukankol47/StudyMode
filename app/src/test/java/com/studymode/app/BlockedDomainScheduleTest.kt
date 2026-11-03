package com.studymode.app

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockedDomainScheduleTest {
    @Test
    fun recurringDomainRuleUsesSelectedLocalDayAndTime() {
        val rule = BlockedDomain(
            domain = "example.com",
            activeDays = setOf(Calendar.MONDAY),
            startMinute = 9 * 60,
            endMinute = 17 * 60,
        )
        val mondayAt0900 = calendarMillis(Calendar.MARCH, 3, 9, 0)
        val mondayAt1700 = calendarMillis(Calendar.MARCH, 3, 17, 0)
        val tuesdayAt1000 = calendarMillis(Calendar.MARCH, 4, 10, 0)

        assertTrue(isBlockedDomainActiveAt(rule, mondayAt0900))
        assertFalse(isBlockedDomainActiveAt(rule, mondayAt1700))
        assertFalse(isBlockedDomainActiveAt(rule, tuesdayAt1000))
    }

    @Test
    fun overnightDomainRuleContinuesIntoTheFollowingDay() {
        val rule = BlockedDomain(
            domain = "example.com",
            activeDays = setOf(Calendar.MONDAY),
            startMinute = 22 * 60,
            endMinute = 60,
        )

        assertTrue(isBlockedDomainActiveAt(rule, calendarMillis(Calendar.MARCH, 3, 23, 0)))
        assertTrue(isBlockedDomainActiveAt(rule, calendarMillis(Calendar.MARCH, 4, 0, 30)))
        assertFalse(isBlockedDomainActiveAt(rule, calendarMillis(Calendar.MARCH, 4, 1, 0)))
    }

    @Test
    fun unscheduledDomainRuleIsAlwaysActive() {
        val rule = BlockedDomain(domain = "example.com")
        assertTrue(isBlockedDomainActiveAt(rule, calendarMillis(Calendar.MARCH, 4, 3, 15)))
    }

    private fun calendarMillis(month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2025, month, day, hour, minute)
        }.timeInMillis
}
