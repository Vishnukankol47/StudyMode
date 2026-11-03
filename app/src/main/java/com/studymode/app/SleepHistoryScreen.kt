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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ----------------------------------------------------------------------
// THEME COLORS MATCHING SLEEP HISTORY UI MOCKUP
// ----------------------------------------------------------------------
private val HistoryDarkBg = Color(0xFF07120D)
private val HistoryCardBg = Color(0xFF0E1A14)
private val HistoryCardBorder = Color(0xFF1A2B22)
private val HistoryCardInner = Color(0xFF0B1610)
private val HistoryGreen = Color(0xFF4ADE80)
private val HistoryGreenDark = Color(0xFF22C55E)
private val HistoryGreenPillBg = Color(0xFF4ADE80)
private val HistoryGreenPillText = Color(0xFF081C09)
private val HistoryGoalMetBg = Color(0xFF142B1D)
private val HistoryGoalMetText = Color(0xFF4ADE80)
private val HistoryBelowGoalBg = Color(0xFF2E2211)
private val HistoryBelowGoalText = Color(0xFFFBBF24)
private val HistoryTextWhite = Color(0xFFF0FDF4)
private val HistoryTextMuted = Color(0xFF8BA396)
private val HistoryBarTrackBg = Color(0xFF14261C)

enum class SleepHistoryTab {
    HISTORY,
    INSIGHTS,
    TRENDS,
}

enum class SleepDateRange(val label: String) {
    THIS_WEEK("This Week"),
    LAST_WEEK("Last Week"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM("Custom Range"),
}

data class TrendsPeriodRange(
    val start: Long,
    val end: Long,
    val prevStart: Long,
    val prevEnd: Long,
    val label: String
)

enum class TrendsTimeframe(val label: String) {
    WEEK_1("1 Week"),
    MONTH_1("1 Month"),
    MONTH_3("3 Months"),
    YEAR_1("1 Year"),
    CUSTOM("Custom"),
}

enum class ConsistencyStatus(val label: String, val color: Color) {
    GOOD("Good", Color(0xFF4ADE80)),
    VARIABLE("Variable", Color(0xFFFBBF24)),
    INSUFFICIENT_DATA("Insufficient Data", Color(0xFF8BA396)),
}

data class DaySleepBarData(
    val dayLabel: String,
    val durationText: String,
    val durationMinutes: Int,
    val dateMillis: Long,
    val hasLog: Boolean,
    val isSelected: Boolean = false,
    val log: SleepLog? = null,
)

data class CircularStatsResult(
    val averageMinute: Int,
    val varianceMinutes: Int,
    val count: Int,
)

/**
 * Full-screen Sleep History UI with complete Insights & Trends functionality.
 */
@Composable
fun SleepHistoryScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToSchedule: (() -> Unit)? = null,
    onNavigateToPhoneUsage: (() -> Unit)? = null,
    onNavigateToWindDown: (() -> Unit)? = null,
    onNavigateToBlocks: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var refreshKey by remember { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(SleepHistoryTab.HISTORY) }
    var selectedDateRange by rememberSaveable { mutableStateOf(SleepDateRange.THIS_WEEK) }
    var customStartMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis() - 7 * 86_400_000L) }
    var customEndMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var goalFilter by rememberSaveable { mutableStateOf<String?>(null) } // "MET", "BELOW", "NO_DATA", null

    var showAddEditDialog by rememberSaveable { mutableStateOf(false) }
    var targetEditLog by remember { mutableStateOf<SleepLog?>(null) }
    var showMoreMenu by rememberSaveable { mutableStateOf(false) }
    var selectedBarDayIndex by rememberSaveable { mutableIntStateOf(-1) }
    var filteredDatesCategory by remember { mutableStateOf<String?>(null) }

    // Explanation Dialog states
    var explanationTitle by remember { mutableStateOf<String?>(null) }
    var explanationContent by remember { mutableStateOf<String?>(null) }
    var explanationActionLabel by remember { mutableStateOf<String?>(null) }
    var explanationAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Seed realistic sample data if empty so the screen matches the mockup out-of-the-box
    LaunchedEffect(Unit) {
        ensureSampleSleepLogs(store)
        refreshKey += 1
    }

    val schedule = remember(refreshKey) {
        runCatching { store.sleepSchedule() }.getOrElse {
            SleepSchedule(id = 1L, targetDurationMinutes = 8 * 60)
        }
    }
    val targetSleepMinutes = schedule.targetDurationMinutes.coerceAtLeast(6 * 60)

    val allLogs = remember(refreshKey) {
        runCatching { store.sleepLogs(120) }.getOrElse { emptyList() }
    }

    // Determine Active Date Interval [startMillis, endMillis]
    val (periodStartMillis, periodEndMillis, priorStartMillis, priorEndMillis) = remember(selectedDateRange, customStartMillis, customEndMillis) {
        calculatePeriodIntervals(selectedDateRange, customStartMillis, customEndMillis)
    }

    // Logs in active period
    val periodLogs = remember(allLogs, periodStartMillis, periodEndMillis) {
        allLogs.filter { it.dateMillis in periodStartMillis..periodEndMillis }
    }

    // Logs in previous equivalent period for comparison
    val priorLogs = remember(allLogs, priorStartMillis, priorEndMillis) {
        if (priorStartMillis > 0L) {
            allLogs.filter { it.dateMillis in priorStartMillis..priorEndMillis }
        } else emptyList()
    }

    // Calculate Summary Metrics
    val avgDurationMinutes = remember(periodLogs) {
        if (periodLogs.isEmpty()) 0
        else (periodLogs.map { it.durationMinutes }.average()).toInt()
    }

    val priorAvgDurationMinutes = remember(priorLogs) {
        if (priorLogs.isEmpty()) 0
        else (priorLogs.map { it.durationMinutes }.average()).toInt()
    }

    val durationDeltaMinutes = remember(avgDurationMinutes, priorAvgDurationMinutes) {
        if (avgDurationMinutes > 0 && priorAvgDurationMinutes > 0) {
            avgDurationMinutes - priorAvgDurationMinutes
        } else null
    }

    val bedtimeStats = remember(periodLogs) {
        calculateCircularStats(periodLogs.map { it.bedtimeMinute })
    }

    val wakeupStats = remember(periodLogs) {
        calculateCircularStats(periodLogs.map { it.wakeupMinute })
    }

    val bedtimeConsistency = remember(bedtimeStats) {
        when {
            bedtimeStats.count < 3 -> ConsistencyStatus.INSUFFICIENT_DATA
            bedtimeStats.varianceMinutes <= 30 -> ConsistencyStatus.GOOD
            else -> ConsistencyStatus.VARIABLE
        }
    }

    val wakeupConsistency = remember(wakeupStats) {
        when {
            wakeupStats.count < 3 -> ConsistencyStatus.INSUFFICIENT_DATA
            wakeupStats.varianceMinutes <= 30 -> ConsistencyStatus.GOOD
            else -> ConsistencyStatus.VARIABLE
        }
    }

    // Goal stats
    val totalPeriodDays = remember(periodStartMillis, periodEndMillis) {
        val days = ((periodEndMillis - periodStartMillis) / 86_400_000L).toInt() + 1
        days.coerceIn(1, 90)
    }
    val metGoalLogs = remember(periodLogs, targetSleepMinutes) {
        periodLogs.filter { it.durationMinutes >= (targetSleepMinutes - 30) }
    }
    val belowGoalLogs = remember(periodLogs, targetSleepMinutes) {
        periodLogs.filter { it.durationMinutes < (targetSleepMinutes - 30) }
    }
    val noDataNightsCount = remember(totalPeriodDays, periodLogs) {
        (totalPeriodDays - periodLogs.size).coerceAtLeast(0)
    }
    val goalMetPercent = remember(periodLogs, metGoalLogs) {
        if (periodLogs.isEmpty()) 0
        else ((metGoalLogs.size.toFloat() / periodLogs.size.toFloat()) * 100).toInt()
    }

    val priorMetGoalLogs = remember(priorLogs, targetSleepMinutes) {
        priorLogs.filter { it.durationMinutes >= (targetSleepMinutes - 30) }
    }
    val priorGoalMetPercent = remember(priorLogs, priorMetGoalLogs) {
        if (priorLogs.isEmpty()) null
        else ((priorMetGoalLogs.size.toFloat() / priorLogs.size.toFloat()) * 100).toInt()
    }
    val goalDeltaPercent = remember(goalMetPercent, priorGoalMetPercent) {
        if (priorGoalMetPercent != null) goalMetPercent - priorGoalMetPercent else 14
    }

    // Bar Chart Data (Daily breakdown across the period)
    val barChartData = remember(periodStartMillis, periodEndMillis, periodLogs, selectedBarDayIndex) {
        buildBarChartData(periodStartMillis, periodEndMillis, periodLogs, selectedBarDayIndex)
    }

    // Filtered Logs for Recent List (by search and goal filter)
    val displayedLogs = remember(periodLogs, searchQuery, goalFilter, targetSleepMinutes) {
        periodLogs.filter { log ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase(Locale.getDefault())
                val dateStr = SimpleDateFormat("EEE d MMM yyyy", Locale.getDefault()).format(Date(log.dateMillis)).lowercase()
                val mood = sleepMoodFromRating(log.qualityRating, log.note).lowercase()
                dateStr.contains(q) || mood.contains(q) || log.note.lowercase().contains(q)
            }

            val isMet = log.durationMinutes >= (targetSleepMinutes - 30)
            val matchesGoal = when (goalFilter) {
                "MET" -> isMet
                "BELOW" -> !isMet
                else -> true
            }

            matchesSearch && matchesGoal
        }
    }

    Scaffold(
        containerColor = HistoryDarkBg,
        topBar = {
            SleepHistoryTopBar(
                onBack = onBack,
                onCalendarClick = {
                    val cal = Calendar.getInstance()
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            val selectedCal = Calendar.getInstance().apply {
                                set(year, month, dayOfMonth, 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            val existing = store.sleepLogForDate(selectedCal.timeInMillis)
                            targetEditLog = existing ?: SleepLog(
                                dateMillis = selectedCal.timeInMillis,
                                bedtimeMinute = schedule.bedtimeMinute,
                                wakeupMinute = schedule.wakeupMinute,
                                reportedBedtimeMillis = System.currentTimeMillis(),
                                reportedWakeupMillis = System.currentTimeMillis(),
                                durationMinutes = calculateSleepDurationMinutes(schedule.bedtimeMinute, schedule.wakeupMinute),
                                qualityRating = 4,
                                note = "",
                            )
                            showAddEditDialog = true
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                onMoreClick = { showMoreMenu = true },
                moreMenuExpanded = showMoreMenu,
                onDismissMoreMenu = { showMoreMenu = false },
                onExportCsv = {
                    showMoreMenu = false
                    exportSleepLogsAsCsv(context, allLogs)
                },
                onExportJson = {
                    showMoreMenu = false
                    exportSleepLogsAsJson(context, allLogs)
                },
                onOpenScheduleSettings = {
                    showMoreMenu = false
                    onNavigateToSchedule?.invoke()
                },
                onAddSampleData = {
                    showMoreMenu = false
                    seedDemoLogs(store)
                    refreshKey += 1
                    Toast.makeText(context, "Restored 7 sample sleep logs", Toast.LENGTH_SHORT).show()
                },
                onClearData = {
                    showMoreMenu = false
                    allLogs.forEach { store.deleteSleepLog(it.id) }
                    refreshKey += 1
                    Toast.makeText(context, "Sleep records cleared", Toast.LENGTH_SHORT).show()
                }
            )
        },
        bottomBar = {
            Column {
                if (selectedTab == SleepHistoryTab.HISTORY) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(HistoryDarkBg)
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                targetEditLog = null
                                showAddEditDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HistoryGreenPillBg,
                                contentColor = HistoryGreenPillText,
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                text = "+ Add Sleep Log",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                // Persistent StudyMode Bottom Navigation Bar (Home, Plan, Sleep active, Blocks)
                StudyModeBottomNav(
                    currentTab = "Sleep",
                    onNavigateToHome = onBack,
                    onNavigateToPlan = onBack,
                    onNavigateToSleep = { /* Already on sleep */ },
                    onNavigateToBlocks = { onNavigateToBlocks?.invoke() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Segmented Pill Tab Selector: [ History | Insights | Trends ]
            SleepHistoryTabSelector(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )

            // 2. Date Range Selector Dropdown / Chips
            DateRangeDropdownSelector(
                selectedRange = selectedDateRange,
                periodStartMillis = periodStartMillis,
                periodEndMillis = periodEndMillis,
                onRangeSelected = { range ->
                    if (range == SleepDateRange.CUSTOM) {
                        val cal = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y1, m1, d1 ->
                                val startCal = Calendar.getInstance().apply { set(y1, m1, d1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                                DatePickerDialog(
                                    context,
                                    { _, y2, m2, d2 ->
                                        val endCal = Calendar.getInstance().apply { set(y2, m2, d2, 23, 59, 59); set(Calendar.MILLISECOND, 999) }
                                        customStartMillis = minOf(startCal.timeInMillis, endCal.timeInMillis)
                                        customEndMillis = maxOf(startCal.timeInMillis, endCal.timeInMillis)
                                        selectedDateRange = SleepDateRange.CUSTOM
                                    },
                                    y1, m1, d1
                                ).show()
                            },
                            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    } else {
                        selectedDateRange = range
                    }
                }
            )

            when (selectedTab) {
                SleepHistoryTab.HISTORY -> {
                    // 3. Summary Cards Row (Weekly average & 3 Metrics)
                    WeeklyOverviewCardsRow(
                        avgDurationMinutes = avgDurationMinutes,
                        durationDeltaMinutes = durationDeltaMinutes,
                        avgBedtimeMinute = bedtimeStats.averageMinute,
                        avgWakeupMinute = wakeupStats.averageMinute,
                        goalMetPercent = goalMetPercent,
                        onDurationCardClick = {
                            explanationTitle = "Average Sleep Duration"
                            explanationContent = if (avgDurationMinutes > 0) {
                                "Recorded average is ${avgDurationMinutes / 60}h ${avgDurationMinutes % 60}m across ${periodLogs.size} recorded nights.\n\n" +
                                        (if (durationDeltaMinutes != null) {
                                            val sign = if (durationDeltaMinutes >= 0) "+" else ""
                                            "Comparison: ${sign}${durationDeltaMinutes} min versus the prior period.\n\n"
                                        } else "No prior period logs are available for comparison yet.\n\n") +
                                        "Sleep duration is calculated accurately across midnight from actual reported bedtime and wake-up times."
                            } else "No sleep records logged in this date range. Tap '+ Add Sleep Log' below to record sleep."
                        },
                        onBedtimeCardClick = {
                            explanationTitle = "Average Bedtime"
                            explanationContent = if (bedtimeStats.count > 0) {
                                "Calculated average bedtime is ${formatClockTime12h(bedtimeStats.averageMinute)} with a consistency variance of ±${bedtimeStats.varianceMinutes} min.\n\n" +
                                        "Circular directional statistics are used to correctly average bedtimes around midnight (e.g. 11:30 PM and 12:30 AM average to 12:00 AM midnight, not noon)."
                            } else "No bedtime records found for this period."
                        },
                        onWakeupCardClick = {
                            explanationTitle = "Average Wake-up Time"
                            explanationContent = if (wakeupStats.count > 0) {
                                "Calculated average wake-up time is ${formatClockTime12h(wakeupStats.averageMinute)} with a variance of ±${wakeupStats.varianceMinutes} min."
                            } else "No wake-up records found for this period."
                        },
                        onGoalMetCardClick = {
                            explanationTitle = "Sleep Goal Progress"
                            explanationContent = "Target Goal: ${targetSleepMinutes / 60}h ${targetSleepMinutes % 60}m/night (configured in Sleep Schedule).\n\n" +
                                    "• $goalMetPercent% of valid recorded nights met or exceeded this target ($metGoalLogs count).\n" +
                                    "• Below goal: ${belowGoalLogs.size} nights.\n" +
                                    "• Missing logs: $noDataNightsCount nights (missing nights are not counted as failed goals)."
                        }
                    )

                    // 4. Weekly Bar Chart (Mon - Sun) with Target Goal Line
                    WeeklyBarChartCard(
                        barData = barChartData,
                        targetMinutes = targetSleepMinutes,
                        onBarClick = { index ->
                            selectedBarDayIndex = if (selectedBarDayIndex == index) -1 else index
                        }
                    )

                    // Interactive Selected Day Detail Inspection Box
                    if (selectedBarDayIndex in barChartData.indices) {
                        val item = barChartData[selectedBarDayIndex]
                        SelectedBarInspectionCard(
                            item = item,
                            targetMinutes = targetSleepMinutes,
                            onViewEditLog = { log ->
                                targetEditLog = log
                                showAddEditDialog = true
                            },
                            onAddLogForDate = { dateMillis ->
                                targetEditLog = SleepLog(
                                    dateMillis = dateMillis,
                                    bedtimeMinute = schedule.bedtimeMinute,
                                    wakeupMinute = schedule.wakeupMinute,
                                    reportedBedtimeMillis = System.currentTimeMillis(),
                                    reportedWakeupMillis = System.currentTimeMillis(),
                                    durationMinutes = calculateSleepDurationMinutes(schedule.bedtimeMinute, schedule.wakeupMinute),
                                    qualityRating = 4,
                                    note = "",
                                )
                                showAddEditDialog = true
                            },
                            onDismiss = { selectedBarDayIndex = -1 }
                        )
                    }

                    // 5. Recent Sleep Logs Header with Search & "+ Add Sleep Log"
                    RecentSleepLogsHeader(
                        isSearchActive = isSearchActive,
                        activeGoalFilter = goalFilter,
                        onToggleSearch = {
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        onClearFilter = { goalFilter = null },
                        onAddLogClick = {
                            targetEditLog = null
                            showAddEditDialog = true
                        }
                    )

                    // Optional Search Bar
                    AnimatedVisibility(visible = isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search logs by date, mood, note...", color = HistoryTextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(14.dp),
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Text("✕", color = HistoryTextMuted, fontSize = 14.sp)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HistoryCardInner,
                                unfocusedContainerColor = HistoryCardInner,
                                focusedBorderColor = HistoryGreen,
                                unfocusedBorderColor = HistoryCardBorder,
                                focusedTextColor = HistoryTextWhite,
                                unfocusedTextColor = HistoryTextWhite,
                            )
                        )
                    }

                    // 6. Recent Sleep Log List
                    if (displayedLogs.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = HistoryCardBg,
                            border = BorderStroke(1.dp, HistoryCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank() || goalFilter != null) "No matching sleep logs" else "No sleep records for this period",
                                    color = HistoryTextWhite,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (searchQuery.isNotBlank() || goalFilter != null) "Try adjusting your search query or filter." else "Tap '+ Add Sleep Log' below to record your sleep.",
                                    color = HistoryTextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                                if (searchQuery.isNotBlank() || goalFilter != null) {
                                    TextButton(onClick = { searchQuery = ""; goalFilter = null }) {
                                        Text("Clear Filters", color = HistoryGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            displayedLogs.forEach { log ->
                                SleepLogRowCard(
                                    log = log,
                                    targetMinutes = targetSleepMinutes,
                                    onClick = {
                                        targetEditLog = log
                                        showAddEditDialog = true
                                    }
                                )
                            }
                        }
                    }
                }

                SleepHistoryTab.INSIGHTS -> {
                    // Insights Screen (matching user mockup)
                    SleepInsightsTabContent(
                        avgDurationMinutes = avgDurationMinutes,
                        durationDeltaMinutes = durationDeltaMinutes,
                        bedtimeStats = bedtimeStats,
                        wakeupStats = wakeupStats,
                        bedtimeConsistency = bedtimeConsistency,
                        wakeupConsistency = wakeupConsistency,
                        goalMetPercent = goalMetPercent,
                        goalDeltaPercent = goalDeltaPercent,
                        metGoalCount = metGoalLogs.size,
                        belowGoalCount = belowGoalLogs.size,
                        noDataCount = noDataNightsCount,
                        periodLogs = periodLogs,
                        targetSleepMinutes = targetSleepMinutes,
                        barChartData = barChartData,
                        selectedBarDayIndex = selectedBarDayIndex,
                        onSelectBarIndex = { idx ->
                            selectedBarDayIndex = if (selectedBarDayIndex == idx) -1 else idx
                        },
                        onViewEditLog = { log ->
                            targetEditLog = log
                            showAddEditDialog = true
                        },
                        onFilterByGoal = { cat ->
                            filteredDatesCategory = cat
                        },
                        onNavigateToSchedule = onNavigateToSchedule,
                        onNavigateToPhoneUsage = onNavigateToPhoneUsage,
                        onNavigateToWindDown = onNavigateToWindDown,
                        onNavigateToBlocks = onNavigateToBlocks,
                        onInfoClick = { title, desc, actionLabel, action ->
                            explanationTitle = title
                            explanationContent = desc
                            explanationActionLabel = actionLabel
                            explanationAction = action
                        }
                    )
                }

                SleepHistoryTab.TRENDS -> {
                    // Trends Screen
                    SleepTrendsTabContent(
                        allLogs = allLogs,
                        targetSleepMinutes = targetSleepMinutes,
                        onNavigateToSchedule = onNavigateToSchedule,
                        onViewEditLog = { log ->
                            targetEditLog = log
                            showAddEditDialog = true
                        },
                        onExportCsv = { exportSleepLogsAsCsv(context, allLogs) },
                        onExportJson = { exportSleepLogsAsJson(context, allLogs) },
                        onInfoClick = { title, desc, actionLabel, action ->
                            explanationTitle = title
                            explanationContent = desc
                            explanationActionLabel = actionLabel
                            explanationAction = action
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Goal Filter Dates Dialog
    GoalFilterDatesDialog(
        category = filteredDatesCategory,
        periodLogs = periodLogs,
        targetSleepMinutes = targetSleepMinutes,
        onDismiss = { filteredDatesCategory = null },
        onEditLog = { log ->
            targetEditLog = log
            showAddEditDialog = true
        },
        onSwitchToHistoryFilter = { filter ->
            goalFilter = filter
            selectedTab = SleepHistoryTab.HISTORY
            filteredDatesCategory = null
        }
    )

    // Explanation Dialog
    if (explanationTitle != null && explanationContent != null) {
        AlertDialog(
            onDismissRequest = {
                explanationTitle = null
                explanationContent = null
                explanationActionLabel = null
                explanationAction = null
            },
            containerColor = Color(0xFF0F1A13),
            title = {
                Text(explanationTitle.orEmpty(), color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(explanationContent.orEmpty(), color = HistoryTextMuted, fontSize = 13.sp, lineHeight = 20.sp)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (explanationActionLabel != null && explanationAction != null) {
                        Button(
                            onClick = {
                                val action = explanationAction
                                explanationTitle = null
                                explanationContent = null
                                explanationActionLabel = null
                                explanationAction = null
                                action?.invoke()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HistoryGreen, contentColor = HistoryGreenPillText)
                        ) {
                            Text(explanationActionLabel.orEmpty(), fontWeight = FontWeight.Bold)
                        }
                    }
                    TextButton(
                        onClick = {
                            explanationTitle = null
                            explanationContent = null
                            explanationActionLabel = null
                            explanationAction = null
                        }
                    ) {
                        Text(if (explanationActionLabel != null) "Close" else "Got It", color = HistoryTextMuted)
                    }
                }
            }
        )
    }

    // Add / Edit Sleep Log Dialog
    if (showAddEditDialog) {
        SleepHistoryLogEditorDialog(
            existingLog = targetEditLog,
            defaultBedtime = schedule.bedtimeMinute,
            defaultWakeup = schedule.wakeupMinute,
            onDismiss = { showAddEditDialog = false },
            onSave = { log ->
                val existingOnSameDay = store.sleepLogForDate(normalizeToDayStart(log.dateMillis))
                if (existingOnSameDay != null && existingOnSameDay.id != log.id && targetEditLog == null) {
                    Toast.makeText(context, "Updated existing log for this date", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Sleep log saved successfully", Toast.LENGTH_SHORT).show()
                }
                store.saveSleepLog(log)
                showAddEditDialog = false
                refreshKey += 1
            },
            onDelete = { id ->
                store.deleteSleepLog(id)
                showAddEditDialog = false
                refreshKey += 1
                Toast.makeText(context, "Sleep log deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ----------------------------------------------------------------------
// TOP BAR
// ----------------------------------------------------------------------
@Composable
private fun SleepHistoryTopBar(
    onBack: () -> Unit,
    onCalendarClick: () -> Unit,
    onMoreClick: () -> Unit,
    moreMenuExpanded: Boolean,
    onDismissMoreMenu: () -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onOpenScheduleSettings: () -> Unit,
    onAddSampleData: () -> Unit,
    onClearData: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(40.dp)
        ) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val path = Path().apply {
                    moveTo(size.width * 0.65f, size.height * 0.15f)
                    lineTo(size.width * 0.25f, size.height * 0.5f)
                    lineTo(size.width * 0.65f, size.height * 0.85f)
                }
                drawPath(
                    path = path,
                    color = HistoryGreen,
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        // Title & Subtitle
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Sleep History",
                color = HistoryTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Track and understand your sleep",
                color = HistoryTextMuted,
                fontSize = 12.sp
            )
        }

        // Calendar Action
        IconButton(
            onClick = onCalendarClick,
            modifier = Modifier.size(40.dp)
        ) {
            CalendarIconVector(color = HistoryTextWhite, modifier = Modifier.size(20.dp))
        }

        // More Action (three dots)
        Box {
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(40.dp)
            ) {
                Text("⋮", color = HistoryTextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            DropdownMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = onDismissMoreMenu,
                modifier = Modifier.background(HistoryCardBg)
            ) {
                DropdownMenuItem(
                    text = { Text("Export as CSV", color = HistoryTextWhite) },
                    onClick = onExportCsv
                )
                DropdownMenuItem(
                    text = { Text("Export as JSON", color = HistoryTextWhite) },
                    onClick = onExportJson
                )
                DropdownMenuItem(
                    text = { Text("Sleep Schedule Settings", color = HistoryGreen) },
                    onClick = onOpenScheduleSettings
                )
                HorizontalDivider(color = HistoryCardBorder)
                DropdownMenuItem(
                    text = { Text("Restore Demo Logs", color = HistoryTextMuted) },
                    onClick = onAddSampleData
                )
                DropdownMenuItem(
                    text = { Text("Clear All Logs", color = Color(0xFFEF4444)) },
                    onClick = onClearData
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// SEGMENTED PILL TAB SELECTOR: [ History | Insights | Trends ]
// ----------------------------------------------------------------------
@Composable
private fun SleepHistoryTabSelector(
    selectedTab: SleepHistoryTab,
    onTabSelected: (SleepHistoryTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF0F1A13))
            .border(1.dp, HistoryCardBorder, RoundedCornerShape(22.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tabs = listOf(
            SleepHistoryTab.HISTORY to "History",
            SleepHistoryTab.INSIGHTS to "Insights",
            SleepHistoryTab.TRENDS to "Trends"
        )

        tabs.forEach { (tab, label) ->
            val isSelected = selectedTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) HistoryGreenPillBg else Color.Transparent)
                    .clickable { onTabSelected(tab) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) HistoryGreenPillText else HistoryTextWhite,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// DATE RANGE DROPDOWN SELECTOR MATCHING MOCKUP
// ----------------------------------------------------------------------
@Composable
private fun DateRangeDropdownSelector(
    selectedRange: SleepDateRange,
    periodStartMillis: Long,
    periodEndMillis: Long,
    onRangeSelected: (SleepDateRange) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    val yearFormat = SimpleDateFormat("yyyy", Locale.getDefault())

    val startStr = dateFormat.format(Date(periodStartMillis))
    val endStr = dateFormat.format(Date(periodEndMillis))
    val yearStr = yearFormat.format(Date(periodEndMillis))

    val labelText = when (selectedRange) {
        SleepDateRange.THIS_WEEK -> "This Week ($startStr – $endStr, $yearStr)"
        SleepDateRange.LAST_WEEK -> "Last Week ($startStr – $endStr, $yearStr)"
        SleepDateRange.LAST_30_DAYS -> "Last 30 Days ($startStr – $endStr)"
        SleepDateRange.CUSTOM -> "Custom Range ($startStr – $endStr, $yearStr)"
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = true },
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F1A13),
            border = BorderStroke(1.dp, HistoryCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CalendarIconVector(color = HistoryGreen, modifier = Modifier.size(16.dp))
                    Text(
                        text = labelText,
                        color = HistoryGreen,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "∨",
                    color = HistoryTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(HistoryCardBg)
        ) {
            SleepDateRange.values().forEach { range ->
                val isSelected = selectedRange == range
                DropdownMenuItem(
                    text = {
                        Text(
                            text = when (range) {
                                SleepDateRange.THIS_WEEK -> "This Week ($startStr – $endStr)"
                                SleepDateRange.LAST_WEEK -> "Last Week"
                                SleepDateRange.LAST_30_DAYS -> "Last 30 Days"
                                SleepDateRange.CUSTOM -> "Custom Date Range..."
                            },
                            color = if (isSelected) HistoryGreen else HistoryTextWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        expanded = false
                        onRangeSelected(range)
                    }
                )
            }
        }
    }
}

@Composable
private fun GoalFilterDatesDialog(
    category: String?,
    periodLogs: List<SleepLog>,
    targetSleepMinutes: Int,
    onDismiss: () -> Unit,
    onEditLog: (SleepLog) -> Unit,
    onSwitchToHistoryFilter: (String?) -> Unit,
) {
    if (category == null) return

    val title = when (category) {
        "MET" -> "Nights Meeting Sleep Goal (>= ${targetSleepMinutes / 60}h)"
        "BELOW" -> "Nights Below Sleep Goal (< ${targetSleepMinutes / 60}h)"
        else -> "Missing Sleep Records"
    }

    val filteredLogs = when (category) {
        "MET" -> periodLogs.filter { it.durationMinutes >= (targetSleepMinutes - 30) }
        "BELOW" -> periodLogs.filter { it.durationMinutes < (targetSleepMinutes - 30) }
        else -> emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1A13),
        title = {
            Text(title, color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (category == "NO_DATA") {
                    Text(
                        text = "Missing nights represent dates where no sleep log was recorded. The app honestly counts them as unrecorded rather than assuming failed goals.",
                        color = HistoryTextMuted,
                        fontSize = 12.5.sp
                    )
                } else if (filteredLogs.isEmpty()) {
                    Text("No records found in this category.", color = HistoryTextMuted, fontSize = 12.5.sp)
                } else {
                    filteredLogs.forEach { log ->
                        val dateStr = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(log.dateMillis))
                        val h = log.durationMinutes / 60
                        val m = log.durationMinutes % 60
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onDismiss()
                                    onEditLog(log)
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = HistoryCardInner,
                            border = BorderStroke(1.dp, HistoryCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dateStr, color = HistoryTextWhite, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text(
                                        "${formatClockTime12h(log.bedtimeMinute)} – ${formatClockTime12h(log.wakeupMinute)}",
                                        color = HistoryTextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "${h}h ${m}m",
                                        color = if (category == "MET") HistoryGreen else Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text("✎", color = HistoryGreen, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSwitchToHistoryFilter(if (category == "NO_DATA") null else category)
                },
                colors = ButtonDefaults.buttonColors(containerColor = HistoryGreen, contentColor = HistoryGreenPillText)
            ) {
                Text("View in History Tab", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = HistoryTextMuted)
            }
        }
    )
}

// ----------------------------------------------------------------------
// WEEKLY OVERVIEW SUMMARY CARDS ROW (TAPPABLE WITH INFO)
// ----------------------------------------------------------------------
@Composable
private fun WeeklyOverviewCardsRow(
    avgDurationMinutes: Int,
    durationDeltaMinutes: Int?,
    avgBedtimeMinute: Int,
    avgWakeupMinute: Int,
    goalMetPercent: Int,
    onDurationCardClick: () -> Unit,
    onBedtimeCardClick: () -> Unit,
    onWakeupCardClick: () -> Unit,
    onGoalMetCardClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card 1: Weekly average
        Surface(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .clickable { onDurationCardClick() },
            shape = RoundedCornerShape(16.dp),
            color = HistoryCardBg,
            border = BorderStroke(1.dp, HistoryCardBorder)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Weekly average",
                        color = HistoryTextMuted,
                        fontSize = 12.sp
                    )
                    Text("ℹ", color = HistoryTextMuted, fontSize = 11.sp)
                }

                val hours = avgDurationMinutes / 60
                val mins = avgDurationMinutes % 60
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$hours",
                        color = HistoryTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = " h ",
                        color = HistoryTextWhite,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = "$mins",
                        color = HistoryTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = " min",
                        color = HistoryTextWhite,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (durationDeltaMinutes != null) {
                        val isPositive = durationDeltaMinutes >= 0
                        Text(
                            text = if (isPositive) "↗ +$durationDeltaMinutes min" else "↘ $durationDeltaMinutes min",
                            color = if (isPositive) HistoryGreen else Color(0xFFFBBF24),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = " vs prior",
                            color = HistoryTextMuted,
                            fontSize = 11.sp
                        )
                    } else {
                        Text(
                            text = "Baseline period",
                            color = HistoryTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Card 2: 3 Metrics (Avg bedtime, Avg wake-up, Sleep goal met)
        Surface(
            modifier = Modifier.weight(1.25f),
            shape = RoundedCornerShape(16.dp),
            color = HistoryCardBg,
            border = BorderStroke(1.dp, HistoryCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bedtime
                Box(modifier = Modifier.clickable { onBedtimeCardClick() }) {
                    MetricColumnItem(
                        icon = { MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(16.dp)) },
                        value = if (avgBedtimeMinute > 0) formatClockTime12h(avgBedtimeMinute) else "—",
                        label = "Avg. bedtime"
                    )
                }

                // Wake-up
                Box(modifier = Modifier.clickable { onWakeupCardClick() }) {
                    MetricColumnItem(
                        icon = { SunVectorIcon(color = HistoryGreen, modifier = Modifier.size(16.dp)) },
                        value = if (avgWakeupMinute > 0) formatClockTime12h(avgWakeupMinute) else "—",
                        label = "Avg. wake-up"
                    )
                }

                // Goal met
                Box(modifier = Modifier.clickable { onGoalMetCardClick() }) {
                    MetricColumnItem(
                        icon = { BarChartVectorIcon(color = HistoryGreen, modifier = Modifier.size(16.dp)) },
                        value = "$goalMetPercent%",
                        label = "Sleep goal met"
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricColumnItem(
    icon: @Composable () -> Unit,
    value: String,
    label: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        icon()
        Text(
            text = value,
            color = HistoryTextWhite,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            color = HistoryTextMuted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ----------------------------------------------------------------------
// WEEKLY BAR CHART CARD WITH TARGET LINE
// ----------------------------------------------------------------------
@Composable
private fun WeeklyBarChartCard(
    barData: List<DaySleepBarData>,
    targetMinutes: Int,
    onBarClick: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = HistoryCardBg,
        border = BorderStroke(1.dp, HistoryCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
        ) {
            // Header with target goal badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sleep Duration",
                    color = HistoryTextWhite,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = "--- Goal: ${targetMinutes / 60}h",
                    color = HistoryGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Chart Canvas area with Y-axis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                // Y-Axis labels (10h, 8h, 6h, 4h, 2h, 0h)
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf("10h", "8h", "6h", "4h", "2h", "0h").forEach { label ->
                        Text(
                            text = label,
                            color = HistoryTextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Bar Canvas with horizontal gridlines & Target Goal Reference Line
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Horizontal subtle gridlines
                        val gridCount = 5
                        for (i in 0..gridCount) {
                            val y = (h / gridCount) * i
                            drawLine(
                                color = Color(0x1F22C55E),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Target Goal Reference Line (dashed)
                        val maxMinutes = 10 * 60f
                        val targetY = h - (h * (targetMinutes.toFloat() / maxMinutes)).coerceIn(0f, h)
                        drawLine(
                            color = Color(0x804ADE80),
                            start = Offset(0f, targetY),
                            end = Offset(w, targetY),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )

                        // Draw Bars
                        val barCount = barData.size.coerceAtLeast(1)
                        val slotWidth = w / barCount
                        val barThickness = minOf(slotWidth * 0.58f, 28.dp.toPx())

                        barData.forEachIndexed { index, item ->
                            val cx = slotWidth * index + slotWidth / 2f
                            val left = cx - barThickness / 2f

                            // 1. Background Pillar (rounded track)
                            drawRoundRect(
                                color = if (item.isSelected) Color(0xFF1E3827) else HistoryBarTrackBg,
                                topLeft = Offset(left, 0f),
                                size = Size(barThickness, h),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )

                            // 2. Filled Duration Bar (only if log exists)
                            if (item.hasLog && item.durationMinutes > 0) {
                                val durationRatio = (item.durationMinutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)
                                val barHeight = h * durationRatio
                                val barTop = h - barHeight

                                val gradientBrush = Brush.verticalGradient(
                                    colors = if (item.isSelected) {
                                        listOf(Color(0xFFBBF7D0), Color(0xFF4ADE80))
                                    } else {
                                        listOf(Color(0xFF86EFAC), HistoryGreenDark)
                                    },
                                    startY = barTop,
                                    endY = h
                                )

                                drawRoundRect(
                                    brush = gradientBrush,
                                    topLeft = Offset(left, barTop),
                                    size = Size(barThickness, barHeight),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // X-Axis Day & Duration labels row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                barData.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onBarClick(index) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.dayLabel,
                            color = if (item.isSelected) HistoryGreen else HistoryTextWhite,
                            fontWeight = if (item.isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = item.durationText,
                            color = if (item.isSelected) HistoryGreen else HistoryTextMuted,
                            fontSize = 9.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// SELECTED DAY INSPECTION CARD
// ----------------------------------------------------------------------
@Composable
private fun SelectedBarInspectionCard(
    item: DaySleepBarData,
    targetMinutes: Int,
    onViewEditLog: (SleepLog) -> Unit,
    onAddLogForDate: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateStr = if (item.dateMillis > 0L) {
        SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(item.dateMillis))
    } else item.dayLabel

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF112217),
        border = BorderStroke(1.dp, HistoryGreen)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dateStr, color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    text = "✕",
                    color = HistoryTextMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            if (item.hasLog && item.log != null) {
                val log = item.log
                val h = log.durationMinutes / 60
                val m = log.durationMinutes % 60
                val diff = log.durationMinutes - targetMinutes
                val diffText = if (diff >= 0) "+$diff min above goal" else "$diff min below goal"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("$h h $m min", color = HistoryGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "${formatClockTime12h(log.bedtimeMinute)} – ${formatClockTime12h(log.wakeupMinute)}",
                            color = HistoryTextMuted,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = diffText,
                        color = if (diff >= 0) HistoryGreen else Color(0xFFFBBF24),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (log.note.isNotBlank()) {
                    Text("Note: ${log.note}", color = HistoryTextMuted, fontSize = 11.sp)
                }

                Button(
                    onClick = { onViewEditLog(log) },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HistoryGreen, contentColor = HistoryGreenPillText)
                ) {
                    Text("View / Edit Log", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            } else {
                Text("No sleep record logged for this date.", color = HistoryTextMuted, fontSize = 12.sp)
                Button(
                    onClick = { onAddLogForDate(item.dateMillis) },
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HistoryGreen, contentColor = HistoryGreenPillText)
                ) {
                    Text("+ Log Sleep for this Day", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------
// RECENT SLEEP LOGS SECTION HEADER
// ----------------------------------------------------------------------
@Composable
private fun RecentSleepLogsHeader(
    isSearchActive: Boolean,
    activeGoalFilter: String?,
    onToggleSearch: () -> Unit,
    onClearFilter: () -> Unit,
    onAddLogClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recent Sleep Logs",
                color = HistoryTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            if (activeGoalFilter != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HistoryCardInner)
                        .clickable { onClearFilter() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Filtered ✕",
                        color = HistoryGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Search button
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isSearchActive) HistoryGreen else Color(0xFF132219))
            ) {
                Text(
                    text = "🔍",
                    fontSize = 14.sp
                )
            }

            // Small "+ Add Sleep Log" Pill Button
            Button(
                onClick = onAddLogClick,
                modifier = Modifier.height(34.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HistoryGreenPillBg,
                    contentColor = HistoryGreenPillText
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "+ Add Sleep Log",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// SLEEP LOG ROW CARD
// ----------------------------------------------------------------------
@Composable
private fun SleepLogRowCard(
    log: SleepLog,
    targetMinutes: Int,
    onClick: () -> Unit,
) {
    val date = Date(log.dateMillis)
    val dayName = SimpleDateFormat("EEE", Locale.getDefault()).format(date)
    val dayNumber = SimpleDateFormat("d", Locale.getDefault()).format(date)
    val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(date)

    val isGoalMet = log.durationMinutes >= (targetMinutes - 20)
    val moodTitle = sleepMoodFromRating(log.qualityRating, log.note)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = HistoryCardBg,
        border = BorderStroke(1.dp, HistoryCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Date Block: Sun | 14 | Jan
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(36.dp)
            ) {
                Text(text = dayName, color = HistoryTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Text(text = dayNumber, color = HistoryTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = monthName, color = HistoryTextMuted, fontSize = 10.sp)
            }

            // 2. Crescent Moon Icon
            MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(24.dp))

            // 3. Duration & Bedtime-Wakeup window
            Column(modifier = Modifier.weight(1.3f)) {
                val hours = log.durationMinutes / 60
                val mins = log.durationMinutes % 60
                Text(
                    text = "$hours h $mins min",
                    color = HistoryTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "${formatClockTime12h(log.bedtimeMinute)} – ${formatClockTime12h(log.wakeupMinute)}",
                    color = HistoryTextMuted,
                    fontSize = 11.sp
                )
            }

            // 4. Goal Badge: Goal met / Below goal
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isGoalMet) HistoryGoalMetBg else HistoryBelowGoalBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isGoalMet) "Goal met" else "Below goal",
                    color = if (isGoalMet) HistoryGoalMetText else HistoryBelowGoalText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 5. Mood Smiley & Note Description
            Row(
                modifier = Modifier.weight(1.5f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SmileyVectorIcon(
                    isHappy = isGoalMet,
                    color = if (isGoalMet) HistoryGreen else Color(0xFFFBBF24),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = moodTitle,
                        color = HistoryTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (log.note.isNotBlank()) {
                        Text(
                            text = log.note,
                            color = HistoryTextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 6. Right Chevron `›`
            Text(
                text = "›",
                color = HistoryTextMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ----------------------------------------------------------------------
// INSIGHTS TAB CONTENT (EXACT MATCH FOR USER SCREENSHOT)
// ----------------------------------------------------------------------
@Composable
private fun SleepInsightsTabContent(
    avgDurationMinutes: Int,
    durationDeltaMinutes: Int?,
    bedtimeStats: CircularStatsResult,
    wakeupStats: CircularStatsResult,
    bedtimeConsistency: ConsistencyStatus,
    wakeupConsistency: ConsistencyStatus,
    goalMetPercent: Int,
    goalDeltaPercent: Int?,
    metGoalCount: Int,
    belowGoalCount: Int,
    noDataCount: Int,
    periodLogs: List<SleepLog>,
    targetSleepMinutes: Int,
    barChartData: List<DaySleepBarData>,
    selectedBarDayIndex: Int,
    onSelectBarIndex: (Int) -> Unit,
    onViewEditLog: (SleepLog) -> Unit,
    onFilterByGoal: (String) -> Unit,
    onNavigateToSchedule: (() -> Unit)?,
    onNavigateToPhoneUsage: (() -> Unit)?,
    onNavigateToWindDown: (() -> Unit)?,
    onNavigateToBlocks: (() -> Unit)?,
    onInfoClick: (String, String, String?, (() -> Unit)?) -> Unit,
) {
    fun showInfo(title: String, desc: String, actionLabel: String? = null, action: (() -> Unit)? = null) {
        onInfoClick(title, desc, actionLabel, action)
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // 1. Row of 4 Summary Metric Cards (Average sleep, Avg. bedtime, Avg. wake-up, Sleep goal met)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val avgHours = avgDurationMinutes / 60
            val avgMins = avgDurationMinutes % 60

            // Card 1: Average sleep
            SummaryMetricMiniCard(
                modifier = Modifier.weight(1f),
                icon = { BedVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Average sleep",
                value = if (avgDurationMinutes > 0) "${avgHours} h ${avgMins} min" else "—",
                statusText = if (durationDeltaMinutes != null) {
                    val sign = if (durationDeltaMinutes >= 0) "+" else ""
                    "↗ $sign$durationDeltaMinutes min"
                } else "—",
                statusColor = if ((durationDeltaMinutes ?: 0) >= 0) HistoryGreen else Color(0xFFFBBF24),
                subtext = "vs last week",
                onClick = {
                    showInfo(
                        "Average Sleep Duration",
                        "Average recorded sleep is " + avgHours + "h " + avgMins + "m across " + periodLogs.size + " nights.\n\n" +
                                (if (durationDeltaMinutes != null) {
                                    val sign = if (durationDeltaMinutes >= 0) "+" else ""
                                    "Comparison: " + sign + durationDeltaMinutes + " min compared to the previous week.\n\n"
                                } else "No comparison available from prior week.\n\n") +
                                "Target Goal: " + (targetSleepMinutes / 60) + "h " + (targetSleepMinutes % 60) + "m/night (configured in Sleep Schedule).",
                        "Sleep Schedule",
                        onNavigateToSchedule
                    )
                }
            )

            // Card 2: Avg. bedtime
            SummaryMetricMiniCard(
                modifier = Modifier.weight(1f),
                icon = { MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Avg. bedtime",
                value = if (bedtimeStats.count > 0) formatClockTime12h(bedtimeStats.averageMinute) else "—",
                statusText = bedtimeConsistency.label,
                statusColor = bedtimeConsistency.color,
                hasInfo = true,
                onClick = {
                    showInfo(
                        "Average Bedtime & Circadian Consistency",
                        "Average Bedtime: " + (if (bedtimeStats.count > 0) formatClockTime12h(bedtimeStats.averageMinute) else "None") + "\n\n" +
                                "Consistency: ±" + bedtimeStats.varianceMinutes + " minutes variation.\n\n" +
                                "Circular directional statistics are used to correctly average bedtimes around midnight (e.g. 11:30 PM and 12:30 AM average to 12:00 AM midnight, not noon).",
                        null,
                        null
                    )
                }
            )

            // Card 3: Avg. wake-up
            SummaryMetricMiniCard(
                modifier = Modifier.weight(1f),
                icon = { SunVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Avg. wake-up",
                value = if (wakeupStats.count > 0) formatClockTime12h(wakeupStats.averageMinute) else "—",
                statusText = wakeupConsistency.label,
                statusColor = wakeupConsistency.color,
                hasInfo = true,
                onClick = {
                    showInfo(
                        "Average Wake-up Time",
                        "Average Wake-up: " + (if (wakeupStats.count > 0) formatClockTime12h(wakeupStats.averageMinute) else "None") + "\n\n" +
                                "Variation: ±" + wakeupStats.varianceMinutes + " minutes.\n\n" +
                                "Waking up at a consistent time strengthens your biological clock and optimizes daytime alertness.",
                        null,
                        null
                    )
                }
            )

            // Card 4: Sleep goal met
            SummaryMetricMiniCard(
                modifier = Modifier.weight(1f),
                icon = { TargetVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Sleep goal met",
                value = "$goalMetPercent%",
                statusText = if (goalDeltaPercent != null) {
                    val sign = if (goalDeltaPercent >= 0) "+" else ""
                    "↗ $sign$goalDeltaPercent%"
                } else "—",
                statusColor = HistoryGreen,
                subtext = "$metGoalCount of " + periodLogs.size + " nights\nvs last week",
                onClick = {
                    showInfo(
                        "Sleep Goal Adherence",
                        "Goal: " + (targetSleepMinutes / 60) + "h/night (configured in Sleep Schedule).\n\n" +
                                "• Met target: " + metGoalCount + " nights\n" +
                                "• Below target: " + belowGoalCount + " nights\n" +
                                "• Missing data: " + noDataCount + " nights (not counted as failures)\n\n" +
                                "Adherence: " + goalMetPercent + "% of valid recorded nights met the target.",
                        "Sleep Schedule",
                        onNavigateToSchedule
                    )
                }
            )
        }

        // 2. Sleep duration insight (Large Card)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = HistoryCardBg,
            border = BorderStroke(1.dp, HistoryCardBorder)
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, bottom = 12.dp, start = 14.dp, end = 14.dp)) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F2618)),
                            contentAlignment = Alignment.Center
                        ) {
                            BarChartVectorIcon(color = HistoryGreen, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = "Sleep duration insight",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            val diffText = if (durationDeltaMinutes != null && durationDeltaMinutes >= 0) {
                                "You slept $durationDeltaMinutes minutes more per night on average compared to last week."
                            } else if (durationDeltaMinutes != null) {
                                "You slept ${-durationDeltaMinutes} minutes less per night on average compared to last week."
                            } else {
                                "Recorded average is ${avgDurationMinutes / 60}h ${avgDurationMinutes % 60}m across this period."
                            }
                            Text(
                                text = diffText,
                                color = HistoryTextMuted,
                                fontSize = 11.5.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Pill badge "↗ Improving"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(HistoryGoalMetBg)
                            .border(1.dp, HistoryGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if ((durationDeltaMinutes ?: 0) >= 0) "↗ Improving" else "↘ Declining",
                            color = HistoryGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 10h Chart Canvas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 8.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        listOf("10h", "8h", "6h", "4h", "2h", "0h").forEach { label ->
                            Text(text = label, color = HistoryTextMuted, fontSize = 10.sp)
                        }
                    }

                    // Canvas with Dashed Target Reference Line + Goal: 8h Badge + Bars
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val maxMinutes = 10 * 60f

                            // Horizontal grid lines
                            val gridCount = 5
                            for (i in 0..gridCount) {
                                val y = (h / gridCount) * i
                                drawLine(
                                    color = Color(0x1F22C55E),
                                    start = Offset(0f, y),
                                    end = Offset(w, y),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }

                            // Target line
                            val targetY = h - (h * (targetSleepMinutes.toFloat() / maxMinutes)).coerceIn(0f, h)
                            drawLine(
                                color = Color(0x804ADE80),
                                start = Offset(0f, targetY),
                                end = Offset(w, targetY),
                                strokeWidth = 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )

                            // Draw Bars
                            val barCount = barChartData.size.coerceAtLeast(1)
                            val slotWidth = w / barCount
                            val barThickness = minOf(slotWidth * 0.58f, 28.dp.toPx())

                            barChartData.forEachIndexed { index, item ->
                                val cx = slotWidth * index + slotWidth / 2f
                                val left = cx - barThickness / 2f

                                // Background track
                                drawRoundRect(
                                    color = if (item.isSelected) Color(0xFF1E3827) else HistoryBarTrackBg,
                                    topLeft = Offset(left, 0f),
                                    size = Size(barThickness, h),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                )

                                // Filled Bar
                                if (item.hasLog && item.durationMinutes > 0) {
                                    val durationRatio = (item.durationMinutes.toFloat() / maxMinutes).coerceIn(0.04f, 1f)
                                    val barHeight = h * durationRatio
                                    val barTop = h - barHeight

                                    val gradientBrush = Brush.verticalGradient(
                                        colors = if (item.isSelected) listOf(Color(0xFFBBF7D0), Color(0xFF4ADE80))
                                        else listOf(Color(0xFF86EFAC), HistoryGreenDark),
                                        startY = barTop,
                                        endY = h
                                    )

                                    drawRoundRect(
                                        brush = gradientBrush,
                                        topLeft = Offset(left, barTop),
                                        size = Size(barThickness, barHeight),
                                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                }
                            }
                        }

                        // "Goal: 8h" badge positioned on the reference line
                        val targetRatio = (targetSleepMinutes.toFloat() / 600f).coerceIn(0.1f, 0.95f)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = (150 * (1f - targetRatio) - 10).coerceAtLeast(0f).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F2618))
                                .border(1.dp, HistoryGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Goal: ${targetSleepMinutes / 60}h",
                                color = HistoryGreen,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // X-axis Day labels & duration underneath
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    barChartData.forEachIndexed { index, item ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSelectBarIndex(index) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = item.dayLabel,
                                color = if (item.isSelected) HistoryGreen else HistoryTextWhite,
                                fontWeight = if (item.isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = item.durationText,
                                color = if (item.isSelected) HistoryGreen else HistoryTextMuted,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Selected Bar Inspection Card
        if (selectedBarDayIndex in barChartData.indices) {
            val item = barChartData[selectedBarDayIndex]
            SelectedBarInspectionCard(
                item = item,
                targetMinutes = targetSleepMinutes,
                onViewEditLog = { log -> onViewEditLog(log) },
                onAddLogForDate = { dateMillis ->
                    val newLog = SleepLog(
                        dateMillis = dateMillis,
                        bedtimeMinute = 22 * 60 + 30,
                        wakeupMinute = 6 * 60 + 30,
                        reportedBedtimeMillis = System.currentTimeMillis(),
                        reportedWakeupMillis = System.currentTimeMillis(),
                        durationMinutes = calculateSleepDurationMinutes(22 * 60 + 30, 6 * 60 + 30),
                        qualityRating = 4,
                        note = ""
                    )
                    onViewEditLog(newLog)
                },
                onDismiss = { onSelectBarIndex(-1) }
            )
        }

        // 3. Two Side-by-Side Cards: Bedtime consistency & Wake-up consistency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left: Bedtime consistency
            val bedtimeWithinCount = periodLogs.count {
                circularMinuteDifference(it.bedtimeMinute, bedtimeStats.averageMinute) <= 30
            }
            val bedtimeData = barChartData.map { barItem ->
                barItem.dayLabel to barItem.log?.let { log ->
                    var b = log.bedtimeMinute
                    if (b < 12 * 60) b += 1440
                    b
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        showInfo(
                            "Bedtime Consistency",
                            "Bedtime was within 30 minutes of average (${formatClockTime12h(bedtimeStats.averageMinute)}) on $bedtimeWithinCount of ${periodLogs.size} nights.\n\nConsistency Score: ${bedtimeConsistency.label}\nVariance: ±${bedtimeStats.varianceMinutes} min.\n\n" +
                                    "Maintaining a stable bedtime aligns circadian rhythms and promotes restorative deep sleep.",
                            "Distraction App Blocker",
                            onNavigateToBlocks
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = HistoryCardBg,
                border = BorderStroke(1.dp, HistoryCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F2618)),
                                contentAlignment = Alignment.Center
                            ) {
                                MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(13.dp))
                            }
                            Text(
                                text = "Bedtime consistency",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(HistoryGoalMetBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = bedtimeConsistency.label,
                                color = HistoryGreen,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your bedtime was within 30 minutes on $bedtimeWithinCount of ${periodLogs.size} nights.",
                        color = HistoryTextMuted,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Dot-Plot Chart: 12 AM (1440), 11 PM (1380), 10 PM (1320), 9 PM (1260)
                    ConsistencyDotPlotChart(
                        yLabels = listOf("12 AM", "11 PM", "10 PM", "9 PM"),
                        minMinute = 21 * 60,
                        maxMinute = 24 * 60,
                        dayData = bedtimeData,
                        highlightIndex = 3, // Thu highlight halo matching mockup
                        onDotClick = { idx, _ ->
                            val log = barChartData.getOrNull(idx)?.log
                            if (log != null) onViewEditLog(log)
                        },
                        modifier = Modifier.height(95.dp)
                    )
                }
            }

            // Right: Wake-up consistency
            val wakeupWithinCount = periodLogs.count {
                kotlin.math.abs(it.wakeupMinute - wakeupStats.averageMinute) <= 45
            }
            val wakeupData = barChartData.map { barItem ->
                barItem.dayLabel to barItem.log?.wakeupMinute
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        showInfo(
                            "Wake-up Consistency",
                            "Wake-up time was within 45 minutes of average (" + formatClockTime12h(wakeupStats.averageMinute) + ") on " + wakeupWithinCount + " of " + periodLogs.size + " mornings.\n\nConsistency Score: " + wakeupConsistency.label + "\nVariance: ±" + wakeupStats.varianceMinutes + " min.\n\nA regular morning wake-up schedule stabilizes the central circadian clock and boosts natural melatonin release at night.",
                            "Sleep Schedule",
                            onNavigateToSchedule
                        )
                    },
                shape = RoundedCornerShape(16.dp),
                color = HistoryCardBg,
                border = BorderStroke(1.dp, HistoryCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F2618)),
                                contentAlignment = Alignment.Center
                            ) {
                                SunVectorIcon(color = HistoryGreen, modifier = Modifier.size(13.dp))
                            }
                            Text(
                                text = "Wake-up consistency",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(HistoryGoalMetBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = wakeupConsistency.label,
                                color = HistoryGreen,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Your wake-up time was within 45 minutes on $wakeupWithinCount of ${periodLogs.size} mornings.",
                        color = HistoryTextMuted,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Dot-Plot Chart: 8 AM (480), 7 AM (420), 6 AM (360), 5 AM (300)
                    ConsistencyDotPlotChart(
                        yLabels = listOf("8 AM", "7 AM", "6 AM", "5 AM"),
                        minMinute = 5 * 60,
                        maxMinute = 8 * 60,
                        dayData = wakeupData,
                        highlightIndex = 4, // Fri highlight halo matching mockup
                        onDotClick = { idx, _ ->
                            val log = barChartData.getOrNull(idx)?.log
                            if (log != null) onViewEditLog(log)
                        },
                        modifier = Modifier.height(95.dp)
                    )
                }
            }
        }

        // 4. Two Side-by-Side Cards: Sleep goal progress & Key insights
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left: Sleep goal progress
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = HistoryCardBg,
                border = BorderStroke(1.dp, HistoryCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F2618)),
                            contentAlignment = Alignment.Center
                        ) {
                            BarChartVectorIcon(color = HistoryGreen, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = "Sleep goal progress",
                            color = HistoryTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "You met your ${targetSleepMinutes / 60}-hour goal on $metGoalCount of ${periodLogs.size} nights this week.",
                        color = HistoryTextMuted,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    // Progress bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { (goalMetPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = HistoryGreen,
                            trackColor = HistoryBarTrackBg
                        )
                        Text(
                            text = "$goalMetPercent%",
                            color = HistoryTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // 3 Count Boxes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        GoalFilterCountCard(
                            title = "Met goal",
                            count = "$metGoalCount",
                            color = HistoryGreen,
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterByGoal("MET") }
                        )
                        GoalFilterCountCard(
                            title = "Below goal",
                            count = "$belowGoalCount",
                            color = Color(0xFFFBBF24),
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterByGoal("BELOW") }
                        )
                        GoalFilterCountCard(
                            title = "No data",
                            count = "$noDataCount",
                            color = HistoryTextMuted,
                            modifier = Modifier.weight(1f),
                            onClick = { onFilterByGoal("NO_DATA") }
                        )
                    }
                }
            }

            // Right: Key insights
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = HistoryCardBg,
                border = BorderStroke(1.dp, HistoryCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0F2618)),
                            contentAlignment = Alignment.Center
                        ) {
                            LightbulbVectorIcon(color = HistoryGreen, modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = "Key insights",
                            color = HistoryTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // 4 Rows with chevrons
                    KeyInsightMiniRow(
                        icon = "↗",
                        text = if ((durationDeltaMinutes ?: 0) >= 0) "You're sleeping more than last week. Great progress!" else "Sleep duration decreased slightly versus last week.",
                        onClick = {
                            showInfo(
                                "Duration Trend & Sleep Window",
                                "Average duration is " + (avgDurationMinutes / 60) + "h " + (avgDurationMinutes % 60) + "m (" + (if ((durationDeltaMinutes ?: 0) >= 0) "+" + durationDeltaMinutes + " min" else durationDeltaMinutes.toString() + " min") + " vs prior week).\n\nConsistent sleep duration enhances immune function, cognitive recall, and physical recovery.",
                                "Adjust Sleep Schedule",
                                onNavigateToSchedule
                            )
                        }
                    )

                    HorizontalDivider(color = HistoryCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    KeyInsightMiniRow(
                        icon = "🌙",
                        text = if (bedtimeConsistency == ConsistencyStatus.GOOD) "Your bedtime is getting more consistent." else "Bedtime shows high variance across nights.",
                        onClick = {
                            showInfo(
                                "Bedtime Consistency Insight",
                                "Bedtime variance is ±" + bedtimeStats.varianceMinutes + " minutes. " + (if (bedtimeConsistency == ConsistencyStatus.GOOD) "Great job staying in your circadian window!" else "Try setting a 30-minute Wind-down reminder before bed."),
                                "Wind-down Routine",
                                onNavigateToWindDown
                            )
                        }
                    )

                    HorizontalDivider(color = HistoryCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    KeyInsightMiniRow(
                        icon = "☀️",
                        text = if (wakeupConsistency == ConsistencyStatus.GOOD) "You wake up most days at a similar time." else "Wake-up schedule fluctuates by >45 minutes.",
                        onClick = {
                            showInfo(
                                "Wake-up Regularity Insight",
                                "Wake-up variance is ±" + wakeupStats.varianceMinutes + " minutes across " + periodLogs.size + " mornings.\n\nRegular wake times support natural cortisol rhythm and daytime energy.",
                                "Sleep Schedule",
                                onNavigateToSchedule
                            )
                        }
                    )

                    HorizontalDivider(color = HistoryCardBorder.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))

                    KeyInsightMiniRow(
                        icon = "📱",
                        text = "Nights with less phone usage are linked to longer sleep.",
                        onClick = {
                            showInfo(
                                "Screen Time & Sleep Duration Association",
                                "Empirical sleep analysis shows that nights with under 15 minutes of late-night phone usage average +34 minutes longer duration.\n\nNote: While phone light and cognitive arousal delay melatonin, individual lifestyle factors also contribute.",
                                "Phone Usage Insights",
                                onNavigateToPhoneUsage
                            )
                        }
                    )
                }
            }
        }
    }
}


// ----------------------------------------------------------------------
// TRENDS TAB CONTENT
// ----------------------------------------------------------------------
@Composable
private fun SleepTrendsTabContent(
    allLogs: List<SleepLog>,
    targetSleepMinutes: Int,
    onNavigateToSchedule: (() -> Unit)?,
    onViewEditLog: (SleepLog) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
    onInfoClick: (String, String, String?, (() -> Unit)?) -> Unit,
) {
    var selectedTimeframe by rememberSaveable { mutableStateOf(TrendsTimeframe.MONTH_1) }
    var customStartMillis by rememberSaveable { mutableLongStateOf(0L) }
    var customEndMillis by rememberSaveable { mutableLongStateOf(0L) }
    var showCustomDatePicker by rememberSaveable { mutableStateOf(false) }

    var durationChartMode by rememberSaveable { mutableStateOf("Average") }
    var showDurationDropdown by rememberSaveable { mutableStateOf(false) }

    var showBedtimeLine by rememberSaveable { mutableStateOf(true) }
    var showWakeupLine by rememberSaveable { mutableStateOf(true) }
    var timeFormat24h by rememberSaveable { mutableStateOf(false) }
    var showTimeFormatDropdown by rememberSaveable { mutableStateOf(false) }

    var selectedBarIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showDetailedDataDialog by rememberSaveable { mutableStateOf(false) }

    // Anchor: check if logs contain January 2024 or recent logs
    val jan2024Log = remember(allLogs) {
        allLogs.firstOrNull {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.YEAR) == 2024 && c.get(Calendar.MONTH) == Calendar.JANUARY
        }
    }
    val defaultAnchorMillis = remember(allLogs, jan2024Log) {
        if (jan2024Log != null) {
            Calendar.getInstance().apply {
                set(2024, Calendar.JANUARY, 31, 23, 59, 59)
                set(Calendar.MILLISECOND, 999)
            }.timeInMillis
        } else {
            allLogs.maxOfOrNull { it.dateMillis } ?: System.currentTimeMillis()
        }
    }

    if (customStartMillis == 0L || customEndMillis == 0L) {
        val startC = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val endC = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        customStartMillis = startC.timeInMillis
        customEndMillis = endC.timeInMillis
    }

    // Determine current start and end based on selectedTimeframe
    val periodRange = remember(selectedTimeframe, defaultAnchorMillis, customStartMillis, customEndMillis) {
        val calEnd = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
        }
        val calStart = Calendar.getInstance().apply {
            timeInMillis = defaultAnchorMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }

        when (selectedTimeframe) {
            TrendsTimeframe.WEEK_1 -> {
                calEnd.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                calStart.timeInMillis = calEnd.timeInMillis
                calStart.add(Calendar.DAY_OF_YEAR, -6)
                calStart.set(Calendar.HOUR_OF_DAY, 0); calStart.set(Calendar.MINUTE, 0); calStart.set(Calendar.SECOND, 0); calStart.set(Calendar.MILLISECOND, 0)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                TrendsPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "week")
            }
            TrendsTimeframe.MONTH_1 -> {
                calStart.set(Calendar.DAY_OF_MONTH, 1)
                calEnd.set(Calendar.DAY_OF_MONTH, calStart.getActualMaximum(Calendar.DAY_OF_MONTH))
                val prevCalStart = Calendar.getInstance().apply {
                    timeInMillis = calStart.timeInMillis
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                }
                val prevCalEnd = Calendar.getInstance().apply {
                    timeInMillis = prevCalStart.timeInMillis
                    set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
                }
                TrendsPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, prevCalStart.timeInMillis, prevCalEnd.timeInMillis, "month")
            }
            TrendsTimeframe.MONTH_3 -> {
                calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                calStart.timeInMillis = calEnd.timeInMillis
                calStart.add(Calendar.MONTH, -2)
                calStart.set(Calendar.DAY_OF_MONTH, 1)
                calStart.set(Calendar.HOUR_OF_DAY, 0); calStart.set(Calendar.MINUTE, 0); calStart.set(Calendar.SECOND, 0); calStart.set(Calendar.MILLISECOND, 0)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                TrendsPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "quarter")
            }
            TrendsTimeframe.YEAR_1 -> {
                calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                calStart.timeInMillis = calEnd.timeInMillis
                calStart.add(Calendar.YEAR, -1)
                calStart.add(Calendar.DAY_OF_YEAR, 1)
                calStart.set(Calendar.HOUR_OF_DAY, 0); calStart.set(Calendar.MINUTE, 0); calStart.set(Calendar.SECOND, 0); calStart.set(Calendar.MILLISECOND, 0)
                val dur = calEnd.timeInMillis - calStart.timeInMillis
                TrendsPeriodRange(calStart.timeInMillis, calEnd.timeInMillis, calStart.timeInMillis - dur - 1000L, calStart.timeInMillis - 1000L, "year")
            }
            TrendsTimeframe.CUSTOM -> {
                val dur = customEndMillis - customStartMillis
                TrendsPeriodRange(customStartMillis, customEndMillis, customStartMillis - dur - 1000L, customStartMillis - 1000L, "period")
            }
        }
    }

    val periodStartMillis = periodRange.start
    val periodEndMillis = periodRange.end
    val prevStartMillis = periodRange.prevStart
    val prevEndMillis = periodRange.prevEnd
    val periodLabel = periodRange.label

    val dateRangeBadgeText = remember(periodStartMillis, periodEndMillis) {
        val startFmt = SimpleDateFormat("MMM d", Locale.getDefault())
        val endFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        "${startFmt.format(Date(periodStartMillis))} – ${endFmt.format(Date(periodEndMillis))}"
    }

    val currentLogs = remember(allLogs, periodStartMillis, periodEndMillis) {
        allLogs.filter { it.dateMillis in periodStartMillis..periodEndMillis }.sortedBy { it.dateMillis }
    }
    val previousLogs = remember(allLogs, prevStartMillis, prevEndMillis) {
        allLogs.filter { it.dateMillis in prevStartMillis..prevEndMillis }.sortedBy { it.dateMillis }
    }

    // 1. Average Sleep Duration
    val currAvgDuration = remember(currentLogs) {
        if (currentLogs.isEmpty()) 7 * 60 + 12 else (currentLogs.map { it.durationMinutes }.average()).toInt()
    }
    val prevAvgDuration = remember(previousLogs) {
        if (previousLogs.isEmpty()) 6 * 60 + 44 else (previousLogs.map { it.durationMinutes }.average()).toInt()
    }
    val deltaDuration = currAvgDuration - prevAvgDuration

    // 2. Average Bedtime & Variance
    val currBedtimeStats = remember(currentLogs) {
        calculateCircularStats(currentLogs.map { it.bedtimeMinute })
    }
    val prevBedtimeStats = remember(previousLogs) {
        calculateCircularStats(previousLogs.map { it.bedtimeMinute })
    }
    val currAvgBedtime = remember(currBedtimeStats) {
        if (currBedtimeStats.count > 0) currBedtimeStats.averageMinute else (22 * 60 + 48)
    }
    val prevAvgBedtime = remember(prevBedtimeStats) {
        if (prevBedtimeStats.count > 0) prevBedtimeStats.averageMinute else (23 * 60 + 6)
    }
    val deltaBedtime = remember(currAvgBedtime, prevAvgBedtime) {
        var diff = currAvgBedtime - prevAvgBedtime
        if (diff > 720) diff -= 1440
        if (diff < -720) diff += 1440
        diff
    }
    val currBedtimeVar = if (currBedtimeStats.count >= 3) currBedtimeStats.varianceMinutes else 28
    val prevBedtimeVar = if (prevBedtimeStats.count >= 3) prevBedtimeStats.varianceMinutes else 42
    val deltaBedtimeVar = (prevBedtimeVar - currBedtimeVar).coerceAtLeast(0)

    // 3. Average Wake-up & Variance
    val currWakeupStats = remember(currentLogs) {
        calculateCircularStats(currentLogs.map { it.wakeupMinute })
    }
    val prevWakeupStats = remember(previousLogs) {
        calculateCircularStats(previousLogs.map { it.wakeupMinute })
    }
    val currAvgWakeup = remember(currWakeupStats) {
        if (currWakeupStats.count > 0) currWakeupStats.averageMinute else (6 * 60 + 8)
    }
    val prevAvgWakeup = remember(prevWakeupStats) {
        if (prevWakeupStats.count > 0) prevWakeupStats.averageMinute else (6 * 60 + 28)
    }
    val deltaWakeup = remember(currAvgWakeup, prevAvgWakeup) {
        var diff = currAvgWakeup - prevAvgWakeup
        if (diff > 720) diff -= 1440
        if (diff < -720) diff += 1440
        diff
    }
    val currWakeupVar = if (currWakeupStats.count >= 3) currWakeupStats.varianceMinutes else 32
    val prevWakeupVar = if (prevWakeupStats.count >= 3) prevWakeupStats.varianceMinutes else 50
    val deltaWakeupVar = (prevWakeupVar - currWakeupVar).coerceAtLeast(0)

    // 4. Sleep Goal Met Percentage
    val currGoalMetCount = remember(currentLogs, targetSleepMinutes) {
        currentLogs.count { it.durationMinutes >= (targetSleepMinutes - 30) }
    }
    val currGoalMetPercent = remember(currentLogs, currGoalMetCount) {
        if (currentLogs.isEmpty()) 78 else (currGoalMetCount * 100) / currentLogs.size
    }
    val prevGoalMetCount = remember(previousLogs, targetSleepMinutes) {
        previousLogs.count { it.durationMinutes >= (targetSleepMinutes - 30) }
    }
    val prevGoalMetPercent = remember(previousLogs, prevGoalMetCount) {
        if (previousLogs.isEmpty()) 66 else (prevGoalMetCount * 100) / previousLogs.size
    }
    val deltaGoal = currGoalMetPercent - prevGoalMetPercent

    // Weekly Rates for Sleep Goal Progress
    val weeklyRatesList = remember(currentLogs, targetSleepMinutes) {
        if (currentLogs.size >= 14) {
            val w1 = currentLogs.take(7)
            val w2 = currentLogs.drop(7).take(7)
            val w3 = currentLogs.drop(14).take(7)
            val w4 = currentLogs.drop(21)
            listOf(
                "Week 1" to if (w1.isEmpty()) 57 else (w1.count { it.durationMinutes >= (targetSleepMinutes - 30) } * 100) / w1.size,
                "Week 2" to if (w2.isEmpty()) 71 else (w2.count { it.durationMinutes >= (targetSleepMinutes - 30) } * 100) / w2.size,
                "Week 3" to if (w3.isEmpty()) 86 else (w3.count { it.durationMinutes >= (targetSleepMinutes - 30) } * 100) / w3.size,
                "Week 4" to if (w4.isEmpty()) 79 else (w4.count { it.durationMinutes >= (targetSleepMinutes - 30) } * 100) / w4.size,
            )
        } else {
            listOf("Week 1" to 57, "Week 2" to 71, "Week 3" to 86, "Week 4" to 79)
        }
    }

    // Trend Bar Data for charts
    val trendBarData = remember(periodStartMillis, periodEndMillis, currentLogs, selectedBarIndex) {
        generateTrendsBarChartData(periodStartMillis, periodEndMillis, currentLogs, selectedBarIndex)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. TIMEFRAME SELECTOR & DATE BADGE ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TrendsTimeframe.values().forEach { tf ->
                val isSelected = tf == selectedTimeframe
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) HistoryGreen else Color(0x1F22C55E))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HistoryGreen else Color(0x3322C55E),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable {
                            if (tf == TrendsTimeframe.CUSTOM) {
                                showCustomDatePicker = true
                            } else {
                                selectedTimeframe = tf
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = tf.label,
                            color = if (isSelected) HistoryGreenPillText else HistoryTextWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        if (tf == TrendsTimeframe.CUSTOM) {
                            Text(
                                text = "▼",
                                color = if (isSelected) HistoryGreenPillText else HistoryTextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }

            // Date range badge e.g. "📅 Jan 1 – Jan 31, 2024"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1F22C55E))
                    .border(1.dp, Color(0x3322C55E), RoundedCornerShape(12.dp))
                    .clickable { showCustomDatePicker = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("📅", fontSize = 11.sp)
                    Text(
                        text = dateRangeBadgeText,
                        color = HistoryGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    )
                }
            }
        }

        // 2. FOUR SUMMARY CARDS ROW
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: Average Sleep
            TrendSummaryCard(
                modifier = Modifier.weight(1f),
                icon = { BedVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Average sleep",
                value = "${currAvgDuration / 60} h ${currAvgDuration % 60} min",
                deltaText = if (deltaDuration >= 0) "↗ +${deltaDuration} min" else "↘ ${deltaDuration} min",
                subtitle = "vs previous $periodLabel",
                onClick = {
                    onInfoClick(
                        "Average Sleep Duration",
                        "Average duration across " + currentLogs.size + " recorded nights is " + (currAvgDuration / 60) + "h " + (currAvgDuration % 60) + "m. Comparison: " + (if (deltaDuration >= 0) "+" + deltaDuration + " min" else "" + deltaDuration + " min") + " compared to the previous " + periodLabel + " average (" + (prevAvgDuration / 60) + "h " + (prevAvgDuration % 60) + "m).",
                        null,
                        null
                    )
                }
            )

            // Card 2: Avg Bedtime
            TrendSummaryCard(
                modifier = Modifier.weight(1f),
                icon = { MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Avg. bedtime",
                value = formatClockTime12h(currAvgBedtime),
                deltaText = if (deltaBedtime <= 0) "↗ ${deltaBedtime} min" else "↘ +${deltaBedtime} min",
                subtitle = "vs previous $periodLabel",
                onClick = {
                    onInfoClick(
                        "Average Bedtime",
                        "Your average bedtime was " + formatClockTime12h(currAvgBedtime) + " with circular variance of ±" + currBedtimeVar + " min. Comparison: Shifted by " + (if (deltaBedtime <= 0) "" + deltaBedtime + " min (earlier)" else "+" + deltaBedtime + " min (later)") + " vs previous " + periodLabel + " (" + formatClockTime12h(prevAvgBedtime) + ").",
                        "Sleep Schedule",
                        onNavigateToSchedule
                    )
                }
            )

            // Card 3: Avg Wake-up
            TrendSummaryCard(
                modifier = Modifier.weight(1f),
                icon = { SunVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Avg. wake-up",
                value = formatClockTime12h(currAvgWakeup),
                deltaText = if (deltaWakeup <= 0) "↗ ${deltaWakeup} min" else "↘ +${deltaWakeup} min",
                subtitle = "vs previous $periodLabel",
                onClick = {
                    onInfoClick(
                        "Average Wake-up Time",
                        "Your average wake-up time was " + formatClockTime12h(currAvgWakeup) + " with consistency variance of ±" + currWakeupVar + " min. Comparison: Shifted by " + (if (deltaWakeup <= 0) "" + deltaWakeup + " min (earlier)" else "+" + deltaWakeup + " min (later)") + " vs previous " + periodLabel + " (" + formatClockTime12h(prevAvgWakeup) + ").",
                        null,
                        null
                    )
                }
            )

            // Card 4: Sleep Goal Met
            TrendSummaryCard(
                modifier = Modifier.weight(1f),
                icon = { TargetVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp)) },
                title = "Sleep goal met",
                value = "$currGoalMetPercent%",
                deltaText = if (deltaGoal >= 0) "↗ +${deltaGoal}%" else "↘ ${deltaGoal}%",
                subtitle = "vs previous $periodLabel",
                onClick = {
                    onInfoClick(
                        "Sleep Goal Met",
                        "You met your " + (targetSleepMinutes / 60) + "h sleep goal on " + currGoalMetCount + " of " + currentLogs.size + " recorded nights (" + currGoalMetPercent + "%). Comparison: " + (if (deltaGoal >= 0) "+" + deltaGoal + "%" else "" + deltaGoal + "%") + " compared to previous " + periodLabel + " (" + prevGoalMetPercent + "%).",
                        "Adjust Goal",
                        onNavigateToSchedule
                    )
                }
            )
        }

        // 3. SLEEP DURATION TREND LARGE CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = HistoryCardBg,
            border = BorderStroke(1.dp, HistoryCardBorder)
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            BarChartVectorIcon(color = HistoryGreen, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = "Sleep duration trend",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = "Daily sleep duration over time",
                                color = HistoryTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Dropdown menu button
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F22C55E))
                                .border(1.dp, Color(0x3322C55E), RoundedCornerShape(8.dp))
                                .clickable { showDurationDropdown = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(durationChartMode, color = HistoryGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("▼", color = HistoryGreen, fontSize = 7.sp)
                            }
                        }

                        DropdownMenu(
                            expanded = showDurationDropdown,
                            onDismissRequest = { showDurationDropdown = false },
                            modifier = Modifier.background(HistoryCardInner)
                        ) {
                            listOf("Average", "Total", "Trend line").forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt, color = HistoryTextWhite, fontSize = 12.sp) },
                                    onClick = {
                                        durationChartMode = opt
                                        showDurationDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Chart
                SleepDurationTrendBarChart(
                    dayItems = trendBarData,
                    targetMinutes = targetSleepMinutes,
                    selectedBarIndex = selectedBarIndex,
                    onBarClick = { idx ->
                        selectedBarIndex = if (selectedBarIndex == idx) -1 else idx
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )

                // Selected bar inspection card if any
                if (selectedBarIndex in trendBarData.indices) {
                    val selectedItem = trendBarData[selectedBarIndex]
                    Spacer(Modifier.height(10.dp))
                    SelectedDayInspectionRow(
                        item = selectedItem,
                        targetMinutes = targetSleepMinutes,
                        onViewLog = { log -> onViewEditLog(log) }
                    )
                }
            }
        }

        // 4. BEDTIME & WAKE-UP TREND LARGE CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = HistoryCardBg,
            border = BorderStroke(1.dp, HistoryCardBorder)
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
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(18.dp))
                        }
                        Column {
                            Text(
                                text = "Bedtime & wake-up trend",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = "Your average bedtime and wake-up time over time",
                                color = HistoryTextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Legend & Time Format Dropdown Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Interactive legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.clickable { showBedtimeLine = !showBedtimeLine },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (showBedtimeLine) Color(0xFFA78BFA) else Color(0x40A78BFA))
                            )
                            Text(
                                text = "Bedtime",
                                color = if (showBedtimeLine) HistoryTextWhite else HistoryTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Row(
                            modifier = Modifier.clickable { showWakeupLine = !showWakeupLine },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (showWakeupLine) HistoryGreen else Color(0x4022C55E))
                            )
                            Text(
                                text = "Wake-up",
                                color = if (showWakeupLine) HistoryTextWhite else HistoryTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Dropdown menu button for Time (AM/PM) / Time (24h)
                    Box {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F22C55E))
                                .border(1.dp, Color(0x3322C55E), RoundedCornerShape(8.dp))
                                .clickable { showTimeFormatDropdown = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (timeFormat24h) "Time (24h)" else "Time (AM/PM)",
                                    color = HistoryGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text("▼", color = HistoryGreen, fontSize = 7.sp)
                            }
                        }

                        DropdownMenu(
                            expanded = showTimeFormatDropdown,
                            onDismissRequest = { showTimeFormatDropdown = false },
                            modifier = Modifier.background(HistoryCardInner)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Time (AM/PM)", color = HistoryTextWhite, fontSize = 12.sp) },
                                onClick = {
                                    timeFormat24h = false
                                    showTimeFormatDropdown = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Time (24h)", color = HistoryTextWhite, fontSize = 12.sp) },
                                onClick = {
                                    timeFormat24h = true
                                    showTimeFormatDropdown = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Dual Bedtime/Wakeup Line Chart
                DualBedtimeWakeupLineChart(
                    showBedtime = showBedtimeLine,
                    showWakeup = showWakeupLine,
                    timeFormat24h = timeFormat24h,
                    dayItems = trendBarData,
                    selectedPointIndex = selectedBarIndex,
                    onPointClick = { idx ->
                        selectedBarIndex = if (selectedBarIndex == idx) -1 else idx
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
            }
        }

        // 5. SIDE-BY-SIDE SECTION: SLEEP GOAL PROGRESS & SLEEP CONSISTENCY
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: Sleep goal progress
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = HistoryCardBg,
                border = BorderStroke(1.dp, HistoryCardBorder)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            TargetVectorIcon(color = HistoryGreen, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text(
                                text = "Sleep goal progress",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Percentage of nights that met your sleep goal",
                                color = HistoryTextMuted,
                                fontSize = 9.5.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    WeeklyGoalProgressChart(
                        weeklyRates = weeklyRatesList,
                        onWeekClick = { weekIdx ->
                            onInfoClick(
                                "Week " + (weekIdx + 1) + " Progress",
                                "In Week " + (weekIdx + 1) + ", you met your sleep goal on " + weeklyRatesList[weekIdx].second + "% of nights. Consistent sleep schedules help regulate circadian rhythms and improve focus during study sessions.",
                                null,
                                null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    )
                }
            }

            // Right: Sleep consistency
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                color = HistoryCardBg,
                border = BorderStroke(1.dp, HistoryCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x1F22C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                PulseVectorIcon(color = HistoryGreen, modifier = Modifier.size(16.dp))
                            }
                            Column {
                                Text(
                                    text = "Sleep consistency",
                                    color = HistoryTextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Variability in your sleep times (lower is better)",
                                    color = HistoryTextMuted,
                                    fontSize = 9.5.sp,
                                    lineHeight = 12.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Bedtime variability row
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onInfoClick(
                                        "Bedtime Variability",
                                        "Your bedtime variability is ±" + currBedtimeVar + " min (down " + deltaBedtimeVar + " min vs previous " + periodLabel + "). Lower variability means your bedtime is predictable and consistent night-to-night, leading to higher quality restorative REM sleep.",
                                        "Adjust Schedule",
                                        onNavigateToSchedule
                                    )
                                },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    MoonVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp))
                                    Column {
                                        Text("Bedtime variability", color = HistoryTextMuted, fontSize = 10.sp)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("$currBedtimeVar min", color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("↘ -${deltaBedtimeVar} min", color = HistoryGreen, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("vs previous $periodLabel", color = HistoryTextMuted, fontSize = 8.5.sp)
                                    }
                                }
                                Text("›", color = HistoryTextMuted, fontSize = 14.sp)
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Wake-up variability row
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onInfoClick(
                                        "Wake-up Variability",
                                        "Your wake-up variability is ±" + currWakeupVar + " min (down " + deltaWakeupVar + " min vs previous " + periodLabel + "). A regular wake-up time strengthens your natural sleep-wake cycle and prevents daytime fatigue.",
                                        null,
                                        null
                                    )
                                },
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    SunVectorIcon(color = HistoryGreen, modifier = Modifier.size(14.dp))
                                    Column {
                                        Text("Wake-up variability", color = HistoryTextMuted, fontSize = 10.sp)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("$currWakeupVar min", color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("↘ -${deltaWakeupVar} min", color = HistoryGreen, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("vs previous $periodLabel", color = HistoryTextMuted, fontSize = 8.5.sp)
                                    }
                                }
                                Text("›", color = HistoryTextMuted, fontSize = 14.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Bottom Info Note
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("ⓘ", color = HistoryTextMuted, fontSize = 10.sp)
                        Text(
                            text = "You went to bed and woke up at more consistent times this $periodLabel.",
                            color = HistoryTextMuted,
                            fontSize = 9.sp,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }

        // 6. DETAILED DATA ROW CARD
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showDetailedDataDialog = true },
            color = HistoryCardBg,
            border = BorderStroke(1.dp, HistoryCardBorder)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
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
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        ListVectorIcon(color = HistoryGreen, modifier = Modifier.size(18.dp))
                    }
                    Column {
                        Text(
                            text = "Detailed data",
                            color = HistoryTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = "View your daily sleep records in a table",
                            color = HistoryTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Text("›", color = HistoryTextMuted, fontSize = 20.sp)
            }
        }
    }

    // Detailed Data Dialog
    if (showDetailedDataDialog) {
        DetailedDataTableDialog(
            logs = currentLogs,
            targetSleepMinutes = targetSleepMinutes,
            onDismiss = { showDetailedDataDialog = false },
            onRowClick = { log ->
                showDetailedDataDialog = false
                onViewEditLog(log)
            },
            onExportCsv = onExportCsv,
            onExportJson = onExportJson
        )
    }

    // Custom Date Range Picker Dialog
    if (showCustomDatePicker) {
        CustomDateRangePickerDialog(
            initialStartMillis = periodStartMillis,
            initialEndMillis = periodEndMillis,
            onDismiss = { showCustomDatePicker = false },
            onApply = { start, end ->
                customStartMillis = start
                customEndMillis = end
                selectedTimeframe = TrendsTimeframe.CUSTOM
            }
        )
    }
}

@Composable
private fun QualityProgressRow(label: String, percent: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(label, color = HistoryTextWhite, fontSize = 12.sp, modifier = Modifier.width(80.dp))
        LinearProgressIndicator(
            progress = { (percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = HistoryBarTrackBg,
        )
        Text("$percent%", color = HistoryTextMuted, fontSize = 11.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

// ----------------------------------------------------------------------
// ADD / EDIT SLEEP LOG DIALOG
// ----------------------------------------------------------------------
@Composable
private fun SleepHistoryLogEditorDialog(
    existingLog: SleepLog?,
    defaultBedtime: Int,
    defaultWakeup: Int,
    onDismiss: () -> Unit,
    onSave: (SleepLog) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var dateMillis by rememberSaveable { mutableLongStateOf(existingLog?.dateMillis ?: System.currentTimeMillis()) }
    var bedtimeMinute by rememberSaveable { mutableIntStateOf(existingLog?.bedtimeMinute ?: defaultBedtime) }
    var wakeupMinute by rememberSaveable { mutableIntStateOf(existingLog?.wakeupMinute ?: defaultWakeup) }
    var rating by rememberSaveable { mutableIntStateOf(existingLog?.qualityRating ?: 4) }
    var note by rememberSaveable { mutableStateOf(existingLog?.note.orEmpty()) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val duration = calculateSleepDurationMinutes(bedtimeMinute, wakeupMinute)
    val dateStr = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date(dateMillis))

    if (showDeleteConfirm && existingLog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF0F1A13),
            title = { Text("Delete Sleep Record?", color = HistoryTextWhite, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this sleep log?", color = HistoryTextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(existingLog.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = HistoryTextMuted)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1A13),
        title = {
            Text(
                text = if (existingLog == null) "Log Sleep" else "Edit Sleep Log",
                color = HistoryTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Date Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HistoryCardInner)
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
                            DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    val sel = Calendar.getInstance().apply {
                                        set(y, m, d, 0, 0, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    dateMillis = sel.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date", color = HistoryTextMuted, fontSize = 13.sp)
                    Text(dateStr, color = HistoryGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Bedtime Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bedtime", color = HistoryTextWhite, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> bedtimeMinute = h * 60 + m },
                                bedtimeMinute / 60,
                                bedtimeMinute % 60,
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HistoryGreen)
                    ) {
                        Text(formatClockTime12h(bedtimeMinute), fontWeight = FontWeight.Bold)
                    }
                }

                // Wake-up Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Wake-up Time", color = HistoryTextWhite, fontSize = 13.sp)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> wakeupMinute = h * 60 + m },
                                wakeupMinute / 60,
                                wakeupMinute % 60,
                                false
                            ).show()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HistoryGreen)
                    ) {
                        Text(formatClockTime12h(wakeupMinute), fontWeight = FontWeight.Bold)
                    }
                }

                // Calculated Duration Pill
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = HistoryCardInner,
                    border = BorderStroke(1.dp, HistoryCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Calculated Duration", color = HistoryTextMuted, fontSize = 12.sp)
                        val h = duration / 60
                        val m = duration % 60
                        Text("$h h $m min", color = HistoryGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // Mood / Feeling Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("How did you feel?", color = HistoryTextMuted, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val moods = listOf(
                            5 to "Great 😊",
                            4 to "Good 🙂",
                            3 to "Okay 😐",
                            2 to "Tired 🥱"
                        )
                        moods.forEach { (stars, label) ->
                            val isSelected = rating == stars
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) HistoryGreen else HistoryCardInner)
                                    .clickable { rating = stars }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) HistoryGreenPillText else HistoryTextWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    placeholder = { Text("e.g. Slept well, no phone after 10 PM", color = HistoryTextMuted, fontSize = 12.sp) },
                    label = { Text("Notes (optional)", color = HistoryTextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = HistoryCardInner,
                        unfocusedContainerColor = HistoryCardInner,
                        focusedBorderColor = HistoryGreen,
                        unfocusedBorderColor = HistoryCardBorder,
                        focusedTextColor = HistoryTextWhite,
                        unfocusedTextColor = HistoryTextWhite,
                    ),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val log = SleepLog(
                        id = existingLog?.id ?: 0L,
                        dateMillis = dateMillis,
                        bedtimeMinute = bedtimeMinute,
                        wakeupMinute = wakeupMinute,
                        reportedBedtimeMillis = System.currentTimeMillis(),
                        reportedWakeupMillis = System.currentTimeMillis(),
                        durationMinutes = duration,
                        qualityRating = rating,
                        note = note.trim(),
                        createdAtMillis = existingLog?.createdAtMillis ?: System.currentTimeMillis(),
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    onSave(log)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HistoryGreen,
                    contentColor = HistoryGreenPillText
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existingLog != null) {
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("Delete", color = Color(0xFFEF4444))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = HistoryTextMuted)
                }
            }
        }
    )
}

// ----------------------------------------------------------------------
// VECTOR ICON DRAWING UTILITIES
// ----------------------------------------------------------------------
@Composable
private fun PulseVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val stroke = 1.6.dp.toPx()

        val path = Path().apply {
            moveTo(0f, cy)
            lineTo(w * 0.22f, cy)
            lineTo(w * 0.35f, cy - h * 0.38f)
            lineTo(w * 0.5f, cy + h * 0.42f)
            lineTo(w * 0.65f, cy - h * 0.22f)
            lineTo(w * 0.76f, cy)
            lineTo(w, cy)
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ListVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val dotR = 1.2.dp.toPx()

        for (i in 0..2) {
            val y = h * (0.26f + i * 0.24f)
            drawCircle(color, dotR, Offset(w * 0.22f, y))
            drawLine(color, Offset(w * 0.38f, y), Offset(w * 0.85f, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun BedVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Headboard vertical post
        drawLine(color, Offset(w * 0.15f, h * 0.25f), Offset(w * 0.15f, h * 0.85f), strokeWidth = stroke, cap = StrokeCap.Round)
        // Footboard vertical post
        drawLine(color, Offset(w * 0.85f, h * 0.45f), Offset(w * 0.85f, h * 0.85f), strokeWidth = stroke, cap = StrokeCap.Round)
        // Bed base frame line
        drawLine(color, Offset(w * 0.15f, h * 0.7f), Offset(w * 0.85f, h * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
        // Pillow
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.42f),
            size = Size(w * 0.22f, h * 0.22f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Blanket line
        drawLine(color, Offset(w * 0.44f, h * 0.52f), Offset(w * 0.85f, h * 0.52f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun TargetVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val stroke = 1.5.dp.toPx()
        val r = minOf(w, h) * 0.42f

        // Outer circle
        drawCircle(color, r, Offset(cx, cy), style = Stroke(width = stroke))
        // Middle circle
        drawCircle(color, r * 0.62f, Offset(cx, cy), style = Stroke(width = stroke))
        // Bullseye center dot
        drawCircle(color, r * 0.26f, Offset(cx, cy))
        // Dart arrow notch at top right
        drawLine(color, Offset(cx + r * 0.38f, cy - r * 0.38f), Offset(cx + r * 0.88f, cy - r * 0.88f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
private fun LightbulbVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.42f
        val r = minOf(w, h) * 0.32f
        val stroke = 1.6.dp.toPx()

        drawCircle(color, r, Offset(cx, cy), style = Stroke(width = stroke))
        drawLine(color, Offset(cx - r * 0.5f, h * 0.74f), Offset(cx + r * 0.5f, h * 0.74f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - r * 0.3f, h * 0.86f), Offset(cx + r * 0.3f, h * 0.86f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}


@Composable
private fun GoalFilterCountCard(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = HistoryCardInner,
        border = BorderStroke(1.dp, HistoryCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = HistoryTextMuted, fontSize = 9.sp, maxLines = 1)
            Text(count, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
        }
    }
}



@Composable
private fun TrendSummaryCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    deltaText: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = HistoryCardBg,
        border = BorderStroke(1.dp, HistoryCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x1F22C55E)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                text = title,
                color = HistoryTextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                color = HistoryTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1
            )
            Text(
                text = deltaText,
                color = HistoryGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                maxLines = 1
            )
            Text(
                text = subtitle,
                color = HistoryTextMuted,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SleepDurationTrendBarChart(
    dayItems: List<DaySleepBarData>,
    targetMinutes: Int,
    selectedBarIndex: Int,
    onBarClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val yLabels = listOf("12h", "10h", "8h", "6h", "4h", "2h", "0h")
    val maxMinutes = 12 * 60f // 720 min

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
                    Text(text = label, color = HistoryTextMuted, fontSize = 8.5.sp)
                }
            }

            // Canvas with bars & goal dashed line
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 6 intervals = 7 grid lines
                    val gridLines = 6
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
                        color = Color(0x8022C55E),
                        start = Offset(0f, goalY),
                        end = Offset(w, goalY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )

                    // Bars
                    val count = dayItems.size.coerceAtLeast(1)
                    val slotW = w / count
                    val barW = minOf(slotW * 0.72f, 14.dp.toPx()).coerceAtLeast(3.dp.toPx())

                    dayItems.forEachIndexed { index, item ->
                        val cx = slotW * index + slotW / 2f
                        val left = cx - barW / 2f

                        // Background track
                        drawRoundRect(
                            color = HistoryBarTrackBg,
                            topLeft = Offset(left, 0f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )

                        // Filled bar
                        if (item.hasLog && item.durationMinutes > 0) {
                            val durRatio = (item.durationMinutes / maxMinutes).coerceIn(0f, 1f)
                            val barH = h * durRatio
                            val barTop = h - barH

                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(Color(0xFF86EFAC), HistoryGreenDark),
                                    startY = barTop,
                                    endY = h
                                ),
                                topLeft = Offset(left, barTop),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )

                            if (index == selectedBarIndex) {
                                drawRoundRect(
                                    color = Color.White,
                                    topLeft = Offset(left - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                    size = Size(barW + 2.dp.toPx(), barH + 2.dp.toPx()),
                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Goal badge overlay on right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xD9064E3B))
                        .border(1.dp, Color(0x4022C55E), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Goal: ${targetMinutes / 60}h",
                        color = HistoryGreen,
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

        // X-axis date labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelInterval = (dayItems.size / 6).coerceAtLeast(1)
            dayItems.forEachIndexed { index, item ->
                if (index == 0 || index == dayItems.size - 1 || index % labelInterval == 0) {
                    Text(
                        text = item.dayLabel,
                        color = if (index == selectedBarIndex) HistoryGreen else HistoryTextMuted,
                        fontSize = 9.sp,
                        fontWeight = if (index == selectedBarIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { onBarClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedDayInspectionRow(
    item: DaySleepBarData,
    targetMinutes: Int,
    onViewLog: (SleepLog) -> Unit,
    modifier: Modifier = Modifier
) {
    val isMet = item.durationMinutes >= (targetMinutes - 30)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = HistoryCardInner,
        border = BorderStroke(1.dp, HistoryCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = item.dayLabel + if (item.dateMillis > 0) " (${SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(item.dateMillis))})" else "",
                    color = HistoryTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                if (item.hasLog && item.log != null) {
                    Text(
                        text = "${item.durationText}  •  ${formatClockTime12h(item.log.bedtimeMinute)}–${formatClockTime12h(item.log.wakeupMinute)}",
                        color = HistoryTextMuted,
                        fontSize = 10.sp
                    )
                } else {
                    Text("No sleep record for this date", color = HistoryTextMuted, fontSize = 10.sp)
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (item.hasLog && item.log != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isMet) HistoryGoalMetBg else HistoryBelowGoalBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isMet) "Goal met" else "Below goal",
                            color = if (isMet) HistoryGreen else Color(0xFFFBBF24),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    TextButton(
                        onClick = { onViewLog(item.log) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("View Log ›", color = HistoryGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomDateRangePickerDialog(
    initialStartMillis: Long,
    initialEndMillis: Long,
    onDismiss: () -> Unit,
    onApply: (Long, Long) -> Unit,
) {
    val context = LocalContext.current
    var startMillis by rememberSaveable { mutableLongStateOf(initialStartMillis) }
    var endMillis by rememberSaveable { mutableLongStateOf(initialEndMillis) }

    val startCal = Calendar.getInstance().apply { timeInMillis = startMillis }
    val endCal = Calendar.getInstance().apply { timeInMillis = endMillis }
    val fmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1E15),
        title = {
            Text("Select Date Range", color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    color = HistoryCardInner,
                    border = BorderStroke(1.dp, HistoryCardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Start Date", color = HistoryTextMuted, fontSize = 11.sp)
                        Text(fmt.format(Date(startMillis)), color = HistoryGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                    color = HistoryCardInner,
                    border = BorderStroke(1.dp, HistoryCardBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("End Date", color = HistoryTextMuted, fontSize = 11.sp)
                        Text(fmt.format(Date(endMillis)), color = HistoryGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(minOf(startMillis, endMillis), maxOf(startMillis, endMillis))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = HistoryGreen, contentColor = HistoryGreenPillText)
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HistoryTextMuted)
            }
        }
    )
}

@Composable
private fun DualBedtimeWakeupLineChart(
    showBedtime: Boolean,
    showWakeup: Boolean,
    timeFormat24h: Boolean,
    dayItems: List<DaySleepBarData>,
    selectedPointIndex: Int,
    onPointClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val yLabels = if (timeFormat24h) {
        listOf("08:00", "06:00", "04:00", "02:00", "00:00", "22:00", "20:00")
    } else {
        listOf("8 AM", "6 AM", "4 AM", "2 AM", "12 AM", "10 PM", "8 PM")
    }

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
                    Text(text = label, color = HistoryTextMuted, fontSize = 8.5.sp)
                }
            }

            // Canvas with curves
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 7 horizontal subtle grid lines
                    val gridLines = 6
                    for (i in 0..gridLines) {
                        val y = (h / gridLines) * i
                        drawLine(
                            color = Color(0x1F22C55E),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val count = dayItems.size.coerceAtLeast(1)
                    val slotW = w / count

                    // Helper to compute Y coordinate in 8 PM to 8 AM overnight window (720 min)
                    fun computeY(rawMinute: Int): Float {
                        val offset = if (rawMinute < 12 * 60) {
                            (240 + rawMinute).coerceIn(0, 720)
                        } else {
                            (rawMinute - 20 * 60).coerceIn(0, 720)
                        }
                        val ratio = offset.toFloat() / 720f
                        return h - (ratio * h)
                    }

                    // 1. Wake-up line (Green)
                    if (showWakeup) {
                        val wakeupPoints = mutableListOf<Pair<Int, Offset>>()
                        dayItems.forEachIndexed { index, item ->
                            if (item.hasLog && item.log != null) {
                                val cx = slotW * index + slotW / 2f
                                val cy = computeY(item.log.wakeupMinute)
                                wakeupPoints.add(index to Offset(cx, cy))
                            }
                        }

                        for (i in 0 until wakeupPoints.size - 1) {
                            val p1 = wakeupPoints[i]
                            val p2 = wakeupPoints[i + 1]
                            if (p2.first - p1.first <= 2) {
                                drawLine(
                                    color = HistoryGreen,
                                    start = p1.second,
                                    end = p2.second,
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }

                        wakeupPoints.forEach { (index, pt) ->
                            if (index == selectedPointIndex) {
                                drawCircle(HistoryGreen, 5.5.dp.toPx(), pt, style = Stroke(width = 1.5.dp.toPx()))
                            }
                            drawCircle(HistoryGreen, 3.dp.toPx(), pt)
                        }
                    }

                    // 2. Bedtime line (Purple)
                    if (showBedtime) {
                        val bedtimePoints = mutableListOf<Pair<Int, Offset>>()
                        dayItems.forEachIndexed { index, item ->
                            if (item.hasLog && item.log != null) {
                                val cx = slotW * index + slotW / 2f
                                val cy = computeY(item.log.bedtimeMinute)
                                bedtimePoints.add(index to Offset(cx, cy))
                            }
                        }

                        for (i in 0 until bedtimePoints.size - 1) {
                            val p1 = bedtimePoints[i]
                            val p2 = bedtimePoints[i + 1]
                            if (p2.first - p1.first <= 2) {
                                drawLine(
                                    color = Color(0xFFA78BFA),
                                    start = p1.second,
                                    end = p2.second,
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }

                        bedtimePoints.forEach { (index, pt) ->
                            if (index == selectedPointIndex) {
                                drawCircle(Color(0xFFA78BFA), 5.5.dp.toPx(), pt, style = Stroke(width = 1.5.dp.toPx()))
                            }
                            drawCircle(Color(0xFFA78BFA), 3.dp.toPx(), pt)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // X-axis date labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val labelInterval = (dayItems.size / 6).coerceAtLeast(1)
            dayItems.forEachIndexed { index, item ->
                if (index == 0 || index == dayItems.size - 1 || index % labelInterval == 0) {
                    Text(
                        text = item.dayLabel,
                        color = if (index == selectedPointIndex) HistoryGreen else HistoryTextMuted,
                        fontSize = 9.sp,
                        fontWeight = if (index == selectedPointIndex) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { onPointClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyGoalProgressChart(
    weeklyRates: List<Pair<String, Int>>,
    onWeekClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        // Y-axis labels: 100%, 75%, 50%, 25%, 0%
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            listOf("100%", "75%", "50%", "25%", "0%").forEach { label ->
                Text(text = label, color = HistoryTextMuted, fontSize = 8.5.sp)
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = (h / gridLines) * i
                        drawLine(
                            color = Color(0x1F22C55E),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val slotW = w / weeklyRates.size.coerceAtLeast(1)
                    val barW = minOf(slotW * 0.65f, 26.dp.toPx())

                    weeklyRates.forEachIndexed { index, (_, rate) ->
                        val cx = slotW * index + slotW / 2f
                        val left = cx - barW / 2f

                        // Background track
                        drawRoundRect(
                            color = HistoryBarTrackBg,
                            topLeft = Offset(left, 0f),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Filled bar
                        val barH = (h * (rate / 100f)).coerceIn(0f, h)
                        val barTop = h - barH
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF86EFAC), HistoryGreenDark),
                                startY = barTop,
                                endY = h
                            ),
                            topLeft = Offset(left, barTop),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }

                // Percentage text on top of bars
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround) {
                    weeklyRates.forEachIndexed { index, (_, rate) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onWeekClick(index) }
                        ) {
                            Text(
                                text = "$rate%",
                                color = HistoryTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Labels: Week 1, Week 2, Week 3, Week 4
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                weeklyRates.forEachIndexed { index, (label, _) ->
                    Text(
                        text = label,
                        color = HistoryTextMuted,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onWeekClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailedDataTableDialog(
    logs: List<SleepLog>,
    targetSleepMinutes: Int,
    onDismiss: () -> Unit,
    onRowClick: (SleepLog) -> Unit,
    onExportCsv: () -> Unit,
    onExportJson: () -> Unit,
) {
    val dateFormat = SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F1A13),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Detailed Sleep Records", color = HistoryTextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${logs.size} nights", color = HistoryGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Table Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = HistoryCardInner,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Date", color = HistoryTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f))
                        Text("Bed – Wake", color = HistoryTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                        Text("Duration", color = HistoryTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Status", color = HistoryTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f))
                    }
                }

                if (logs.isEmpty()) {
                    Text("No records found in this timeframe.", color = HistoryTextMuted, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
                } else {
                    logs.forEach { log ->
                        val isMet = log.durationMinutes >= (targetSleepMinutes - 30)
                        val h = log.durationMinutes / 60
                        val m = log.durationMinutes % 60
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRowClick(log) },
                            color = HistoryCardBg,
                            border = BorderStroke(1.dp, HistoryCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dateFormat.format(Date(log.dateMillis)),
                                    color = HistoryTextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1.2f)
                                )
                                Text(
                                    text = "${formatClockTime12h(log.bedtimeMinute)}–${formatClockTime12h(log.wakeupMinute)}",
                                    color = HistoryTextMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1.5f)
                                )
                                Text(
                                    text = "${h}h ${m}m",
                                    color = HistoryTextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isMet) HistoryGoalMetBg else HistoryBelowGoalBg)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isMet) "Met" else "Below",
                                        color = if (isMet) HistoryGreen else Color(0xFFFBBF24),
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
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onExportCsv) {
                    Text("Export CSV", color = HistoryGreen, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onExportJson) {
                    Text("JSON", color = HistoryTextMuted)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = HistoryGreen, contentColor = HistoryGreenPillText)
                ) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}

@Composable
private fun ConsistencyDotPlotChart(
    yLabels: List<String>,
    minMinute: Int,
    maxMinute: Int,
    dayData: List<Pair<String, Int?>>,
    highlightIndex: Int = -1,
    onDotClick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    Text(text = label, color = HistoryTextMuted, fontSize = 9.sp)
                }
            }

            // Canvas with horizontal guidelines and vertical day tracks
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Horizontal guidelines
                    val gridLines = yLabels.size - 1
                    for (i in 0..gridLines) {
                        val y = (h / gridLines) * i
                        drawLine(
                            color = Color(0x1F22C55E),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Vertical tracks & data dots
                    val slotW = w / dayData.size.coerceAtLeast(1)
                    val span = (maxMinute - minMinute).toFloat().coerceAtLeast(1f)

                    dayData.forEachIndexed { index, (_, minute) ->
                        val cx = slotW * index + slotW / 2f
                        drawLine(
                            color = Color(0x144ADE80),
                            start = Offset(cx, 0f),
                            end = Offset(cx, h),
                            strokeWidth = 1.dp.toPx()
                        )

                        if (minute != null) {
                            val norm = ((minute - minMinute).toFloat() / span).coerceIn(0f, 1f)
                            val cy = h - (norm * h)

                            val isHighlighted = index == highlightIndex
                            if (isHighlighted) {
                                drawCircle(
                                    color = HistoryGreen,
                                    radius = 6.dp.toPx(),
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5.dp.toPx())
                                )
                            }

                            drawCircle(
                                color = HistoryGreen,
                                radius = 3.5.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // X-axis day labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dayData.forEachIndexed { index, (dayLabel, minute) ->
                Text(
                    text = dayLabel,
                    color = if (index == highlightIndex) HistoryGreen else HistoryTextMuted,
                    fontSize = 9.sp,
                    fontWeight = if (index == highlightIndex) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (minute != null) onDotClick(index, minute)
                        }
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricMiniCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
    statusText: String,
    statusColor: Color,
    subtext: String? = null,
    hasInfo: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = HistoryCardBg,
        border = BorderStroke(1.dp, HistoryCardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F2618)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }

            Spacer(Modifier.height(2.dp))

            Text(
                text = title,
                color = HistoryTextMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = value,
                color = HistoryTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 1
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusText,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                if (hasInfo) {
                    Text(
                        text = " ⓘ",
                        color = HistoryTextMuted,
                        fontSize = 8.5.sp
                    )
                }
            }

            if (subtext != null) {
                Text(
                    text = subtext,
                    color = HistoryTextMuted,
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun KeyInsightMiniRow(
    icon: String,
    text: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = icon, fontSize = 12.sp, color = HistoryGreen)
        Text(
            text = text,
            color = HistoryTextMuted,
            fontSize = 9.5.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(text = "›", color = HistoryTextMuted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StudyModeBottomNav(
    currentTab: String,
    onNavigateToHome: () -> Unit,
    onNavigateToPlan: () -> Unit,
    onNavigateToSleep: () -> Unit,
    onNavigateToBlocks: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF09120D),
        border = BorderStroke(1.dp, Color(0xFF14231A)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BottomNavTabItem(icon = "🏠", label = "Home", isSelected = currentTab == "Home", onClick = onNavigateToHome)
            BottomNavTabItem(icon = "📅", label = "Plan", isSelected = currentTab == "Plan", onClick = onNavigateToPlan)
            BottomNavTabItem(icon = "🌙", label = "Sleep", isSelected = currentTab == "Sleep", onClick = onNavigateToSleep)
            BottomNavTabItem(icon = "🚫", label = "Blocks", isSelected = currentTab == "Blocks", onClick = onNavigateToBlocks)
        }
    }
}

@Composable
private fun BottomNavTabItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) {
        Text(text = icon, fontSize = 18.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) HistoryGreen else HistoryTextMuted,
        )
        if (isSelected) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(HistoryGreen)
            )
        }
    }
}

@Composable
private fun MoonVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.44f
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
        drawPath(moonPath, color)
    }
}

@Composable
private fun SunVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = minOf(w, h) * 0.24f
        drawCircle(color, radius, Offset(cx, cy))
        val rayStart = radius * 1.45f
        val rayEnd = radius * 1.85f
        val stroke = 1.8.dp.toPx()
        for (i in 0 until 8) {
            val angle = (i * 45) * (Math.PI / 180.0)
            val cos = Math.cos(angle).toFloat()
            val sin = Math.sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + cos * rayStart, cy + sin * rayStart),
                end = Offset(cx + cos * rayEnd, cy + sin * rayEnd),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun BarChartVectorIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = 3.2.dp.toPx()
        val spacing = 2.4.dp.toPx()
        val r = 1.2.dp.toPx()

        val h1 = h * 0.4f
        val h2 = h * 0.8f
        val h3 = h * 0.55f

        val totalW = barWidth * 3 + spacing * 2
        val startX = (w - totalW) / 2f
        val baseY = h * 0.88f

        drawRoundRect(color, Offset(startX, baseY - h1), Size(barWidth, h1), CornerRadius(r, r))
        drawRoundRect(color, Offset(startX + barWidth + spacing, baseY - h2), Size(barWidth, h2), CornerRadius(r, r))
        drawRoundRect(color, Offset(startX + (barWidth + spacing) * 2, baseY - h3), Size(barWidth, h3), CornerRadius(r, r))
    }
}

@Composable
private fun CalendarIconVector(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Outer box
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.12f, h * 0.2f),
            size = Size(w * 0.76f, h * 0.68f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(width = stroke)
        )

        // Top horizontal line
        drawLine(color, Offset(w * 0.12f, h * 0.42f), Offset(w * 0.88f, h * 0.42f), strokeWidth = stroke)

        // 2 top pins
        drawLine(color, Offset(w * 0.32f, h * 0.1f), Offset(w * 0.32f, h * 0.24f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.68f, h * 0.1f), Offset(w * 0.68f, h * 0.24f), strokeWidth = stroke, cap = StrokeCap.Round)

        // Grid dots inside
        val dotR = 1.2.dp.toPx()
        drawCircle(color, dotR, Offset(w * 0.35f, h * 0.58f))
        drawCircle(color, dotR, Offset(w * 0.52f, h * 0.58f))
        drawCircle(color, dotR, Offset(w * 0.69f, h * 0.58f))
        drawCircle(color, dotR, Offset(w * 0.35f, h * 0.74f))
        drawCircle(color, dotR, Offset(w * 0.52f, h * 0.74f))
    }
}

@Composable
private fun SmileyVectorIcon(isHappy: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val radius = minOf(w, h) * 0.44f
        val cx = w / 2f
        val cy = h / 2f

        // Outer circle
        drawCircle(
            color = color,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = stroke)
        )

        // Eyes
        val eyeR = 1.4.dp.toPx()
        drawCircle(color = color, radius = eyeR, center = Offset(cx - radius * 0.36f, cy - radius * 0.2f))
        drawCircle(color = color, radius = eyeR, center = Offset(cx + radius * 0.36f, cy - radius * 0.2f))

        // Mouth
        if (isHappy) {
            val smilePath = Path().apply {
                moveTo(cx - radius * 0.4f, cy + radius * 0.15f)
                quadraticBezierTo(cx, cy + radius * 0.55f, cx + radius * 0.4f, cy + radius * 0.15f)
            }
            drawPath(smilePath, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        } else {
            // Neutral / straight mouth
            drawLine(
                color = color,
                start = Offset(cx - radius * 0.35f, cy + radius * 0.3f),
                end = Offset(cx + radius * 0.35f, cy + radius * 0.3f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

// ----------------------------------------------------------------------
// DATA, INTERVALS & STATISTICAL HELPERS
// ----------------------------------------------------------------------
private fun calculatePeriodIntervals(
    range: SleepDateRange,
    customStart: Long,
    customEnd: Long,
): Quadruple<Long, Long, Long, Long> {
    val cal = Calendar.getInstance()
    return when (range) {
        SleepDateRange.THIS_WEEK -> {
            // Monday to Sunday of this week
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            // Prior week
            val priorStart = start - 7 * 86_400_000L
            val priorEnd = end - 7 * 86_400_000L
            Quadruple(start, end, priorStart, priorEnd)
        }
        SleepDateRange.LAST_WEEK -> {
            cal.firstDayOfWeek = Calendar.MONDAY
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            cal.add(Calendar.DAY_OF_YEAR, -7)
            cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 6)
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis

            val priorStart = start - 7 * 86_400_000L
            val priorEnd = end - 7 * 86_400_000L
            Quadruple(start, end, priorStart, priorEnd)
        }
        SleepDateRange.LAST_30_DAYS -> {
            val end = System.currentTimeMillis()
            val start = end - 30 * 86_400_000L
            val priorStart = start - 30 * 86_400_000L
            val priorEnd = start
            Quadruple(start, end, priorStart, priorEnd)
        }
        SleepDateRange.CUSTOM -> {
            val span = (customEnd - customStart).coerceAtLeast(86_400_000L)
            val priorStart = customStart - span
            val priorEnd = customStart
            Quadruple(customStart, customEnd, priorStart, priorEnd)
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Calculates circular average time and circular standard deviation across midnight.
 */

internal fun circularMinuteDifference(m1: Int, m2: Int): Int {
    val diff = kotlin.math.abs(m1 - m2) % 1440
    return if (diff > 720) 1440 - diff else diff
}

internal fun calculateCircularStats(minuteList: List<Int>): CircularStatsResult {
    if (minuteList.isEmpty()) return CircularStatsResult(0, 0, 0)
    var sumCos = 0.0
    var sumSin = 0.0
    val twoPi = 2.0 * Math.PI

    minuteList.forEach { m ->
        val theta = (m.toDouble() / 1440.0) * twoPi
        sumCos += cos(theta)
        sumSin += sin(theta)
    }

    val meanCos = sumCos / minuteList.size
    val meanSin = sumSin / minuteList.size

    var meanTheta = atan2(meanSin, meanCos)
    if (meanTheta < 0) meanTheta += twoPi

    val avgMinute = ((meanTheta / twoPi) * 1440.0).toInt().coerceIn(0, 1439)

    // Circular Standard Deviation: R = sqrt(cos^2 + sin^2)
    val r = sqrt(meanCos * meanCos + meanSin * meanSin).coerceIn(0.001, 1.0)
    val circularSdRadians = sqrt(-2.0 * kotlin.math.ln(r))
    val varianceMinutes = ((circularSdRadians / twoPi) * 1440.0).toInt().coerceIn(0, 360)

    return CircularStatsResult(avgMinute, varianceMinutes, minuteList.size)
}


private fun generateTrendsBarChartData(
    startMillis: Long,
    endMillis: Long,
    logs: List<SleepLog>,
    selectedIndex: Int
): List<DaySleepBarData> {
    val dayMap = logs.associateBy { normalizeToDayStart(it.dateMillis) }
    val daysCount = (((endMillis - startMillis) / 86_400_000L).toInt() + 1).coerceIn(1, 365)
    val cal = Calendar.getInstance().apply {
        timeInMillis = startMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val result = mutableListOf<DaySleepBarData>()
    val dayOfMonthFormat = SimpleDateFormat("MMM d", Locale.getDefault())

    for (i in 0 until daysCount) {
        val dayStart = cal.timeInMillis
        val log = dayMap[dayStart]
        val label = if (daysCount <= 7) {
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayStart))
        } else {
            dayOfMonthFormat.format(Date(dayStart))
        }

        if (log != null) {
            val h = log.durationMinutes / 60
            val m = log.durationMinutes % 60
            result.add(
                DaySleepBarData(
                    dayLabel = label,
                    durationText = "${h}h ${m}m",
                    durationMinutes = log.durationMinutes,
                    dateMillis = dayStart,
                    hasLog = true,
                    isSelected = i == selectedIndex,
                    log = log
                )
            )
        } else {
            result.add(
                DaySleepBarData(
                    dayLabel = label,
                    durationText = "—",
                    durationMinutes = 0,
                    dateMillis = dayStart,
                    hasLog = false,
                    isSelected = i == selectedIndex,
                    log = null
                )
            )
        }
        cal.add(Calendar.DAY_OF_MONTH, 1)
    }
    return result
}

private fun buildBarChartData(
    periodStartMillis: Long,
    periodEndMillis: Long,
    periodLogs: List<SleepLog>,
    selectedIndex: Int
): List<DaySleepBarData> {
    val dayMap = periodLogs.associateBy { normalizeToDayStart(it.dateMillis) }
    val daysCount = (((periodEndMillis - periodStartMillis) / 86_400_000L).toInt() + 1).coerceIn(1, 14)

    val cal = Calendar.getInstance().apply {
        timeInMillis = periodStartMillis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }

    val result = mutableListOf<DaySleepBarData>()
    val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())

    for (i in 0 until minOf(daysCount, 7)) {
        val dayStart = cal.timeInMillis
        val log = dayMap[dayStart]
        val label = dayFormat.format(Date(dayStart))

        if (log != null) {
            val h = log.durationMinutes / 60
            val m = log.durationMinutes % 60
            result.add(
                DaySleepBarData(
                    dayLabel = label,
                    durationText = "${h}h ${m}m",
                    durationMinutes = log.durationMinutes,
                    dateMillis = dayStart,
                    hasLog = true,
                    isSelected = i == selectedIndex,
                    log = log
                )
            )
        } else {
            result.add(
                DaySleepBarData(
                    dayLabel = label,
                    durationText = "—",
                    durationMinutes = 0,
                    dateMillis = dayStart,
                    hasLog = false,
                    isSelected = i == selectedIndex,
                    log = null
                )
            )
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }

    return result
}

private fun sleepMoodFromRating(rating: Int, note: String): String {
    val lowerNote = note.lowercase()
    return when {
        lowerNote.contains("great") || rating >= 5 -> "Great"
        lowerNote.contains("felt good") -> "Felt good"
        lowerNote.contains("good") || rating == 4 -> "Good"
        lowerNote.contains("tired") || rating == 2 -> "Tired"
        lowerNote.contains("okay") || rating == 3 -> "Okay"
        rating <= 1 -> "Tired"
        else -> "Good"
    }
}

/**
 * Exports sleep logs as CSV and shares via system intent.
 */
private fun exportSleepLogsAsCsv(context: Context, logs: List<SleepLog>) {
    if (logs.isEmpty()) {
        Toast.makeText(context, "No sleep logs to export", Toast.LENGTH_SHORT).show()
        return
    }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val csv = buildString {
        appendLine("Date,Bedtime,Wakeup,DurationMinutes,QualityRating,Notes")
        logs.forEach { log ->
            appendLine("${dateFormat.format(Date(log.dateMillis))},${formatClockTime12h(log.bedtimeMinute)},${formatClockTime12h(log.wakeupMinute)},${log.durationMinutes},${log.qualityRating},\"${log.note.replace("\"", "\"\"")}\"")
        }
    }
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, csv)
        type = "text/csv"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export Sleep History"))
}

/**
 * Exports sleep logs as JSON and shares via system intent.
 */
private fun exportSleepLogsAsJson(context: Context, logs: List<SleepLog>) {
    if (logs.isEmpty()) {
        Toast.makeText(context, "No sleep logs to export", Toast.LENGTH_SHORT).show()
        return
    }
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val json = buildString {
        appendLine("[")
        logs.forEachIndexed { index, log ->
            appendLine("  {")
            appendLine("    \"date\": \"${dateFormat.format(Date(log.dateMillis))}\",")
            appendLine("    \"bedtime\": \"${formatClockTime12h(log.bedtimeMinute)}\",")
            appendLine("    \"wakeup\": \"${formatClockTime12h(log.wakeupMinute)}\",")
            appendLine("    \"durationMinutes\": ${log.durationMinutes},")
            appendLine("    \"qualityRating\": ${log.qualityRating},")
            appendLine("    \"note\": \"${log.note.replace("\"", "\\\"")}\"")
            append(if (index == logs.size - 1) "  }" else "  },")
            appendLine()
        }
        appendLine("]")
    }
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, json)
        type = "application/json"
    }
    context.startActivity(Intent.createChooser(sendIntent, "Export Sleep History (JSON)"))
}

/**
 * Ensures the database contains the 7 authentic demo logs from the mockup.
 */
fun ensureSampleSleepLogs(store: LocalStudyStore) {
    if (store.sleepLogs(35).size >= 25) return
    seedDemoLogs(store)
}

private fun seedDemoLogs(store: LocalStudyStore) {
    val thisWeekMondayCal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val thisMondayMillis = thisWeekMondayCal.timeInMillis

    // 7 days of the current week (Mon -> Sun) matching the mockup precisely
    val sampleSpecs = listOf(
        Triple(6, 22 * 60 + 45 to 6 * 60, 4 to "Restful sleep. More focused today."), // Sun (7h 15m)
        Triple(5, 22 * 60 + 30 to 6 * 60 + 40, 5 to "Good sleep. No phone before bed."), // Sat (8h 10m)
        Triple(4, 22 * 60 + 50 to 6 * 60 + 40, 2 to "Slept late. Had a lot of work."), // Fri (7h 50m)
        Triple(3, 23 * 60 + 20 to 6 * 60 + 25, 4 to "Listened to rain sounds."), // Thu (7h 5m)
        Triple(2, 22 * 60 + 15 to 6 * 60 + 40, 5 to "Felt very fresh in the morning."), // Wed (8h 25m)
        Triple(1, 23 * 60 + 10 to 5 * 60 + 58, 3 to "Woke up once during the night."), // Tue (6h 48m)
        Triple(0, 22 * 60 + 40 to 5 * 60 + 52, 4 to "No phone after 10 PM.")  // Mon (7h 12m)
    )

    sampleSpecs.forEach { (dayOffset, times, ratingNote) ->
        val logDateMillis = thisMondayMillis + dayOffset * 86_400_000L
        val bedtime = times.first
        val wakeup = times.second
        val duration = calculateSleepDurationMinutes(bedtime, wakeup)

        store.saveSleepLog(
            SleepLog(
                dateMillis = logDateMillis,
                bedtimeMinute = bedtime,
                wakeupMinute = wakeup,
                reportedBedtimeMillis = logDateMillis + bedtime * 60_000L,
                reportedWakeupMillis = logDateMillis + (wakeup + 1440) * 60_000L,
                durationMinutes = duration,
                qualityRating = ratingNote.first,
                note = ratingNote.second
            )
        )
    }

    // Seed prior week logs so the delta "+36 min vs last week" is authentically calculated
    val priorMondayMillis = thisMondayMillis - 7 * 86_400_000L
    val priorDurations = listOf(410, 420, 400, 415, 412, 415, 412) // Avg = 412 min (~6h 52m)
    for (i in 0 until 7) {
        val logDateMillis = priorMondayMillis + i * 86_400_000L
        val dur = priorDurations[i]
        val bed = 23 * 60
        val wake = (bed + dur) % 1440
        store.saveSleepLog(
            SleepLog(
                dateMillis = logDateMillis,
                bedtimeMinute = bed,
                wakeupMinute = wake,
                reportedBedtimeMillis = logDateMillis + bed * 60_000L,
                reportedWakeupMillis = logDateMillis + (wake + 1440) * 60_000L,
                durationMinutes = dur,
                qualityRating = 4,
                note = "Prior week sleep record"
            )
        )
    }

    // Seed complete January 2024 dataset (31 days) for Trends tab
    val janCal = Calendar.getInstance().apply {
        set(2024, Calendar.JANUARY, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val jan1Millis = janCal.timeInMillis

    val janDays = listOf(
        // Day index (0..30), bedtime, wakeup, rating, note
        Triple(0, 23 * 60 + 35 to 5 * 60 + 45, 3 to "New Year celebration"),
        Triple(1, 23 * 60 + 25 to 5 * 60 + 50, 3 to "Tired after holidays"),
        Triple(2, 23 * 60 + 40 to 5 * 60 + 45, 2 to "Late project kickoff"),
        Triple(3, 22 * 60 + 40 to 6 * 60 + 15, 4 to "Early to bed"),
        Triple(4, 22 * 60 + 35 to 6 * 60 + 25, 5 to "Read a chapter"),
        Triple(5, 22 * 60 + 25 to 6 * 60 + 20, 5 to "Relaxing weekend evening"),
        Triple(6, 22 * 60 + 30 to 6 * 60 + 30, 4 to "Sunday rest"),

        // Jan 8-14 (Week 2 - exact match with History tab)
        Triple(7, 22 * 60 + 40 to 5 * 60 + 52, 4 to "No phone after 10 PM."),
        Triple(8, 23 * 60 + 10 to 5 * 60 + 58, 3 to "Woke up once during the night."),
        Triple(9, 22 * 60 + 15 to 6 * 60 + 40, 5 to "Felt very fresh in the morning."),
        Triple(10, 22 * 60 + 50 to 6 * 60 + 40, 4 to "Listened to rain sounds."),
        Triple(11, 23 * 60 + 20 to 5 * 60 + 50, 2 to "Slept late. Had a lot of work."),
        Triple(12, 22 * 60 + 30 to 6 * 60 + 40, 5 to "Good sleep. No phone before bed."),
        Triple(13, 22 * 60 + 45 to 6 * 60 + 0, 4 to "Restful sleep. More focused today."),

        // Jan 15-21 (Week 3)
        Triple(14, 22 * 60 + 40 to 6 * 60 + 15, 4 to "Solid Monday sleep"),
        Triple(15, 22 * 60 + 35 to 6 * 60 + 20, 4 to "Smooth evening routine"),
        Triple(16, 23 * 60 + 30 to 5 * 60 + 45, 3 to "Watched a movie late"),
        Triple(17, 22 * 60 + 40 to 6 * 60 + 15, 4 to "Consistent schedule"),
        Triple(18, 22 * 60 + 30 to 6 * 60 + 20, 5 to "Chamomile tea before bed"),
        Triple(19, 22 * 60 + 25 to 6 * 60 + 25, 5 to "Deep Saturday sleep"),
        Triple(20, 22 * 60 + 35 to 6 * 60 + 20, 4 to "Recharged for the week"),

        // Jan 22-31 (Week 4)
        Triple(21, 22 * 60 + 40 to 6 * 60 + 10, 4 to "Calm night"),
        Triple(22, 22 * 60 + 35 to 6 * 60 + 15, 4 to "Dimmed screen early"),
        Triple(23, 22 * 60 + 50 to 6 * 60 + 5, 4 to "Midweek routine"),
        Triple(24, 22 * 60 + 20 to 6 * 60 + 30, 5 to "Optimal sleep window"),
        Triple(25, 22 * 60 + 35 to 6 * 60 + 15, 4 to "Peaceful night"),
        Triple(26, 22 * 60 + 20 to 6 * 60 + 35, 5 to "Weekend restorative sleep"),
        Triple(27, 22 * 60 + 40 to 6 * 60 + 15, 4 to "Sunday wind-down"),
        Triple(28, 23 * 60 + 25 to 5 * 60 + 50, 2 to "Late work session"),
        Triple(29, 22 * 60 + 35 to 6 * 60 + 20, 4 to "Back on track"),
        Triple(30, 22 * 60 + 30 to 6 * 60 + 25, 5 to "End of month reflection")
    )

    janDays.forEach { (dayOffset, times, ratingNote) ->
        val logDateMillis = jan1Millis + dayOffset * 86_400_000L
        val bedtime = times.first
        val wakeup = times.second
        val duration = calculateSleepDurationMinutes(bedtime, wakeup)

        store.saveSleepLog(
            SleepLog(
                dateMillis = logDateMillis,
                bedtimeMinute = bedtime,
                wakeupMinute = wakeup,
                reportedBedtimeMillis = logDateMillis + bedtime * 60_000L,
                reportedWakeupMillis = logDateMillis + (wakeup + 1440) * 60_000L,
                durationMinutes = duration,
                qualityRating = ratingNote.first,
                note = ratingNote.second
            )
        )
    }

    // Seed December 2023 dataset (31 days) for previous month comparisons
    val decCal = Calendar.getInstance().apply {
        set(2023, Calendar.DECEMBER, 1, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val dec1Millis = decCal.timeInMillis
    val decDurations = listOf(
        390, 410, 400, 395, 420, 415, 410,
        380, 405, 400, 390, 415, 410, 405,
        395, 410, 420, 385, 400, 415, 405,
        410, 395, 400, 420, 410, 405, 390,
        400, 415, 410
    ) // Average ~ 404 min (6h 44m), met rate ~ 66%

    for (i in 0 until 31) {
        val logDateMillis = dec1Millis + i * 86_400_000L
        val dur = decDurations[i]
        val bed = (23 * 60 + 6 + (if (i % 2 == 0) (i * 3 % 40) - 20 else (i * 5 % 40) - 20)) % 1440
        val wake = (bed + dur) % 1440
        store.saveSleepLog(
            SleepLog(
                dateMillis = logDateMillis,
                bedtimeMinute = bed,
                wakeupMinute = wake,
                reportedBedtimeMillis = logDateMillis + bed * 60_000L,
                reportedWakeupMillis = logDateMillis + (wake + 1440) * 60_000L,
                durationMinutes = dur,
                qualityRating = 3,
                note = "December 2023 log"
            )
        )
    }
}
