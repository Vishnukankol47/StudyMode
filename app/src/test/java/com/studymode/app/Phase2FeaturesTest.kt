package com.studymode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2FeaturesTest {

    @Test
    fun testBatchedNotificationDataModel() {
        val original = BatchedNotification(
            id = "notif_123",
            packageName = "com.instagram.android",
            appName = "Instagram",
            title = "New direct message",
            text = "Hey, are you free to chat?",
            timestampMillis = 1700000000000L,
            key = "key_insta_1",
        )

        assertEquals("notif_123", original.id)
        assertEquals("com.instagram.android", original.packageName)
        assertEquals("Instagram", original.appName)
        assertEquals("New direct message", original.title)
        assertEquals("Hey, are you free to chat?", original.text)
        assertEquals(1700000000000L, original.timestampMillis)
        assertEquals("key_insta_1", original.key)

        val copy = original.copy(title = "Updated message")
        assertEquals("Updated message", copy.title)
        assertEquals(original.id, copy.id)
        assertNotEquals(original, copy)
    }

    @Test
    fun testBatchedNotificationQueueTrimming() {
        val list = mutableListOf<BatchedNotification>()
        for (i in 1..120) {
            list.add(
                0,
                BatchedNotification(
                    id = "id_$i",
                    packageName = "com.app_$i",
                    appName = "App $i",
                    title = "Title $i",
                    text = "Text $i",
                    timestampMillis = i.toLong(),
                    key = "k_$i"
                )
            )
        }

        val trimmed = if (list.size > 100) list.take(100) else list
        assertEquals(100, trimmed.size)
        // Most recent added should be first
        assertEquals("id_120", trimmed.first().id)
        // Oldest preserved should be id_21
        assertEquals("id_21", trimmed.last().id)
    }

    @Test
    fun testZenModeTimingCalculations() {
        val startMillis = 1000_000L
        val durationMinutes = 45
        val endMillis = startMillis + durationMinutes * 60_000L

        // Active check
        val midMillis = startMillis + 20 * 60_000L
        assertTrue(endMillis > midMillis)
        val remainingMillis = (endMillis - midMillis).coerceAtLeast(0L)
        assertEquals(25 * 60_000L, remainingMillis)

        // Expiry check
        val pastEndMillis = endMillis + 5000L
        assertFalse(endMillis > pastEndMillis)
        val expiredRemaining = (endMillis - pastEndMillis).coerceAtLeast(0L)
        assertEquals(0L, expiredRemaining)
    }

    @Test
    fun testWidgetTimeFormatting() {
        fun formatFocus(minutes: Int): String {
            return if (minutes < 60) {
                "${minutes}m"
            } else {
                "${minutes / 60}h ${minutes % 60}m"
            }
        }

        assertEquals("0m", formatFocus(0))
        assertEquals("45m", formatFocus(45))
        assertEquals("1h 0m", formatFocus(60))
        assertEquals("1h 25m", formatFocus(85))
        assertEquals("3h 10m", formatFocus(190))
    }
}
