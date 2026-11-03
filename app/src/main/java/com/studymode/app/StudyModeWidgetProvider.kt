package com.studymode.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Android Home Screen Glance Widget for StudyMode.
 * Displays today's focus time, phone pickups, and reclaimed time, with a one-tap
 * quick action to immediately launch into a focus session.
 */
class StudyModeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.studymode_widget_layout)
        val store = LocalStudyStore(context)

        // 1. Focus duration
        val focusSeconds = store.todayFocusSeconds()
        val focusMinutes = (focusSeconds / 60).toInt()
        val focusText = if (focusMinutes < 60) {
            "${focusMinutes}m"
        } else {
            "${focusMinutes / 60}h ${focusMinutes % 60}m"
        }
        views.setTextViewText(R.id.widget_focus_time, focusText)

        // 2. Pickups
        val pickups = store.todayPickupCount()
        views.setTextViewText(R.id.widget_pickups, pickups.toString())

        // 3. Time Saved
        val timeSavedSummary = calculateTimeSaved(context, store)
        val savedText = "+${timeSavedSummary.todayMinutesSaved}m saved"
        views.setTextViewText(R.id.widget_time_saved, savedText)

        // 4. Tap action to launch directly into focus
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_START_FOCUS_FROM_WIDGET
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            appWidgetId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_btn_start_focus, pendingIntent)
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    companion object {
        const val ACTION_START_FOCUS_FROM_WIDGET = "com.studymode.app.ACTION_START_FOCUS_WIDGET"

        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
                val ids = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, StudyModeWidgetProvider::class.java)
                )
                if (ids != null && ids.isNotEmpty()) {
                    val intent = Intent(context, StudyModeWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (_: Exception) {}
        }
    }
}
