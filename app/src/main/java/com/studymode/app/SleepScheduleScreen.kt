package com.studymode.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Calendar

private val SleepDarkBg = Color(0xFF07120D)
private val SleepCard = Color(0xFF111E18)
private val SleepCardBorder = Color(0xFF1B2C23)
private val SleepGreen = Color(0xFF22C55E)
private val SleepGreenLight = Color(0xFF4ADE80)
private val SleepText = Color(0xFFE8F5E9)
private val SleepMuted = Color(0xFF8BA396)
private val SleepSurface = Color(0xFF16251E)
private val SleepAmber = Color(0xFFF59E0B)
private val SleepRed = Color(0xFFEF4444)

private val LEAD_TIME_OPTIONS = listOf(10, 15, 30, 45, 60)

enum class ScheduleTab {
    WEEKDAYS,
    WEEKENDS,
}

@Composable
fun SleepScheduleScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current

    // Initial saved schedules from storage
    val initialWeekday = remember { store.weekdaySleepSchedule() }
    val initialWeekend = remember { store.weekendSleepSchedule() }

    // Active mutable schedule states for both tabs
    var weekdayState by remember { mutableStateOf(initialWeekday) }
    var weekendState by remember { mutableStateOf(initialWeekend) }

    // Selected tab
    var selectedTab by rememberSaveable { mutableStateOf(ScheduleTab.WEEKDAYS) }

    // Check if there are unsaved changes
    val hasUnsavedChanges = remember(weekdayState, weekendState, initialWeekday, initialWeekend) {
        weekdayState != initialWeekday || weekendState != initialWeekend
    }

    var showDiscardDialog by remember { mutableStateOf(false) }

    // Intercept back button
    BackHandler(enabled = true) {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = SleepCard,
            title = {
                Text(
                    "Discard changes?",
                    color = SleepText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            },
            text = {
                Text(
                    "You have unsaved changes to your sleep schedule. Do you want to discard them or continue editing?",
                    color = SleepMuted,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SleepRed),
                ) {
                    Text("Discard", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDiscardDialog = false },
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(SleepCardBorder)),
                ) {
                    Text("Continue Editing", color = SleepText)
                }
            },
        )
    }

    val currentSchedule = if (selectedTab == ScheduleTab.WEEKDAYS) weekdayState else weekendState

    fun updateCurrent(updated: SleepSchedule) {
        if (selectedTab == ScheduleTab.WEEKDAYS) {
            weekdayState = updated
        } else {
            weekendState = updated
        }
    }

    // Calculations for current schedule
    val plannedDuration = calculateDurationMinutes(currentSchedule.bedtimeMinute, currentSchedule.wakeupMinute)
    val isIdenticalTime = currentSchedule.bedtimeMinute == currentSchedule.wakeupMinute
    val targetDuration = if (currentSchedule.targetDurationMinutes in 240..720) currentSchedule.targetDurationMinutes else 480
    val durationDiff = plannedDuration - targetDuration

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepDarkBg)
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
                onClick = {
                    if (hasUnsavedChanges) {
                        showDiscardDialog = true
                    } else {
                        onBack()
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SleepCard),
            ) {
                Text(
                    "←",
                    color = SleepText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = "Sleep Schedule",
                    color = SleepText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Set your ideal bedtime and wake-up target",
                    color = SleepMuted,
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
            // Segmented Tabs: Weekdays vs Weekends
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SleepCard)
                    .border(1.dp, SleepCardBorder, RoundedCornerShape(16.dp))
                    .padding(4.dp),
            ) {
                // Weekdays Tab
                val isWeekdays = selectedTab == ScheduleTab.WEEKDAYS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isWeekdays) Color(0xFF1E3A2B) else Color.Transparent)
                        .border(
                            width = if (isWeekdays) 1.dp else 0.dp,
                            color = if (isWeekdays) SleepGreen.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { selectedTab = ScheduleTab.WEEKDAYS }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Weekdays",
                            color = if (isWeekdays) SleepGreen else SleepText,
                            fontSize = 15.sp,
                            fontWeight = if (isWeekdays) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = "Mon - Fri",
                            color = if (isWeekdays) SleepGreenLight.copy(alpha = 0.8f) else SleepMuted,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Weekends Tab
                val isWeekends = selectedTab == ScheduleTab.WEEKENDS
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isWeekends) Color(0xFF1E3A2B) else Color.Transparent)
                        .border(
                            width = if (isWeekends) 1.dp else 0.dp,
                            color = if (isWeekends) SleepGreen.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { selectedTab = ScheduleTab.WEEKENDS }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Weekends",
                            color = if (isWeekends) SleepGreen else SleepText,
                            fontSize = 15.sp,
                            fontWeight = if (isWeekends) FontWeight.Bold else FontWeight.Medium,
                        )
                        Text(
                            text = "Sat - Sun",
                            color = if (isWeekends) SleepGreenLight.copy(alpha = 0.8f) else SleepMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Bedtime Card & Time Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SleepCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SleepCardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SleepSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🌙", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Bedtime",
                                color = SleepText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Aim for consistent sleep onset",
                                color = SleepMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    WheelTimePicker(
                        minuteOfDay = currentSchedule.bedtimeMinute,
                        onTimeChange = { newMinute ->
                            updateCurrent(currentSchedule.copy(bedtimeMinute = newMinute))
                        },
                    )
                }
            }

            // Wake-up Time Card & Time Picker
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SleepCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SleepCardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SleepSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("☀️", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Wake-up Time",
                                color = SleepText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Alarm and morning schedule",
                                color = SleepMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    WheelTimePicker(
                        minuteOfDay = currentSchedule.wakeupMinute,
                        onTimeChange = { newMinute ->
                            updateCurrent(currentSchedule.copy(wakeupMinute = newMinute))
                        },
                    )
                }
            }

            // Target Sleep Duration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SleepCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SleepCardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SleepSurface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🎯", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Target Sleep Duration",
                                color = SleepText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Recommended: 7 - 9 hours for adults",
                                color = SleepMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Stepper: Minus, Duration Display, Plus
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(SleepDarkBg)
                            .border(1.dp, SleepCardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Minus button
                        val canDecrease = targetDuration > 240 // min 4h
                        IconButton(
                            onClick = {
                                if (canDecrease) {
                                    updateCurrent(currentSchedule.copy(targetDurationMinutes = targetDuration - 15))
                                }
                            },
                            enabled = canDecrease,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (canDecrease) SleepCard else SleepCard.copy(alpha = 0.4f))
                                .border(1.dp, SleepCardBorder, CircleShape),
                        ) {
                            Text(
                                "−",
                                color = if (canDecrease) SleepText else SleepMuted.copy(alpha = 0.4f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Target Duration Text
                        val targetH = targetDuration / 60
                        val targetM = targetDuration % 60
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (targetM > 0) "${targetH}h ${targetM}m" else "${targetH} hours",
                                color = SleepGreen,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Target goal",
                                color = SleepMuted,
                                fontSize = 11.sp,
                            )
                        }

                        // Plus button
                        val canIncrease = targetDuration < 720 // max 12h
                        IconButton(
                            onClick = {
                                if (canIncrease) {
                                    updateCurrent(currentSchedule.copy(targetDurationMinutes = targetDuration + 15))
                                }
                            },
                            enabled = canIncrease,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (canIncrease) SleepCard else SleepCard.copy(alpha = 0.4f))
                                .border(1.dp, SleepCardBorder, CircleShape),
                        ) {
                            Text(
                                "+",
                                color = if (canIncrease) SleepText else SleepMuted.copy(alpha = 0.4f),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // Planned Window & Validation Comparison Banner
            if (isIdenticalTime) {
                // Error banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SleepRed.copy(alpha = 0.15f))
                        .border(1.dp, SleepRed.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Bedtime and wake-up time cannot be identical.",
                            color = Color(0xFFFF8B8B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                // Info banner
                val plannedH = plannedDuration / 60
                val plannedM = plannedDuration % 60
                val targetH = targetDuration / 60
                val targetM = targetDuration % 60

                val targetStr = if (targetM > 0) "${targetH}h ${targetM}m" else "${targetH}h"
                val diffStr = when {
                    durationDiff == 0 -> "Matches your $targetStr target perfectly"
                    durationDiff < 0 -> "${-durationDiff}m less than your $targetStr target"
                    else -> "${durationDiff}m more than your $targetStr target"
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (durationDiff < 0) SleepAmber.copy(alpha = 0.12f) else Color(0xFF142E20))
                        .border(
                            1.dp,
                            if (durationDiff < 0) SleepAmber.copy(alpha = 0.35f) else SleepGreen.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Planned sleep: ${plannedH}h ${plannedM}m",
                                    color = SleepText,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (durationDiff < 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("⚠️", fontSize = 12.sp)
                                }
                            }
                            Text(
                                text = diffStr,
                                color = if (durationDiff < 0) SleepAmber else SleepGreenLight,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }

            // Reminders Section Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SleepCard),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(SleepCardBorder)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Bedtime Reminder Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SleepSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🔔", fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Bedtime Reminder",
                                    color = SleepText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Get notified before your bedtime",
                                    color = SleepMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        Switch(
                            checked = currentSchedule.reminderEnabled,
                            onCheckedChange = { isEnabled ->
                                updateCurrent(currentSchedule.copy(reminderEnabled = isEnabled))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF07120D),
                                checkedTrackColor = SleepGreen,
                                uncheckedThumbColor = SleepMuted,
                                uncheckedTrackColor = SleepSurface,
                            ),
                        )
                    }

                    // Configurable lead time selector
                    if (currentSchedule.reminderEnabled) {
                        Spacer(Modifier.height(12.dp))

                        var leadTimeDropdownExpanded by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SleepSurface)
                                    .border(1.dp, SleepCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { leadTimeDropdownExpanded = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "⏱ ${currentSchedule.reminderAdvanceMinutes} min before bedtime",
                                    color = SleepGreenLight,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("▼", color = SleepMuted, fontSize = 10.sp)
                            }

                            DropdownMenu(
                                expanded = leadTimeDropdownExpanded,
                                onDismissRequest = { leadTimeDropdownExpanded = false },
                                modifier = Modifier.background(SleepCard),
                            ) {
                                LEAD_TIME_OPTIONS.forEach { mins ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "$mins minutes before bedtime",
                                                color = if (mins == currentSchedule.reminderAdvanceMinutes) SleepGreen else SleepText,
                                                fontWeight = if (mins == currentSchedule.reminderAdvanceMinutes) FontWeight.Bold else FontWeight.Normal,
                                            )
                                        },
                                        onClick = {
                                            updateCurrent(currentSchedule.copy(reminderAdvanceMinutes = mins))
                                            leadTimeDropdownExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = SleepCardBorder, thickness = 1.dp)
                    Spacer(Modifier.height(14.dp))

                    // Wake-up Reminder Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SleepSurface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("⏰", fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Wake-up Reminder",
                                    color = SleepText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Notification at your wake-up time",
                                    color = SleepMuted,
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        Switch(
                            checked = currentSchedule.wakeupReminderEnabled,
                            onCheckedChange = { isEnabled ->
                                updateCurrent(currentSchedule.copy(wakeupReminderEnabled = isEnabled))
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF07120D),
                                checkedTrackColor = SleepGreen,
                                uncheckedThumbColor = SleepMuted,
                                uncheckedTrackColor = SleepSurface,
                            ),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        // Save Schedule Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Button(
                onClick = {
                    // Validation
                    if (weekdayState.bedtimeMinute == weekdayState.wakeupMinute) {
                        Toast.makeText(context, "Weekday bedtime and wake-up time cannot be identical.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (weekendState.bedtimeMinute == weekendState.wakeupMinute) {
                        Toast.makeText(context, "Weekend bedtime and wake-up time cannot be identical.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    // Save both schedules independently
                    store.saveSleepSchedule(weekdayState)
                    store.saveSleepSchedule(weekendState)

                    // Schedule / Cancel alarms
                    SleepAlarms.reschedule(context)

                    // Notification permission check feedback
                    val anyReminderEnabled = weekdayState.reminderEnabled || weekdayState.wakeupReminderEnabled ||
                            weekendState.reminderEnabled || weekendState.wakeupReminderEnabled

                    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }

                    if (anyReminderEnabled && !hasPermission) {
                        Toast.makeText(
                            context,
                            "Sleep schedule saved. Please enable notifications in Settings for reminders to ring.",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else if (anyReminderEnabled) {
                        Toast.makeText(context, "Sleep schedule saved. Reminders active.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Sleep schedule saved.", Toast.LENGTH_SHORT).show()
                    }

                    onSaved()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SleepGreen,
                    contentColor = Color(0xFF051B0F),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Text(
                    text = "Save Schedule",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF051B0F),
                )
            }
        }
    }
}

/**
 * Modern Wheel / Stepper Time Picker matching screenshot design:
 * Hours (1..12), Minutes (00..59), and AM/PM toggle.
 */
@Composable
private fun WheelTimePicker(
    minuteOfDay: Int,
    onTimeChange: (Int) -> Unit,
) {
    val totalMinutes = (minuteOfDay % 1440 + 1440) % 1440
    val rawHour = totalMinutes / 60
    val currentMinute = totalMinutes % 60
    val isPm = rawHour >= 12

    val currentHour12 = when {
        rawHour == 0 -> 12
        rawHour > 12 -> rawHour - 12
        else -> rawHour
    }

    fun computeMinuteOfDay(h12: Int, m: Int, pm: Boolean): Int {
        val h24 = when {
            pm && h12 < 12 -> h12 + 12
            !pm && h12 == 12 -> 0
            else -> h12
        }
        return h24 * 60 + m
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SleepDarkBg)
            .border(1.dp, SleepCardBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Hours Column (prev, current, next)
        val prevHour = if (currentHour12 == 1) 12 else currentHour12 - 1
        val nextHour = if (currentHour12 == 12) 1 else currentHour12 + 1

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(64.dp),
        ) {
            // Upper Hour
            Text(
                text = "%02d".format(prevHour),
                color = SleepMuted.copy(alpha = 0.35f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        onTimeChange(computeMinuteOfDay(prevHour, currentMinute, isPm))
                    }
                    .padding(vertical = 4.dp),
            )

            // Current Hour
            Text(
                text = "%02d".format(currentHour12),
                color = SleepText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp),
            )

            // Lower Hour
            Text(
                text = "%02d".format(nextHour),
                color = SleepMuted.copy(alpha = 0.35f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        onTimeChange(computeMinuteOfDay(nextHour, currentMinute, isPm))
                    }
                    .padding(vertical = 4.dp),
            )
        }

        // Separator Colon
        Text(
            text = ":",
            color = SleepGreen,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp),
        )

        // Minutes Column (prev, current, next in steps of 5, or tap)
        val prevMin = (currentMinute - 5 + 60) % 60
        val nextMin = (currentMinute + 5) % 60

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(64.dp),
        ) {
            // Upper Minute
            Text(
                text = "%02d".format(prevMin),
                color = SleepMuted.copy(alpha = 0.35f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        onTimeChange(computeMinuteOfDay(currentHour12, prevMin, isPm))
                    }
                    .padding(vertical = 4.dp),
            )

            // Current Minute
            Text(
                text = "%02d".format(currentMinute),
                color = SleepText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 2.dp),
            )

            // Lower Minute
            Text(
                text = "%02d".format(nextMin),
                color = SleepMuted.copy(alpha = 0.35f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clickable {
                        onTimeChange(computeMinuteOfDay(currentHour12, nextMin, isPm))
                    }
                    .padding(vertical = 4.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        // AM / PM Vertical Pill Selector
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SleepSurface)
                .border(1.dp, SleepCardBorder, RoundedCornerShape(12.dp))
                .padding(3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // AM Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isPm) SleepGreen else Color.Transparent)
                    .clickable {
                        if (isPm) {
                            onTimeChange(computeMinuteOfDay(currentHour12, currentMinute, false))
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "AM",
                    color = if (!isPm) Color(0xFF051B0F) else SleepMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // PM Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPm) SleepGreen else Color.Transparent)
                    .clickable {
                        if (!isPm) {
                            onTimeChange(computeMinuteOfDay(currentHour12, currentMinute, true))
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "PM",
                    color = if (isPm) Color(0xFF051B0F) else SleepMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Calculates sleep duration in minutes correctly handling schedules crossing midnight.
 */
private fun calculateDurationMinutes(bedtimeMinute: Int, wakeupMinute: Int): Int {
    return if (wakeupMinute >= bedtimeMinute) {
        wakeupMinute - bedtimeMinute
    } else {
        (1440 - bedtimeMinute) + wakeupMinute
    }
}
