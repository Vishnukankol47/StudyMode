package com.studymode.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// StudyMode Sleep Theme Colors (Matching exact screenshot resource)
private val SleepBg = Color(0xFF090D0B)
private val SleepCard = Color(0xFF131915)
private val SleepCardBorder = Color(0xFF1A221C)
private val SleepElevated = Color(0xFF18201A)
private val SleepGreen = Color(0xFF4ADE80)
private val SleepGreenDark = Color(0xFF1C2720)
private val SleepSoftGreen = Color(0xFF1C2720)
private val SleepText = Color(0xFFFFFFFF)
private val SleepMuted = Color(0xFF8E9993)
private val SleepChevron = Color(0xFF6B7570)
private val SleepDivider = Color(0xFF1E2822)
private val SleepWarning = Color(0xFFFF7777)
private val SleepPurple = Color(0xFFA98AFF)

private val DAYS_OF_WEEK_ORDER = listOf(
    Calendar.SUNDAY,
    Calendar.MONDAY,
    Calendar.TUESDAY,
    Calendar.WEDNESDAY,
    Calendar.THURSDAY,
    Calendar.FRIDAY,
    Calendar.SATURDAY,
)

@Composable
fun SleepScreen(
    store: LocalStudyStore,
    onBack: () -> Unit = {},
    onNavigateToBlocks: () -> Unit = {},
) {
    val context = LocalContext.current
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var schedule by remember(refreshKey) {
        mutableStateOf(
            runCatching { store.sleepSchedule() }.getOrElse {
                SleepSchedule(
                    id = 1L,
                    bedtimeMinute = 23 * 60,
                    wakeupMinute = 6 * 60 + 50,
                    activeDays = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY),
                    targetDurationMinutes = 8 * 60,
                )
            }
        )
    }
    val todayLog = remember(refreshKey) { runCatching { store.todaySleepLog() }.getOrNull() }
    val last7Nights = remember(refreshKey) { runCatching { store.last7NightsSleepLogs() }.getOrElse { emptyList() } }
    val usageAccess = remember(refreshKey) { runCatching { hasUsageAccess(context) }.getOrDefault(false) }
    val apps = remember { runCatching { installedLaunchableApps(context) }.getOrElse { emptyList() } }

    // Dialog states
    var showScheduleScreen by rememberSaveable { mutableStateOf(false) }

    if (showScheduleScreen) {
        SleepScheduleScreen(
            store = store,
            onBack = { showScheduleScreen = false },
            onSaved = {
                showScheduleScreen = false
                schedule = store.sleepSchedule()
                refreshKey += 1
            },
        )
        return
    }

    var showPhoneUsageScreen by rememberSaveable { mutableStateOf(false) }

    if (showPhoneUsageScreen) {
        PhoneUsageInsightsScreen(
            store = store,
            onBack = { showPhoneUsageScreen = false },
        )
        return
    }

    var showWindDownScreen by rememberSaveable { mutableStateOf(false) }
    var windDownInitialDest by remember { mutableStateOf(WindDownDestination.MAIN) }

    if (showWindDownScreen) {
        WindDownCoordinatorScreen(
            store = store,
            initialDestination = windDownInitialDest,
            onBackToSleep = { showWindDownScreen = false },
            onNavigateToBlocks = onNavigateToBlocks,
        )
        return
    }

    var showSleepSettingsScreen by rememberSaveable { mutableStateOf(false) }
    var sleepSettingsInitialPanel by remember { mutableStateOf(SleepSettingsPanel.HUB) }

    if (showSleepSettingsScreen) {
        SleepSettingsCoordinatorScreen(
            store = store,
            initialPanel = sleepSettingsInitialPanel,
            onBack = {
                showSleepSettingsScreen = false
                schedule = store.sleepSchedule()
                refreshKey += 1
            },
            onNavigateToBlocks = onNavigateToBlocks,
        )
        return
    }

    var showSleepHistoryScreen by rememberSaveable { mutableStateOf(false) }

    if (showSleepHistoryScreen) {
        SleepHistoryScreen(
            store = store,
            onBack = {
                showSleepHistoryScreen = false
                refreshKey += 1
            },
            onNavigateToSchedule = {
                showSleepHistoryScreen = false
                showScheduleScreen = true
            },
            onNavigateToPhoneUsage = {
                showSleepHistoryScreen = false
                showPhoneUsageScreen = true
            },
            onNavigateToWindDown = {
                showSleepHistoryScreen = false
                showWindDownScreen = true
            },
            onNavigateToBlocks = onNavigateToBlocks,
        )
        return
    }

    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showDetailsDialog by rememberSaveable { mutableStateOf(false) }
    var showProtectionDialog by rememberSaveable { mutableStateOf(false) }
    var showWindDownNotice by rememberSaveable { mutableStateOf(false) }
    var showSoundsNotice by rememberSaveable { mutableStateOf(false) }
    var showLogDialog by rememberSaveable { mutableStateOf(false) }
    var showRemindersDialog by rememberSaveable { mutableStateOf(false) }
    var editTargetLog by remember { mutableStateOf<SleepLog?>(null) }

    // Measure phone usage after planned bedtime for today's sleep
    val usageAfterBedtimeMillis = remember(refreshKey, todayLog, schedule, usageAccess) {
        if (!usageAccess) null
        else {
            try {
                val now = System.currentTimeMillis()
                val windowStart = Calendar.getInstance().apply {
                    timeInMillis = now
                    val currentHour = get(Calendar.HOUR_OF_DAY)
                    if (currentHour < 12) add(Calendar.DAY_OF_YEAR, -1)
                    set(Calendar.HOUR_OF_DAY, schedule.bedtimeMinute / 60)
                    set(Calendar.MINUTE, schedule.bedtimeMinute % 60)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val windowEnd = Calendar.getInstance().apply {
                    timeInMillis = windowStart
                    val duration = calculateSleepDurationMinutes(schedule.bedtimeMinute, schedule.wakeupMinute)
                    add(Calendar.MINUTE, duration)
                }.timeInMillis
                sleepPhoneUsage(context, windowStart, minOf(now, windowEnd), apps).totalForegroundMillis
            } catch (_: Exception) {
                null
            }
        }
    }

    fun updateSchedule(newSchedule: SleepSchedule) {
        store.saveSleepSchedule(newSchedule)
        SleepAlarms.schedule(context, newSchedule)
        schedule = newSchedule
        refreshKey += 1
    }

    // Sleep duration calculation (defaults to 7h 35m from 11:15 PM -> 6:50 AM if no log recorded yet)
    val durationMinutes = todayLog?.durationMinutes
        ?: calculateSleepDurationMinutes(schedule.bedtimeMinute, schedule.wakeupMinute)
    val hours = durationMinutes / 60
    val mins = durationMinutes % 60
    val targetMinutes = if (schedule.targetDurationMinutes > 0) schedule.targetDurationMinutes else 480
    val progress = (durationMinutes.toFloat() / targetMinutes.toFloat()).coerceIn(0f, 1f)
    val diffMinutes = targetMinutes - durationMinutes
    val targetHours = targetMinutes / 60
    val gapText = if (diffMinutes > 0) {
        "You're ${diffMinutes} min away from your target of ${targetHours} hours."
    } else {
        "Target of ${targetHours} hours reached!"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Top Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
        ) {
            Text(
                text = "Sleep",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = SleepText,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "Your nightly reset",
                fontSize = 14.sp,
                color = SleepMuted,
            )
        }

        // Card 1: Last night / Sleep duration & circular ring
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SleepCard,
            border = BorderStroke(1.dp, SleepCardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Last night",
                        color = SleepMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${hours}h ${mins}m",
                        color = SleepText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Sleep duration",
                        color = SleepMuted,
                        fontSize = 13.sp,
                    )
                }

                // Circular Progress Ring with Bed Icon
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 7.dp.toPx()
                        val pad = strokeWidth / 2f
                        val arcSize = Size(size.width - pad * 2, size.height - pad * 2)

                        // Background track
                        drawArc(
                            color = SleepGreenDark,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = arcSize,
                            style = Stroke(width = strokeWidth),
                        )

                        // Progress arc
                        val sweep = (progress * 360f).coerceAtLeast(10f)
                        drawArc(
                            color = SleepGreen,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(pad, pad),
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                        )
                    }

                    BedIcon(color = SleepGreen, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Row of 2 Cards: Bedtime & Wake up
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Bedtime Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showScheduleScreen = true },
                color = SleepCard,
                border = BorderStroke(1.dp, SleepCardBorder),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SleepGreenDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        MoonIcon(color = SleepGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = formatClockTime12h(schedule.bedtimeMinute),
                        color = SleepText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Bedtime",
                        color = SleepMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            // Wake up Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showScheduleScreen = true },
                color = SleepCard,
                border = BorderStroke(1.dp, SleepCardBorder),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SleepGreenDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        SunIcon(color = SleepGreen, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = formatClockTime12h(schedule.wakeupMinute),
                        color = SleepText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Wake up",
                        color = SleepMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Target Gap / Insight Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { showScheduleScreen = true },
            color = SleepCard,
            border = BorderStroke(1.dp, SleepCardBorder),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SleepGreenDark),
                    contentAlignment = Alignment.Center,
                ) {
                    BarChartIcon(color = SleepGreen, modifier = Modifier.size(18.dp))
                }
                Text(
                    text = gapText,
                    color = SleepText,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "›",
                    color = SleepChevron,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Section Title: Sleep Tools
        Text(
            text = "Sleep Tools",
            color = SleepText,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        )

        // 6 Sleep Tools
        SleepToolItem(
            icon = { CalendarClockIcon(color = SleepGreen, modifier = Modifier.size(20.dp)) },
            title = "Sleep Schedule",
            subtitle = "Set bedtime, wake-up time, reminders and target duration.",
            onClick = { showScheduleScreen = true },
        )

        SleepToolItem(
            icon = { BarChartIcon(color = SleepGreen, modifier = Modifier.size(20.dp)) },
            title = "Sleep History",
            subtitle = "View and manage your past sleep records.",
            onClick = { showSleepHistoryScreen = true },
        )

        SleepToolItem(
            icon = { PhoneUsageIcon(color = SleepGreen, modifier = Modifier.size(20.dp)) },
            title = "Phone Usage Insights",
            subtitle = "See your phone activity during sleep hours.",
            onClick = {
                showPhoneUsageScreen = true
            },
        )

        SleepToolItem(
            icon = { WindDownLeafIcon(color = SleepGreen, modifier = Modifier.size(20.dp)) },
            title = "Wind-down Mode",
            subtitle = "Guided routine to help you sleep better.",
            onClick = {
                windDownInitialDest = WindDownDestination.MAIN
                showWindDownScreen = true
            },
        )

        SleepToolItem(
            icon = { SleepSoundsMusicIcon(color = SleepGreen, modifier = Modifier.size(20.dp)) },
            title = "Sleep Sounds",
            subtitle = "Relaxing sounds for a peaceful sleep.",
            onClick = {
                windDownInitialDest = WindDownDestination.RELAXING_SOUNDS
                showWindDownScreen = true
            },
        )

        SleepToolItem(
            icon = { SleepSettingsGearIcon(color = SleepGreen, modifier = Modifier.size(20.dp)) },
            title = "Sleep Settings",
            subtitle = "Customize preferences and permissions.",
            onClick = {
                sleepSettingsInitialPanel = SleepSettingsPanel.HUB
                showSleepSettingsScreen = true
            },
        )

        Spacer(Modifier.height(4.dp))

        // Start Wind-down Pill Button
        Button(
            onClick = {
                windDownInitialDest = WindDownDestination.MAIN
                showWindDownScreen = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = SleepGreen),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayTriangleIcon(color = Color(0xFF090D0B), size = 16.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Start Wind-down",
                    color = Color(0xFF090D0B),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Space before bottom navigation bar
        Spacer(Modifier.height(20.dp))
    }

    // Dialogs & Sheets


    if (showWindDownNotice) {
        AlertDialog(
            onDismissRequest = { showWindDownNotice = false },
            containerColor = SleepCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SleepGreenDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        WindDownLeafIcon(color = SleepGreen, modifier = Modifier.size(20.dp))
                    }
                    Text("Wind-down Mode", color = SleepText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "Wind-down Mode routine is ready.\n\nWe will configure guided routines, audio soundscapes, and bedtime triggers in the next step.",
                    color = SleepMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWindDownNotice = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF090D0B)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (showSoundsNotice) {
        AlertDialog(
            onDismissRequest = { showSoundsNotice = false },
            containerColor = SleepCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SleepGreenDark),
                        contentAlignment = Alignment.Center,
                    ) {
                        SleepSoundsMusicIcon(color = SleepGreen, modifier = Modifier.size(20.dp))
                    }
                    Text("Sleep Sounds", color = SleepText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Text(
                    "Sleep Sounds player is ready.\n\nRelaxing ambient sleep soundscapes and timer controls will be connected in the next step.",
                    color = SleepMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSoundsNotice = false },
                    colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF090D0B)),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (showLogDialog) {
        SleepLogDialog(
            existingLog = editTargetLog ?: todayLog,
            defaultBedtime = schedule.bedtimeMinute,
            defaultWakeup = schedule.wakeupMinute,
            onDismiss = {
                showLogDialog = false
                editTargetLog = null
            },
            onSave = { log ->
                store.saveSleepLog(log)
                refreshKey += 1
                showLogDialog = false
                editTargetLog = null
            },
            onDelete = { id ->
                store.deleteSleepLog(id)
                refreshKey += 1
                showLogDialog = false
                editTargetLog = null
            },
        )
    }

    if (showDetailsDialog && todayLog != null) {
        SleepDetailsDialog(
            log = todayLog,
            targetMinutes = schedule.targetDurationMinutes,
            usageAfterBedtimeMillis = usageAfterBedtimeMillis,
            usageAccess = usageAccess,
            onDismiss = { showDetailsDialog = false },
            onEdit = {
                showDetailsDialog = false
                editTargetLog = todayLog
                showLogDialog = true
            },
        )
    }

    if (showHistoryDialog) {
        SleepHistoryDialog(
            logs = store.sleepLogs(60),
            targetMinutes = schedule.targetDurationMinutes,
            onDismiss = { showHistoryDialog = false },
            onEditLog = { log ->
                editTargetLog = log
                showLogDialog = true
            },
            onDeleteLog = { id ->
                store.deleteSleepLog(id)
                refreshKey += 1
            },
        )
    }

    if (showProtectionDialog) {
        SleepProtectionDialog(
            schedule = schedule,
            allApps = apps,
            onDismiss = { showProtectionDialog = false },
            onSave = { enabled, blockedPackages ->
                val updated = schedule.copy(
                    protectionEnabled = enabled,
                    protectionBlockedPackages = blockedPackages,
                )
                updateSchedule(updated)
                showProtectionDialog = false
            },
        )
    }

    if (showRemindersDialog) {
        SleepRemindersDialog(
            schedule = schedule,
            onDismiss = { showRemindersDialog = false },
            onSave = { enabled, advanceMinutes ->
                val updated = schedule.copy(
                    reminderEnabled = enabled,
                    reminderAdvanceMinutes = advanceMinutes,
                )
                updateSchedule(updated)
                showRemindersDialog = false
            },
        )
    }
}

// ----------------------------------------------------------------------
// SLEEP TOOL ITEM
// ----------------------------------------------------------------------
@Composable
private fun SleepToolItem(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = SleepCard,
        border = BorderStroke(1.dp, SleepCardBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(SleepGreenDark),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = SleepText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = SleepMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Text(
                text = "›",
                color = SleepChevron,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ----------------------------------------------------------------------
// SLEEP SCHEDULE DIALOG
// ----------------------------------------------------------------------
@Composable
private fun SleepScheduleDialog(
    schedule: SleepSchedule,
    onDismiss: () -> Unit,
    onSave: (SleepSchedule) -> Unit,
) {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(schedule.enabled) }
    var bedtime by rememberSaveable { mutableIntStateOf(schedule.bedtimeMinute) }
    var wakeup by rememberSaveable { mutableIntStateOf(schedule.wakeupMinute) }
    var activeDays by rememberSaveable { mutableStateOf(schedule.activeDays) }
    var targetMinutes by rememberSaveable { mutableIntStateOf(schedule.targetDurationMinutes) }

    val calculatedDuration = calculateSleepDurationMinutes(bedtime, wakeup)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SleepCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SleepGreenDark),
                    contentAlignment = Alignment.Center,
                ) {
                    CalendarClockIcon(color = SleepGreen, modifier = Modifier.size(20.dp))
                }
                Text("Sleep Schedule", color = SleepText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Enabled switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Enable Schedule", color = SleepText, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SleepText,
                            checkedTrackColor = SleepGreen,
                            uncheckedThumbColor = SleepMuted,
                            uncheckedTrackColor = SleepGreenDark,
                        ),
                    )
                }

                // Bedtime & Wake up Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Bedtime
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> bedtime = h * 60 + m },
                                    bedtime / 60,
                                    bedtime % 60,
                                    android.text.format.DateFormat.is24HourFormat(context),
                                ).show()
                            },
                        color = SleepGreenDark,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Bedtime", color = SleepMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formatClockTime12h(bedtime), color = SleepGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    // Wake up
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> wakeup = h * 60 + m },
                                    wakeup / 60,
                                    wakeup % 60,
                                    android.text.format.DateFormat.is24HourFormat(context),
                                ).show()
                            },
                        color = SleepGreenDark,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Wake up", color = SleepMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(formatClockTime12h(wakeup), color = SleepGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }

                // Calculated Planned Sleep Duration
                Text(
                    text = "Planned duration: ${formatMinutesToHm(calculatedDuration)}",
                    color = SleepMuted,
                    fontSize = 12.sp,
                )

                // Target duration selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Target Sleep Duration", color = SleepText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(420 to "7h", 450 to "7.5h", 480 to "8h", 510 to "8.5h", 540 to "9h").forEach { (targetMin, label) ->
                            val isSel = targetMinutes == targetMin
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { targetMinutes = targetMin },
                                color = if (isSel) SleepGreen else SleepGreenDark,
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSel) Color(0xFF090D0B) else SleepText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // Active Days
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Active Days", color = SleepText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val dayLetters = listOf("S", "M", "T", "W", "T", "F", "S")
                        DAYS_OF_WEEK_ORDER.forEachIndexed { index, dayOfWeek ->
                            val isSelected = dayOfWeek in activeDays
                            Surface(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clickable {
                                        activeDays = if (isSelected) {
                                            if (activeDays.size > 1) activeDays - dayOfWeek else activeDays
                                        } else {
                                            activeDays + dayOfWeek
                                        }
                                    },
                                shape = CircleShape,
                                color = if (isSelected) SleepGreen else SleepGreenDark,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = dayLetters[index],
                                        color = if (isSelected) Color(0xFF090D0B) else SleepMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
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
                    onSave(
                        schedule.copy(
                            enabled = enabled,
                            bedtimeMinute = bedtime,
                            wakeupMinute = wakeup,
                            activeDays = activeDays,
                            targetDurationMinutes = targetMinutes,
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF090D0B)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SleepMuted)
            }
        },
    )
}

// ----------------------------------------------------------------------
// VECTOR CANVAS ICONS
// ----------------------------------------------------------------------
@Composable
private fun BedIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.2.dp.toPx()
        // Headboard on left
        drawLine(
            color = color,
            start = Offset(w * 0.15f, h * 0.28f),
            end = Offset(w * 0.15f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Footboard on right
        drawLine(
            color = color,
            start = Offset(w * 0.85f, h * 0.52f),
            end = Offset(w * 0.85f, h * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Base mattress frame line
        drawLine(
            color = color,
            start = Offset(w * 0.15f, h * 0.70f),
            end = Offset(w * 0.85f, h * 0.70f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Blanket block
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.18f, h * 0.56f),
            size = Size(w * 0.65f, h * 0.14f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
        // Pillow
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.22f, h * 0.42f),
            size = Size(w * 0.22f, h * 0.12f),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
    }
}

@Composable
private fun MoonIcon(color: Color, modifier: Modifier = Modifier) {
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
private fun SunIcon(color: Color, modifier: Modifier = Modifier) {
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
private fun BarChartIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = 3.2.dp.toPx()
        val spacing = 2.8.dp.toPx()
        val r = 1.5.dp.toPx()

        val h1 = h * 0.38f
        val h2 = h * 0.72f
        val h3 = h * 0.52f

        val totalW = barWidth * 3 + spacing * 2
        val startX = (w - totalW) / 2f
        val baseY = h * 0.85f

        drawRoundRect(color, Offset(startX, baseY - h1), Size(barWidth, h1), CornerRadius(r, r))
        drawRoundRect(color, Offset(startX + barWidth + spacing, baseY - h2), Size(barWidth, h2), CornerRadius(r, r))
        drawRoundRect(color, Offset(startX + (barWidth + spacing) * 2, baseY - h3), Size(barWidth, h3), CornerRadius(r, r))
    }
}

@Composable
private fun CalendarClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val pad = 2.5.dp.toPx()
        val topPad = 4.5.dp.toPx()
        val r = 3.dp.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(pad, topPad),
            size = Size(w - pad * 2, h - topPad - 2.dp.toPx()),
            cornerRadius = CornerRadius(r, r),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = color,
            start = Offset(pad, topPad + 4.dp.toPx()),
            end = Offset(w - pad, topPad + 4.dp.toPx()),
            strokeWidth = 1.4.dp.toPx()
        )
        drawLine(color, Offset(pad + 3.5.dp.toPx(), 1.5.dp.toPx()), Offset(pad + 3.5.dp.toPx(), topPad + 1.dp.toPx()), stroke, StrokeCap.Round)
        drawLine(color, Offset(w - pad - 3.5.dp.toPx(), 1.5.dp.toPx()), Offset(w - pad - 3.5.dp.toPx(), topPad + 1.dp.toPx()), stroke, StrokeCap.Round)

        val clockR = 4.dp.toPx()
        val clockCenter = Offset(w / 2f, topPad + (h - topPad) * 0.54f)
        drawCircle(color, clockR, clockCenter, style = Stroke(stroke))
        drawLine(color, clockCenter, Offset(clockCenter.x, clockCenter.y - clockR * 0.6f), stroke, StrokeCap.Round)
        drawLine(color, clockCenter, Offset(clockCenter.x + clockR * 0.5f, clockCenter.y), stroke, StrokeCap.Round)
    }
}

@Composable
private fun PhoneUsageIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        val phoneW = w * 0.55f
        val phoneH = h * 0.85f
        val x = (w - phoneW) / 2f
        val y = (h - phoneH) / 2f
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(phoneW, phoneH),
            cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx()),
            style = Stroke(stroke)
        )
        drawLine(
            color = color,
            start = Offset(w / 2f - 3.dp.toPx(), y + phoneH - 3.dp.toPx()),
            end = Offset(w / 2f + 3.dp.toPx(), y + phoneH - 3.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun WindDownLeafIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val leaf = Path().apply {
            moveTo(w * 0.18f, h * 0.82f)
            cubicTo(w * 0.18f, h * 0.35f, w * 0.55f, h * 0.15f, w * 0.85f, h * 0.15f)
            cubicTo(w * 0.85f, h * 0.45f, w * 0.65f, h * 0.82f, w * 0.18f, h * 0.82f)
            close()
        }
        drawPath(leaf, color)
        drawLine(
            color = SleepGreenDark,
            start = Offset(w * 0.18f, h * 0.82f),
            end = Offset(w * 0.65f, h * 0.35f),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SleepSoundsMusicIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val noteHeadR = 2.8.dp.toPx()
        val stemW = 1.8.dp.toPx()
        val x1 = w * 0.30f
        val y1 = h * 0.72f
        val x2 = w * 0.70f
        val y2 = h * 0.60f
        val topY = h * 0.25f

        drawCircle(color, noteHeadR, Offset(x1, y1))
        drawLine(color, Offset(x1 + noteHeadR, y1), Offset(x1 + noteHeadR, topY), stemW, StrokeCap.Round)

        drawCircle(color, noteHeadR, Offset(x2, y2))
        drawLine(color, Offset(x2 + noteHeadR, y2), Offset(x2 + noteHeadR, topY - 2.dp.toPx()), stemW, StrokeCap.Round)

        drawLine(
            color = color,
            start = Offset(x1 + noteHeadR, topY),
            end = Offset(x2 + noteHeadR, topY - 2.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun SleepSettingsGearIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = minOf(w, h) * 0.38f
        val innerR = minOf(w, h) * 0.24f
        val holeR = minOf(w, h) * 0.11f

        drawCircle(color, innerR, Offset(cx, cy))
        val stroke = 2.8.dp.toPx()
        for (i in 0 until 6) {
            val angle = (i * 60) * (Math.PI / 180.0)
            val cos = Math.cos(angle).toFloat()
            val sin = Math.sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + cos * innerR, cy + sin * innerR),
                end = Offset(cx + cos * outerR, cy + sin * outerR),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(SleepGreenDark, holeR, Offset(cx, cy))
    }
}

@Composable
private fun PlayTriangleIcon(color: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.26f, h * 0.18f)
            lineTo(w * 0.84f, h * 0.50f)
            lineTo(w * 0.26f, h * 0.82f)
            close()
        }
        drawPath(path, color)
    }
}

// ----------------------------------------------------------------------
// DIALOG: HELP
// ----------------------------------------------------------------------
@Composable
private fun SleepHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🌙")
                Text("About StudyMode Sleep", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("• Setting a Sleep Schedule: Customize your bedtime, wake-up target, and active days. StudyMode calculates duration across midnight automatically.", fontSize = 13.sp)
                Text("• Manual Sleep Logging: Log your actual bedtime and wake-up times to track consistency. You can also rate sleep quality (1–5) and add optional study/sleep notes.", fontSize = 13.sp)
                Text("• Phone Usage Measurement: When Usage Access is granted, StudyMode measures foreground app usage during your planned sleep window.", fontSize = 13.sp)
                Text("• Neutral & Honest: Phone activity is not proof of wakefulness (e.g. streaming, podcasts, alarms). StudyMode never shames you or marks your logs false.", fontSize = 13.sp)
                Text("• Sleep Protection: Automatically blocks selected apps using the Accessibility service during your active sleep hours.", fontSize = 13.sp)
                Text("• Sleep Reminders: Gentle local notifications remind you 15 minutes before bedtime to wind down and prepare for rest.", fontSize = 13.sp)
                Text("• 100% Offline & Private: All sleep data remains securely on your device. No cloud sync, no tracking.", fontSize = 13.sp, color = SleepGreen)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = SleepGreen, fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ----------------------------------------------------------------------
// DIALOG: SLEEP LOG EDITOR
// ----------------------------------------------------------------------
@Composable
private fun SleepLogDialog(
    existingLog: SleepLog?,
    defaultBedtime: Int,
    defaultWakeup: Int,
    onDismiss: () -> Unit,
    onSave: (SleepLog) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val context = LocalContext.current
    var bedtimeMinute by rememberSaveable { mutableIntStateOf(existingLog?.bedtimeMinute ?: defaultBedtime) }
    var wakeupMinute by rememberSaveable { mutableIntStateOf(existingLog?.wakeupMinute ?: defaultWakeup) }
    var rating by rememberSaveable { mutableIntStateOf(existingLog?.qualityRating ?: 4) }
    var note by rememberSaveable { mutableStateOf(existingLog?.note.orEmpty()) }
    var dateMillis by rememberSaveable { mutableLongStateOf(existingLog?.dateMillis ?: System.currentTimeMillis()) }

    val duration = calculateSleepDurationMinutes(bedtimeMinute, wakeupMinute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingLog == null) "Log Sleep" else "Edit Sleep Log",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Record your actual bedtime and wake-up time.", color = SleepMuted, fontSize = 12.sp)

                // Bedtime Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Reported Bedtime", color = SleepText)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> bedtimeMinute = h * 60 + m },
                                bedtimeMinute / 60,
                                bedtimeMinute % 60,
                                android.text.format.DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(formatClockTime12h(bedtimeMinute), color = SleepGreen, fontWeight = FontWeight.Bold)
                    }
                }

                // Wake-up Picker Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Reported Wake-up", color = SleepText)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> wakeupMinute = h * 60 + m },
                                wakeupMinute / 60,
                                wakeupMinute % 60,
                                android.text.format.DateFormat.is24HourFormat(context),
                            ).show()
                        },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(formatClockTime12h(wakeupMinute), color = SleepGreen, fontWeight = FontWeight.Bold)
                    }
                }

                // Calculated Duration Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SleepElevated,
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Calculated Duration", color = SleepMuted, fontSize = 13.sp)
                        Text(formatMinutesToHm(duration), color = SleepGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                // Sleep Quality Rating (1 to 5 stars)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Sleep Quality", color = SleepMuted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..5).forEach { star ->
                            Text(
                                text = if (star <= rating) "★" else "☆",
                                color = if (star <= rating) Color(0xFFFFD700) else SleepMuted,
                                fontSize = 28.sp,
                                modifier = Modifier.clickable { rating = star },
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SleepGreen,
                        unfocusedBorderColor = SleepDivider,
                    ),
                    maxLines = 3,
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
                        note = note,
                    )
                    onSave(log)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF081C09)),
            ) {
                Text("Save Sleep Log", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (existingLog != null) {
                    TextButton(onClick = { onDelete(existingLog.id) }) {
                        Text("Delete", color = SleepWarning)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = SleepMuted)
                }
            }
        },
    )
}

// ----------------------------------------------------------------------
// DIALOG: SLEEP DETAILS
// ----------------------------------------------------------------------
@Composable
private fun SleepDetailsDialog(
    log: SleepLog,
    targetMinutes: Int,
    usageAfterBedtimeMillis: Long?,
    usageAccess: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🌙")
                Text("Sleep Details", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DetailItem("Reported Bedtime", formatClockTime12h(log.bedtimeMinute))
                DetailItem("Reported Wake-up", formatClockTime12h(log.wakeupMinute))
                DetailItem("Total Sleep Duration", formatMinutesToHm(log.durationMinutes))
                DetailItem("Sleep Target", formatMinutesToHm(targetMinutes))

                val diff = log.durationMinutes - targetMinutes
                val diffText = when {
                    diff > 0 -> "+${formatMinutesToHm(diff)} above target"
                    diff < 0 -> "${formatMinutesToHm(-diff)} below target"
                    else -> "Met target exactly"
                }
                DetailItem("Target Difference", diffText)

                val usageMinutes = usageAfterBedtimeMillis?.let { it / 60_000 } ?: 0L
                val usageStr = if (!usageAccess) "Usage Access required" else "${usageMinutes}m detected"
                DetailItem("Phone Activity After Bedtime", usageStr)

                if (log.qualityRating > 0) {
                    DetailItem("Sleep Quality", "★".repeat(log.qualityRating) + "☆".repeat(5 - log.qualityRating))
                }

                if (log.note.isNotBlank()) {
                    DetailItem("Note", log.note)
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "StudyMode estimates phone activity from available device-usage records. It cannot confirm when you actually fell asleep or whether you were awake during every recorded interaction.",
                    color = SleepMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onEdit,
                colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF081C09)),
            ) {
                Text("Edit Log", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SleepMuted)
            }
        },
    )
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = SleepMuted, fontSize = 13.sp)
        Text(value, color = SleepText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

// ----------------------------------------------------------------------
// DIALOG: SLEEP HISTORY
// ----------------------------------------------------------------------
@Composable
private fun SleepHistoryDialog(
    logs: List<SleepLog>,
    targetMinutes: Int,
    onDismiss: () -> Unit,
    onEditLog: (SleepLog) -> Unit,
    onDeleteLog: (Long) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Sleep History", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (logs.isEmpty()) {
                    Text("No past sleep logs found. Start logging tonight!", color = SleepMuted)
                } else {
                    val avgMinutes = (logs.map { it.durationMinutes }.average()).toInt()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = SleepElevated,
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Weekly Recorded Average", color = SleepMuted, fontSize = 12.sp)
                            Text(formatMinutesToHm(avgMinutes), color = SleepGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Based only on valid recorded entries. Not a medical assessment.", color = SleepMuted, fontSize = 10.sp)
                        }
                    }

                    val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
                    logs.forEach { log ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = SleepElevated,
                            border = BorderStroke(1.dp, SleepDivider),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        dateFormat.format(Date(log.dateMillis)),
                                        fontWeight = FontWeight.Bold,
                                        color = SleepText,
                                        fontSize = 14.sp,
                                    )
                                    Text(
                                        "${formatClockTime12h(log.bedtimeMinute)} – ${formatClockTime12h(log.wakeupMinute)}",
                                        color = SleepMuted,
                                        fontSize = 12.sp,
                                    )
                                    if (log.qualityRating > 0) {
                                        Text("★".repeat(log.qualityRating), color = Color(0xFFFFD700), fontSize = 11.sp)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        formatMinutesToHm(log.durationMinutes),
                                        color = if (log.durationMinutes >= targetMinutes) SleepGreen else SleepWarning,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                    )
                                    Row {
                                        TextButton(onClick = { onEditLog(log) }) {
                                            Text("Edit", color = SleepGreen, fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { onDeleteLog(log.id) }) {
                                            Text("✕", color = SleepMuted, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = SleepGreen, fontWeight = FontWeight.Bold)
            }
        },
    )
}

// ----------------------------------------------------------------------
// DIALOG: SLEEP MODE PROTECTION
// ----------------------------------------------------------------------
@Composable
private fun SleepProtectionDialog(
    schedule: SleepSchedule,
    allApps: List<LaunchableApp>,
    onDismiss: () -> Unit,
    onSave: (Boolean, Set<String>) -> Unit,
) {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(schedule.protectionEnabled) }
    var selectedPackages by remember { mutableStateOf(schedule.protectionBlockedPackages) }
    val usageAccess = remember { hasUsageAccess(context) }
    val overlayEnabled = remember { hasOverlayPermission(context) }
    var showAppPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🛡️")
                Text("Sleep Mode Protection", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Protection", fontWeight = FontWeight.SemiBold, color = SleepText)
                        Text("Block selected apps during sleep hours", color = SleepMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = SleepText, checkedTrackColor = SleepGreen),
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = SleepElevated,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Protected Window", color = SleepMuted, fontSize = 12.sp)
                        Text(
                            "${formatClockTime12h(schedule.bedtimeMinute)} to ${formatClockTime12h(schedule.wakeupMinute)}",
                            color = SleepText,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("Follows your configured Sleep Schedule.", color = SleepMuted, fontSize = 11.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Blocked Apps", fontWeight = FontWeight.SemiBold, color = SleepText)
                        Text(
                            if (selectedPackages.isEmpty()) "No apps chosen"
                            else "${selectedPackages.size} app(s) selected",
                            color = SleepGreen,
                            fontSize = 12.sp,
                        )
                    }
                    OutlinedButton(
                        onClick = { showAppPicker = true },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Choose Apps", color = SleepGreen)
                    }
                }

                if (!usageAccess || !overlayEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF332014),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("⚠️ Permissions Needed", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Usage Access and Display Over Other Apps are required to enforce app closures during sleep.", color = SleepMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    if (!usageAccess) {
                                        openUsageAccessSettings(context)
                                    } else {
                                        openOverlaySettings(context)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB74D), contentColor = Color(0xFF221100)),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text("Open Permission Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(enabled, selectedPackages) },
                colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF081C09)),
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SleepMuted)
            }
        },
    )

    if (showAppPicker) {
        AppPickerDialog(
            title = "Choose Apps for Sleep Protection",
            apps = allApps,
            initiallySelected = selectedPackages,
            onDismiss = { showAppPicker = false },
            onConfirm = { selected ->
                selectedPackages = selected.map { it.packageName }.toSet()
                showAppPicker = false
            },
        )
    }
}

// ----------------------------------------------------------------------
// DIALOG: SLEEP REMINDERS
// ----------------------------------------------------------------------
@Composable
private fun SleepRemindersDialog(
    schedule: SleepSchedule,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int) -> Unit,
) {
    val context = LocalContext.current
    var enabled by rememberSaveable { mutableStateOf(schedule.reminderEnabled) }
    var advanceMinutes by rememberSaveable { mutableIntStateOf(schedule.reminderAdvanceMinutes) }
    val notificationsAllowed = remember { StudyScheduleAlarms.notificationsEnabled(context) }
    var testSent by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) enabled = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🔔")
                Text("Sleep Reminders", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bedtime Reminder", fontWeight = FontWeight.SemiBold, color = SleepText)
                        Text("Get notified to wind down before sleep", color = SleepMuted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            if (it && !notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            enabled = it
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = SleepText, checkedTrackColor = SleepGreen),
                    )
                }

                Text("Remind me before bedtime:", color = SleepMuted, fontSize = 13.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30, 45, 60).forEach { mins ->
                        val selected = advanceMinutes == mins
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clickable { advanceMinutes = mins },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) SleepSoftGreen else SleepElevated,
                            border = BorderStroke(1.dp, if (selected) SleepGreen else SleepDivider),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${mins}m",
                                    color = if (selected) SleepGreen else SleepMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }

                // Test Reminder Button
                OutlinedButton(
                    onClick = {
                        SleepAlarms.postTestReminder(context, schedule.copy(reminderAdvanceMinutes = advanceMinutes))
                        testSent = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(if (testSent) "✓ Notification Sent!" else "Send Test Notification", color = SleepGreen)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(enabled, advanceMinutes) },
                colors = ButtonDefaults.buttonColors(containerColor = SleepGreen, contentColor = Color(0xFF081C09)),
            ) {
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = SleepMuted)
            }
        },
    )
}

// ----------------------------------------------------------------------
// FORMATTING HELPERS
// ----------------------------------------------------------------------
private fun formatMinutesToHm(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatMinutesToHmShort(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
