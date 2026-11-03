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
import java.util.Calendar

object StudyScheduleAlarms {
    private const val ACTION_SCHEDULE_REMINDER = "com.studymode.app.SCHEDULE_REMINDER"
    private const val EXTRA_SCHEDULE_ID = "schedule_id"

    fun schedule(context: Context, schedule: StudySchedule, nowMillis: Long = System.currentTimeMillis()) {
        if (!schedule.enabled || !schedule.reminderEnabled || !notificationsEnabled(context)) {
            cancel(context, schedule.id)
            return
        }

        val triggerAtMillis = nextScheduleOccurrenceMillis(
            schedule.activeDays,
            schedule.startMinute,
            nowMillis,
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent(context, schedule.id),
        )
    }

    fun cancel(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = pendingIntent(context, scheduleId)
        alarmManager.cancel(intent)
        intent.cancel()
    }

    private fun pendingIntent(context: Context, scheduleId: Long): PendingIntent {
        val intent = Intent(context, ScheduleReminderReceiver::class.java)
            .setAction(ACTION_SCHEDULE_REMINDER)
            .setData(Uri.parse("studymode://schedule/$scheduleId"))
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        return PendingIntent.getBroadcast(
            context,
            scheduleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    internal fun scheduleIdFrom(intent: Intent): Long =
        intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)

    internal fun postAndReschedule(context: Context, scheduleId: Long) {
        val store = LocalStudyStore(context)
        val schedule = store.schedule(scheduleId) ?: return
        if (!schedule.enabled || !schedule.reminderEnabled) {
            cancel(context, scheduleId)
            return
        }
        postReminder(context, schedule)
        schedule(context, schedule)
    }

    fun notificationsEnabled(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    internal fun postReminder(context: Context, schedule: StudySchedule) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Study reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            schedule.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("studymode://open/schedule/${schedule.id}"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle("Study time")
            .setContentText("Tap to open StudyMode and start ${schedule.title}.")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(schedule.id.hashCode(), notification)
    }

    private const val NOTIFICATION_CHANNEL_ID = "study_schedule_reminders"
}

internal fun nextScheduleOccurrenceMillis(
    dayOfWeek: Int,
    startMinute: Int,
    nowMillis: Long,
): Long = nextScheduleOccurrenceMillis(setOf(dayOfWeek), startMinute, nowMillis)

internal fun nextScheduleOccurrenceMillis(
    activeDays: Set<Int>,
    startMinute: Int,
    nowMillis: Long,
): Long {
    require(activeDays.isNotEmpty())
    return (0..7).asSequence()
        .map { offset ->
            Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, offset)
            }
        }
        .filter { it.get(Calendar.DAY_OF_WEEK) in activeDays }
        .map { day ->
            day.apply {
                set(Calendar.HOUR_OF_DAY, startMinute / 60)
                set(Calendar.MINUTE, startMinute % 60)
            }.timeInMillis
        }
        .firstOrNull { it > nowMillis }
        ?: throw IllegalStateException("Could not find the next schedule reminder.")
}

class ScheduleReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = StudyScheduleAlarms.scheduleIdFrom(intent)
        if (scheduleId <= 0) return
        StudyScheduleAlarms.postAndReschedule(context, scheduleId)
    }
}

class ScheduleAlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = LocalStudyStore(context)
        store.schedules().filter { it.enabled && it.reminderEnabled }.forEach {
            StudyScheduleAlarms.schedule(context, it)
        }
        FocusTimerAlarms.restore(context)
        SleepAlarms.reschedule(context)
        if (hasUsageAccess(context)) {
            AppMonitorService.start(context)
        }
        AppBlockingAccessibilityService.updateConfiguration(context)
    }
}
