package com.studymode.app

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val IconBg = Color(0xFF16281F)
private val AccentGreen = Color(0xFF22C55E)
private val AccentLightGreen = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)

@Composable
fun TrackingRemindersSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var trackAutomatically by remember { mutableStateOf(prefs.trackSleepAutomatically) }
    var sleepGoalMinutes by remember { mutableIntStateOf(prefs.sleepGoalMinutes) }
    var bedtimeReminder by remember { mutableStateOf(prefs.bedtimeReminderEnabled) }
    var reminderAdvanceMinutes by remember { mutableIntStateOf(prefs.bedtimeReminderAdvanceMinutes) }
    var wakeupAlarm by remember { mutableStateOf(prefs.wakeupAlarmEnabled) }
    var alarmSound by remember { mutableStateOf(prefs.alarmSound) }
    var morningNotification by remember { mutableStateOf(prefs.morningNotificationEnabled) }

    var showGoalDialog by remember { mutableStateOf(false) }
    var showReminderLeadDialog by remember { mutableStateOf(false) }
    var showAlarmSoundDialog by remember { mutableStateOf(false) }

    val goalHours = sleepGoalMinutes / 60
    val goalRemMin = sleepGoalMinutes % 60
    val goalStr = if (goalRemMin > 0) "${goalHours} hr ${goalRemMin} min" else "${goalHours} hr"

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, CardBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Tracking & Reminders",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Manage sleep tracking and alerts",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Track sleep automatically
                WindDownToggleRow(
                    icon = "📊",
                    title = "Track sleep automatically",
                    subtitle = "Use device sensors and usage data",
                    checked = trackAutomatically,
                    onCheckedChange = { trackAutomatically = it },
                )

                // Sleep goal row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { showGoalDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IconBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🎯", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Sleep goal", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(1.dp))
                                Text("Set your daily sleep goal", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(goalStr, color = AccentLightGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Text("›", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Bedtime reminder
                WindDownToggleRow(
                    icon = "🔔",
                    title = "Bedtime reminder",
                    subtitle = "Remind you to start wind-down",
                    checked = bedtimeReminder,
                    onCheckedChange = { bedtimeReminder = it },
                )

                // Reminder time
                if (bedtimeReminder) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .clickable { showReminderLeadDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Reminder time",
                                color = TextWhite,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "$reminderAdvanceMinutes min before",
                                    color = AccentLightGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("›", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Wake-up alarm
                WindDownToggleRow(
                    icon = "⏰",
                    title = "Wake-up alarm",
                    subtitle = "Sound alarm at wake-up time",
                    checked = wakeupAlarm,
                    onCheckedChange = { wakeupAlarm = it },
                )

                // Alarm sound
                if (wakeupAlarm) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .clickable { showAlarmSoundDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Alarm sound",
                                color = TextWhite,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    alarmSound,
                                    color = AccentLightGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("›", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Morning notification
                WindDownToggleRow(
                    icon = "☀️",
                    title = "Morning notification",
                    subtitle = "Show sleep summary after waking up",
                    checked = morningNotification,
                    onCheckedChange = { morningNotification = it },
                )
            }

            Spacer(Modifier.height(28.dp))
        }

        // Save Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Button(
                onClick = {
                    val updated = prefs.copy(
                        trackSleepAutomatically = trackAutomatically,
                        sleepGoalMinutes = sleepGoalMinutes,
                        bedtimeReminderEnabled = bedtimeReminder,
                        bedtimeReminderAdvanceMinutes = reminderAdvanceMinutes,
                        wakeupAlarmEnabled = wakeupAlarm,
                        alarmSound = alarmSound,
                        morningNotificationEnabled = morningNotification,
                    )
                    store.saveSleepPreferences(updated)

                    // Also sync sleep schedule target duration
                    val currentSched = store.sleepSchedule()
                    store.saveSleepSchedule(
                        currentSched.copy(
                            targetDurationMinutes = sleepGoalMinutes,
                            reminderEnabled = bedtimeReminder,
                            reminderAdvanceMinutes = reminderAdvanceMinutes,
                            wakeupReminderEnabled = wakeupAlarm,
                        )
                    )

                    SleepAlarms.reschedule(context)

                    Toast.makeText(context, "Tracking & reminder settings saved", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(
                    text = "Save",
                    color = Color(0xFF06150D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Dialogs
    if (showGoalDialog) {
        val goalOptions = listOf(
            6 * 60 to "6 hr",
            6 * 60 + 30 to "6 hr 30 min",
            7 * 60 to "7 hr",
            7 * 60 + 30 to "7 hr 30 min",
            8 * 60 to "8 hr",
            8 * 60 + 30 to "8 hr 30 min",
            9 * 60 to "9 hr",
        )
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            containerColor = CardBg,
            title = { Text("Set Sleep Goal", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    goalOptions.forEach { (mins, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sleepGoalMinutes = mins
                                    showGoalDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(
                                selected = sleepGoalMinutes == mins,
                                onClick = {
                                    sleepGoalMinutes = mins
                                    showGoalDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentGreen,
                                    unselectedColor = TextMuted,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = TextWhite, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
        )
    }

    if (showReminderLeadDialog) {
        val leadOptions = listOf(10, 15, 30, 45, 60)
        AlertDialog(
            onDismissRequest = { showReminderLeadDialog = false },
            containerColor = CardBg,
            title = { Text("Bedtime Reminder Lead Time", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    leadOptions.forEach { mins ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    reminderAdvanceMinutes = mins
                                    showReminderLeadDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(
                                selected = reminderAdvanceMinutes == mins,
                                onClick = {
                                    reminderAdvanceMinutes = mins
                                    showReminderLeadDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentGreen,
                                    unselectedColor = TextMuted,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$mins minutes before", color = TextWhite, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReminderLeadDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
        )
    }

    if (showAlarmSoundDialog) {
        val tones = listOf("Gentle Tone", "Morning Birds", "Chimes", "Rising Sun", "Digital Bell")
        AlertDialog(
            onDismissRequest = { showAlarmSoundDialog = false },
            containerColor = CardBg,
            title = { Text("Select Alarm Sound", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    tones.forEach { tone ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    alarmSound = tone
                                    showAlarmSoundDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(
                                selected = alarmSound == tone,
                                onClick = {
                                    alarmSound = tone
                                    showAlarmSoundDialog = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentGreen,
                                    unselectedColor = TextMuted,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tone, color = TextWhite, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAlarmSoundDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
        )
    }
}
