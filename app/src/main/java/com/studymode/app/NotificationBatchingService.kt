package com.studymode.app

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * Intercepts, silences, and batches distracting incoming notifications during
 * Focus Sessions or scheduled digest windows.
 * 
 * Held notifications are safely stored locally in LocalStudyStore and released
 * as a mindful digest upon session completion or at scheduled intervals.
 */
class NotificationBatchingService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        // 1. Never intercept ongoing system processes or foreground services
        if (sbn.isOngoing) return

        val pkg = sbn.packageName ?: return

        // 2. Safeguard system, telephone, and critical emergency services
        if (pkg == packageName ||
            pkg == "android" ||
            pkg == "com.android.systemui" ||
            pkg == "com.google.android.googlequicksearchbox"
        ) {
            return
        }

        // Safeguard default dialer and calls
        try {
            val telecom = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecom?.defaultDialerPackage
            if (pkg == defaultDialer || pkg.contains("dialer") || pkg.contains("telecom")) {
                return
            }
        } catch (_: Exception) {}

        // Never intercept phone calls or emergency alarms
        val category = sbn.notification?.category
        if (category == Notification.CATEGORY_CALL ||
            category == Notification.CATEGORY_ALARM ||
            category == Notification.CATEGORY_ERROR
        ) {
            return
        }

        val store = LocalStudyStore(this)
        if (!store.isNotificationBatchingEnabled()) return

        val now = System.currentTimeMillis()
        if (store.isNotificationBatchingFocusOnly()) {
            val timer = store.activeTimer()
            val isTimerRunning = timer != null && timer.running && timer.remainingAt(now) > 0
            val isZenActive = store.isZenModeActive(now)
            if (!isTimerRunning && !isZenActive) {
                return
            }
        }

        // Extract readable details
        val extras = sbn.notification?.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(100).orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(250).orEmpty()

        val appName = try {
            val pm = packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            pkg
        }

        // Silence & dismiss the heads-up/system notification
        try {
            cancelNotification(sbn.key)
        } catch (e: Exception) {
            Log.w("NotifBatching", "Unable to dismiss notification: ${e.message}")
        }

        // Store into local mindful digest queue
        store.addBatchedNotification(
            packageName = pkg,
            appName = appName,
            title = title.ifBlank { "Notification" },
            text = text,
            key = sbn.key,
            timestampMillis = now,
        )
    }

    companion object {
        fun isNotificationAccessGranted(context: Context): Boolean {
            return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        }

        fun openNotificationAccessSettings(context: Context) {
            try {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                        putExtra(
                            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                            "${context.packageName}/${NotificationBatchingService::class.java.name}"
                        )
                    }
                } else {
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (_: Exception) {
                val fallback = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            }
        }
    }
}
