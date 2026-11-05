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
import kotlin.math.hypot

/**
 * Option B: Mindful Dual-Stat Floating HUD Pill.
 *
 * Displays a non-intrusive, draggable floating badge directly over YouTube Shorts,
 * Instagram Reels, Snapchat Spotlight, and Facebook Reels.
 *
 * Shows:
 * - Real-time reels/shorts swiped count in current session
 * - Active elapsed time in current session (e.g. "⚡ 12 Reels • 3m")
 * - Dynamic awareness outline color (Green -> Amber -> Red Alert)
 * - Persists today's total scrolls into LocalStudyStore
 * - Smooth touch dragging across the screen
 */
object FloatingScrollCounterOverlayManager {

    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var isShowing: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var sessionScrollCount: Int = 0
    @Volatile private var sessionStartTimeMillis: Long = 0L
    @Volatile private var pausedElapsedSeconds: Long = 0L
    @Volatile private var pausedAtMillis: Long = 0L
    @Volatile private var wasScreenOffPause: Boolean = false
    @Volatile private var isPaused: Boolean = false
    private var lastScrollEventMillis: Long = 0L
    private var currentPlatform: String = ""

    // Drag positions (persisted during session)
    private var savedPosX: Int = -1
    private var savedPosY: Int = -1

    // View references
    private var countTextView: TextView? = null
    private var iconImageView: ImageView? = null
    private var bgDrawable: GradientDrawable? = null

    // 1-second ticker for active elapsed time
    private val timeTickerRunnable = object : Runnable {
        override fun run() {
            if (isShowing && overlayView != null) {
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

        val now = System.currentTimeMillis()

        // Debounce swipe events (min 1200ms between reels)
        var countIncremented = false
        if (isScrollEvent) {
            if (now - lastScrollEventMillis >= 1200L) {
                lastScrollEventMillis = now
                sessionScrollCount++
                store.incrementTodayReelsScrolledCount()
                countIncremented = true

                // Check scroll limit
                val limit = store.getShortsScrollLimit()
                if (limit in 1..sessionScrollCount) {
                    onLimitReached?.invoke()
                    resetAndHide(context)
                    return
                }
            }
        }

        mainHandler.post {
            // Restore from pause: preserves session scroll count & resumes timer exactly where it left off
            if (isPaused || pausedElapsedSeconds > 0L) {
                val nowCurrent = System.currentTimeMillis()
                // Expire only if user left to a completely different app for > 5 minutes AND not a screen off
                if (!wasScreenOffPause && pausedAtMillis > 0L && (nowCurrent - pausedAtMillis > 5 * 60 * 1000L)) {
                    sessionScrollCount = 0
                    sessionStartTimeMillis = 0L
                    pausedElapsedSeconds = 0L
                } else if (pausedElapsedSeconds > 0L) {
                    sessionStartTimeMillis = nowCurrent - (pausedElapsedSeconds * 1000L)
                    pausedElapsedSeconds = 0L
                }
                isPaused = false
                pausedAtMillis = 0L
                wasScreenOffPause = false
            }

            if (!isShowing || overlayView == null || currentPlatform != platformName) {
                currentPlatform = platformName
                if (!isShowing) {
                    if (sessionScrollCount <= 0) {
                        sessionScrollCount = if (countIncremented) 2 else 1
                        if (sessionScrollCount == 1 && !countIncremented) {
                            store.incrementTodayReelsScrolledCount()
                        }
                    }
                    if (sessionStartTimeMillis == 0L) {
                        sessionStartTimeMillis = now
                    }
                }
                showOverlay(context, platformName)
            } else {
                updateTextAndColor()
            }
        }
    }

    private fun showOverlay(context: Context, platformName: String) {
        dismissInternal(context)
        if (sessionStartTimeMillis == 0L) {
            sessionStartTimeMillis = System.currentTimeMillis()
        }
        if (sessionScrollCount <= 0) {
            sessionScrollCount = 1
        }

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
            // Matches modern short-form UX and leaves right-side action buttons completely clear
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
                    setStroke((1.5f * density).toInt(), Color.parseColor("#80D957"))
                }
                bgDrawable = gd
                background = gd
                elevation = 10 * density
            }

            // Platform Icon
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

            // Dual Stat Text: "12 Shorts • 3m"
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
                                val elapsedSec = if (sessionStartTimeMillis > 0L) ((System.currentTimeMillis() - sessionStartTimeMillis) / 1000).coerceAtLeast(0) else 0L
                                val timeText = formatTime(elapsedSec)
                                Toast.makeText(
                                    targetContext,
                                    "⚡ $sessionScrollCount short videos watched in $timeText.",
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
            android.util.Log.i("StudyMode", "FloatingScrollCounter: Displayed HUD for $platformName, count=$sessionScrollCount")

            // Start real-time ticker
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
        val count = sessionScrollCount
        val elapsedSec = if (sessionStartTimeMillis > 0L) {
            ((System.currentTimeMillis() - sessionStartTimeMillis) / 1000).coerceAtLeast(0)
        } else {
            0L
        }
        val timeText = formatTime(elapsedSec)

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

    private fun formatTime(seconds: Long): String {
        return if (seconds < 60) {
            "${seconds}s"
        } else {
            val mins = seconds / 60
            val secs = seconds % 60
            if (secs > 0 && mins < 10) {
                "${mins}m ${secs}s"
            } else {
                "${mins}m"
            }
        }
    }

    fun pause(context: Context, isScreenOff: Boolean = false) {
        mainHandler.post {
            if (sessionStartTimeMillis > 0L) {
                pausedElapsedSeconds = ((System.currentTimeMillis() - sessionStartTimeMillis) / 1000L).coerceAtLeast(1L)
            }
            isPaused = true
            pausedAtMillis = System.currentTimeMillis()
            if (isScreenOff) {
                wasScreenOffPause = true
            }
            dismissInternal(context)
        }
    }

    fun hide(context: Context, resetSession: Boolean = false) {
        if (resetSession) {
            resetAndHide(context)
        } else {
            pause(context, isScreenOff = false)
        }
    }

    fun resetAndHide(context: Context) {
        mainHandler.post {
            dismissInternal(context)
            sessionScrollCount = 0
            sessionStartTimeMillis = 0L
            pausedElapsedSeconds = 0L
            pausedAtMillis = 0L
            wasScreenOffPause = false
            isPaused = false
        }
    }

    private fun dismissInternal(context: Context) {
        mainHandler.removeCallbacks(timeTickerRunnable)
        overlayView?.let { view ->
            try {
                val wm = (if (context is AccessibilityService) context else (context.applicationContext ?: context))
                    .getSystemService(Context.WINDOW_SERVICE) as WindowManager
                if (view.isAttachedToWindow) {
                    wm.removeView(view)
                }
            } catch (_: Exception) {
                try {
                    val appWm = context.applicationContext?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    if (view.isAttachedToWindow) {
                        appWm?.removeView(view)
                    }
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
