package com.studymode.app

import java.util.Calendar

data class FocusMilestone(
    val hours: Int,
    val unlockedAtMillis: Long?,
)

val FOCUS_MILESTONE_HOURS = listOf(
    1, 3, 5, 10, 15, 25, 50, 75,
    100, 150, 200, 250, 400, 500, 750, 1000,
)

internal fun milestoneUnlockTimes(
    completedSessions: List<Pair<Long, Long>>,
): Map<Int, Long> {
    val unlockTimes = mutableMapOf<Int, Long>()
    val thresholds = FOCUS_MILESTONE_HOURS.associateWith { it * 60L * 60L }
    var totalSeconds = 0L

    completedSessions.sortedBy { it.first }.forEach { (completedAtMillis, elapsedSeconds) ->
        if (elapsedSeconds <= 0) return@forEach
        totalSeconds += elapsedSeconds
        thresholds.forEach { (hours, thresholdSeconds) ->
            if (hours !in unlockTimes && thresholdSeconds <= totalSeconds) {
                unlockTimes[hours] = completedAtMillis
            }
        }
    }
    return unlockTimes
}

internal fun currentFocusStreak(
    sessionDayStarts: Set<Long>,
    todayStartMillis: Long,
): Int {
    val day = Calendar.getInstance().apply { timeInMillis = todayStartMillis }
    if (todayStartMillis !in sessionDayStarts) day.add(Calendar.DAY_OF_YEAR, -1)
    if (day.timeInMillis !in sessionDayStarts) return 0

    var streak = 0
    while (day.timeInMillis in sessionDayStarts) {
        streak += 1
        day.add(Calendar.DAY_OF_YEAR, -1)
    }
    return streak
}

internal fun longestFocusStreak(sessionDayStarts: Set<Long>): Int {
    if (sessionDayStarts.isEmpty()) return 0
    val sortedDays = sessionDayStarts.sorted()
    var longest = 0
    var current = 0
    var lastDayCal: Calendar? = null

    for (dayMillis in sortedDays) {
        val cal = Calendar.getInstance().apply { timeInMillis = dayMillis }
        if (lastDayCal == null) {
            current = 1
        } else {
            val nextDayCal = Calendar.getInstance().apply {
                timeInMillis = lastDayCal!!.timeInMillis
                add(Calendar.DAY_OF_YEAR, 1)
            }
            if (cal.get(Calendar.YEAR) == nextDayCal.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == nextDayCal.get(Calendar.DAY_OF_YEAR)
            ) {
                current += 1
            } else if (dayMillis > nextDayCal.timeInMillis) {
                current = 1
            }
        }
        lastDayCal = cal
        if (current > longest) longest = current
    }
    return longest
}
