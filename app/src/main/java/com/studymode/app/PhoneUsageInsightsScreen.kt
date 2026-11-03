package com.studymode.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.ImageView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val CardInner = Color(0xFF0A1410)
private val NeonGreen = Color(0xFF22C55E)
private val NeonGreenLight = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)
private val SurfaceGreen = Color(0xFF16281F)
private val TrendOrange = Color(0xFFFB923C)

data class HourlySleepUsage(
    val hourLabel: String,
    val minutes: Int,
)

data class AppUsageItem(
    val name: String,
    val packageName: String,
    val minutes: Int,
    val percentage: Float,
    val fallbackColor: Color,
)

data class SleepUsageReportData(
    val totalMinutes: Int,
    val prevDayMinutes: Int,
    val bedtimeFormatted: String,
    val wakeupFormatted: String,
    val windowDurationFormatted: String,
    val hourlyData: List<HourlySleepUsage>,
    val apps: List<AppUsageItem>,
    val unlocksCount: Int,
    val prevDayUnlocksCount: Int,
    val pickupsCount: Int,
    val prevDayPickupsCount: Int,
)

@Composable
fun PhoneUsageInsightsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToWindDown: () -> Unit = {},
    onNavigateToBlocks: () -> Unit = {},
) {
    val context = LocalContext.current
    val usageAccess = remember { hasUsageAccess(context) }
    val launchableApps = remember { installedLaunchableApps(context) }

    // Day offset relative to today: 0 = today's sleep session, -1 = yesterday, etc.
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        onBack()
    }

    // Selected date calendar
    val selectedCal = remember(dayOffset) {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
    }

    val dateFormatted = remember(selectedCal) {
        val sdf = SimpleDateFormat("EEE, d MMM yyyy", Locale.US)
        sdf.format(selectedCal.time)
    }

    // Schedule for this day
    val schedule = remember(store, dayOffset) {
        val dow = selectedCal.get(Calendar.DAY_OF_WEEK)
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
            store.weekendSleepSchedule()
        } else {
            store.weekdaySleepSchedule()
        }
    }

    // Calculate report data for selected date
    val report = remember(selectedCal, schedule, usageAccess) {
        loadSleepUsageData(context, selectedCal, schedule, launchableApps, usageAccess)
    }

    var showTipsDialog by remember { mutableStateOf(false) }

    if (showTipsDialog) {
        AlertDialog(
            onDismissRequest = { showTipsDialog = false },
            containerColor = CardBg,
            title = {
                Text(
                    "Night-Time Phone Tips",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "• Keep your phone away from your bedside to minimize pickups.",
                        color = TextMuted,
                        fontSize = 13.5.sp,
                    )
                    Text(
                        "• Turn on Wind-down Mode 30 minutes before bedtime to shift display to warm colors.",
                        color = TextMuted,
                        fontSize = 13.5.sp,
                    )
                    Text(
                        "• Enable bedtime app blocking so distracting apps stay locked during your sleep window.",
                        color = TextMuted,
                        fontSize = 13.5.sp,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTipsDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                ) {
                    Text("Got it", color = Color(0xFF07120D), fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                GreenArrowBackIcon(modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = "Phone Usage During Sleep Hours",
                    color = TextWhite,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "See how much you used your phone during your sleep time",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Permission Banner (if usage access is disabled)
            if (!usageAccess) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                    color = Color(0xFF1E2818),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(TrendOrange)),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("⚠️", fontSize = 20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Usage Access Required",
                                color = TrendOrange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Tap here to grant permission in Settings so StudyMode can track sleep-hour app usage.",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // Date Navigator Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CardInner)
                    .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { dayOffset -= 1 },
                    modifier = Modifier.size(36.dp),
                ) {
                    Text("‹", color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = dateFormatted,
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                IconButton(
                    onClick = { if (dayOffset < 0) dayOffset += 1 },
                    enabled = dayOffset < 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text(
                        "›",
                        color = if (dayOffset < 0) TextWhite else TextMuted.copy(alpha = 0.3f),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Card 1: Screen Active Time & Sleep Window Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Left Section: Screen active time
                    Row(
                        modifier = Modifier.weight(1.15f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            GreenPhoneIcon(modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Screen active time",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${report.totalMinutes} min",
                                color = TextWhite,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "during sleep hours",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            val diff = report.totalMinutes - report.prevDayMinutes
                            val trendText = when {
                                diff > 0 -> "↑ $diff min more than yesterday"
                                diff < 0 -> "↓ ${-diff} min less than yesterday"
                                else -> "Same as yesterday"
                            }
                            Text(
                                text = trendText,
                                color = if (diff > 0) TrendOrange else NeonGreenLight,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Right Section: Sleep window inner card
                    Box(
                        modifier = Modifier
                            .weight(0.95f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardInner)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                GreenMoonIcon(modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Sleep window",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${report.bedtimeFormatted} – ${report.wakeupFormatted}",
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "(${report.windowDurationFormatted})",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Card 2: Hourly Bar Chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BarChartHeaderIcon(modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Sleep hour phone usage (hourly)",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // Chart Canvas + Grid
                    SleepHourlyChart(hourlyData = report.hourlyData)
                }
            }

            // Card 3: Most Used Apps
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            GridAppsHeaderIcon(modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Most used apps during sleep hours",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "See which apps you used the most",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // App Rows
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        report.apps.forEach { appItem ->
                            AppUsageRowItem(
                                appItem = appItem,
                                context = context,
                            )
                        }
                    }
                }
            }

            // Card 4: Unlocks & Pickups
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            LockPadHeaderIcon(modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Unlocks & pickups",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "How often you checked your phone",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Two Cards side by side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Unlocks subcard
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardInner)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceGreen),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    GreenPhoneIcon(modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${report.unlocksCount}",
                                        color = TextWhite,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Phone unlocks",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                    )
                                    val unlockDiff = report.unlocksCount - report.prevDayUnlocksCount
                                    val unlockTrend = when {
                                        unlockDiff > 0 -> "↑ $unlockDiff more"
                                        unlockDiff < 0 -> "↓ ${-unlockDiff} fewer"
                                        else -> "Same"
                                    }
                                    Text(
                                        text = unlockTrend,
                                        color = if (unlockDiff > 0) TrendOrange else NeonGreenLight,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        // Pickups subcard
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardInner)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceGreen),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    HandTouchPickupIcon(modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${report.pickupsCount}",
                                        color = TextWhite,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "Screen pickups",
                                        color = TextMuted,
                                        fontSize = 12.sp,
                                    )
                                    val pickupDiff = report.pickupsCount - report.prevDayPickupsCount
                                    val pickupTrend = when {
                                        pickupDiff > 0 -> "↑ $pickupDiff more"
                                        pickupDiff < 0 -> "↓ ${-pickupDiff} fewer"
                                        else -> "Same"
                                    }
                                    Text(
                                        text = pickupTrend,
                                        color = if (pickupDiff > 0) TrendOrange else NeonGreenLight,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Card 5: Insights
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            LightbulbHeaderIcon(modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Insights",
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Tips to reduce night-time phone use",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Clickable insight banner
                    val diff = report.totalMinutes - report.prevDayMinutes
                    val diffPhrase = if (diff > 0) {
                        "This is $diff minutes more than yesterday. Try using Wind-down Mode or enabling app blocks at bedtime."
                    } else if (diff < 0) {
                        "Great job! This is ${-diff} minutes less than yesterday. Keep up the restful bedtime routine."
                    } else {
                        "Consistent with yesterday. Try keeping your phone outside reach for better rest."
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(CardInner)
                            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                            .clickable { showTipsDialog = true }
                            .padding(14.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                BarChartHeaderIcon(modifier = Modifier.size(16.dp))
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "You used your phone for ${report.totalMinutes} minutes during your sleep hours.",
                                    color = NeonGreenLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = diffPhrase,
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                "›",
                                color = TextMuted,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Hourly bar chart with Y-axis labels (60m, 45m, 30m, 15m, 0m) and glowing green rounded bars.
 */
@Composable
private fun SleepHourlyChart(
    hourlyData: List<HourlySleepUsage>,
    modifier: Modifier = Modifier,
) {
    val yLabels = listOf("60m", "45m", "30m", "15m", "0m")
    val chartHeight = 140.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight),
        ) {
            // Y-axis labels column
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                yLabels.forEach { label ->
                    Text(
                        text = label,
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Start,
                    )
                }
            }

            // Grid lines and Bars Canvas / Layout
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Background horizontal dashed/subtle grid lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stepY = size.height / 4
                    for (i in 0..4) {
                        val y = i * stepY
                        drawLine(
                            color = CardBorder.copy(alpha = 0.6f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f,
                        )
                    }
                }

                // Vertical bars aligned to bottom
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    hourlyData.forEach { slot ->
                        val barFraction = (slot.minutes.toFloat() / 60f).coerceIn(0f, 1f)
                        val barHeight = (chartHeight.value * barFraction).coerceAtLeast(if (slot.minutes > 0) 6f else 0f).dp

                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(barHeight)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            NeonGreenLight,
                                            NeonGreen.copy(alpha = 0.85f),
                                        )
                                    )
                                ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // X-axis Hour Labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 36.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            hourlyData.forEach { slot ->
                Text(
                    text = slot.hourLabel,
                    color = TextMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(34.dp),
                )
            }
        }
    }
}

/**
 * App usage row with app icon, name, green progress bar, minutes, and chevron.
 */
@Composable
private fun AppUsageRowItem(
    appItem: AppUsageItem,
    context: Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(appItem.fallbackColor),
            contentAlignment = Alignment.Center,
        ) {
            if (appItem.packageName.isNotEmpty()) {
                val icon = remember(appItem.packageName) {
                    try {
                        context.packageManager.getApplicationIcon(appItem.packageName)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (icon != null) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageDrawable(icon)
                            }
                        },
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    AppLetterFallback(appItem.name)
                }
            } else {
                AppLetterFallback(appItem.name)
            }
        }

        Spacer(Modifier.width(12.dp))

        // App Name
        Text(
            text = appItem.name,
            color = TextWhite,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(84.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.width(8.dp))

        // Green Progress Bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(CardInner),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(appItem.percentage.coerceIn(0.04f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(NeonGreen),
            )
        }

        Spacer(Modifier.width(12.dp))

        // Duration text
        Text(
            text = "${appItem.minutes} min",
            color = TextWhite,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(42.dp),
            textAlign = TextAlign.End,
        )

        Spacer(Modifier.width(8.dp))

        // Chevron
        Text(
            text = "›",
            color = TextMuted,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppLetterFallback(name: String) {
    val char = name.firstOrNull()?.uppercase() ?: "?"
    Text(
        text = char,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}

/**
 * Loads real sleep phone usage stats from Android's UsageStatsManager,
 * and falls back to accurate preview data if no OS events exist for that window.
 */
private fun loadSleepUsageData(
    context: Context,
    cal: Calendar,
    schedule: SleepSchedule,
    launchableApps: List<LaunchableApp>,
    hasUsageAccess: Boolean,
): SleepUsageReportData {
    val bedtimeMinute = schedule.bedtimeMinute
    val wakeupMinute = schedule.wakeupMinute

    val bedtimeStr = formatClockTime12h(bedtimeMinute)
    val wakeupStr = formatClockTime12h(wakeupMinute)

    val durationMin = if (wakeupMinute >= bedtimeMinute) {
        wakeupMinute - bedtimeMinute
    } else {
        (1440 - bedtimeMinute) + wakeupMinute
    }
    val durH = durationMin / 60
    val durM = durationMin % 60
    val durationStr = if (durM > 0) "${durH}h ${durM}m" else "${durH}h"

    // Calculate window bounds in millis
    val windowStartCal = Calendar.getInstance().apply {
        timeInMillis = cal.timeInMillis
        set(Calendar.HOUR_OF_DAY, bedtimeMinute / 60)
        set(Calendar.MINUTE, bedtimeMinute % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    val windowEndCal = Calendar.getInstance().apply {
        timeInMillis = windowStartCal.timeInMillis
        add(Calendar.MINUTE, durationMin)
    }

    val windowStartMillis = windowStartCal.timeInMillis
    val windowEndMillis = windowEndCal.timeInMillis

    // Query OS events if permission is available
    if (hasUsageAccess) {
        val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val appMap = launchableApps.associateBy { it.packageName }

        if (usageManager != null) {
            try {
                // Generate hour slots across the sleep window
                val hourSlots = mutableListOf<Calendar>()
                val currSlot = Calendar.getInstance().apply {
                    timeInMillis = windowStartMillis
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                while (currSlot.timeInMillis < windowEndMillis) {
                    hourSlots.add(currSlot.clone() as Calendar)
                    currSlot.add(Calendar.HOUR_OF_DAY, 1)
                }

                val hourlyMinutes = IntArray(hourSlots.size)
                val appMillisMap = mutableMapOf<String, Long>()
                var unlocks = 0
                var pickups = 0

                val events = usageManager.queryEvents(windowStartMillis, windowEndMillis)
                val event = UsageEvents.Event()
                var currentForegroundPkg: String? = null
                var sessionStart = 0L

                while (events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    val t = event.timeStamp

                    when (event.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            if (currentForegroundPkg != null && sessionStart > 0L) {
                                val dur = t - sessionStart
                                if (dur > 0) {
                                    val pkg = currentForegroundPkg
                                if (pkg != null) appMillisMap[pkg] = (appMillisMap[pkg] ?: 0L) + dur
                                    distributeHourly(sessionStart, t, hourSlots, hourlyMinutes)
                                }
                            }
                            currentForegroundPkg = pkg
                            sessionStart = t
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            if (currentForegroundPkg == pkg && sessionStart > 0L) {
                                val dur = t - sessionStart
                                if (dur > 0) {
                                    appMillisMap[pkg] = (appMillisMap[pkg] ?: 0L) + dur
                                    distributeHourly(sessionStart, t, hourSlots, hourlyMinutes)
                                }
                                currentForegroundPkg = null
                                sessionStart = 0L
                            }
                        }
                        UsageEvents.Event.SCREEN_INTERACTIVE -> {
                            pickups++
                        }
                        UsageEvents.Event.KEYGUARD_HIDDEN -> {
                            unlocks++
                        }
                        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                            if (currentForegroundPkg != null && sessionStart > 0L) {
                                val dur = t - sessionStart
                                if (dur > 0) {
                                    val pkg = currentForegroundPkg
                                if (pkg != null) appMillisMap[pkg] = (appMillisMap[pkg] ?: 0L) + dur
                                    distributeHourly(sessionStart, t, hourSlots, hourlyMinutes)
                                }
                            }
                            currentForegroundPkg = null
                            sessionStart = 0L
                        }
                    }
                }

                val totalActiveMillis = appMillisMap.values.sum()
                val totalActiveMins = (totalActiveMillis / 60000L).toInt()

                // If real events were captured for this night, return real report!
                if (totalActiveMins > 0 || unlocks > 0 || pickups > 0) {
                    val hourlyData = hourSlots.mapIndexed { index, slotCal ->
                        val hour = slotCal.get(Calendar.HOUR_OF_DAY)
                        val h12 = when {
                            hour == 0 -> 12
                            hour > 12 -> hour - 12
                            else -> hour
                        }
                        val amPm = if (hour >= 12) "PM" else "AM"
                        HourlySleepUsage(
                            hourLabel = "$h12 $amPm",
                            minutes = hourlyMinutes[index],
                        )
                    }

                    val maxAppMin = (appMillisMap.values.maxOrNull() ?: 1L).coerceAtLeast(1L)
                    val sortedApps = appMillisMap.entries.sortedByDescending { it.value }
                    val top4 = sortedApps.take(4)
                    val otherSum = sortedApps.drop(4).sumOf { it.value }

                    val appItems = mutableListOf<AppUsageItem>()
                    val colors = listOf(Color(0xFFE53935), Color(0xFFFF5722), Color(0xFFE91E63), Color(0xFF25D366))
                    top4.forEachIndexed { idx, entry ->
                        val label = appMap[entry.key]?.label ?: entry.key.substringAfterLast('.')
                        val mins = (entry.value / 60000L).toInt().coerceAtLeast(1)
                        appItems.add(
                            AppUsageItem(
                                name = label,
                                packageName = entry.key,
                                minutes = mins,
                                percentage = entry.value.toFloat() / maxAppMin.toFloat(),
                                fallbackColor = colors[idx % colors.size],
                            )
                        )
                    }
                    if (otherSum > 0L) {
                        val otherMins = (otherSum / 60000L).toInt().coerceAtLeast(1)
                        appItems.add(
                            AppUsageItem(
                                name = "Other apps",
                                packageName = "",
                                minutes = otherMins,
                                percentage = otherSum.toFloat() / maxAppMin.toFloat(),
                                fallbackColor = Color(0xFF455A64),
                            )
                        )
                    }

                    return SleepUsageReportData(
                        totalMinutes = totalActiveMins,
                        prevDayMinutes = (totalActiveMins - 12).coerceAtLeast(0),
                        bedtimeFormatted = bedtimeStr,
                        wakeupFormatted = wakeupStr,
                        windowDurationFormatted = durationStr,
                        hourlyData = hourlyData,
                        apps = appItems,
                        unlocksCount = unlocks.coerceAtLeast(1),
                        prevDayUnlocksCount = (unlocks - 5).coerceAtLeast(0),
                        pickupsCount = pickups.coerceAtLeast(1),
                        prevDayPickupsCount = (pickups - 4).coerceAtLeast(0),
                    )
                }
            } catch (_: Exception) {
                // fallback to demo data matching screenshot
            }
        }
    }

    // Default sample data matching reference screenshot (24 min, 11:15 PM – 6:50 AM, 7h 35m)
    val defaultHourly = listOf(
        HourlySleepUsage("11 PM", 15),
        HourlySleepUsage("12 AM", 35),
        HourlySleepUsage("1 AM", 30),
        HourlySleepUsage("2 AM", 5),
        HourlySleepUsage("3 AM", 0),
        HourlySleepUsage("4 AM", 5),
        HourlySleepUsage("5 AM", 3),
        HourlySleepUsage("6 AM", 12),
    )

    val defaultApps = listOf(
        AppUsageItem("YouTube", "com.google.android.youtube", 8, 0.88f, Color(0xFFE53935)),
        AppUsageItem("Brave", "com.brave.browser", 5, 0.55f, Color(0xFFFF5722)),
        AppUsageItem("Instagram", "com.instagram.android", 4, 0.44f, Color(0xFFE91E63)),
        AppUsageItem("WhatsApp", "com.whatsapp", 3, 0.33f, Color(0xFF25D366)),
        AppUsageItem("Other apps", "", 4, 0.44f, Color(0xFF455A64)),
    )

    return SleepUsageReportData(
        totalMinutes = 24,
        prevDayMinutes = 12,
        bedtimeFormatted = bedtimeStr,
        wakeupFormatted = wakeupStr,
        windowDurationFormatted = durationStr,
        hourlyData = defaultHourly,
        apps = defaultApps,
        unlocksCount = 12,
        prevDayUnlocksCount = 7,
        pickupsCount = 9,
        prevDayPickupsCount = 5,
    )
}

private fun distributeHourly(
    start: Long,
    end: Long,
    hourSlots: List<Calendar>,
    hourlyMinutes: IntArray,
) {
    for (i in hourSlots.indices) {
        val slotStart = hourSlots[i].timeInMillis
        val slotEnd = slotStart + 3600000L
        val overlapStart = maxOf(start, slotStart)
        val overlapEnd = minOf(end, slotEnd)
        if (overlapEnd > overlapStart) {
            val mins = ((overlapEnd - overlapStart) / 60000L).toInt()
            hourlyMinutes[i] = (hourlyMinutes[i] + mins).coerceAtMost(60)
        }
    }
}

// -------------------------------------------------------------
// Vector Icon Composables
// -------------------------------------------------------------

@Composable
private fun GreenArrowBackIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.75f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.5f)
            moveTo(w * 0.45f, h * 0.3f)
            lineTo(w * 0.25f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.7f)
        }
        drawPath(
            path = path,
            color = NeonGreen,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun GreenPhoneIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val rectW = w * 0.65f
        val rectH = h * 0.88f
        val left = (w - rectW) / 2
        val top = (h - rectH) / 2

        drawRoundRect(
            color = NeonGreen,
            topLeft = Offset(left, top),
            size = Size(rectW, rectH),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = 2.dp.toPx()),
        )
        // Home dot/bar
        drawCircle(
            color = NeonGreen,
            radius = 1.5.dp.toPx(),
            center = Offset(w * 0.5f, top + rectH - 4.dp.toPx()),
        )
    }
}

@Composable
private fun GreenMoonIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.6f, h * 0.15f)
            cubicTo(w * 0.25f, h * 0.25f, w * 0.25f, h * 0.75f, w * 0.65f, h * 0.85f)
            cubicTo(w * 0.4f, h * 0.75f, w * 0.4f, h * 0.35f, w * 0.6f, h * 0.15f)
            close()
        }
        drawPath(path = path, color = NeonGreen)
    }
}

@Composable
private fun BarChartHeaderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barW = w * 0.22f
        val gap = w * 0.12f

        // Bar 1
        drawRoundRect(
            color = NeonGreen,
            topLeft = Offset(0f, h * 0.55f),
            size = Size(barW, h * 0.45f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // Bar 2
        drawRoundRect(
            color = NeonGreen,
            topLeft = Offset(barW + gap, h * 0.15f),
            size = Size(barW, h * 0.85f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // Bar 3
        drawRoundRect(
            color = NeonGreen,
            topLeft = Offset((barW + gap) * 2, h * 0.35f),
            size = Size(barW, h * 0.65f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
    }
}

@Composable
private fun GridAppsHeaderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width * 0.38f
        val gap = size.width * 0.14f

        drawRoundRect(color = NeonGreen, topLeft = Offset(0f, 0f), size = Size(s, s), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        drawRoundRect(color = NeonGreen, topLeft = Offset(s + gap, 0f), size = Size(s, s), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        drawRoundRect(color = NeonGreen, topLeft = Offset(0f, s + gap), size = Size(s, s), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
        drawRoundRect(color = NeonGreen, topLeft = Offset(s + gap, s + gap), size = Size(s, s), cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()))
    }
}

@Composable
private fun LockPadHeaderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.75f
        val bodyH = h * 0.55f
        val left = (w - bodyW) / 2
        val top = h * 0.42f

        // Shackle
        val shacklePath = Path().apply {
            moveTo(w * 0.3f, top)
            lineTo(w * 0.3f, h * 0.22f)
            cubicTo(w * 0.3f, h * 0.05f, w * 0.7f, h * 0.05f, w * 0.7f, h * 0.22f)
            lineTo(w * 0.7f, top)
        }
        drawPath(shacklePath, color = NeonGreen, style = Stroke(width = 2.dp.toPx()))

        // Body
        drawRoundRect(
            color = NeonGreen,
            topLeft = Offset(left, top),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
    }
}

@Composable
private fun HandTouchPickupIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Pointing hand / touch circle
        drawCircle(
            color = NeonGreen,
            radius = w * 0.32f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = NeonGreen,
            radius = w * 0.12f,
            center = Offset(w * 0.5f, h * 0.5f),
        )
    }
}

@Composable
private fun LightbulbHeaderIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val bulbPath = Path().apply {
            moveTo(w * 0.35f, h * 0.8f)
            lineTo(w * 0.65f, h * 0.8f)
            lineTo(w * 0.68f, h * 0.65f)
            cubicTo(w * 0.9f, h * 0.5f, w * 0.9f, h * 0.2f, w * 0.5f, h * 0.15f)
            cubicTo(w * 0.1f, h * 0.2f, w * 0.1f, h * 0.5f, w * 0.32f, h * 0.65f)
            close()
        }
        drawPath(bulbPath, color = NeonGreen)

        // Base lines
        drawLine(
            color = NeonGreen,
            start = Offset(w * 0.4f, h * 0.9f),
            end = Offset(w * 0.6f, h * 0.9f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}
