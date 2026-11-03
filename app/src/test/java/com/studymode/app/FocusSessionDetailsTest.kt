package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class FocusSessionDetailsTest {

    @Test
    fun testFocusSessionRecordDefaultsAndDisplaySubject() {
        // Standard record with explicit subject
        val record1 = FocusSessionRecord(
            id = 1L,
            startedAtMillis = 1705219200000L, // Jan 14, 2024 8:30 AM
            elapsedSeconds = 4800L, // 1h 20m
            targetMinutes = 80,
            goal = "Exam prep",
            completed = true,
            finishedAtMillis = 1705224000000L, // Jan 14, 2024 9:50 AM
            breakSeconds = 600L, // 10 min
            mode = "Focus",
            subject = "Math practice",
            notes = "Algebra practice and problem solving."
        )

        assertEquals("Math practice", record1.displaySubject)
        assertEquals("Algebra practice and problem solving.", record1.notes)
        assertEquals(4800L, record1.elapsedSeconds)
        assertEquals(600L, record1.breakSeconds)
        assertEquals("Focus", record1.mode)
        assertTrue(record1.completed)

        // Record with blank subject falls back to goal
        val record2 = FocusSessionRecord(
            id = 2L,
            startedAtMillis = 1000L,
            elapsedSeconds = 1800L,
            targetMinutes = 30,
            goal = "Physics HW",
            completed = true,
            subject = ""
        )
        assertEquals("Physics HW", record2.displaySubject)

        // Record with blank subject and blank goal falls back to "Study Focus"
        val record3 = FocusSessionRecord(
            id = 3L,
            startedAtMillis = 1000L,
            elapsedSeconds = 1800L,
            targetMinutes = 30,
            goal = "",
            completed = false,
            subject = ""
        )
        assertEquals("Study Focus", record3.displaySubject)
    }

    @Test
    fun testSessionTimelineEventDurations() {
        val start = 1705219200000L
        val end = start + 20 * 60000L // 20 minutes

        val event = SessionTimelineEvent(
            type = "FOCUS",
            startMillis = start,
            endMillis = end,
            label = "Focused",
            details = "Linear algebra practice"
        )

        assertEquals(20 * 60000L, event.durationMillis)
        assertEquals(20, event.durationMinutes)
        assertEquals("FOCUS", event.type)
        assertEquals("Focused", event.label)
    }

    @Test
    fun testSessionAppUsageModel() {
        val app = SessionAppUsage(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            durationMillis = 300_000L, // 5 min
            launchCount = 2,
            isDistraction = true,
            isBlocked = true
        )

        assertEquals("YouTube", app.appName)
        assertEquals(5, app.durationMinutes)
        assertEquals(2, app.launchCount)
        assertTrue(app.isDistraction)
        assertTrue(app.isBlocked)
    }

    @Test
    fun testSessionIntervalBarProperties() {
        val bar = SessionIntervalBar(
            index = 0,
            startMillis = 1000L,
            endMillis = 61000L,
            timeLabel = "8:30 AM – 8:40 AM",
            intensity = 0.85f,
            type = "FOCUS",
            durationMinutes = 10
        )

        assertEquals(0, bar.index)
        assertEquals("8:30 AM – 8:40 AM", bar.timeLabel)
        assertEquals(0.85f, bar.intensity, 0.001f)
        assertEquals("FOCUS", bar.type)
        assertEquals(10, bar.durationMinutes)
    }

}
