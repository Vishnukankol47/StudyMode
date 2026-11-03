package com.studymode.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

// ----------------------------------------------------------------------
// COLOR PALETTE TOKENS
// ----------------------------------------------------------------------
private val InsightsDarkBg = Color(0xFF07120D)
private val InsightsCardBg = Color(0xFF0E1A14)
private val InsightsCardBorder = Color(0xFF1B2E24)
private val InsightsCardInner = Color(0xFF13231B)
private val InsightsGreen = Color(0xFF4ADE80)
private val InsightsGreenDark = Color(0xFF22C55E)
private val InsightsGreenPillText = Color(0xFF0A2E18)
private val InsightsTextWhite = Color(0xFFF3F4F6)
private val InsightsTextMuted = Color(0xFF8E9993)
private val InsightsBarTrackBg = Color(0xFF132219)
private val InsightsGold = Color(0xFFFBBF24)
private val InsightsOrange = Color(0xFFF97316)
private val InsightsRed = Color(0xFFEF4444)

// ----------------------------------------------------------------------
// ENUMS & MODELS
// ----------------------------------------------------------------------
enum class InsightDetailScreen {
    NONE,
    WEEKLY_IMPROVEMENT,
    BEST_FOCUS_TIME,
}

enum class ComparisonAggregation(val label: String) {
    WEEKS("Weeks"),
    MONTHS("Months"),
}

data class TimeRangeSlot(
    val label: String,
    val startHour: Int,
    val endHour: Int, // inclusive
    val durationMinutes: Int,
    val formattedDuration: String,
    val sessionCount: Int,
    val percentageOfTotal: Int,
    val isPeak: Boolean = false,
)

data class WeeklyImprovementData(
    val currentPeriodMinutes: Int,
    val formattedCurrentTime: String,
    val previousPeriodMinutes: Int,
    val formattedPreviousTime: String,
    val deltaMinutes: Int,
    val formattedDeltaTime: String,
    val percentChange: Int,
    val isIncrease: Boolean,
    val currentCompletedCount: Int,
    val previousCompletedCount: Int,
    val currentPeriodLabel: String,
    val previousPeriodLabel: String,
    val summaryTitle: String,
    val summarySubtitle: String,
    val badgeText: String,
)

data class BestFocusTimeData(
    val peakPeriodName: String, // "morning", "afternoon", "evening"
    val peakTimeRangeLabel: String, // "8 AM – 11 AM"
    val peakStartHour: Int,
    val peakEndHour: Int,
    val peakPercentage: Int, // 45%
    val peakDurationMinutes: Int,
    val formattedPeakDuration: String,
    val summaryTitle: String,
    val summarySubtitle: String,
    val tipText: String,
    val timeRanges: List<TimeRangeSlot>,
    val hourlyBuckets: List<HourlyBucketData>,
)

// ----------------------------------------------------------------------
// CALCULATION LOGIC
// ----------------------------------------------------------------------
fun calculateWeeklyImprovement(
    currentSessions: List<FocusSessionRecord>,
    prevSessions: List<FocusSessionRecord>,
    currentPeriodLabel: String = "This Week",
    previousPeriodLabel: String = "Last Week"
): WeeklyImprovementData {
    val currentMins = (currentSessions.sumOf { it.elapsedSeconds } / 60L).toInt()
    val prevMins = (prevSessions.sumOf { it.elapsedSeconds } / 60L).toInt()
    val deltaMins = currentMins - prevMins
    val isIncrease = deltaMins >= 0
    val absDeltaMins = kotlin.math.abs(deltaMins)

    val currentCompleted = currentSessions.count { it.completed }
    val prevCompleted = prevSessions.count { it.completed }

    val percentChange = if (prevMins > 0) {
        ((deltaMins.toDouble() / prevMins.toDouble()) * 100).toInt()
    } else if (currentMins > 0) {
        100
    } else {
        0
    }

    val absPercent = kotlin.math.abs(percentChange)
    val badgeText = if (percentChange >= 0) "+$percentChange%" else "$percentChange%"

    val summaryTitle = when {
        isIncrease && absDeltaMins > 0 -> "You focused ${formatFocusDuration(absDeltaMins)} more this week"
        !isIncrease && absDeltaMins > 0 -> "You focused ${formatFocusDuration(absDeltaMins)} less this week"
        currentMins > 0 -> "You maintained ${formatFocusDuration(currentMins)} of focus this week"
        else -> "No focus recorded this week"
    }

    val summarySubtitle = when {
        prevMins > 0 && isIncrease -> "That's a $absPercent% increase compared to last week."
        prevMins > 0 && !isIncrease -> "That's a $absPercent% decrease compared to last week."
        currentMins > 0 -> "Great start! Keep tracking your study sessions to build momentum."
        else -> "Start a focus session to begin tracking your weekly growth."
    }

    return WeeklyImprovementData(
        currentPeriodMinutes = currentMins,
        formattedCurrentTime = formatFocusDuration(currentMins),
        previousPeriodMinutes = prevMins,
        formattedPreviousTime = formatFocusDuration(prevMins),
        deltaMinutes = deltaMins,
        formattedDeltaTime = formatFocusDuration(absDeltaMins),
        percentChange = percentChange,
        isIncrease = isIncrease,
        currentCompletedCount = currentCompleted,
        previousCompletedCount = prevCompleted,
        currentPeriodLabel = currentPeriodLabel,
        previousPeriodLabel = previousPeriodLabel,
        summaryTitle = summaryTitle,
        summarySubtitle = summarySubtitle,
        badgeText = badgeText
    )
}

fun calculateBestFocusTimeInsight(
    sessions: List<FocusSessionRecord>,
    period: BestTimesPeriod = BestTimesPeriod.WEEK,
    selectedHour: Int = 9
): BestFocusTimeData {
    val cal = Calendar.getInstance()
    val durationByHour = IntArray(24)
    val countByHour = IntArray(24)

    sessions.forEach { s ->
        cal.timeInMillis = s.startedAtMillis
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val mins = (s.elapsedSeconds / 60L).toInt()
        if (h in 0..23) {
            durationByHour[h] += mins
            countByHour[h] += 1
        }
    }

    val totalMins = durationByHour.sum().coerceAtLeast(1)

    // Standard time slots:
    // 6 AM–8 AM (hours 6..7)
    // 8 AM–11 AM (hours 8..10)
    // 11 AM–2 PM (hours 11..13)
    // 2 PM–5 PM (hours 14..16)
    // 5 PM–8 PM (hours 17..19)
    // 8 PM–11 PM (hours 20..22)
    // Overnight: 11 PM–6 AM (hours 23, 0..5)
    val slotDefinitions = listOf(
        Triple("6 AM – 8 AM", 6, 7),
        Triple("8 AM – 11 AM", 8, 10),
        Triple("11 AM – 2 PM", 11, 13),
        Triple("2 PM – 5 PM", 14, 16),
        Triple("5 PM – 8 PM", 17, 19),
        Triple("8 PM – 11 PM", 20, 22),
        Triple("11 PM – 6 AM", 23, 5)
    )

    // Calculate slots
    val rawSlots = slotDefinitions.map { (label, startH, endH) ->
        val mins = if (startH <= endH) {
            (startH..endH).sumOf { durationByHour[it] }
        } else {
            // crosses midnight (23 to 5)
            durationByHour[23] + (0..5).sumOf { durationByHour[it] }
        }
        val cnt = if (startH <= endH) {
            (startH..endH).sumOf { countByHour[it] }
        } else {
            countByHour[23] + (0..5).sumOf { countByHour[it] }
        }
        val pct = ((mins.toDouble() / totalMins.toDouble()) * 100).toInt()
        TimeRangeSlot(
            label = label,
            startHour = startH,
            endHour = endH,
            durationMinutes = mins,
            formattedDuration = formatFocusDuration(mins),
            sessionCount = cnt,
            percentageOfTotal = pct
        )
    }

    // Find peak slot among daytime slots
    val daytimeSlots = rawSlots.take(6)
    val peakSlot = daytimeSlots.maxByOrNull { it.durationMinutes } ?: rawSlots[1]
    val markedSlots = rawSlots.map { it.copy(isPeak = it.label == peakSlot.label) }

    val peakPeriodName = when (peakSlot.startHour) {
        in 6..10 -> "morning"
        in 11..16 -> "afternoon"
        else -> "evening"
    }

    val summaryTitle = if (sessions.isEmpty()) {
        "No focus data yet"
    } else {
        "Your most productive time is in the $peakPeriodName"
    }

    val summarySubtitle = if (sessions.isEmpty()) {
        "Start a session to discover your peak focus window."
    } else {
        "You complete ${peakSlot.percentageOfTotal}% of your focus time between ${peakSlot.label}."
    }

    val tipText = "Try scheduling your most important study sessions between ${peakSlot.label} when your focus is naturally higher."

    // Hourly buckets for chart (6 AM to 10 PM)
    val (buckets, _) = calculateHourlyPatterns(sessions, period, selectedHour)

    return BestFocusTimeData(
        peakPeriodName = peakPeriodName,
        peakTimeRangeLabel = peakSlot.label,
        peakStartHour = peakSlot.startHour,
        peakEndHour = peakSlot.endHour,
        peakPercentage = peakSlot.percentageOfTotal,
        peakDurationMinutes = peakSlot.durationMinutes,
        formattedPeakDuration = peakSlot.formattedDuration,
        summaryTitle = summaryTitle,
        summarySubtitle = summarySubtitle,
        tipText = tipText,
        timeRanges = markedSlots,
        hourlyBuckets = buckets
    )
}

// ----------------------------------------------------------------------
// SCREEN 1: INSIGHTS DASHBOARD CONTENT
// ----------------------------------------------------------------------
@Composable
fun InsightsDashboardContent(
    sessions: List<FocusSessionRecord>,
    prevSessions: List<FocusSessionRecord>,
    goalCompletionPercent: Int,
    daysMetGoalCount: Int,
    trackedDaysCount: Int,
    avgSessionMinutes: Int,
    currentStreak: Int,
    longestStreak: Int,
    onOpenWeeklyImprovement: () -> Unit,
    onOpenBestFocusTime: () -> Unit,
    onOpenGoalProgress: () -> Unit,
    onOpenSessionLength: () -> Unit,
    onOpenStreakHistory: () -> Unit,
    onOpenDayOfWeek: () -> Unit,
) {
    val weeklyImprovement = remember(sessions, prevSessions) {
        calculateWeeklyImprovement(sessions, prevSessions)
    }

    val bestFocusTime = remember(sessions) {
        calculateBestFocusTimeInsight(sessions)
    }

    val (_, weekdaySummary) = remember(sessions) {
        calculateWeekdayPatterns(sessions)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {

        // Section Title: Your Insights
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PatternsLightbulbIcon(color = InsightsGreen, modifier = Modifier.size(17.dp))
            Column {
                Text(
                    text = "Your Insights",
                    color = InsightsTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
                Text(
                    text = "Personalized observations based on your focus data",
                    color = InsightsTextMuted,
                    fontSize = 11.5.sp
                )
            }
        }

        if (sessions.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = InsightsCardBg,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsLightbulbIcon(color = InsightsGreen, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        text = "More data needed",
                        color = InsightsTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Complete your daily study sessions to unlock personalized weekly improvements, peak focus hours, and habit consistency.",
                        color = InsightsTextMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // CARD 1: Weekly Focus Improvement
            InsightNavCard(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsTrendUpIcon(color = InsightsGreen, modifier = Modifier.size(17.dp))
                    }
                },
                title = weeklyImprovement.summaryTitle,
                subtitle = weeklyImprovement.summarySubtitle,
                badge = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2622C55E))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = weeklyImprovement.badgeText,
                            color = InsightsGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                onClick = onOpenWeeklyImprovement
            )

            // CARD 2: Most Productive Time
            InsightNavCard(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsSunIcon(color = InsightsGold, modifier = Modifier.size(18.dp))
                    }
                },
                title = bestFocusTime.summaryTitle,
                subtitle = bestFocusTime.summarySubtitle,
                onClick = onOpenBestFocusTime
            )

            // CARD 3: Daily Goal Achievement
            val goalSub = if (trackedDaysCount > 0) {
                "You met your daily goal on $daysMetGoalCount of $trackedDaysCount days"
            } else {
                "Daily goal consistency tracking"
            }
            InsightNavCard(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsCalendarIcon(color = InsightsGreen, modifier = Modifier.size(16.dp))
                    }
                },
                title = goalSub,
                subtitle = if (goalCompletionPercent >= 70) "Great consistency! Keep going to build the habit." else "Keep pushing to hit your daily goal regularly.",
                badge = {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 2.5.dp.toPx()
                            val r = (size.minDimension - stroke) / 2f
                            drawCircle(
                                color = Color(0x2E1B2E24),
                                radius = r,
                                style = Stroke(stroke)
                            )
                            drawArc(
                                color = InsightsGreen,
                                startAngle = -90f,
                                sweepAngle = 360f * (goalCompletionPercent / 100f).coerceIn(0f, 1f),
                                useCenter = false,
                                style = Stroke(stroke, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "$goalCompletionPercent%",
                            color = InsightsTextWhite,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                onClick = onOpenGoalProgress
            )

            // CARD 4: Average Session Length
            InsightNavCard(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsClockIcon(color = InsightsGreen, modifier = Modifier.size(16.dp))
                    }
                },
                title = "Your average session length is $avgSessionMinutes minutes",
                subtitle = "This is a good balance for consistent focus and productivity.",
                onClick = onOpenSessionLength
            )

            // CARD 5: Focus Streak
            InsightNavCard(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x26F97316)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔥", fontSize = 16.sp)
                    }
                },
                title = "You're on a $currentStreak-day streak",
                subtitle = "Your longest streak is $longestStreak days. Keep it up!",
                onClick = onOpenStreakHistory
            )

            // CARD 6: Most Productive Weekday
            val weekdaySub = if (weekdaySummary.peakDayPercentVsAvg > 0) {
                "You focus ${weekdaySummary.peakDayPercentVsAvg}% more on ${weekdaySummary.peakDayName}s compared to your weekly average."
            } else {
                "You focus most on ${weekdaySummary.peakDayName}s."
            }
            InsightNavCard(
                icon = {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsBarChartIcon(color = InsightsGreen, modifier = Modifier.size(16.dp))
                    }
                },
                title = "${weekdaySummary.peakDayName} is your most productive day",
                subtitle = weekdaySub,
                onClick = onOpenDayOfWeek
            )
        }
    }
}

/**
 * Clickable Insight Card for the Dashboard
 */
@Composable
private fun InsightNavCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = InsightsCardBg,
        border = BorderStroke(1.dp, InsightsCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = InsightsTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
                Text(
                    text = subtitle,
                    color = InsightsTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }

            if (badge != null) {
                badge()
            }

            Text("›", color = InsightsTextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------------------------
// SCREEN 2: WEEKLY FOCUS IMPROVEMENT DETAILS
// ----------------------------------------------------------------------
@Composable
fun WeeklyImprovementDetailScreen(
    currentSessions: List<FocusSessionRecord>,
    prevSessions: List<FocusSessionRecord>,
    onBack: () -> Unit,
    onViewThisWeekSessions: () -> Unit,
    onViewLastWeekSessions: () -> Unit,
    onOpenDailyBreakdown: () -> Unit = {},
    onOpenSessionDuration: () -> Unit = {},
) {
    var aggregation by rememberSaveable { mutableStateOf(ComparisonAggregation.WEEKS) }
    var showAggregationMenu by rememberSaveable { mutableStateOf(false) }
    var selectedBarIndex by rememberSaveable { mutableIntStateOf(1) } // 0 = Prev, 1 = Current

    val data = remember(currentSessions, prevSessions) {
        calculateWeeklyImprovement(currentSessions, prevSessions)
    }

    Scaffold(
        containerColor = InsightsDarkBg,
        topBar = {
            InsightsDetailHeader(
                title = "Insight Details",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero / Summary card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = InsightsCardBg,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x1F22C55E)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsTrendUpIcon(color = InsightsGreen, modifier = Modifier.size(19.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = data.summaryTitle,
                            color = InsightsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = data.summarySubtitle,
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2622C55E))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = data.badgeText,
                            color = InsightsGreen,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Weekly Comparison Chart Container
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = InsightsCardBg,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header row with aggregation dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Weekly Comparison",
                            color = InsightsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )

                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(InsightsCardInner)
                                    .border(1.dp, InsightsCardBorder, RoundedCornerShape(8.dp))
                                    .clickable { showAggregationMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = aggregation.label,
                                        color = InsightsTextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text("▼", color = InsightsTextMuted, fontSize = 7.sp)
                                }
                            }

                            DropdownMenu(
                                expanded = showAggregationMenu,
                                onDismissRequest = { showAggregationMenu = false }
                            ) {
                                ComparisonAggregation.values().forEach { agg ->
                                    DropdownMenuItem(
                                        text = { Text(agg.label) },
                                        onClick = {
                                            aggregation = agg
                                            showAggregationMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Two-Bar Comparison Chart
                    TwoBarComparisonChart(
                        prevDurationMinutes = data.previousPeriodMinutes,
                        prevLabel = "Jan 1 – Jan 7\n(Last Week)",
                        currentDurationMinutes = data.currentPeriodMinutes,
                        currentLabel = "Jan 8 – Jan 14\n(This Week)",
                        selectedBarIndex = selectedBarIndex,
                        onSelectBar = { idx -> selectedBarIndex = idx },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                    )
                }
            }

            // Four Summary Cards in 2x2 grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsClockIcon(color = InsightsGreen, modifier = Modifier.size(16.dp)) },
                    value = data.formattedCurrentTime,
                    label = "This week",
                    onClick = onOpenDailyBreakdown
                )
                val deltaSign = if (data.deltaMinutes >= 0) "+" else "-"
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsTrendUpIcon(color = InsightsGreen, modifier = Modifier.size(16.dp)) },
                    value = "$deltaSign${data.formattedDeltaTime}",
                    label = if (data.isIncrease) "Increase" else "Decrease",
                    onClick = onOpenDailyBreakdown
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsCalendarIcon(color = InsightsGreen, modifier = Modifier.size(16.dp)) },
                    value = data.currentCompletedCount.toString(),
                    label = "Sessions this week",
                    onClick = onViewThisWeekSessions
                )
                InsightStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { Text("%", color = InsightsGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
                    value = "${kotlin.math.abs(data.percentChange)}%",
                    label = if (data.isIncrease) "Growth" else "Decline",
                    onClick = onOpenSessionDuration
                )
            }

            // Motivation Message Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = InsightsCardInner,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsTrophyIcon(color = InsightsGold, modifier = Modifier.size(18.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = if (data.isIncrease) "Great progress!" else "Keep building momentum!",
                            color = InsightsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        val motivDesc = if (data.isIncrease) {
                            "You focused ${data.formattedDeltaTime} more this week compared to last week. Maintain this momentum for even better results."
                        } else {
                            "Small consistent study blocks add up over time. Schedule your focus hours early to stay on track."
                        }
                        Text(
                            text = motivDesc,
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Related Actions Section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Related Actions",
                    color = InsightsTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onViewThisWeekSessions() },
                        shape = RoundedCornerShape(12.dp),
                        color = InsightsCardBg,
                        border = BorderStroke(1.dp, InsightsCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PatternsBarChartIcon(color = InsightsGreen, modifier = Modifier.size(18.dp))
                            Text(
                                text = "View This Week\nSessions",
                                color = InsightsTextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onViewLastWeekSessions() },
                        shape = RoundedCornerShape(12.dp),
                        color = InsightsCardBg,
                        border = BorderStroke(1.dp, InsightsCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PatternsCalendarIcon(color = InsightsGreen, modifier = Modifier.size(18.dp))
                            Text(
                                text = "View Last Week\nSessions",
                                color = InsightsTextWhite,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Two-Bar Comparison Chart for Weekly Improvement Detail
 */
@Composable
private fun TwoBarComparisonChart(
    prevDurationMinutes: Int,
    prevLabel: String,
    currentDurationMinutes: Int,
    currentLabel: String,
    selectedBarIndex: Int,
    onSelectBar: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val maxMins = 360f // 6h scale
    val stepMins = 120f // 2h step

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val labelAreaW = 26.dp.toPx()
                val chartLeft = labelAreaW
                val chartW = w - chartLeft
                val chartBottom = h - 6.dp.toPx()
                val chartTop = 18.dp.toPx()
                val chartH = chartBottom - chartTop

                // Grid lines (0h, 2h, 4h, 6h)
                for (step in 0..3) {
                    val stepVal = step * stepMins
                    val y = chartBottom - (stepVal / maxMins) * chartH
                    drawLine(
                        color = Color(0x2E1B2E24),
                        start = Offset(chartLeft, y),
                        end = Offset(w, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                val barWidth = 84.dp.toPx()

                // Bar 0: Previous period
                val bar0CenterX = chartLeft + chartW * 0.32f
                val bar0Left = bar0CenterX - barWidth / 2f
                val bar0Ratio = (prevDurationMinutes.toFloat() / maxMins).coerceIn(0f, 1f)
                val bar0Height = (chartH * bar0Ratio).coerceAtLeast(6.dp.toPx())
                val bar0Top = chartBottom - bar0Height

                drawRoundRect(
                    color = Color(0xFF1E3A2B),
                    topLeft = Offset(bar0Left, bar0Top),
                    size = Size(barWidth, bar0Height),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                if (selectedBarIndex == 0) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(bar0Left - 1.dp.toPx(), bar0Top - 1.dp.toPx()),
                        size = Size(barWidth + 2.dp.toPx(), bar0Height + 2.dp.toPx()),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }

                // Bar 1: Current period (taller, bright green)
                val bar1CenterX = chartLeft + chartW * 0.72f
                val bar1Left = bar1CenterX - barWidth / 2f
                val bar1Ratio = (currentDurationMinutes.toFloat() / maxMins).coerceIn(0f, 1f)
                val bar1Height = (chartH * bar1Ratio).coerceAtLeast(6.dp.toPx())
                val bar1Top = chartBottom - bar1Height

                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF86EFAC), Color(0xFF22C55E)),
                        startY = bar1Top,
                        endY = chartBottom
                    ),
                    topLeft = Offset(bar1Left, bar1Top),
                    size = Size(barWidth, bar1Height),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                if (selectedBarIndex == 1) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(bar1Left - 1.dp.toPx(), bar1Top - 1.dp.toPx()),
                        size = Size(barWidth + 2.dp.toPx(), bar1Height + 2.dp.toPx()),
                        cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }

            // Left Y-axis labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .padding(top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text("6h", color = InsightsTextMuted, fontSize = 8.5.sp)
                Text("4h", color = InsightsTextMuted, fontSize = 8.5.sp)
                Text("2h", color = InsightsTextMuted, fontSize = 8.5.sp)
                Text("0h", color = InsightsTextMuted, fontSize = 8.5.sp)
            }

            // Values above bars
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = formatFocusDuration(prevDurationMinutes),
                        color = InsightsTextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 4.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = formatFocusDuration(currentDurationMinutes),
                        color = InsightsTextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Tappable overlays
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 26.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectBar(0) }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelectBar(1) }
                )
            }
        }

        // Labels below bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, top = 4.dp)
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = prevLabel,
                    color = if (selectedBarIndex == 0) InsightsGreen else InsightsTextMuted,
                    fontSize = 9.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    text = currentLabel,
                    color = if (selectedBarIndex == 1) InsightsGreen else InsightsTextMuted,
                    fontSize = 9.5.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

/**
 * 2x2 Summary Card in Weekly Improvement Detail
 */
@Composable
private fun InsightStatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = InsightsCardBg,
        border = BorderStroke(1.dp, InsightsCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0x1F22C55E)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                text = value,
                color = InsightsTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                color = InsightsTextMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ----------------------------------------------------------------------
// SCREEN 3: BEST FOCUS TIME DETAILS
// ----------------------------------------------------------------------
@Composable
fun BestFocusTimeDetailScreen(
    sessions: List<FocusSessionRecord>,
    onBack: () -> Unit,
    onNavigateToSessionsForHourRange: (IntRange) -> Unit,
) {
    var period by rememberSaveable { mutableStateOf(BestTimesPeriod.WEEK) }
    var selectedHour by rememberSaveable { mutableIntStateOf(9) }

    val data = remember(sessions, period, selectedHour) {
        calculateBestFocusTimeInsight(sessions, period, selectedHour)
    }

    Scaffold(
        containerColor = InsightsDarkBg,
        topBar = {
            InsightsDetailHeader(
                title = "Insight Details",
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero / Summary card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = InsightsCardBg,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsSunIcon(color = InsightsGold, modifier = Modifier.size(19.dp))
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = data.summaryTitle,
                            color = InsightsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        )
                        Text(
                            text = data.summarySubtitle,
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Hourly Focus Distribution Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = InsightsCardBg,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Hourly Focus Distribution",
                        color = InsightsTextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    )

                    // Day / Week / Month selector
                    SegmentedPeriodSelector(
                        selectedPeriod = period,
                        onSelectPeriod = { period = it }
                    )

                    // Hourly Bar Chart with translucent highlight overlay over 8 AM - 11 AM
                    HourlyDistributionChartWithHighlight(
                        buckets = data.hourlyBuckets,
                        selectedHour = selectedHour,
                        highlightStartHour = data.peakStartHour,
                        highlightEndHour = data.peakEndHour,
                        onSelectHour = { h ->
                            selectedHour = if (selectedHour == h) -1 else h
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                    )
                }
            }

            // Peak Focus Time Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = InsightsCardInner,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsSunIcon(color = InsightsGold, modifier = Modifier.size(18.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Peak focus time: ${data.peakTimeRangeLabel}",
                            color = InsightsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "You complete ${data.peakPercentage}% of your total focus time during this period.",
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            // Time Range Breakdown Section
            Text(
                text = "Time Range Breakdown",
                color = InsightsTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Table of Time Ranges
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = InsightsCardBg,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time Range",
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "Focus Time",
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = "Sessions",
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    HorizontalDivider(color = InsightsCardBorder, thickness = 1.dp)

                    val maxDuration = data.timeRanges.maxOfOrNull { it.durationMinutes }?.coerceAtLeast(1) ?: 1

                    data.timeRanges.forEachIndexed { idx, slot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val range = if (slot.startHour <= slot.endHour) {
                                        slot.startHour..slot.endHour
                                    } else {
                                        slot.startHour..23 // simplified for crossing midnight
                                    }
                                    onNavigateToSessionsForHourRange(range)
                                }
                                .padding(vertical = 9.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = slot.label,
                                color = if (slot.isPeak) InsightsGreen else InsightsTextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (slot.isPeak) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )

                            // Focus Time with proportional mint progress bar
                            Row(
                                modifier = Modifier.weight(2f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val fraction = (slot.durationMinutes.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(InsightsBarTrackBg)
                                ) {
                                    if (fraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (slot.isPeak) InsightsGreen else Color(0xFF34D399))
                                        )
                                    }
                                }
                                Text(
                                    text = slot.formattedDuration,
                                    color = if (slot.durationMinutes > 0) InsightsGreen else InsightsTextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(55.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            // Sessions count
                            Text(
                                text = slot.sessionCount.toString(),
                                color = if (slot.sessionCount > 0) InsightsTextWhite else InsightsTextMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        if (idx < data.timeRanges.size - 1) {
                            HorizontalDivider(color = Color(0x1A1B2E24), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // Bottom Productivity Tip Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = InsightsCardInner,
                border = BorderStroke(1.dp, InsightsCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(0x26FBBF24)),
                        contentAlignment = Alignment.Center
                    ) {
                        PatternsLightbulbIcon(color = InsightsGold, modifier = Modifier.size(18.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Tip",
                            color = InsightsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = data.tipText,
                            color = InsightsTextMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Hourly Distribution Chart with peak window glow highlight
 */
@Composable
private fun HourlyDistributionChartWithHighlight(
    buckets: List<HourlyBucketData>,
    selectedHour: Int,
    highlightStartHour: Int,
    highlightEndHour: Int,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem = buckets.find { it.hour == selectedHour }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val labelAreaW = 26.dp.toPx()
                val chartLeft = labelAreaW
                val chartW = w - chartLeft
                val chartBottom = h - 6.dp.toPx()
                val chartTop = 26.dp.toPx()
                val chartH = chartBottom - chartTop

                val yMaxMinutes = 120f // 2h scale
                val yStepMinutes = 60f

                // Draw Y-axis grid lines
                for (step in 0..2) {
                    val stepMins = step * yStepMinutes
                    val y = chartBottom - (stepMins / yMaxMinutes) * chartH
                    drawLine(
                        color = Color(0x2E1B2E24),
                        start = Offset(chartLeft, y),
                        end = Offset(w, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                val barCount = buckets.size
                if (barCount > 0) {
                    val slotW = chartW / barCount

                    // Highlight background over peak hours (e.g. 8 AM - 11 AM)
                    val hlStartIdx = buckets.indexOfFirst { it.hour >= highlightStartHour }
                    val hlEndIdx = buckets.indexOfLast { it.hour <= highlightEndHour }
                    if (hlStartIdx >= 0 && hlEndIdx >= hlStartIdx) {
                        val hlLeft = chartLeft + slotW * hlStartIdx
                        val hlWidth = slotW * (hlEndIdx - hlStartIdx + 1)
                        drawRoundRect(
                            color = Color(0x14FBBF24), // amber subtle glow
                            topLeft = Offset(hlLeft, chartTop - 4.dp.toPx()),
                            size = Size(hlWidth, chartH + 8.dp.toPx()),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }

                    val barW = slotW * 0.58f

                    buckets.forEachIndexed { index, item ->
                        val centerX = chartLeft + slotW * index + slotW / 2f
                        val barLeft = centerX - barW / 2f

                        val durRatio = (item.durationMinutes.toFloat() / yMaxMinutes).coerceIn(0f, 1f)
                        val barH = (chartH * durRatio).coerceAtLeast(3.dp.toPx())
                        val barTop = chartBottom - barH

                        val isSel = item.hour == selectedHour

                        // Dim bar background track
                        drawRoundRect(
                            color = Color(0x1A4ADE80),
                            topLeft = Offset(barLeft, chartTop),
                            size = Size(barW, chartH),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )

                        // Filled bar
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF86EFAC), Color(0xFF22C55E)),
                                startY = barTop,
                                endY = chartBottom
                            ),
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )

                        if (isSel) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(barLeft - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                size = Size(barW + 2.dp.toPx(), barH + 2.dp.toPx()),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }
            }

            // Left Y-axis labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(24.dp)
                    .padding(top = 18.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text("2h", color = InsightsTextMuted, fontSize = 8.5.sp)
                Text("1h", color = InsightsTextMuted, fontSize = 8.5.sp)
                Text("0h", color = InsightsTextMuted, fontSize = 8.5.sp)
            }

            // Floating Tooltip above selected bar
            if (selectedItem != null) {
                val selectedIndex = buckets.indexOf(selectedItem)
                if (selectedIndex >= 0) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 26.dp)
                    ) {
                        val totalW = maxWidth
                        val slotW = totalW / buckets.size
                        val barCenter = slotW * selectedIndex + slotW / 2f
                        val tooltipW = 90.dp
                        val tooltipOffset = (barCenter - tooltipW / 2f).coerceIn(0.dp, totalW - tooltipW)

                        Box(
                            modifier = Modifier
                                .offset(x = tooltipOffset, y = 0.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xE605100A))
                                .border(1.dp, Color(0x664ADE80), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = selectedItem.formattedDuration,
                                    color = InsightsTextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = selectedItem.timeLabel,
                                    color = InsightsGreen,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Tappable overlay columns
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 26.dp)
            ) {
                buckets.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectHour(item.hour) }
                    )
                }
            }
        }

        // X-axis labels below: 6 AM, 9 AM, 12 PM, 3 PM, 6 PM, 9 PM
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val keyHours = listOf("6 AM", "9 AM", "12 PM", "3 PM", "6 PM", "9 PM")
            keyHours.forEach { label ->
                Text(
                    text = label,
                    color = InsightsTextMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Top Header for Detail Screens
 */
@Composable
private fun InsightsDetailHeader(
    title: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = InsightsDarkBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Text("←", color = InsightsGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = InsightsTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp
                )
            }

            Spacer(Modifier.size(36.dp))
        }
    }
}
