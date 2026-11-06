package com.studymode.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.net.IDN
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date

data class FocusSessionRecord(
    val id: Long,
    val startedAtMillis: Long,
    val elapsedSeconds: Long,
    val targetMinutes: Int,
    val goal: String,
    val completed: Boolean,
    val finishedAtMillis: Long = startedAtMillis + elapsedSeconds * 1000L,
    val breakSeconds: Long = 0L,
    val mode: String = "Focus",
    val subject: String = "",
    val notes: String = "",
    val timelineEventsJson: String = "",
    val appsUsedJson: String = "",
) {
    val displaySubject: String
        get() = subject.ifBlank { goal.ifBlank { "Study Focus" } }
}

data class StudySchedule(
    val id: Long,
    val title: String,
    val dayOfWeek: Int,
    val startMinute: Int,
    val durationMinutes: Int,
    val enabled: Boolean,
    val activeDays: Set<Int> = setOf(dayOfWeek),
    val endMinute: Int = (startMinute + durationMinutes) % (24 * 60),
    val reminderEnabled: Boolean = false,
    val breaksEnabled: Boolean = true,
    val focusIntervalMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val intervalsBeforeLongBreak: Int = 4,
    val blockedPackages: Set<String> = emptySet(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

data class ScheduleWeekDay(
    val dayStartMillis: Long,
    val dayOfWeek: Int,
    val schedules: List<StudySchedule>,
)

data class ScheduleProgress(
    val totalSchedules: Int,
    val enabledSchedules: Int,
    val completedFocusSessions: Int,
    val totalFocusSeconds: Long,
    val scheduledSeconds: Long,
    val weeklyReport: List<DailyFocusReport>,
)

data class BlockedApp(
    val packageName: String,
    val label: String,
    val blocked: Boolean,
)

data class BatchedNotification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestampMillis: Long,
    val key: String,
)

data class AppDailyLimit(
    val packageName: String,
    val label: String,
    val minutes: Int,
    val warningPercent: Int = 0,
    val blockingEnabled: Boolean = true,
)

data class SleepSchedule(
    val id: Long = 1L,
    val enabled: Boolean = true,
    val bedtimeMinute: Int = 23 * 60,
    val wakeupMinute: Int = 6 * 60 + 50,
    val activeDays: Set<Int> = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
    ),
    val targetDurationMinutes: Int = 8 * 60,
    val reminderEnabled: Boolean = true,
    val reminderAdvanceMinutes: Int = 30,
    val wakeupReminderEnabled: Boolean = true,
    val protectionEnabled: Boolean = false,
    val protectionBlockedPackages: Set<String> = emptySet(),
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
) {
    val isWeekend: Boolean get() = id == 2L || activeDays.all { it == Calendar.SATURDAY || it == Calendar.SUNDAY }
}

data class SleepLog(
    val id: Long = 0L,
    val dateMillis: Long,
    val bedtimeMinute: Int,
    val wakeupMinute: Int,
    val reportedBedtimeMillis: Long,
    val reportedWakeupMillis: Long,
    val durationMinutes: Int,
    val qualityRating: Int = 0,
    val note: String = "",
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L,
)

data class DailyFocusReport(
    val dayStartMillis: Long,
    val elapsedSeconds: Long,
    val sessionCount: Int,
    val scheduledSeconds: Long = 0L,
)

data class BlockedDomain(
    val domain: String,
    val enabled: Boolean = true,
    val activeDays: Set<Int> = emptySet(),
    val startMinute: Int = -1,
    val endMinute: Int = -1,
)

data class ActiveTimer(
    val startedAtMillis: Long,
    val segmentStartedAtMillis: Long,
    val segmentTargetMillis: Long,
    val savedRemainingMillis: Long,
    val elapsedBeforeSegmentMillis: Long,
    val targetMinutes: Int,
    val goal: String,
    val running: Boolean,
) {
    fun remainingAt(nowMillis: Long): Long =
        if (running) {
            (segmentTargetMillis - (nowMillis - segmentStartedAtMillis)).coerceAtLeast(0)
        } else {
            savedRemainingMillis.coerceAtLeast(0)
        }

    fun elapsedAt(nowMillis: Long): Long =
        elapsedBeforeSegmentMillis +
            if (running) (nowMillis - segmentStartedAtMillis).coerceAtLeast(0) else 0
}

data class UserProfile(
    val name: String,
    val email: String,
    val avatarUri: String?,
    val startedAtMillis: Long,
)

data class FocusOverview(
    val totalSeconds: Long,
    val currentStreakDays: Int,
    val completedSessions: Int,
)

data class WeeklyFocusSummary(
    val totalSeconds: Long,
    val completedSessions: Int,
    val averageSecondsPerDay: Long,
    val goalProgressPercent: Int,
)

enum class AppCategory(val id: String, val displayName: String, val colorHex: Long) {
    DISTRACTING("DISTRACTING", "Distracting", 0xFFFF7A00),
    PRODUCTIVE("PRODUCTIVE", "Productive", 0xFF4CAF50),
    OTHERS("OTHERS", "Others", 0xFF8E8E93);

    companion object {
        fun fromId(id: String?): AppCategory {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: OTHERS
        }
    }
}

class LocalStudyStore(context: Context) {
    private val appContext = context.applicationContext
    private val database = StudyDatabase(appContext)
    private val timerPreferences = context.getSharedPreferences("active_focus_timer", Context.MODE_PRIVATE)
    private val blockerPreferences = context.getSharedPreferences("app_blocker", Context.MODE_PRIVATE)
    private val settingsPreferences = context.getSharedPreferences("studymode_settings", Context.MODE_PRIVATE)
    private val profilePreferences = context.getSharedPreferences("local_profile", Context.MODE_PRIVATE)

    fun profile(nowMillis: Long = System.currentTimeMillis()): UserProfile {
        val startedAt = profilePreferences.getLong(KEY_PROFILE_STARTED_AT, 0L).takeIf { it > 0 }
            ?: nowMillis.also {
                profilePreferences.edit().putLong(KEY_PROFILE_STARTED_AT, it).apply()
            }
        return UserProfile(
            name = profilePreferences.getString(KEY_PROFILE_NAME, DEFAULT_PROFILE_NAME).orEmpty(),
            email = profilePreferences.getString(KEY_PROFILE_EMAIL, "").orEmpty(),
            avatarUri = profilePreferences.getString(KEY_PROFILE_AVATAR_URI, null),
            startedAtMillis = startedAt,
        )
    }

    fun updateProfile(name: String, email: String) {
        require(name.isNotBlank()) { "Enter a display name." }
        require(email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            "Enter a valid email address or leave it blank."
        }
        profilePreferences.edit()
            .putString(KEY_PROFILE_NAME, name.trim().take(MAX_PROFILE_NAME_LENGTH))
            .putString(KEY_PROFILE_EMAIL, email.trim().take(MAX_PROFILE_EMAIL_LENGTH))
            .apply()
    }

    fun updateProfileAvatar(uri: String?) {
        profilePreferences.edit().apply {
            if (uri == null) remove(KEY_PROFILE_AVATAR_URI) else putString(KEY_PROFILE_AVATAR_URI, uri)
        }.apply()
    }

    fun focusOverview(nowMillis: Long = System.currentTimeMillis()): FocusOverview {
        val sessions = completedFocusSessions()
        val totalSeconds = sessions.sumOf { it.second }
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis
        val sessionDays = sessions.mapTo(mutableSetOf()) { (startedAt, _) ->
            Calendar.getInstance().apply {
                timeInMillis = startedAt
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        return FocusOverview(
            totalSeconds = totalSeconds,
            currentStreakDays = currentFocusStreak(sessionDays, todayStart),
            completedSessions = sessions.size,
        )
    }

    fun focusMilestones(): List<FocusMilestone> {
        val savedUnlocks = synchronizeFocusAchievements()
        return FOCUS_MILESTONE_HOURS.map { hours ->
            FocusMilestone(hours, savedUnlocks[hours])
        }
    }

    private fun synchronizeFocusAchievements(): Map<Int, Long> {
        val completedSessions = completedFocusSessions()
        val unlockTimes = milestoneUnlockTimes(completedSessions)
        val writable = database.writableDatabase
        writable.beginTransaction()
        try {
            unlockTimes.forEach { (hours, unlockedAt) ->
                val values = ContentValues().apply {
                    put("hours", hours)
                    put("unlocked_at", unlockedAt)
                }
                writable.insertWithOnConflict(
                    "focus_achievements",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            writable.setTransactionSuccessful()
        } finally {
            writable.endTransaction()
        }

        val savedUnlocks = writable.rawQuery(
            "SELECT hours, unlocked_at FROM focus_achievements",
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) put(cursor.getInt(0), cursor.getLong(1))
            }
        }
        return savedUnlocks
    }

    fun firstWeeklyReportAvailableAt(): Long {
        val startedAt = profile().startedAtMillis
        return Calendar.getInstance().apply {
            timeInMillis = startedAt
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 7)
        }.timeInMillis
    }

    fun weeklyFocusSummary(nowMillis: Long = System.currentTimeMillis()): WeeklyFocusSummary {
        val end = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val endMillis = end.timeInMillis
        end.add(Calendar.DAY_OF_YEAR, -7)
        val startMillis = end.timeInMillis
        val sessions = completedFocusSessions(startMillis, endMillis)
        val totalSeconds = sessions.sumOf { it.second }
        val weeklyGoalSeconds = dailyGoalMinutes() * 60L * 7L
        return WeeklyFocusSummary(
            totalSeconds = totalSeconds,
            completedSessions = sessions.size,
            averageSecondsPerDay = totalSeconds / 7,
            goalProgressPercent = if (weeklyGoalSeconds == 0L) 0
                else ((totalSeconds * 100) / weeklyGoalSeconds).toInt().coerceAtMost(100),
        )
    }

    fun completedFocusSessions(
        startMillis: Long = Long.MIN_VALUE,
        endMillis: Long = Long.MAX_VALUE,
    ): List<Pair<Long, Long>> =
        database.readableDatabase.rawQuery(
            """
            SELECT finished_at, elapsed_seconds
            FROM focus_sessions
            WHERE completed = 1 AND elapsed_seconds > 0 AND finished_at >= ? AND finished_at < ?
            ORDER BY finished_at, id
            """.trimIndent(),
            arrayOf(startMillis.toString(), endMillis.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getLong(1))
            }
        }

    fun dailyGoalMinutes(): Int =
        database.readableDatabase.rawQuery(
            "SELECT daily_minutes FROM study_goal WHERE id = 1",
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else DEFAULT_DAILY_GOAL_MINUTES
        }

    fun setDailyGoalMinutes(minutes: Int) {
        require(minutes in MIN_DAILY_GOAL_MINUTES..MAX_DAILY_GOAL_MINUTES)
        val values = ContentValues().apply { put("daily_minutes", minutes) }
        database.writableDatabase.insertWithOnConflict(
            "study_goal",
            null,
            values.apply { put("id", Integer.valueOf(1)) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun todayFocusSeconds(nowMillis: Long = System.currentTimeMillis()): Long {
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return database.readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(elapsed_seconds), 0) FROM focus_sessions WHERE started_at >= ?",
            arrayOf(startOfDay.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun mapFocusSessionCursor(cursor: Cursor): FocusSessionRecord {
        val id = cursor.getLong(0)
        val started = cursor.getLong(1)
        val elapsed = cursor.getLong(2)
        val targetM = cursor.getInt(3)
        val goal = cursor.getString(4)
        val completed = cursor.getInt(5) == 1
        val finished = if (cursor.isNull(6) || cursor.getLong(6) == 0L) started + elapsed * 1000L else cursor.getLong(6)
        val defaultMode = when {
            goal.contains("Pomodoro", ignoreCase = true) -> "Pomodoro"
            goal.contains("Stopwatch", ignoreCase = true) -> "Stopwatch"
            else -> "Focus"
        }
        val totalSpanSec = ((finished - started) / 1000L).coerceAtLeast(elapsed)
        val defaultBreakSec = (totalSpanSec - elapsed).coerceAtLeast(0L)

        val subjectIdx = cursor.getColumnIndex("subject")
        val notesIdx = cursor.getColumnIndex("notes")
        val modeIdx = cursor.getColumnIndex("mode")
        val breakSecIdx = cursor.getColumnIndex("break_seconds")
        val timelineEventsIdx = cursor.getColumnIndex("timeline_events")
        val appsUsedIdx = cursor.getColumnIndex("apps_used")

        val subjectVal = if (subjectIdx >= 0 && !cursor.isNull(subjectIdx)) cursor.getString(subjectIdx) else ""
        val notesVal = if (notesIdx >= 0 && !cursor.isNull(notesIdx)) cursor.getString(notesIdx) else ""
        val modeVal = if (modeIdx >= 0 && !cursor.isNull(modeIdx) && cursor.getString(modeIdx).isNotBlank()) cursor.getString(modeIdx) else defaultMode
        val storedBreakSec = if (breakSecIdx >= 0 && !cursor.isNull(breakSecIdx)) cursor.getLong(breakSecIdx) else defaultBreakSec
        val timelineVal = if (timelineEventsIdx >= 0 && !cursor.isNull(timelineEventsIdx)) cursor.getString(timelineEventsIdx) else ""
        val appsUsedVal = if (appsUsedIdx >= 0 && !cursor.isNull(appsUsedIdx)) cursor.getString(appsUsedIdx) else ""

        return FocusSessionRecord(
            id = id,
            startedAtMillis = started,
            elapsedSeconds = elapsed,
            targetMinutes = targetM,
            goal = goal,
            completed = completed,
            finishedAtMillis = finished,
            breakSeconds = storedBreakSec,
            mode = modeVal,
            subject = subjectVal,
            notes = notesVal,
            timelineEventsJson = timelineVal,
            appsUsedJson = appsUsedVal,
        )
    }

    fun recentSessions(limit: Int = 30): List<FocusSessionRecord> =
        database.readableDatabase.rawQuery(
            """
            SELECT id, started_at, elapsed_seconds, target_minutes, goal, completed, finished_at,
                   subject, notes, mode, break_seconds, timeline_events, apps_used
            FROM focus_sessions
            ORDER BY started_at DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(limit.coerceIn(1, 100).toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(mapFocusSessionCursor(cursor))
                }
            }
        }

    fun focusSessions(
        startMillis: Long = 0L,
        endMillis: Long = Long.MAX_VALUE,
    ): List<FocusSessionRecord> =
        database.readableDatabase.rawQuery(
            """
            SELECT id, started_at, elapsed_seconds, target_minutes, goal, completed, finished_at,
                   subject, notes, mode, break_seconds, timeline_events, apps_used
            FROM focus_sessions
            WHERE started_at >= ? AND started_at <= ?
            ORDER BY started_at DESC
            """.trimIndent(),
            arrayOf(startMillis.toString(), endMillis.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(mapFocusSessionCursor(cursor))
                }
            }
        }

    fun focusSession(id: Long): FocusSessionRecord? =
        database.readableDatabase.rawQuery(
            """
            SELECT id, started_at, elapsed_seconds, target_minutes, goal, completed, finished_at,
                   subject, notes, mode, break_seconds, timeline_events, apps_used
            FROM focus_sessions
            WHERE id = ?
            """.trimIndent(),
            arrayOf(id.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) mapFocusSessionCursor(cursor) else null
        }

    fun saveFocusSession(record: FocusSessionRecord): Long {
        val values = ContentValues().apply {
            put("started_at", record.startedAtMillis)
            put("finished_at", record.finishedAtMillis)
            put("elapsed_seconds", record.elapsedSeconds)
            put("target_minutes", record.targetMinutes)
            put("goal", record.goal)
            put("completed", if (record.completed) 1 else 0)
            put("subject", record.subject)
            put("notes", record.notes)
            put("mode", record.mode)
            put("break_seconds", record.breakSeconds)
            put("timeline_events", record.timelineEventsJson)
            put("apps_used", record.appsUsedJson)
        }
        val id = if (record.id > 0) {
            database.writableDatabase.update("focus_sessions", values, "id = ?", arrayOf(record.id.toString()))
            record.id
        } else {
            database.writableDatabase.insert("focus_sessions", null, values)
        }
        if (record.completed) synchronizeFocusAchievements()
        return id
    }

    fun deleteFocusSession(id: Long): Boolean {
        val count = database.writableDatabase.delete("focus_sessions", "id = ?", arrayOf(id.toString()))
        if (count > 0) synchronizeFocusAchievements()
        return count > 0
    }

    fun clearAllFocusSessions() {
        database.writableDatabase.delete("focus_sessions", null, null)
        synchronizeFocusAchievements()
    }

    fun focusStreaks(nowMillis: Long = System.currentTimeMillis()): Pair<Int, Int> {
        val completedSessions = completedFocusSessions()
        val cal = Calendar.getInstance()
        val sessionDays = completedSessions.mapTo(mutableSetOf()) { (startedAt, _) ->
            cal.apply {
                timeInMillis = startedAt
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        val todayStart = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val current = currentFocusStreak(sessionDays, todayStart)
        val longest = maxOf(longestFocusStreak(sessionDays), current)
        return current to longest
    }

    fun weeklyFocusReport(nowMillis: Long = System.currentTimeMillis()): List<DailyFocusReport> {
        val firstDay = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val startMillis = firstDay.timeInMillis
        val totalsByDay = mutableMapOf<Long, Pair<Long, Int>>()

        database.readableDatabase.rawQuery(
            """
            SELECT started_at, elapsed_seconds
            FROM focus_sessions
            WHERE started_at >= ?
            """.trimIndent(),
            arrayOf(startMillis.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val sessionDay = Calendar.getInstance().apply {
                    timeInMillis = cursor.getLong(0)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val previous = totalsByDay[sessionDay] ?: (0L to 0)
                totalsByDay[sessionDay] = (previous.first + cursor.getLong(1)) to (previous.second + 1)
            }
        }

        return (0..6).map { offset ->
            val dayStartMillis = (firstDay.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }.timeInMillis
            val totals = totalsByDay[dayStartMillis] ?: (0L to 0)
            DailyFocusReport(dayStartMillis, totals.first, totals.second)
        }
    }

    fun schedules(): List<StudySchedule> =
        database.readableDatabase.rawQuery(
            """
            SELECT id, title, day_of_week, start_minute, duration_minutes, enabled,
                   weekdays, end_minute, reminder_enabled, breaks_enabled,
                   focus_interval_minutes, short_break_minutes, long_break_minutes,
                   intervals_before_long_break, blocked_packages, created_at, updated_at
            FROM study_schedules
            ORDER BY day_of_week, start_minute
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        cursor.toStudySchedule(),
                    )
                }
            }
        }

    fun schedule(id: Long): StudySchedule? =
        database.readableDatabase.rawQuery(
            """
            SELECT id, title, day_of_week, start_minute, duration_minutes, enabled,
                   weekdays, end_minute, reminder_enabled, breaks_enabled,
                   focus_interval_minutes, short_break_minutes, long_break_minutes,
                   intervals_before_long_break, blocked_packages, created_at, updated_at
            FROM study_schedules
            WHERE id = ?
            """.trimIndent(),
            arrayOf(id.toString()),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else {
                cursor.toStudySchedule()
            }
        }

    fun addSchedule(
        title: String,
        dayOfWeek: Int,
        startMinute: Int,
        durationMinutes: Int,
    ): Long {
        require(title.isNotBlank())
        require(dayOfWeek in Calendar.SUNDAY..Calendar.SATURDAY)
        require(startMinute in 0 until 24 * 60)
        require(durationMinutes in 5..(24 * 60))
        val endMinute = (startMinute + durationMinutes) % (24 * 60)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title.trim())
            put("day_of_week", dayOfWeek)
            put("start_minute", startMinute)
            put("duration_minutes", durationMinutes)
            put("enabled", 1)
            put("weekdays", dayOfWeek.toString())
            put("end_minute", endMinute)
            put("created_at", now)
            put("updated_at", now)
        }
        return database.writableDatabase.insertOrThrow("study_schedules", null, values)
    }

    fun saveSchedule(schedule: StudySchedule): Long {
        require(schedule.title.isNotBlank()) { "Enter a schedule name." }
        require(schedule.activeDays.isNotEmpty()) { "Select at least one active day." }
        require(schedule.activeDays.all { it in Calendar.SUNDAY..Calendar.SATURDAY }) {
            "Choose valid weekdays."
        }
        require(schedule.startMinute in 0 until 24 * 60) { "Choose a valid start time." }
        require(schedule.endMinute in 0 until 24 * 60 && schedule.endMinute != schedule.startMinute) {
            "End time must be different from start time."
        }
        val duration = (schedule.endMinute - schedule.startMinute + 24 * 60) % (24 * 60)
        require(duration in 1..(24 * 60 - 1)) { "Schedule duration is invalid." }
        require(schedule.focusIntervalMinutes in 5..180)
        require(schedule.shortBreakMinutes in 1..60)
        require(schedule.longBreakMinutes in 1..120)
        require(schedule.intervalsBeforeLongBreak in 2..12)

        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", schedule.title.trim().take(60))
            put("day_of_week", schedule.activeDays.minOrNull() ?: schedule.dayOfWeek)
            put("start_minute", schedule.startMinute)
            put("duration_minutes", Integer.valueOf(duration))
            put("enabled", if (schedule.enabled) 1 else 0)
            put("weekdays", schedule.activeDays.sorted().joinToString(","))
            put("end_minute", schedule.endMinute)
            put("reminder_enabled", if (schedule.reminderEnabled) 1 else 0)
            put("breaks_enabled", if (schedule.breaksEnabled) 1 else 0)
            put("focus_interval_minutes", schedule.focusIntervalMinutes)
            put("short_break_minutes", schedule.shortBreakMinutes)
            put("long_break_minutes", schedule.longBreakMinutes)
            put("intervals_before_long_break", schedule.intervalsBeforeLongBreak)
            put("blocked_packages", schedule.blockedPackages.sorted().joinToString(","))
            put("updated_at", now)
        }
        if (schedule.id > 0) {
            val updated = database.writableDatabase.update(
                "study_schedules",
                values,
                "id = ?",
                arrayOf(schedule.id.toString()),
            )
            require(updated == 1) { "The schedule no longer exists." }
            return schedule.id
        }
        values.put("created_at", now)
        return database.writableDatabase.insertOrThrow("study_schedules", null, values)
    }

    fun hasSameNameTimingConflict(candidate: StudySchedule): Boolean {
        val name = candidate.title.trim()
        val candidateDuration = (candidate.endMinute - candidate.startMinute + 1440) % 1440
        if (candidateDuration == 0) return false
        val candidateIntervals = candidate.activeDays.map { day ->
            (day - Calendar.SUNDAY) * 1440 + candidate.startMinute to
                (day - Calendar.SUNDAY) * 1440 + candidate.startMinute + candidateDuration
        }
        return schedules().asSequence()
            .filter { it.id != candidate.id && it.title.equals(name, ignoreCase = true) }
            .any { existing ->
                val existingDuration = (existing.endMinute - existing.startMinute + 1440) % 1440
                val existingIntervals = existing.activeDays.map { day ->
                    (day - Calendar.SUNDAY) * 1440 + existing.startMinute to
                        (day - Calendar.SUNDAY) * 1440 + existing.startMinute + existingDuration
                }
                candidateIntervals.any { (candidateStart, candidateEnd) ->
                    existingIntervals.any { (existingStart, existingEnd) ->
                        listOf(-10_080, 0, 10_080).any { weekShift ->
                            candidateStart < existingEnd + weekShift &&
                                existingStart + weekShift < candidateEnd
                        }
                    }
                }
            }
    }

    fun setScheduleEnabled(id: Long, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        database.writableDatabase.update(
            "study_schedules",
            values,
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    fun deleteSchedule(id: Long) {
        database.writableDatabase.delete("study_schedules", "id = ?", arrayOf(id.toString()))
    }

    fun schedulesForDate(dayStartMillis: Long): List<StudySchedule> {
        val calendar = Calendar.getInstance().apply { timeInMillis = dayStartMillis }
        val day = calendar.get(Calendar.DAY_OF_WEEK)
        val previousDay = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }.get(Calendar.DAY_OF_WEEK)
        return schedules().filter { schedule ->
            day in schedule.activeDays ||
                (schedule.endMinute < schedule.startMinute &&
                    previousDay in schedule.activeDays &&
                    schedule.endMinute > 0)
        }.sortedBy { schedule ->
            if (schedule.endMinute < schedule.startMinute && previousDay in schedule.activeDays) {
                0
            } else {
                schedule.startMinute
            }
        }
    }

    fun activeScheduleBlockedPackages(nowMillis: Long = System.currentTimeMillis()): Set<String> =
        schedules().asSequence()
            .filter { it.enabled && isScheduleActiveAt(it, nowMillis) }
            .flatMap { it.blockedPackages.asSequence() }
            .toSet()

    fun scheduleProgress(weekOffset: Int = 0, nowMillis: Long = System.currentTimeMillis()): ScheduleProgress {
        val weekStart = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
            add(Calendar.WEEK_OF_YEAR, weekOffset)
        }
        val daily = (0..6).map { index ->
            val day = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, index) }
            val start = day.timeInMillis
            val next = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val actual = database.readableDatabase.rawQuery(
                """
                SELECT COALESCE(SUM(elapsed_seconds), 0), COUNT(*)
                FROM focus_sessions
                WHERE completed = 1 AND finished_at >= ? AND finished_at < ?
                """.trimIndent(),
                arrayOf(start.toString(), next.timeInMillis.toString()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getInt(1) else 0L to 0
            }
            val scheduled = schedules().filter { it.enabled && day.get(Calendar.DAY_OF_WEEK) in it.activeDays }
                .sumOf { it.durationMinutes.toLong() * 60L }
            DailyFocusReport(start, actual.first, actual.second, scheduled)
        }
        val allSchedules = schedules()
        val scheduledWeekSeconds = daily.sumOf { it.scheduledSeconds }
        return ScheduleProgress(
            totalSchedules = allSchedules.size,
            enabledSchedules = allSchedules.count { it.enabled },
            completedFocusSessions = daily.sumOf { it.sessionCount },
            totalFocusSeconds = daily.sumOf { it.elapsedSeconds },
            scheduledSeconds = scheduledWeekSeconds,
            weeklyReport = daily,
        )
    }

    fun appDailyLimits(): List<AppDailyLimit> =
        database.readableDatabase.rawQuery(
            """
            SELECT package_name, label, daily_minutes, warning_percent, blocking_enabled
            FROM app_daily_limits ORDER BY label COLLATE NOCASE
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        AppDailyLimit(
                            packageName = cursor.getString(0),
                            label = cursor.getString(1),
                            minutes = cursor.getInt(2),
                            warningPercent = cursor.getInt(3),
                            blockingEnabled = cursor.getInt(4) == 1,
                        ),
                    )
                }
            }
        }

    fun appDailyLimit(packageName: String): AppDailyLimit? =
        database.readableDatabase.rawQuery(
            """
            SELECT package_name, label, daily_minutes, warning_percent, blocking_enabled
            FROM app_daily_limits WHERE package_name = ?
            """.trimIndent(),
            arrayOf(packageName),
        ).use { cursor ->
            if (!cursor.moveToFirst()) null
            else AppDailyLimit(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getInt(2),
                cursor.getInt(3),
                cursor.getInt(4) == 1,
            )
        }

    fun setAppDailyLimit(
        packageName: String,
        label: String,
        minutes: Int?,
        warningPercent: Int = 0,
        blockingEnabled: Boolean = true,
    ) {
        if (minutes == null) {
            database.writableDatabase.delete(
                "app_daily_limits",
                "package_name = ?",
                arrayOf(packageName),
            )
            AppBlockingAccessibilityService.updateConfiguration(appContext)
            AppMonitorService.start(appContext)
            return
        }

        require(minutes in MIN_APP_LIMIT_MINUTES..MAX_APP_LIMIT_MINUTES)
        require(warningPercent in setOf(0, 50, 75, 90))
        val values = ContentValues().apply {
            put("package_name", packageName)
            put("label", label)
            put("daily_minutes", minutes)
            put("warning_percent", warningPercent)
            put("blocking_enabled", if (blockingEnabled) 1 else 0)
        }
        database.writableDatabase.insertWithOnConflict(
            "app_daily_limits",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        AppBlockingAccessibilityService.updateConfiguration(appContext)
        AppMonitorService.start(appContext)
    }

    fun blockedDomains(): List<BlockedDomain> =
        database.readableDatabase.rawQuery(
            """
            SELECT domain, enabled, active_days, start_minute, end_minute
            FROM blocked_domains ORDER BY domain COLLATE NOCASE
            """.trimIndent(),
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val days = cursor.getString(2)
                        .split(',')
                        .mapNotNull { it.toIntOrNull() }
                        .toSet()
                    add(
                        BlockedDomain(
                            domain = cursor.getString(0),
                            enabled = cursor.getInt(1) == 1,
                            activeDays = days,
                            startMinute = cursor.getInt(3),
                            endMinute = cursor.getInt(4),
                        ),
                    )
                }
            }
        }

    fun addBlockedDomain(input: String): String {
        val domain = normalizeBlockedDomain(input)
        val values = ContentValues().apply { put("domain", domain) }
        database.writableDatabase.insertWithOnConflict(
            "blocked_domains",
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return domain
    }

    fun renameBlockedDomain(existingDomain: String, input: String): String {
        val domain = normalizeBlockedDomain(input)
        val values = ContentValues().apply { put("domain", domain) }
        val updated = database.writableDatabase.update(
            "blocked_domains",
            values,
            "domain = ?",
            arrayOf(existingDomain),
        )
        check(updated == 1) { "The website rule no longer exists." }
        return domain
    }

    fun removeBlockedDomain(domain: String) {
        database.writableDatabase.delete("blocked_domains", "domain = ?", arrayOf(domain))
    }

    fun setBlockedDomainEnabled(domain: String, enabled: Boolean) {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        database.writableDatabase.update(
            "blocked_domains",
            values,
            "domain = ?",
            arrayOf(domain),
        )
    }

    fun setBlockedDomainSchedule(
        domain: String,
        activeDays: Set<Int>,
        startMinute: Int,
        endMinute: Int,
    ) {
        require(activeDays.isNotEmpty() && activeDays.all { it in Calendar.SUNDAY..Calendar.SATURDAY })
        require(startMinute in 0 until 24 * 60 && endMinute in 0 until 24 * 60)
        require(startMinute != endMinute) { "Choose different start and end times." }
        val values = ContentValues().apply {
            put("active_days", activeDays.sorted().joinToString(","))
            put("start_minute", startMinute)
            put("end_minute", endMinute)
        }
        database.writableDatabase.update(
            "blocked_domains",
            values,
            "domain = ?",
            arrayOf(domain),
        )
    }

    fun clearBlockedDomainSchedule(domain: String) {
        val values = ContentValues().apply {
            put("active_days", "")
            put("start_minute", -1)
            put("end_minute", -1)
        }
        database.writableDatabase.update(
            "blocked_domains",
            values,
            "domain = ?",
            arrayOf(domain),
        )
    }

    fun isDomainBlocked(hostname: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val host = hostname.lowercase(Locale.ROOT).trimEnd('.')
        if (host.isBlank()) return false
        return blockedDomains().any {
            it.enabled &&
                domainRuleMatches(host, it.domain) &&
                isBlockedDomainActiveAt(it, nowMillis)
        }
    }

    fun blockedApps(): List<BlockedApp> =
        database.readableDatabase.rawQuery(
            "SELECT package_name, label, blocked FROM blocked_apps ORDER BY label COLLATE NOCASE",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        BlockedApp(
                            packageName = cursor.getString(0),
                            label = cursor.getString(1),
                            blocked = cursor.getInt(2) == 1,
                        ),
                    )
                }
            }
        }

    fun setAppBlocked(packageName: String, label: String, blocked: Boolean) {
        val cleanPkg = packageName.trim()
        if (blocked) {
            val values = ContentValues().apply {
                put("package_name", cleanPkg)
                put("label", label)
                put("blocked", 1)
            }
            database.writableDatabase.insertWithOnConflict(
                "blocked_apps",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            blockerPreferences.edit().remove(KEY_BLOCKING_PAUSED_UNTIL).apply()
        } else {
            database.writableDatabase.delete(
                "blocked_apps",
                "LOWER(TRIM(package_name)) = LOWER(?)",
                arrayOf(cleanPkg),
            )
        }
        AppBlockingAccessibilityService.updateConfiguration(appContext)
        AppMonitorService.start(appContext)
    }

    fun isAppBlocked(packageName: String): Boolean =
        try {
            database.readableDatabase.rawQuery(
                "SELECT 1 FROM blocked_apps WHERE LOWER(TRIM(package_name)) = LOWER(TRIM(?)) AND blocked = 1 LIMIT 1",
                arrayOf(packageName.trim()),
            ).use { it.moveToFirst() }
        } catch (_: Exception) {
            false
        }

    fun pauseBlockingUntil(untilMillis: Long) {
        blockerPreferences.edit()
            .putLong(KEY_BLOCKING_PAUSED_UNTIL, untilMillis)
            .apply()
        AppBlockOverlayManager.dismiss(appContext)
    }

    fun pauseBlockingForFiveMinutes(nowMillis: Long = System.currentTimeMillis()) {
        blockerPreferences.edit()
            .putLong(KEY_BLOCKING_PAUSED_UNTIL, nowMillis + BLOCKING_PAUSE_MILLIS)
            .apply()
        AppBlockOverlayManager.dismiss(appContext)
    }

    fun resumeBlockingNow() {
        blockerPreferences.edit().remove(KEY_BLOCKING_PAUSED_UNTIL).apply()
        AppBlockingAccessibilityService.updateConfiguration(appContext)
        AppMonitorService.start(appContext)
    }

    fun blockingPausedUntil(): Long =
        blockerPreferences.getLong(KEY_BLOCKING_PAUSED_UNTIL, 0)

    fun focusOnlyBlockingEnabled(): Boolean =
        settingsPreferences.getBoolean(KEY_FOCUS_ONLY_BLOCKING, false)

    fun setFocusOnlyBlockingEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_FOCUS_ONLY_BLOCKING, enabled).apply()
        AppBlockingAccessibilityService.updateConfiguration(appContext)
        AppMonitorService.start(appContext)
    }

    fun isMindfulFrictionEnabled(): Boolean =
        settingsPreferences.getBoolean(KEY_MINDFUL_FRICTION_ENABLED, true)

    fun setMindfulFrictionEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_MINDFUL_FRICTION_ENABLED, enabled).apply()
    }

    fun mindfulFrictionDefaultTab(): String =
        settingsPreferences.getString(KEY_MINDFUL_FRICTION_DEFAULT_TAB, "BREATHE") ?: "BREATHE"

    fun setMindfulFrictionDefaultTab(tab: String) {
        settingsPreferences.edit().putString(KEY_MINDFUL_FRICTION_DEFAULT_TAB, tab).apply()
    }

    fun mindfulFrictionPauseDurationSeconds(): Int =
        settingsPreferences.getInt(KEY_MINDFUL_FRICTION_PAUSE_SECONDS, 10)

    fun setMindfulFrictionPauseDurationSeconds(seconds: Int) {
        val safeSeconds = seconds.coerceIn(5, 60)
        settingsPreferences.edit().putInt(KEY_MINDFUL_FRICTION_PAUSE_SECONDS, safeSeconds).apply()
    }

    fun mindfulFrictionBreathsTarget(): Int =
        settingsPreferences.getInt(KEY_MINDFUL_FRICTION_BREATHS_TARGET, 2)

    fun setMindfulFrictionBreathsTarget(breaths: Int) {
        val safeBreaths = breaths.coerceIn(1, 5)
        settingsPreferences.edit().putInt(KEY_MINDFUL_FRICTION_BREATHS_TARGET, safeBreaths).apply()
    }

    fun recordPhonePickup(nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDay = settingsPreferences.getString(KEY_UNLOCK_COUNT_DATE, "")
        val currentCount = if (savedDay == todayKey) {
            settingsPreferences.getInt(KEY_TODAY_PICKUPS, 0)
        } else {
            0
        }
        val newCount = currentCount + 1
        settingsPreferences.edit()
            .putString(KEY_UNLOCK_COUNT_DATE, todayKey)
            .putInt(KEY_TODAY_PICKUPS, newCount)
            .apply()
        return newCount
    }

    fun recordPhoneUnlock(nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDay = settingsPreferences.getString(KEY_UNLOCK_COUNT_DATE, "")
        val currentCount = if (savedDay == todayKey) {
            settingsPreferences.getInt(KEY_TODAY_UNLOCKS, 0)
        } else {
            0
        }
        val newCount = currentCount + 1
        settingsPreferences.edit()
            .putString(KEY_UNLOCK_COUNT_DATE, todayKey)
            .putInt(KEY_TODAY_UNLOCKS, newCount)
            .apply()
        return newCount
    }

    fun todayUnlockCount(nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDay = settingsPreferences.getString(KEY_UNLOCK_COUNT_DATE, "")
        return if (savedDay == todayKey) settingsPreferences.getInt(KEY_TODAY_UNLOCKS, 0) else 0
    }

    fun todayPickupCount(nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDay = settingsPreferences.getString(KEY_UNLOCK_COUNT_DATE, "")
        return if (savedDay == todayKey) settingsPreferences.getInt(KEY_TODAY_PICKUPS, 0) else 0
    }

    fun dailyUnlockGoal(): Int =
        settingsPreferences.getInt(KEY_DAILY_UNLOCK_GOAL, DEFAULT_DAILY_UNLOCK_GOAL)

    fun setDailyUnlockGoal(goal: Int) {
        val safeGoal = goal.coerceIn(10, 300)
        settingsPreferences.edit().putInt(KEY_DAILY_UNLOCK_GOAL, safeGoal).apply()
    }

    fun isStrictUnblockDelayEnabled(): Boolean =
        settingsPreferences.getBoolean(KEY_STRICT_UNBLOCK_DELAY_ENABLED, true)

    fun setStrictUnblockDelayEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_STRICT_UNBLOCK_DELAY_ENABLED, enabled).apply()
    }

    fun strictUnblockDelaySeconds(): Int =
        settingsPreferences.getInt(KEY_STRICT_UNBLOCK_DELAY_SECONDS, DEFAULT_STRICT_DELAY_SECONDS)

    fun setStrictUnblockDelaySeconds(seconds: Int) {
        val safeSecs = seconds.coerceIn(10, 120)
        settingsPreferences.edit().putInt(KEY_STRICT_UNBLOCK_DELAY_SECONDS, safeSecs).apply()
    }

    fun recordAppInterception(packageName: String = "", nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDay = settingsPreferences.getString(KEY_APP_INTERCEPTION_DAY, "")
        val currentCount = if (savedDay == todayKey) {
            settingsPreferences.getInt(KEY_APP_INTERCEPTIONS_COUNT, 0)
        } else {
            0
        }
        val newCount = currentCount + 1
        settingsPreferences.edit()
            .putString(KEY_APP_INTERCEPTION_DAY, todayKey)
            .putInt(KEY_APP_INTERCEPTIONS_COUNT, newCount)
            .apply()
        return newCount
    }

    fun todayAppInterceptionsCount(nowMillis: Long = System.currentTimeMillis()): Int {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(nowMillis))
        val savedDay = settingsPreferences.getString(KEY_APP_INTERCEPTION_DAY, "")
        return if (savedDay == todayKey) settingsPreferences.getInt(KEY_APP_INTERCEPTIONS_COUNT, 0) else 0
    }

    // --- Phase 2: Notification Batching & Scheduled Digest ---

    fun isNotificationBatchingEnabled(): Boolean =
        settingsPreferences.getBoolean(KEY_NOTIF_BATCHING_ENABLED, false)

    fun setNotificationBatchingEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_NOTIF_BATCHING_ENABLED, enabled).apply()
    }

    fun isNotificationBatchingFocusOnly(): Boolean =
        settingsPreferences.getBoolean(KEY_NOTIF_BATCH_FOCUS_ONLY, true)

    fun setNotificationBatchingFocusOnly(focusOnly: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_NOTIF_BATCH_FOCUS_ONLY, focusOnly).apply()
    }

    fun isDeliverDigestOnFocusEnd(): Boolean =
        settingsPreferences.getBoolean(KEY_NOTIF_DIGEST_ON_FOCUS_END, true)

    fun setDeliverDigestOnFocusEnd(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_NOTIF_DIGEST_ON_FOCUS_END, enabled).apply()
    }

    fun addBatchedNotification(
        packageName: String,
        appName: String,
        title: String,
        text: String,
        key: String,
        timestampMillis: Long = System.currentTimeMillis(),
    ): BatchedNotification {
        val id = "${System.currentTimeMillis()}_${packageName.hashCode().coerceAtLeast(0)}"
        val item = BatchedNotification(
            id = id,
            packageName = packageName,
            appName = appName,
            title = title,
            text = text,
            timestampMillis = timestampMillis,
            key = key,
        )
        synchronized(NOTIF_LOCK) {
            val list = getBatchedNotifications().toMutableList()
            list.add(0, item)
            val trimmed = if (list.size > 100) list.take(100) else list
            val arr = JSONArray()
            trimmed.forEach { n ->
                val obj = JSONObject()
                obj.put("id", n.id)
                obj.put("packageName", n.packageName)
                obj.put("appName", n.appName)
                obj.put("title", n.title)
                obj.put("text", n.text)
                obj.put("timestampMillis", n.timestampMillis)
                obj.put("key", n.key)
                arr.put(obj)
            }
            settingsPreferences.edit().putString(KEY_BATCHED_NOTIFICATIONS_JSON, arr.toString()).apply()
        }
        return item
    }

    fun getBatchedNotifications(): List<BatchedNotification> {
        val raw = settingsPreferences.getString(KEY_BATCHED_NOTIFICATIONS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<BatchedNotification>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                result.add(
                    BatchedNotification(
                        id = obj.optString("id", ""),
                        packageName = obj.optString("packageName", ""),
                        appName = obj.optString("appName", ""),
                        title = obj.optString("title", ""),
                        text = obj.optString("text", ""),
                        timestampMillis = obj.optLong("timestampMillis", 0L),
                        key = obj.optString("key", ""),
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearBatchedNotifications() {
        synchronized(NOTIF_LOCK) {
            settingsPreferences.edit().remove(KEY_BATCHED_NOTIFICATIONS_JSON).apply()
        }
    }

    fun getBatchedNotificationsCount(): Int = getBatchedNotifications().size

    // --- Phase 2: Zen Mode (Deep Exam Lockout) ---

    fun isZenModeActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val end = timerPreferences.getLong(KEY_ZEN_MODE_END_TIME, 0L)
        return end > nowMillis
    }

    fun zenModeRemainingMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val end = timerPreferences.getLong(KEY_ZEN_MODE_END_TIME, 0L)
        return (end - nowMillis).coerceAtLeast(0L)
    }

    fun startZenMode(durationMinutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        val safeMinutes = durationMinutes.coerceIn(5, 240)
        val end = nowMillis + safeMinutes * 60_000L
        timerPreferences.edit()
            .putLong(KEY_ZEN_MODE_END_TIME, end)
            .putInt(KEY_ZEN_MODE_DURATION_MINUTES, safeMinutes)
            .putLong(KEY_ZEN_SESSION_STARTED_AT, nowMillis)
            .apply()
    }

    fun cancelZenMode() {
        timerPreferences.edit()
            .remove(KEY_ZEN_MODE_END_TIME)
            .remove(KEY_ZEN_MODE_DURATION_MINUTES)
            .apply()
    }

    /**
     * Saves the current Zen Mode session as a FocusSessionRecord so it appears in Focus Analysis.
     * Safe to call on complete or early exit — ignores sessions shorter than 30 seconds.
     */
    fun saveZenModeAsSession(nowMillis: Long = System.currentTimeMillis()) {
        val startedAt = timerPreferences.getLong(KEY_ZEN_SESSION_STARTED_AT, 0L)
        if (startedAt == 0L) return
        val endTime = timerPreferences.getLong(KEY_ZEN_MODE_END_TIME, 0L)
        val plannedDuration = timerPreferences.getInt(KEY_ZEN_MODE_DURATION_MINUTES, 0)
        val elapsed = ((nowMillis - startedAt) / 1000L).coerceAtLeast(0L)
        val completed = endTime > 0 && nowMillis >= endTime
        if (elapsed < 30) {
            timerPreferences.edit().remove(KEY_ZEN_SESSION_STARTED_AT).apply()
            return
        }
        saveFocusSession(
            FocusSessionRecord(
                id = 0,
                startedAtMillis = startedAt,
                elapsedSeconds = elapsed,
                targetMinutes = plannedDuration,
                goal = "Zen Mode Study",
                completed = completed,
                finishedAtMillis = nowMillis,
                mode = "Zen",
            )
        )
        timerPreferences.edit().remove(KEY_ZEN_SESSION_STARTED_AT).apply()
    }

    fun activeTimer(): ActiveTimer? {
        if (!timerPreferences.contains(KEY_TIMER_STARTED_AT)) return null
        return ActiveTimer(
            startedAtMillis = timerPreferences.getLong(KEY_TIMER_STARTED_AT, 0),
            segmentStartedAtMillis = timerPreferences.getLong(KEY_SEGMENT_STARTED_AT, 0),
            segmentTargetMillis = timerPreferences.getLong(KEY_SEGMENT_TARGET, 0),
            savedRemainingMillis = timerPreferences.getLong(KEY_SAVED_REMAINING, 0),
            elapsedBeforeSegmentMillis = timerPreferences.getLong(KEY_ELAPSED_BEFORE_SEGMENT, 0),
            targetMinutes = timerPreferences.getInt(KEY_TARGET_MINUTES, DEFAULT_FOCUS_MINUTES),
            goal = timerPreferences.getString(KEY_GOAL, "").orEmpty(),
            running = timerPreferences.getBoolean(KEY_RUNNING, false),
        )
    }

    fun startTimer(targetMinutes: Int, goal: String, nowMillis: Long = System.currentTimeMillis()) {
        require(targetMinutes in MIN_FOCUS_MINUTES..MAX_FOCUS_MINUTES)
        val durationMillis = targetMinutes * MILLIS_PER_MINUTE
        saveTimer(
            ActiveTimer(
                startedAtMillis = nowMillis,
                segmentStartedAtMillis = nowMillis,
                segmentTargetMillis = durationMillis,
                savedRemainingMillis = durationMillis,
                elapsedBeforeSegmentMillis = 0,
                targetMinutes = targetMinutes,
                goal = goal.trim(),
                running = true,
            ),
        )
    }

    fun focusTag(): String =
        timerPreferences.getString(KEY_FOCUS_TAG, "Untagged") ?: "Untagged"

    fun setFocusTag(tag: String) {
        timerPreferences.edit().putString(KEY_FOCUS_TAG, tag.trim().ifBlank { "Untagged" }).apply()
    }

    fun focusTheme(): String =
        timerPreferences.getString(KEY_FOCUS_THEME, "Aurora") ?: "Aurora"

    fun setFocusTheme(theme: String) {
        timerPreferences.edit().putString(KEY_FOCUS_THEME, theme.trim().ifBlank { "Aurora" }).apply()
    }

    fun focusMusic(): String =
        timerPreferences.getString(KEY_FOCUS_MUSIC, "Off") ?: "Off"

    fun setFocusMusic(music: String) {
        timerPreferences.edit().putString(KEY_FOCUS_MUSIC, music.trim().ifBlank { "Off" }).apply()
    }

    fun isLauncherBlocked(): Boolean =
        timerPreferences.getBoolean(KEY_IS_LAUNCHER_BLOCKED, false)

    fun setLauncherBlocked(blocked: Boolean) {
        timerPreferences.edit().putBoolean(KEY_IS_LAUNCHER_BLOCKED, blocked).apply()
    }

    fun isUltraFocus(): Boolean =
        timerPreferences.getBoolean(KEY_IS_ULTRA_FOCUS, false)

    fun setUltraFocus(active: Boolean) {
        timerPreferences.edit().putBoolean(KEY_IS_ULTRA_FOCUS, active).apply()
    }

    fun isBlockShortsReelsEnabled(): Boolean =
        blockerPreferences.getBoolean(KEY_BLOCK_SHORTS_REELS_ENABLED, true)

    fun setBlockShortsReelsEnabled(enabled: Boolean) {
        blockerPreferences.edit().putBoolean(KEY_BLOCK_SHORTS_REELS_ENABLED, enabled).apply()
    }

    fun isYouTubeShortsBlocked(): Boolean =
        blockerPreferences.getBoolean(KEY_BLOCK_YT_SHORTS, true)

    fun setYouTubeShortsBlocked(blocked: Boolean) {
        blockerPreferences.edit().putBoolean(KEY_BLOCK_YT_SHORTS, blocked).apply()
    }

    fun isInstagramReelsBlocked(): Boolean =
        blockerPreferences.getBoolean(KEY_BLOCK_IG_REELS, true)

    fun setInstagramReelsBlocked(blocked: Boolean) {
        blockerPreferences.edit().putBoolean(KEY_BLOCK_IG_REELS, blocked).apply()
    }

    fun isSnapchatSpotlightBlocked(): Boolean =
        blockerPreferences.getBoolean(KEY_BLOCK_SNAP_SPOTLIGHT, true)

    fun setSnapchatSpotlightBlocked(blocked: Boolean) {
        blockerPreferences.edit().putBoolean(KEY_BLOCK_SNAP_SPOTLIGHT, blocked).apply()
    }

    fun isFacebookReelsBlocked(): Boolean =
        blockerPreferences.getBoolean(KEY_BLOCK_FB_REELS, true)

    fun setFacebookReelsBlocked(blocked: Boolean) {
        blockerPreferences.edit().putBoolean(KEY_BLOCK_FB_REELS, blocked).apply()
    }

    fun todayShortsBlockedCount(): Int {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedDay = blockerPreferences.getString(KEY_SHORTS_BLOCKED_DAY, "")
        return if (savedDay == today) blockerPreferences.getInt(KEY_SHORTS_BLOCKED_COUNT, 0) else 0
    }

    fun incrementShortsBlockedCount() {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedDay = blockerPreferences.getString(KEY_SHORTS_BLOCKED_DAY, "")
        val currentCount = if (savedDay == today) blockerPreferences.getInt(KEY_SHORTS_BLOCKED_COUNT, 0) else 0
        blockerPreferences.edit()
            .putString(KEY_SHORTS_BLOCKED_DAY, today)
            .putInt(KEY_SHORTS_BLOCKED_COUNT, currentCount + 1)
            .apply()
    }

    fun isFloatingScrollCounterEnabled(): Boolean =
        blockerPreferences.getBoolean(KEY_FLOATING_SCROLL_COUNTER_ENABLED, true)

    fun setFloatingScrollCounterEnabled(enabled: Boolean) {
        blockerPreferences.edit().putBoolean(KEY_FLOATING_SCROLL_COUNTER_ENABLED, enabled).apply()
    }

    fun getShortsScrollLimit(): Int =
        blockerPreferences.getInt(KEY_SHORTS_SCROLL_LIMIT, 0)

    fun setShortsScrollLimit(limit: Int) {
        blockerPreferences.edit().putInt(KEY_SHORTS_SCROLL_LIMIT, limit).apply()
    }

    fun todayReelsScrolledCount(): Int {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedDay = blockerPreferences.getString(KEY_REELS_SCROLLED_DAY, "")
        return if (savedDay == today) blockerPreferences.getInt(KEY_REELS_SCROLLED_COUNT, 0) else 0
    }

    fun incrementTodayReelsScrolledCount() {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedDay = blockerPreferences.getString(KEY_REELS_SCROLLED_DAY, "")
        val currentCount = if (savedDay == today) blockerPreferences.getInt(KEY_REELS_SCROLLED_COUNT, 0) else 0
        blockerPreferences.edit()
            .putString(KEY_REELS_SCROLLED_DAY, today)
            .putInt(KEY_REELS_SCROLLED_COUNT, currentCount + 1)
            .apply()
    }

    fun todayReelsWatchTimeSeconds(): Long {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedDay = blockerPreferences.getString(KEY_REELS_WATCH_TIME_DAY, "")
        return if (savedDay == today) blockerPreferences.getLong(KEY_REELS_WATCH_TIME_SECONDS, 0L) else 0L
    }

    fun addTodayReelsWatchTimeSeconds(seconds: Long) {
        if (seconds <= 0L) return
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedDay = blockerPreferences.getString(KEY_REELS_WATCH_TIME_DAY, "")
        val current = if (savedDay == today) blockerPreferences.getLong(KEY_REELS_WATCH_TIME_SECONDS, 0L) else 0L
        blockerPreferences.edit()
            .putString(KEY_REELS_WATCH_TIME_DAY, today)
            .putLong(KEY_REELS_WATCH_TIME_SECONDS, current + seconds)
            .apply()
    }

    fun resetTodayReelsIfNewDay(): Boolean {
        val today = Calendar.getInstance().let { "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}" }
        val savedCountDay = blockerPreferences.getString(KEY_REELS_SCROLLED_DAY, "")
        val savedTimeDay = blockerPreferences.getString(KEY_REELS_WATCH_TIME_DAY, "")
        if (savedCountDay != today || savedTimeDay != today) {
            blockerPreferences.edit()
                .putString(KEY_REELS_SCROLLED_DAY, today)
                .putInt(KEY_REELS_SCROLLED_COUNT, 0)
                .putString(KEY_REELS_WATCH_TIME_DAY, today)
                .putLong(KEY_REELS_WATCH_TIME_SECONDS, 0L)
                .apply()
            return true
        }
        return false
    }

    fun pauseTimer(timer: ActiveTimer, nowMillis: Long = System.currentTimeMillis()) {
        val remaining = timer.remainingAt(nowMillis)
        val elapsed = timer.elapsedAt(nowMillis)
        saveTimer(
            timer.copy(
                segmentTargetMillis = remaining,
                savedRemainingMillis = remaining,
                elapsedBeforeSegmentMillis = elapsed,
                running = false,
            ),
        )
    }

    fun resumeTimer(timer: ActiveTimer, nowMillis: Long = System.currentTimeMillis()) {
        saveTimer(
            timer.copy(
                segmentStartedAtMillis = nowMillis,
                segmentTargetMillis = timer.savedRemainingMillis,
                running = true,
            ),
        )
    }

    fun finishTimer(
        completed: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = synchronized(TIMER_LOCK) {
        val timer = activeTimer() ?: return@synchronized false
        finishTimerLocked(timer, completed, nowMillis)
    }

    fun finishTimerIfDue(nowMillis: Long = System.currentTimeMillis()): Boolean =
        synchronized(TIMER_LOCK) {
            val timer = activeTimer() ?: return@synchronized false
            if (!timer.running || timer.remainingAt(nowMillis) > 0) return@synchronized false
            finishTimerLocked(timer, completed = true, nowMillis = nowMillis)
        }

    private fun finishTimerLocked(
        timer: ActiveTimer,
        completed: Boolean,
        nowMillis: Long,
    ): Boolean {
        val elapsedSeconds = timer.elapsedAt(nowMillis) / 1_000
        if (elapsedSeconds > 0) {
            val values = ContentValues().apply {
                put("started_at", timer.startedAtMillis)
                put("finished_at", nowMillis)
                put("elapsed_seconds", elapsedSeconds)
                put("target_minutes", timer.targetMinutes)
                put("goal", timer.goal)
                put("completed", if (completed) 1 else 0)
            }
            database.writableDatabase.insertOrThrow("focus_sessions", null, values)
            if (completed) synchronizeFocusAchievements()
        }
        timerPreferences.edit().clear().apply()
        return true
    }

    private fun saveTimer(timer: ActiveTimer) {
        timerPreferences.edit()
            .putLong(KEY_TIMER_STARTED_AT, timer.startedAtMillis)
            .putLong(KEY_SEGMENT_STARTED_AT, timer.segmentStartedAtMillis)
            .putLong(KEY_SEGMENT_TARGET, timer.segmentTargetMillis)
            .putLong(KEY_SAVED_REMAINING, timer.savedRemainingMillis)
            .putLong(KEY_ELAPSED_BEFORE_SEGMENT, timer.elapsedBeforeSegmentMillis)
            .putInt(KEY_TARGET_MINUTES, timer.targetMinutes)
            .putString(KEY_GOAL, timer.goal)
            .putBoolean(KEY_RUNNING, timer.running)
            .apply()
    }

    private class StudyDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE study_goal (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    daily_minutes INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE focus_sessions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    started_at INTEGER NOT NULL,
                    elapsed_seconds INTEGER NOT NULL,
                    target_minutes INTEGER NOT NULL,
                    goal TEXT NOT NULL,
                    completed INTEGER NOT NULL,
                    finished_at INTEGER NOT NULL,
                    subject TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT '',
                    mode TEXT NOT NULL DEFAULT 'Focus',
                    break_seconds INTEGER NOT NULL DEFAULT 0,
                    timeline_events TEXT NOT NULL DEFAULT '',
                    apps_used TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE study_schedules (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    day_of_week INTEGER NOT NULL,
                    start_minute INTEGER NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    weekdays TEXT NOT NULL DEFAULT '',
                    end_minute INTEGER NOT NULL DEFAULT -1,
                    reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    breaks_enabled INTEGER NOT NULL DEFAULT 1,
                    focus_interval_minutes INTEGER NOT NULL DEFAULT 25,
                    short_break_minutes INTEGER NOT NULL DEFAULT 5,
                    long_break_minutes INTEGER NOT NULL DEFAULT 15,
                    intervals_before_long_break INTEGER NOT NULL DEFAULT 4,
                    blocked_packages TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            createBlockedAppsTable(db)
            createAppDailyLimitsTable(db)
            createBlockedDomainsTable(db)
            createFocusAchievementsTable(db)
            createSleepTables(db)
            createAppCategoriesTable(db)
            createWindDownTables(db)
            createSleepPreferencesTable(db)
            db.execSQL(
                "INSERT INTO study_goal (id, daily_minutes) VALUES (1, $DEFAULT_DAILY_GOAL_MINUTES)",
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) createBlockedAppsTable(db)
            if (oldVersion < 3) createAppDailyLimitsTable(db)
            if (oldVersion < 4) createBlockedDomainsTable(db)
            if (oldVersion < 5) createFocusAchievementsTable(db)
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE focus_sessions ADD COLUMN finished_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE focus_sessions SET finished_at = started_at + elapsed_seconds * 1000")
            }
            if (oldVersion < 7) {
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN weekdays TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN end_minute INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN reminder_enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN breaks_enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN focus_interval_minutes INTEGER NOT NULL DEFAULT 25")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN short_break_minutes INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN long_break_minutes INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN intervals_before_long_break INTEGER NOT NULL DEFAULT 4")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN blocked_packages TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE study_schedules ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE study_schedules
                    SET weekdays = CAST(day_of_week AS TEXT),
                        end_minute = (start_minute + duration_minutes) % 1440
                    """.trimIndent(),
                )
            }
            if (oldVersion < 8) {
                db.execSQL("ALTER TABLE app_daily_limits ADD COLUMN warning_percent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_daily_limits ADD COLUMN blocking_enabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE blocked_domains ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
            }
            if (oldVersion < 9) {
                db.execSQL("ALTER TABLE blocked_domains ADD COLUMN active_days TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE blocked_domains ADD COLUMN start_minute INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE blocked_domains ADD COLUMN end_minute INTEGER NOT NULL DEFAULT -1")
            }
            if (oldVersion < 10) {
                createSleepTables(db)
            createAppCategoriesTable(db)
            createWindDownTables(db)
            }
                        if (oldVersion < 13) {
                createWindDownTables(db)
            }
            if (oldVersion < 12) {
                upgradeSleepScheduleTableV12(db)
            }
            if (oldVersion < 14) {
                createSleepTables(db)
                createSleepPreferencesTable(db)
                createWindDownTables(db)
            }
            if (oldVersion < 15) {
                ensureFocusSessionsColumns(db)
            }
        }

        override fun onOpen(db: SQLiteDatabase) {
            super.onOpen(db)
            createBlockedAppsTable(db)
            createAppDailyLimitsTable(db)
            createBlockedDomainsTable(db)
            createSleepTables(db)
            createSleepPreferencesTable(db)
            createWindDownTables(db)
            ensureFocusSessionsColumns(db)
        }

        private fun ensureFocusSessionsColumns(db: SQLiteDatabase) {
            try {
                val existing = mutableSetOf<String>()
                db.rawQuery("PRAGMA table_info(focus_sessions)", null).use { c ->
                    val nameIdx = c.getColumnIndex("name")
                    while (c.moveToNext()) {
                        existing.add(c.getString(nameIdx))
                    }
                }
                if ("subject" !in existing) db.execSQL("ALTER TABLE focus_sessions ADD COLUMN subject TEXT NOT NULL DEFAULT ''")
                if ("notes" !in existing) db.execSQL("ALTER TABLE focus_sessions ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
                if ("mode" !in existing) db.execSQL("ALTER TABLE focus_sessions ADD COLUMN mode TEXT NOT NULL DEFAULT 'Focus'")
                if ("break_seconds" !in existing) db.execSQL("ALTER TABLE focus_sessions ADD COLUMN break_seconds INTEGER NOT NULL DEFAULT 0")
                if ("timeline_events" !in existing) db.execSQL("ALTER TABLE focus_sessions ADD COLUMN timeline_events TEXT NOT NULL DEFAULT ''")
                if ("apps_used" !in existing) db.execSQL("ALTER TABLE focus_sessions ADD COLUMN apps_used TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}
        }

        fun createBlockedAppsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS blocked_apps (
                    package_name TEXT PRIMARY KEY,
                    label TEXT NOT NULL,
                    blocked INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        fun createAppDailyLimitsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_daily_limits (
                    package_name TEXT PRIMARY KEY,
                    label TEXT NOT NULL,
                    daily_minutes INTEGER NOT NULL,
                    warning_percent INTEGER NOT NULL DEFAULT 0,
                    blocking_enabled INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
        }

        fun createBlockedDomainsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS blocked_domains (
                    domain TEXT PRIMARY KEY,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    active_days TEXT NOT NULL DEFAULT '',
                    start_minute INTEGER NOT NULL DEFAULT -1,
                    end_minute INTEGER NOT NULL DEFAULT -1
                )
                """.trimIndent(),
            )
        }

                        fun createSleepTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sleep_schedule (
                    id INTEGER PRIMARY KEY CHECK (id IN (1, 2)),
                    enabled INTEGER NOT NULL DEFAULT 1,
                    bedtime_minute INTEGER NOT NULL DEFAULT 1380,
                    wakeup_minute INTEGER NOT NULL DEFAULT 410,
                    active_days TEXT NOT NULL DEFAULT '2,3,4,5,6',
                    target_duration_minutes INTEGER NOT NULL DEFAULT 480,
                    reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    reminder_advance_minutes INTEGER NOT NULL DEFAULT 30,
                    wakeup_reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    protection_enabled INTEGER NOT NULL DEFAULT 0,
                    protection_blocked_packages TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """ .trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO sleep_schedule (
                    id, enabled, bedtime_minute, wakeup_minute, active_days,
                    target_duration_minutes, reminder_enabled, reminder_advance_minutes,
                    wakeup_reminder_enabled, protection_enabled, protection_blocked_packages, created_at, updated_at
                ) VALUES (1, 1, 1380, 410, '2,3,4,5,6', 480, 1, 30, 1, 0, '', 0, 0)
                """ .trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO sleep_schedule (
                    id, enabled, bedtime_minute, wakeup_minute, active_days,
                    target_duration_minutes, reminder_enabled, reminder_advance_minutes,
                    wakeup_reminder_enabled, protection_enabled, protection_blocked_packages, created_at, updated_at
                ) VALUES (2, 1, 1410, 480, '1,7', 510, 1, 30, 1, 0, '', 0, 0)
                """ .trimIndent(),
            )
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS sleep_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date_millis INTEGER NOT NULL,
                    bedtime_minute INTEGER NOT NULL,
                    wakeup_minute INTEGER NOT NULL,
                    reported_bedtime_millis INTEGER NOT NULL,
                    reported_wakeup_millis INTEGER NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    quality_rating INTEGER NOT NULL DEFAULT 0,
                    note TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }

        private fun createFocusAchievementsTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE focus_achievements (
                    hours INTEGER PRIMARY KEY,
                    unlocked_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

                        private fun upgradeSleepScheduleTableV12(db: SQLiteDatabase) {
            createSleepTables(db)
            try {
                db.execSQL("ALTER TABLE sleep_schedule ADD COLUMN wakeup_reminder_enabled INTEGER NOT NULL DEFAULT 1")
            } catch (_: Exception) {}
        }


        private fun createWindDownTables(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS winddown_audio_tracks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    uri TEXT NOT NULL,
                    title TEXT NOT NULL,
                    artist TEXT NOT NULL DEFAULT '',
                    category TEXT NOT NULL DEFAULT 'Music',
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    file_size_bytes INTEGER NOT NULL DEFAULT 0,
                    is_favourite INTEGER NOT NULL DEFAULT 0,
                    added_at INTEGER NOT NULL DEFAULT 0,
                    last_played_at INTEGER NOT NULL DEFAULT 0,
                    playback_position_ms INTEGER NOT NULL DEFAULT 0,
                    album_art_uri TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS winddown_audio_settings (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    continue_in_background INTEGER NOT NULL DEFAULT 1,
                    show_media_controls INTEGER NOT NULL DEFAULT 1,
                    fade_out_at_end INTEGER NOT NULL DEFAULT 1,
                    default_sleep_timer_minutes INTEGER NOT NULL DEFAULT 30,
                    resume_from_last_position INTEGER NOT NULL DEFAULT 1,
                    pause_on_incoming_calls INTEGER NOT NULL DEFAULT 1,
                    allow_notification_controls INTEGER NOT NULL DEFAULT 1,
                    mix_relaxing_sound INTEGER NOT NULL DEFAULT 0,
                    selected_relaxing_sound TEXT NOT NULL DEFAULT 'Rain',
                    relaxing_sound_volume REAL NOT NULL DEFAULT 0.5,
                    audio_volume REAL NOT NULL DEFAULT 0.85
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS winddown_session_config (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    duration_minutes INTEGER NOT NULL DEFAULT 30,
                    block_apps_enabled INTEGER NOT NULL DEFAULT 1,
                    screen_dimming_enabled INTEGER NOT NULL DEFAULT 0,
                    do_not_disturb_enabled INTEGER NOT NULL DEFAULT 0,
                    selected_audio_track_id INTEGER,
                    selected_relaxing_sound TEXT
                )
                """.trimIndent()
            )

            // Seed default settings
            db.execSQL(
                """
                INSERT OR IGNORE INTO winddown_audio_settings (
                    id, continue_in_background, show_media_controls, fade_out_at_end,
                    default_sleep_timer_minutes, resume_from_last_position, pause_on_incoming_calls,
                    allow_notification_controls, mix_relaxing_sound, selected_relaxing_sound,
                    relaxing_sound_volume, audio_volume
                ) VALUES (1, 1, 1, 1, 30, 1, 1, 1, 0, 'Rain', 0.5, 0.85)
                """.trimIndent()
            )

            db.execSQL(
                """
                INSERT OR IGNORE INTO winddown_session_config (
                    id, duration_minutes, block_apps_enabled, screen_dimming_enabled,
                    do_not_disturb_enabled, selected_audio_track_id, selected_relaxing_sound
                ) VALUES (1, 30, 1, 0, 0, 1, 'Rain')
                """.trimIndent()
            )

            // Seed sample tracks matching the mockup if table is empty
            seedDefaultWindDownTracks(db)
        }

        private fun seedDefaultWindDownTracks(db: SQLiteDatabase) {
            val cursor = db.rawQuery("SELECT COUNT(*) FROM winddown_audio_tracks", null)
            val count = cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
            if (count > 0) return

            val now = System.currentTimeMillis()
            val sampleTracks = listOf(
                Triple("Chill Study Mix", "Chillhop Music", "Music") to (42 * 60 * 1000L),
                Triple("The Daily", "The New York Times", "Podcasts") to (28 * 60 * 1000L),
                Triple("Focus Music", "StudyMode Ambient", "Music") to (75 * 60 * 1000L),
                Triple("Sleep Stories", "Drift to Sleep", "Podcasts") to (35 * 60 * 1000L),
                Triple("Nature Sounds", "Earth Acoustics", "Music") to (30 * 60 * 1000L),
                Triple("Motivational Talks", "Inspire Audio", "Podcasts") to (45 * 60 * 1000L),
                Triple("My Favourites", "Personal Mix", "Music") to (24 * 60 * 1000L),
                Triple("Calm Nights.mp3", "Ambient Rain", "Music") to (5 * 60 * 1000L),
                Triple("Sleep Podcast Ep.1.m4a", "Nightly Wisdom", "Podcasts") to (18 * 60 * 1000L),
                Triple("Forest Ambience.mp3", "Deep Wilderness", "Audiobooks") to (60 * 60 * 1000L)
            )

            sampleTracks.forEachIndexed { index, (info, dur) ->
                val (title, artist, category) = info
                val isFav = if (title in listOf("Chill Study Mix", "My Favourites")) 1 else 0
                val sizeBytes = (dur / 1000L) * 128 * 1024 / 8 // ~128kbps approx
                db.execSQL(
                    """
                    INSERT INTO winddown_audio_tracks (
                        uri, title, artist, category, duration_ms, file_size_bytes,
                        is_favourite, added_at, last_played_at, playback_position_ms, album_art_uri
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )
                    """.trimIndent(),
                    arrayOf(
                        "sample://winddown/$title",
                        title,
                        artist,
                        category,
                        dur,
                        sizeBytes,
                        isFav,
                        now - (index * 3600000L),
                        if (index == 0) now - 1800000L else 0L,
                        if (index == 0) (12 * 60 + 34) * 1000L else 0L,
                        ""
                    )
                )
            }
        }

        private fun createAppCategoriesTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_categories (
                    package_name TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }

        
        private fun createSleepPreferencesTable(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sleep_preferences (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    wind_down_duration_minutes INTEGER NOT NULL DEFAULT 30,
                    auto_start_wind_down INTEGER NOT NULL DEFAULT 0,
                    app_blocking_during_wind_down INTEGER NOT NULL DEFAULT 1,
                    screen_dimming_wind_down INTEGER NOT NULL DEFAULT 1,
                    do_not_disturb_wind_down INTEGER NOT NULL DEFAULT 1,
                    show_wind_down_reminder INTEGER NOT NULL DEFAULT 1,
                    wind_down_reminder_advance_minutes INTEGER NOT NULL DEFAULT 10,
                    remember_last_played_sound INTEGER NOT NULL DEFAULT 1,
                    sound_default_volume REAL NOT NULL DEFAULT 0.70,
                    sound_fade_out_at_end INTEGER NOT NULL DEFAULT 1,
                    mix_with_my_audio INTEGER NOT NULL DEFAULT 1,
                    sound_mix_volume REAL NOT NULL DEFAULT 0.40,
                    play_through_speaker INTEGER NOT NULL DEFAULT 1,
                    track_sleep_automatically INTEGER NOT NULL DEFAULT 1,
                    sleep_goal_minutes INTEGER NOT NULL DEFAULT 450,
                    bedtime_reminder_enabled INTEGER NOT NULL DEFAULT 1,
                    bedtime_reminder_advance_minutes INTEGER NOT NULL DEFAULT 30,
                    wakeup_alarm_enabled INTEGER NOT NULL DEFAULT 1,
                    alarm_sound TEXT NOT NULL DEFAULT 'Gentle Tone',
                    morning_notification_enabled INTEGER NOT NULL DEFAULT 1,
                    enable_night_mode INTEGER NOT NULL DEFAULT 1,
                    night_mode_schedule INTEGER NOT NULL DEFAULT 1,
                    reduce_blue_light INTEGER NOT NULL DEFAULT 1,
                    screen_dimming_during_sleep INTEGER NOT NULL DEFAULT 1,
                    keep_screen_off INTEGER NOT NULL DEFAULT 1,
                    lock_device_during_sleep INTEGER NOT NULL DEFAULT 0,
                    notify_bedtime INTEGER NOT NULL DEFAULT 1,
                    notify_wind_down INTEGER NOT NULL DEFAULT 1,
                    notify_wakeup INTEGER NOT NULL DEFAULT 1,
                    notify_sleep_summary INTEGER NOT NULL DEFAULT 1,
                    notify_tips_insights INTEGER NOT NULL DEFAULT 1,
                    notify_marketing INTEGER NOT NULL DEFAULT 0,
                    store_locally INTEGER NOT NULL DEFAULT 1,
                    backup_to_cloud INTEGER NOT NULL DEFAULT 0,
                    smart_bedtime_suggestions INTEGER NOT NULL DEFAULT 1,
                    adjust_for_weekends INTEGER NOT NULL DEFAULT 0,
                    minimum_sleep_goal_hours REAL NOT NULL DEFAULT 7.0,
                    vibration_on_wakeup INTEGER NOT NULL DEFAULT 1,
                    adaptive_reminders INTEGER NOT NULL DEFAULT 1,
                    beta_features_enabled INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO sleep_preferences (id) VALUES (1)
                """.trimIndent()
            )
        }

        companion object {
            private const val DATABASE_NAME = "studymode.db"
            private const val DATABASE_VERSION = 15
        }

    }

        fun getCustomAppCategory(packageName: String): AppCategory? {
        return try {
            val cursor = database.readableDatabase.query(
                "app_categories",
                arrayOf("category"),
                "package_name = ?",
                arrayOf(packageName),
                null, null, null,
            )
            cursor.use {
                if (it.moveToFirst()) {
                    AppCategory.fromId(it.getString(0))
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun allCustomAppCategories(): Map<String, AppCategory> {
        val map = mutableMapOf<String, AppCategory>()
        try {
            val cursor = database.readableDatabase.query(
                "app_categories",
                arrayOf("package_name", "category"),
                null, null, null, null, null,
            )
            cursor.use {
                while (it.moveToNext()) {
                    map[it.getString(0)] = AppCategory.fromId(it.getString(1))
                }
            }
        } catch (_: Exception) {}
        return map
    }

    fun setAppCategory(packageName: String, category: AppCategory) {
        try {
            val db = database.writableDatabase
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_categories (
                    package_name TEXT PRIMARY KEY,
                    category TEXT NOT NULL,
                    updated_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            val values = ContentValues().apply {
                put("package_name", packageName)
                put("category", category.id)
                put("updated_at", System.currentTimeMillis())
            }
            db.insertWithOnConflict(
                "app_categories",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            blockerPreferences.edit().remove(KEY_BLOCKING_PAUSED_UNTIL).apply()
        } catch (_: Exception) {}
    }

    fun resolveAppCategory(packageName: String): AppCategory {
        val custom = getCustomAppCategory(packageName)
        if (custom != null) return custom

        if (isAppBlocked(packageName) || appDailyLimit(packageName) != null) {
            return AppCategory.DISTRACTING
        }

        val pkg = packageName.lowercase(Locale.ROOT)
        if (pkg.contains("reddit") || pkg.contains("youtube") || pkg.contains("netflix") ||
            pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("twitter") ||
            pkg.contains("tiktok") || pkg.contains("musically") || pkg.contains("snapchat") ||
            pkg.contains("spotify") || pkg.contains("chess") || pkg.contains("game") ||
            pkg.contains("primevideo") || pkg.contains("hotstar") || pkg.contains("twitch") ||
            pkg.contains("disney") || pkg.contains("roblox") || pkg.contains("pubg") ||
            pkg.contains("freefire") || pkg.contains("candycrush")
        ) {
            return AppCategory.DISTRACTING
        }

        if (pkg.contains("studymode") || pkg.contains("duolingo") || pkg.contains("anki") ||
            pkg.contains("notion") || pkg.contains("keep") || pkg.contains("docs") ||
            pkg.contains("sheets") || pkg.contains("slides") || pkg.contains("word") ||
            pkg.contains("excel") || pkg.contains("powerpoint") || pkg.contains("strava") ||
            pkg.contains("todoist") || pkg.contains("calculator") || pkg.contains("calendar") ||
            pkg.contains("udemy") || pkg.contains("coursera") || pkg.contains("khan") ||
            pkg.contains("sololearn") || pkg.contains("quizlet") || pkg.contains("forest")
        ) {
            return AppCategory.PRODUCTIVE
        }

        return AppCategory.OTHERS
    }

        fun currentSleepScheduleId(): Long {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) 2L else 1L
    }

    fun weekdaySleepSchedule(): SleepSchedule = sleepSchedule(1L)
    fun weekendSleepSchedule(): SleepSchedule = sleepSchedule(2L)

    fun sleepSchedule(id: Long): SleepSchedule {
        try {
            val cursor = database.readableDatabase.rawQuery(
                """
                SELECT enabled, bedtime_minute, wakeup_minute, active_days,
                       target_duration_minutes, reminder_enabled, reminder_advance_minutes,
                       protection_enabled, protection_blocked_packages, created_at, updated_at,
                       wakeup_reminder_enabled
                FROM sleep_schedule WHERE id = ?
                """ .trimIndent(),
                arrayOf(id.toString()),
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val enabled = it.getInt(0) == 1
                    val bedtime = it.getInt(1)
                    val wakeup = it.getInt(2)
                    val defaultDays = if (id == 2L) {
                        setOf(Calendar.SATURDAY, Calendar.SUNDAY)
                    } else {
                        setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
                    }
                    val activeDays = it.getString(3).orEmpty()
                        .split(',')
                        .mapNotNull(String::toIntOrNull)
                        .filter { d -> d in Calendar.SUNDAY..Calendar.SATURDAY }
                        .toSet()
                        .ifEmpty { defaultDays }
                    val targetDuration = it.getInt(4)
                    val reminderEnabled = it.getInt(5) == 1
                    val reminderAdvance = it.getInt(6)
                    val protectionEnabled = it.getInt(7) == 1
                    val blocked = it.getString(8).orEmpty()
                        .split(',')
                        .filter(String::isNotBlank)
                        .toSet()
                    val createdAt = it.getLong(9)
                    val updatedAt = it.getLong(10)
                    val wakeupReminder = if (it.columnCount > 11 && !it.isNull(11)) it.getInt(11) == 1 else true
                    return SleepSchedule(
                        id = id,
                        enabled = enabled,
                        bedtimeMinute = bedtime,
                        wakeupMinute = wakeup,
                        activeDays = activeDays,
                        targetDurationMinutes = targetDuration,
                        reminderEnabled = reminderEnabled,
                        reminderAdvanceMinutes = reminderAdvance,
                        wakeupReminderEnabled = wakeupReminder,
                        protectionEnabled = protectionEnabled,
                        protectionBlockedPackages = blocked,
                        createdAtMillis = createdAt,
                        updatedAtMillis = updatedAt,
                    )
                }
            }
        } catch (_: Exception) {
            // Self-heal table if missing
            try {
                database.createSleepTables(database.writableDatabase)
            } catch (_: Exception) {}
        }
        return if (id == 2L) {
            SleepSchedule(
                id = 2L,
                bedtimeMinute = 23 * 60 + 30,
                wakeupMinute = 8 * 60,
                activeDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY),
                targetDurationMinutes = 8 * 60 + 30,
                reminderAdvanceMinutes = 30,
                wakeupReminderEnabled = true,
            )
        } else {
            SleepSchedule(
                id = 1L,
                bedtimeMinute = 23 * 60,
                wakeupMinute = 6 * 60 + 50,
                activeDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
                targetDurationMinutes = 8 * 60,
                reminderAdvanceMinutes = 30,
                wakeupReminderEnabled = true,
            )
        }
    }

    fun sleepSchedule(): SleepSchedule = sleepSchedule(currentSleepScheduleId())

    fun saveSleepSchedule(schedule: SleepSchedule) {
        val targetId = if (schedule.id in listOf(1L, 2L)) schedule.id else (if (schedule.isWeekend) 2L else 1L)
        val values = ContentValues().apply {
            put("id", targetId)
            put("enabled", if (schedule.enabled) 1 else 0)
            put("bedtime_minute", schedule.bedtimeMinute)
            put("wakeup_minute", schedule.wakeupMinute)
            put("active_days", schedule.activeDays.sorted().joinToString(","))
            put("target_duration_minutes", schedule.targetDurationMinutes)
            put("reminder_enabled", if (schedule.reminderEnabled) 1 else 0)
            put("reminder_advance_minutes", schedule.reminderAdvanceMinutes)
            put("wakeup_reminder_enabled", if (schedule.wakeupReminderEnabled) 1 else 0)
            put("protection_enabled", if (schedule.protectionEnabled) 1 else 0)
            put("protection_blocked_packages", schedule.protectionBlockedPackages.joinToString(","))
            put("updated_at", System.currentTimeMillis())
        }
        try {
            database.writableDatabase.insertWithOnConflict(
                "sleep_schedule",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        } catch (_: Exception) {
            try {
                database.createSleepTables(database.writableDatabase)
                database.writableDatabase.insertWithOnConflict(
                    "sleep_schedule",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            } catch (_: Exception) {}
        }
    }

fun sleepLogs(limit: Int = 30): List<SleepLog> {
        val list = mutableListOf<SleepLog>()
        try {
            val cursor = database.readableDatabase.rawQuery(
                """
                SELECT id, date_millis, bedtime_minute, wakeup_minute,
                       reported_bedtime_millis, reported_wakeup_millis,
                       duration_minutes, quality_rating, note, created_at, updated_at
                FROM sleep_logs
                ORDER BY date_millis DESC
                LIMIT ?
                """.trimIndent(),
                arrayOf(limit.toString()),
            )
            cursor.use {
                while (it.moveToNext()) {
                    list.add(it.toSleepLog())
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun sleepLogForDate(dateMillis: Long): SleepLog? {
        try {
            val dayStart = normalizeToDayStart(dateMillis)
            val cursor = database.readableDatabase.rawQuery(
                """
                SELECT id, date_millis, bedtime_minute, wakeup_minute,
                       reported_bedtime_millis, reported_wakeup_millis,
                       duration_minutes, quality_rating, note, created_at, updated_at
                FROM sleep_logs
                WHERE date_millis = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(dayStart.toString()),
            )
            cursor.use {
                if (it.moveToFirst()) {
                    return it.toSleepLog()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun todaySleepLog(nowMillis: Long = System.currentTimeMillis()): SleepLog? {
        val today = normalizeToDayStart(nowMillis)
        val yesterday = today - 86_400_000L
        return sleepLogForDate(today) ?: sleepLogForDate(yesterday)
    }

    fun last7NightsSleepLogs(nowMillis: Long = System.currentTimeMillis()): List<Pair<Long, SleepLog?>> {
        val today = normalizeToDayStart(nowMillis)
        val calendar = Calendar.getInstance().apply {
            timeInMillis = today
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val result = mutableListOf<Pair<Long, SleepLog?>>()
        for (i in 0..6) {
            val dayMillis = calendar.timeInMillis
            val log = sleepLogForDate(dayMillis)
            result.add(dayMillis to log)
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }

    fun saveSleepLog(log: SleepLog): Long {
        val dayStart = normalizeToDayStart(log.dateMillis)
        val duration = calculateSleepDurationMinutes(log.bedtimeMinute, log.wakeupMinute)
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("date_millis", java.lang.Long.valueOf(dayStart))
            put("bedtime_minute", log.bedtimeMinute)
            put("wakeup_minute", log.wakeupMinute)
            put("reported_bedtime_millis", log.reportedBedtimeMillis)
            put("reported_wakeup_millis", log.reportedWakeupMillis)
            put("duration_minutes", Integer.valueOf(duration))
            put("quality_rating", log.qualityRating.coerceIn(0, 5))
            put("note", log.note.trim())
            put("updated_at", now)
        }
        val existing = sleepLogForDate(dayStart)
        return if (existing != null) {
            database.writableDatabase.update(
                "sleep_logs",
                values,
                "id = ?",
                arrayOf(existing.id.toString()),
            )
            existing.id
        } else {
            values.put("created_at", now)
            database.writableDatabase.insert("sleep_logs", null, values)
        }
    }

    fun deleteSleepLog(id: Long) {
        database.writableDatabase.delete("sleep_logs", "id = ?", arrayOf(id.toString()))
    }

    fun isSleepModeActive(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val schedule = sleepSchedule()
        if (!schedule.enabled) return false
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val todayOfWeek = now.get(Calendar.DAY_OF_WEEK)

        return if (schedule.bedtimeMinute < schedule.wakeupMinute) {
            todayOfWeek in schedule.activeDays &&
                currentMinute >= schedule.bedtimeMinute &&
                currentMinute < schedule.wakeupMinute
        } else {
            if (currentMinute >= schedule.bedtimeMinute) {
                todayOfWeek in schedule.activeDays
            } else if (currentMinute < schedule.wakeupMinute) {
                val yesterdayOfWeek = Calendar.getInstance().apply {
                    timeInMillis = nowMillis
                    add(Calendar.DAY_OF_YEAR, -1)
                }.get(Calendar.DAY_OF_WEEK)
                yesterdayOfWeek in schedule.activeDays
            } else {
                false
            }
        }
    }

    fun sleepBlockedPackages(nowMillis: Long = System.currentTimeMillis()): Set<String> {
        val schedule = sleepSchedule()
        if (!schedule.enabled || !schedule.protectionEnabled || !isSleepModeActive(nowMillis)) {
            return emptySet()
        }
        return schedule.protectionBlockedPackages
    }

    // ============================================================================
    // WIND-DOWN AUDIO & SESSION STORAGE
    // ============================================================================

    fun windDownTracks(category: String? = null): List<WindDownAudioTrack> {
        val tracks = mutableListOf<WindDownAudioTrack>()
        val selection = if (category != null && category != "All") "category = ?" else null
        val selectionArgs = if (category != null && category != "All") arrayOf(category) else null
        val cursor = database.readableDatabase.query(
            "winddown_audio_tracks",
            null,
            selection,
            selectionArgs,
            null,
            null,
            "added_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                tracks.add(
                    WindDownAudioTrack(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        uri = it.getString(it.getColumnIndexOrThrow("uri")),
                        title = it.getString(it.getColumnIndexOrThrow("title")),
                        artist = it.getString(it.getColumnIndexOrThrow("artist")),
                        category = it.getString(it.getColumnIndexOrThrow("category")),
                        durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                        fileSizeBytes = it.getLong(it.getColumnIndexOrThrow("file_size_bytes")),
                        isFavourite = it.getInt(it.getColumnIndexOrThrow("is_favourite")) == 1,
                        addedAt = it.getLong(it.getColumnIndexOrThrow("added_at")),
                        lastPlayedAt = it.getLong(it.getColumnIndexOrThrow("last_played_at")),
                        playbackPositionMs = it.getLong(it.getColumnIndexOrThrow("playback_position_ms")),
                        albumArtUri = it.getString(it.getColumnIndexOrThrow("album_art_uri")),
                    )
                )
            }
        }
        return tracks
    }

    fun windDownTrackById(id: Long): WindDownAudioTrack? {
        val cursor = database.readableDatabase.query(
            "winddown_audio_tracks",
            null,
            "id = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                WindDownAudioTrack(
                    id = it.getLong(it.getColumnIndexOrThrow("id")),
                    uri = it.getString(it.getColumnIndexOrThrow("uri")),
                    title = it.getString(it.getColumnIndexOrThrow("title")),
                    artist = it.getString(it.getColumnIndexOrThrow("artist")),
                    category = it.getString(it.getColumnIndexOrThrow("category")),
                    durationMs = it.getLong(it.getColumnIndexOrThrow("duration_ms")),
                    fileSizeBytes = it.getLong(it.getColumnIndexOrThrow("file_size_bytes")),
                    isFavourite = it.getInt(it.getColumnIndexOrThrow("is_favourite")) == 1,
                    addedAt = it.getLong(it.getColumnIndexOrThrow("added_at")),
                    lastPlayedAt = it.getLong(it.getColumnIndexOrThrow("last_played_at")),
                    playbackPositionMs = it.getLong(it.getColumnIndexOrThrow("playback_position_ms")),
                    albumArtUri = it.getString(it.getColumnIndexOrThrow("album_art_uri")),
                )
            } else null
        }
    }

    fun saveWindDownTrack(track: WindDownAudioTrack): Long {
        val values = ContentValues().apply {
            put("uri", track.uri)
            put("title", track.title)
            put("artist", track.artist)
            put("category", track.category)
            put("duration_ms", track.durationMs)
            put("file_size_bytes", track.fileSizeBytes)
            put("is_favourite", if (track.isFavourite) 1 else 0)
            put("added_at", if (track.addedAt > 0) track.addedAt else System.currentTimeMillis())
            put("last_played_at", track.lastPlayedAt)
            put("playback_position_ms", track.playbackPositionMs)
            put("album_art_uri", track.albumArtUri)
        }
        val db = database.writableDatabase
        return if (track.id > 0) {
            db.update("winddown_audio_tracks", values, "id = ?", arrayOf(track.id.toString()))
            track.id
        } else {
            db.insert("winddown_audio_tracks", null, values)
        }
    }

    fun deleteWindDownTrack(id: Long) {
        database.writableDatabase.delete("winddown_audio_tracks", "id = ?", arrayOf(id.toString()))
    }

    fun setWindDownTrackFavourite(id: Long, isFav: Boolean) {
        val values = ContentValues().apply {
            put("is_favourite", if (isFav) 1 else 0)
        }
        database.writableDatabase.update("winddown_audio_tracks", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateWindDownTrackPlayback(id: Long, posMs: Long) {
        val values = ContentValues().apply {
            put("playback_position_ms", posMs)
            put("last_played_at", System.currentTimeMillis())
        }
        database.writableDatabase.update("winddown_audio_tracks", values, "id = ?", arrayOf(id.toString()))
    }

    fun windDownSettings(): WindDownAudioSettings {
        val cursor = database.readableDatabase.query(
            "winddown_audio_settings",
            null,
            "id = 1",
            null,
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                WindDownAudioSettings(
                    continueInBackground = it.getInt(it.getColumnIndexOrThrow("continue_in_background")) == 1,
                    showMediaControls = it.getInt(it.getColumnIndexOrThrow("show_media_controls")) == 1,
                    fadeOutAtEnd = it.getInt(it.getColumnIndexOrThrow("fade_out_at_end")) == 1,
                    defaultSleepTimerMinutes = it.getInt(it.getColumnIndexOrThrow("default_sleep_timer_minutes")),
                    resumeFromLastPosition = it.getInt(it.getColumnIndexOrThrow("resume_from_last_position")) == 1,
                    pauseOnIncomingCalls = it.getInt(it.getColumnIndexOrThrow("pause_on_incoming_calls")) == 1,
                    allowNotificationControls = it.getInt(it.getColumnIndexOrThrow("allow_notification_controls")) == 1,
                    mixRelaxingSound = it.getInt(it.getColumnIndexOrThrow("mix_relaxing_sound")) == 1,
                    selectedRelaxingSound = it.getString(it.getColumnIndexOrThrow("selected_relaxing_sound")),
                    relaxingSoundVolume = it.getFloat(it.getColumnIndexOrThrow("relaxing_sound_volume")),
                    audioVolume = it.getFloat(it.getColumnIndexOrThrow("audio_volume")),
                )
            } else {
                WindDownAudioSettings()
            }
        }
    }

    fun saveWindDownSettings(settings: WindDownAudioSettings) {
        val values = ContentValues().apply {
            put("id", 1)
            put("continue_in_background", if (settings.continueInBackground) 1 else 0)
            put("show_media_controls", if (settings.showMediaControls) 1 else 0)
            put("fade_out_at_end", if (settings.fadeOutAtEnd) 1 else 0)
            put("default_sleep_timer_minutes", settings.defaultSleepTimerMinutes)
            put("resume_from_last_position", if (settings.resumeFromLastPosition) 1 else 0)
            put("pause_on_incoming_calls", if (settings.pauseOnIncomingCalls) 1 else 0)
            put("allow_notification_controls", if (settings.allowNotificationControls) 1 else 0)
            put("mix_relaxing_sound", if (settings.mixRelaxingSound) 1 else 0)
            put("selected_relaxing_sound", settings.selectedRelaxingSound)
            put("relaxing_sound_volume", settings.relaxingSoundVolume)
            put("audio_volume", settings.audioVolume)
        }
        database.writableDatabase.insertWithOnConflict(
            "winddown_audio_settings",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun windDownSessionConfig(): WindDownSessionConfig {
        val cursor = database.readableDatabase.query(
            "winddown_session_config",
            null,
            "id = 1",
            null,
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                WindDownSessionConfig(
                    durationMinutes = it.getInt(it.getColumnIndexOrThrow("duration_minutes")),
                    blockAppsEnabled = it.getInt(it.getColumnIndexOrThrow("block_apps_enabled")) == 1,
                    screenDimmingEnabled = it.getInt(it.getColumnIndexOrThrow("screen_dimming_enabled")) == 1,
                    doNotDisturbEnabled = it.getInt(it.getColumnIndexOrThrow("do_not_disturb_enabled")) == 1,
                    selectedAudioTrackId = if (it.isNull(it.getColumnIndexOrThrow("selected_audio_track_id"))) null else it.getLong(it.getColumnIndexOrThrow("selected_audio_track_id")),
                    selectedRelaxingSound = if (it.isNull(it.getColumnIndexOrThrow("selected_relaxing_sound"))) null else it.getString(it.getColumnIndexOrThrow("selected_relaxing_sound")),
                )
            } else {
                WindDownSessionConfig()
            }
        }
    }

    fun saveWindDownSessionConfig(config: WindDownSessionConfig) {
        val values = ContentValues().apply {
            put("id", 1)
            put("duration_minutes", config.durationMinutes)
            put("block_apps_enabled", if (config.blockAppsEnabled) 1 else 0)
            put("screen_dimming_enabled", if (config.screenDimmingEnabled) 1 else 0)
            put("do_not_disturb_enabled", if (config.doNotDisturbEnabled) 1 else 0)
            put("selected_audio_track_id", config.selectedAudioTrackId)
            put("selected_relaxing_sound", config.selectedRelaxingSound)
        }
        database.writableDatabase.insertWithOnConflict(
            "winddown_session_config",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }




    // ============================================================================
    // SLEEP SETTINGS & PREFERENCES STORAGE
    // ============================================================================

    fun sleepPreferences(): SleepPreferences {
        database.writableDatabase.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sleep_preferences (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                wind_down_duration_minutes INTEGER NOT NULL DEFAULT 30,
                auto_start_wind_down INTEGER NOT NULL DEFAULT 0,
                app_blocking_during_wind_down INTEGER NOT NULL DEFAULT 1,
                screen_dimming_wind_down INTEGER NOT NULL DEFAULT 1,
                do_not_disturb_wind_down INTEGER NOT NULL DEFAULT 1,
                show_wind_down_reminder INTEGER NOT NULL DEFAULT 1,
                wind_down_reminder_advance_minutes INTEGER NOT NULL DEFAULT 10,
                remember_last_played_sound INTEGER NOT NULL DEFAULT 1,
                sound_default_volume REAL NOT NULL DEFAULT 0.70,
                sound_fade_out_at_end INTEGER NOT NULL DEFAULT 1,
                mix_with_my_audio INTEGER NOT NULL DEFAULT 1,
                sound_mix_volume REAL NOT NULL DEFAULT 0.40,
                play_through_speaker INTEGER NOT NULL DEFAULT 1,
                track_sleep_automatically INTEGER NOT NULL DEFAULT 1,
                sleep_goal_minutes INTEGER NOT NULL DEFAULT 450,
                bedtime_reminder_enabled INTEGER NOT NULL DEFAULT 1,
                bedtime_reminder_advance_minutes INTEGER NOT NULL DEFAULT 30,
                wakeup_alarm_enabled INTEGER NOT NULL DEFAULT 1,
                alarm_sound TEXT NOT NULL DEFAULT 'Gentle Tone',
                morning_notification_enabled INTEGER NOT NULL DEFAULT 1,
                enable_night_mode INTEGER NOT NULL DEFAULT 1,
                night_mode_schedule INTEGER NOT NULL DEFAULT 1,
                reduce_blue_light INTEGER NOT NULL DEFAULT 1,
                screen_dimming_during_sleep INTEGER NOT NULL DEFAULT 1,
                keep_screen_off INTEGER NOT NULL DEFAULT 1,
                lock_device_during_sleep INTEGER NOT NULL DEFAULT 0,
                notify_bedtime INTEGER NOT NULL DEFAULT 1,
                notify_wind_down INTEGER NOT NULL DEFAULT 1,
                notify_wakeup INTEGER NOT NULL DEFAULT 1,
                notify_sleep_summary INTEGER NOT NULL DEFAULT 1,
                notify_tips_insights INTEGER NOT NULL DEFAULT 1,
                notify_marketing INTEGER NOT NULL DEFAULT 0,
                store_locally INTEGER NOT NULL DEFAULT 1,
                backup_to_cloud INTEGER NOT NULL DEFAULT 0,
                smart_bedtime_suggestions INTEGER NOT NULL DEFAULT 1,
                adjust_for_weekends INTEGER NOT NULL DEFAULT 0,
                minimum_sleep_goal_hours REAL NOT NULL DEFAULT 7.0,
                vibration_on_wakeup INTEGER NOT NULL DEFAULT 1,
                adaptive_reminders INTEGER NOT NULL DEFAULT 1,
                beta_features_enabled INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        database.writableDatabase.execSQL("INSERT OR IGNORE INTO sleep_preferences (id) VALUES (1)")

        val cursor = database.readableDatabase.query(
            "sleep_preferences",
            null,
            "id = 1",
            null,
            null,
            null,
            null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                SleepPreferences(
                    windDownDurationMinutes = it.getInt(it.getColumnIndexOrThrow("wind_down_duration_minutes")),
                    autoStartWindDown = it.getInt(it.getColumnIndexOrThrow("auto_start_wind_down")) == 1,
                    appBlockingDuringWindDown = it.getInt(it.getColumnIndexOrThrow("app_blocking_during_wind_down")) == 1,
                    screenDimmingWindDown = it.getInt(it.getColumnIndexOrThrow("screen_dimming_wind_down")) == 1,
                    doNotDisturbWindDown = it.getInt(it.getColumnIndexOrThrow("do_not_disturb_wind_down")) == 1,
                    showWindDownReminder = it.getInt(it.getColumnIndexOrThrow("show_wind_down_reminder")) == 1,
                    windDownReminderAdvanceMinutes = it.getInt(it.getColumnIndexOrThrow("wind_down_reminder_advance_minutes")),
                    rememberLastPlayedSound = it.getInt(it.getColumnIndexOrThrow("remember_last_played_sound")) == 1,
                    soundDefaultVolume = it.getFloat(it.getColumnIndexOrThrow("sound_default_volume")),
                    soundFadeOutAtEnd = it.getInt(it.getColumnIndexOrThrow("sound_fade_out_at_end")) == 1,
                    mixWithMyAudio = it.getInt(it.getColumnIndexOrThrow("mix_with_my_audio")) == 1,
                    soundMixVolume = it.getFloat(it.getColumnIndexOrThrow("sound_mix_volume")),
                    playThroughSpeaker = it.getInt(it.getColumnIndexOrThrow("play_through_speaker")) == 1,
                    trackSleepAutomatically = it.getInt(it.getColumnIndexOrThrow("track_sleep_automatically")) == 1,
                    sleepGoalMinutes = it.getInt(it.getColumnIndexOrThrow("sleep_goal_minutes")),
                    bedtimeReminderEnabled = it.getInt(it.getColumnIndexOrThrow("bedtime_reminder_enabled")) == 1,
                    bedtimeReminderAdvanceMinutes = it.getInt(it.getColumnIndexOrThrow("bedtime_reminder_advance_minutes")),
                    wakeupAlarmEnabled = it.getInt(it.getColumnIndexOrThrow("wakeup_alarm_enabled")) == 1,
                    alarmSound = it.getString(it.getColumnIndexOrThrow("alarm_sound")),
                    morningNotificationEnabled = it.getInt(it.getColumnIndexOrThrow("morning_notification_enabled")) == 1,
                    enableNightMode = it.getInt(it.getColumnIndexOrThrow("enable_night_mode")) == 1,
                    nightModeSchedule = it.getInt(it.getColumnIndexOrThrow("night_mode_schedule")) == 1,
                    reduceBlueLight = it.getInt(it.getColumnIndexOrThrow("reduce_blue_light")) == 1,
                    screenDimmingDuringSleep = it.getInt(it.getColumnIndexOrThrow("screen_dimming_during_sleep")) == 1,
                    keepScreenOff = it.getInt(it.getColumnIndexOrThrow("keep_screen_off")) == 1,
                    lockDeviceDuringSleep = it.getInt(it.getColumnIndexOrThrow("lock_device_during_sleep")) == 1,
                    notifyBedtime = it.getInt(it.getColumnIndexOrThrow("notify_bedtime")) == 1,
                    notifyWindDown = it.getInt(it.getColumnIndexOrThrow("notify_wind_down")) == 1,
                    notifyWakeup = it.getInt(it.getColumnIndexOrThrow("notify_wakeup")) == 1,
                    notifySleepSummary = it.getInt(it.getColumnIndexOrThrow("notify_sleep_summary")) == 1,
                    notifyTipsInsights = it.getInt(it.getColumnIndexOrThrow("notify_tips_insights")) == 1,
                    notifyMarketing = it.getInt(it.getColumnIndexOrThrow("notify_marketing")) == 1,
                    storeLocally = it.getInt(it.getColumnIndexOrThrow("store_locally")) == 1,
                    backupToCloud = it.getInt(it.getColumnIndexOrThrow("backup_to_cloud")) == 1,
                    smartBedtimeSuggestions = it.getInt(it.getColumnIndexOrThrow("smart_bedtime_suggestions")) == 1,
                    adjustForWeekends = it.getInt(it.getColumnIndexOrThrow("adjust_for_weekends")) == 1,
                    minimumSleepGoalHours = it.getFloat(it.getColumnIndexOrThrow("minimum_sleep_goal_hours")),
                    vibrationOnWakeup = it.getInt(it.getColumnIndexOrThrow("vibration_on_wakeup")) == 1,
                    adaptiveReminders = it.getInt(it.getColumnIndexOrThrow("adaptive_reminders")) == 1,
                    betaFeaturesEnabled = it.getInt(it.getColumnIndexOrThrow("beta_features_enabled")) == 1,
                )
            } else {
                SleepPreferences()
            }
        }
    }

    fun saveSleepPreferences(prefs: SleepPreferences) {
        val values = ContentValues().apply {
            put("id", 1)
            put("wind_down_duration_minutes", prefs.windDownDurationMinutes)
            put("auto_start_wind_down", if (prefs.autoStartWindDown) 1 else 0)
            put("app_blocking_during_wind_down", if (prefs.appBlockingDuringWindDown) 1 else 0)
            put("screen_dimming_wind_down", if (prefs.screenDimmingWindDown) 1 else 0)
            put("do_not_disturb_wind_down", if (prefs.doNotDisturbWindDown) 1 else 0)
            put("show_wind_down_reminder", if (prefs.showWindDownReminder) 1 else 0)
            put("wind_down_reminder_advance_minutes", prefs.windDownReminderAdvanceMinutes)
            put("remember_last_played_sound", if (prefs.rememberLastPlayedSound) 1 else 0)
            put("sound_default_volume", prefs.soundDefaultVolume)
            put("sound_fade_out_at_end", if (prefs.soundFadeOutAtEnd) 1 else 0)
            put("mix_with_my_audio", if (prefs.mixWithMyAudio) 1 else 0)
            put("sound_mix_volume", prefs.soundMixVolume)
            put("play_through_speaker", if (prefs.playThroughSpeaker) 1 else 0)
            put("track_sleep_automatically", if (prefs.trackSleepAutomatically) 1 else 0)
            put("sleep_goal_minutes", prefs.sleepGoalMinutes)
            put("bedtime_reminder_enabled", if (prefs.bedtimeReminderEnabled) 1 else 0)
            put("bedtime_reminder_advance_minutes", prefs.bedtimeReminderAdvanceMinutes)
            put("wakeup_alarm_enabled", if (prefs.wakeupAlarmEnabled) 1 else 0)
            put("alarm_sound", prefs.alarmSound)
            put("morning_notification_enabled", if (prefs.morningNotificationEnabled) 1 else 0)
            put("enable_night_mode", if (prefs.enableNightMode) 1 else 0)
            put("night_mode_schedule", if (prefs.nightModeSchedule) 1 else 0)
            put("reduce_blue_light", if (prefs.reduceBlueLight) 1 else 0)
            put("screen_dimming_during_sleep", if (prefs.screenDimmingDuringSleep) 1 else 0)
            put("keep_screen_off", if (prefs.keepScreenOff) 1 else 0)
            put("lock_device_during_sleep", if (prefs.lockDeviceDuringSleep) 1 else 0)
            put("notify_bedtime", if (prefs.notifyBedtime) 1 else 0)
            put("notify_wind_down", if (prefs.notifyWindDown) 1 else 0)
            put("notify_wakeup", if (prefs.notifyWakeup) 1 else 0)
            put("notify_sleep_summary", if (prefs.notifySleepSummary) 1 else 0)
            put("notify_tips_insights", if (prefs.notifyTipsInsights) 1 else 0)
            put("notify_marketing", if (prefs.notifyMarketing) 1 else 0)
            put("store_locally", if (prefs.storeLocally) 1 else 0)
            put("backup_to_cloud", if (prefs.backupToCloud) 1 else 0)
            put("smart_bedtime_suggestions", if (prefs.smartBedtimeSuggestions) 1 else 0)
            put("adjust_for_weekends", if (prefs.adjustForWeekends) 1 else 0)
            put("minimum_sleep_goal_hours", prefs.minimumSleepGoalHours)
            put("vibration_on_wakeup", if (prefs.vibrationOnWakeup) 1 else 0)
            put("adaptive_reminders", if (prefs.adaptiveReminders) 1 else 0)
            put("beta_features_enabled", if (prefs.betaFeaturesEnabled) 1 else 0)
        }
        database.writableDatabase.insertWithOnConflict(
            "sleep_preferences",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        // Sync connected subsystems safely
        try {
            val curSessionConfig = windDownSessionConfig()
            saveWindDownSessionConfig(
                curSessionConfig.copy(
                    durationMinutes = prefs.windDownDurationMinutes,
                    blockAppsEnabled = prefs.appBlockingDuringWindDown,
                    screenDimmingEnabled = prefs.screenDimmingWindDown,
                    doNotDisturbEnabled = prefs.doNotDisturbWindDown,
                )
            )

            val curAudioSettings = windDownSettings()
            saveWindDownSettings(
                curAudioSettings.copy(
                    fadeOutAtEnd = prefs.soundFadeOutAtEnd,
                    mixRelaxingSound = prefs.mixWithMyAudio,
                    relaxingSoundVolume = prefs.soundMixVolume,
                    audioVolume = prefs.soundDefaultVolume,
                )
            )
        } catch (_: Exception) {}
    }

    fun exportSleepDataJson(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "StudyMode")
        root.put("exported_at_millis", System.currentTimeMillis())
        root.put("export_date_iso", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))

        val weekday = weekdaySleepSchedule()
        val weekend = weekendSleepSchedule()
        val schedulesObj = JSONObject().apply {
            put("weekday", JSONObject().apply {
                put("enabled", weekday.enabled)
                put("bedtime_minute", weekday.bedtimeMinute)
                put("wakeup_minute", weekday.wakeupMinute)
                put("target_duration_minutes", weekday.targetDurationMinutes)
                put("active_days", weekday.activeDays.sorted().joinToString(","))
            })
            put("weekend", JSONObject().apply {
                put("enabled", weekend.enabled)
                put("bedtime_minute", weekend.bedtimeMinute)
                put("wakeup_minute", weekend.wakeupMinute)
                put("target_duration_minutes", weekend.targetDurationMinutes)
                put("active_days", weekend.activeDays.sorted().joinToString(","))
            })
        }
        root.put("schedules", schedulesObj)

        val logsArr = JSONArray()
        sleepLogs(100).forEach { log ->
            logsArr.put(
                JSONObject().apply {
                    put("id", log.id)
                    put("date_millis", log.dateMillis)
                    put("bedtime_minute", log.bedtimeMinute)
                    put("wakeup_minute", log.wakeupMinute)
                    put("duration_minutes", log.durationMinutes)
                    put("quality_rating", log.qualityRating)
                    put("note", log.note)
                }
            )
        }
        root.put("sleep_logs", logsArr)

        val prefs = sleepPreferences()
        val prefsObj = JSONObject().apply {
            put("wind_down_duration_minutes", prefs.windDownDurationMinutes)
            put("sleep_goal_minutes", prefs.sleepGoalMinutes)
            put("track_sleep_automatically", prefs.trackSleepAutomatically)
            put("enable_night_mode", prefs.enableNightMode)
            put("reduce_blue_light", prefs.reduceBlueLight)
            put("alarm_sound", prefs.alarmSound)
            put("vibration_on_wakeup", prefs.vibrationOnWakeup)
            put("store_locally", prefs.storeLocally)
        }
        root.put("preferences", prefsObj)

        return root.toString(2)
    }

    fun deleteAllSleepData() {
        database.writableDatabase.execSQL("DELETE FROM sleep_logs")
        database.writableDatabase.execSQL(
            "UPDATE sleep_schedule SET bedtime_minute = 1380, wakeup_minute = 420, target_duration_minutes = 480 WHERE id = 1"
        )
        database.writableDatabase.execSQL(
            "UPDATE sleep_schedule SET bedtime_minute = 1410, wakeup_minute = 480, target_duration_minutes = 510 WHERE id = 2"
        )
        saveSleepPreferences(SleepPreferences())
    }

    companion object {
        const val DEFAULT_DAILY_GOAL_MINUTES = 120
        const val DEFAULT_DAILY_UNLOCK_GOAL = 50
        const val DEFAULT_STRICT_DELAY_SECONDS = 30
        const val DEFAULT_FOCUS_MINUTES = 25
        const val MIN_DAILY_GOAL_MINUTES = 15
        const val MAX_DAILY_GOAL_MINUTES = 600
        const val MIN_FOCUS_MINUTES = 1
        const val MAX_FOCUS_MINUTES = 720
        const val MIN_APP_LIMIT_MINUTES = 5
        const val MAX_APP_LIMIT_MINUTES = 600
        private const val MILLIS_PER_MINUTE = 60_000L

        private const val KEY_TIMER_STARTED_AT = "started_at"
        private const val KEY_SEGMENT_STARTED_AT = "segment_started_at"
        private const val KEY_SEGMENT_TARGET = "segment_target"
        private const val KEY_SAVED_REMAINING = "saved_remaining"
        private const val KEY_ELAPSED_BEFORE_SEGMENT = "elapsed_before_segment"
        private const val KEY_TARGET_MINUTES = "target_minutes"
        private const val KEY_GOAL = "goal"
        private const val KEY_FOCUS_TAG = "focus_tag"
        private const val KEY_FOCUS_THEME = "focus_theme"
        private const val KEY_FOCUS_MUSIC = "focus_music"
        private const val KEY_IS_LAUNCHER_BLOCKED = "is_launcher_blocked"
        private const val KEY_IS_ULTRA_FOCUS = "is_ultra_focus"
        private const val KEY_BLOCK_SHORTS_REELS_ENABLED = "block_shorts_reels_enabled"
        private const val KEY_BLOCK_YT_SHORTS = "block_yt_shorts"
        private const val KEY_BLOCK_IG_REELS = "block_ig_reels"
        private const val KEY_BLOCK_SNAP_SPOTLIGHT = "block_snap_spotlight"
        private const val KEY_BLOCK_FB_REELS = "block_fb_reels"
        private const val KEY_SHORTS_BLOCKED_DAY = "shorts_blocked_day"
        private const val KEY_SHORTS_BLOCKED_COUNT = "shorts_blocked_count"
        private const val KEY_FLOATING_SCROLL_COUNTER_ENABLED = "floating_scroll_counter_enabled"
        private const val KEY_SHORTS_SCROLL_LIMIT = "shorts_scroll_limit"
        private const val KEY_REELS_SCROLLED_DAY = "reels_scrolled_day"
        private const val KEY_REELS_SCROLLED_COUNT = "reels_scrolled_count"
        private const val KEY_REELS_WATCH_TIME_DAY = "reels_watch_time_day"
        private const val KEY_REELS_WATCH_TIME_SECONDS = "reels_watch_time_seconds"
        private const val KEY_RUNNING = "running"
        private const val KEY_FOCUS_ONLY_BLOCKING = "focus_only_blocking"
        private const val KEY_BLOCKING_PAUSED_UNTIL = "blocking_paused_until"
        private const val KEY_MINDFUL_FRICTION_ENABLED = "mindful_friction_enabled"
        private const val KEY_MINDFUL_FRICTION_DEFAULT_TAB = "mindful_friction_default_tab"
        private const val KEY_MINDFUL_FRICTION_PAUSE_SECONDS = "mindful_friction_pause_seconds"
        private const val KEY_MINDFUL_FRICTION_BREATHS_TARGET = "mindful_friction_breaths_target"
        private const val KEY_UNLOCK_COUNT_DATE = "unlock_count_date"
        private const val KEY_TODAY_UNLOCKS = "today_unlocks"
        private const val KEY_TODAY_PICKUPS = "today_pickups"
        private const val KEY_DAILY_UNLOCK_GOAL = "daily_unlock_goal"
        private const val KEY_STRICT_UNBLOCK_DELAY_ENABLED = "strict_unblock_delay_enabled"
        private const val KEY_STRICT_UNBLOCK_DELAY_SECONDS = "strict_unblock_delay_seconds"
        private const val KEY_APP_INTERCEPTION_DAY = "app_interception_day"
        private const val KEY_APP_INTERCEPTIONS_COUNT = "app_interceptions_count"
        private const val KEY_NOTIF_BATCHING_ENABLED = "notif_batching_enabled"
        private const val KEY_NOTIF_BATCH_FOCUS_ONLY = "notif_batch_focus_only"
        private const val KEY_NOTIF_DIGEST_ON_FOCUS_END = "notif_digest_on_focus_end"
        private const val KEY_BATCHED_NOTIFICATIONS_JSON = "batched_notifications_json"
        private const val KEY_ZEN_MODE_END_TIME = "zen_mode_end_time"
        private const val KEY_ZEN_MODE_DURATION_MINUTES = "zen_mode_duration_minutes"
        private const val KEY_ZEN_SESSION_STARTED_AT = "zen_session_started_at"
        private val NOTIF_LOCK = Any()
        private const val BLOCKING_PAUSE_MILLIS = 5 * 60_000L
        private const val KEY_PROFILE_STARTED_AT = "started_at"
        private const val KEY_PROFILE_NAME = "display_name"
        private const val KEY_PROFILE_EMAIL = "email"
        private const val KEY_PROFILE_AVATAR_URI = "avatar_uri"
        private const val DEFAULT_PROFILE_NAME = "StudyMode User"
        private const val MAX_PROFILE_NAME_LENGTH = 60
        private const val MAX_PROFILE_EMAIL_LENGTH = 254
        private val TIMER_LOCK = Any()
    }
}

private fun Cursor.toStudySchedule(): StudySchedule {
    val id = getLong(0)
    val title = getString(1)
    val dayOfWeek = getInt(2)
    val startMinute = getInt(3)
    val durationMinutes = getInt(4)
    val enabled = getInt(5) == 1
    val days = getString(6).orEmpty()
        .split(',')
        .mapNotNull(String::toIntOrNull)
        .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
        .toSet()
        .ifEmpty { setOf(dayOfWeek) }
    val storedEndMinute = getInt(7)
    return StudySchedule(
        id = id,
        title = title,
        dayOfWeek = dayOfWeek,
        startMinute = startMinute,
        durationMinutes = durationMinutes,
        enabled = enabled,
        activeDays = days,
        endMinute = if (storedEndMinute in 0 until 24 * 60) storedEndMinute
            else (startMinute + durationMinutes) % (24 * 60),
        reminderEnabled = getInt(8) == 1,
        breaksEnabled = getInt(9) == 1,
        focusIntervalMinutes = getInt(10),
        shortBreakMinutes = getInt(11),
        longBreakMinutes = getInt(12),
        intervalsBeforeLongBreak = getInt(13),
        blockedPackages = getString(14).orEmpty().split(',').filter(String::isNotBlank).toSet(),
        createdAtMillis = getLong(15),
        updatedAtMillis = getLong(16),
    )
}

internal fun calculateSleepDurationMinutes(bedtimeMinute: Int, wakeupMinute: Int): Int =
    if (wakeupMinute >= bedtimeMinute) {
        wakeupMinute - bedtimeMinute
    } else {
        (24 * 60 - bedtimeMinute) + wakeupMinute
    }

internal fun normalizeToDayStart(timeMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun Cursor.toSleepLog(): SleepLog =
    SleepLog(
        id = getLong(0),
        dateMillis = getLong(1),
        bedtimeMinute = getInt(2),
        wakeupMinute = getInt(3),
        reportedBedtimeMillis = getLong(4),
        reportedWakeupMillis = getLong(5),
        durationMinutes = getInt(6),
        qualityRating = getInt(7),
        note = getString(8).orEmpty(),
        createdAtMillis = getLong(9),
        updatedAtMillis = getLong(10),
    )


internal fun isScheduleActiveAt(schedule: StudySchedule, nowMillis: Long): Boolean {
    if (!schedule.enabled) return false
    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val day = now.get(Calendar.DAY_OF_WEEK)
    if (schedule.endMinute > schedule.startMinute) {
        return day in schedule.activeDays &&
            minuteOfDay >= schedule.startMinute &&
            minuteOfDay < schedule.endMinute
    }
    if (minuteOfDay >= schedule.startMinute) return day in schedule.activeDays
    if (minuteOfDay < schedule.endMinute) {
        val previousDay = (Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }).get(Calendar.DAY_OF_WEEK)
        return previousDay in schedule.activeDays
    }
    return false
}

internal fun normalizeBlockedDomain(input: String): String {
    val trimmed = input.trim().trimEnd('.')
    require(trimmed.isNotEmpty() && !trimmed.contains(Regex("[/:?#@\\s]"))) {
        "Enter a hostname such as example.com, without a URL or path."
    }
    val ascii = try {
        IDN.toASCII(trimmed, IDN.USE_STD3_ASCII_RULES).lowercase(Locale.ROOT)
    } catch (exception: IllegalArgumentException) {
        throw IllegalArgumentException("Enter a valid hostname.", exception)
    }
    val labels = ascii.split('.')
    require(
        ascii.length <= 253 &&
            labels.size >= 2 &&
            labels.all { it.isNotEmpty() && it.length <= 63 },
    ) {
        "Enter a valid domain with at least two labels."
    }
    return ascii
}

internal fun isBlockedDomainActiveAt(rule: BlockedDomain, nowMillis: Long): Boolean {
    if (rule.activeDays.isEmpty()) return true
    if (rule.startMinute !in 0 until 24 * 60 ||
        rule.endMinute !in 0 until 24 * 60 ||
        rule.startMinute == rule.endMinute
    ) return false

    val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val today = now.get(Calendar.DAY_OF_WEEK)
    if (rule.startMinute < rule.endMinute) {
        return today in rule.activeDays && minute in rule.startMinute until rule.endMinute
    }
    if (minute >= rule.startMinute) return today in rule.activeDays
    if (minute < rule.endMinute) {
        val previousDay = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, -1)
        }.get(Calendar.DAY_OF_WEEK)
        return previousDay in rule.activeDays
    }
    return false
}

internal fun domainRuleMatches(hostname: String, blockedDomain: String): Boolean {
    val host = hostname.lowercase(Locale.ROOT).trimEnd('.')
    val domain = blockedDomain.lowercase(Locale.ROOT).trimEnd('.')
    return host == domain || host.endsWith(".$domain")

}

data class WindDownAudioTrack(
    val id: Long = 0L,
    val uri: String,
    val title: String,
    val artist: String = "",
    val category: String = "Music",
    val durationMs: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val isFavourite: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = 0L,
    val playbackPositionMs: Long = 0L,
    val albumArtUri: String = "",
)

data class WindDownAudioSettings(
    val continueInBackground: Boolean = true,
    val showMediaControls: Boolean = true,
    val fadeOutAtEnd: Boolean = true,
    val defaultSleepTimerMinutes: Int = 30,
    val resumeFromLastPosition: Boolean = true,
    val pauseOnIncomingCalls: Boolean = true,
    val allowNotificationControls: Boolean = true,
    val mixRelaxingSound: Boolean = false,
    val selectedRelaxingSound: String = "Rain",
    val relaxingSoundVolume: Float = 0.5f,
    val audioVolume: Float = 0.85f,
)

data class WindDownSessionConfig(
    val durationMinutes: Int = 30,
    val blockAppsEnabled: Boolean = true,
    val screenDimmingEnabled: Boolean = false,
    val doNotDisturbEnabled: Boolean = false,
    val selectedAudioTrackId: Long? = null,
    val selectedRelaxingSound: String? = null,
)

data class SleepPreferences(
    val windDownDurationMinutes: Int = 30,
    val autoStartWindDown: Boolean = false,
    val appBlockingDuringWindDown: Boolean = true,
    val screenDimmingWindDown: Boolean = true,
    val doNotDisturbWindDown: Boolean = true,
    val showWindDownReminder: Boolean = true,
    val windDownReminderAdvanceMinutes: Int = 10,
    val rememberLastPlayedSound: Boolean = true,
    val soundDefaultVolume: Float = 0.70f,
    val soundFadeOutAtEnd: Boolean = true,
    val mixWithMyAudio: Boolean = true,
    val soundMixVolume: Float = 0.40f,
    val playThroughSpeaker: Boolean = true,
    val trackSleepAutomatically: Boolean = true,
    val sleepGoalMinutes: Int = 450,
    val bedtimeReminderEnabled: Boolean = true,
    val bedtimeReminderAdvanceMinutes: Int = 30,
    val wakeupAlarmEnabled: Boolean = true,
    val alarmSound: String = "Gentle Tone",
    val morningNotificationEnabled: Boolean = true,
    val enableNightMode: Boolean = true,
    val nightModeSchedule: Boolean = true,
    val reduceBlueLight: Boolean = true,
    val screenDimmingDuringSleep: Boolean = true,
    val keepScreenOff: Boolean = true,
    val lockDeviceDuringSleep: Boolean = false,
    val notifyBedtime: Boolean = true,
    val notifyWindDown: Boolean = true,
    val notifyWakeup: Boolean = true,
    val notifySleepSummary: Boolean = true,
    val notifyTipsInsights: Boolean = true,
    val notifyMarketing: Boolean = false,
    val storeLocally: Boolean = true,
    val backupToCloud: Boolean = false,
    val smartBedtimeSuggestions: Boolean = true,
    val adjustForWeekends: Boolean = false,
    val minimumSleepGoalHours: Float = 7.0f,
    val vibrationOnWakeup: Boolean = true,
    val adaptiveReminders: Boolean = true,
    val betaFeaturesEnabled: Boolean = false,
)
