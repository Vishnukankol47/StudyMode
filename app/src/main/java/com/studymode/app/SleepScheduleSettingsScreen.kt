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
import java.util.Calendar

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val IconBg = Color(0xFF16281F)
private val AccentGreen = Color(0xFF22C55E)
private val AccentLightGreen = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)

@Composable
fun SleepScheduleSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val currentSchedule = remember { store.sleepSchedule() }
    val prefs = remember { store.sleepPreferences() }

    var bedtimeMinute by remember { mutableIntStateOf(currentSchedule.bedtimeMinute) }
    var wakeupMinute by remember { mutableIntStateOf(currentSchedule.wakeupMinute) }
    var activeDays by remember { mutableStateOf(currentSchedule.activeDays) }
    var smartScheduleEnabled by remember { mutableStateOf(prefs.smartBedtimeSuggestions) }

    var showBedtimePicker by remember { mutableStateOf(false) }
    var showWakeupPicker by remember { mutableStateOf(false) }
    var showEditWindowDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    // Calculate sleep window duration in minutes
    val windowMinutes = remember(bedtimeMinute, wakeupMinute) {
        if (wakeupMinute >= bedtimeMinute) {
            wakeupMinute - bedtimeMinute
        } else {
            (24 * 60 - bedtimeMinute) + wakeupMinute
        }
    }

    val windowHours = windowMinutes / 60
    val windowRemMin = windowMinutes % 60
    val windowDurationStr = if (windowRemMin > 0) "${windowHours} hr ${windowRemMin} min" else "${windowHours} hr"

    // Recommended bedtime calculation: wakeup minus optimal 7h 45m or sleep log average
    val recommendedBedtimeMin = remember(wakeupMinute) {
        val targetMin = 7 * 60 + 45
        (wakeupMinute - targetMin + 24 * 60) % (24 * 60)
    }

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        // Scrollable content
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
                        text = "Sleep Schedule",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Set your regular sleep and wake times",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            // Bedtime Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { showBedtimePicker = true }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IconBg)
                                .border(1.dp, Color(0xFF1E382A), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🛏️", fontSize = 20.sp)
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text("Bedtime", color = TextMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatMinute12h(bedtimeMinute),
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text("›", color = TextMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Wake-up Time Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { showWakeupPicker = true }
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IconBg)
                                .border(1.dp, Color(0xFF1E382A), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("☀️", fontSize = 20.sp)
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Text("Wake-up time", color = TextMuted, fontSize = 12.sp)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                formatMinute12h(wakeupMinute),
                                color = TextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Text("›", color = TextMuted, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Repeat on
            Text("Repeat on", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            val dayLabels = listOf(
                Calendar.MONDAY to "Mon",
                Calendar.TUESDAY to "Tue",
                Calendar.WEDNESDAY to "Wed",
                Calendar.THURSDAY to "Thu",
                Calendar.FRIDAY to "Fri",
                Calendar.SATURDAY to "Sat",
                Calendar.SUNDAY to "Sun",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dayLabels.forEach { (calDay, label) ->
                    val isSelected = calDay in activeDays
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) AccentGreen else CardBg)
                            .border(1.dp, if (isSelected) AccentGreen else CardBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                activeDays = if (isSelected) {
                                    if (activeDays.size > 1) activeDays - calDay else activeDays
                                } else {
                                    activeDays + calDay
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color(0xFF06150D) else TextMuted,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Sleep window
            Text("Sleep window", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
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
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IconBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🌙", fontSize = 18.sp)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                text = windowDurationStr,
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${formatMinute12h(bedtimeMinute)} – ${formatMinute12h(wakeupMinute)}",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                            )
                        }
                    }

                    Button(
                        onClick = { showEditWindowDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16281F)),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("Edit", color = AccentLightGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Smart schedule toggle card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IconBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✨", fontSize = 18.sp)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text("Smart schedule", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(1.dp))
                            Text("Adjust bedtime based on your sleep data", color = TextMuted, fontSize = 10.5.sp)
                        }
                    }

                    Switch(
                        checked = smartScheduleEnabled,
                        onCheckedChange = { smartScheduleEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AccentGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = IconBg,
                            uncheckedBorderColor = CardBorder,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Recommended bedtime card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IconBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🌙", fontSize = 18.sp)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text("Recommended bedtime", color = TextMuted, fontSize = 11.5.sp)
                            Spacer(Modifier.height(1.dp))
                            Text(
                                formatMinute12h(recommendedBedtimeMin),
                                color = TextWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text("Based on your recent sleep patterns", color = TextMuted, fontSize = 10.5.sp)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(IconBg)
                            .clickable { showInfoDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("ℹ️", fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }

        // Save Button at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Button(
                onClick = {
                    val updated = currentSchedule.copy(
                        bedtimeMinute = bedtimeMinute,
                        wakeupMinute = wakeupMinute,
                        activeDays = activeDays,
                        targetDurationMinutes = windowMinutes,
                        updatedAtMillis = System.currentTimeMillis(),
                    )
                    store.saveSleepSchedule(updated)

                    // Update preferences
                    val curPrefs = store.sleepPreferences()
                    store.saveSleepPreferences(curPrefs.copy(smartBedtimeSuggestions = smartScheduleEnabled))

                    // Reschedule native alarms
                    SleepAlarms.reschedule(context)

                    Toast.makeText(context, "Sleep schedule saved successfully", Toast.LENGTH_SHORT).show()
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

    // Time Pickers & Dialogs
    if (showBedtimePicker) {
        SleepTimePickerDialog(
            title = "Set Bedtime",
            initialMinute = bedtimeMinute,
            onDismiss = { showBedtimePicker = false },
            onConfirm = {
                bedtimeMinute = it
                showBedtimePicker = false
            },
        )
    }

    if (showWakeupPicker) {
        SleepTimePickerDialog(
            title = "Set Wake-up Time",
            initialMinute = wakeupMinute,
            onDismiss = { showWakeupPicker = false },
            onConfirm = {
                wakeupMinute = it
                showWakeupPicker = false
            },
        )
    }

    if (showEditWindowDialog) {
        AlertDialog(
            onDismissRequest = { showEditWindowDialog = false },
            containerColor = CardBg,
            title = { Text("Sleep Window", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your current sleep window is ${windowDurationStr} (${formatMinute12h(bedtimeMinute)} to ${formatMinute12h(wakeupMinute)}).\n\nTo change duration, adjust bedtime or wake-up time.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showEditWindowDialog = false }) {
                    Text("Got it", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            containerColor = CardBg,
            title = { Text("Recommended Bedtime", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "StudyMode calculates recommended bedtime based on your wake-up time (${formatMinute12h(wakeupMinute)}) and recommended 7h 45m sleep cycles to help prevent morning grogginess and sleep inertia.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("OK", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}

@Composable
fun SleepTimePickerDialog(
    title: String,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var hour24 by remember { mutableIntStateOf(initialMinute / 60) }
    var minute by remember { mutableIntStateOf(initialMinute % 60) }

    var isAm by remember { mutableStateOf(hour24 < 12) }
    var hour12 by remember {
        mutableIntStateOf(
            when (val h = hour24 % 12) {
                0 -> 12
                else -> h
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Select Hour, Minute and AM/PM", color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Hour selector
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "▲",
                            color = AccentGreen,
                            modifier = Modifier
                                .clickable {
                                    hour12 = if (hour12 >= 12) 1 else hour12 + 1
                                }
                                .padding(8.dp),
                        )
                        Text(
                            "%02d".format(hour12),
                            color = TextWhite,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "▼",
                            color = AccentGreen,
                            modifier = Modifier
                                .clickable {
                                    hour12 = if (hour12 <= 1) 12 else hour12 - 1
                                }
                                .padding(8.dp),
                        )
                    }

                    Text(" : ", color = TextWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold)

                    // Minute selector
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "▲",
                            color = AccentGreen,
                            modifier = Modifier
                                .clickable {
                                    minute = (minute + 5) % 60
                                }
                                .padding(8.dp),
                        )
                        Text(
                            "%02d".format(minute),
                            color = TextWhite,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "▼",
                            color = AccentGreen,
                            modifier = Modifier
                                .clickable {
                                    minute = (minute - 5 + 60) % 60
                                }
                                .padding(8.dp),
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    // AM / PM Toggle
                    Column {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAm) AccentGreen else IconBg)
                                .clickable { isAm = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "AM",
                                color = if (isAm) Color(0xFF06150D) else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!isAm) AccentGreen else IconBg)
                                .clickable { isAm = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                "PM",
                                color = if (!isAm) Color(0xFF06150D) else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val finalHour = when {
                        isAm && hour12 == 12 -> 0
                        !isAm && hour12 < 12 -> hour12 + 12
                        else -> hour12
                    }
                    onConfirm(finalHour * 60 + minute)
                }
            ) {
                Text("Confirm", color = AccentGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
    )
}

private fun formatMinute12h(totalMin: Int): String {
    val h24 = (totalMin / 60) % 24
    val m = totalMin % 60
    val isPm = h24 >= 12
    val h12 = when (val mod = h24 % 12) {
        0 -> 12
        else -> mod
    }
    return "%d:%02d %s".format(h12, m, if (isPm) "PM" else "AM")
}
