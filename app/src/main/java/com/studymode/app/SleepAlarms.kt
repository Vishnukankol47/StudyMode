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

object SleepAlarms {
    const val ACTION_SLEEP_REMINDER = "com.studymode.app.SLEEP_REMINDER"
    const val ACTION_WAKEUP_REMINDER = "com.studymode.app.WAKEUP_REMINDER"
    const val EXTRA_SCHEDULE_ID = "schedule_id"
    const val EXTRA_IS_WAKEUP = "is_wakeup"

    const val REQ_BEDTIME_WEEKDAY = 4001
    const val REQ_WAKEUP_WEEKDAY = 4002
    const val REQ_BEDTIME_WEEKEND = 4003
    const val REQ_WAKEUP_WEEKEND = 4004

    const val NOTIFICATION_CHANNEL_ID = "sleep_reminders"

    fun schedule(context: Context, schedule: SleepSchedule, nowMillis: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Bedtime reminder
        val bedtimeReqCode = if (schedule.id == 2L) REQ_BEDTIME_WEEKEND else REQ_BEDTIME_WEEKDAY
        val bedtimeIntent = pendingIntent(context, schedule.id, false, bedtimeReqCode)
        if (schedule.enabled && schedule.reminderEnabled && StudyScheduleAlarms.notificationsEnabled(context) && schedule.activeDays.isNotEmpty()) {
            val reminderMinute = (schedule.bedtimeMinute - schedule.reminderAdvanceMinutes + 1440) % 1440
            val triggerAtMillis = safeNextOccurrence(schedule.activeDays, reminderMinute, nowMillis)
            if (triggerAtMillis > 0L) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    bedtimeIntent,
                )
            }
        } else {
            alarmManager.cancel(bedtimeIntent)
            bedtimeIntent.cancel()
        }

        // Wake-up reminder
        val wakeupReqCode = if (schedule.id == 2L) REQ_WAKEUP_WEEKEND else REQ_WAKEUP_WEEKDAY
        val wakeupIntent = pendingIntent(context, schedule.id, true, wakeupReqCode)
        if (schedule.enabled && schedule.wakeupReminderEnabled && StudyScheduleAlarms.notificationsEnabled(context) && schedule.activeDays.isNotEmpty()) {
            val triggerAtMillis = safeNextOccurrence(schedule.activeDays, schedule.wakeupMinute, nowMillis)
            if (triggerAtMillis > 0L) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    wakeupIntent,
                )
            }
        } else {
            alarmManager.cancel(wakeupIntent)
            wakeupIntent.cancel()
        }
    }

    fun cancel(context: Context, scheduleId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val bedtimeReqCode = if (scheduleId == 2L) REQ_BEDTIME_WEEKEND else REQ_BEDTIME_WEEKDAY
        val wakeupReqCode = if (scheduleId == 2L) REQ_WAKEUP_WEEKEND else REQ_WAKEUP_WEEKDAY

        val bedtimeIntent = pendingIntent(context, scheduleId, false, bedtimeReqCode)
        alarmManager.cancel(bedtimeIntent)
        bedtimeIntent.cancel()

        val wakeupIntent = pendingIntent(context, scheduleId, true, wakeupReqCode)
        alarmManager.cancel(wakeupIntent)
        wakeupIntent.cancel()
    }

    fun cancelAll(context: Context) {
        cancel(context, 1L)
        cancel(context, 2L)
    }

    fun reschedule(context: Context) {
        val store = LocalStudyStore(context)
        val weekday = store.weekdaySleepSchedule()
        val weekend = store.weekendSleepSchedule()
        schedule(context, weekday)
        schedule(context, weekend)
    }

    private fun pendingIntent(
        context: Context,
        scheduleId: Long,
        isWakeup: Boolean,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(context, SleepReminderReceiver::class.java).apply {
            action = if (isWakeup) ACTION_WAKEUP_REMINDER else ACTION_SLEEP_REMINDER
            data = Uri.parse("studymode://sleep/reminder/$scheduleId/$isWakeup")
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_IS_WAKEUP, isWakeup)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun safeNextOccurrence(
        activeDays: Set<Int>,
        targetMinute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        if (activeDays.isEmpty()) return 0L
        for (offset in 0..14) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (cal.get(Calendar.DAY_OF_WEEK) in activeDays) {
                cal.set(Calendar.HOUR_OF_DAY, targetMinute / 60)
                cal.set(Calendar.MINUTE, targetMinute % 60)
                if (cal.timeInMillis > nowMillis) {
                    return cal.timeInMillis
                }
            }
        }
        return 0L
    }

    fun postReminder(context: Context, schedule: SleepSchedule, isWakeup: Boolean = false) {
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
                    "Sleep Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }

        val contentIntent = PendingIntent.getActivity(
            context,
            schedule.id.toInt(),
            Intent(context, MainActivity::class.java)
                .setData(Uri.parse("studymode://open/sleep"))
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notificationId = if (isWakeup) {
            if (schedule.id == 2L) REQ_WAKEUP_WEEKEND else REQ_WAKEUP_WEEKDAY
        } else {
            if (schedule.id == 2L) REQ_BEDTIME_WEEKEND else REQ_BEDTIME_WEEKDAY
        }

        val notification = if (isWakeup) {
            val wakeupFormatted = formatClockTime12h(schedule.wakeupMinute)
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_studymode)
                .setContentTitle("Time to wake up!")
                .setContentText("Good morning! Your scheduled wake-up time is $wakeupFormatted.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        } else {
            val bedtimeFormatted = formatClockTime12h(schedule.bedtimeMinute)
            val title = if (schedule.reminderAdvanceMinutes > 0) {
                "Bedtime in ${schedule.reminderAdvanceMinutes} minutes"
            } else {
                "Time for Bed"
            }
            NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_studymode)
                .setContentTitle(title)
                .setContentText("Wind down and get ready for sleep. Target bedtime is $bedtimeFormatted.")
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        }

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun postTestReminder(context: Context, schedule: SleepSchedule) {
        postReminder(context, schedule, isWakeup = false)
    }
}

class SleepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(SleepAlarms.EXTRA_SCHEDULE_ID, 1L)
        val isWakeup = intent.getBooleanExtra(SleepAlarms.EXTRA_IS_WAKEUP, false)
        val store = LocalStudyStore(context)
        val schedule = if (scheduleId == 2L) store.weekendSleepSchedule() else store.weekdaySleepSchedule()

        if (schedule.enabled) {
            if (isWakeup && schedule.wakeupReminderEnabled) {
                SleepAlarms.postReminder(context, schedule, isWakeup = true)
                SleepAlarms.schedule(context, schedule)
            } else if (!isWakeup && schedule.reminderEnabled) {
                SleepAlarms.postReminder(context, schedule, isWakeup = false)
                SleepAlarms.schedule(context, schedule)
            }
        }
    }
}

internal fun formatClockTime12h(minuteOfDay: Int): String {
    val hour = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val period = if (hour >= 12) "PM" else "AM"
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return "%d:%02d %s".format(hour12, minute, period)
}
