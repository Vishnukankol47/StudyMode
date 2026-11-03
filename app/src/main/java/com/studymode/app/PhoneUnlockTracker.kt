package com.studymode.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// Theme Colors matching StudyMode Green-Ink styling
private val TrackerBg = Color(0xFF0A100C)
private val TrackerCardBg = Color(0xFF111C15)
private val TrackerCardBorder = Color(0xFF1B2E21)
private val MintAccent = Color(0xFF4ADE80)
private val MintLight = Color(0xFF86EFAC)
private val MintMuted = Color(0xFF193823)
private val WarningOrange = Color(0xFFFB923C)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8BA393)
private val ChipActiveBg = Color(0xFF1E3A28)
private val ChipInactiveBg = Color(0xFF132018)

/**
 * Statistics for a single 24-hour day of device pickups and unlocks.
 */
data class DayUnlockStats(
    val dayStartMillis: Long,
    val dayLabel: String,
    val totalUnlocks: Int,
    val totalPickups: Int,
    val prevDayUnlocks: Int,
    val prevDayPickups: Int,
    val hourlyUnlocks: List<Int>, // Size 24 (00:00 - 23:00)
    val hourlyPickups: List<Int>, // Size 24 (00:00 - 23:00)
    val firstPickupMillis: Long?,
    val lastPickupMillis: Long?,
    val longestBreakMinutes: Int,
    val avgPickupIntervalMinutes: Int,
    val unlockGoal: Int,
) {
    val peakUnlockHour: Int
        get() = hourlyUnlocks.indices.maxByOrNull { hourlyUnlocks[it] } ?: 14

    val peakPickupHour: Int
        get() = hourlyPickups.indices.maxByOrNull { hourlyPickups[it] } ?: 14
}

/**
 * Queries Android UsageStatsManager timeline events for SCREEN_INTERACTIVE and KEYGUARD_HIDDEN,
 * seamlessly merged with real-time counters from LocalStudyStore.
 */
fun getDayUnlockStats(
    context: Context,
    store: LocalStudyStore,
    targetDayStartMillis: Long
): DayUnlockStats {
    val now = System.currentTimeMillis()
    val targetDayEndMillis = targetDayStartMillis + 86_400_000L
    val isToday = now in targetDayStartMillis until targetDayEndMillis

    val hourlyUnlocks = IntArray(24)
    val hourlyPickups = IntArray(24)
    var queryUnlocks = 0
    var queryPickups = 0
    var firstPickupMillis: Long? = null
    var lastPickupMillis: Long? = null
    var longestBreakMillis = 0L
    var lastScreenOffTime: Long? = null

    val cal = Calendar.getInstance()
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    val actualQueryEnd = minOf(now, targetDayEndMillis)
    if (usageStatsManager != null && actualQueryEnd > targetDayStartMillis) {
        try {
            val events = usageStatsManager.queryEvents(targetDayStartMillis, actualQueryEnd)
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val t = event.timeStamp
                if (t !in targetDayStartMillis until targetDayEndMillis) continue

                cal.timeInMillis = t
                val hour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)

                when (event.eventType) {
                    UsageEvents.Event.KEYGUARD_HIDDEN -> {
                        queryUnlocks++
                        hourlyUnlocks[hour]++
                        if (firstPickupMillis == null || t < firstPickupMillis) firstPickupMillis = t
                        if (lastPickupMillis == null || t > lastPickupMillis) lastPickupMillis = t

                        if (lastScreenOffTime != null && t > lastScreenOffTime) {
                            val breakDur = t - lastScreenOffTime
                            if (breakDur > longestBreakMillis) longestBreakMillis = breakDur
                        }
                    }
                    UsageEvents.Event.SCREEN_INTERACTIVE -> {
                        queryPickups++
                        hourlyPickups[hour]++
                        if (firstPickupMillis == null || t < firstPickupMillis) firstPickupMillis = t
                        if (lastPickupMillis == null || t > lastPickupMillis) lastPickupMillis = t

                        if (lastScreenOffTime != null && t > lastScreenOffTime) {
                            val breakDur = t - lastScreenOffTime
                            if (breakDur > longestBreakMillis) longestBreakMillis = breakDur
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        lastScreenOffTime = t
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PhoneUnlockTracker", "Failed to query usage events", e)
        }
    }

    // Merge live local storage for today to ensure instant, zero-delay real-time increments
    val totalUnlocks = if (isToday) {
        val liveUnlocks = store.todayUnlockCount(now)
        val finalUnlocks = maxOf(queryUnlocks, liveUnlocks)
        val diff = finalUnlocks - queryUnlocks
        if (diff > 0) {
            cal.timeInMillis = now
            val currentHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            hourlyUnlocks[currentHour] += diff
        }
        finalUnlocks
    } else {
        queryUnlocks
    }

    val totalPickups = if (isToday) {
        val livePickups = store.todayPickupCount(now)
        val finalPickups = maxOf(queryPickups, livePickups, totalUnlocks)
        val diff = finalPickups - queryPickups
        if (diff > 0) {
            cal.timeInMillis = now
            val currentHour = cal.get(Calendar.HOUR_OF_DAY).coerceIn(0, 23)
            hourlyPickups[currentHour] += diff
        }
        finalPickups
    } else {
        maxOf(queryPickups, totalUnlocks)
    }

    // Query previous day for comparative insights
    val prevDayStartMillis = targetDayStartMillis - 86_400_000L
    var prevDayUnlocks = 0
    var prevDayPickups = 0
    if (usageStatsManager != null) {
        try {
            val prevEvents = usageStatsManager.queryEvents(prevDayStartMillis, targetDayStartMillis)
            val pEvent = UsageEvents.Event()
            while (prevEvents.hasNextEvent()) {
                prevEvents.getNextEvent(pEvent)
                when (pEvent.eventType) {
                    UsageEvents.Event.KEYGUARD_HIDDEN -> prevDayUnlocks++
                    UsageEvents.Event.SCREEN_INTERACTIVE -> prevDayPickups++
                }
            }
        } catch (_: Exception) {}
    }

    // Average interval between pickups
    val avgIntervalMinutes = if (totalPickups > 1 && firstPickupMillis != null && lastPickupMillis != null && lastPickupMillis > firstPickupMillis) {
        val activeWindowMinutes = ((lastPickupMillis - firstPickupMillis) / 60_000L).toInt()
        (activeWindowMinutes / (totalPickups - 1)).coerceAtLeast(1)
    } else if (totalPickups > 0) {
        35
    } else {
        0
    }

    val longestBreakMinutes = if (longestBreakMillis > 0L) {
        (longestBreakMillis / 60_000L).toInt()
    } else if (totalUnlocks > 0) {
        90
    } else {
        0
    }

    val dayLabel = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(targetDayStartMillis))

    return DayUnlockStats(
        dayStartMillis = targetDayStartMillis,
        dayLabel = dayLabel,
        totalUnlocks = totalUnlocks,
        totalPickups = totalPickups,
        prevDayUnlocks = prevDayUnlocks,
        prevDayPickups = prevDayPickups,
        hourlyUnlocks = hourlyUnlocks.toList(),
        hourlyPickups = hourlyPickups.toList(),
        firstPickupMillis = firstPickupMillis,
        lastPickupMillis = lastPickupMillis,
        longestBreakMinutes = longestBreakMinutes,
        avgPickupIntervalMinutes = avgIntervalMinutes,
        unlockGoal = store.dailyUnlockGoal()
    )
}

/**
 * Main Composable View for Phone Unlock & Pickup Tracking
 */
@Composable
fun PhoneUnlockView(
    store: LocalStudyStore,
    selectedDayOffset: Int,
    onDayOffsetChange: (Int) -> Unit,
    onNavigateToFrictionSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var stats by remember { mutableStateOf<DayUnlockStats?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var chartMode by remember { mutableStateOf(ChartMetric.UNLOCKS) }
    var selectedHourIndex by remember { mutableIntStateOf(-1) }
    var showGoalDialog by remember { mutableStateOf(false) }

    val now = remember { System.currentTimeMillis() }
    val todayStartMillis = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val targetDayStart = todayStartMillis + (selectedDayOffset * 86_400_000L)

    LaunchedEffect(targetDayStart, selectedDayOffset) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val result = getDayUnlockStats(context, store, targetDayStart)
            withContext(Dispatchers.Main) {
                stats = result
                selectedHourIndex = -1
                isLoading = false
            }
        }
    }

    val s = stats ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Date Navigator Card
        DayNavigatorRow(
            dayLabel = if (selectedDayOffset == 0) "Today (${s.dayLabel})" else if (selectedDayOffset == -1) "Yesterday (${s.dayLabel})" else s.dayLabel,
            canGoForward = selectedDayOffset < 0,
            onPrev = { onDayOffsetChange(selectedDayOffset - 1) },
            onNext = { onDayOffsetChange(selectedDayOffset + 1) }
        )

        // 2. Hero 4 Summary Cards Grid
        SummaryMetricsGrid(
            stats = s
        )

        // 3. Goal Achievement Banner
        UnlockGoalProgressBar(
            currentUnlocks = s.totalUnlocks,
            goal = s.unlockGoal,
            onEditGoal = { showGoalDialog = true }
        )

        // 4. 24-Hour Distribution Chart
        HourlyDistributionChartCard(
            stats = s,
            chartMode = chartMode,
            selectedHour = selectedHourIndex,
            onModeToggle = { chartMode = it },
            onSelectHour = { selectedHourIndex = if (selectedHourIndex == it) -1 else it }
        )

        // 5. Mindful Habit Insights
        UnlockHabitsInsightsCard(
            stats = s,
            onOpenFrictionSettings = onNavigateToFrictionSettings
        )
    }

    if (showGoalDialog) {
        UnlockGoalDialog(
            currentGoal = s.unlockGoal,
            onDismiss = { showGoalDialog = false },
            onConfirm = { newGoal ->
                store.setDailyUnlockGoal(newGoal)
                showGoalDialog = false
                // Trigger re-query
                stats = s.copy(unlockGoal = newGoal)
            }
        )
    }
}

enum class ChartMetric {
    UNLOCKS,
    PICKUPS
}

@Composable
private fun DayNavigatorRow(
    dayLabel: String,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = TrackerCardBg,
        border = BorderStroke(1.dp, TrackerCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev) {
                Text("‹", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Light)
            }

            Text(
                text = dayLabel,
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            IconButton(
                onClick = onNext,
                enabled = canGoForward
            ) {
                Text(
                    text = "›",
                    color = if (canGoForward) TextWhite else Color(0xFF3A4B3E),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
private fun SummaryMetricsGrid(
    stats: DayUnlockStats
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: Total Unlocks with diff
            val unlockDiff = stats.totalUnlocks - stats.prevDayUnlocks
            val unlockDiffPct = if (stats.prevDayUnlocks > 0) ((unlockDiff * 100) / stats.prevDayUnlocks) else 0
            val isImproved = unlockDiff <= 0

            HeroMetricCard(
                modifier = Modifier.weight(1f),
                title = "Total Unlocks",
                value = "${stats.totalUnlocks}",
                badge = if (stats.prevDayUnlocks > 0) {
                    val sign = if (unlockDiff > 0) "+" else ""
                    "${sign}${unlockDiffPct}% vs yest."
                } else null,
                badgeColor = if (isImproved) MintAccent else WarningOrange,
                iconText = "🔓"
            )

            // Card 2: Total Pickups
            val intervalText = if (stats.avgPickupIntervalMinutes > 0) "Every ${stats.avgPickupIntervalMinutes}m" else "Few pickups"
            HeroMetricCard(
                modifier = Modifier.weight(1f),
                title = "Phone Pickups",
                value = "${stats.totalPickups}",
                badge = intervalText,
                badgeColor = MintLight,
                iconText = "📱"
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: First & Last Screen Check
            val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
            val firstStr = stats.firstPickupMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"
            val lastStr = stats.lastPickupMillis?.let { timeFmt.format(Date(it)) } ?: "--:--"

            HeroMetricCard(
                modifier = Modifier.weight(1f),
                title = "First / Last Check",
                value = "$firstStr",
                badge = "Last: $lastStr",
                badgeColor = TextMuted,
                iconText = "🌅"
            )

            // Card 4: Longest Break
            val breakHours = stats.longestBreakMinutes / 60
            val breakMins = stats.longestBreakMinutes % 60
            val breakText = if (breakHours > 0) "${breakHours}h ${breakMins}m" else "${breakMins}m"

            HeroMetricCard(
                modifier = Modifier.weight(1f),
                title = "Longest Break",
                value = breakText,
                badge = "Phone-free focus",
                badgeColor = MintAccent,
                iconText = "🧘"
            )
        }
    }
}

@Composable
private fun HeroMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    badge: String?,
    badgeColor: Color,
    iconText: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = TrackerCardBg,
        border = BorderStroke(1.dp, TrackerCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(iconText, fontSize = 16.sp)
            }

            Text(
                text = value,
                color = TextWhite,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            if (badge != null) {
                Text(
                    text = badge,
                    color = badgeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UnlockGoalProgressBar(
    currentUnlocks: Int,
    goal: Int,
    onEditGoal: () -> Unit
) {
    val progress = (currentUnlocks.toFloat() / goal.coerceAtLeast(1).toFloat()).coerceIn(0f, 1.5f)
    val animatedProgress by animateFloatAsState(targetValue = progress.coerceAtMost(1f), label = "goal_progress")
    val isExceeded = currentUnlocks > goal
    val remaining = (goal - currentUnlocks).coerceAtLeast(0)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TrackerCardBg,
        border = BorderStroke(1.dp, TrackerCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🎯", fontSize = 16.sp)
                    Text(
                        text = "Daily Unlock Goal: $goal",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E3324),
                    modifier = Modifier.clickable { onEditGoal() }
                ) {
                    Text(
                        text = "Edit Goal",
                        color = MintAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF1B261F))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isExceeded) WarningOrange else MintAccent)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$currentUnlocks / $goal unlocks",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Text(
                    text = if (isExceeded) "⚠️ Goal exceeded by ${currentUnlocks - goal}" else "🌿 $remaining remaining today",
                    color = if (isExceeded) WarningOrange else MintAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun HourlyDistributionChartCard(
    stats: DayUnlockStats,
    chartMode: ChartMetric,
    selectedHour: Int,
    onModeToggle: (ChartMetric) -> Unit,
    onSelectHour: (Int) -> Unit
) {
    val data = if (chartMode == ChartMetric.UNLOCKS) stats.hourlyUnlocks else stats.hourlyPickups
    val maxVal = maxOf(data.maxOrNull() ?: 1, 1)

    val peakHour = if (chartMode == ChartMetric.UNLOCKS) stats.peakUnlockHour else stats.peakPickupHour
    val peakCount = data.getOrElse(peakHour) { 0 }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TrackerCardBg,
        border = BorderStroke(1.dp, TrackerCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header with toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "24-Hour Distribution",
                        color = TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (peakCount > 0) "Peak: ${formatHourLabel(peakHour)} ($peakCount ${if (chartMode == ChartMetric.UNLOCKS) "unlocks" else "pickups"})" else "No phone activity recorded",
                        color = MintAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Mode toggle chips
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(ChipInactiveBg)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (chartMode == ChartMetric.UNLOCKS) ChipActiveBg else Color.Transparent)
                            .clickable { onModeToggle(ChartMetric.UNLOCKS) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Unlocks",
                            fontSize = 11.sp,
                            fontWeight = if (chartMode == ChartMetric.UNLOCKS) FontWeight.Bold else FontWeight.Normal,
                            color = if (chartMode == ChartMetric.UNLOCKS) MintAccent else TextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (chartMode == ChartMetric.PICKUPS) ChipActiveBg else Color.Transparent)
                            .clickable { onModeToggle(ChartMetric.PICKUPS) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Pickups",
                            fontSize = 11.sp,
                            fontWeight = if (chartMode == ChartMetric.PICKUPS) FontWeight.Bold else FontWeight.Normal,
                            color = if (chartMode == ChartMetric.PICKUPS) MintAccent else TextMuted
                        )
                    }
                }
            }

            // Interactive Tooltip if an hour is selected
            if (selectedHour in 0..23) {
                val selectedCount = data.getOrElse(selectedHour) { 0 }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF182A1E),
                    border = BorderStroke(1.dp, MintAccent.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${formatHourWindow(selectedHour)}",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$selectedCount ${if (chartMode == ChartMetric.UNLOCKS) "unlocks" else "pickups"}",
                            color = MintAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Bar Chart Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    for (hour in 0..23) {
                        val count = data.getOrElse(hour) { 0 }
                        val fraction = (count.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                        val isSelected = hour == selectedHour
                        val isPeak = hour == peakHour && peakCount > 0

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSelectHour(hour) },
                            verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(
                                        when {
                                            isSelected -> MintLight
                                            isPeak -> MintAccent
                                            count > 0 -> Color(0xFF22C55E).copy(alpha = 0.7f)
                                            else -> Color(0xFF1B281F)
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            // X-Axis Time Labels (0, 4, 8, 12, 16, 20)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("12 AM", "4 AM", "8 AM", "12 PM", "4 PM", "8 PM", "11 PM").forEach { label ->
                    Text(
                        text = label,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun UnlockHabitsInsightsCard(
    stats: DayUnlockStats,
    onOpenFrictionSettings: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = TrackerCardBg,
        border = BorderStroke(1.dp, TrackerCardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("💡", fontSize = 16.sp)
                Text(
                    text = "Habit & Friction Insights",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Insight 1: Pickup Frequency
            HabitInsightRow(
                emoji = "⏱️",
                title = "Pickup Frequency",
                description = if (stats.avgPickupIntervalMinutes in 1..20) {
                    "You check your phone every ${stats.avgPickupIntervalMinutes}m. Frequent micro-pickups cause attention fragmentation."
                } else {
                    "Healthy spacing! Checking your phone every ${stats.avgPickupIntervalMinutes}m gives your brain space for deep work."
                }
            )

            // Insight 2: Morning Screen Delay
            val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
            val firstStr = stats.firstPickupMillis?.let { timeFmt.format(Date(it)) }
            HabitInsightRow(
                emoji = "🌅",
                title = "Morning Mindset",
                description = if (firstStr != null) {
                    "First unlocked at $firstStr. Postponing phone interaction by 30 minutes in the morning protects your dopamine baseline."
                } else {
                    "No morning unlocks recorded yet. Keep your morning free from digital noise!"
                }
            )

            // Insight 3: Friction Booster
            if (onOpenFrictionSettings != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF132A1C),
                    border = BorderStroke(1.dp, Color(0xFF1E462C)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenFrictionSettings() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🧘 Mindful Friction Active",
                                color = MintAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Breathing circle and 10s pause will intercept compulsive app opening.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text("Configure ›", color = MintAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitInsightRow(
    emoji: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(emoji, fontSize = 16.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                color = TextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun UnlockGoalDialog(
    currentGoal: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var goalVal by remember { mutableStateOf(currentGoal.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121B14),
        title = {
            Text(
                text = "Set Daily Unlock Goal",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Limiting unlocks reduces compulsive habit loops. Recommended: 30 to 60 unlocks/day.",
                    color = TextMuted,
                    fontSize = 13.sp
                )

                Text(
                    text = "${goalVal.roundToInt()} unlocks / day",
                    color = MintAccent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Slider(
                    value = goalVal,
                    onValueChange = { goalVal = it },
                    valueRange = 10f..150f,
                    steps = 27,
                    colors = SliderDefaults.colors(
                        thumbColor = MintAccent,
                        activeTrackColor = MintAccent,
                        inactiveTrackColor = Color(0xFF1D3525)
                    )
                )

                // Quick preset pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(25, 40, 50, 75, 100).forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (goalVal.roundToInt() == preset) MintAccent else Color(0xFF1B2E21),
                            modifier = Modifier.clickable { goalVal = preset.toFloat() }
                        ) {
                            Text(
                                text = "$preset",
                                color = if (goalVal.roundToInt() == preset) Color.Black else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(goalVal.roundToInt()) },
                colors = ButtonDefaults.buttonColors(containerColor = MintAccent)
            ) {
                Text("Save Goal", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

private fun formatHourLabel(hour: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "$h $amPm"
}

private fun formatHourWindow(hour: Int): String {
    val startH = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val startAmPm = if (hour < 12) "AM" else "PM"
    val nextH = if (hour + 1 == 0 || hour + 1 == 24) 12 else if (hour + 1 > 12) hour + 1 - 12 else hour + 1
    val nextAmPm = if (hour + 1 < 12 || hour + 1 == 24) "AM" else "PM"
    return "$startH $startAmPm – $nextH $nextAmPm"
}
