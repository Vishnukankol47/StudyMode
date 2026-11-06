package com.studymode.app

import android.Manifest
import android.app.Activity
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.IconButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val requestedScheduleId = mutableLongStateOf(0L)

    companion object {
        val pendingOpenActiveFocus = mutableStateOf(false)
        val showLauncherBlockedToast = mutableStateOf(false)
        val pendingOpenNotificationDigest = mutableStateOf(false)
        val pendingOpenFocusSetup = mutableStateOf(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("studymode", MODE_PRIVATE)
        val studyStore = LocalStudyStore(this)
        val ytLimit = studyStore.appDailyLimit("com.google.android.youtube")
        if (ytLimit != null && ytLimit.minutes == 15) {
            studyStore.setAppDailyLimit("com.google.android.youtube", "YouTube", null)
        }
        FocusTimerAlarms.restore(this)
        AppMonitorService.start(this)
                if (intent?.getBooleanExtra("OPEN_ACTIVE_FOCUS", false) == true) {
            pendingOpenActiveFocus.value = true
        }
        if (intent?.getBooleanExtra("LAUNCHER_BLOCKED_NOTICE", false) == true) {
            showLauncherBlockedToast.value = true
        }
        if (intent?.action == NotificationDigestHelper.ACTION_OPEN_DIGEST) {
            pendingOpenNotificationDigest.value = true
        }
        if (intent?.action == StudyModeWidgetProvider.ACTION_START_FOCUS_FROM_WIDGET) {
            pendingOpenFocusSetup.value = true
        }
        requestedScheduleId.longValue = intent?.data?.pathSegments
            ?.takeIf { it.size == 2 && it[0] == "schedule" }
            ?.get(1)
            ?.toLongOrNull() ?: 0L

        setContent {
            StudyModeTheme {
                var onboardingComplete by rememberSaveable {
                    mutableStateOf(preferences.getBoolean("onboarding_complete", false))
                }

                if (onboardingComplete) {
                    StudyModeHome(studyStore, requestedScheduleId.longValue.takeIf { it > 0 })
                } else {
                    WelcomeFlow(
                        onComplete = {
                            preferences.edit()
                                .putBoolean("onboarding_complete", true)
                                .apply()
                            onboardingComplete = true
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AppMonitorService.start(this)
                StudyModeWidgetProvider.updateAllWidgets(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("OPEN_ACTIVE_FOCUS", false)) {
            pendingOpenActiveFocus.value = true
        }
        if (intent.getBooleanExtra("LAUNCHER_BLOCKED_NOTICE", false)) {
            showLauncherBlockedToast.value = true
        }
        if (intent.action == NotificationDigestHelper.ACTION_OPEN_DIGEST) {
            pendingOpenNotificationDigest.value = true
        }
        if (intent.action == StudyModeWidgetProvider.ACTION_START_FOCUS_FROM_WIDGET) {
            pendingOpenFocusSetup.value = true
        }
        requestedScheduleId.longValue = intent.data?.pathSegments
            ?.takeIf { it.size == 2 && it[0] == "schedule" }
            ?.get(1)
            ?.toLongOrNull() ?: 0L
    }
}

private val Ink = Color(0xFF101312)
private val Panel = Color(0xFF1B211F)
private val Muted = Color(0xFF9AA6A0)
private val Mint = Color(0xFFB9F28D)
private val NightInk = Color(0xFF07101D)
private val NightPanel = Color(0xFF111C31)
private val SkyBlue = Color(0xFF55B8F2)
private val Violet = Color(0xFF9B83FF)
private const val MAX_DOMAIN_INPUT_LENGTH = 253

@Composable
private fun StudyModeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Ink,
            background = Ink,
            onBackground = Color(0xFFF4F7F3),
            surface = Panel,
            onSurface = Color(0xFFF4F7F3),
            secondary = Color(0xFF87D7B1),
        ),
        content = content,
    )
}

@Composable
private fun WelcomeFlow(onComplete: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf(
        "Make room for what matters",
        "Build a study rhythm",
        "See your progress grow",
    )
    val descriptions = listOf(
        "A calmer, simpler space for focused study, kept on your device.",
        "Plan a session, choose a goal, and take it one step at a time.",
        "Your sessions and goals stay private and available offline.",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 28.dp, vertical = 24.dp),
    ) {
        BrandMark()
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(184.dp)
                .align(Alignment.CenterHorizontally)
                .clip(CircleShape)
                .background(Panel),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "S",
                color = Mint,
                fontSize = 78.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = titles[page],
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = descriptions[page],
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
            lineHeight = 25.sp,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            titles.indices.forEach { index ->
                Box(
                    modifier = Modifier
                        .width(if (index == page) 24.dp else 8.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (index == page) Mint else Panel),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (page == titles.lastIndex) onComplete() else page += 1
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mint,
                contentColor = Ink,
            ),
        ) {
            Text(
                text = if (page == titles.lastIndex) "Get started" else "Continue",
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(30.dp)) {
            val w = this.size.width
            val h = this.size.height
            // Draw sprout stem and leaves
            val stemPath = Path().apply {
                moveTo(w * 0.5f, h * 0.9f)
                cubicTo(w * 0.5f, h * 0.6f, w * 0.48f, h * 0.45f, w * 0.5f, h * 0.35f)
            }
            drawPath(stemPath, Color(0xFF69ED70), style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
            
            // Left leaf
            val leftLeaf = Path().apply {
                moveTo(w * 0.48f, h * 0.45f)
                cubicTo(w * 0.15f, h * 0.4f, w * 0.1f, h * 0.15f, w * 0.35f, h * 0.1f)
                cubicTo(w * 0.5f, h * 0.2f, w * 0.48f, h * 0.38f, w * 0.48f, h * 0.45f)
                close()
            }
            drawPath(leftLeaf, Color(0xFF69ED70))
            
            // Right leaf
            val rightLeaf = Path().apply {
                moveTo(w * 0.52f, h * 0.4f)
                cubicTo(w * 0.85f, h * 0.35f, w * 0.9f, h * 0.15f, w * 0.65f, h * 0.1f)
                cubicTo(w * 0.5f, h * 0.2f, w * 0.52f, h * 0.35f, w * 0.52f, h * 0.4f)
                close()
            }
            drawPath(rightLeaf, Color(0xFF69ED70))
        }
        Spacer(Modifier.width(10.dp))
        Text("StudyMode", fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color.White)
    }
}

private enum class HomeTab(val label: String, val icon: String) {
    HOME("Home", "🏠"),
    PLAN("Plan", "📅"),
    SLEEP("Sleep", "🌙"),
    BLOCKS("Blocks", "🚫"),
    PROFILE("Profile", ""),
    ACHIEVEMENTS("Achievements", ""),
    SETTINGS("Settings", ""),
    USAGE_STATS("Usage Stats", ""),
    FOCUS_ANALYSIS("Focus Analysis", ""),
}

private val navigationTabs = listOf(
    HomeTab.HOME,
    HomeTab.PLAN,
    HomeTab.SLEEP,
    HomeTab.BLOCKS,
)

@Composable
private fun StudyModeHome(store: LocalStudyStore, requestedScheduleId: Long?) {
    var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.HOME.name) }
    var settingsPreviousTab by rememberSaveable { mutableStateOf(HomeTab.HOME.name) }
    var usageStatsInitialMode by rememberSaveable { mutableStateOf(StatsTabMode.DAILY) }
    LaunchedEffect(requestedScheduleId) {
        if (requestedScheduleId != null) selectedTabName = HomeTab.PLAN.name
    }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    val selectedTab = HomeTab.valueOf(selectedTabName)
    val dailyGoalMinutes = remember(refreshKey) { store.dailyGoalMinutes() }
    val todayFocusSeconds = remember(refreshKey) { store.todayFocusSeconds() }
    val activeTimer = remember(refreshKey) { store.activeTimer() }
    val schedules = remember(refreshKey) { store.schedules() }
    val context = LocalContext.current
    val usageAccess = remember(refreshKey) { hasUsageAccess(context) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                resumeTrigger += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usageAccessEnabled = remember(resumeTrigger) { hasUsageAccess(context) }
    val overlayEnabled = remember(resumeTrigger) { hasOverlayPermission(context) }
        var permissionsPromptDismissed by rememberSaveable { mutableStateOf(false) }

    val shouldShowPermissionsPrompt = (!usageAccessEnabled || !overlayEnabled) && !permissionsPromptDismissed

    if (shouldShowPermissionsPrompt) {
        AppBlockingPermissionsDialog(
            usageAccessEnabled = usageAccessEnabled,
            overlayEnabled = overlayEnabled,
            accessibilityEnabled = false,
            onDismiss = { permissionsPromptDismissed = true },
        )
    }

    var screenTimeMinutes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(refreshKey, usageAccess, resumeTrigger) {
        if (usageAccess) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val usageManager = context.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                val startOfDay = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val now = System.currentTimeMillis()
                val aggStats = usageManager?.queryAndAggregateUsageStats(startOfDay, now) ?: emptyMap()
                val launchable = AppListCache.getPackages(context)
                var aggTotal = 0L
                for (pkg in launchable) {
                    val s = aggStats[pkg] ?: continue
                    if (s.lastTimeUsed >= startOfDay) {
                        aggTotal += s.totalTimeInForeground
                    }
                }
                android.util.Log.d("StudyModeUsage", "Aggregated launchable total: ${aggTotal / 60_000}m (${aggTotal / 3600000}h ${(aggTotal % 3600000) / 60000}m)")

                val usageMap = todayAccurateUsageMap(context)
                val total = usageMap.values.sum() / 60_000
                android.util.Log.d("StudyModeUsage", "Event timeline total: ${total}m (${total / 60}h ${total % 60}m)")
                for ((pkg, time) in usageMap.entries.sortedByDescending { it.value }) {
                    if (time >= 60_000) {
                        android.util.Log.d("StudyModeUsage", "  $pkg: ${time / 60000}m")
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    screenTimeMinutes = total
                }
            }
        } else {
            screenTimeMinutes = null
        }
    }

    var showActiveFocusWindow by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(MainActivity.pendingOpenActiveFocus.value, activeTimer) {
        if (MainActivity.pendingOpenActiveFocus.value && activeTimer != null) {
            showActiveFocusWindow = true
            MainActivity.pendingOpenActiveFocus.value = false
        }
    }

    LaunchedEffect(MainActivity.showLauncherBlockedToast.value) {
        if (MainActivity.showLauncherBlockedToast.value) {
            Toast.makeText(
                context,
                "Hold on! To leave, you’ll need to finish or end your current session",
                Toast.LENGTH_SHORT
            ).show()
            MainActivity.showLauncherBlockedToast.value = false
        }
    }

    val isFocusWindowVisible = showActiveFocusWindow && activeTimer != null

    Scaffold(
        containerColor = Ink,
        bottomBar = {
            if (!isFocusWindowVisible && (selectedTab in navigationTabs || selectedTab == HomeTab.FOCUS_ANALYSIS)) {
                Surface(
                    color = Color(0xFF080A08),
                    border = BorderStroke(1.dp, Color(0xFF1A221A)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        navigationTabs.forEach { tab ->
                            val isSelected = (selectedTab == tab) || (tab == HomeTab.HOME && selectedTab == HomeTab.FOCUS_ANALYSIS)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { selectedTabName = tab.name }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            ) {
                                BottomNavVectorIcon(
                                    tab = tab,
                                    isSelected = isSelected,
                                )
                                Text(
                                    text = tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF8E9993),
                                )
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(3.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4ADE80)),
                                    )
                                } else {
                                    Spacer(Modifier.height(3.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFocusWindowVisible) PaddingValues(0.dp) else contentPadding),
        ) {
            if (isFocusWindowVisible && activeTimer != null) {
                ActiveFocusScreen(
                    store = store,
                    timer = activeTimer,
                    onMinimize = { showActiveFocusWindow = false },
                    onPause = {
                        store.pauseTimer(activeTimer, System.currentTimeMillis())
                        FocusTimerAlarms.cancel(context)
                        refreshKey += 1
                    },
                    onResume = {
                        store.resumeTimer(activeTimer, System.currentTimeMillis())
                        store.activeTimer()?.let { resumed -> FocusTimerAlarms.schedule(context, resumed) }
                        refreshKey += 1
                    },
                    onFinish = { completed ->
                        if (completed) {
                            SoundEffectManager.play(context, SoundEffect.TIMER_END)
                        } else {
                            SoundEffectManager.play(context, SoundEffect.SESSION_END)
                        }
                        store.finishTimer(completed = completed, nowMillis = System.currentTimeMillis())
                        FocusTimerAlarms.cancel(context)
                        showActiveFocusWindow = false
                        refreshKey += 1
                    },
                    onTakeBreak = { _ ->
                        SoundEffectManager.play(context, SoundEffect.BREAK_START)
                        store.pauseTimer(activeTimer, System.currentTimeMillis())
                        FocusTimerAlarms.cancel(context)
                        refreshKey += 1
                    },
                )
            } else {
                when (selectedTab) {
                    HomeTab.HOME -> HomeScreen(
                        store = store,
                        dailyGoalMinutes = dailyGoalMinutes,
                        todayFocusSeconds = todayFocusSeconds,
                        activeTimer = activeTimer,
                        schedules = schedules,
                        screenTimeMinutes = screenTimeMinutes,
                        onOpenProfile = { selectedTabName = HomeTab.PROFILE.name },
                        onOpenPlan = { selectedTabName = HomeTab.PLAN.name },
                        onOpenSleep = { selectedTabName = HomeTab.SLEEP.name },
                        onOpenApps = { selectedTabName = HomeTab.BLOCKS.name },
                        onOpenUsageStats = { mode ->
                            usageStatsInitialMode = mode
                            selectedTabName = HomeTab.USAGE_STATS.name
                        },
                        onOpenSettings = {
                            settingsPreviousTab = HomeTab.HOME.name
                            selectedTabName = HomeTab.SETTINGS.name
                        },
                        onOpenFocusAnalysis = { selectedTabName = HomeTab.FOCUS_ANALYSIS.name },
                        onTimerChanged = { refreshKey += 1 },
                        onGoalChange = {
                            store.setDailyGoalMinutes(it)
                            refreshKey += 1
                        },
                        onOpenActiveFocusWindow = { showActiveFocusWindow = true },
                    )
                HomeTab.PLAN -> ScheduleSystemScreen(
                    store = store,
                    onSchedulesChanged = { refreshKey += 1 },
                )
                HomeTab.SLEEP -> SleepScreen(
                    store = store,
                    onBack = { selectedTabName = HomeTab.HOME.name },
                    onNavigateToBlocks = { selectedTabName = HomeTab.BLOCKS.name },
                )
                HomeTab.BLOCKS -> AppsAndUsageScreen(
                    store = store,
                    onDataChanged = { refreshKey += 1 },
                    onBack = { selectedTabName = HomeTab.HOME.name },
                )
                HomeTab.SETTINGS -> SettingsScreen(
                    store = store,
                    onBack = { selectedTabName = settingsPreviousTab },
                )
                HomeTab.PROFILE -> ProfileScreen(
                    store = store,
                    onBack = { selectedTabName = HomeTab.HOME.name },
                    onSettings = {
                        settingsPreviousTab = HomeTab.PROFILE.name
                        selectedTabName = HomeTab.SETTINGS.name
                    },
                    onViewAchievements = { selectedTabName = HomeTab.ACHIEVEMENTS.name },
                )
                HomeTab.ACHIEVEMENTS -> AchievementsScreen(
                    store = store,
                    onBack = { selectedTabName = HomeTab.PROFILE.name },
                )
                HomeTab.USAGE_STATS -> UsageStatsScreen(
                    store = store,
                    onBack = { selectedTabName = HomeTab.HOME.name },
                    initialMode = usageStatsInitialMode,
                    onOpenAppsAndUsage = { selectedTabName = HomeTab.BLOCKS.name },
                )
                HomeTab.FOCUS_ANALYSIS -> FocusAnalysisScreen(
                    store = store,
                    onBack = { selectedTabName = HomeTab.HOME.name },
                    onNavigateToPlan = { selectedTabName = HomeTab.PLAN.name },
                    onNavigateToSleep = { selectedTabName = HomeTab.SLEEP.name },
                    onNavigateToBlocks = { selectedTabName = HomeTab.BLOCKS.name },
                    onDataChanged = { refreshKey += 1 },
                )
            }
        }
    }
}
}

@Composable
private fun BottomNavVectorIcon(tab: HomeTab, isSelected: Boolean) {
    val iconColor = if (isSelected) Color(0xFF4ADE80) else Color(0xFF8E9993)
    Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        when (tab) {
            HomeTab.HOME -> {
                val stroke = 1.8.dp.toPx()
                val path = Path().apply {
                    moveTo(w / 2f, 2.5.dp.toPx())
                    lineTo(w - 3.dp.toPx(), h * 0.44f)
                    lineTo(w - 4.5.dp.toPx(), h * 0.44f)
                    lineTo(w - 4.5.dp.toPx(), h - 2.dp.toPx())
                    lineTo(4.5.dp.toPx(), h - 2.dp.toPx())
                    lineTo(4.5.dp.toPx(), h * 0.44f)
                    lineTo(3.dp.toPx(), h * 0.44f)
                    close()
                }
                drawPath(path, iconColor, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val doorW = 5.dp.toPx()
                val doorH = 6.dp.toPx()
                drawRect(
                    color = iconColor,
                    topLeft = Offset((w - doorW) / 2f, h - 2.dp.toPx() - doorH),
                    size = Size(doorW, doorH),
                    style = Stroke(width = 1.4.dp.toPx())
                )
            }
            HomeTab.PLAN -> {
                val pad = 2.5.dp.toPx()
                val topPad = 4.5.dp.toPx()
                val stroke = 1.8.dp.toPx()
                drawRoundRect(
                    color = iconColor,
                    topLeft = Offset(pad, topPad),
                    size = Size(w - pad * 2, h - topPad - 2.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(stroke)
                )
                drawLine(
                    color = iconColor,
                    start = Offset(pad, topPad + 4.dp.toPx()),
                    end = Offset(w - pad, topPad + 4.dp.toPx()),
                    strokeWidth = 1.4.dp.toPx()
                )
                drawLine(
                    color = iconColor,
                    start = Offset(pad + 4.dp.toPx(), 1.5.dp.toPx()),
                    end = Offset(pad + 4.dp.toPx(), topPad + 1.dp.toPx()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = iconColor,
                    start = Offset(w - pad - 4.dp.toPx(), 1.5.dp.toPx()),
                    end = Offset(w - pad - 4.dp.toPx(), topPad + 1.dp.toPx()),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            HomeTab.SLEEP -> {
                val cx = w / 2f
                val cy = h / 2f
                val radius = (minOf(w, h) / 2f) - 1.5.dp.toPx()
                val moonPath = Path().apply {
                    moveTo(cx + radius * 0.22f, cy - radius)
                    cubicTo(
                        cx + radius * 1.05f, cy - radius * 0.45f,
                        cx + radius * 1.05f, cy + radius * 0.45f,
                        cx + radius * 0.22f, cy + radius
                    )
                    cubicTo(
                        cx - radius * 0.45f, cy + radius * 0.45f,
                        cx - radius * 0.45f, cy - radius * 0.45f,
                        cx + radius * 0.22f, cy - radius
                    )
                    close()
                }
                if (isSelected) {
                    drawPath(moonPath, iconColor)
                } else {
                    drawPath(moonPath, iconColor, style = Stroke(width = 1.8.dp.toPx()))
                }
            }
            HomeTab.BLOCKS -> {
                val cx = w / 2f
                val cy = h / 2f
                val radius = (minOf(w, h) / 2f) - 2.dp.toPx()
                drawCircle(iconColor, radius, Offset(cx, cy), style = Stroke(width = 1.8.dp.toPx()))
                val offset = radius * 0.7071f
                drawLine(
                    color = iconColor,
                    start = Offset(cx - offset, cy - offset),
                    end = Offset(cx + offset, cy + offset),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            else -> {}
        }
    }
}

@Composable
private fun GroupsPlaceholderScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("👥", fontSize = 56.sp)
        Spacer(Modifier.height(16.dp))
        Text("Groups", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Study together with friends.\nComing soon.", color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun SettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    BackHandler {
        onBack()
    }
    var domains by remember { mutableStateOf(store.blockedDomains()) }
    var domainInput by rememberSaveable { mutableStateOf("") }
    var domainError by rememberSaveable { mutableStateOf<String?>(null) }
    var filterActive by remember { mutableStateOf(LocalDnsFilterService.isActive(context)) }
    var filterError by remember { mutableStateOf(LocalDnsFilterService.lastError(context)) }
    var consentError by rememberSaveable { mutableStateOf<String?>(null) }
    var focusOnlyBlocking by remember { mutableStateOf(store.focusOnlyBlockingEnabled()) }
    val filterSupported = Build.VERSION.SDK_INT != Build.VERSION_CODES.P
    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            consentError = null
            LocalDnsFilterService.start(context)
        } else {
            consentError = "VPN access was not granted. The site filter remains off."
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            filterActive = LocalDnsFilterService.isActive(context)
            filterError = LocalDnsFilterService.lastError(context)
            delay(1_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(36.dp)
                    .offset(x = (-6).dp),
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(w * 0.8f, h * 0.5f)
                        lineTo(w * 0.2f, h * 0.5f)
                        moveTo(w * 0.5f, h * 0.2f)
                        lineTo(w * 0.2f, h * 0.5f)
                        lineTo(w * 0.5f, h * 0.8f)
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF69ED70),
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
            BrandMark()
        }
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("StudyMode keeps your study data on this device.", color = Muted)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Panel,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Block selected apps during focus",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Switch(
                        checked = focusOnlyBlocking,
                        onCheckedChange = {
                            store.setFocusOnlyBlockingEnabled(it)
                            focusOnlyBlocking = it
                        },
                    )
                }
                Text(
                    "When enabled, manually blocked apps are blocked only while a focus timer is running. Daily app limits are unchanged.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Panel,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Website filter", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    if (filterActive) "Local DNS filter is active." else "Local DNS filter is off.",
                    color = if (filterActive) Mint else Muted,
                )
                Button(
                    onClick = {
                        if (filterActive) {
                            LocalDnsFilterService.stop(context)
                        } else {
                            val consentIntent = VpnService.prepare(context)
                            if (consentIntent == null) {
                                LocalDnsFilterService.start(context)
                            } else {
                                vpnConsent.launch(consentIntent)
                            }
                        }
                    },
                    enabled = filterActive || filterSupported,
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) {
                    Text(if (filterActive) "Turn off site filter" else "Enable local VPN filter")
                }
                if (!filterSupported) {
                    Text(
                        "Site filtering is unavailable on Android 9 because the local DNS proxy cannot preserve that version's Private DNS behavior.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                consentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                filterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "This optional Android VPN handles DNS for the local filter. Allowed lookups go directly to the resolver supplied by your current network; StudyMode does not log them or send them to a StudyMode server. Android 10+ uses the system DNS resolver; Android 7-8.1 use standard DNS over UDP, which may be unencrypted. Other app traffic is not routed through StudyMode.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Filters apply to hostnames and their subdomains, not URL paths. YouTube Shorts cannot be separated from YouTube itself by this filter. Only standard IPv4 UDP DNS is filtered; apps using their own encrypted DNS, direct IP connections, or other DNS transports may bypass it.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Android allows one active VPN connection at a time. Enabling this may replace another VPN.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Panel,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Blocked domains", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("No domains are preconfigured. Add hostnames you choose.", color = Muted)
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it.take(MAX_DOMAIN_INPUT_LENGTH)
                        domainError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("example.com") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        try {
                            store.addBlockedDomain(domainInput)
                            domainInput = ""
                            domainError = null
                            domains = store.blockedDomains()
                        } catch (exception: IllegalArgumentException) {
                            domainError = exception.message ?: "Enter a valid hostname."
                        }
                    },
                    enabled = domainInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                ) {
                    Text("Add domain")
                }
                domainError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (domains.isEmpty()) {
                    Text("Your blocked-domain list is empty.", color = Muted)
                } else {
                    domains.forEach { blockedDomain ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                blockedDomain.domain,
                                modifier = Modifier.weight(1f),
                                fontWeight = FontWeight.Medium,
                            )
                            TextButton(
                                onClick = {
                                    store.removeBlockedDomain(blockedDomain.domain)
                                    domains = store.blockedDomains()
                                },
                            ) {
                                Text("Remove", color = Muted)
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Account, advertising, and payment features are not included. The local site filter is optional and can be turned off here or in Android VPN settings.",
            modifier = Modifier.padding(bottom = 24.dp),
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HomeScreen(
    store: LocalStudyStore,
    dailyGoalMinutes: Int,
    todayFocusSeconds: Long,
    activeTimer: ActiveTimer?,
    schedules: List<StudySchedule>,
    screenTimeMinutes: Long?,
    onOpenProfile: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenUsageStats: (StatsTabMode) -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenFocusAnalysis: () -> Unit = {},
    onTimerChanged: () -> Unit,
    onGoalChange: (Int) -> Unit,
    onOpenActiveFocusWindow: () -> Unit = {},
) {
    val context = LocalContext.current
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var timer by remember(activeTimer) { mutableStateOf(activeTimer) }
    var selectedMode by rememberSaveable { mutableIntStateOf(0) } // 0: Focus (45m), 1: Short Break (5m), 2: Long Break (15m)
    var focusDurationMinutes by rememberSaveable { mutableIntStateOf(45) }
    var showDurationPickerDialog by rememberSaveable { mutableStateOf(false) }
    var showFocusSetupSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(MainActivity.pendingOpenFocusSetup.value) {
        if (MainActivity.pendingOpenFocusSetup.value) {
            showFocusSetupSheet = true
            MainActivity.pendingOpenFocusSetup.value = false
        }
    }

    val overview = remember(todayFocusSeconds) { store.focusOverview() }
    val focusedMinutes = (todayFocusSeconds / 60).toInt()
    val focusedHours = focusedMinutes / 60
    val focusedRemainder = focusedMinutes % 60
    val goalHours = (dailyGoalMinutes / 60).coerceAtLeast(1)

    // Filter today's schedules
    val todaySchedules = remember(schedules, nowMillis) {
        val todayCalendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayOfWeek = todayCalendar.get(Calendar.DAY_OF_WEEK)
        schedules.filter { dayOfWeek in it.activeDays }
    }

    LaunchedEffect(timer?.running, timer?.segmentStartedAtMillis) {
        while (timer?.running == true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
            val active = timer ?: break
            if (active.remainingAt(nowMillis) == 0L) {
                FocusTimerAlarms.complete(context, store, nowMillis)
                timer = null
                onTimerChanged()
                break
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080A08))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top App Bar matching Image 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrandMark()
            Spacer(Modifier.weight(1f))
            
            // Notification bell with green indicator dot
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔔", fontSize = 18.sp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 6.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF69ED70)),
                )
            }
            
            Spacer(Modifier.width(8.dp))
            
            // User avatar circle ("V")
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = Color(0xFF162A17),
                onClick = onOpenProfile,
                border = BorderStroke(1.dp, Color(0xFF28482A)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        store.profile().name.take(1).uppercase(Locale.getDefault()).ifBlank { "V" },
                        color = Color(0xFF69ED70),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        // Landscape banner: "Discipline today builds a better tomorrow." with night hills & moon
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0C140C), Color(0xFF0E1A0F), Color(0xFF132A16)),
                    ),
                ),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                // Draw mountain contours on right
                val hillPath = Path().apply {
                    moveTo(w * 0.45f, h)
                    cubicTo(w * 0.6f, h * 0.6f, w * 0.75f, h * 0.45f, w, h * 0.65f)
                    lineTo(w, h)
                    close()
                }
                drawPath(hillPath, Color(0xFF0A180C))
                
                // Glowing green moon
                val moonCenter = Offset(w * 0.85f, h * 0.4f)
                val moonRadius = 26.dp.toPx()
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF69ED70).copy(alpha = 0.45f), Color.Transparent),
                        center = moonCenter,
                        radius = moonRadius * 2.2f,
                    ),
                    radius = moonRadius * 2.2f,
                    center = moonCenter,
                )
                drawCircle(Color(0xFF9EFF9E), radius = moonRadius, center = moonCenter)
            }

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Discipline today",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                Row {
                    Text("builds a ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("better tomorrow.", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }

        // Focus Session Card (matching Image 1)
        FocusSessionMainCard(
            timer = timer,
            selectedMode = selectedMode,
            focusDurationMinutes = focusDurationMinutes,
            onSelectMode = { selectedMode = it },
            onOpenDurationPicker = { showDurationPickerDialog = true },
            nowMillis = nowMillis,
            onSettings = onOpenSettings,
            onOpenActiveFocusWindow = onOpenActiveFocusWindow,
            onToggleTimer = {
                if (timer == null) {
                    showFocusSetupSheet = true
                } else if (timer?.running == true) {
                    store.pauseTimer(timer!!, nowMillis)
                    FocusTimerAlarms.cancel(context)
                    timer = store.activeTimer()
                    onTimerChanged()
                } else {
                    store.resumeTimer(timer!!, nowMillis)
                    store.activeTimer()?.let { FocusTimerAlarms.schedule(context, it) }
                    timer = store.activeTimer()
                    onTimerChanged()
                    onOpenActiveFocusWindow()
                }
            },
            onFinishTimer = {
                if (timer != null) {
                    store.finishTimer(completed = false, nowMillis = nowMillis)
                    FocusTimerAlarms.cancel(context)
                    timer = null
                    onTimerChanged()
                }
            },
        )

        val screenTimeDisplay = if (screenTimeMinutes == null) {
            "Setup"
        } else {
            val hours = screenTimeMinutes / 60
            val mins = screenTimeMinutes % 60
            if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        }

        // 3 Stats Row matching Reference UI: Screen time, Current streak, Daily goal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Card 1: Screen time (phone usage today)
            StatBox(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenUsageStats(StatsTabMode.DAILY) },
                icon = "📊",
                iconBg = Color(0xFF132A16),
                iconColor = Color(0xFF69ED70),
                value = screenTimeDisplay,
                label = "Screen time",
            )
            // Card 2: Current streak
            StatBox(
                modifier = Modifier.weight(1f),
                icon = "🔥",
                iconBg = Color(0xFF33200D),
                iconColor = Color(0xFFFF9800),
                value = "${overview.currentStreakDays} days",
                label = "Current",
            )
            // Card 3: Daily goal
            StatBox(
                modifier = Modifier
                    .weight(1f)
                    .clickable { showGoalDialog = true },
                icon = "🎯",
                iconBg = Color(0xFF112538),
                iconColor = Color(0xFF4FC3F7),
                value = "${goalHours}h",
                label = "Daily goal",
            )
        }

        // Time Saved & Impact Banner (Phase 1)
        var showTimeSavedDialog by rememberSaveable { mutableStateOf(false) }
        var showNotificationDigestInbox by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(MainActivity.pendingOpenNotificationDigest.value) {
            if (MainActivity.pendingOpenNotificationDigest.value) {
                showNotificationDigestInbox = true
                MainActivity.pendingOpenNotificationDigest.value = false
            }
        }
        val timeSavedSummary = remember(nowMillis) {
            calculateTimeSaved(context, store, nowMillis)
        }

        TimeSavedImpactCard(
            summary = timeSavedSummary,
            onClick = { showTimeSavedDialog = true }
        )

        if (showTimeSavedDialog) {
            TimeSavedImpactDialog(
                summary = timeSavedSummary,
                onDismiss = { showTimeSavedDialog = false }
            )
        }

        if (showNotificationDigestInbox) {
            NotificationDigestInboxDialog(
                store = store,
                onDismiss = { showNotificationDigestInbox = false },
            )
        }

        // Focused Study Completed Card matching Screen 1 in design mockup
        val focusRemainderFormatted = String.format(java.util.Locale.US, "%02d", focusedRemainder)
        val focusCompletedTitle = "${focusedHours}h ${focusRemainderFormatted}m focused study completed"

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenFocusAnalysis() },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1712),
            border = BorderStroke(1.dp, Color(0xFF1B2A20)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Progress icon: dark container with 3 vertical mint bars
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF132A1D)),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.size(20.dp)) {
                        val barWidth = 3.5.dp.toPx()
                        val corner = 1.5.dp.toPx()
                        val barColor = Color(0xFF4ADE80)
                        // Bar 1 (left)
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), size.height - 8.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(barWidth, 8.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                        )
                        // Bar 2 (center, tallest)
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(7.5.dp.toPx(), size.height - 18.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(barWidth, 18.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                        )
                        // Bar 3 (right, medium)
                        drawRoundRect(
                            color = barColor,
                            topLeft = androidx.compose.ui.geometry.Offset(13.5.dp.toPx(), size.height - 13.dp.toPx()),
                            size = androidx.compose.ui.geometry.Size(barWidth, 13.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = focusCompletedTitle,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "View your focus analysis",
                        color = Color(0xFF8E9993),
                        fontSize = 12.sp,
                    )
                }

                Text(
                    text = "›",
                    color = Color(0xFF8E9993),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        // Phone Unlock & Pickup Tracker Card
        val todayUnlocks = remember(nowMillis) { store.todayUnlockCount(nowMillis) }
        val todayPickups = remember(nowMillis) { store.todayPickupCount(nowMillis) }
        val unlockGoal = remember { store.dailyUnlockGoal() }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onOpenUsageStats(StatsTabMode.UNLOCKS) },
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1712),
            border = BorderStroke(1.dp, Color(0xFF1B2A20)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF132A1D)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🔓", fontSize = 18.sp)
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$todayUnlocks unlocks today",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (todayUnlocks <= unlockGoal) {
                            Text(
                                text = "• On track",
                                color = Color(0xFF4ADE80),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "• Goal exceeded",
                                color = Color(0xFFFB923C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$todayPickups pickups · Goal: $unlockGoal unlocks",
                        color = Color(0xFF8E9993),
                        fontSize = 12.sp,
                    )
                }

                Text(
                    text = "›",
                    color = Color(0xFF8E9993),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                )
            }
        }

        // Section: Today's Plan matching Image 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Today's Plan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            TextButton(
                onClick = onOpenPlan,
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("View all ›", color = Color(0xFF69ED70), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }

        if (todaySchedules.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF101510),
                border = BorderStroke(1.dp, Color(0xFF1E281E)),
                onClick = onOpenPlan,
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("No study sessions scheduled for today", fontWeight = FontWeight.SemiBold, color = Color.White)
                    Text("Tap to add a study block and plan your day.", color = Color(0xFFA4AAA4), fontSize = 12.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                todaySchedules.take(3).forEach { schedule ->
                    TodayScheduleCard(
                        schedule = schedule,
                        nowMillis = nowMillis,
                        onClick = onOpenPlan,
                    )
                }
            }
        }

        // Section: Quick Tools matching Image 1
        Text(
            "Quick Tools",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Card 1: App Limits
            QuickToolBox(
                modifier = Modifier.weight(1f),
                icon = "📱",
                iconBg = Color(0xFF132A16),
                iconColor = Color(0xFF69ED70),
                title = "App Limits",
                subtitle = "Reduce distractions",
                onClick = onOpenApps,
            )
            // Card 2: Sleep
            QuickToolBox(
                modifier = Modifier.weight(1f),
                icon = "🌙",
                iconBg = Color(0xFF231838),
                iconColor = Color(0xFFA98AFF),
                title = "Sleep",
                subtitle = "Track & improve\nsleep habits",
                onClick = onOpenSleep,
            )
            // Card 3: Blocks
            QuickToolBox(
                modifier = Modifier.weight(1f),
                icon = "🚫",
                iconBg = Color(0xFF331616),
                iconColor = Color(0xFFFF7777),
                title = "Blocks",
                subtitle = "Block distracting\napps",
                onClick = onOpenApps,
            )
        }

        Spacer(Modifier.height(16.dp))
    }

    if (showGoalDialog) {
        GoalDialog(
            currentGoal = dailyGoalMinutes,
            onDismiss = { showGoalDialog = false },
            onSelect = {
                onGoalChange(it)
                showGoalDialog = false
            },
        )
    }

    if (showFocusSetupSheet) {
        FocusSetupBottomSheet(
            store = store,
            focusMinutes = focusDurationMinutes,
            onOpenDurationPicker = {
                showDurationPickerDialog = true
            },
            onDismiss = { showFocusSetupSheet = false },
            onStartSession = { mode, durationMins, breakMins, ultraFocus ->
                showFocusSetupSheet = false
                val goalLabel = when (mode) {
                    FocusSessionMode.STOPWATCH -> if (ultraFocus) "Ultra Stopwatch" else "Stopwatch Focus"
                    FocusSessionMode.POMODORO -> if (ultraFocus) "Ultra Pomodoro" else "Pomodoro Focus"
                    FocusSessionMode.TIMER -> if (ultraFocus) "Ultra Focus" else "Study Focus"
                }
                val finalDuration = when (mode) {
                    FocusSessionMode.STOPWATCH -> 720
                    FocusSessionMode.POMODORO -> 25
                    FocusSessionMode.TIMER -> durationMins
                }
                focusDurationMinutes = durationMins
                SoundEffectManager.play(context, SoundEffect.SESSION_START)
                store.startTimer(finalDuration, goalLabel)
                store.setUltraFocus(ultraFocus)
                val ultraConfig = UltraFocusPreferences.load(context)
                val blockLauncher = ultraFocus && ultraConfig.blockHomeScreen
                store.setLauncherBlocked(blockLauncher)
                if (ultraFocus) {
                    store.setBlockShortsReelsEnabled(ultraConfig.blockShortsReels)
                }
                timer = store.activeTimer()
                timer?.let { FocusTimerAlarms.schedule(context, it) }
                nowMillis = System.currentTimeMillis()
                onTimerChanged()
                onOpenActiveFocusWindow()
            },
        )
    }

    if (showDurationPickerDialog) {
        FocusDurationPickerDialog(
            initialMinutes = focusDurationMinutes,
            onDismiss = { showDurationPickerDialog = false },
            onConfirm = { newMinutes ->
                focusDurationMinutes = newMinutes
                showDurationPickerDialog = false
            },
        )
    }
}

// ----------------------------------------------------------------------
// HOME SCREEN COMPONENTS (IMAGE 1)
// ----------------------------------------------------------------------
@Composable
private fun FocusSessionMainCard(
    timer: ActiveTimer?,
    selectedMode: Int,
    focusDurationMinutes: Int,
    onSelectMode: (Int) -> Unit,
    onOpenDurationPicker: () -> Unit,
    nowMillis: Long,
    onSettings: () -> Unit,
    onToggleTimer: () -> Unit,
    onFinishTimer: () -> Unit = {},
    onOpenActiveFocusWindow: () -> Unit = {},
) {
    val isStopwatch = timer?.goal?.contains("Stopwatch", ignoreCase = true) == true
    val displaySecs = if (isStopwatch) {
        timer!!.elapsedAt(nowMillis) / 1000
    } else {
        val remainingMillis = timer?.remainingAt(nowMillis) ?: run {
            when (selectedMode) {
                0 -> focusDurationMinutes * 60_000L
                1 -> 5 * 60_000L
                else -> 15 * 60_000L
            }
        }
        remainingMillis / 1000
    }
    val timerText = "%02d:%02d:%02d".format(displaySecs / 3600, (displaySecs % 3600) / 60, displaySecs % 60)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF101510),
        border = BorderStroke(1.dp, Color(0xFF1E281E)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top row: Green dot + Focus Session, Gear icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF69ED70)),
                    )
                    Text(
                        "Focus Session",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(28.dp),
                ) {
                    Text("⚙️", fontSize = 16.sp)
                }
            }

            // Giant Timer - Clickable to set desired focus duration or open window!
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        if (timer == null) {
                            onOpenDurationPicker()
                        } else {
                            onOpenActiveFocusWindow()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(
                    text = timerText,
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }

            Text(
                text = when {
                    timer?.running == true -> if (isStopwatch) "Stopwatch active · Stay with it" else "Focus is active · Stay with it"
                    timer != null -> "Paused · Tap button to resume"
                    else -> "Stay focused! · Tap timer to edit"
                },
                color = if (timer == null) Color(0xFF69ED70) else Color(0xFFA4AAA4),
                fontSize = 13.sp,
            )

            // Aurora Focus Window shortcut when a timer is active
            if (timer != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF142B18),
                    border = BorderStroke(1.dp, Color(0xFF2E6338)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenActiveFocusWindow() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("🌌", fontSize = 16.sp)
                            Column {
                                Text("Aurora Focus Window", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Tap to view full screen", color = Color(0xFF69ED70), fontSize = 11.sp)
                            }
                        }
                        Text("↗", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // 3 Mode chips: Focus, Short Break, Long Break
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip(
                    modifier = Modifier.weight(1f),
                    label = "Focus",
                    icon = "⏳",
                    isSelected = selectedMode == 0,
                    onClick = { onSelectMode(0) },
                )
                ModeChip(
                    modifier = Modifier.weight(1f),
                    label = "Short Break",
                    icon = "☕",
                    isSelected = selectedMode == 1,
                    onClick = { onSelectMode(1) },
                )
                ModeChip(
                    modifier = Modifier.weight(1f),
                    label = "Long Break",
                    icon = "🍃",
                    isSelected = selectedMode == 2,
                    onClick = { onSelectMode(2) },
                )
            }

            // Big Pill Button: "▶ Start Focus" / "⏸ Pause Focus" / "▶ Resume Focus"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onToggleTimer,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF69ED70),
                        contentColor = Color(0xFF081C09),
                    ),
                ) {
                    val buttonText = when {
                        timer?.running == true -> "⏸ Pause Focus"
                        timer != null -> "▶ Resume Focus"
                        else -> "▶ Start Focus"
                    }
                    Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                if (timer != null) {
                    Surface(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onFinishTimer),
                        shape = CircleShape,
                        color = Color(0xFF261919),
                        border = BorderStroke(1.dp, Color(0xFF4A2525)),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⏹", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) Color(0xFF1A331E) else Color(0xFF171D17),
        border = BorderStroke(1.dp, if (isSelected) Color(0xFF69ED70) else Color(0xFF252C25)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(icon, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                color = if (isSelected) Color(0xFF69ED70) else Color(0xFFA4AAA4),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatBox(
    modifier: Modifier = Modifier,
    icon: String,
    iconBg: Color,
    iconColor: Color,
    value: String,
    label: String,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF101510),
        border = BorderStroke(1.dp, Color(0xFF1E281E)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, color = iconColor, fontSize = 14.sp)
            }
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(label, color = Color(0xFFA4AAA4), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TodayScheduleCard(
    schedule: StudySchedule,
    nowMillis: Long,
    onClick: () -> Unit,
) {
    val nowCalendar = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val currentMinute = nowCalendar.get(Calendar.HOUR_OF_DAY) * 60 + nowCalendar.get(Calendar.MINUTE)
    val diffMinutes = schedule.startMinute - currentMinute

    val statusText = when {
        diffMinutes > 60 -> "Starts in ${diffMinutes / 60}h ${diffMinutes % 60}m"
        diffMinutes > 0 -> "Starts in ${diffMinutes}m"
        currentMinute < schedule.endMinute -> "Active now"
        else -> "Completed"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF101510),
        border = BorderStroke(1.dp, Color(0xFF1E281E)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF132A16)),
                contentAlignment = Alignment.Center,
            ) {
                Text("📖", fontSize = 18.sp)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(schedule.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "${formatClockTime12h(schedule.startMinute)} – ${formatClockTime12h(schedule.endMinute)}",
                    color = Color(0xFFA4AAA4),
                    fontSize = 11.sp,
                )
                Text(
                    "${schedule.durationMinutes} min study block",
                    color = Color(0xFFA4AAA4),
                    fontSize = 11.sp,
                )
            }
            // Status pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (diffMinutes <= 60 && diffMinutes > 0) Color(0xFF162E1A) else Color(0xFF171D17),
                border = BorderStroke(1.dp, if (diffMinutes <= 60 && diffMinutes > 0) Color(0xFF69ED70) else Color(0xFF252C25)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("🕒", fontSize = 11.sp)
                    Text(
                        statusText,
                        color = if (diffMinutes <= 60 && diffMinutes > 0) Color(0xFF69ED70) else Color(0xFFA4AAA4),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickToolBox(
    modifier: Modifier = Modifier,
    icon: String,
    iconBg: Color,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = Color(0xFF101510),
        border = BorderStroke(1.dp, Color(0xFF1E281E)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon, color = iconColor, fontSize = 15.sp)
                }
                Text("›", color = Color(0xFFA4AAA4), fontSize = 16.sp)
            }
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color(0xFFA4AAA4), fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun GoalDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    var customGoal by rememberSaveable { mutableStateOf(currentGoal.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val presets = listOf(60, 90, 120, 180, 240, 300)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily study goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            OutlinedButton(
                                onClick = { onSelect(minutes) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("${minutes}m")
                            }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(
                    value = customGoal,
                    onValueChange = {
                        customGoal = it.filter(Char::isDigit).take(3)
                        error = null
                    },
                    label = { Text("Custom minutes (15–600)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = customGoal.toIntOrNull()
                    if (value == null || value !in LocalStudyStore.MIN_DAILY_GOAL_MINUTES..LocalStudyStore.MAX_DAILY_GOAL_MINUTES) {
                        error = "Enter a goal between 15 and 600 minutes."
                    } else {
                        onSelect(value)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FocusScreen(
    store: LocalStudyStore,
    scheduleId: Long?,
    onSessionChanged: () -> Unit,
    onOpenSchedules: () -> Unit,
) {
    val context = LocalContext.current
    val scheduledFocus = remember(scheduleId) {
        scheduleId?.let(store::schedule)?.takeIf { it.enabled }
    }
    var timer by remember { mutableStateOf(store.activeTimer()) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedMinutes by rememberSaveable(scheduleId) {
        mutableIntStateOf(scheduledFocus?.focusIntervalMinutes ?: LocalStudyStore.DEFAULT_FOCUS_MINUTES)
    }
    var goal by rememberSaveable(scheduleId) { mutableStateOf(scheduledFocus?.title.orEmpty()) }
    var sessionSaved by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(FocusTimerAlarms.notificationsEnabled(context)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsEnabled = granted && FocusTimerAlarms.notificationsEnabled(context)
    }

    LaunchedEffect(Unit) {
        notificationsEnabled = FocusTimerAlarms.notificationsEnabled(context)
        FocusTimerAlarms.restore(context)
    }

    LaunchedEffect(timer?.running, timer?.segmentStartedAtMillis) {
        while (timer?.running == true) {
            delay(1_000)
            nowMillis = System.currentTimeMillis()
            val active = timer ?: break
            if (active.remainingAt(nowMillis) == 0L) {
                val completed = FocusTimerAlarms.complete(context, store, nowMillis)
                timer = store.activeTimer()
                if (completed || timer == null) {
                    sessionSaved = true
                    onSessionChanged()
                }
                break
            }
        }
    }

    val remainingMillis = timer?.remainingAt(nowMillis) ?: selectedMinutes * 60_000L
    val remainingSeconds = remainingMillis / 1_000
    val timeLabel = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BrandMark()
        Text("Focus session", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = Panel,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = timeLabel,
                    color = if (timer?.running == true) Mint else MaterialTheme.colorScheme.onSurface,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (timer == null) "Choose a session length" else timer?.goal?.ifBlank { "Focused study" }.orEmpty(),
                    color = Muted,
                )
                if (timer == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(25, 45, 60).forEach { minutes ->
                            OutlinedButton(
                                onClick = { selectedMinutes = minutes },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (selectedMinutes == minutes) Mint else Muted,
                                ),
                            ) {
                                Text("${minutes}m")
                            }
                        }
                    }
                    OutlinedTextField(
                        value = goal,
                        onValueChange = { goal = it.take(80) },
                        label = { Text("What are you studying?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            store.startTimer(selectedMinutes, goal)
                            timer = store.activeTimer()
                            timer?.let { FocusTimerAlarms.schedule(context, it) }
                            nowMillis = System.currentTimeMillis()
                            sessionSaved = false
                            onSessionChanged()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                    ) {
                        Text("Start $selectedMinutes-minute session", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val active = timer ?: return@Button
                                if (active.running) {
                                    store.pauseTimer(active, nowMillis)
                                    FocusTimerAlarms.cancel(context)
                                } else {
                                    store.resumeTimer(active, nowMillis)
                                    store.activeTimer()?.let { FocusTimerAlarms.schedule(context, it) }
                                }
                                timer = store.activeTimer()
                                onSessionChanged()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                        ) {
                            Text(if (timer?.running == true) "Pause" else "Resume")
                        }
                        OutlinedButton(
                            onClick = {
                                store.finishTimer(completed = false, nowMillis = nowMillis)
                                FocusTimerAlarms.cancel(context)
                                timer = null
                                sessionSaved = true
                                onSessionChanged()
                            },
                        ) {
                            Text("End session")
                        }
                    }
                }
            }
        }

        if (sessionSaved) {
            Text(
                "Session saved on this device.",
                color = Mint,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (!notificationsEnabled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Panel,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Get a focus completion alert", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Android may delay the alert to save battery. Your session is still saved if notifications are off.",
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    android.content.Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                    ) {
                        Text("Enable session alerts")
                    }
                }
            }
        }
        Text(
            "Your timer and study notes stay on this device.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = onOpenSchedules) {
            Text("Open weekly study plan", color = Mint)
        }
    }
}

@Composable
private fun ScheduleScreen(
    store: LocalStudyStore,
    onSchedulesChanged: () -> Unit,
) {
    val context = LocalContext.current
    var schedules by remember { mutableStateOf(store.schedules()) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(StudyScheduleAlarms.notificationsEnabled(context))
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsEnabled = StudyScheduleAlarms.notificationsEnabled(context)
    }

    LaunchedEffect(Unit) {
        schedules.filter { it.enabled }.forEach {
            StudyScheduleAlarms.schedule(context, it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BrandMark()
        Text("Study plan", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("Weekly study blocks saved offline.", color = Muted)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Panel,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (notificationsEnabled) "Local reminders are enabled" else "Allow local reminders",
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Reminders run on this device. Android may delay them to save battery.",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!notificationsEnabled) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                context.startActivity(
                                    android.content.Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                    ) {
                        Text("Enable reminders")
                    }
                }
            }
        }
        Button(
            onClick = { showAddDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
        ) {
            Text("Add a study block")
        }

        if (schedules.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Panel,
            ) {
                Text(
                    "No study blocks yet. Add a day, start time, and duration to plan a routine.",
                    modifier = Modifier.padding(18.dp),
                    color = Muted,
                )
            }
        } else {
            schedules.forEach { schedule ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Panel,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(schedule.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${dayName(schedule.dayOfWeek)} | ${formatClockTime(schedule.startMinute)} | ${schedule.durationMinutes} min",
                                    color = Muted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Switch(
                                checked = schedule.enabled,
                                onCheckedChange = {
                                    store.setScheduleEnabled(schedule.id, it)
                                    store.schedule(schedule.id)?.let { updated ->
                                        StudyScheduleAlarms.schedule(context, updated)
                                    }
                                    schedules = store.schedules()
                                    onSchedulesChanged()
                                },
                            )
                        }
                        HorizontalDivider(color = Color(0xFF303936))
                        TextButton(
                            onClick = {
                                store.deleteSchedule(schedule.id)
                                StudyScheduleAlarms.cancel(context, schedule.id)
                                schedules = store.schedules()
                                onSchedulesChanged()
                            },
                        ) {
                            Text("Remove", color = Muted)
                        }
                    }
                }
            }
        }
        Text(
            "Reminders repeat weekly at the saved start time. Android may deliver them later than scheduled.",
            color = Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (showAddDialog) {
        ScheduleDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, day, minute, duration ->
                val scheduleId = store.addSchedule(title, day, minute, duration)
                store.schedule(scheduleId)?.let { StudyScheduleAlarms.schedule(context, it) }
                schedules = store.schedules()
                showAddDialog = false
                onSchedulesChanged()
            },
        )
    }
}

@Composable
private fun ScheduleDialog(
    onDismiss: () -> Unit,
    onSave: (String, Int, Int, Int) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedDay by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) }
    var startTime by rememberSaveable { mutableStateOf("17:00") }
    var duration by rememberSaveable { mutableStateOf("45") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val days = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New study block") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Study topic") },
                    singleLine = true,
                )
                Text("Day", color = Muted)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    days.forEach { day ->
                        OutlinedButton(
                            onClick = { selectedDay = day },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (selectedDay == day) Mint else Muted,
                            ),
                        ) {
                            Text(dayName(day).take(2))
                        }
                    }
                }
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it.take(5) },
                    label = { Text("Start time (HH:MM)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(3) },
                    label = { Text("Duration in minutes (5-240)") },
                    singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsedTime = parseClockTime(startTime)
                    val parsedDuration = duration.toIntOrNull()
                    val validDuration = parsedDuration?.takeIf { it in 5..240 }
                    when {
                        title.isBlank() -> error = "Add a topic for this study block."
                        parsedTime == null -> error = "Enter a valid time such as 17:30."
                        validDuration == null -> error = "Duration must be between 5 and 240 minutes."
                        else -> onSave(title, selectedDay, parsedTime, validDuration)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ProgressScreen(
    sessions: List<FocusSessionRecord>,
    weeklyReport: List<DailyFocusReport>,
    dailyGoalMinutes: Int,
) {
    val totalSeconds = weeklyReport.sumOf { it.elapsedSeconds }
    val todaySeconds = weeklyReport.lastOrNull()?.elapsedSeconds ?: 0L
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandMark()
        Text("Your progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = Panel,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("${totalSeconds / 60} min", color = Mint, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("focused in the last 7 days", color = Muted)
                Text(
                    "Today: ${todaySeconds / 60} / $dailyGoalMinutes min goal",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Text("Daily focus", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        weeklyReport.forEach { day ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Panel,
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            DateFormat.getDateInstance(DateFormat.MEDIUM)
                                .format(Date(day.dayStartMillis)),
                            fontWeight = FontWeight.Medium,
                        )
                        Text("${day.sessionCount} focus sessions", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("${day.elapsedSeconds / 60} min", color = Mint, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (sessions.isEmpty()) {
            Text("Complete a focus session to start building your history.", color = Muted)
        } else {
            sessions.forEach { session ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = Panel,
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(session.goal.ifBlank { "Study session" }, fontWeight = FontWeight.Medium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                    .format(Date(session.startedAtMillis)),
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${session.elapsedSeconds / 60} min", color = Mint, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (session.completed) "Completed" else "Ended early",
                                color = Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseClockTime(value: String): Int? {
    val match = Regex("^(\\d{1,2}):(\\d{2})$").matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour * 60 + minute
}

private fun formatClockTime(minuteOfDay: Int): String =
    "%02d:%02d".format(Locale.getDefault(), minuteOfDay / 60, minuteOfDay % 60)

private fun dayName(dayOfWeek: Int): String =
    Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, dayOfWeek)
    }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty()

@Composable
private fun ComingSoonScreen(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandMark()
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(message, color = Muted, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)
    }
}
