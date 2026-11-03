package com.studymode.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object FocusTimerAlarms {
    private const val ACTION_FOCUS_TIMER_COMPLETE = "com.studymode.app.FOCUS_TIMER_COMPLETE"
    private const val NOTIFICATION_CHANNEL_ID = "focus_timer_completion"
    private const val NOTIFICATION_ID = 7201

    fun schedule(context: Context, timer: ActiveTimer) {
        if (!timer.running) {
            cancel(context)
            return
        }
        val triggerAtMillis = timer.segmentStartedAtMillis + timer.segmentTargetMillis
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context),
        )
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = pendingIntent(context)
        alarmManager.cancel(intent)
        intent.cancel()
    }

    fun restore(context: Context) {
        val timer = LocalStudyStore(context).activeTimer()
        if (timer?.running == true) schedule(context, timer) else cancel(context)
    }

    fun notificationsEnabled(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    internal fun complete(
        context: Context,
        store: LocalStudyStore,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val timer = store.activeTimer() ?: run {
            cancel(context)
            return false
        }
        if (!store.finishTimerIfDue(nowMillis)) {
            restore(context)
            return false
        }
        cancel(context)
        SoundEffectManager.play(context, SoundEffect.TIMER_END)
        postCompletionNotification(context, timer.goal)
        if (store.isNotificationBatchingEnabled() && store.isDeliverDigestOnFocusEnd()) {
            val count = store.getBatchedNotificationsCount()
            if (count > 0) {
                NotificationDigestHelper.showDigestNotification(context, count)
            }
        }
        return true
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, FocusTimerCompletionReceiver::class.java)
                .setAction(ACTION_FOCUS_TIMER_COMPLETE)
                .setData(Uri.parse("studymode://focus/complete")),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun postCompletionNotification(context: Context, goal: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Focus session alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("studymode://open/focus"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle("Focus session complete")
            .setContentText(goal.ifBlank { "Take a short break or start another session." })
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class FocusTimerCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusTimerAlarms.complete(context, LocalStudyStore(context))
    }
}
