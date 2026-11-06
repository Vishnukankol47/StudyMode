package com.studymode.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar
import kotlin.math.hypot

/**
 * Option B: Mindful Dual-Stat Floating HUD Pill.
 *
 * Displays a non-intrusive, draggable floating badge directly over YouTube Shorts,
 * Instagram Reels, Snapchat Spotlight, and Facebook Reels.
 *
 * Shows:
 * - Real-time reels/shorts swiped count for the current calendar day (resets at 12:00 AM midnight)
 * - Actual active watch time for the current calendar day (formatted as e.g. "12 Reels • 3m", "25 Reels • 1 hr 12 min")
 * - Freezes immediately on screen-off / app-switch without accumulating sleep or idle hours
 * - Dynamic awareness outline color (Green -> Amber -> Red Alert)
 * - Smooth touch dragging across the screen with saved position
 */
object FloatingScrollCounterOverlayManager {

    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var isShowing: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // Day & Active Watch Tracking (Resets at 12:00 AM Midnight)
    @Volatile private var activeDayKey: String = ""
    @Volatile private var todayDisplayCount: Int = 0
    @Volatile private var todayActiveWatchSeconds: Long = 0L
    @Volatile private var lastSecondsSyncTime: Long = 0L
    @Volatile private var currentStore: LocalStudyStore? = null

    @Volatile private var lastScrollEventMillis: Long = 0L
    @Volatile private var currentPlatform: String = ""

    // Drag positions (persisted across sessions)
    private var savedPosX: Int = -1
    private var savedPosY: Int = -1

    // View references
    private var countTextView: TextView? = null
    private var iconImageView: ImageView? = null
    private var bgDrawable: GradientDrawable? = null

    private fun getTodayDayKey(): String = Calendar.getInstance().let {
        "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun checkDayRollover(store: LocalStudyStore) {
        val today = getTodayDayKey()
        if (activeDayKey.isEmpty()) {
            activeDayKey = today
            store.resetTodayReelsIfNewDay()
            if (currentPlatform.isNotEmpty()) {
                todayDisplayCount = store.todayPlatformScrolledCount(currentPlatform)
                todayActiveWatchSeconds = store.todayPlatformWatchTimeSeconds(currentPlatform)
            }
            lastSecondsSyncTime = System.currentTimeMillis()
        } else if (today != activeDayKey) {
            // 12:00 AM Midnight Rollover!
            activeDayKey = today
            store.resetTodayReelsIfNewDay()
            todayDisplayCount = 0
            todayActiveWatchSeconds = 0L
            lastSecondsSyncTime = System.currentTimeMillis()
            updateTextAndColor()
        }
    }

    // 1-second ticker for active elapsed time (only runs while viewing Shorts/Reels)
    private val timeTickerRunnable = object : Runnable {
        override fun run() {
            if (isShowing && overlayView != null) {
                currentStore?.let { store ->
                    checkDayRollover(store)
                }
                todayActiveWatchSeconds++

                // Periodically sync active watch time every 5 seconds
                val now = System.currentTimeMillis()
                if (now - lastSecondsSyncTime >= 5000L) {
                    val delta = ((now - lastSecondsSyncTime) / 1000L).coerceAtLeast(1L)
                    lastSecondsSyncTime = now
                    if (currentPlatform.isNotEmpty()) {
                        currentStore?.addTodayPlatformWatchTimeSeconds(currentPlatform, delta)
                    }
                }

                updateTextAndColor()
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    fun updateCounter(
        context: Context,
        platformName: String,
        isScrollEvent: Boolean,
        store: LocalStudyStore,
        onLimitReached: (() -> Unit)? = null,
    ) {
        if (!store.isFloatingScrollCounterEnabled()) {
            resetAndHide(context)
            return
        }

        // TYPE_ACCESSIBILITY_OVERLAY does not require Settings.canDrawOverlays(context).
        // Only require overlay permission if context is NOT an AccessibilityService.
        if (context !is AccessibilityService && !Settings.canDrawOverlays(context)) {
            return
        }

        currentStore = store

        val now = System.currentTimeMillis()

        mainHandler.post {
            checkDayRollover(store)

            val isPlatformChanged = currentPlatform.isNotEmpty() && currentPlatform != platformName
            if (isPlatformChanged) {
                flushActiveSecondsToStore()
            }

            val needsShow = !isShowing || overlayView == null || isPlatformChanged
            currentPlatform = platformName

            if (needsShow) {
                todayDisplayCount = store.todayPlatformScrolledCount(platformName)
                todayActiveWatchSeconds = store.todayPlatformWatchTimeSeconds(platformName)
                lastSecondsSyncTime = System.currentTimeMillis()

                // If user just entered Reels/Shorts and today's count is 0,
                // count the first video currently on screen as 1!
                if (todayDisplayCount <= 0) {
                    store.incrementTodayPlatformScrolledCount(platformName)
                    todayDisplayCount = 1
                }
            }

            if (isScrollEvent) {
                store.incrementTodayPlatformScrolledCount(platformName)
                todayDisplayCount = store.todayPlatformScrolledCount(platformName)

                // Check scroll limit
                val limit = store.getShortsScrollLimit()
                if (limit in 1..todayDisplayCount) {
                    onLimitReached?.invoke()
                    resetAndHide(context)
                    return@post
                }
            }

            if (needsShow) {
                showOverlay(context, platformName)
            } else {
                updateTextAndColor()
            }
        }
    }

    private fun showOverlay(context: Context, platformName: String) {
        if (isShowing) {
            flushActiveSecondsToStore()
        }
        dismissInternal(context)

        try {
            val (targetContext, windowType) = if (context is AccessibilityService) {
                Pair(context, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
            } else {
                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                Pair(context.applicationContext ?: context, type)
            }

            val windowManager = targetContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val density = targetContext.resources.displayMetrics.density

            // Default position: top-left corner (14dp from left, 64dp from top)
            val posX = if (savedPosX >= 0) savedPosX else (14 * density).toInt()
            val posY = if (savedPosY >= 0) savedPosY else (64 * density).toInt()

            val windowParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = posX
                y = posY
            }

            // Outer Container Layout
            val container = LinearLayout(targetContext).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padH = (12 * density).toInt()
                val padV = (7 * density).toInt()
                setPadding(padH, padV, padH, padV)

                val gd = GradientDrawable().apply {
                    cornerRadius = 20 * density
                    setColor(Color.parseColor("#E6121412"))
                    setStroke((1.5f * density).toInt(), Color.parseColor("#4ADE80"))
                }
                bgDrawable = gd
                background = gd
                elevation = 10 * density
            }

            // Platform Icon (dynamic Shorts vs Reels)
            val iconView = ImageView(targetContext).apply {
                val iconSize = (18 * density).toInt()
                this.layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    marginEnd = (7 * density).toInt()
                }
                val resId = if (platformName.contains("YouTube", ignoreCase = true)) {
                    R.drawable.ic_short_video
                } else {
                    R.drawable.ic_reel_video
                }
                setImageResource(resId)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            iconImageView = iconView
            container.addView(iconView)

            // Dual Stat Text: e.g. "12 Shorts • 15 min" or "18 Reels • 1 hr 12 min"
            val textView = TextView(targetContext).apply {
                this.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                textSize = 12.5f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }
            countTextView = textView
            container.addView(textView)

            // Draggable Touch Listener
            container.setOnTouchListener(object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f
                private var hasMoved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = windowParams.x
                            initialY = windowParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            hasMoved = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (hypot(dx.toDouble(), dy.toDouble()) > 10 * density) {
                                hasMoved = true
                            }
                            windowParams.x = initialX + dx
                            windowParams.y = initialY + dy
                            savedPosX = windowParams.x
                            savedPosY = windowParams.y
                            try {
                                windowManager.updateViewLayout(container, windowParams)
                            } catch (_: Exception) {}
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!hasMoved) {
                                // Tap action: quick summary toast
                                val timeText = formatTime(todayActiveWatchSeconds)
                                val videoUnit = if (todayDisplayCount == 1) "short video" else "short videos"
                                Toast.makeText(
                                    targetContext,
                                    "⚡ $todayDisplayCount $videoUnit watched today ($timeText).",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            return true
                        }
                    }
                    return false
                }
            })

            try {
                windowManager.addView(container, windowParams)
            } catch (e: Exception) {
                android.util.Log.w("StudyMode", "Primary addView failed (${e.message}), trying applicationContext fallback")
                val fallbackContext = context.applicationContext ?: context
                val fallbackWm = fallbackContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowParams.type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                fallbackWm.addView(container, windowParams)
            }

            overlayView = container
            isShowing = true
            currentPlatform = platformName
            android.util.Log.i("StudyMode", "FloatingScrollCounter: Displayed HUD for $platformName, count=$todayDisplayCount")

            // Start real-time active ticker
            mainHandler.removeCallbacks(timeTickerRunnable)
            updateTextAndColor()
            mainHandler.postDelayed(timeTickerRunnable, 1000L)

        } catch (e: Exception) {
            android.util.Log.e("StudyMode", "FloatingScrollCounter: Failed to show HUD", e)
            isShowing = false
            overlayView = null
        }
    }

    private fun updateTextAndColor() {
        val count = todayDisplayCount.coerceAtLeast(0)
        val timeText = formatTime(todayActiveWatchSeconds)

        val isYt = currentPlatform.contains("YouTube", ignoreCase = true)
        val unit = if (isYt) {
            if (count == 1) "Short" else "Shorts"
        } else {
            if (count == 1) "Reel" else "Reels"
        }
        val iconRes = if (isYt) R.drawable.ic_short_video else R.drawable.ic_reel_video
        iconImageView?.setImageResource(iconRes)
        countTextView?.text = "$count $unit • $timeText"

        // Dynamic Color: Green (1-5) -> Amber (6-15) -> Crimson Alert (16+)
        val strokeColor = when {
            count <= 5 -> Color.parseColor("#4ADE80")   // Fresh Green (Mindful)
            count <= 15 -> Color.parseColor("#FBBF24")  // Warning Amber (Slipping)
            else -> Color.parseColor("#EF4444")         // Crimson (Doom-scrolling Alert)
        }

        val density = countTextView?.resources?.displayMetrics?.density ?: 2f
        bgDrawable?.setStroke((1.5f * density).toInt(), strokeColor)
    }

    /**
     * Formats seconds into human-readable text.
     * Shows:
     * - Under 1 min: "45s"
     * - Under 5 min: "3m 25s"
     * - Under 1 hour: "15 min"
     * - 1 hour or more: "1 hr 12 min", "2 hr"
     */
    fun formatTime(seconds: Long): String {
        if (seconds < 60) {
            return "${seconds}s"
        }
        val hours = seconds / 3600
        val remainingSeconds = seconds % 3600
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60

        return if (hours > 0) {
            if (mins > 0) {
                "$hours hr $mins min"
            } else {
                "$hours hr"
            }
        } else {
            if (secs > 0 && mins < 5) {
                "${mins}m ${secs}s"
            } else {
                "${mins} min"
            }
        }
    }

    private fun flushActiveSecondsToStore() {
        val now = System.currentTimeMillis()
        if (lastSecondsSyncTime > 0L && now > lastSecondsSyncTime && currentPlatform.isNotEmpty()) {
            val delta = ((now - lastSecondsSyncTime) / 1000L).coerceAtLeast(0L)
            if (delta > 0L) {
                currentStore?.addTodayPlatformWatchTimeSeconds(currentPlatform, delta)
            }
        }
        lastSecondsSyncTime = System.currentTimeMillis()
    }

    fun pause(context: Context, isScreenOff: Boolean = false) {
        mainHandler.post {
            flushActiveSecondsToStore()
            dismissInternal(context)
            currentPlatform = ""
        }
    }

    fun hide(context: Context, resetSession: Boolean = false) {
        pause(context, isScreenOff = false)
    }

    fun resetAndHide(context: Context) {
        mainHandler.post {
            flushActiveSecondsToStore()
            dismissInternal(context)
            currentPlatform = ""
        }
    }

    private fun dismissInternal(context: Context) {
        mainHandler.removeCallbacks(timeTickerRunnable)
        overlayView?.let { view ->
            try {
                val wm = (if (context is AccessibilityService) context else (context.applicationContext ?: context))
                    .getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    val appWm = context.applicationContext?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    appWm?.removeViewImmediate(view)
                } catch (_: Exception) {}
            }
        }
        overlayView = null
        isShowing = false
        countTextView = null
        iconImageView = null
        bgDrawable = null
    }

    fun isShowing(): Boolean = isShowing
}
