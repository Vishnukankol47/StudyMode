package com.studymode.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Manages the sleek floating HUD overlay displayed when Shorts or Reels are intercepted.
 * Auto-dismisses in 2 seconds while returning the user to the constructive part of the app.
 */
object ShortsBlockOverlayManager {
    @Volatile
    private var overlayView: View? = null

    @Volatile
    private var isShowing: Boolean = false

    private var lastShownMillis: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null

    fun show(context: Context, platformName: String) {
        val now = System.currentTimeMillis()
        if (now - lastShownMillis < 2500L) {
            return
        }
        lastShownMillis = now

        mainHandler.post {
            if (!Settings.canDrawOverlays(context)) {
                // Fallback to stylized Toast if overlay permission is not granted
                Toast.makeText(
                    context,
                    "🚫 $platformName Blocked!\nMindless scrolling prevented.",
                    Toast.LENGTH_SHORT
                ).show()
                return@post
            }

            dismissInternal(context)

            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val density = context.resources.displayMetrics.density

                // Container layout (Sleek dark card with neon red/gold accent)
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val padH = (16 * density).toInt()
                    val padV = (12 * density).toInt()
                    setPadding(padH, padV, padH, padV)

                    val bg = GradientDrawable().apply {
                        cornerRadius = 24 * density
                        setColor(Color.parseColor("#E6121412"))
                        setStroke((1.5f * density).toInt(), Color.parseColor("#4DE53935"))
                    }
                    background = bg
                    elevation = 16 * density
                }

                // Mascot / Icon
                val iconView = ImageView(context).apply {
                    val iconSize = (34 * density).toInt()
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                        marginEnd = (12 * density).toInt()
                    }
                    val resId = if (platformName.contains("YouTube", ignoreCase = true)) {
                        R.drawable.ic_short_video
                    } else {
                        R.drawable.ic_reel_video
                    }
                    setImageResource(resId)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
                container.addView(iconView)

                // Text column
                val textCol = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val titleView = TextView(context).apply {
                    text = "$platformName Blocked!"
                    setTextColor(Color.parseColor("#FFFFFF"))
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                textCol.addView(titleView)

                val subtitleView = TextView(context).apply {
                    text = "Mindless scrolling prevented • Focus protected"
                    setTextColor(Color.parseColor("#A5B2A5"))
                    textSize = 11f
                }
                textCol.addView(subtitleView)

                container.addView(textCol)

                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (64 * density).toInt()
                    windowAnimations = android.R.style.Animation_Toast
                }

                windowManager.addView(container, params)
                overlayView = container
                isShowing = true

                // Auto dismiss after 2.2 seconds
                val dismissTask = Runnable {
                    dismissInternal(context)
                }
                dismissRunnable = dismissTask
                mainHandler.postDelayed(dismissTask, 2200L)

            } catch (e: Exception) {
                android.util.Log.e("StudyMode", "Failed to show ShortsBlockOverlay", e)
            }
        }
    }

    private fun dismissInternal(context: Context) {
        dismissRunnable?.let { mainHandler.removeCallbacks(it) }
        dismissRunnable = null

        overlayView?.let { view ->
            try {
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {}
        }
        overlayView = null
        isShowing = false
    }
}
