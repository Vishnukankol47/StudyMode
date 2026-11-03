package com.studymode.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ----------------------------------------------------------------------
// THEME COLORS MATCHING UI MOCKUP
// ----------------------------------------------------------------------
private val FocusDarkBg = Color(0xFF07120D)
private val FocusCardBg = Color(0xFF0E1A14)
private val FocusCardBorder = Color(0xFF1A2B22)
private val FocusCardInner = Color(0xFF0B1610)
private val FocusGreen = Color(0xFF4ADE80)
private val FocusGreenDark = Color(0xFF22C55E)
private val FocusGreenPillBg = Color(0xFF4ADE80)
private val FocusGreenPillText = Color(0xFF081C09)
private val FocusTextWhite = Color(0xFFF0FDF4)
private val FocusTextMuted = Color(0xFF8E9993)
private val FocusBarTrackBg = Color(0xFF132219)
private val FocusFlameOrange = Color(0xFFF97316)

enum class FocusAnalysisTab(val label: String) {
    OVERVIEW("Overview"),
    SESSIONS("Sessions"),
    PATTERNS("Patterns"),
    INSIGHTS("Insights"),
}

enum class FocusDateRange(val label: String) {
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_MONTH("This Month"),
    LAST_3_MONTHS("Last 3 Months"),
    CUSTOM("Custom Range"),
}

enum class TrendAggregation(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
}

data class FocusPeriodRange(
    val start: Long,
    val end: Long,
    val prevStart: Long,
    val prevEnd: Long,
    val label: String,
    val badgeLabel: String,
)

data class FocusDayBarData(
    val dayLabel: String,
    val durationLabel: String,
    val durationMinutes: Int,
    val dateMillis: Long,
    val sessionCount: Int,
    val hasData: Boolean,
    val isSelected: Boolean,
)

// ----------------------------------------------------------------------
// VECTOR ICONS
// ----------------------------------------------------------------------
@Composable
fun FocusBarChartVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.22f
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        // 3 vertical bars: short, tall, medium
        val h1 = h * 0.48f
        drawRoundRect(color, Offset(w * 0.08f, h - h1), Size(barW, h1), r)
        val h2 = h * 0.95f
        drawRoundRect(color, Offset(w * 0.39f, h - h2), Size(barW, h2), r)
        val h3 = h * 0.68f
        drawRoundRect(color, Offset(w * 0.70f, h - h3), Size(barW, h3), r)
    }
}

@Composable
private fun ClockVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val r = minOf(w, h) / 2f * 0.88f
        val center = Offset(w / 2f, h / 2f)
        val stroke = 1.6.dp.toPx()

        drawCircle(color, r, center, style = Stroke(stroke))
        drawLine(color, center, Offset(center.x, center.y - r * 0.52f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(center.x + r * 0.42f, center.y), stroke, StrokeCap.Round)
    }
}

@Composable
private fun TargetVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val c = Offset(w / 2f, h / 2f)
        val stroke = 1.6.dp.toPx()
        val r = minOf(w, h) / 2f * 0.88f

        drawCircle(color, r, c, style = Stroke(stroke))
        drawCircle(color, r * 0.55f, c, style = Stroke(stroke))
        drawCircle(color, r * 0.22f, c)
    }
}

@Composable
private fun TrophyVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()

        val cupPath = Path().apply {
            moveTo(w * 0.28f, h * 0.18f)
            lineTo(w * 0.72f, h * 0.18f)
            lineTo(w * 0.68f, h * 0.55f)
            cubicTo(w * 0.65f, h * 0.72f, w * 0.35f, h * 0.72f, w * 0.32f, h * 0.55f)
            close()
        }
        drawPath(cupPath, color, style = Stroke(stroke, join = StrokeJoin.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.70f), Offset(w * 0.5f, h * 0.85f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.32f, h * 0.85f), Offset(w * 0.68f, h * 0.85f), stroke, StrokeCap.Round)

        val leftHandle = Path().apply {
            moveTo(w * 0.28f, h * 0.26f)
            cubicTo(w * 0.12f, h * 0.28f, w * 0.12f, h * 0.52f, w * 0.31f, h * 0.52f)
        }
        drawPath(leftHandle, color, style = Stroke(stroke, cap = StrokeCap.Round))

        val rightHandle = Path().apply {
            moveTo(w * 0.72f, h * 0.26f)
            cubicTo(w * 0.88f, h * 0.28f, w * 0.88f, h * 0.52f, w * 0.69f, h * 0.52f)
        }
        drawPath(rightHandle, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun FlameVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val flame = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            cubicTo(w * 0.65f, h * 0.28f, w * 0.85f, h * 0.52f, w * 0.78f, h * 0.75f)
            cubicTo(w * 0.72f, h * 0.92f, w * 0.55f, h * 0.92f, w * 0.5f, h * 0.92f)
            cubicTo(w * 0.45f, h * 0.92f, w * 0.28f, h * 0.92f, w * 0.22f, h * 0.75f)
            cubicTo(w * 0.15f, h * 0.52f, w * 0.35f, h * 0.35f, w * 0.5f, h * 0.12f)
            close()
        }
        drawPath(flame, color)
    }
}

@Composable
private fun CalendarVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.22f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(stroke)
        )
        drawLine(color, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.88f, h * 0.42f), stroke)
        drawLine(color, Offset(w * 0.32f, h * 0.12f), Offset(w * 0.32f, h * 0.26f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.12f), Offset(w * 0.68f, h * 0.26f), stroke, StrokeCap.Round)
    }
}

// ----------------------------------------------------------------------
// MAIN FOCUS ANALYSIS SCREEN
// ----------------------------------------------------------------------
@Composable
fun FocusAnalysisScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToPlan: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToBlocks: () -> Unit,
    onDataChanged: () -> Unit = {},
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ensureSampleFocusSessions(store)
    }

    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(FocusAnalysisTab.OVERVIEW) }
    var selectedDateRange by rememberSaveable { mutableStateOf(FocusDateRange.THIS_WEEK) }
    var showDatePickerDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomRangeDialog by rememberSaveable { mutableStateOf(false) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }

    var customStartMillis by rememberSaveable { mutableLongStateOf(0L) }
    var customEndMillis by rememberSaveable { mutableLongStateOf(0L) }

    var selectedBarIndex by rememberSaveable { mutableIntStateOf(-1) }
    var trendAggregation by rememberSaveable { mutableStateOf(TrendAggregation.DAILY) }
    var showAggregationDropdown by rememberSaveable { mutableStateOf(false) }

    // Dialogs state
    var showDailyBreakdownDialog by rememberSaveable { mutableStateOf(false) }
    var showSessionDurationDialog by rememberSaveable { mutableStateOf(false) }
    var showGoalProgressDialog by rememberSaveable { mutableStateOf(false) }
    var showStreakHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<FocusSessionRecord?>(null) }
    var showAddSessionDialog by rememberSaveable { mutableStateOf(false) }
    var sessionsFilterCompletedOnly by rememberSaveable { mutableStateOf(false) }
    var selectedDateForSessions by rememberSaveable { mutableLongStateOf(0L) }
    var selectedSessionIdForDetails by rememberSaveable { mutableStateOf<Long?>(null) }
    var activePatternsDetailScreen by rememberSaveable { mutableStateOf(PatternsDetailScreen.NONE) }
    var activeInsightDetailScreen by rememberSaveable { mutableStateOf(InsightDetailScreen.NONE) }
    var filterWeekday by rememberSaveable { mutableStateOf<Int?>(null) }
    var filterHour by rememberSaveable { mutableStateOf<Int?>(null) }
    var filterHourRange by rememberSaveable { mutableStateOf<IntRange?>(null) }
    var filterLengthBracket by rememberSaveable { mutableStateOf<SessionLengthBracket?>(null) }

    val allSessions = remember(refreshKey) { store.focusSessions() }
    val dailyGoalMinutes = remember(refreshKey) { store.dailyGoalMinutes() }

    BackHandler {
        if (selectedSessionIdForDetails != null) {
            selectedSessionIdForDetails = null
        } else if (activePatternsDetailScreen != PatternsDetailScreen.NONE) {
            activePatternsDetailScreen = PatternsDetailScreen.NONE
        } else if (activeInsightDetailScreen != InsightDetailScreen.NONE) {
            activeInsightDetailScreen = InsightDetailScreen.NONE
        } else {
            onBack()
        }
    }

    if (selectedSessionIdForDetails != null) {
        val detailSession = store.focusSession(selectedSessionIdForDetails!!)
            ?: allSessions.find { it.id == selectedSessionIdForDetails }
        if (detailSession != null) {
            FocusSessionDetailsScreen(
                store = store,
                session = detailSession,
                onBack = { selectedSessionIdForDetails = null },
                onSessionUpdated = {
                    refreshKey += 1
                    onDataChanged()
                },
                onSessionDeleted = {
                    selectedSessionIdForDetails = null
                    refreshKey += 1
                    onDataChanged()
                }
            )
            return
        } else {
            selectedSessionIdForDetails = null
        }
    }
    val streaks = remember(refreshKey) { store.focusStreaks() }
    val currentStreak = streaks.first
    val longestStreak = streaks.second

    // Check if January 2024 demo data is present
    val jan2024Session = remember(allSessions) {
        allSessions.firstOrNull {
            val c = Calendar.getInstance().apply { timeInMillis = it.startedAtMillis }
            c.get(Calendar.YEAR) == 2024 && c.get(Calendar.MONTH) == Calendar.JANUARY
        }
    }
    val defaultAnchorMillis = remember(allSessions, jan2024Session) {
        if (jan2024Session != null) {
            Calendar.getInstance().apply {
                set(2024, Calendar.JANUARY, 14, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        } else {
            System.currentTimeMillis()
        }
    }

    if (customStartMillis == 0L || customEndMillis == 0L) {
        val startC = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endC = Calendar.getInstance().apply {
            timeInMillis = startC.timeInMillis
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        customStartMillis = startC.timeInMillis
        customEndMillis = endC.timeInMillis
    }

    // Compute period range
    val periodRange = remember(selectedDateRange, defaultAnchorMillis, customStartMillis, customEndMillis) {
        val calEnd = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        val calStart = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        when (selectedDateRange) {
            FocusDateRange.THIS_WEEK -> {
                calStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calEnd.timeInMillis = calStart.timeInMillis
                calEnd.add(Calendar.DAY_OF_YEAR, 6)
                calEnd.set(Calendar.HOUR_OF_DAY, 23); calEnd.set(Calendar.MINUTE, 59); calEnd.set(Calendar.SECOND, 59); calEnd.set(Calendar.MILLISECOND, 999)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                val startFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val badge = "This Week (${startFmt.format(Date(calStart.timeInMillis))} – ${endFmt.format(Date(calEnd.timeInMillis))})"
                FocusPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "last week", badge)
            }
            FocusDateRange.LAST_WEEK -> {
                calStart.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calStart.add(Calendar.DAY_OF_YEAR, -7)
                calEnd.timeInMillis = calStart.timeInMillis
                calEnd.add(Calendar.DAY_OF_YEAR, 6)
                calEnd.set(Calendar.HOUR_OF_DAY, 23); calEnd.set(Calendar.MINUTE, 59); calEnd.set(Calendar.SECOND, 59); calEnd.set(Calendar.MILLISECOND, 999)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                val startFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val badge = "Last Week (${startFmt.format(Date(calStart.timeInMillis))} – ${endFmt.format(Date(calEnd.timeInMillis))})"
                FocusPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "prior week", badge)
            }
            FocusDateRange.LAST_30_DAYS -> {
                calStart.add(Calendar.DAY_OF_YEAR, -29)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                val startFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val badge = "Last 30 Days (${startFmt.format(Date(calStart.timeInMillis))} – ${endFmt.format(Date(calEnd.timeInMillis))})"
                FocusPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "prior 30 days", badge)
            }
            FocusDateRange.THIS_MONTH -> {
                calStart.set(Calendar.DAY_OF_MONTH, 1)
                calEnd.set(Calendar.DAY_OF_MONTH, calStart.getActualMaximum(Calendar.DAY_OF_MONTH))
                val prevStart = Calendar.getInstance().apply {
                    timeInMillis = calStart.timeInMillis
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val prevEnd = Calendar.getInstance().apply {
                    timeInMillis = prevStart.timeInMillis
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
                val badge = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(calStart.timeInMillis))
                FocusPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, prevStart.timeInMillis, prevEnd.timeInMillis, "last month", badge)
            }
            FocusDateRange.LAST_3_MONTHS -> {
                calStart.add(Calendar.MONTH, -3)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                val startFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val badge = "Last 3 Months (${startFmt.format(Date(calStart.timeInMillis))} – ${endFmt.format(Date(calEnd.timeInMillis))})"
                FocusPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "prior 3 months", badge)
            }
            FocusDateRange.CUSTOM -> {
                val dur = customEndMillis - customStartMillis
                val startFmt = SimpleDateFormat("MMM d", Locale.getDefault())
                val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val badge = "${startFmt.format(Date(customStartMillis))} – ${endFmt.format(Date(customEndMillis))}"
                FocusPeriodRange(customStartMillis, customEndMillis, customStartMillis - dur - 1000L, customStartMillis - 1000L, "prior period", badge)
            }
        }
    }

    val periodSessions = remember(allSessions, periodRange) {
        allSessions.filter { it.startedAtMillis in periodRange.start..periodRange.end }.sortedBy { it.startedAtMillis }
    }
    val prevSessions = remember(allSessions, periodRange) {
        allSessions.filter { it.startedAtMillis in periodRange.prevStart..periodRange.prevEnd }.sortedBy { it.startedAtMillis }
    }

    if (activePatternsDetailScreen == PatternsDetailScreen.BEST_FOCUS_TIMES) {
        BestFocusTimesDetailScreen(
            sessions = periodSessions,
            onBack = { activePatternsDetailScreen = PatternsDetailScreen.NONE },
            onNavigateToSessionsForHour = { hour ->
                filterHour = hour
                filterWeekday = null
                filterLengthBracket = null
                selectedDateForSessions = 0L
                selectedTab = FocusAnalysisTab.SESSIONS
                activePatternsDetailScreen = PatternsDetailScreen.NONE
            }
        )
        return
    } else if (activePatternsDetailScreen == PatternsDetailScreen.DAY_OF_WEEK) {
        FocusByDayOfWeekDetailScreen(
            sessions = periodSessions,
            onBack = { activePatternsDetailScreen = PatternsDetailScreen.NONE },
            onNavigateToSessionsForWeekday = { dow ->
                filterWeekday = dow
                filterHour = null
                filterLengthBracket = null
                selectedDateForSessions = 0L
                selectedTab = FocusAnalysisTab.SESSIONS
                activePatternsDetailScreen = PatternsDetailScreen.NONE
            }
        )
        return
    } else if (activePatternsDetailScreen == PatternsDetailScreen.SESSION_LENGTH) {
        StudySessionLengthDetailScreen(
            sessions = periodSessions,
            prevSessions = prevSessions,
            onBack = { activePatternsDetailScreen = PatternsDetailScreen.NONE },
            onNavigateToSessionsForBracket = { bracket ->
                filterLengthBracket = bracket
                filterWeekday = null
                filterHour = null
                selectedDateForSessions = 0L
                selectedTab = FocusAnalysisTab.SESSIONS
                activePatternsDetailScreen = PatternsDetailScreen.NONE
            }
        )
        return
    }

    if (activeInsightDetailScreen == InsightDetailScreen.WEEKLY_IMPROVEMENT) {
        WeeklyImprovementDetailScreen(
            currentSessions = periodSessions,
            prevSessions = prevSessions,
            onBack = { activeInsightDetailScreen = InsightDetailScreen.NONE },
            onViewThisWeekSessions = {
                selectedDateRange = FocusDateRange.THIS_WEEK
                filterWeekday = null
                filterHour = null
                filterHourRange = null
                filterLengthBracket = null
                selectedDateForSessions = 0L
                selectedTab = FocusAnalysisTab.SESSIONS
                activeInsightDetailScreen = InsightDetailScreen.NONE
            },
            onViewLastWeekSessions = {
                selectedDateRange = FocusDateRange.LAST_WEEK
                filterWeekday = null
                filterHour = null
                filterHourRange = null
                filterLengthBracket = null
                selectedDateForSessions = 0L
                selectedTab = FocusAnalysisTab.SESSIONS
                activeInsightDetailScreen = InsightDetailScreen.NONE
            },
            onOpenDailyBreakdown = { showDailyBreakdownDialog = true },
            onOpenSessionDuration = { showSessionDurationDialog = true }
        )
        return
    } else if (activeInsightDetailScreen == InsightDetailScreen.BEST_FOCUS_TIME) {
        BestFocusTimeDetailScreen(
            sessions = periodSessions,
            onBack = { activeInsightDetailScreen = InsightDetailScreen.NONE },
            onNavigateToSessionsForHourRange = { range ->
                filterHourRange = range
                filterHour = null
                filterWeekday = null
                filterLengthBracket = null
                selectedDateForSessions = 0L
                selectedTab = FocusAnalysisTab.SESSIONS
                activeInsightDetailScreen = InsightDetailScreen.NONE
            }
        )
        return
    }

    // 1. Total Focus Time (minutes)
    val totalFocusMinutes = remember(periodSessions) {
        (periodSessions.sumOf { it.elapsedSeconds } / 60L).toInt()
    }
    val prevTotalFocusMinutes = remember(prevSessions) {
        (prevSessions.sumOf { it.elapsedSeconds } / 60L).toInt()
    }
    val deltaTotalMinutes = totalFocusMinutes - prevTotalFocusMinutes

    // 2. Completed Sessions
    val completedSessionsCount = remember(periodSessions) {
        periodSessions.count { it.completed }
    }
    val prevCompletedCount = remember(prevSessions) {
        prevSessions.count { it.completed }
    }
    val deltaCompletedCount = completedSessionsCount - prevCompletedCount

    // 3. Average Session Duration
    val avgSessionMinutes = remember(periodSessions, completedSessionsCount) {
        if (completedSessionsCount > 0) {
            val compSecs = periodSessions.filter { it.completed }.sumOf { it.elapsedSeconds }
            ((compSecs / completedSessionsCount) / 60L).toInt()
        } else 0
    }
    val prevAvgSessionMinutes = remember(prevSessions, prevCompletedCount) {
        if (prevCompletedCount > 0) {
            val compSecs = prevSessions.filter { it.completed }.sumOf { it.elapsedSeconds }
            ((compSecs / prevCompletedCount) / 60L).toInt()
        } else 0
    }
    val deltaAvgMinutes = avgSessionMinutes - prevAvgSessionMinutes

    // 4. Goal Completion
    val dailyMap = remember(periodSessions) {
        val map = mutableMapOf<Long, Long>()
        val cal = Calendar.getInstance()
        periodSessions.forEach {
            cal.timeInMillis = it.startedAtMillis
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            map[dayStart] = (map[dayStart] ?: 0L) + it.elapsedSeconds
        }
        map
    }
    val trackedDaysCount = dailyMap.size
    val daysMetGoalCount = remember(dailyMap, dailyGoalMinutes) {
        dailyMap.count { it.value >= dailyGoalMinutes * 60L }
    }
    val goalCompletionPercent = remember(trackedDaysCount, daysMetGoalCount) {
        if (trackedDaysCount > 0) (daysMetGoalCount * 100) / trackedDaysCount else 0
    }

    val prevDailyMap = remember(prevSessions) {
        val map = mutableMapOf<Long, Long>()
        val cal = Calendar.getInstance()
        prevSessions.forEach {
            cal.timeInMillis = it.startedAtMillis
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val dayStart = cal.timeInMillis
            map[dayStart] = (map[dayStart] ?: 0L) + it.elapsedSeconds
        }
        map
    }
    val prevTrackedDaysCount = prevDailyMap.size
    val prevDaysMetGoalCount = remember(prevDailyMap, dailyGoalMinutes) {
        prevDailyMap.count { it.value >= dailyGoalMinutes * 60L }
    }
    val prevGoalPercent = remember(prevTrackedDaysCount, prevDaysMetGoalCount) {
        if (prevTrackedDaysCount > 0) (prevDaysMetGoalCount * 100) / prevTrackedDaysCount else 0
    }
    val deltaGoalPercent = goalCompletionPercent - prevGoalPercent

    // Bars for Focus Time Trend
    val trendBars = remember(periodRange, periodSessions, selectedBarIndex) {
        buildFocusBarData(periodRange.start, periodRange.end, periodSessions, selectedBarIndex)
    }

    Scaffold(
        containerColor = FocusDarkBg,
        topBar = {
            FocusAnalysisHeader(
                subtitle = if (selectedTab == FocusAnalysisTab.INSIGHTS) {
                    "Discover insights from your study habits"
                } else {
                    "Track your study progress and build better habits"
                },
                onBack = onBack,
                onCalendarClick = { showDatePickerDialog = true },
                onMoreClick = { showMoreMenu = true }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. TABS ROW
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0C1610))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FocusAnalysisTab.values().forEach { tab ->
                    val isSelected = tab == selectedTab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) FocusGreen else Color.Transparent)
                            .clickable { selectedTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSelected) FocusGreenPillText else FocusTextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }

            // 2. DATE RANGE BADGE DROPDOWN PILL
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1F22C55E))
                    .border(1.dp, Color(0x3322C55E), RoundedCornerShape(12.dp))
                    .clickable { showDatePickerDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CalendarVectorIcon(color = FocusGreen, modifier = Modifier.size(13.dp))
                    Text(
                        text = periodRange.badgeLabel,
                        color = FocusGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Text("▼", color = FocusGreen, fontSize = 8.sp)
                }
            }

            // CONTENT BASED ON ACTIVE TAB
            when (selectedTab) {
                FocusAnalysisTab.OVERVIEW -> {
                    // Overview Content matching the mockup!
                    OverviewTabContent(
                        totalFocusMinutes = totalFocusMinutes,
                        deltaTotalMinutes = deltaTotalMinutes,
                        completedCount = completedSessionsCount,
                        deltaCompletedCount = deltaCompletedCount,
                        avgSessionMinutes = avgSessionMinutes,
                        deltaAvgMinutes = deltaAvgMinutes,
                        goalCompletionPercent = goalCompletionPercent,
                        deltaGoalPercent = deltaGoalPercent,
                        comparisonSubtitle = "vs ${periodRange.label}",
                        dailyGoalMinutes = dailyGoalMinutes,
                        trackedDaysCount = trackedDaysCount,
                        daysMetGoalCount = daysMetGoalCount,
                        currentStreak = currentStreak,
                        longestStreak = longestStreak,
                        trendBars = trendBars,
                        selectedBarIndex = selectedBarIndex,
                        trendAggregation = trendAggregation,
                        onSelectBarIndex = { idx ->
                            selectedBarIndex = if (selectedBarIndex == idx) -1 else idx
                        },
                        onSelectAggregation = { trendAggregation = it },
                        onTotalTimeClick = { showDailyBreakdownDialog = true },
                        onCompletedSessionsClick = {
                            sessionsFilterCompletedOnly = true
                            selectedTab = FocusAnalysisTab.SESSIONS
                        },
                        onAvgSessionClick = { showSessionDurationDialog = true },
                        onGoalCompletionClick = { showGoalProgressDialog = true },
                        onGoalProgressCardClick = { showGoalProgressDialog = true },
                        onStreakCardClick = { showStreakHistoryDialog = true },
                        onViewSessionsForBar = { barData ->
                            selectedDateForSessions = barData.dateMillis
                            selectedTab = FocusAnalysisTab.SESSIONS
                        }
                    )

                    // Time Saved Impact Banner (Phase 1)
                    val timeSavedSummary = remember { calculateTimeSaved(context, store) }
                    var showTimeSavedDialog by remember { mutableStateOf(false) }

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
                }

                FocusAnalysisTab.SESSIONS -> {
                    SessionsTabContent(
                        sessions = periodSessions,
                        filterCompletedOnly = sessionsFilterCompletedOnly,
                        selectedDateFilter = selectedDateForSessions,
                        filterWeekday = filterWeekday,
                        filterHour = filterHour,
                        filterHourRange = filterHourRange,
                        filterLengthBracket = filterLengthBracket,
                        onClearDateFilter = { selectedDateForSessions = 0L },
                        onClearPatternFilters = {
                            filterWeekday = null
                            filterHour = null
                            filterHourRange = null
                            filterLengthBracket = null
                        },
                        onToggleFilterCompleted = { sessionsFilterCompletedOnly = !sessionsFilterCompletedOnly },
                        onAddSessionClick = { showAddSessionDialog = true },
                        onSessionClick = { session -> selectedSessionIdForDetails = session.id },
                        onEditSession = { session -> editingSession = session },
                        onDeleteSession = { id ->
                            store.deleteFocusSession(id)
                            refreshKey += 1
                            onDataChanged()
                            Toast.makeText(context, "Session deleted", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                FocusAnalysisTab.PATTERNS -> {
                    PatternsDashboardContent(
                        sessions = periodSessions,
                        prevSessions = prevSessions,
                        onOpenBestFocusTimes = { activePatternsDetailScreen = PatternsDetailScreen.BEST_FOCUS_TIMES },
                        onOpenDayOfWeek = { activePatternsDetailScreen = PatternsDetailScreen.DAY_OF_WEEK },
                        onOpenSessionLength = { activePatternsDetailScreen = PatternsDetailScreen.SESSION_LENGTH },
                        onNavigateToSessionsForBracket = { bracket ->
                            filterLengthBracket = bracket
                            filterWeekday = null
                            filterHour = null
                            selectedDateForSessions = 0L
                            selectedTab = FocusAnalysisTab.SESSIONS
                        },
                        onNavigateToSessionsForWeekday = { dow ->
                            filterWeekday = dow
                            filterHour = null
                            filterLengthBracket = null
                            selectedDateForSessions = 0L
                            selectedTab = FocusAnalysisTab.SESSIONS
                        },
                        onNavigateToSessionsForHour = { hour ->
                            filterHour = hour
                            filterWeekday = null
                            filterLengthBracket = null
                            selectedDateForSessions = 0L
                            selectedTab = FocusAnalysisTab.SESSIONS
                        }
                    )
                }

                FocusAnalysisTab.INSIGHTS -> {
                    InsightsDashboardContent(
                        sessions = periodSessions,
                        prevSessions = prevSessions,
                        goalCompletionPercent = goalCompletionPercent,
                        daysMetGoalCount = daysMetGoalCount,
                        trackedDaysCount = trackedDaysCount,
                        avgSessionMinutes = avgSessionMinutes,
                        currentStreak = currentStreak,
                        longestStreak = longestStreak,
                        onOpenWeeklyImprovement = {
                            activeInsightDetailScreen = InsightDetailScreen.WEEKLY_IMPROVEMENT
                        },
                        onOpenBestFocusTime = {
                            activeInsightDetailScreen = InsightDetailScreen.BEST_FOCUS_TIME
                        },
                        onOpenGoalProgress = {
                            showGoalProgressDialog = true
                        },
                        onOpenSessionLength = {
                            activePatternsDetailScreen = PatternsDetailScreen.SESSION_LENGTH
                        },
                        onOpenStreakHistory = {
                            showStreakHistoryDialog = true
                        },
                        onOpenDayOfWeek = {
                            activePatternsDetailScreen = PatternsDetailScreen.DAY_OF_WEEK
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    // Dropdown / Date Picker Menu
    if (showDatePickerDialog) {
        AlertDialog(
            onDismissRequest = { showDatePickerDialog = false },
            containerColor = Color(0xFF0F1E15),
            title = {
                Text("Select Date Range", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusDateRange.values().forEach { range ->
                        val isSelected = range == selectedDateRange
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showDatePickerDialog = false
                                    if (range == FocusDateRange.CUSTOM) {
                                        showCustomRangeDialog = true
                                    } else {
                                        selectedDateRange = range
                                    }
                                },
                            color = if (isSelected) Color(0x3322C55E) else FocusCardInner,
                            border = BorderStroke(1.dp, if (isSelected) FocusGreen else FocusCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = range.label,
                                    color = if (isSelected) FocusGreen else FocusTextWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.5.sp
                                )
                                if (isSelected) {
                                    Text("✓", color = FocusGreen, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDatePickerDialog = false }) {
                    Text("Close", color = FocusGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Custom Range Dialog
    if (showCustomRangeDialog) {
        CustomFocusDateRangeDialog(
            initialStart = customStartMillis,
            initialEnd = customEndMillis,
            onDismiss = { showCustomRangeDialog = false },
            onApply = { start, end ->
                customStartMillis = start
                customEndMillis = end
                selectedDateRange = FocusDateRange.CUSTOM
                showCustomRangeDialog = false
            }
        )
    }

    // More Options Menu Dialog
    if (showMoreMenu) {
        AlertDialog(
            onDismissRequest = { showMoreMenu = false },
            containerColor = Color(0xFF0F1E15),
            title = {
                Text("Focus Analysis Options", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreMenu = false
                                exportFocusSessionsAsCsv(context, allSessions)
                            },
                        color = FocusCardInner,
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Text("Export as CSV", color = FocusTextWhite, modifier = Modifier.padding(12.dp))
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreMenu = false
                                exportFocusSessionsAsJson(context, allSessions)
                            },
                        color = FocusCardInner,
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Text("Export as JSON", color = FocusTextWhite, modifier = Modifier.padding(12.dp))
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreMenu = false
                                seedDemoFocusSessions(store)
                                refreshKey += 1
                                onDataChanged()
                                Toast.makeText(context, "Sample focus data loaded", Toast.LENGTH_SHORT).show()
                            },
                        color = FocusCardInner,
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Text("Restore Sample Data", color = FocusGreen, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(12.dp))
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                showMoreMenu = false
                                store.clearAllFocusSessions()
                                refreshKey += 1
                                onDataChanged()
                                Toast.makeText(context, "Focus sessions cleared", Toast.LENGTH_SHORT).show()
                            },
                        color = FocusCardInner,
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Text("Clear All Focus Records", color = Color(0xFFF87171), modifier = Modifier.padding(12.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMoreMenu = false }) {
                    Text("Close", color = FocusGreen)
                }
            }
        )
    }

    // Daily Breakdown Dialog
    if (showDailyBreakdownDialog) {
        DailyFocusBreakdownDialog(
            dailyMap = dailyMap,
            sessions = periodSessions,
            dailyGoalMinutes = dailyGoalMinutes,
            onDismiss = { showDailyBreakdownDialog = false }
        )
    }

    // Session Duration Breakdown Dialog
    if (showSessionDurationDialog) {
        SessionDurationBreakdownDialog(
            sessions = periodSessions.filter { it.completed },
            avgMinutes = avgSessionMinutes,
            onDismiss = { showSessionDurationDialog = false }
        )
    }

    // Goal Progress Calendar Dialog
    if (showGoalProgressDialog) {
        GoalProgressCalendarDialog(
            dailyMap = dailyMap,
            dailyGoalMinutes = dailyGoalMinutes,
            onDismiss = { showGoalProgressDialog = false },
            onDateClick = { dateMillis ->
                showGoalProgressDialog = false
                selectedDateForSessions = dateMillis
                selectedTab = FocusAnalysisTab.SESSIONS
            }
        )
    }

    // Focus Streak History Dialog
    if (showStreakHistoryDialog) {
        FocusStreakHistoryDialog(
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            completedDays = store.completedFocusSessions().map { it.first },
            onDismiss = { showStreakHistoryDialog = false }
        )
    }

    // Add / Edit Session Dialog
    if (showAddSessionDialog || editingSession != null) {
        FocusSessionEditorDialog(
            existing = editingSession,
            onDismiss = {
                showAddSessionDialog = false
                editingSession = null
            },
            onSave = { saved ->
                store.saveFocusSession(saved)
                refreshKey += 1
                onDataChanged()
                showAddSessionDialog = false
                editingSession = null
                Toast.makeText(context, "Session saved", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ----------------------------------------------------------------------
// HEADER
// ----------------------------------------------------------------------
@Composable
private fun FocusAnalysisHeader(
    subtitle: String = "Track your study progress and build better habits",
    onBack: () -> Unit,
    onCalendarClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = FocusDarkBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Text("←", color = FocusGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Focus Analysis",
                    color = FocusTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp
                )
                Text(
                    text = subtitle,
                    color = FocusTextMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onCalendarClick, modifier = Modifier.size(36.dp)) {
                    CalendarVectorIcon(color = FocusGreen, modifier = Modifier.size(19.dp))
                }
                IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                    Text("⋮", color = FocusGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// OVERVIEW TAB CONTENT (MATCHING SCREEN 2 MOCKUP)
// ----------------------------------------------------------------------
@Composable
private fun OverviewTabContent(
    totalFocusMinutes: Int,
    deltaTotalMinutes: Int,
    completedCount: Int,
    deltaCompletedCount: Int,
    avgSessionMinutes: Int,
    deltaAvgMinutes: Int,
    goalCompletionPercent: Int,
    deltaGoalPercent: Int,
    comparisonSubtitle: String,
    dailyGoalMinutes: Int,
    trackedDaysCount: Int,
    daysMetGoalCount: Int,
    currentStreak: Int,
    longestStreak: Int,
    trendBars: List<FocusDayBarData>,
    selectedBarIndex: Int,
    trendAggregation: TrendAggregation,
    onSelectBarIndex: (Int) -> Unit,
    onSelectAggregation: (TrendAggregation) -> Unit,
    onTotalTimeClick: () -> Unit,
    onCompletedSessionsClick: () -> Unit,
    onAvgSessionClick: () -> Unit,
    onGoalCompletionClick: () -> Unit,
    onGoalProgressCardClick: () -> Unit,
    onStreakCardClick: () -> Unit,
    onViewSessionsForBar: (FocusDayBarData) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. FOUR STAT CARDS IN 2x2 GRID
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Total Focus Time
            val h = totalFocusMinutes / 60
            val m = totalFocusMinutes % 60
            val deltaH = Math.abs(deltaTotalMinutes) / 60
            val deltaM = Math.abs(deltaTotalMinutes) % 60
            val deltaText = if (deltaTotalMinutes >= 0) "↑ +${deltaH}h ${deltaM}m" else "↓ -${deltaH}h ${deltaM}m"

            FocusStatCard(
                modifier = Modifier.weight(1f),
                icon = { ClockVectorIcon(color = FocusGreen, modifier = Modifier.size(15.dp)) },
                value = if (totalFocusMinutes > 0) "${h}h ${m}m" else "0h 00m",
                title = "Total focus time",
                deltaText = deltaText,
                subtitle = comparisonSubtitle,
                onClick = onTotalTimeClick
            )

            // Card 2: Completed Sessions
            FocusStatCard(
                modifier = Modifier.weight(1f),
                icon = { TargetVectorIcon(color = FocusGreen, modifier = Modifier.size(15.dp)) },
                value = "$completedCount",
                title = "Completed sessions",
                deltaText = if (deltaCompletedCount >= 0) "↑ +$deltaCompletedCount" else "↓ $deltaCompletedCount",
                subtitle = comparisonSubtitle,
                onClick = onCompletedSessionsClick
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Average Session
            FocusStatCard(
                modifier = Modifier.weight(1f),
                icon = { FocusBarChartVectorIcon(color = FocusGreen, modifier = Modifier.size(15.dp)) },
                value = "$avgSessionMinutes min",
                title = "Avg. session",
                deltaText = if (deltaAvgMinutes >= 0) "↑ +$deltaAvgMinutes min" else "↓ $deltaAvgMinutes min",
                subtitle = comparisonSubtitle,
                onClick = onAvgSessionClick
            )

            // Card 4: Goal Completion
            FocusStatCard(
                modifier = Modifier.weight(1f),
                icon = { TrophyVectorIcon(color = FocusGreen, modifier = Modifier.size(15.dp)) },
                value = "$goalCompletionPercent%",
                title = "Goal completion",
                deltaText = if (deltaGoalPercent >= 0) "↑ +$deltaGoalPercent%" else "↓ $deltaGoalPercent%",
                subtitle = comparisonSubtitle,
                onClick = onGoalCompletionClick
            )
        }

        // 2. FOCUS TIME TREND CARD
        var showAggMenu by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = FocusCardBg,
            border = BorderStroke(1.dp, FocusCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            FocusBarChartVectorIcon(color = FocusGreen, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "Focus Time Trend",
                            color = FocusTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                    }

                    // Aggregation Dropdown: Daily ∨
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F22C55E))
                                .border(1.dp, Color(0x3322C55E), RoundedCornerShape(8.dp))
                                .clickable { showAggMenu = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = trendAggregation.label,
                                    color = FocusGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("▼", color = FocusGreen, fontSize = 7.sp)
                            }
                        }

                        DropdownMenu(
                            expanded = showAggMenu,
                            onDismissRequest = { showAggMenu = false },
                            modifier = Modifier.background(FocusCardInner)
                        ) {
                            TrendAggregation.values().forEach { agg ->
                                DropdownMenuItem(
                                    text = { Text(agg.label, color = FocusTextWhite, fontSize = 12.sp) },
                                    onClick = {
                                        onSelectAggregation(agg)
                                        showAggMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Chart Canvas
                FocusTrendBarChart(
                    dayItems = trendBars,
                    targetMinutes = dailyGoalMinutes,
                    selectedBarIndex = selectedBarIndex,
                    onBarClick = onSelectBarIndex,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                // Tooltip / selected day details if a bar is selected
                if (selectedBarIndex in trendBars.indices) {
                    val item = trendBars[selectedBarIndex]
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = FocusCardInner,
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val dateFormatted = if (item.dateMillis > 0L) {
                                    SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date(item.dateMillis))
                                } else item.dayLabel
                                Text(dateFormatted, color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                                Text(
                                    text = "${item.durationLabel} focused  •  ${item.sessionCount} session${if (item.sessionCount != 1) "s" else ""}",
                                    color = FocusTextMuted,
                                    fontSize = 10.5.sp
                                )
                            }

                            if (item.hasData) {
                                TextButton(
                                    onClick = { onViewSessionsForBar(item) },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("View Sessions ›", color = FocusGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. SIDE-BY-SIDE SECTION: GOAL PROGRESS & FOCUS STREAK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left Card: Goal Progress
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onGoalProgressCardClick() },
                color = FocusCardBg,
                border = BorderStroke(1.dp, FocusCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            TargetVectorIcon(color = FocusGreen, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "Goal Progress",
                            color = FocusTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "$goalCompletionPercent%",
                        color = FocusTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )

                    LinearProgressIndicator(
                        progress = { (goalCompletionPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = FocusGreen,
                        trackColor = FocusBarTrackBg
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$daysMetGoalCount of $trackedDaysCount days met your ${dailyGoalMinutes / 60}h goal",
                            color = FocusTextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("›", color = FocusTextMuted, fontSize = 13.sp)
                    }
                }
            }

            // Right Card: Focus Streak
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onStreakCardClick() },
                color = FocusCardBg,
                border = BorderStroke(1.dp, FocusCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x26F97316)),
                            contentAlignment = Alignment.Center
                        ) {
                            FlameVectorIcon(color = FocusFlameOrange, modifier = Modifier.size(15.dp))
                        }
                        Text(
                            text = "Focus Streak",
                            color = FocusTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "$currentStreak day${if (currentStreak != 1) "s" else ""}",
                                color = FocusTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text("Current streak", color = FocusTextMuted, fontSize = 9.5.sp)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "$longestStreak days",
                                    color = FocusTextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text("Longest streak", color = FocusTextMuted, fontSize = 9.5.sp)
                            }
                            Text("›", color = FocusTextMuted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// STAT CARD COMPONENT
// ----------------------------------------------------------------------
@Composable
private fun FocusStatCard(
    icon: @Composable () -> Unit,
    value: String,
    title: String,
    deltaText: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        color = FocusCardBg,
        border = BorderStroke(1.dp, FocusCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0x1F22C55E)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Text(
                text = value,
                color = FocusTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                maxLines = 1
            )

            Text(
                text = title,
                color = FocusTextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = deltaText,
                    color = FocusGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
                Text(
                    text = subtitle,
                    color = FocusTextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// FOCUS TREND BAR CHART
// ----------------------------------------------------------------------
@Composable
private fun FocusTrendBarChart(
    dayItems: List<FocusDayBarData>,
    targetMinutes: Int,
    selectedBarIndex: Int,
    onBarClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxMinutes = 180f // 3 hours scale
    val yLabels = listOf("3h", "2h", "1h", "0h")

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            // Y-axis labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yLabels.forEach { label ->
                    Text(text = label, color = FocusTextMuted, fontSize = 8.5.sp)
                }
            }

            // Canvas with Bars & Goal line
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 3 intervals = 4 grid lines
                    val gridLines = 3
                    for (i in 0..gridLines) {
                        val y = (h / gridLines) * i
                        drawLine(
                            color = Color(0x1F22C55E),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Dashed goal reference line
                    val goalRatio = (targetMinutes / maxMinutes).coerceIn(0f, 1f)
                    val goalY = h - (goalRatio * h)
                    drawLine(
                        color = Color(0x9922C55E),
                        start = Offset(0f, goalY),
                        end = Offset(w, goalY),
                        strokeWidth = 1.3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    // Bars
                    val count = dayItems.size.coerceAtLeast(1)
                    val slotW = w / count
                    val barW = minOf(slotW * 0.65f, 22.dp.toPx()).coerceAtLeast(6.dp.toPx())

                    dayItems.forEachIndexed { index, item ->
                        val cx = slotW * index + slotW / 2f
                        val left = cx - barW / 2f

                        // Background track
                        drawRoundRect(
                            color = FocusBarTrackBg,
                            topLeft = Offset(left, 0f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Filled bar
                        if (item.hasData && item.durationMinutes > 0) {
                            val durRatio = (item.durationMinutes / maxMinutes).coerceIn(0f, 1f)
                            val barH = h * durRatio
                            val barTop = h - barH

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF86EFAC), FocusGreenDark),
                                    startY = barTop,
                                    endY = h
                                ),
                                topLeft = Offset(left, barTop),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            if (index == selectedBarIndex) {
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(left - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                    size = Size(barW + 2.dp.toPx(), barH + 2.dp.toPx()),
                                    cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Goal badge overlay on right of dashed line
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xD9064E3B))
                        .border(1.dp, Color(0x4022C55E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Goal: ${targetMinutes / 60}h",
                        color = FocusGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Tappable overlay columns
                Row(modifier = Modifier.fillMaxSize()) {
                    dayItems.forEachIndexed { index, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onBarClick(index) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // X-axis: Day labels & Duration labels below
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            dayItems.forEachIndexed { index, item ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onBarClick(index) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.dayLabel,
                        color = if (index == selectedBarIndex) FocusGreen else FocusTextWhite,
                        fontSize = 10.sp,
                        fontWeight = if (index == selectedBarIndex) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(
                        text = item.durationLabel,
                        color = FocusTextMuted,
                        fontSize = 8.5.sp
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// SESSIONS TAB CONTENT
// ----------------------------------------------------------------------
@Composable
private fun SessionsTabContent(
    sessions: List<FocusSessionRecord>,
    filterCompletedOnly: Boolean,
    selectedDateFilter: Long,
    filterWeekday: Int? = null,
    filterHour: Int? = null,
    filterHourRange: IntRange? = null,
    filterLengthBracket: SessionLengthBracket? = null,
    onClearDateFilter: () -> Unit,
    onClearPatternFilters: () -> Unit = {},
    onToggleFilterCompleted: () -> Unit,
    onAddSessionClick: () -> Unit,
    onSessionClick: (FocusSessionRecord) -> Unit = {},
    onEditSession: (FocusSessionRecord) -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    val cal = Calendar.getInstance()
    val filteredSessions = remember(sessions, filterCompletedOnly, selectedDateFilter, filterWeekday, filterHour, filterHourRange, filterLengthBracket) {
        sessions.filter { session ->
            val matchCompleted = !filterCompletedOnly || session.completed
            val matchDate = if (selectedDateFilter > 0L) {
                cal.timeInMillis = session.startedAtMillis
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis == selectedDateFilter
            } else true
            val matchWeekday = if (filterWeekday != null) {
                cal.timeInMillis = session.startedAtMillis
                cal.get(Calendar.DAY_OF_WEEK) == filterWeekday
            } else true
            val matchHour = if (filterHour != null) {
                cal.timeInMillis = session.startedAtMillis
                cal.get(Calendar.HOUR_OF_DAY) == filterHour
            } else true
            val matchHourRange = if (filterHourRange != null) {
                cal.timeInMillis = session.startedAtMillis
                val h = cal.get(Calendar.HOUR_OF_DAY)
                if (filterHourRange.first <= filterHourRange.last) {
                    h in filterHourRange
                } else {
                    h >= filterHourRange.first || h <= filterHourRange.last
                }
            } else true
            val matchLength = if (filterLengthBracket != null) {
                filterLengthBracket.matches(session.elapsedSeconds)
            } else true
            matchCompleted && matchDate && matchWeekday && matchHour && matchHourRange && matchLength
        }
    }

    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = !filterCompletedOnly,
                    onClick = { if (filterCompletedOnly) onToggleFilterCompleted() },
                    label = { Text("All (${sessions.size})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FocusGreen,
                        selectedLabelColor = FocusGreenPillText,
                        containerColor = FocusCardInner,
                        labelColor = FocusTextMuted
                    )
                )

                FilterChip(
                    selected = filterCompletedOnly,
                    onClick = { if (!filterCompletedOnly) onToggleFilterCompleted() },
                    label = { Text("Completed (${sessions.count { it.completed }})", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = FocusGreen,
                        selectedLabelColor = FocusGreenPillText,
                        containerColor = FocusCardInner,
                        labelColor = FocusTextMuted
                    )
                )
            }

            Button(
                onClick = onAddSessionClick,
                colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("+ Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (selectedDateFilter > 0L) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = FocusCardInner,
                border = BorderStroke(1.dp, FocusCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filtered by date: ${dateFormat.format(Date(selectedDateFilter))}",
                        color = FocusGreen,
                        fontSize = 11.5.sp
                    )
                    TextButton(onClick = onClearDateFilter, contentPadding = PaddingValues(0.dp)) {
                        Text("Clear filter", color = FocusTextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        val patternFilterLabel = when {
            filterWeekday != null -> {
                val dowName = when (filterWeekday) {
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    Calendar.SATURDAY -> "Saturday"
                    Calendar.SUNDAY -> "Sunday"
                    else -> "Day of week"
                }
                "Filtered by: $dowName"
            }
            filterHour != null -> {
                val nextH = (filterHour + 1) % 24
                val hStr = if (filterHour < 12) (if (filterHour == 0) "12 AM" else "$filterHour AM") else (if (filterHour == 12) "12 PM" else "${filterHour - 12} PM")
                val nextStr = if (nextH < 12) (if (nextH == 0) "12 AM" else "$nextH AM") else (if (nextH == 12) "12 PM" else "${nextH - 12} PM")
                "Filtered by: $hStr – $nextStr"
            }
            filterHourRange != null -> {
                val startH = filterHourRange.first
                val endH = (filterHourRange.last + 1) % 24
                val startStr = if (startH == 0) "12 AM" else if (startH < 12) "$startH AM" else if (startH == 12) "12 PM" else "${startH - 12} PM"
                val endStr = if (endH == 0) "12 AM" else if (endH < 12) "$endH AM" else if (endH == 12) "12 PM" else "${endH - 12} PM"
                "Filtered by: $startStr – $endStr"
            }
            filterLengthBracket != null -> "Filtered by: ${filterLengthBracket.label}"
            else -> null
        }

        if (patternFilterLabel != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = FocusCardInner,
                border = BorderStroke(1.dp, FocusCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = patternFilterLabel,
                        color = FocusGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = onClearPatternFilters, contentPadding = PaddingValues(0.dp)) {
                        Text("Clear filter", color = FocusTextMuted, fontSize = 11.sp)
                    }
                }
            }
        }

        if (filteredSessions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = FocusCardBg,
                border = BorderStroke(1.dp, FocusCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No focus sessions found", color = FocusTextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Complete a timer session or tap '+ Add' to record one.", color = FocusTextMuted, fontSize = 11.sp)
                }
            }
        } else {
            filteredSessions.forEach { session ->
                val focusMins = session.elapsedSeconds / 60
                val breakMins = session.breakSeconds / 60
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSessionClick(session) },
                    color = FocusCardBg,
                    border = BorderStroke(1.dp, FocusCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1F22C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (session.mode) {
                                        "Pomodoro" -> "☕"
                                        "Stopwatch" -> "⏱"
                                        else -> "⏳"
                                    },
                                    fontSize = 16.sp
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (session.goal.isNotBlank()) session.goal else "Study Focus",
                                    color = FocusTextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    text = "${dateFormat.format(Date(session.startedAtMillis))} • ${timeFormat.format(Date(session.startedAtMillis))} – ${timeFormat.format(Date(session.finishedAtMillis))}",
                                    color = FocusTextMuted,
                                    fontSize = 10.5.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "${focusMins}m focused",
                                        color = FocusGreen,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (breakMins > 0) {
                                        Text(
                                            text = "• ${breakMins}m break",
                                            color = FocusTextMuted,
                                            fontSize = 10.5.sp
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (session.completed) Color(0x1F22C55E) else Color(0x26FBBF24))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (session.completed) "Completed" else "Incomplete",
                                color = if (session.completed) FocusGreen else Color(0xFFFBBF24),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// PATTERNS TAB CONTENT
// ----------------------------------------------------------------------
@Composable
private fun PatternsTabContent(
    sessions: List<FocusSessionRecord>,
    targetMinutes: Int,
) {
    val morningMins = sessions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.startedAtMillis }
        c.get(Calendar.HOUR_OF_DAY) in 6..11
    }.sumOf { it.elapsedSeconds } / 60

    val afternoonMins = sessions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.startedAtMillis }
        c.get(Calendar.HOUR_OF_DAY) in 12..17
    }.sumOf { it.elapsedSeconds } / 60

    val eveningMins = sessions.filter {
        val c = Calendar.getInstance().apply { timeInMillis = it.startedAtMillis }
        c.get(Calendar.HOUR_OF_DAY) in 18..23
    }.sumOf { it.elapsedSeconds } / 60

    val totalMins = (morningMins + afternoonMins + eveningMins).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = FocusCardBg,
            border = BorderStroke(1.dp, FocusCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Study Time Distribution", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("When you are most focused during the day", color = FocusTextMuted, fontSize = 11.5.sp)

                PatternBarRow(label = "Morning (6 AM – 12 PM)", minutes = morningMins.toInt(), total = totalMins.toInt(), color = FocusGreen)
                PatternBarRow(label = "Afternoon (12 PM – 6 PM)", minutes = afternoonMins.toInt(), total = totalMins.toInt(), color = Color(0xFF86EFAC))
                PatternBarRow(label = "Evening (6 PM – 12 AM)", minutes = eveningMins.toInt(), total = totalMins.toInt(), color = Color(0xFFA78BFA))
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = FocusCardBg,
            border = BorderStroke(1.dp, FocusCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Session Length Sweet Spot", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(
                    text = "Most of your successful focus sessions are between 30 and 45 minutes in length. Sessions within this range show a 92% completion rate without distraction breaks.",
                    color = FocusTextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun PatternBarRow(label: String, minutes: Int, total: Int, color: Color) {
    val percent = ((minutes.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = FocusTextWhite, fontSize = 11.5.sp)
            Text("${minutes / 60}h ${minutes % 60}m ($percent%)", color = FocusTextMuted, fontSize = 11.sp)
        }
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(3.5.dp)),
            color = color,
            trackColor = FocusBarTrackBg
        )
    }
}

// ----------------------------------------------------------------------
// INSIGHTS TAB CONTENT
// ----------------------------------------------------------------------
@Composable
private fun InsightsTabContent(
    sessions: List<FocusSessionRecord>,
    goalCompletionPercent: Int,
    currentStreak: Int,
    longestStreak: Int,
    targetDailyMinutes: Int,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InsightCard(
            emoji = "⚡",
            title = "Peak Focus Window",
            desc = "Your study efficiency peaks between 9:00 AM and 11:30 AM with minimal interruption. Scheduling high-complexity topics here yields better retention."
        )

        InsightCard(
            emoji = "🎯",
            title = "Target Adherence",
            desc = "You hit your ${targetDailyMinutes / 60}h daily goal on $goalCompletionPercent% of active days. Consistency is trending upwards compared to the prior period."
        )

        InsightCard(
            emoji = "🔥",
            title = "Streak Momentum",
            desc = "Current streak is $currentStreak day${if (currentStreak != 1) "s" else ""}. You are on track to challenge your personal record of $longestStreak consecutive days."
        )

        InsightCard(
            emoji = "💡",
            title = "Recommended Action",
            desc = "Take structured 5-minute pauses after 45 minutes of intense focus. Regular micro-breaks prevent cognitive fatigue and sustain long-term stamina."
        )
    }
}

@Composable
private fun InsightCard(emoji: String, title: String, desc: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = FocusCardBg,
        border = BorderStroke(1.dp, FocusCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(emoji, fontSize = 20.sp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(desc, color = FocusTextMuted, fontSize = 11.5.sp, lineHeight = 16.sp)
            }
        }
    }
}

// ----------------------------------------------------------------------
// DIALOGS: GOAL PROGRESS CALENDAR, STREAK, BREAKDOWNS, EDITOR
// ----------------------------------------------------------------------
@Composable
private fun GoalProgressCalendarDialog(
    dailyMap: Map<Long, Long>,
    dailyGoalMinutes: Int,
    onDismiss: () -> Unit,
    onDateClick: (Long) -> Unit,
) {
    val cal = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val monthDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val monthTitle = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Goal Progress Calendar", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(monthTitle, color = FocusGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Daily target: ${dailyGoalMinutes / 60}h (${dailyGoalMinutes} min). Tap a day to view sessions.",
                    color = FocusTextMuted,
                    fontSize = 11.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                        Text(it, color = FocusTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    }
                }

                val targetSecs = dailyGoalMinutes * 60L
                val daysList = (1..monthDays).toList()

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    daysList.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            week.forEach { dayNum ->
                                val dayCal = Calendar.getInstance().apply {
                                    timeInMillis = cal.timeInMillis
                                    set(Calendar.DAY_OF_MONTH, dayNum)
                                }
                                val dayStart = dayCal.timeInMillis
                                val elapsedSec = dailyMap[dayStart] ?: 0L
                                val isMet = elapsedSec >= targetSecs
                                val hasData = elapsedSec > 0

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isMet -> FocusGreen
                                                hasData -> Color(0xFFFBBF24)
                                                else -> FocusCardInner
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (hasData) Color.Transparent else FocusCardBorder,
                                            shape = CircleShape
                                        )
                                        .clickable { onDateClick(dayStart) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isMet) FocusGreenPillText else if (hasData) Color(0xFF081C09) else FocusTextMuted,
                                        fontSize = 10.sp,
                                        fontWeight = if (hasData) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(FocusGreen))
                        Text("Goal met", color = FocusTextWhite, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFFBBF24)))
                        Text("Below goal", color = FocusTextWhite, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(FocusCardInner).border(1.dp, FocusCardBorder, CircleShape))
                        Text("No data", color = FocusTextMuted, fontSize = 9.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun FocusStreakHistoryDialog(
    currentStreak: Int,
    longestStreak: Int,
    completedDays: List<Long>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔥", fontSize = 18.sp)
                Text("Focus Streak History", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = FocusCardInner,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Current Streak", color = FocusTextMuted, fontSize = 10.sp)
                            Text("$currentStreak day${if (currentStreak != 1) "s" else ""}", color = FocusGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = FocusCardInner,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, FocusCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Longest Streak", color = FocusTextMuted, fontSize = 10.sp)
                            Text("$longestStreak days", color = FocusFlameOrange, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }

                Text(
                    text = "A qualifying streak day is counted whenever at least one focus session is completed on that local calendar day.\n\nStay consistent daily to maintain your momentum!",
                    color = FocusTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText)
            ) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun DailyFocusBreakdownDialog(
    dailyMap: Map<Long, Long>,
    sessions: List<FocusSessionRecord>,
    dailyGoalMinutes: Int,
    onDismiss: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
    val targetSecs = dailyGoalMinutes * 60L

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Text("Daily Focus Time Breakdown", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (dailyMap.isEmpty()) {
                    Text("No focus time logged for this period.", color = FocusTextMuted, fontSize = 12.sp)
                } else {
                    dailyMap.entries.sortedByDescending { it.key }.forEach { (dayStart, secs) ->
                        val m = secs / 60
                        val isMet = secs >= targetSecs
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = FocusCardInner,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, FocusCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(dateFormat.format(Date(dayStart)), color = FocusTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("${m / 60}h ${m % 60}m", color = FocusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isMet) Color(0x1F22C55E) else Color(0x26FBBF24))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isMet) "Met" else "Below",
                                        color = if (isMet) FocusGreen else Color(0xFFFBBF24),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText)) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun SessionDurationBreakdownDialog(
    sessions: List<FocusSessionRecord>,
    avgMinutes: Int,
    onDismiss: () -> Unit,
) {
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Column {
                Text("Session Duration Details", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Average: $avgMinutes min across ${sessions.size} completed sessions", color = FocusGreen, fontSize = 11.5.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (sessions.isEmpty()) {
                    Text("No completed focus sessions.", color = FocusTextMuted, fontSize = 12.sp)
                } else {
                    sessions.sortedByDescending { it.elapsedSeconds }.forEach { s ->
                        val m = s.elapsedSeconds / 60
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = FocusCardInner,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, FocusCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(s.goal.ifBlank { "Focus Session" }, color = FocusTextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${dateFormat.format(Date(s.startedAtMillis))} • ${timeFormat.format(Date(s.startedAtMillis))}", color = FocusTextMuted, fontSize = 10.sp)
                                }
                                Text("${m} min", color = FocusGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText)) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun CustomFocusDateRangeDialog(
    initialStart: Long,
    initialEnd: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    val context = LocalContext.current
    var startMillis by rememberSaveable { mutableLongStateOf(initialStart) }
    var endMillis by rememberSaveable { mutableLongStateOf(initialEnd) }

    val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
    val endCal = Calendar.getInstance().apply { timeInMillis = endMillis }
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Text("Select Custom Range", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val c = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                                    startMillis = c.timeInMillis
                                    if (endMillis < startMillis) endMillis = startMillis + 86_400_000L * 6
                                },
                                startCal.get(Calendar.YEAR),
                                startCal.get(Calendar.MONTH),
                                startCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    color = FocusCardInner,
                    border = BorderStroke(1.dp, FocusCardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Start Date", color = FocusTextMuted, fontSize = 10.5.sp)
                        Text(fmt.format(Date(startMillis)), color = FocusGreen, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val c = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                                    endMillis = c.timeInMillis
                                    if (endMillis < startMillis) startMillis = endMillis - 86_400_000L * 6
                                },
                                endCal.get(Calendar.YEAR),
                                endCal.get(Calendar.MONTH),
                                endCal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    color = FocusCardInner,
                    border = BorderStroke(1.dp, FocusCardBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("End Date", color = FocusTextMuted, fontSize = 10.5.sp)
                        Text(fmt.format(Date(endMillis)), color = FocusGreen, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(minOf(startMillis, endMillis), maxOf(startMillis, endMillis)) },
                colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusTextMuted)
            }
        }
    )
}

@Composable
private fun FocusSessionEditorDialog(
    existing: FocusSessionRecord?,
    onDismiss: () -> Unit,
    onSave: (FocusSessionRecord) -> Unit,
) {
    val context = LocalContext.current
    var dateMillis by rememberSaveable { mutableLongStateOf(existing?.startedAtMillis ?: System.currentTimeMillis()) }
    var durationMinutes by rememberSaveable { mutableIntStateOf((existing?.elapsedSeconds?.div(60) ?: 45).toInt()) }
    var breakMinutes by rememberSaveable { mutableIntStateOf((existing?.breakSeconds?.div(60) ?: 5).toInt()) }
    var goalText by rememberSaveable { mutableStateOf(existing?.goal ?: "Study Focus") }
    var isCompleted by rememberSaveable { mutableStateOf(existing?.completed ?: true) }
    var mode by rememberSaveable { mutableStateOf(existing?.mode ?: "Focus") }

    val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy - h:mm a", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Text(if (existing != null) "Edit Focus Session" else "Add Focus Session", color = FocusTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Goal input
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    label = { Text("Topic / Goal", color = FocusTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = FocusTextWhite,
                        unfocusedTextColor = FocusTextWhite,
                        focusedBorderColor = FocusGreen,
                        unfocusedBorderColor = FocusCardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Duration Slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Duration", color = FocusTextMuted, fontSize = 11.sp)
                        Text("${durationMinutes}m", color = FocusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Slider(
                        value = durationMinutes.toFloat(),
                        onValueChange = { durationMinutes = it.toInt() },
                        valueRange = 5f..180f,
                        steps = 34,
                        colors = SliderDefaults.colors(thumbColor = FocusGreen, activeTrackColor = FocusGreen)
                    )
                }

                // Completed switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mark as Completed", color = FocusTextWhite, fontSize = 12.sp)
                    Switch(
                        checked = isCompleted,
                        onCheckedChange = { isCompleted = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = FocusGreen, checkedTrackColor = Color(0x3322C55E))
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finish = dateMillis + (durationMinutes + breakMinutes) * 60_000L
                    onSave(
                        FocusSessionRecord(
                            id = existing?.id ?: 0L,
                            startedAtMillis = dateMillis,
                            elapsedSeconds = durationMinutes * 60L,
                            targetMinutes = durationMinutes,
                            goal = goalText,
                            completed = isCompleted,
                            finishedAtMillis = finish,
                            breakSeconds = breakMinutes * 60L,
                            mode = mode
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = FocusGreen, contentColor = FocusGreenPillText)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusTextMuted)
            }
        }
    )
}

// ----------------------------------------------------------------------
// DATA BUILDER UTILITIES
// ----------------------------------------------------------------------
private fun buildFocusBarData(
    startMillis: Long,
    endMillis: Long,
    sessions: List<FocusSessionRecord>,
    selectedIndex: Int
): List<FocusDayBarData> {
    val dayMap = mutableMapOf<Long, MutableList<FocusSessionRecord>>()
    val cal = Calendar.getInstance()
    sessions.forEach { s ->
        cal.timeInMillis = s.startedAtMillis
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val list = dayMap.getOrPut(dayStart) { mutableListOf() }
        list.add(s)
    }

    val calIter = Calendar.getInstance().apply {
        timeInMillis = startMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
    val result = mutableListOf<FocusDayBarData>()
    var count = 0

    while (calIter.timeInMillis <= endMillis && count < 7) {
        val dayStart = calIter.timeInMillis
        val daySessions = dayMap[dayStart] ?: emptyList()
        val totalSecs = daySessions.sumOf { it.elapsedSeconds }
        val totalMins = (totalSecs / 60L).toInt()
        val h = totalMins / 60
        val m = totalMins % 60
        val durationLabel = if (totalMins > 0) "${h}h ${m}m" else "0m"

        result.add(
            FocusDayBarData(
                dayLabel = dayFormat.format(Date(dayStart)),
                durationLabel = durationLabel,
                durationMinutes = totalMins,
                dateMillis = dayStart,
                sessionCount = daySessions.size,
                hasData = totalMins > 0,
                isSelected = count == selectedIndex
            )
        )

        calIter.add(Calendar.DAY_OF_YEAR, 1)
        count++
    }

    return result
}

// ----------------------------------------------------------------------
// DEMO SEEDING MATCHING SCREENSHOT EXACTLY
// ----------------------------------------------------------------------
fun ensureSampleFocusSessions(store: LocalStudyStore) {
    if (store.focusSessions().size >= 10) return
    seedDemoFocusSessions(store)
}

fun seedDemoFocusSessions(store: LocalStudyStore) {
    // January 8 to January 14, 2024 (exact values matching Patterns & Analysis mockups)
    val janCal = Calendar.getInstance().apply {
        set(2024, Calendar.JANUARY, 8, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val monJan8 = janCal.timeInMillis

    // Seed week 2 sessions (Jan 8 - 14, 2024):
    // DayOffset, startHour, startMin, durationMins, breakMins, completed, goal, mode
    val week2Sessions = listOf(
        // Monday (Jan 8): 3 sessions, total 80 min (1h 20m)
        Triple(0, 6 to 30, 25 to 0) to Triple(true, "Deep Study Focus", "Focus"),
        Triple(0, 8 to 20, 30 to 0) to Triple(true, "Algebra Practice", "Focus"),
        Triple(0, 10 to 15, 25 to 0) to Triple(true, "Reading Notes", "Focus"),

        // Tuesday (Jan 9): 2 sessions, total 65 min (1h 5m)
        Triple(1, 9 to 15, 35 to 0) to Triple(true, "Concept Review", "Pomodoro"),
        Triple(1, 11 to 30, 30 to 0) to Triple(true, "Grammar Drills", "Focus"),

        // Wednesday (Jan 10): 2 sessions, total 70 min (1h 10m)
        Triple(2, 8 to 15, 40 to 5) to Triple(true, "Math Exercises", "Focus"),
        Triple(2, 12 to 15, 30 to 0) to Triple(true, "Quick Review", "Focus"),

        // Thursday (Jan 11): 1 session, total 45 min
        Triple(3, 9 to 15, 45 to 0) to Triple(true, "Reading Notes", "Focus"),

        // Friday (Jan 12): 4 sessions, total 110 min (1h 50m)
        Triple(4, 7 to 20, 25 to 0) to Triple(true, "Physics Problems", "Focus"),
        Triple(4, 9 to 20, 35 to 0) to Triple(true, "Formula Practice", "Focus"),
        Triple(4, 10 to 15, 30 to 0) to Triple(true, "Lab Prep", "Focus"),
        Triple(4, 11 to 30, 20 to 0) to Triple(true, "Summary Notes", "Focus"),

        // Saturday (Jan 13): 5 sessions, total 150 min (2h 30m)
        Triple(5, 7 to 30, 30 to 0) to Triple(true, "Algorithms Practice", "Focus"),
        Triple(5, 8 to 15, 50 to 5) to Triple(true, "Data Structures", "Focus"),
        Triple(5, 9 to 30, 30 to 0) to Triple(true, "Coding Problem", "Focus"),
        Triple(5, 10 to 30, 15 to 0) to Triple(true, "Flashcards", "Stopwatch"),
        Triple(5, 13 to 20, 20 to 0) to Triple(true, "Code Review", "Stopwatch"),

        // Sunday (Jan 14): 2 sessions, total 105 min (1h 45m)
        Triple(6, 8 to 30, 80 to 10) to Triple(true, "Math practice", "Focus"),
        Triple(6, 18 to 0, 25 to 0) to Triple(true, "Weekly Wrap-up", "Focus")
    )

    week2Sessions.forEach { (timeInfo, meta) ->
        val dayOffset = timeInfo.first
        val hour = timeInfo.second.first
        val min = timeInfo.second.second
        val durMins = timeInfo.third.first
        val breakMins = timeInfo.third.second
        val completed = meta.first
        val goal = meta.second
        val mode = meta.third

        val start = monJan8 + dayOffset * 86_400_000L + hour * 3600_000L + min * 60_000L
        val finish = start + (durMins + breakMins) * 60_000L

        val isJan14Main = dayOffset == 6 && hour == 8
        val subject = if (isJan14Main) "Math practice" else goal
        val notes = if (isJan14Main) "Algebra practice and problem solving." else ""
        val appsJson = if (isJan14Main) """[
            {"packageName":"com.android.chrome","appName":"Chrome","durationSeconds":720,"isDistraction":false,"launchCount":1},
            {"packageName":"com.google.android.youtube","appName":"YouTube","durationSeconds":300,"isDistraction":true,"launchCount":1},
            {"packageName":"com.google.android.calculator","appName":"Calculator","durationSeconds":120,"isDistraction":false,"launchCount":1}
        ]""".trimIndent() else ""

        val timelineJson = if (isJan14Main) {
            val t1 = start + 40 * 60_000L
            val t2 = t1 + 10 * 60_000L
            val t3 = t2 + 2 * 60_000L
            """[
                {"type":"FOCUS","startMillis":$start,"endMillis":$t1,"label":"Focused","details":"Algebra practice and problem solving."},
                {"type":"BREAK","startMillis":$t1,"endMillis":$t2,"label":"Short break","details":"Scheduled rest break (10 min)"},
                {"type":"DISTRACTION","startMillis":$t2,"endMillis":$t3,"label":"Distracted","details":"App opened: YouTube (2 min)","packageName":"com.google.android.youtube"},
                {"type":"FOCUS","startMillis":$t3,"endMillis":$finish,"label":"Focused","details":"Resumed algebra problem solving."}
            ]""".trimIndent()
        } else ""

        store.saveFocusSession(
            FocusSessionRecord(
                id = 0L,
                startedAtMillis = start,
                elapsedSeconds = durMins * 60L,
                targetMinutes = durMins,
                goal = goal,
                completed = completed,
                finishedAtMillis = finish,
                breakSeconds = breakMins * 60L,
                mode = mode,
                subject = subject,
                notes = notes,
                timelineEventsJson = timelineJson,
                appsUsedJson = appsJson
            )
        )
    }

    // January 1 to 7, 2024: 12 additional sessions across the month to reach 31 total sessions
    // Distribution matches Screen 4 exactly:
    // < 15 min: 3 sessions (10%)
    // 15 - 30 min: 9 sessions (30%)
    // 30 - 60 min: 14 sessions (45%)
    // > 60 min: 5 sessions (15%)
    // Total: 31 sessions, avg 34 min
    val monJan1 = monJan8 - 7 * 86_400_000L
    val week1AdditionalDurations = listOf(
        // < 15 min (2 in week 1, 1 in week 2 = 3)
        10, 12,
        // 15 - 30 min (3 in week 1, 6 in week 2 = 9)
        18, 22, 28,
        // 30 - 60 min (3 in week 1, 11 in week 2 = 14)
        35, 42, 50,
        // > 60 min (4 in week 1, 1 in week 2 = 5)
        70, 75, 85, 90
    )

    week1AdditionalDurations.forEachIndexed { i, dur ->
        val dayOffset = i % 7
        val hour = 9 + (i % 6)
        val start = monJan1 + dayOffset * 86_400_000L + hour * 3600_000L
        val finish = start + dur * 60_000L
        store.saveFocusSession(
            FocusSessionRecord(
                id = 0L,
                startedAtMillis = start,
                elapsedSeconds = dur * 60L,
                targetMinutes = dur,
                goal = "Prior Study Session",
                completed = true,
                finishedAtMillis = finish,
                breakSeconds = 0L,
                mode = "Focus"
            )
        )
    }

    // Prior Month (December 2023): Sessions with average duration 26 min
    // Delta for January: 34 - 26 = +8 min vs last month (matches Screen 4 stat card exactly)
    val decCal = Calendar.getInstance().apply {
        set(2023, Calendar.DECEMBER, 20, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dec20 = decCal.timeInMillis
    for (i in 0 until 12) {
        val start = dec20 + i * 86_400_000L
        store.saveFocusSession(
            FocusSessionRecord(
                id = 0L,
                startedAtMillis = start,
                elapsedSeconds = 26 * 60L,
                targetMinutes = 26,
                goal = "December Study Session",
                completed = true,
                finishedAtMillis = start + 26 * 60_000L,
                breakSeconds = 0L,
                mode = "Focus"
            )
        )
    }
}

// ----------------------------------------------------------------------
// EXPORT UTILITIES
// ----------------------------------------------------------------------
private fun exportFocusSessionsAsCsv(context: Context, sessions: List<FocusSessionRecord>) {
    if (sessions.isEmpty()) {
        Toast.makeText(context, "No focus sessions to export", Toast.LENGTH_SHORT).show()
        return
    }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val csv = buildString {
        appendLine("ID,StartedAt,FinishedAt,ElapsedSeconds,DurationMinutes,Goal,Mode,Completed")
        sessions.forEach { s ->
            val safeGoal = s.goal.replace("\"", "\"\"")
            appendLine("${s.id},${dateFormat.format(Date(s.startedAtMillis))},${dateFormat.format(Date(s.finishedAtMillis))},${s.elapsedSeconds},${s.elapsedSeconds / 60},\"$safeGoal\",${s.mode},${s.completed}")
        }
    }
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, csv)
        type = "text/csv"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export Focus Sessions"))
}

private fun exportFocusSessionsAsJson(context: Context, sessions: List<FocusSessionRecord>) {
    if (sessions.isEmpty()) {
        Toast.makeText(context, "No focus sessions to export", Toast.LENGTH_SHORT).show()
        return
    }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    val json = buildString {
        appendLine("[")
        sessions.forEachIndexed { index, s ->
            val safeGoal = s.goal.replace("\"", "\\\"")
            appendLine("  {")
            appendLine("    \"id\": ${s.id},")
            appendLine("    \"startedAt\": \"${dateFormat.format(Date(s.startedAtMillis))}\",")
            appendLine("    \"finishedAt\": \"${dateFormat.format(Date(s.finishedAtMillis))}\",")
            appendLine("    \"elapsedSeconds\": ${s.elapsedSeconds},")
            appendLine("    \"durationMinutes\": ${s.elapsedSeconds / 60},")
            appendLine("    \"goal\": \"$safeGoal\",")
            appendLine("    \"mode\": \"${s.mode}\",")
            appendLine("    \"completed\": ${s.completed}")
            appendLine(if (index < sessions.size - 1) "  }," else "  }")
        }
        appendLine("]")
    }
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, json)
        type = "application/json"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export Focus Sessions (JSON)"))
}
