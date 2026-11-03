package com.studymode.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Handles posting the mindful summary notification digest after focus sessions or at scheduled digest intervals.
 */
object NotificationDigestHelper {

    private const val CHANNEL_ID = "studymode_notification_digest"
    private const val NOTIFICATION_ID = 4004
    const val ACTION_OPEN_DIGEST = "com.studymode.app.ACTION_OPEN_DIGEST"

    fun showDigestNotification(context: Context, batchedCount: Int) {
        if (batchedCount <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "StudyMode Notification Digest",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Summary of notifications batched and silenced during focus sessions"
                }
            )
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_DIGEST
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = "StudyMode Focus Digest 📬"
        val content = if (batchedCount == 1) {
            "1 notification was held while you were focusing. Tap to review."
        } else {
            "$batchedCount notifications were held while you were focusing. Tap to review."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$content\nTake your time to review them calmly without breaking your focus flow."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
