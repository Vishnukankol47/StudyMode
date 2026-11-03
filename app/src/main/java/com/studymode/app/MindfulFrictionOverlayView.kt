package com.studymode.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Pre-Open Mindful Friction Overlay.
 *
 * Intercepts launches of blocked or daily-limited apps with two calm, intentional interventions:
 * 1. Mindful Breathing Circle: Inhale (4s) -> Hold (2s) -> Exhale (4s) -> Rest (1.5s).
 * 2. 10-Second Pause & Reflection: Smooth countdown ring with inspiring focus reflection quotes.
 * 3. Limits & Usage Stats: App time spent today, daily limit progress, and quick extension options.
 *
 * Pinned primary action: "Stay Focused (Return Home)" immediately navigates home.
 * Unlock button remains locked during breathing / countdown to build genuine mindful willpower.
 */
class MindfulFrictionOverlayView(
    context: Context,
    private val appLabel: String,
    private val packageName: String,
    private val reason: String,
    private val store: LocalStudyStore,
    private val onCloseApp: () -> Unit,
    private val onUnlockMinutes: (Int) -> Unit,
    private val onManageSettings: () -> Unit,
) : FrameLayout(context) {

    private enum class FrictionTab { BREATHE, PAUSE, STATS }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTab: FrictionTab = FrictionTab.BREATHE
    private var isBreatheComplete = false
    private var isPauseComplete = false

    // Containers
    private val contentContainer: FrameLayout
    private val tabBreatheBtn: TextView
    private val tabPauseBtn: TextView
    private val tabStatsBtn: TextView
    private val btnUnlockApp: Button

    // Views
    private var breathingView: BreathingCircleView? = null
    private var countdownView: CountdownRingView? = null
    private var statsView: View? = null

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    init {
        // Full screen dark translucent background (StudyMode Ink)
        setBackgroundColor(Color.parseColor("#F5080E0B"))
        isClickable = true
        isFocusable = true

        // Main vertical layout
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setPadding(dp(20), dp(32), dp(20), dp(24))
        }
        addView(rootLayout)

        // -------------------------------------------------------------
        // 1. TOP HEADER (App Icon, Name, Badge, Close)
        // -------------------------------------------------------------
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // App Icon
        val appIcon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                marginEnd = dp(14)
            }
            try {
                if (packageName.isNotBlank()) {
                    setImageDrawable(context.packageManager.getApplicationIcon(packageName))
                } else {
                    setImageResource(R.drawable.ic_studymode)
                }
            } catch (_: Exception) {
                setImageResource(R.drawable.ic_studymode)
            }
        }
        headerLayout.addView(appIcon)

        // App title & badge
        val titleColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val appNameRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val textAppName = TextView(context).apply {
            text = appLabel
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
            paint.isFakeBoldText = true
        }
        appNameRow.addView(textAppName)

        // "Mindful Pause" badge
        val badgePill = TextView(context).apply {
            text = "🌿 Mindful Pause"
            setTextColor(Color.parseColor("#4ADE80"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            paint.isFakeBoldText = true
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#152E20"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#264A35"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8)
            }
        }
        appNameRow.addView(badgePill)
        titleColumn.addView(appNameRow)

        val textSubtitle = TextView(context).apply {
            text = reason.ifBlank { "Restricted app in StudyMode" }
            setTextColor(Color.parseColor("#9AA6A0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, dp(2), 0, 0)
        }
        titleColumn.addView(textSubtitle)
        headerLayout.addView(titleColumn)

        // Manage / Settings button
        val btnManage = TextView(context).apply {
            text = "⚙ Limits"
            setTextColor(Color.parseColor("#9AA6A0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#111C16"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#223B2D"))
            }
            setOnClickListener { onManageSettings() }
        }
        headerLayout.addView(btnManage)
        rootLayout.addView(headerLayout)

        // -------------------------------------------------------------
        // 2. TAB SWITCHER (Deep Breath | 10s Pause | Stats)
        // -------------------------------------------------------------
        val tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(20)
                bottomMargin = dp(12)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F1A13"))
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), Color.parseColor("#1C3325"))
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }

        tabBreatheBtn = createTabButton("🫁 Deep Breath") { selectTab(FrictionTab.BREATHE) }
        tabPauseBtn = createTabButton("⏳ 10s Pause") { selectTab(FrictionTab.PAUSE) }
        tabStatsBtn = createTabButton("📊 Usage Stats") { selectTab(FrictionTab.STATS) }

        tabContainer.addView(tabBreatheBtn)
        tabContainer.addView(tabPauseBtn)
        tabContainer.addView(tabStatsBtn)
        rootLayout.addView(tabContainer)

        // -------------------------------------------------------------
        // 3. INTERACTIVE CONTENT CONTAINER
        // -------------------------------------------------------------
        contentContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(16)
            }
        }
        rootLayout.addView(contentContainer)

        // -------------------------------------------------------------
        // 4. BOTTOM ACTION BUTTONS
        // -------------------------------------------------------------
        val buttonsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Primary: Stay Focused (Close App)
        val btnCloseApp = Button(context).apply {
            text = "🌿 Stay Focused (Close $appLabel)"
            setTextColor(Color.parseColor("#07150C"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            paint.isFakeBoldText = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(50)
            )
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#4ADE80"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener { onCloseApp() }
        }
        buttonsColumn.addView(btnCloseApp)

        // Secondary: Continue to App (Locked until breathing/timer complete)
        btnUnlockApp = Button(context).apply {
            text = "🔒 Take a mindful pause to unlock"
            setTextColor(Color.parseColor("#6A7870"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            paint.isFakeBoldText = true
            isEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
                topMargin = dp(10)
            }
            background = createDisabledUnlockBackground()
            setOnClickListener {
                onUnlockMinutes(5)
            }
        }
        buttonsColumn.addView(btnUnlockApp)
        rootLayout.addView(buttonsColumn)

        // Select initial tab based on user preference
        val initialTab = when (store.mindfulFrictionDefaultTab()) {
            "PAUSE" -> FrictionTab.PAUSE
            "STATS" -> FrictionTab.STATS
            else -> FrictionTab.BREATHE
        }
        selectTab(initialTab)
    }

    private fun createTabButton(title: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = title
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            paint.isFakeBoldText = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun selectTab(tab: FrictionTab) {
        activeTab = tab

        // Highlight active tab
        fun styleTab(btn: TextView, isSelected: Boolean) {
            if (isSelected) {
                btn.setTextColor(Color.parseColor("#4ADE80"))
                btn.background = GradientDrawable().apply {
                    setColor(Color.parseColor("#173322"))
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.parseColor("#2C6141"))
                }
            } else {
                btn.setTextColor(Color.parseColor("#8E9993"))
                btn.background = null
            }
        }

        styleTab(tabBreatheBtn, tab == FrictionTab.BREATHE)
        styleTab(tabPauseBtn, tab == FrictionTab.PAUSE)
        styleTab(tabStatsBtn, tab == FrictionTab.STATS)

        contentContainer.removeAllViews()

        when (tab) {
            FrictionTab.BREATHE -> {
                if (breathingView == null) {
                    val targetBreaths = store.mindfulFrictionBreathsTarget()
                    breathingView = BreathingCircleView(context, targetBreaths) {
                        isBreatheComplete = true
                        updateUnlockButtonState()
                    }
                }
                breathingView?.let {
                    it.startAnimationIfNeeded()
                    contentContainer.addView(it)
                }
            }
            FrictionTab.PAUSE -> {
                if (countdownView == null) {
                    val pauseSeconds = store.mindfulFrictionPauseDurationSeconds()
                    countdownView = CountdownRingView(context, pauseSeconds) {
                        isPauseComplete = true
                        updateUnlockButtonState()
                    }
                }
                countdownView?.let {
                    it.startCountdownIfNeeded()
                    contentContainer.addView(it)
                }
            }
            FrictionTab.STATS -> {
                if (statsView == null) {
                    statsView = createStatsView()
                }
                statsView?.let { contentContainer.addView(it) }
            }
        }

        updateUnlockButtonState()
    }

    private fun updateUnlockButtonState() {
        val canUnlock = when (activeTab) {
            FrictionTab.BREATHE -> isBreatheComplete
            FrictionTab.PAUSE -> isPauseComplete
            FrictionTab.STATS -> true
        }

        if (canUnlock) {
            btnUnlockApp.isEnabled = true
            btnUnlockApp.text = "🔓 Continue to $appLabel (5m pass)"
            btnUnlockApp.setTextColor(Color.parseColor("#4ADE80"))
            btnUnlockApp.background = GradientDrawable().apply {
                setColor(Color.parseColor("#152E20"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#4ADE80"))
            }
        } else {
            btnUnlockApp.isEnabled = false
            btnUnlockApp.text = if (activeTab == FrictionTab.BREATHE) {
                "🔒 Complete breathing to unlock"
            } else {
                "🔒 Wait for timer to finish"
            }
            btnUnlockApp.setTextColor(Color.parseColor("#6A7870"))
            btnUnlockApp.background = createDisabledUnlockBackground()
        }
    }

    private fun createDisabledUnlockBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor("#101C15"))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#1B2E23"))
        }
    }

    // -------------------------------------------------------------
    // STATS & LIMITS VIEW
    // -------------------------------------------------------------
    private fun createStatsView(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0E1A13"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.parseColor("#1B3324"))
            }
        }

        val dailyLimit = store.appDailyLimit(packageName)
        val usageMillis = todayAppUsageMillis(context, packageName)
        val usedMinutes = (usageMillis / 60_000L).toInt()

        val textUsage = TextView(context).apply {
            val formatted = if (usedMinutes >= 60) "${usedMinutes / 60}h ${usedMinutes % 60}m" else "${usedMinutes}m"
            text = "$formatted Spent Today"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
        }
        layout.addView(textUsage)

        val textLimit = TextView(context).apply {
            text = if (dailyLimit != null && dailyLimit.minutes > 0) {
                val left = (dailyLimit.minutes - usedMinutes).coerceAtLeast(0)
                "${dailyLimit.minutes}m daily limit • ${if (left > 0) "${left}m left" else "Limit reached"}"
            } else {
                "App is in your StudyMode Blocklist"
            }
            setTextColor(Color.parseColor("#9AA6A0"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(4), 0, dp(12))
            gravity = Gravity.CENTER
        }
        layout.addView(textLimit)

        // Progress bar
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(8)
            ).apply {
                bottomMargin = dp(20)
            }
            progressDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#4ADE80"))
                cornerRadius = dp(4).toFloat()
            }
            progress = if (dailyLimit != null && dailyLimit.minutes > 0) {
                ((usedMinutes.toFloat() / dailyLimit.minutes.toFloat()) * 100).toInt().coerceIn(0, 100)
            } else {
                100
            }
        }
        layout.addView(progressBar)

        // Quick minute extensions title
        val textQuick = TextView(context).apply {
            text = "Quick Unlocks"
            setTextColor(Color.parseColor("#C0CDC2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            paint.isFakeBoldText = true
            setPadding(0, 0, 0, dp(8))
        }
        layout.addView(textQuick)

        // Quick extension row
        val quickRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        listOf(2, 5, 10, 20).forEach { mins ->
            val btn = Button(context).apply {
                text = "+${mins}m"
                setTextColor(Color.parseColor("#4ADE80"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                paint.isFakeBoldText = true
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    marginEnd = dp(6)
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#152C1E"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#265237"))
                }
                setOnClickListener { onUnlockMinutes(mins) }
            }
            quickRow.addView(btn)
        }
        layout.addView(quickRow)

        return layout
    }

    /**
     * Call when overlay is being removed to stop animators.
     */
    fun cleanup() {
        breathingView?.cleanup()
        countdownView?.cleanup()
        mainHandler.removeCallbacksAndMessages(null)
    }

    // =========================================================================
    // 🫁 BREATHING CIRCLE VIEW
    // =========================================================================
    class BreathingCircleView(
        context: Context,
        private val targetCycles: Int,
        private val onCompleted: () -> Unit,
    ) : LinearLayout(context) {

        private enum class BreathPhase(val instruction: String, val hint: String, val durationMs: Long) {
            INHALE("Breathe In", "Expand your chest slowly...", 4_000L),
            HOLD_IN("Hold", "Gently retain your breath...", 2_000L),
            EXHALE("Breathe Out", "Slowly release and let go...", 4_000L),
            REST("Rest", "Feel the calmness...", 1_500L),
        }

        private var currentPhase = BreathPhase.INHALE
        private var completedCycles = 0
        private var isCompleted = false
        private var animator: ValueAnimator? = null
        private var currentScale = 0.75f
        private var currentAlphaFactor = 0.35f

        private val circleCanvas: View
        private val textInstruction: TextView
        private val textHint: TextView
        private val textCycleCount: TextView

        private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            // Custom Canvas circle
            circleCanvas = object : View(context) {
                private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.parseColor("#142E20")
                }
                private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.parseColor("#4ADE80")
                    strokeWidth = dp(2).toFloat()
                }
                private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = Color.parseColor("#183F29")
                }
                private val guideRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.parseColor("#1B3827")
                    strokeWidth = dp(1).toFloat()
                }

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val cx = width / 2f
                    val cy = height / 2f
                    val baseRadius = width * 0.30f

                    // Fixed guide ring
                    canvas.drawCircle(cx, cy, baseRadius * 1.35f, guideRingPaint)

                    // Outer pulsing aura
                    auraPaint.alpha = (currentAlphaFactor * 255).toInt().coerceIn(0, 255)
                    canvas.drawCircle(cx, cy, baseRadius * currentScale * 1.25f, auraPaint)

                    // Inner circle
                    canvas.drawCircle(cx, cy, baseRadius * currentScale, fillPaint)
                    canvas.drawCircle(cx, cy, baseRadius * currentScale, strokePaint)
                }
            }.apply {
                layoutParams = LayoutParams(dp(220), dp(220)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            addView(circleCanvas)

            // Instruction Text
            textInstruction = TextView(context).apply {
                text = "Breathe In"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(2))
            }
            addView(textInstruction)

            // Hint Text
            textHint = TextView(context).apply {
                text = "Expand your chest slowly..."
                setTextColor(Color.parseColor("#8E9993"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
            }
            addView(textHint)

            // Cycle tracker pill
            textCycleCount = TextView(context).apply {
                text = "Breath 1 of $targetCycles"
                setTextColor(Color.parseColor("#4ADE80"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(4), dp(12), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#12261A"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor("#214A32"))
                }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(14)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            addView(textCycleCount)

            // Reflection Quote
            val textQuote = TextView(context).apply {
                text = "\"A moment of calm awareness breaks the cycle of mindless scrolling.\""
                setTextColor(Color.parseColor("#6C7A72"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(18), dp(20), 0)
            }
            addView(textQuote)
        }

        fun startAnimationIfNeeded() {
            if (isCompleted || animator != null) return
            startPhase(BreathPhase.INHALE)
        }

        private fun startPhase(phase: BreathPhase) {
            currentPhase = phase
            textInstruction.text = phase.instruction
            textHint.text = phase.hint

            val (fromScale, toScale) = when (phase) {
                BreathPhase.INHALE -> 0.75f to 1.35f
                BreathPhase.HOLD_IN -> 1.35f to 1.35f
                BreathPhase.EXHALE -> 1.35f to 0.75f
                BreathPhase.REST -> 0.75f to 0.75f
            }

            val (fromAlpha, toAlpha) = when (phase) {
                BreathPhase.INHALE -> 0.25f to 0.65f
                BreathPhase.HOLD_IN -> 0.65f to 0.60f
                BreathPhase.EXHALE -> 0.60f to 0.20f
                BreathPhase.REST -> 0.20f to 0.25f
            }

            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = phase.durationMs
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    val fraction = anim.animatedFraction
                    currentScale = fromScale + (toScale - fromScale) * fraction
                    currentAlphaFactor = fromAlpha + (toAlpha - fromAlpha) * fraction
                    circleCanvas.invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        advancePhase()
                    }
                })
                start()
            }
        }

        private fun advancePhase() {
            when (currentPhase) {
                BreathPhase.INHALE -> startPhase(BreathPhase.HOLD_IN)
                BreathPhase.HOLD_IN -> startPhase(BreathPhase.EXHALE)
                BreathPhase.EXHALE -> startPhase(BreathPhase.REST)
                BreathPhase.REST -> {
                    completedCycles++
                    if (completedCycles >= targetCycles) {
                        markCompleted()
                    } else {
                        textCycleCount.text = "Breath ${completedCycles + 1} of $targetCycles"
                        startPhase(BreathPhase.INHALE)
                    }
                }
            }
        }

        private fun markCompleted() {
            isCompleted = true
            textInstruction.text = "✨ Mindful Pause Complete"
            textHint.text = "You are in control of your attention."
            textCycleCount.text = "✓ $targetCycles breaths completed"
            textCycleCount.setTextColor(Color.parseColor("#4ADE80"))
            currentScale = 1.0f
            currentAlphaFactor = 0.5f
            circleCanvas.invalidate()
            onCompleted()
        }

        fun cleanup() {
            animator?.cancel()
            animator = null
        }
    }

    // =========================================================================
    // ⏳ 10-SECOND COUNTDOWN RING VIEW
    // =========================================================================
    class CountdownRingView(
        context: Context,
        private val totalSeconds: Int,
        private val onCompleted: () -> Unit,
    ) : LinearLayout(context) {

        private var remainingSeconds = totalSeconds
        private var isCompleted = false
        private var animator: ValueAnimator? = null
        private var progressFraction = 1.0f

        private val ringCanvas: View
        private val textCountdown: TextView
        private val textPrompt: TextView

        private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

        private val promptQuotes = listOf(
            "Notice the impulse to scroll. Does this serve your goals today?",
            "Pause for 10 seconds. Bring awareness back to your purpose.",
            "Discipline today builds a better tomorrow.",
            "Ask yourself: What was I doing right before picking up my phone?",
            "A 10-second pause can prevent an hour of wasted focus.",
            "You are in complete control of your attention."
        )

        init {
            orientation = VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

            // Custom Canvas Ring
            val ringContainer = FrameLayout(context).apply {
                layoutParams = LayoutParams(dp(220), dp(220)).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }

            ringCanvas = object : View(context) {
                private val bgRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.parseColor("#152B1E")
                    strokeWidth = dp(8).toFloat()
                }
                private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    color = Color.parseColor("#4ADE80")
                    strokeWidth = dp(8).toFloat()
                    strokeCap = Paint.Cap.ROUND
                }
                private val oval = RectF()

                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val stroke = dp(8).toFloat()
                    val pad = stroke / 2f + dp(4)
                    oval.set(pad, pad, width - pad, height - pad)

                    // Background track
                    canvas.drawArc(oval, 0f, 360f, false, bgRingPaint)

                    // Foreground sweep arc
                    val sweepAngle = 360f * progressFraction
                    canvas.drawArc(oval, -90f, sweepAngle, false, arcPaint)
                }
            }
            ringContainer.addView(ringCanvas)

            // Center Text inside ring
            val centerColumn = LinearLayout(context).apply {
                orientation = VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            }

            textCountdown = TextView(context).apply {
                text = "$totalSeconds"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 44f)
                paint.isFakeBoldText = true
                gravity = Gravity.CENTER
            }
            centerColumn.addView(textCountdown)

            val textSecLabel = TextView(context).apply {
                text = "seconds to pause"
                setTextColor(Color.parseColor("#8E9993"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
            }
            centerColumn.addView(textSecLabel)
            ringContainer.addView(centerColumn)
            addView(ringContainer)

            // Reflection Quote
            textPrompt = TextView(context).apply {
                text = promptQuotes.random()
                setTextColor(Color.parseColor("#C0CDC2"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(20), dp(24), 0)
            }
            addView(textPrompt)
        }

        fun startCountdownIfNeeded() {
            if (isCompleted || animator != null) return

            animator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = totalSeconds * 1000L
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    progressFraction = anim.animatedFraction
                    val elapsedSec = (anim.animatedFraction * totalSeconds).toInt()
                    remainingSeconds = (totalSeconds - elapsedSec).coerceAtLeast(0)
                    textCountdown.text = "$remainingSeconds"
                    ringCanvas.invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        isCompleted = true
                        progressFraction = 1f
                        textCountdown.text = "✓"
                        textCountdown.setTextColor(Color.parseColor("#4ADE80"))
                        textPrompt.text = "✨ Mindful reflection complete. Choose your next step."
                        ringCanvas.invalidate()
                        onCompleted()
                    }
                })
                start()
            }
        }

        fun cleanup() {
            animator?.cancel()
            animator = null
        }
    }
}
