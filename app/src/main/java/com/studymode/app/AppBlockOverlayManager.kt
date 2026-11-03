package com.studymode.app

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Manages the full-screen bottom-sheet overlay window displayed directly over blocked apps.
 *
 * Bottom sheet overlay blocker for foreground distractions,
 * displaying the exact same translucent bottom sheet over blocked apps with app stats,
 * quick extension options, and a prominent "Close App" action button.
 */
object AppBlockOverlayManager {
    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var isShowing: Boolean = false

    @Volatile
    private var currentlyBlockedPackage: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isShowing(): Boolean = isShowing

    fun currentBlockedPackage(): String? = currentlyBlockedPackage

    fun show(
        context: Context,
        appLabel: String,
        reason: String,
    ) {
        show(context, appLabel, reason, "")
    }

    fun show(
        context: Context,
        appLabel: String,
        reason: String,
        packageName: String,
    ) {
        val hasOverlayPermission = Settings.canDrawOverlays(context)
        val accessibilityService = AppBlockingAccessibilityService.activeInstance

        val canShowOverlay = hasOverlayPermission || accessibilityService != null
        if (!canShowOverlay) {
            android.util.Log.w("StudyMode", "AppBlockOverlayManager: neither overlay nor accessibility available, falling back to Activity")
            try {
                AppBlockedActivity.start(context, appLabel, reason, packageName)
            } catch (e: Exception) {
                android.util.Log.e("StudyMode", "Failed to start fallback activity", e)
            }
            return
        }

        // If already showing for this exact package, don't recreate or flicker
        if (isShowing && currentlyBlockedPackage == packageName && overlayView != null) {
            return
        }

        mainHandler.post {
            try {
                val (targetContext, layoutType) = when {
                    hasOverlayPermission -> {
                        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        } else {
                            @Suppress("DEPRECATION")
                            WindowManager.LayoutParams.TYPE_PHONE
                        }
                        Pair(context.applicationContext, type)
                    }
                    accessibilityService != null -> {
                        Pair(accessibilityService, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                    }
                    else -> {
                        Pair(context.applicationContext, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    }
                }

                val windowManager = targetContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@post

                // Dismiss existing if already showing for a different app
                if (overlayView != null) {
                    if (overlayView is MindfulFrictionOverlayView) {
                        (overlayView as MindfulFrictionOverlayView).cleanup()
                    }
                    try {
                        windowManager.removeView(overlayView)
                    } catch (_: Exception) {}
                    overlayView = null
                    isShowing = false
                    currentlyBlockedPackage = null
                }

                val store = LocalStudyStore(targetContext)
                val view: View = if (store.isMindfulFrictionEnabled()) {
                    MindfulFrictionOverlayView(
                        context = targetContext,
                        appLabel = appLabel,
                        packageName = packageName,
                        reason = reason,
                        store = store,
                        onCloseApp = {
                            dismiss(targetContext)
                            val service = AppBlockingAccessibilityService.activeInstance
                            if (service != null) {
                                try {
                                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                                } catch (_: Exception) {
                                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                        addCategory(Intent.CATEGORY_HOME)
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                    }
                                    targetContext.startActivity(homeIntent)
                                }
                            } else {
                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                }
                                targetContext.startActivity(homeIntent)
                            }
                        },
                        onUnlockMinutes = { minutes ->
                            val now = System.currentTimeMillis()
                            val focusTimer = store.activeTimer()
                            val isFocusActive = focusTimer?.let { it.running && it.remainingAt(now) > 0 } == true

                            if (isFocusActive && store.focusOnlyBlockingEnabled()) {
                                Toast.makeText(targetContext, "Focus Session is active. App remains blocked.", Toast.LENGTH_SHORT).show()
                            } else {
                                store.pauseBlockingUntil(now + minutes * 60_000L)
                                Toast.makeText(targetContext, "Unlocked $appLabel for $minutes minutes", Toast.LENGTH_SHORT).show()
                                dismiss(targetContext)
                            }
                        },
                        onManageSettings = {
                            dismiss(targetContext)
                            val mainIntent = Intent(targetContext, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            targetContext.startActivity(mainIntent)
                        }
                    )
                } else {
                    val themedContext = ContextThemeWrapper(targetContext, R.style.Theme_StudyMode)
                    val layoutInflater = LayoutInflater.from(themedContext)
                    val legacyView = layoutInflater.inflate(R.layout.layout_app_blocked_overlay, null)

                    // 1. App Icon
                    val imgIcon = legacyView.findViewById<ImageView>(R.id.img_app_icon)
                    if (packageName.isNotBlank()) {
                        try {
                            val iconDrawable = targetContext.packageManager.getApplicationIcon(packageName)
                            imgIcon?.setImageDrawable(iconDrawable)
                        } catch (_: Exception) {
                            imgIcon?.setImageResource(R.drawable.ic_studymode)
                        }
                    } else {
                        imgIcon?.setImageResource(R.drawable.ic_studymode)
                    }

                    // 2. Reason banner
                    legacyView.findViewById<TextView>(R.id.text_reason)?.text = reason

                    // 3. Usage & Limit Info
                    val dailyLimit = store.appDailyLimit(packageName)
                    val usageMillis = todayAppUsageMillis(targetContext, packageName)
                    val usedMinutes = (usageMillis / 60_000L).toInt()

                    val textTitleLimit = legacyView.findViewById<TextView>(R.id.text_app_title_limit)
                    val textSpentToday = legacyView.findViewById<TextView>(R.id.text_spent_today)
                    val textLimitLeft = legacyView.findViewById<TextView>(R.id.text_limit_left)
                    val progressUsage = legacyView.findViewById<ProgressBar>(R.id.progress_usage)

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

                    // 4. Close App button
                    val btnClose = legacyView.findViewById<Button>(R.id.btn_close_app)
                    btnClose?.text = "Close $appLabel"
                    btnClose?.setOnClickListener {
                        dismiss(targetContext)
                        val service = AppBlockingAccessibilityService.activeInstance
                        if (service != null) {
                            try {
                                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                            } catch (_: Exception) {
                                val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                    addCategory(Intent.CATEGORY_HOME)
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                                }
                                targetContext.startActivity(homeIntent)
                            }
                        } else {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                            }
                            targetContext.startActivity(homeIntent)
                        }
                    }

                    // 5. Manage / Turn Off button
                    legacyView.findViewById<Button>(R.id.btn_turn_off)?.setOnClickListener {
                        dismiss(targetContext)
                        val mainIntent = Intent(targetContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        targetContext.startActivity(mainIntent)
                    }

                    // 6. Quick minute extension buttons
                    fun grantQuickSession(minutes: Int) {
                        val now = System.currentTimeMillis()
                        val focusTimer = store.activeTimer()
                        val isFocusActive = focusTimer?.let { it.running && it.remainingAt(now) > 0 } == true

                        if (isFocusActive && store.focusOnlyBlockingEnabled()) {
                            Toast.makeText(targetContext, "Focus Session is active. App remains blocked.", Toast.LENGTH_SHORT).show()
                            return
                        }

                        store.pauseBlockingUntil(now + minutes * 60_000L)
                        Toast.makeText(targetContext, "Unlocked $appLabel for $minutes minutes", Toast.LENGTH_SHORT).show()
                        dismiss(targetContext)
                    }

                    legacyView.findViewById<Button>(R.id.btn_quick_2m)?.setOnClickListener { grantQuickSession(2) }
                    legacyView.findViewById<Button>(R.id.btn_quick_5m)?.setOnClickListener { grantQuickSession(5) }
                    legacyView.findViewById<Button>(R.id.btn_quick_10m)?.setOnClickListener { grantQuickSession(10) }
                    legacyView.findViewById<Button>(R.id.btn_quick_20m)?.setOnClickListener { grantQuickSession(20) }

                    legacyView
                }

                // 7. Window Layout Params
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.FILL
                }

                // Consume background touches so blocked app never receives touches
                view.setOnClickListener {
                    // Touch on dimmed background stays on blocker
                }

                windowManager.addView(view, params)
                overlayView = view
                isShowing = true
                currentlyBlockedPackage = packageName
                android.util.Log.i("StudyMode", "AppBlockOverlayManager: Displayed bottom sheet overlay for $appLabel ($packageName) with layoutType=$layoutType")
            } catch (e: Exception) {
                android.util.Log.e("StudyMode", "Failed to add overlay view", e)
                try {
                    AppBlockedActivity.start(context, appLabel, reason, packageName)
                } catch (fallbackError: Exception) {
                    android.util.Log.e("StudyMode", "Fallback activity start also failed", fallbackError)
                }
            }
        }
    }

    fun dismiss(context: Context) {
        val targetContext = AppBlockingAccessibilityService.activeInstance ?: context.applicationContext
        mainHandler.post {
            try {
                val windowManager = targetContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    ?: return@post

                overlayView?.let {
                    if (it is MindfulFrictionOverlayView) {
                        it.cleanup()
                    }
                    try {
                        windowManager.removeView(it)
                    } catch (_: Exception) {}
                    overlayView = null
                    isShowing = false
                    currentlyBlockedPackage = null
                    android.util.Log.i("StudyMode", "AppBlockOverlayManager: Overlay dismissed")
                }
            } catch (e: Exception) {
                android.util.Log.w("StudyMode", "Failed to remove overlay view", e)
            }
        }
    }
}
