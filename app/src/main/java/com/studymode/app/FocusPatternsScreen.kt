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
private val PatternsDarkBg = Color(0xFF07120D)
private val PatternsCardBg = Color(0xFF0E1A14)
private val PatternsCardBorder = Color(0xFF1B2E24)
private val PatternsCardInner = Color(0xFF13231B)
private val PatternsGreen = Color(0xFF4ADE80)
private val PatternsGreenDark = Color(0xFF22C55E)
private val PatternsGreenPillText = Color(0xFF0A2E18)
private val PatternsTextWhite = Color(0xFFF3F4F6)
private val PatternsTextMuted = Color(0xFF8E9993)
private val PatternsBarTrackBg = Color(0xFF132219)
private val PatternsGold = Color(0xFFFBBF24)
private val PatternsRed = Color(0xFFEF4444)

// ----------------------------------------------------------------------
// ENUMS & MODELS
// ----------------------------------------------------------------------
enum class PatternsDetailScreen {
    NONE,
    BEST_FOCUS_TIMES,
    DAY_OF_WEEK,
    SESSION_LENGTH,
}

enum class BestTimesPeriod(val label: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month"),
}

enum class SessionLengthBracket(
    val label: String,
    val shortLabel: String,
    val minSeconds: Long,
    val maxSeconds: Long
) {
    UNDER_15("< 15 min", "< 15 min", 0L, 14 * 60L + 59L),
    BETWEEN_15_30("15 – 30 min", "15–30 min", 15 * 60L, 30 * 60L - 1L),
    BETWEEN_30_60("30 – 60 min", "30–60 min", 30 * 60L, 60 * 60L),
    OVER_60("> 60 min", "> 60 min", 60 * 60L + 1L, Long.MAX_VALUE);

    fun matches(elapsedSeconds: Long): Boolean {
        return elapsedSeconds in minSeconds..maxSeconds
    }
}

data class HourlyBucketData(
    val hour: Int,
    val timeLabel: String,
    val shortTimeLabel: String,
    val durationMinutes: Int,
    val sessionCount: Int,
    val formattedDuration: String,
    val isSelected: Boolean = false,
)

data class WeekdayPatternData(
    val dayOfWeek: Int,
    val dayName: String,
    val shortName: String,
    val durationMinutes: Int,
    val sessionCount: Int,
    val formattedDuration: String,
    val vsAvgPercent: Int,
    val isSelected: Boolean = false,
)

data class WeekdayStatsSummary(
    val totalMinutes: Int,
    val formattedTotalTime: String,
    val totalSessions: Int,
    val dailyAverageMinutes: Int,
    val formattedDailyAvgTime: String,
    val peakDayName: String,
    val peakDayPercentVsAvg: Int,
)

data class LengthBracketData(
    val bracket: SessionLengthBracket,
    val count: Int,
    val percentage: Int,
    val avgDurationMinutes: Int,
    val formattedAvgDuration: String,
    val isSelected: Boolean = false,
)

data class LengthStatsSummary(
    val averageLengthMinutes: Int,
    val formattedAvgLength: String,
    val totalSessions: Int,
    val deltaVsLastMonthMinutes: Int,
    val mostCommonBracket: SessionLengthBracket,
)

data class BestTimesInsight(
    val periodTitle: String,
    val periodDescription: String,
    val peakHoursLabel: String,
    val peakPeriodType: String,
)

// ----------------------------------------------------------------------
// FORMATTING HELPERS
// ----------------------------------------------------------------------
fun formatFocusDuration(minutes: Int): String {
    if (minutes <= 0) return "0 min"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m} min"
    }
}

// ----------------------------------------------------------------------
// DATA COMPUTATION FUNCTIONS
// ----------------------------------------------------------------------
fun calculateHourlyPatterns(
    sessions: List<FocusSessionRecord>,
    period: BestTimesPeriod = BestTimesPeriod.DAY,
    selectedHour: Int = -1
): Pair<List<HourlyBucketData>, BestTimesInsight> {
    val cal = Calendar.getInstance()
    // We compute stats for hours 6 AM to 10 PM (hours 6..22) for the chart, but bucket all 24h
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

    val chartHours = (6..22).toList()
    val buckets = chartHours.map { h ->
        val nextH = (h + 1) % 24
        val hStr = when {
            h == 0 -> "12 AM"
            h < 12 -> "$h AM"
            h == 12 -> "12 PM"
            else -> "${h - 12} PM"
        }
        val nextHStr = when {
            nextH == 0 -> "12 AM"
            nextH < 12 -> "$nextH AM"
            nextH == 12 -> "12 PM"
            else -> "${nextH - 12} PM"
        }
        val timeLabel = "$hStr – $nextHStr"
        val dur = durationByHour[h]
        val cnt = countByHour[h]
        HourlyBucketData(
            hour = h,
            timeLabel = timeLabel,
            shortTimeLabel = hStr,
            durationMinutes = dur,
            sessionCount = cnt,
            formattedDuration = formatFocusDuration(dur),
            isSelected = h == selectedHour
        )
    }

    // Determine peak time insight
    val morningMins = (6..11).sumOf { durationByHour[it] }
    val afternoonMins = (12..17).sumOf { durationByHour[it] }
    val eveningMins = (18..23).sumOf { durationByHour[it] }

    // Find best 3-hour continuous window
    var bestWindowStart = 8
    var maxWindowSum = 0
    for (startH in 6..20) {
        val windowSum = durationByHour[startH] + durationByHour[startH + 1] + durationByHour[startH + 2]
        if (windowSum > maxWindowSum) {
            maxWindowSum = windowSum
            bestWindowStart = startH
        }
    }

    val windowEnd = bestWindowStart + 3
    val startFmt = if (bestWindowStart < 12) "$bestWindowStart AM" else if (bestWindowStart == 12) "12 PM" else "${bestWindowStart - 12} PM"
    val endFmt = if (windowEnd < 12) "$windowEnd AM" else if (windowEnd == 12) "12 PM" else "${windowEnd - 12} PM"
    val windowLabel = "$startFmt – $endFmt"

    val insight = if (sessions.isEmpty()) {
        BestTimesInsight(
            periodTitle = "No focus data yet",
            periodDescription = "Complete sessions to discover your peak focus hours.",
            peakHoursLabel = "None",
            peakPeriodType = "SUN"
        )
    } else if (morningMins >= afternoonMins && morningMins >= eveningMins) {
        BestTimesInsight(
            periodTitle = "You focus best in the morning",
            periodDescription = "Your highest average focus time is between $windowLabel.",
            peakHoursLabel = windowLabel,
            peakPeriodType = "SUN"
        )
    } else if (afternoonMins >= morningMins && afternoonMins >= eveningMins) {
        BestTimesInsight(
            periodTitle = "You focus best in the afternoon",
            periodDescription = "Your highest average focus time is between $windowLabel.",
            peakHoursLabel = windowLabel,
            peakPeriodType = "AFTERNOON"
        )
    } else {
        BestTimesInsight(
            periodTitle = "You focus best in the evening",
            periodDescription = "Your highest average focus time is between $windowLabel.",
            peakHoursLabel = windowLabel,
            peakPeriodType = "EVENING"
        )
    }

    return Pair(buckets, insight)
}

fun calculateWeekdayPatterns(
    sessions: List<FocusSessionRecord>,
    selectedDayOfWeek: Int = -1
): Pair<List<WeekdayPatternData>, WeekdayStatsSummary> {
    val cal = Calendar.getInstance()
    val weekdays = listOf(
        Triple(Calendar.MONDAY, "Monday", "Mon"),
        Triple(Calendar.TUESDAY, "Tuesday", "Tue"),
        Triple(Calendar.WEDNESDAY, "Wednesday", "Wed"),
        Triple(Calendar.THURSDAY, "Thursday", "Thu"),
        Triple(Calendar.FRIDAY, "Friday", "Fri"),
        Triple(Calendar.SATURDAY, "Saturday", "Sat"),
        Triple(Calendar.SUNDAY, "Sunday", "Sun")
    )

    val durationMap = mutableMapOf<Int, Int>()
    val countMap = mutableMapOf<Int, Int>()

    sessions.forEach { s ->
        cal.timeInMillis = s.startedAtMillis
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val mins = (s.elapsedSeconds / 60L).toInt()
        durationMap[dow] = (durationMap[dow] ?: 0) + mins
        countMap[dow] = (countMap[dow] ?: 0) + 1
    }

    val totalMins = weekdays.sumOf { durationMap[it.first] ?: 0 }
    val totalSessions = weekdays.sumOf { countMap[it.first] ?: 0 }
    val activeDays = weekdays.count { (durationMap[it.first] ?: 0) > 0 }
    val dailyAvgMins = if (activeDays > 0) totalMins / activeDays else 0

    val peakDayEntry = weekdays.maxByOrNull { durationMap[it.first] ?: 0 }
    val peakDayName = peakDayEntry?.second ?: "None"
    val peakDayMins = peakDayEntry?.let { durationMap[it.first] ?: 0 } ?: 0
    val peakPercentVsAvg = if (dailyAvgMins > 0) {
        ((peakDayMins - dailyAvgMins) * 100) / dailyAvgMins
    } else 0

    val weekdayDataList = weekdays.map { (dow, name, shortName) ->
        val mins = durationMap[dow] ?: 0
        val cnt = countMap[dow] ?: 0
        val vsAvg = if (dailyAvgMins > 0) {
            ((mins - dailyAvgMins) * 100) / dailyAvgMins
        } else 0

        WeekdayPatternData(
            dayOfWeek = dow,
            dayName = name,
            shortName = shortName,
            durationMinutes = mins,
            sessionCount = cnt,
            formattedDuration = formatFocusDuration(mins),
            vsAvgPercent = vsAvg,
            isSelected = dow == selectedDayOfWeek
        )
    }

    val summary = WeekdayStatsSummary(
        totalMinutes = totalMins,
        formattedTotalTime = formatFocusDuration(totalMins),
        totalSessions = totalSessions,
        dailyAverageMinutes = dailyAvgMins,
        formattedDailyAvgTime = formatFocusDuration(dailyAvgMins),
        peakDayName = peakDayName,
        peakDayPercentVsAvg = peakPercentVsAvg
    )

    return Pair(weekdayDataList, summary)
}

fun calculateLengthPatterns(
    sessions: List<FocusSessionRecord>,
    prevSessions: List<FocusSessionRecord> = emptyList(),
    selectedBracket: SessionLengthBracket? = null
): Pair<List<LengthBracketData>, LengthStatsSummary> {
    val totalCount = sessions.size
    val brackets = SessionLengthBracket.values()

    val bracketList = brackets.map { bracket ->
        val matched = sessions.filter { bracket.matches(it.elapsedSeconds) }
        val cnt = matched.size
        val pct = if (totalCount > 0) ((cnt * 100) / totalCount) else 0
        val avgMins = if (cnt > 0) (matched.sumOf { it.elapsedSeconds } / cnt / 60L).toInt() else 0

        LengthBracketData(
            bracket = bracket,
            count = cnt,
            percentage = pct,
            avgDurationMinutes = avgMins,
            formattedAvgDuration = formatFocusDuration(avgMins),
            isSelected = bracket == selectedBracket
        )
    }

    val totalDurationSeconds = sessions.sumOf { it.elapsedSeconds }
    val avgLengthMins = if (totalCount > 0) (totalDurationSeconds / totalCount / 60L).toInt() else 0

    val prevTotalCount = prevSessions.size
    val prevAvgLengthMins = if (prevTotalCount > 0) {
        (prevSessions.sumOf { it.elapsedSeconds } / prevTotalCount / 60L).toInt()
    } else 0
    val deltaVsLastMonth = avgLengthMins - prevAvgLengthMins

    val mostCommon = bracketList.maxByOrNull { it.count }?.bracket ?: SessionLengthBracket.BETWEEN_30_60

    val summary = LengthStatsSummary(
        averageLengthMinutes = avgLengthMins,
        formattedAvgLength = formatFocusDuration(avgLengthMins),
        totalSessions = totalCount,
        deltaVsLastMonthMinutes = deltaVsLastMonth,
        mostCommonBracket = mostCommon
    )

    return Pair(bracketList, summary)
}

// ----------------------------------------------------------------------
// SCREEN 1: MAIN PATTERNS DASHBOARD CONTENT
// ----------------------------------------------------------------------
@Composable
fun PatternsDashboardContent(
    sessions: List<FocusSessionRecord>,
    prevSessions: List<FocusSessionRecord>,
    onOpenBestFocusTimes: () -> Unit,
    onOpenDayOfWeek: () -> Unit,
    onOpenSessionLength: () -> Unit,
    onNavigateToSessionsForBracket: (SessionLengthBracket) -> Unit,
    onNavigateToSessionsForWeekday: (Int) -> Unit = {},
    onNavigateToSessionsForHour: (Int) -> Unit = {},
) {
    var bestTimesPeriod by rememberSaveable { mutableStateOf(BestTimesPeriod.DAY) }
    var selectedHour by rememberSaveable { mutableIntStateOf(9) } // Default 9 AM in mockup
    var selectedWeekday by rememberSaveable { mutableIntStateOf(Calendar.SATURDAY) } // Default Saturday

    val (hourlyBuckets, bestTimesInsight) = remember(sessions, bestTimesPeriod, selectedHour) {
        calculateHourlyPatterns(sessions, bestTimesPeriod, selectedHour)
    }

    val (weekdayList, weekdaySummary) = remember(sessions, selectedWeekday) {
        calculateWeekdayPatterns(sessions, selectedWeekday)
    }

    val (lengthList, lengthSummary) = remember(sessions, prevSessions) {
        calculateLengthPatterns(sessions, prevSessions)
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // ==============================================================
        // CARD 1: BEST FOCUS TIMES
        // ==============================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PatternsCardBg,
            border = BorderStroke(1.dp, PatternsCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBestFocusTimes() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            PatternsClockIcon(color = PatternsGreen, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text(
                                text = "Best Focus Times",
                                color = PatternsTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Your most productive hours",
                                color = PatternsTextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text("›", color = PatternsTextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Segmented control: Day / Week / Month
                SegmentedPeriodSelector(
                    selectedPeriod = bestTimesPeriod,
                    onSelectPeriod = { bestTimesPeriod = it }
                )

                // Hourly Focus Bar Chart
                HourlyBarChart(
                    buckets = hourlyBuckets,
                    selectedHour = selectedHour,
                    onSelectHour = { h ->
                        selectedHour = if (selectedHour == h) -1 else h
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )

                // Insight banner
                PatternInsightCard(
                    iconType = bestTimesInsight.peakPeriodType,
                    title = bestTimesInsight.periodTitle,
                    subtitle = bestTimesInsight.periodDescription,
                    onClick = onOpenBestFocusTimes
                )
            }
        }

        // ==============================================================
        // CARD 2: FOCUS BY DAY OF WEEK
        // ==============================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PatternsCardBg,
            border = BorderStroke(1.dp, PatternsCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDayOfWeek() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            PatternsCalendarIcon(color = PatternsGreen, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = "Focus by Day of Week",
                            color = PatternsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Text("›", color = PatternsTextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // Weekday Bar Chart
                WeekdayBarChart(
                    weekdays = weekdayList,
                    selectedDayOfWeek = selectedWeekday,
                    onSelectDay = { dow ->
                        selectedWeekday = if (selectedWeekday == dow) -1 else dow
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                )

                // Insight banner
                val peakSubtitle = if (weekdaySummary.peakDayPercentVsAvg > 0) {
                    "You focus ${weekdaySummary.peakDayPercentVsAvg}% more on ${weekdaySummary.peakDayName}s compared to your weekly average."
                } else {
                    "You focus most on ${weekdaySummary.peakDayName}s."
                }
                PatternInsightCard(
                    iconType = "TROPHY",
                    title = "${weekdaySummary.peakDayName} is your most productive day",
                    subtitle = peakSubtitle,
                    onClick = onOpenDayOfWeek
                )
            }
        }

        // ==============================================================
        // CARD 3: STUDY SESSION LENGTH
        // ==============================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = PatternsCardBg,
            border = BorderStroke(1.dp, PatternsCardBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSessionLength() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0x1F22C55E)),
                            contentAlignment = Alignment.Center
                        ) {
                            PatternsBarChartIcon(color = PatternsGreen, modifier = Modifier.size(16.dp))
                        }
                        Column {
                            Text(
                                text = "Study Session Length",
                                color = PatternsTextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Distribution of your session lengths",
                                color = PatternsTextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text("›", color = PatternsTextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                // 4 Horizontal Progress Rows
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lengthList.forEach { item ->
                        HorizontalLengthProgressRow(
                            item = item,
                            onClick = { onNavigateToSessionsForBracket(item.bracket) }
                        )
                    }
                }

                // Insight banner
                PatternInsightCard(
                    iconType = "HOURGLASS",
                    title = "Most of your sessions are ${lengthSummary.mostCommonBracket.label}",
                    subtitle = "This is a good balance for deep focus.",
                    onClick = onOpenSessionLength
                )
            }
        }
    }
}

// ----------------------------------------------------------------------
// SCREEN 2: BEST FOCUS TIMES DETAILS
// ----------------------------------------------------------------------
@Composable
fun BestFocusTimesDetailScreen(
    sessions: List<FocusSessionRecord>,
    onBack: () -> Unit,
    onNavigateToSessionsForHour: (Int) -> Unit,
) {
    var bestTimesPeriod by rememberSaveable { mutableStateOf(BestTimesPeriod.DAY) }
    var selectedHour by rememberSaveable { mutableIntStateOf(9) }

    val (hourlyBuckets, bestTimesInsight) = remember(sessions, bestTimesPeriod, selectedHour) {
        calculateHourlyPatterns(sessions, bestTimesPeriod, selectedHour)
    }

    Scaffold(
        containerColor = PatternsDarkBg,
        topBar = {
            FocusDetailHeader(
                title = "Best Focus Times",
                subtitle = "Your focus activity by hour of day",
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
            // Segmented control: Day / Week / Month
            SegmentedPeriodSelector(
                selectedPeriod = bestTimesPeriod,
                onSelectPeriod = { bestTimesPeriod = it }
            )

            // Hourly Focus Bar Chart inside surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PatternsCardBg,
                border = BorderStroke(1.dp, PatternsCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HourlyBarChart(
                        buckets = hourlyBuckets,
                        selectedHour = selectedHour,
                        onSelectHour = { h ->
                            selectedHour = if (selectedHour == h) -1 else h
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                    )
                }
            }

            // Insight card
            PatternInsightCard(
                iconType = bestTimesInsight.peakPeriodType,
                title = bestTimesInsight.periodTitle,
                subtitle = bestTimesInsight.periodDescription,
                showChevron = false
            )

            // Section: Hourly Breakdown
            Text(
                text = "Hourly Breakdown",
                color = PatternsTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Table of hourly breakdown
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = PatternsCardBg,
                border = BorderStroke(1.dp, PatternsCardBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Table Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "Focus Time",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(2f)
                        )
                        Text(
                            text = "Sessions",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(0.9f)
                        )
                    }

                    HorizontalDivider(color = PatternsCardBorder, thickness = 1.dp)

                    val maxDuration = hourlyBuckets.maxOfOrNull { it.durationMinutes }?.coerceAtLeast(1) ?: 1

                    hourlyBuckets.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToSessionsForHour(item.hour) }
                                .padding(vertical = 9.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Time label
                            Text(
                                text = item.timeLabel,
                                color = PatternsTextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )

                            // Focus Time with mini horizontal mint bar
                            Row(
                                modifier = Modifier.weight(2f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val fraction = (item.durationMinutes.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PatternsBarTrackBg)
                                ) {
                                    if (fraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(fraction)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(PatternsGreen)
                                        )
                                    }
                                }
                                Text(
                                    text = item.formattedDuration,
                                    color = if (item.durationMinutes > 0) PatternsGreen else PatternsTextMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(55.dp),
                                    textAlign = TextAlign.End
                                )
                            }

                            // Sessions count
                            Text(
                                text = item.sessionCount.toString(),
                                color = if (item.sessionCount > 0) PatternsTextWhite else PatternsTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(0.9f)
                            )
                        }

                        if (idx < hourlyBuckets.size - 1) {
                            HorizontalDivider(color = Color(0x1A1B2E24), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ----------------------------------------------------------------------
// SCREEN 3: FOCUS BY DAY OF WEEK DETAILS
// ----------------------------------------------------------------------
@Composable
fun FocusByDayOfWeekDetailScreen(
    sessions: List<FocusSessionRecord>,
    onBack: () -> Unit,
    onNavigateToSessionsForWeekday: (Int) -> Unit,
) {
    var selectedWeekday by rememberSaveable { mutableIntStateOf(Calendar.SATURDAY) }

    val (weekdayList, weekdaySummary) = remember(sessions, selectedWeekday) {
        calculateWeekdayPatterns(sessions, selectedWeekday)
    }

    Scaffold(
        containerColor = PatternsDarkBg,
        topBar = {
            FocusDetailHeader(
                title = "Focus by Day of Week",
                subtitle = "Compare your focus time across the week",
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
            // 3 Stat Cards in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PatternsTopStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsBarChartIcon(color = PatternsGreen, modifier = Modifier.size(15.dp)) },
                    value = weekdaySummary.formattedTotalTime,
                    label = "Total this week"
                )
                PatternsTopStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsTargetIcon(color = PatternsGreen, modifier = Modifier.size(15.dp)) },
                    value = weekdaySummary.totalSessions.toString(),
                    label = "Total sessions"
                )
                PatternsTopStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsCalendarIcon(color = PatternsGreen, modifier = Modifier.size(15.dp)) },
                    value = weekdaySummary.formattedDailyAvgTime,
                    label = "Daily average"
                )
            }

            // Weekday Bar Chart inside surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PatternsCardBg,
                border = BorderStroke(1.dp, PatternsCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WeekdayBarChart(
                        weekdays = weekdayList,
                        selectedDayOfWeek = selectedWeekday,
                        onSelectDay = { dow ->
                            selectedWeekday = if (selectedWeekday == dow) -1 else dow
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                    )
                }
            }

            // Insight card
            val peakSubtitle = if (weekdaySummary.peakDayPercentVsAvg > 0) {
                "You focus ${weekdaySummary.peakDayPercentVsAvg}% more on ${weekdaySummary.peakDayName}s compared to your weekly average."
            } else {
                "You focus most on ${weekdaySummary.peakDayName}s."
            }
            PatternInsightCard(
                iconType = "TROPHY",
                title = "${weekdaySummary.peakDayName} is your most productive day",
                subtitle = peakSubtitle,
                showChevron = false
            )

            // Section: Detailed Breakdown
            Text(
                text = "Detailed Breakdown",
                color = PatternsTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Table of Weekdays
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = PatternsCardBg,
                border = BorderStroke(1.dp, PatternsCardBorder)
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
                            text = "Day",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "Focus Time",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            text = "Sessions",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = "vs Avg",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    HorizontalDivider(color = PatternsCardBorder, thickness = 1.dp)

                    weekdayList.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToSessionsForWeekday(item.dayOfWeek) }
                                .padding(vertical = 9.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.dayName,
                                color = PatternsTextWhite,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = item.formattedDuration,
                                color = if (item.durationMinutes > 0) PatternsGreen else PatternsTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1.2f)
                            )
                            Text(
                                text = item.sessionCount.toString(),
                                color = PatternsTextWhite,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.9f)
                            )
                            val isPositive = item.vsAvgPercent > 0
                            val vsAvgText = when {
                                item.durationMinutes == 0 -> "0%"
                                isPositive -> "+${item.vsAvgPercent}%"
                                else -> "${item.vsAvgPercent}%"
                            }
                            Text(
                                text = vsAvgText,
                                color = if (isPositive) PatternsGreen else PatternsRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.1f)
                            )
                        }

                        if (idx < weekdayList.size - 1) {
                            HorizontalDivider(color = Color(0x1A1B2E24), thickness = 0.5.dp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ----------------------------------------------------------------------
// SCREEN 4: STUDY SESSION LENGTH DETAILS
// ----------------------------------------------------------------------
@Composable
fun StudySessionLengthDetailScreen(
    sessions: List<FocusSessionRecord>,
    prevSessions: List<FocusSessionRecord>,
    onBack: () -> Unit,
    onNavigateToSessionsForBracket: (SessionLengthBracket) -> Unit,
) {
    var selectedBracket by rememberSaveable { mutableStateOf<SessionLengthBracket?>(SessionLengthBracket.BETWEEN_30_60) }

    val (lengthList, lengthSummary) = remember(sessions, prevSessions, selectedBracket) {
        calculateLengthPatterns(sessions, prevSessions, selectedBracket)
    }

    Scaffold(
        containerColor = PatternsDarkBg,
        topBar = {
            FocusDetailHeader(
                title = "Study Session Length",
                subtitle = "Distribution and analysis of your session durations",
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
            // 3 Stat Cards in a row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PatternsTopStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsClockIcon(color = PatternsGreen, modifier = Modifier.size(15.dp)) },
                    value = lengthSummary.formattedAvgLength,
                    label = "Average length"
                )
                PatternsTopStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsBarChartIcon(color = PatternsGreen, modifier = Modifier.size(15.dp)) },
                    value = lengthSummary.totalSessions.toString(),
                    label = "Total sessions"
                )
                val deltaSign = if (lengthSummary.deltaVsLastMonthMinutes >= 0) "+" else ""
                PatternsTopStatCard(
                    modifier = Modifier.weight(1f),
                    icon = { PatternsTrendUpIcon(color = PatternsGreen, modifier = Modifier.size(15.dp)) },
                    value = "$deltaSign${lengthSummary.deltaVsLastMonthMinutes} min",
                    label = "vs last month"
                )
            }

            // Vertical Distribution Bar Chart inside surface
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = PatternsCardBg,
                border = BorderStroke(1.dp, PatternsCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LengthDistributionBarChart(
                        brackets = lengthList,
                        selectedBracket = selectedBracket,
                        onSelectBracket = { b ->
                            selectedBracket = if (selectedBracket == b) null else b
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    )
                }
            }

            // Insight card
            PatternInsightCard(
                iconType = "HOURGLASS",
                title = "Most of your sessions are ${lengthSummary.mostCommonBracket.label}",
                subtitle = "This is a good balance for deep focus and retention.",
                showChevron = false
            )

            // Section: Session Length Details
            Text(
                text = "Session Length Details",
                color = PatternsTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Table of Session Length Categories
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = PatternsCardBg,
                border = BorderStroke(1.dp, PatternsCardBorder)
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
                            text = "Range",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1.3f)
                        )
                        Text(
                            text = "Percentage",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1.1f)
                        )
                        Text(
                            text = "Sessions",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(0.9f)
                        )
                        Text(
                            text = "Avg Focus Time",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1.4f)
                        )
                    }

                    HorizontalDivider(color = PatternsCardBorder, thickness = 1.dp)

                    lengthList.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToSessionsForBracket(item.bracket) }
                                .padding(vertical = 9.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.bracket.label,
                                color = PatternsTextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = "${item.percentage}%",
                                color = PatternsTextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1.1f)
                            )
                            Text(
                                text = item.count.toString(),
                                color = PatternsTextWhite,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(0.9f)
                            )
                            Text(
                                text = item.formattedAvgDuration,
                                color = if (item.count > 0) PatternsGreen else PatternsTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1.4f)
                            )
                        }

                        if (idx < lengthList.size - 1) {
                            HorizontalDivider(color = Color(0x1A1B2E24), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // Bottom Tip Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = PatternsCardInner,
                border = BorderStroke(1.dp, PatternsCardBorder)
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
                        PatternsLightbulbIcon(color = PatternsGold, modifier = Modifier.size(18.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Tip: Your most common session length is ${lengthSummary.mostCommonBracket.label}.",
                            color = PatternsTextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                        Text(
                            text = "This duration is ideal for maintaining focus and productivity.",
                            color = PatternsTextMuted,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ----------------------------------------------------------------------
// CHARTS & SUB-COMPONENTS
// ----------------------------------------------------------------------

/**
 * Segmented Control for Day, Week, Month
 */
@Composable
fun SegmentedPeriodSelector(
    selectedPeriod: BestTimesPeriod,
    onSelectPeriod: (BestTimesPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C1610))
            .padding(3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BestTimesPeriod.values().forEach { period ->
            val isSelected = period == selectedPeriod
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) PatternsGreen else Color.Transparent)
                    .clickable { onSelectPeriod(period) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label,
                    color = if (isSelected) PatternsGreenPillText else PatternsTextMuted,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Hourly Bar Chart (Screen 1 & 2)
 */
@Composable
fun HourlyBarChart(
    buckets: List<HourlyBucketData>,
    selectedHour: Int,
    onSelectHour: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem = buckets.find { it.hour == selectedHour }

    Column(modifier = modifier) {
        // Chart plotting area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val labelAreaW = 28.dp.toPx()
                val chartLeft = labelAreaW
                val chartW = w - chartLeft
                val chartBottom = h - 6.dp.toPx()
                val chartTop = 26.dp.toPx() // leave headroom for tooltip
                val chartH = chartBottom - chartTop

                val yMaxMinutes = 120f // 2h scale
                val yStepMinutes = 60f // 1h step

                // Draw Y-axis gridlines and reference marks
                val gridStroke = Stroke(width = 0.8.dp.toPx())
                for (step in 0..2) {
                    val stepMins = step * yStepMinutes
                    val y = chartBottom - (stepMins / yMaxMinutes) * chartH
                    drawLine(
                        color = Color(0x2E1B2E24),
                        start = Offset(chartLeft, y),
                        end = Offset(w, y),
                        strokeWidth = gridStroke.width
                    )
                }

                // Render bars
                val barCount = buckets.size
                if (barCount > 0) {
                    val slotW = chartW / barCount
                    val barW = slotW * 0.58f

                    buckets.forEachIndexed { index, item ->
                        val centerX = chartLeft + slotW * index + slotW / 2f
                        val barLeft = centerX - barW / 2f

                        val durRatio = (item.durationMinutes.toFloat() / yMaxMinutes).coerceIn(0f, 1f)
                        val barH = (chartH * durRatio).coerceAtLeast(3.dp.toPx())
                        val barTop = chartBottom - barH

                        val isSel = item.hour == selectedHour

                        // Bar background track (dim)
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
                    .width(26.dp)
                    .padding(top = 18.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text("2h", color = PatternsTextMuted, fontSize = 8.5.sp)
                Text("1h", color = PatternsTextMuted, fontSize = 8.5.sp)
                Text("0h", color = PatternsTextMuted, fontSize = 8.5.sp)
            }

            // Floating Tooltip above selected bar
            if (selectedItem != null) {
                val selectedIndex = buckets.indexOf(selectedItem)
                if (selectedIndex >= 0) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 28.dp)
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
                                    color = PatternsTextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = selectedItem.timeLabel,
                                    color = PatternsGreen,
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
                    .padding(start = 28.dp)
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
                .padding(start = 28.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val keyHours = listOf("6 AM", "9 AM", "12 PM", "3 PM", "6 PM", "9 PM")
            keyHours.forEach { label ->
                Text(
                    text = label,
                    color = PatternsTextMuted,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Weekday Bar Chart (Screen 1 & 3)
 */
@Composable
fun WeekdayBarChart(
    weekdays: List<WeekdayPatternData>,
    selectedDayOfWeek: Int,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedItem = weekdays.find { it.dayOfWeek == selectedDayOfWeek }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                val labelAreaW = 24.dp.toPx()
                val chartLeft = labelAreaW
                val chartW = w - chartLeft
                val chartBottom = h - 4.dp.toPx()
                val chartTop = 26.dp.toPx()
                val chartH = chartBottom - chartTop

                val yMaxMinutes = 180f // 3h scale
                val yStepMinutes = 60f

                // Draw Y-axis grid lines
                for (step in 0..3) {
                    val stepMins = step * yStepMinutes
                    val y = chartBottom - (stepMins / yMaxMinutes) * chartH
                    drawLine(
                        color = Color(0x2E1B2E24),
                        start = Offset(chartLeft, y),
                        end = Offset(w, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                // Render 7 bars
                val barCount = weekdays.size
                if (barCount > 0) {
                    val slotW = chartW / barCount
                    val barW = slotW * 0.52f

                    weekdays.forEachIndexed { index, item ->
                        val centerX = chartLeft + slotW * index + slotW / 2f
                        val barLeft = centerX - barW / 2f

                        val durRatio = (item.durationMinutes.toFloat() / yMaxMinutes).coerceIn(0f, 1f)
                        val barH = (chartH * durRatio).coerceAtLeast(3.dp.toPx())
                        val barTop = chartBottom - barH

                        val isSel = item.dayOfWeek == selectedDayOfWeek

                        // Bar background track
                        drawRoundRect(
                            color = Color(0x1A4ADE80),
                            topLeft = Offset(barLeft, chartTop),
                            size = Size(barW, chartH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
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
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        if (isSel) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(barLeft - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                size = Size(barW + 2.dp.toPx(), barH + 2.dp.toPx()),
                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
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
                    .width(22.dp)
                    .padding(top = 18.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text("3h", color = PatternsTextMuted, fontSize = 8.5.sp)
                Text("2h", color = PatternsTextMuted, fontSize = 8.5.sp)
                Text("1h", color = PatternsTextMuted, fontSize = 8.5.sp)
                Text("0h", color = PatternsTextMuted, fontSize = 8.5.sp)
            }

            // Floating Tooltip above selected weekday
            if (selectedItem != null) {
                val selectedIndex = weekdays.indexOf(selectedItem)
                if (selectedIndex >= 0) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp)
                    ) {
                        val totalW = maxWidth
                        val slotW = totalW / weekdays.size
                        val barCenter = slotW * selectedIndex + slotW / 2f
                        val tooltipW = 85.dp
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
                                    color = PatternsTextWhite,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${selectedItem.sessionCount} sessions",
                                    color = PatternsGreen,
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
                    .padding(start = 24.dp)
            ) {
                weekdays.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectDay(item.dayOfWeek) }
                    )
                }
            }
        }

        // X-axis labels below: Mon, Tue, Wed, Thu, Fri, Sat, Sun
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            weekdays.forEach { item ->
                val isSel = item.dayOfWeek == selectedDayOfWeek
                Text(
                    text = item.shortName,
                    color = if (isSel) PatternsGreen else PatternsTextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Vertical Distribution Bar Chart (Screen 4)
 */
@Composable
fun LengthDistributionBarChart(
    brackets: List<LengthBracketData>,
    selectedBracket: SessionLengthBracket?,
    onSelectBracket: (SessionLengthBracket) -> Unit,
    modifier: Modifier = Modifier
) {
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
                val chartBottom = h - 4.dp.toPx()
                val chartTop = 18.dp.toPx() // headroom for % labels
                val chartH = chartBottom - chartTop

                val yMaxPct = 60f // 60% scale
                val yStepPct = 20f

                // Draw Y-axis gridlines
                for (step in 0..3) {
                    val stepPct = step * yStepPct
                    val y = chartBottom - (stepPct / yMaxPct) * chartH
                    drawLine(
                        color = Color(0x2E1B2E24),
                        start = Offset(chartLeft, y),
                        end = Offset(w, y),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                // Render 4 vertical bars
                val barCount = brackets.size
                if (barCount > 0) {
                    val slotW = chartW / barCount
                    val barW = slotW * 0.48f

                    brackets.forEachIndexed { index, item ->
                        val centerX = chartLeft + slotW * index + slotW / 2f
                        val barLeft = centerX - barW / 2f

                        val pctRatio = (item.percentage.toFloat() / yMaxPct).coerceIn(0f, 1f)
                        val barH = (chartH * pctRatio).coerceAtLeast(4.dp.toPx())
                        val barTop = chartBottom - barH

                        val isSel = item.bracket == selectedBracket

                        // Filled bar
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF86EFAC), Color(0xFF22C55E)),
                                startY = barTop,
                                endY = chartBottom
                            ),
                            topLeft = Offset(barLeft, barTop),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        if (isSel) {
                            drawRoundRect(
                                color = Color.White,
                                topLeft = Offset(barLeft - 1.dp.toPx(), barTop - 1.dp.toPx()),
                                size = Size(barW + 2.dp.toPx(), barH + 2.dp.toPx()),
                                cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
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
                    .padding(top = 10.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text("60%", color = PatternsTextMuted, fontSize = 8.sp)
                Text("40%", color = PatternsTextMuted, fontSize = 8.sp)
                Text("20%", color = PatternsTextMuted, fontSize = 8.sp)
                Text("0%", color = PatternsTextMuted, fontSize = 8.sp)
            }

            // Percentage labels above each bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 26.dp)
            ) {
                brackets.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 0.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Text(
                            text = "${item.percentage}%",
                            color = PatternsTextWhite,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Tappable overlay columns
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 26.dp)
            ) {
                brackets.forEach { item ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectBracket(item.bracket) }
                    )
                }
            }
        }

        // X-axis labels below: < 15 min \n (3), 15 – 30 min \n (9), etc.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 26.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            brackets.forEach { item ->
                val isSel = item.bracket == selectedBracket
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.bracket.label,
                        color = if (isSel) PatternsGreen else PatternsTextWhite,
                        fontSize = 9.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "(${item.count})",
                        color = PatternsTextMuted,
                        fontSize = 8.5.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Horizontal Progress Row for Study Session Length Card (Screen 1)
 */
@Composable
private fun HorizontalLengthProgressRow(
    item: LengthBracketData,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = item.bracket.label,
            color = PatternsTextWhite,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(76.dp)
        )

        // Progress track & fill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PatternsBarTrackBg)
        ) {
            val fraction = (item.percentage.toFloat() / 100f).coerceIn(0f, 1f)
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(4.dp))
                        .background(PatternsGreen)
                )
            }
        }

        Text(
            text = "${item.percentage}% (${item.count})",
            color = PatternsTextWhite,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.End
        )
    }
}

/**
 * Insight Card component used on all 4 screens
 */
@Composable
fun PatternInsightCard(
    iconType: String,
    title: String,
    subtitle: String,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else modifier

    Surface(
        modifier = clickModifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = PatternsCardInner,
        border = BorderStroke(1.dp, PatternsCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in circular badge
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        when (iconType) {
                            "SUN", "TROPHY", "LIGHTBULB" -> Color(0x26FBBF24)
                            else -> Color(0x2622C55E)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (iconType) {
                    "SUN" -> PatternsSunIcon(color = PatternsGold, modifier = Modifier.size(18.dp))
                    "TROPHY" -> PatternsTrophyIcon(color = PatternsGold, modifier = Modifier.size(18.dp))
                    "HOURGLASS" -> PatternsHourglassIcon(color = PatternsGreen, modifier = Modifier.size(18.dp))
                    "LIGHTBULB" -> PatternsLightbulbIcon(color = PatternsGold, modifier = Modifier.size(18.dp))
                    else -> PatternsSunIcon(color = PatternsGreen, modifier = Modifier.size(18.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PatternsTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
                Text(
                    text = subtitle,
                    color = PatternsTextMuted,
                    fontSize = 11.sp
                )
            }

            if (showChevron) {
                Text("›", color = PatternsTextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Top Stat Card for Detail Screens (Screen 3 & 4)
 */
@Composable
private fun PatternsTopStatCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    value: String,
    label: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = PatternsCardBg,
        border = BorderStroke(1.dp, PatternsCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x1F22C55E)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Text(
                text = value,
                color = PatternsTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                color = PatternsTextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Header for Detail Screens (Screen 2, 3, 4)
 */
@Composable
private fun FocusDetailHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PatternsDarkBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Text("←", color = PatternsGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    color = PatternsTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp
                )
                Text(
                    text = subtitle,
                    color = PatternsTextMuted,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Spacer to balance back button
            Spacer(Modifier.size(36.dp))
        }
    }
}

// ----------------------------------------------------------------------
// CRISP VECTOR ICONS
// ----------------------------------------------------------------------
@Composable
fun PatternsClockIcon(color: Color, modifier: Modifier = Modifier) {
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
fun PatternsCalendarIcon(color: Color, modifier: Modifier = Modifier) {
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

@Composable
fun PatternsBarChartIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.22f
        val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        val h1 = h * 0.48f
        drawRoundRect(color, Offset(w * 0.08f, h - h1), Size(barW, h1), r)
        val h2 = h * 0.95f
        drawRoundRect(color, Offset(w * 0.39f, h - h2), Size(barW, h2), r)
        val h3 = h * 0.68f
        drawRoundRect(color, Offset(w * 0.70f, h - h3), Size(barW, h3), r)
    }
}

@Composable
fun PatternsTargetIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) / 2f * 0.88f
        val stroke = 1.6.dp.toPx()

        drawCircle(color, r, center, style = Stroke(stroke))
        drawCircle(color, r * 0.55f, center, style = Stroke(stroke))
        drawCircle(color, r * 0.2f, center)
    }
}

@Composable
fun PatternsTrophyIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Cup
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.25f, h * 0.15f),
            size = Size(w * 0.5f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.25f, w * 0.25f),
            style = Stroke(stroke)
        )
        // Stem
        drawLine(color, Offset(w * 0.5f, h * 0.60f), Offset(w * 0.5f, h * 0.78f), stroke)
        // Base
        drawLine(color, Offset(w * 0.3f, h * 0.78f), Offset(w * 0.7f, h * 0.78f), stroke, StrokeCap.Round)
        // Handles
        drawArc(
            color = color,
            startAngle = 140f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.20f),
            size = Size(w * 0.25f, h * 0.30f),
            style = Stroke(stroke)
        )
        drawArc(
            color = color,
            startAngle = -80f,
            sweepAngle = 160f,
            useCenter = false,
            topLeft = Offset(w * 0.63f, h * 0.20f),
            size = Size(w * 0.25f, h * 0.30f),
            style = Stroke(stroke)
        )
    }
}

@Composable
fun PatternsSunIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val r = minOf(w, h) * 0.22f
        val stroke = 1.6.dp.toPx()

        drawCircle(color, r, center, style = Stroke(stroke))

        // 8 radiating rays
        val rayLen = r * 0.55f
        val rayStart = r * 1.35f
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val cos = Math.cos(angle).toFloat()
            val sin = Math.sin(angle).toFloat()
            val p1 = Offset(center.x + rayStart * cos, center.y + rayStart * sin)
            val p2 = Offset(center.x + (rayStart + rayLen) * cos, center.y + (rayStart + rayLen) * sin)
            drawLine(color, p1, p2, stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun PatternsHourglassIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.18f)
            lineTo(w * 0.75f, h * 0.18f)
            lineTo(w * 0.50f, h * 0.50f)
            lineTo(w * 0.75f, h * 0.82f)
            lineTo(w * 0.25f, h * 0.82f)
            lineTo(w * 0.50f, h * 0.50f)
            close()
        }
        drawPath(path, color, style = Stroke(stroke))
        drawLine(color, Offset(w * 0.2f, h * 0.18f), Offset(w * 0.8f, h * 0.18f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.2f, h * 0.82f), Offset(w * 0.8f, h * 0.82f), stroke, StrokeCap.Round)
    }
}

@Composable
fun PatternsLightbulbIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // Bulb dome
        drawArc(
            color = color,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(w * 0.25f, h * 0.15f),
            size = Size(w * 0.50f, h * 0.50f),
            style = Stroke(stroke)
        )
        // Base lines
        drawLine(color, Offset(w * 0.38f, h * 0.68f), Offset(w * 0.62f, h * 0.68f), stroke)
        drawLine(color, Offset(w * 0.42f, h * 0.76f), Offset(w * 0.58f, h * 0.76f), stroke)
    }
}

@Composable
fun PatternsTrendUpIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        drawLine(color, Offset(w * 0.2f, h * 0.8f), Offset(w * 0.8f, h * 0.2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.8f, h * 0.2f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.8f, h * 0.5f), Offset(w * 0.8f, h * 0.2f), stroke, StrokeCap.Round)
    }
}
