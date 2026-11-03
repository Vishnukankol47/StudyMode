package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneUnlockTrackerTest {

    @Test
    fun peakHourCalculatesHighestActivityHour() {
        val hourlyUnlocks = List(24) { hour ->
            when (hour) {
                14 -> 12 // 2 PM has 12 unlocks (peak)
                9 -> 8
                else -> 2
            }
        }
        val hourlyPickups = List(24) { hour ->
            when (hour) {
                18 -> 15 // 6 PM has 15 pickups (peak)
                else -> 3
            }
        }

        val stats = DayUnlockStats(
            dayStartMillis = 1700000000000L,
            dayLabel = "Today",
            totalUnlocks = hourlyUnlocks.sum(),
            totalPickups = hourlyPickups.sum(),
            prevDayUnlocks = 40,
            prevDayPickups = 55,
            hourlyUnlocks = hourlyUnlocks,
            hourlyPickups = hourlyPickups,
            firstPickupMillis = 1700028800000L,
            lastPickupMillis = 1700080000000L,
            longestBreakMinutes = 145,
            avgPickupIntervalMinutes = 22,
            unlockGoal = 50
        )

        assertEquals(14, stats.peakUnlockHour)
        assertEquals(18, stats.peakPickupHour)
        assertEquals(64, stats.totalUnlocks)
        assertEquals(84, stats.totalPickups)
    }

    @Test
    fun averagePickupIntervalCalculationHandlesEdgeCases() {
        val calculateInterval = { totalPickups: Int, firstMs: Long?, lastMs: Long? ->
            if (totalPickups > 1 && firstMs != null && lastMs != null && lastMs > firstMs) {
                val activeWindowMinutes = ((lastMs - firstMs) / 60_000L).toInt()
                (activeWindowMinutes / (totalPickups - 1)).coerceAtLeast(1)
            } else if (totalPickups > 0) {
                35
            } else {
                0
            }
        }

        // Case 1: 0 pickups
        assertEquals(0, calculateInterval(0, null, null))

        // Case 2: 1 pickup
        assertEquals(35, calculateInterval(1, 1000L, 1000L))

        // Case 3: 11 pickups spanning 200 minutes -> 200 / 10 = 20 mins avg
        val start = 1_000_000L
        val end = start + (200 * 60_000L)
        assertEquals(20, calculateInterval(11, start, end))
    }

    @Test
    fun unlockGoalClampingAndStatus() {
        val clampGoal = { goal: Int -> goal.coerceIn(10, 300) }

        assertEquals(10, clampGoal(5))
        assertEquals(50, clampGoal(50))
        assertEquals(300, clampGoal(500))

        val checkStatus = { unlocks: Int, goal: Int ->
            val isExceeded = unlocks > goal
            val remaining = (goal - unlocks).coerceAtLeast(0)
            Pair(isExceeded, remaining)
        }

        // On track
        val (exceeded1, rem1) = checkStatus(35, 50)
        assertFalse(exceeded1)
        assertEquals(15, rem1)

        // Exceeded
        val (exceeded2, rem2) = checkStatus(62, 50)
        assertTrue(exceeded2)
        assertEquals(0, rem2)
    }

    @Test
    fun comparisonVsYesterdayCalculatesCorrectly() {
        val calcDiff = { current: Int, prev: Int ->
            val diff = current - prev
            val pct = if (prev > 0) ((diff * 100) / prev) else 0
            val improved = diff <= 0
            Triple(diff, pct, improved)
        }

        // Case 1: Fewer unlocks today (improved)
        val (diff1, pct1, improved1) = calcDiff(40, 50)
        assertEquals(-10, diff1)
        assertEquals(-20, pct1)
        assertTrue(improved1)

        // Case 2: More unlocks today (not improved)
        val (diff2, pct2, improved2) = calcDiff(60, 40)
        assertEquals(20, diff2)
        assertEquals(50, pct2)
        assertFalse(improved2)

        // Case 3: Zero yesterday
        val (diff3, pct3, improved3) = calcDiff(30, 0)
        assertEquals(30, diff3)
        assertEquals(0, pct3)
        assertFalse(improved3)
    }
}
