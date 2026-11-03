package com.studymode.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

class AppBlockedActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Cancel any pending notification
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID_FULLSCREEN)
        } catch (_: Exception) {}

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        try {
            val store = LocalStudyStore(this)
            if (store.isMindfulFrictionEnabled()) {
                val appLabel = intent?.getStringExtra(EXTRA_APP_LABEL) ?: "This app"
                val reason = intent?.getStringExtra(EXTRA_REASON) ?: "Blocked by StudyMode"
                val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()

                val frictionView = MindfulFrictionOverlayView(
                    context = this,
                    appLabel = appLabel,
                    packageName = packageName,
                    reason = reason,
                    store = store,
                    onCloseApp = { goHome() },
                    onUnlockMinutes = { minutes ->
                        val now = System.currentTimeMillis()
                        val focusTimer = store.activeTimer()
                        val isFocusActive = focusTimer?.let { it.running && it.remainingAt(now) > 0 } == true
                        if (isFocusActive && store.focusOnlyBlockingEnabled()) {
                            Toast.makeText(this, "Focus Session is active. App remains blocked.", Toast.LENGTH_SHORT).show()
                        } else {
                            store.pauseBlockingUntil(now + minutes * 60_000L)
                            Toast.makeText(this, "Unlocked $appLabel for $minutes minutes", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    },
                    onManageSettings = {
                        val mainIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(mainIntent)
                        finish()
                    }
                )
                setContentView(frictionView)
            } else {
                setContentView(R.layout.layout_app_blocked_overlay)
                setupUI()
            }
        } catch (e: Exception) {
            android.util.Log.e("StudyMode", "Failed in AppBlockedActivity.onCreate", e)
            goHome()
        }
    }

    override fun onDestroy() {
        val root = findViewById<View>(android.R.id.content)
        (root as? android.view.ViewGroup)?.let { vg ->
            for (i in 0 until vg.childCount) {
                (vg.getChildAt(i) as? MindfulFrictionOverlayView)?.cleanup()
            }
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            setupUI()
        } catch (e: Exception) {
            android.util.Log.e("StudyMode", "Failed in AppBlockedActivity.onNewIntent", e)
        }
    }

    private fun setupUI() {
        val appLabel = intent?.getStringExtra(EXTRA_APP_LABEL) ?: "This app"
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "Blocked by StudyMode"
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val store = LocalStudyStore(this)

        // 1. App Icon
        val imgIcon = findViewById<ImageView>(R.id.img_app_icon)
        if (packageName.isNotBlank()) {
            try {
                val iconDrawable = packageManager.getApplicationIcon(packageName)
                imgIcon?.setImageDrawable(iconDrawable)
            } catch (_: Exception) {
                imgIcon?.setImageResource(R.drawable.ic_studymode)
            }
        } else {
            imgIcon?.setImageResource(R.drawable.ic_studymode)
        }

        // 2. Reason banner
        findViewById<TextView>(R.id.text_reason)?.text = reason

        // 3. Usage & Limit Info
        val dailyLimit = if (packageName.isNotBlank()) store.appDailyLimit(packageName) else null
        val usageMillis = if (packageName.isNotBlank()) todayAppUsageMillis(this, packageName) else 0L
        val usedMinutes = (usageMillis / 60_000L).toInt()

        val textTitleLimit = findViewById<TextView>(R.id.text_app_title_limit)
        val textSpentToday = findViewById<TextView>(R.id.text_spent_today)
        val textLimitLeft = findViewById<TextView>(R.id.text_limit_left)
        val progressUsage = findViewById<ProgressBar>(R.id.progress_usage)

        val spentFormatted = if (usedMinutes >= 60) {
            "${usedMinutes / 60}h ${usedMinutes % 60}m"
        } else {
            "${usedMinutes}m"
        }
        textSpentToday?.text = "$spentFormatted Spent today"

        if (dailyLimit != null && dailyLimit.minutes > 0) {
            val limitMins = dailyLimit.minutes
            textTitleLimit?.text = "$appLabel • ${limitMins}m limit"
            val leftMins = (limitMins - usedMinutes).coerceAtLeast(0)
            textLimitLeft?.text = if (leftMins > 0) "${leftMins}m Limit left" else "Limit reached"
            val percent = ((usedMinutes.toFloat() / limitMins.toFloat()) * 100).toInt().coerceIn(0, 100)
            progressUsage?.progress = percent
        } else {
            textTitleLimit?.text = "$appLabel • Blocked App"
            textLimitLeft?.text = "Blocked App"
            progressUsage?.progress = 100
        }

        // 4. Close App button (e.g. "Close Chess" / "Close YouTube")
        val btnClose = findViewById<Button>(R.id.btn_close_app)
        btnClose?.text = "Close $appLabel"
        btnClose?.setOnClickListener {
            goHome()
        }

        // 5. Manage / Turn Off button
        findViewById<Button>(R.id.btn_turn_off)?.setOnClickListener {
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
        }

        // 6. Quick extension buttons
        fun grantQuickSession(minutes: Int) {
            val now = System.currentTimeMillis()
            val focusTimer = store.activeTimer()
            val isFocusActive = focusTimer?.let { it.running && it.remainingAt(now) > 0 } == true

            if (isFocusActive && store.focusOnlyBlockingEnabled()) {
                Toast.makeText(this, "Focus Session is active. App remains blocked.", Toast.LENGTH_SHORT).show()
                return
            }

            store.pauseBlockingUntil(now + minutes * 60_000L)
            Toast.makeText(this, "Unlocked $appLabel for $minutes minutes", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<Button>(R.id.btn_quick_2m)?.setOnClickListener { grantQuickSession(2) }
        findViewById<Button>(R.id.btn_quick_5m)?.setOnClickListener { grantQuickSession(5) }
        findViewById<Button>(R.id.btn_quick_10m)?.setOnClickListener { grantQuickSession(10) }
        findViewById<Button>(R.id.btn_quick_20m)?.setOnClickListener { grantQuickSession(20) }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_APP_LABEL = "extra_app_label"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val NOTIFICATION_ID_FULLSCREEN = 3002
        private const val CHANNEL_ID_FULLSCREEN = "app_block_fullscreen_channel"

        fun start(context: Context, appLabel: String, reason: String, packageName: String = "") {
            try {
                val intent = Intent(context, AppBlockedActivity::class.java).apply {
                    putExtra(EXTRA_APP_LABEL, appLabel)
                    putExtra(EXTRA_REASON, reason)
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.w("StudyMode", "Failed to start AppBlockedActivity directly", e)
            }
        }

        fun showNotificationFullScreenIntent(context: Context, appLabel: String, reason: String, packageName: String = "") {
            try {
                val intent = Intent(context, AppBlockedActivity::class.java).apply {
                    putExtra(EXTRA_APP_LABEL, appLabel)
                    putExtra(EXTRA_REASON, reason)
                    putExtra(EXTRA_PACKAGE_NAME, packageName)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    3001,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
                )

                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    if (manager.getNotificationChannel(CHANNEL_ID_FULLSCREEN) == null) {
                        val channel = android.app.NotificationChannel(
                            CHANNEL_ID_FULLSCREEN,
                            "App Block Fullscreen Alert",
                            NotificationManager.IMPORTANCE_HIGH,
                        ).apply {
                            description = "Full screen alert to display blocked screen over apps"
                            setSound(null, null)
                            enableVibration(false)
                        }
                        manager.createNotificationChannel(channel)
                    }
                }

                val notification = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID_FULLSCREEN)
                    .setSmallIcon(R.drawable.ic_studymode)
                    .setContentTitle("$appLabel is paused")
                    .setContentText(reason)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                    .setFullScreenIntent(pendingIntent, true)
                    .setAutoCancel(true)
                    .build()

                manager.notify(NOTIFICATION_ID_FULLSCREEN, notification)
            } catch (e: Exception) {
                android.util.Log.w("StudyMode", "Failed to show full screen intent notification", e)
            }
        }
    }
}
