package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusAchievementsTest {
    @Test
    fun definesExactlyTheRequestedCumulativeMilestones() {
        assertEquals(
            listOf(1, 3, 5, 10, 15, 25, 50, 75, 100, 150, 200, 250, 400, 500, 750, 1000),
            FOCUS_MILESTONE_HOURS,
        )
    }

    @Test
    fun cumulativeCompletedFocusUnlocksMilestonesAtCompletionDate() {
        val unlocks = milestoneUnlockTimes(
            listOf(
                200_000L to 2 * 60 * 60L,
                100_000L to 90 * 60L,
            ),
        )

        assertEquals(100_000L, unlocks[1])
        assertEquals(200_000L, unlocks[3])
        assertFalse(5 in unlocks)
    }

    @Test
    fun currentStreakIncludesTodayOrYesterdayAndStopsAtMissingDay() {
        val today = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JANUARY, 10)
        }.timeInMillis
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val twoDaysAgo = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -2)
        }.timeInMillis

        assertEquals(3, currentFocusStreak(setOf(today, yesterday, twoDaysAgo), today))
        assertEquals(2, currentFocusStreak(setOf(yesterday, twoDaysAgo), today))
        assertEquals(0, currentFocusStreak(setOf(twoDaysAgo), today))
    }

    @Test
    fun longestStreakFindsMaxConsecutiveDays() {
        val base = Calendar.getInstance().apply {
            clear()
            set(2025, Calendar.JANUARY, 1)
        }.timeInMillis

        // Streak 1: 3 days (Jan 1, 2, 3)
        // Streak 2: 5 days (Jan 6, 7, 8, 9, 10)
        val days = setOf(
            base,
            base + 86_400_000L,
            base + 2 * 86_400_000L,
            base + 5 * 86_400_000L,
            base + 6 * 86_400_000L,
            base + 7 * 86_400_000L,
            base + 8 * 86_400_000L,
            base + 9 * 86_400_000L,
        )

        assertEquals(5, longestFocusStreak(days))
    }
}
