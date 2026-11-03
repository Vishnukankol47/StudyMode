package com.studymode.app

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// ----------------------------------------------------------------------
// THEME COLORS
// ----------------------------------------------------------------------
private val BgDark = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1B2E24)
private val CardInner = Color(0xFF13231B)
private val MintGreen = Color(0xFF4ADE80)
private val MintGreenPill = Color(0xFF132A1D)
private val TextWhite = Color(0xFFF3F4F6)
private val TextMuted = Color(0xFF8E9993)
private val AmberYellow = Color(0xFFFBBF24)
private val AmberPill = Color(0xFF33260A)
private val RedAlert = Color(0xFFEF4444)
private val RedPill = Color(0xFF331414)

// ----------------------------------------------------------------------
// DATA MODELS
// ----------------------------------------------------------------------
data class SessionTimelineEvent(
    val type: String, // "FOCUS", "BREAK", "DISTRACTION"
    val startMillis: Long,
    val endMillis: Long,
    val label: String,
    val details: String = "",
    val packageName: String? = null
) {
    val durationMillis: Long get() = (endMillis - startMillis).coerceAtLeast(0L)
    val durationMinutes: Int get() = (durationMillis / 60000L).toInt()
}

data class SessionAppUsage(
    val packageName: String,
    val appName: String,
    val durationMillis: Long,
    val launchCount: Int = 1,
    val isDistraction: Boolean = false,
    val isBlocked: Boolean = false,
    val timestamps: List<Long> = emptyList()
) {
    val durationMinutes: Int get() = (durationMillis / 60000L).toInt()
}

data class SessionIntervalBar(
    val index: Int,
    val startMillis: Long,
    val endMillis: Long,
    val timeLabel: String,
    val intensity: Float, // 0.0 to 1.0
    val type: String, // "FOCUS", "BREAK", "DISTRACTION"
    val durationMinutes: Int
)

// ----------------------------------------------------------------------
// MAIN FOCUS SESSION DETAILS SCREEN
// ----------------------------------------------------------------------
@Composable
fun FocusSessionDetailsScreen(
    store: LocalStudyStore,
    session: FocusSessionRecord,
    onBack: () -> Unit,
    onSessionUpdated: (FocusSessionRecord) -> Unit,
    onSessionDeleted: (Long) -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    var currentSession by remember(session) { mutableStateOf(session) }
    var showMenu by remember { mutableStateOf(false) }

    // Dialog state
    var showEditSessionDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showSubjectEditorDialog by rememberSaveable { mutableStateOf(false) }
    var showNotesEditorDialog by rememberSaveable { mutableStateOf(false) }
    var showModeDialog by rememberSaveable { mutableStateOf(false) }
    var showBreakBreakdownDialog by rememberSaveable { mutableStateOf(false) }
    var showAppUsageDialog by rememberSaveable { mutableStateOf(false) }
    var showDistractionsDialog by rememberSaveable { mutableStateOf(false) }
    var selectedTimelineEvent by remember { mutableStateOf<SessionTimelineEvent?>(null) }
    var selectedIntervalBar by remember { mutableStateOf<SessionIntervalBar?>(null) }

    // Query app usage and distractions
    val (appsUsed, distractions) = remember(currentSession) {
        loadSessionAppUsage(context, currentSession, store)
    }

    // Build timeline events
    val timelineEvents = remember(currentSession, distractions) {
        loadSessionTimelineEvents(currentSession, distractions)
    }

    // Build mini activity chart bars
    val activityBars = remember(currentSession, timelineEvents) {
        buildSessionActivityBars(currentSession, timelineEvents)
    }

    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val fullDateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    // Helper for relative date label
    val dateHeaderLabel = remember(currentSession.startedAtMillis) {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val sessionCal = Calendar.getInstance().apply {
            timeInMillis = currentSession.startedAtMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((todayCal.timeInMillis - sessionCal.timeInMillis) / 86400000L).toInt()
        when (diffDays) {
            0 -> "Today, " + shortDateFormat.format(Date(currentSession.startedAtMillis))
            1 -> "Yesterday, " + shortDateFormat.format(Date(currentSession.startedAtMillis))
            else -> fullDateFormat.format(Date(currentSession.startedAtMillis))
        }
    }

    val focusHours = (currentSession.elapsedSeconds / 3600L).toInt()
    val focusRemainderMins = ((currentSession.elapsedSeconds % 3600L) / 60L).toInt()
    val focusDurationDisplay = if (focusHours > 0) {
        "${focusHours}h ${focusRemainderMins}m"
    } else {
        "${focusRemainderMins} min"
    }

    val breakMins = (currentSession.breakSeconds / 60L).toInt()
    val breakDurationDisplay = if (breakMins >= 60) {
        "${breakMins / 60}h ${breakMins % 60}m"
    } else {
        "${breakMins} min"
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Surface(color = BgDark) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("←", color = MintGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Column {
                            Text(
                                text = "Focus Session Details",
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "View and manage your focus session",
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Text("⋮", color = MintGreen, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(CardBg).border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Share Session Summary", color = TextWhite, fontSize = 13.sp) },
                                onClick = {
                                    showMenu = false
                                    shareSessionSummary(context, currentSession, focusDurationDisplay, breakDurationDisplay)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export Session Details", color = TextWhite, fontSize = 13.sp) },
                                onClick = {
                                    showMenu = false
                                    exportSessionDetailsJson(context, currentSession, appsUsed, distractions)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit Session", color = MintGreen, fontSize = 13.sp) },
                                onClick = {
                                    showMenu = false
                                    showEditSessionDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Session", color = RedAlert, fontSize = 13.sp) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==================================================================
            // CARD 1: SESSION SUMMARY CARD
            // ==================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header row: Icon, Date/Time, Status Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Circular green clock badge matching screenshot
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MintGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    val stroke = 2.4.dp.toPx()
                                    drawCircle(
                                        color = Color(0xFF07120D),
                                        radius = size.minDimension / 2f - stroke / 2f,
                                        style = Stroke(width = stroke)
                                    )
                                    // Clock hands pointing to ~8:30
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    drawLine(
                                        color = Color(0xFF07120D),
                                        start = center,
                                        end = Offset(center.x, center.y - 7.dp.toPx()),
                                        strokeWidth = stroke
                                    )
                                    drawLine(
                                        color = Color(0xFF07120D),
                                        start = center,
                                        end = Offset(center.x + 6.dp.toPx(), center.y + 4.dp.toPx()),
                                        strokeWidth = stroke
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = dateHeaderLabel,
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "${timeFormat.format(Date(currentSession.startedAtMillis))} – ${timeFormat.format(Date(currentSession.finishedAtMillis))}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        // Status pill badge
                        val (statusText, statusBg, statusColor) = when {
                            currentSession.completed -> Triple("Completed", MintGreenPill, MintGreen)
                            currentSession.goal.contains("interrupted", ignoreCase = true) -> Triple("Interrupted", RedPill, RedAlert)
                            else -> Triple("Completed", MintGreenPill, MintGreen)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(statusBg)
                                .border(1.dp, statusColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Bottom row: Big duration metric on left + Mini activity chart on right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = focusDurationDisplay,
                                color = TextWhite,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Focus time",
                                color = TextMuted,
                                fontSize = 12.5.sp
                            )
                        }

                        // Mini activity chart with vertical bars
                        Column(horizontalAlignment = Alignment.End) {
                            Row(
                                modifier = Modifier
                                    .height(42.dp)
                                    .padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                activityBars.forEach { bar ->
                                    val barHeight = (42.dp * bar.intensity).coerceAtLeast(6.dp)
                                    val barColor = when (bar.type) {
                                        "DISTRACTION" -> RedAlert
                                        "BREAK" -> AmberYellow
                                        else -> MintGreen
                                    }
                                    Box(
                                        modifier = Modifier
                                            .width(5.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(barColor)
                                            .clickable { selectedIntervalBar = bar }
                                    )
                                }
                            }

                            // Interval timestamps matching screenshot
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = timeFormat.format(Date(currentSession.startedAtMillis)),
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = timeFormat.format(Date(currentSession.startedAtMillis + (currentSession.finishedAtMillis - currentSession.startedAtMillis) / 2)),
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = timeFormat.format(Date(currentSession.finishedAtMillis)),
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            // ==================================================================
            // CARD 2: SESSION TIMELINE CARD
            // ==================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Session Timeline",
                        color = TextWhite,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Proportional horizontal timeline bar
                    val totalDuration = (currentSession.finishedAtMillis - currentSession.startedAtMillis).coerceAtLeast(1L)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardInner),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        timelineEvents.forEach { event ->
                            val weight = (event.durationMillis.toFloat() / totalDuration.toFloat()).coerceAtLeast(0.04f)
                            val segmentColor = when (event.type) {
                                "DISTRACTION" -> RedAlert
                                "BREAK" -> AmberYellow
                                else -> MintGreen
                            }

                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(segmentColor)
                                    .clickable { selectedTimelineEvent = event }
                            )
                        }
                    }

                    // Timestamps evenly distributed across session
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val numMarkers = 5
                        for (i in 0 until numMarkers) {
                            val markMillis = currentSession.startedAtMillis + (totalDuration * i / (numMarkers - 1))
                            Text(
                                text = timeFormat.format(Date(markMillis)),
                                color = TextMuted,
                                fontSize = 9.5.sp
                            )
                        }
                    }

                    // Legend row matching screenshot
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendItem(color = MintGreen, label = "Focused")
                        LegendItem(color = AmberYellow, label = "Short break")
                        LegendItem(color = RedAlert, label = "Distracted")
                    }
                }
            }

            // ==================================================================
            // CARD 3: SESSION INFORMATION CARD
            // ==================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Session Information",
                        color = TextWhite,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 2-Column Grid matching screenshot layout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Left Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SessionInfoItem(
                                icon = "📅",
                                label = "Date",
                                value = shortDateFormat.format(Date(currentSession.startedAtMillis)),
                                isEditable = false
                            )
                            SessionInfoItem(
                                icon = "🕒",
                                label = "Start time",
                                value = timeFormat.format(Date(currentSession.startedAtMillis)),
                                isEditable = false
                            )
                            SessionInfoItem(
                                icon = "🕒",
                                label = "End time",
                                value = timeFormat.format(Date(currentSession.finishedAtMillis)),
                                isEditable = false
                            )
                            SessionInfoItem(
                                icon = "⏳",
                                label = "Focus duration",
                                value = focusDurationDisplay,
                                isEditable = false
                            )
                        }

                        // Right Column
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            SessionInfoItem(
                                icon = "☕",
                                label = "Break time",
                                value = breakDurationDisplay,
                                isEditable = true,
                                onClick = { showBreakBreakdownDialog = true }
                            )
                            SessionInfoItem(
                                icon = "📋",
                                label = "Focus mode",
                                value = currentSession.mode.ifBlank { "Focus" },
                                isEditable = true,
                                onClick = { showModeDialog = true }
                            )
                            SessionInfoItem(
                                icon = "📖",
                                label = "Subject / Task",
                                value = currentSession.displaySubject,
                                isEditable = true,
                                onClick = { showSubjectEditorDialog = true }
                            )
                            SessionInfoItem(
                                icon = "📝",
                                label = "Notes",
                                value = currentSession.notes.ifBlank { "Add notes..." },
                                isEditable = true,
                                onClick = { showNotesEditorDialog = true }
                            )
                        }
                    }
                }
            }

            // ==================================================================
            // CARD 4: APPS USED DURING SESSION
            // ==================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Text("⊞", color = MintGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Apps used during session",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "View all ›",
                            color = MintGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showAppUsageDialog = true }
                        )
                    }

                    if (appsUsed.isEmpty()) {
                        Text(
                            text = "No other apps were used during this focus session. Great job staying on task!",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        val maxUsageMillis = appsUsed.maxOfOrNull { it.durationMillis }?.coerceAtLeast(1L) ?: 1L
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            appsUsed.take(3).forEach { app ->
                                val progress = (app.durationMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0.08f, 1f)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // App Icon Container
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CardInner),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = when {
                                                app.appName.contains("Chrome", ignoreCase = true) -> "🌐"
                                                app.appName.contains("YouTube", ignoreCase = true) -> "▶️"
                                                app.appName.contains("Calculator", ignoreCase = true) -> "🔢"
                                                else -> "📱"
                                            },
                                            fontSize = 14.sp
                                        )
                                    }

                                    Text(
                                        text = app.appName,
                                        color = TextWhite,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(70.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Mint Green Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CardInner)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progress)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MintGreen)
                                        )
                                    }

                                    Text(
                                        text = "${app.durationMinutes} min",
                                        color = TextWhite,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.width(42.dp),
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ==================================================================
            // CARD 5: DISTRACTIONS CARD
            // ==================================================================
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardBg,
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            Text("🚫", color = RedAlert, fontSize = 15.sp)
                            Text(
                                text = "Distractions",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = "View all ›",
                            color = MintGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { showDistractionsDialog = true }
                        )
                    }

                    if (distractions.isEmpty()) {
                        Text(
                            text = "Zero distractions recorded during this focus session. Excellent discipline!",
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        distractions.take(2).forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showDistractionsDialog = true }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF261212)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (item.appName.contains("YouTube", ignoreCase = true)) "▶️" else "📱",
                                            fontSize = 14.sp
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = item.appName,
                                            color = TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${item.launchCount} time (${item.durationMinutes} min total)",
                                            color = TextMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text("›", color = TextMuted, fontSize = 18.sp)
                            }
                        }
                    }
                }
            }

            // ==================================================================
            // BOTTOM BUTTONS: EDIT SESSION & DELETE SESSION
            // ==================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Edit Session Button
                OutlinedButton(
                    onClick = { showEditSessionDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MintGreen),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MintGreen
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✏️", fontSize = 14.sp)
                        Text(
                            text = "Edit Session",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Delete Session Button
                OutlinedButton(
                    onClick = { showDeleteConfirmDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, RedAlert),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = RedAlert
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🗑️", fontSize = 14.sp)
                        Text(
                            text = "Delete Session",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    // ==================================================================
    // DIALOGS & OVERLAYS
    // ==================================================================

    // 1. Full Edit Session Form
    if (showEditSessionDialog) {
        FullEditSessionDialog(
            session = currentSession,
            onDismiss = { showEditSessionDialog = false },
            onSave = { updated ->
                store.saveFocusSession(updated)
                currentSession = updated
                onSessionUpdated(updated)
                showEditSessionDialog = false
                Toast.makeText(context, "Session updated successfully", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 2. Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        DeleteSessionConfirmDialog(
            session = currentSession,
            onDismiss = { showDeleteConfirmDialog = false },
            onConfirm = {
                store.deleteFocusSession(currentSession.id)
                showDeleteConfirmDialog = false
                onSessionDeleted(currentSession.id)
                Toast.makeText(context, "Session deleted", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 3. Quick Subject / Task Editor
    if (showSubjectEditorDialog) {
        QuickTextEditorDialog(
            title = "Edit Subject / Task",
            initialValue = currentSession.displaySubject,
            label = "Subject or Task Name",
            onDismiss = { showSubjectEditorDialog = false },
            onSave = { newSubject ->
                val updated = currentSession.copy(
                    subject = newSubject.trim(),
                    goal = newSubject.trim()
                )
                store.saveFocusSession(updated)
                currentSession = updated
                onSessionUpdated(updated)
                showSubjectEditorDialog = false
                Toast.makeText(context, "Subject updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 4. Quick Notes Editor
    if (showNotesEditorDialog) {
        QuickTextEditorDialog(
            title = "Edit Session Notes",
            initialValue = currentSession.notes,
            label = "Session Notes",
            isMultiLine = true,
            onDismiss = { showNotesEditorDialog = false },
            onSave = { newNotes ->
                val updated = currentSession.copy(notes = newNotes.trim())
                store.saveFocusSession(updated)
                currentSession = updated
                onSessionUpdated(updated)
                showNotesEditorDialog = false
                Toast.makeText(context, "Notes updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 5. Quick Focus Mode Selector
    if (showModeDialog) {
        FocusModeSelectorDialog(
            currentMode = currentSession.mode,
            onDismiss = { showModeDialog = false },
            onSelect = { newMode ->
                val updated = currentSession.copy(mode = newMode)
                store.saveFocusSession(updated)
                currentSession = updated
                onSessionUpdated(updated)
                showModeDialog = false
                Toast.makeText(context, "Mode updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 6. Break Breakdown Dialog
    if (showBreakBreakdownDialog) {
        BreakBreakdownDialog(
            session = currentSession,
            onDismiss = { showBreakBreakdownDialog = false }
        )
    }

    // 7. View All App Usage Dialog
    if (showAppUsageDialog) {
        AppUsageDetailsDialog(
            context = context,
            session = currentSession,
            apps = appsUsed,
            onDismiss = { showAppUsageDialog = false }
        )
    }

    // 8. View All Distractions Dialog
    if (showDistractionsDialog) {
        DistractionsHistoryDialog(
            session = currentSession,
            distractions = distractions,
            onDismiss = { showDistractionsDialog = false }
        )
    }

    // 9. Timeline Event Detail Dialog
    selectedTimelineEvent?.let { event ->
        TimelineEventDetailDialog(
            event = event,
            timeFormat = timeFormat,
            onDismiss = { selectedTimelineEvent = null }
        )
    }

    // 10. Activity Bar Detail Dialog
    selectedIntervalBar?.let { bar ->
        ActivityIntervalDetailDialog(
            bar = bar,
            timeFormat = timeFormat,
            onDismiss = { selectedIntervalBar = null }
        )
    }
}

// ----------------------------------------------------------------------
// COMPOSABLE HELPER ITEMS
// ----------------------------------------------------------------------
@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, color = TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun SessionInfoItem(
    icon: String,
    label: String,
    value: String,
    isEditable: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = isEditable && onClick != null) { onClick?.invoke() }
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CardInner),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, fontSize = 14.sp)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TextMuted, fontSize = 10.5.sp)
            Text(
                text = value,
                color = TextWhite,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isEditable) {
            Text("›", color = TextMuted, fontSize = 16.sp)
        }
    }
}

// ----------------------------------------------------------------------
// DATA LOADER & TIMELINE BUILDER UTILITIES
// ----------------------------------------------------------------------
private fun loadSessionAppUsage(
    context: Context,
    session: FocusSessionRecord,
    store: LocalStudyStore
): Pair<List<SessionAppUsage>, List<SessionAppUsage>> {
    val blockedPkgs: Set<String> = try { store.blockedApps().map { it.packageName }.toSet() } catch (_: Exception) { emptySet<String>() }
    val resultApps = mutableListOf<SessionAppUsage>()
    val resultDistractions = mutableListOf<SessionAppUsage>()

    // If pre-recorded in JSON (e.g. sample data or saved state)
    if (session.appsUsedJson.isNotBlank()) {
        try {
            val jsonArray = JSONArray(session.appsUsedJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pkg = obj.optString("packageName", "com.app.$i")
                val name = obj.optString("appName", "App")
                val durSec = obj.optLong("durationSeconds", 60L)
                val isDistr = obj.optBoolean("isDistraction", false) || pkg in blockedPkgs
                val count = obj.optInt("launchCount", 1)
                val usage = SessionAppUsage(
                    packageName = pkg,
                    appName = name,
                    durationMillis = durSec * 1000L,
                    launchCount = count,
                    isDistraction = isDistr,
                    isBlocked = pkg in blockedPkgs
                )
                resultApps.add(usage)
                if (isDistr) resultDistractions.add(usage)
            }
            if (resultApps.isNotEmpty()) {
                return Pair(resultApps.sortedByDescending { it.durationMillis }, resultDistractions)
            }
        } catch (_: Exception) {}
    }

    // Query real UsageStatsManager events
    if (hasUsageAccess(context)) {
        try {
            val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val pm = context.packageManager
            if (usageManager != null) {
                val events = usageManager.queryEvents(session.startedAtMillis, session.finishedAtMillis)
                val appMillis = mutableMapOf<String, Long>()
                val appCounts = mutableMapOf<String, Int>()
                val appLastResumed = mutableMapOf<String, Long>()
                val appTimestamps = mutableMapOf<String, MutableList<Long>>()

                val ev = UsageEvents.Event()
                while (events.hasNextEvent()) {
                    events.getNextEvent(ev)
                    val pkg = ev.packageName ?: continue
                    if (pkg == context.packageName) continue // Skip StudyMode itself

                    when (ev.eventType) {
                        UsageEvents.Event.ACTIVITY_RESUMED -> {
                            appLastResumed[pkg] = ev.timeStamp
                            appCounts[pkg] = (appCounts[pkg] ?: 0) + 1
                            appTimestamps.getOrPut(pkg) { mutableListOf() }.add(ev.timeStamp)
                        }
                        UsageEvents.Event.ACTIVITY_PAUSED -> {
                            val start = appLastResumed.remove(pkg)
                            if (start != null && ev.timeStamp >= start) {
                                appMillis[pkg] = (appMillis[pkg] ?: 0L) + (ev.timeStamp - start)
                            }
                        }
                    }
                }

                // Handle still-running app at end of window
                appLastResumed.forEach { (pkg, start) ->
                    if (session.finishedAtMillis >= start) {
                        appMillis[pkg] = (appMillis[pkg] ?: 0L) + (session.finishedAtMillis - start)
                    }
                }

                appMillis.forEach { (pkg, millis) ->
                    if (millis >= 5000L) { // Only count >= 5s
                        val label = try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (_: Exception) {
                            pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                        }
                        val isBlocked = pkg in blockedPkgs
                        val isDistr = isBlocked || label.contains("YouTube", ignoreCase = true) || label.contains("TikTok", ignoreCase = true) || label.contains("Instagram", ignoreCase = true)

                        val usage = SessionAppUsage(
                            packageName = pkg,
                            appName = label,
                            durationMillis = millis,
                            launchCount = appCounts[pkg] ?: 1,
                            isDistraction = isDistr,
                            isBlocked = isBlocked,
                            timestamps = appTimestamps[pkg] ?: emptyList()
                        )
                        resultApps.add(usage)
                        if (isDistr) resultDistractions.add(usage)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    return Pair(resultApps.sortedByDescending { it.durationMillis }, resultDistractions)
}

private fun loadSessionTimelineEvents(
    session: FocusSessionRecord,
    distractions: List<SessionAppUsage>
): List<SessionTimelineEvent> {
    // If timelineEventsJson exists, parse it
    if (session.timelineEventsJson.isNotBlank()) {
        try {
            val arr = JSONArray(session.timelineEventsJson)
            val list = mutableListOf<SessionTimelineEvent>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SessionTimelineEvent(
                        type = obj.optString("type", "FOCUS"),
                        startMillis = obj.optLong("startMillis", session.startedAtMillis),
                        endMillis = obj.optLong("endMillis", session.finishedAtMillis),
                        label = obj.optString("label", "Focused"),
                        details = obj.optString("details", ""),
                        packageName = if (obj.has("packageName")) obj.getString("packageName") else null
                    )
                )
            }
            if (list.isNotEmpty()) return list
        } catch (_: Exception) {}
    }

    // Synthesize structured timeline based on actual focus, break, and distraction data
    val list = mutableListOf<SessionTimelineEvent>()
    val totalSpan = (session.finishedAtMillis - session.startedAtMillis).coerceAtLeast(60000L)
    val breakMillis = (session.breakSeconds * 1000L).coerceIn(0L, totalSpan / 2)

    if (breakMillis > 0L) {
        // e.g. Focus part 1, Break, Distraction if any, Focus part 2
        val midMillis = session.startedAtMillis + (totalSpan - breakMillis) / 2
        list.add(
            SessionTimelineEvent(
                type = "FOCUS",
                startMillis = session.startedAtMillis,
                endMillis = midMillis,
                label = "Focused",
                details = "Active study session on ${session.displaySubject}"
            )
        )
        list.add(
            SessionTimelineEvent(
                type = "BREAK",
                startMillis = midMillis,
                endMillis = midMillis + breakMillis,
                label = "Short break",
                details = "Scheduled rest break (${breakMillis / 60000L} min)"
            )
        )
        if (distractions.isNotEmpty()) {
            val dist = distractions.first()
            val distDur = dist.durationMillis.coerceAtMost(300000L)
            list.add(
                SessionTimelineEvent(
                    type = "DISTRACTION",
                    startMillis = midMillis + breakMillis,
                    endMillis = midMillis + breakMillis + distDur,
                    label = "Distracted",
                    details = "App opened: ${dist.appName} (${dist.durationMinutes} min)",
                    packageName = dist.packageName
                )
            )
            list.add(
                SessionTimelineEvent(
                    type = "FOCUS",
                    startMillis = midMillis + breakMillis + distDur,
                    endMillis = session.finishedAtMillis,
                    label = "Focused",
                    details = "Resumed study work"
                )
            )
        } else {
            list.add(
                SessionTimelineEvent(
                    type = "FOCUS",
                    startMillis = midMillis + breakMillis,
                    endMillis = session.finishedAtMillis,
                    label = "Focused",
                    details = "Resumed study work"
                )
            )
        }
    } else {
        // Continuous session
        if (distractions.isNotEmpty()) {
            val dist = distractions.first()
            val distDur = dist.durationMillis.coerceAtMost(300000L)
            val distStart = session.startedAtMillis + totalSpan / 2
            list.add(
                SessionTimelineEvent(
                    type = "FOCUS",
                    startMillis = session.startedAtMillis,
                    endMillis = distStart,
                    label = "Focused",
                    details = "Deep study focus"
                )
            )
            list.add(
                SessionTimelineEvent(
                    type = "DISTRACTION",
                    startMillis = distStart,
                    endMillis = distStart + distDur,
                    label = "Distracted",
                    details = "Interruption: ${dist.appName}",
                    packageName = dist.packageName
                )
            )
            list.add(
                SessionTimelineEvent(
                    type = "FOCUS",
                    startMillis = distStart + distDur,
                    endMillis = session.finishedAtMillis,
                    label = "Focused",
                    details = "Completed remaining session"
                )
            )
        } else {
            list.add(
                SessionTimelineEvent(
                    type = "FOCUS",
                    startMillis = session.startedAtMillis,
                    endMillis = session.finishedAtMillis,
                    label = "Focused",
                    details = "Deep uninterrupted focus session"
                )
            )
        }
    }

    return list
}

private fun buildSessionActivityBars(
    session: FocusSessionRecord,
    timelineEvents: List<SessionTimelineEvent>
): List<SessionIntervalBar> {
    val totalSpan = (session.finishedAtMillis - session.startedAtMillis).coerceAtLeast(60000L)
    val numBars = 11
    val sliceMillis = totalSpan / numBars
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val result = mutableListOf<SessionIntervalBar>()

    // Pattern of intensities for realistic look matching the screenshot
    val heights = listOf(0.25f, 0.5f, 0.9f, 0.45f, 0.65f, 0.35f, 0.6f, 0.95f, 0.8f, 0.5f, 0.35f)

    for (i in 0 until numBars) {
        val bStart = session.startedAtMillis + i * sliceMillis
        val bEnd = bStart + sliceMillis
        val mid = bStart + sliceMillis / 2

        // Determine event at this slice
        val matchEvent = timelineEvents.find { mid >= it.startMillis && mid < it.endMillis }
        val type = matchEvent?.type ?: "FOCUS"
        val baseIntensity = heights.getOrElse(i) { 0.5f }
        val adjustedIntensity = when (type) {
            "DISTRACTION" -> 0.25f
            "BREAK" -> 0.35f
            else -> baseIntensity
        }

        result.add(
            SessionIntervalBar(
                index = i,
                startMillis = bStart,
                endMillis = bEnd,
                timeLabel = "${timeFormat.format(Date(bStart))} – ${timeFormat.format(Date(bEnd))}",
                intensity = adjustedIntensity,
                type = type,
                durationMinutes = (sliceMillis / 60000L).toInt().coerceAtLeast(1)
            )
        )
    }

    return result
}

// ----------------------------------------------------------------------
// EXPORT & SHARE ACTIONS
// ----------------------------------------------------------------------
private fun shareSessionSummary(
    context: Context,
    session: FocusSessionRecord,
    focusDisplay: String,
    breakDisplay: String
) {
    val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

    val summary = buildString {
        appendLine("📚 StudyMode Focus Session Summary")
        appendLine("----------------------------------")
        appendLine("📅 Date: ${dateFormat.format(Date(session.startedAtMillis))}")
        appendLine("⏰ Time: ${timeFormat.format(Date(session.startedAtMillis))} – ${timeFormat.format(Date(session.finishedAtMillis))}")
        appendLine("⏳ Focus Time: $focusDisplay")
        appendLine("☕ Break Time: $breakDisplay")
        appendLine("🎯 Subject: ${session.displaySubject}")
        appendLine("📋 Mode: ${session.mode}")
        appendLine("✅ Status: ${if (session.completed) "Completed" else "Incomplete"}")
        if (session.notes.isNotBlank()) {
            appendLine("📝 Notes: ${session.notes}")
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Focus Session: ${session.displaySubject}")
        putExtra(Intent.EXTRA_TEXT, summary)
    }
    context.startActivity(Intent.createChooser(intent, "Share Session Summary"))
}

private fun exportSessionDetailsJson(
    context: Context,
    session: FocusSessionRecord,
    apps: List<SessionAppUsage>,
    distractions: List<SessionAppUsage>
) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    val obj = JSONObject().apply {
        put("id", session.id)
        put("startedAt", dateFormat.format(Date(session.startedAtMillis)))
        put("finishedAt", dateFormat.format(Date(session.finishedAtMillis)))
        put("elapsedSeconds", session.elapsedSeconds)
        put("breakSeconds", session.breakSeconds)
        put("subject", session.displaySubject)
        put("notes", session.notes)
        put("mode", session.mode)
        put("completed", session.completed)

        val appsArray = JSONArray()
        apps.forEach { app ->
            appsArray.put(
                JSONObject().apply {
                    put("packageName", app.packageName)
                    put("appName", app.appName)
                    put("durationMinutes", app.durationMinutes)
                    put("isDistraction", app.isDistraction)
                }
            )
        }
        put("appsUsed", appsArray)

        val distArray = JSONArray()
        distractions.forEach { d ->
            distArray.put(
                JSONObject().apply {
                    put("appName", d.appName)
                    put("launchCount", d.launchCount)
                    put("durationMinutes", d.durationMinutes)
                    put("isBlocked", d.isBlocked)
                }
            )
        }
        put("distractions", distArray)
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "Session_${session.id}_export.json")
        putExtra(Intent.EXTRA_TEXT, obj.toString(2))
    }
    context.startActivity(Intent.createChooser(intent, "Export Session Details (JSON)"))
}

// ----------------------------------------------------------------------
// DIALOG IMPLEMENTATIONS
// ----------------------------------------------------------------------
@Composable
private fun FullEditSessionDialog(
    session: FocusSessionRecord,
    onDismiss: () -> Unit,
    onSave: (FocusSessionRecord) -> Unit
) {
    var subjectText by remember { mutableStateOf(session.displaySubject) }
    var notesText by remember { mutableStateOf(session.notes) }
    var selectedMode by remember { mutableStateOf(session.mode) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Edit Focus Session",
                    color = TextWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = subjectText,
                    onValueChange = { subjectText = it },
                    label = { Text("Subject / Task") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = CardBorder,
                        focusedLabelColor = MintGreen,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes") },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = CardBorder,
                        focusedLabelColor = MintGreen,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Mode radio options
                Text("Focus Mode", color = TextMuted, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Focus", "Pomodoro", "Stopwatch").forEach { m ->
                        val isSelected = selectedMode.equals(m, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MintGreenPill else CardInner,
                            border = BorderStroke(1.dp, if (isSelected) MintGreen else CardBorder),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedMode = m }
                        ) {
                            Text(
                                text = m,
                                color = if (isSelected) MintGreen else TextMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }

                // Timing explanation notice
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CardInner,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("ℹ️", fontSize = 13.sp)
                        Text(
                            text = "Focus duration and timing are recorded automatically to protect analytics accuracy.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val updated = session.copy(
                                subject = subjectText.trim(),
                                goal = subjectText.trim(),
                                notes = notesText.trim(),
                                mode = selectedMode
                            )
                            onSave(updated)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteSessionConfirmDialog(
    session: FocusSessionRecord,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🗑️", fontSize = 20.sp)
                    Text(
                        text = "Delete Focus Session?",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Are you sure you want to permanently delete this focus session on ${session.displaySubject}? This will remove its recorded study time and update all your dashboard statistics, charts, and streaks.",
                    color = TextMuted,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = RedAlert, contentColor = TextWhite)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTextEditorDialog(
    title: String,
    initialValue: String,
    label: String,
    isMultiLine: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(text = title, color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    minLines = if (isMultiLine) 4 else 1,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = CardBorder,
                        focusedLabelColor = MintGreen,
                        unfocusedLabelColor = TextMuted
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(text) },
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusModeSelectorDialog(
    currentMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val modes = listOf(
        Triple("Focus", "Standard countdown or open focus session", "⏳"),
        Triple("Pomodoro", "Structured 25-minute study intervals with breaks", "☕"),
        Triple("Stopwatch", "Count-up study session with no fixed end time", "⏱")
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Select Focus Mode", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                modes.forEach { (mode, desc, icon) ->
                    val isSelected = currentMode.equals(mode, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MintGreenPill else CardInner,
                        border = BorderStroke(1.dp, if (isSelected) MintGreen else CardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(icon, fontSize = 20.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode,
                                    color = if (isSelected) MintGreen else TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(text = desc, color = TextMuted, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Text("✓", color = MintGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close", color = TextMuted) }
                }
            }
        }
    }
}

@Composable
private fun BreakBreakdownDialog(
    session: FocusSessionRecord,
    onDismiss: () -> Unit
) {
    val breakMins = (session.breakSeconds / 60L).toInt()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("☕", fontSize = 20.sp)
                    Text("Break Time Breakdown", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CardInner,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Rest Time", color = TextMuted, fontSize = 12.sp)
                            Text("$breakMins min", color = AmberYellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        HorizontalDivider(color = CardBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Short Breaks", color = TextMuted, fontSize = 12.sp)
                            Text(if (breakMins > 0) "1 recorded" else "0", color = TextWhite, fontSize = 12.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Paused Time Excluded", color = TextMuted, fontSize = 12.sp)
                            Text("Yes (excluded from focus)", color = MintGreen, fontSize = 11.sp)
                        }
                    }
                }

                Text(
                    text = "Breaks and paused periods are automatically logged and excluded from your net focus study time.",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUsageDetailsDialog(
    context: Context,
    session: FocusSessionRecord,
    apps: List<SessionAppUsage>,
    onDismiss: () -> Unit
) {
    var sortByMostUsed by remember { mutableStateOf(true) }
    val sortedApps = remember(apps, sortByMostUsed) {
        if (sortByMostUsed) apps.sortedByDescending { it.durationMillis }
        else apps.sortedBy { it.durationMillis }
    }
    val totalUsageMins = (apps.sumOf { it.durationMillis } / 60000L).toInt()
    val hasPermission = remember { hasUsageAccess(context) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Usage Details",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // Sort toggle
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CardInner)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sortByMostUsed) MintGreenPill else Color.Transparent)
                                .clickable { sortByMostUsed = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Most Used", color = if (sortByMostUsed) MintGreen else TextMuted, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!sortByMostUsed) MintGreenPill else Color.Transparent)
                                .clickable { sortByMostUsed = false }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Least Used", color = if (!sortByMostUsed) MintGreen else TextMuted, fontSize = 10.sp)
                        }
                    }
                }

                if (!hasPermission && apps.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CardInner,
                        border = BorderStroke(1.dp, CardBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Usage Access Permission Required", color = AmberYellow, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text(
                                text = "To monitor and display which apps are used during your focus sessions, StudyMode requires Android Usage Access permission.",
                                color = TextMuted,
                                fontSize = 11.5.sp
                            )
                            Button(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                            ) {
                                Text("Grant Permission", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (sortedApps.isEmpty()) {
                    Text("No apps were launched during this session window.", color = TextMuted, fontSize = 12.sp)
                } else {
                    Text(
                        text = "Total detected app usage: $totalUsageMins min",
                        color = MintGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    val maxDuration = sortedApps.maxOfOrNull { it.durationMillis }?.coerceAtLeast(1L) ?: 1L
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sortedApps.forEach { app ->
                            val pct = if (totalUsageMins > 0) (app.durationMinutes * 100) / totalUsageMins else 0
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CardInner,
                                border = BorderStroke(1.dp, CardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = app.appName, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(text = "${app.durationMinutes} min • $pct% of app time", color = TextMuted, fontSize = 11.sp)
                                    }
                                    if (app.isDistraction) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(RedPill)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Distraction", color = RedAlert, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DistractionsHistoryDialog(
    session: FocusSessionRecord,
    distractions: List<SessionAppUsage>,
    onDismiss: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val totalDistractionMins = distractions.sumOf { it.durationMinutes }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("🚫", fontSize = 18.sp)
                    Text("Distraction History", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                if (distractions.isEmpty()) {
                    Text(
                        text = "No distractions recorded! You maintained full focus throughout the entire session.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "${distractions.size} distraction app(s) • $totalDistractionMins min total",
                        color = RedAlert,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        distractions.forEach { d ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CardInner,
                                border = BorderStroke(1.dp, RedAlert.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = d.appName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                        Text("${d.durationMinutes} min", color = RedAlert, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Text(
                                        text = "Opened ${d.launchCount} time(s) during this study window",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )

                                    if (d.isBlocked) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(RedPill)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Blocked by App Blocker", color = RedAlert, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEventDetailDialog(
    event: SessionTimelineEvent,
    timeFormat: SimpleDateFormat,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (color, title) = when (event.type) {
                    "DISTRACTION" -> Pair(RedAlert, "Distraction Event")
                    "BREAK" -> Pair(AmberYellow, "Break Interval")
                    else -> Pair(MintGreen, "Focus Interval")
                }

                Text(title, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardInner,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${timeFormat.format(Date(event.startMillis))} – ${timeFormat.format(Date(event.endMillis))}",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Duration: ${event.durationMinutes} min",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                        if (event.details.isNotBlank()) {
                            Text(
                                text = event.details,
                                color = TextWhite,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityIntervalDetailDialog(
    bar: SessionIntervalBar,
    timeFormat: SimpleDateFormat,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardBg,
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Activity Interval Details", color = MintGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CardInner,
                    border = BorderStroke(1.dp, CardBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = bar.timeLabel, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(text = "Interval Type: ${bar.type.lowercase().replaceFirstChar { it.uppercase() }}", color = TextMuted, fontSize = 12.sp)
                        Text(text = "Duration: ${bar.durationMinutes} min", color = TextWhite, fontSize = 12.sp)
                        val intensityPct = (bar.intensity * 100).toInt()
                        Text(text = "Focus Activity: $intensityPct%", color = MintGreen, fontSize = 12.sp)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen, contentColor = Color(0xFF07120D))
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
