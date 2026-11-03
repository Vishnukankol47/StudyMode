package com.studymode.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
fun WindDownSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var durationMinutes by remember { mutableIntStateOf(prefs.windDownDurationMinutes) }
    var autoStart by remember { mutableStateOf(prefs.autoStartWindDown) }
    var appBlocking by remember { mutableStateOf(prefs.appBlockingDuringWindDown) }
    var screenDimming by remember { mutableStateOf(prefs.screenDimmingWindDown) }
    var doNotDisturb by remember { mutableStateOf(prefs.doNotDisturbWindDown) }
    var showReminder by remember { mutableStateOf(prefs.showWindDownReminder) }
    var reminderAdvanceMinutes by remember { mutableIntStateOf(prefs.windDownReminderAdvanceMinutes) }

    var showReminderTimeDialog by remember { mutableStateOf(false) }

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
                        text = "Wind-down Settings",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Configure your pre-sleep routine",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            // Default duration Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(IconBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("⏰", fontSize = 18.sp)
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                "Default wind-down duration",
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "How long before bed to start wind-down",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        listOf(15, 30, 60).forEach { mins ->
                            val isSelected = durationMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) AccentGreen else IconBg)
                                    .border(1.dp, if (isSelected) AccentGreen else CardBorder, RoundedCornerShape(12.dp))
                                    .clickable { durationMinutes = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (mins == 30) "🍃 " else "⏱️ ",
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "$mins min",
                                        color = if (isSelected) Color(0xFF06150D) else TextWhite,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Toggles
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WindDownToggleRow(
                    icon = "▶️",
                    title = "Auto start wind-down",
                    subtitle = "Automatically start at scheduled time",
                    checked = autoStart,
                    onCheckedChange = { autoStart = it },
                )

                WindDownToggleRow(
                    icon = "🚫",
                    title = "App blocking during wind-down",
                    subtitle = "Enable distraction blocking",
                    checked = appBlocking,
                    onCheckedChange = { appBlocking = it },
                )

                WindDownToggleRow(
                    icon = "🌙",
                    title = "Screen dimming",
                    subtitle = "Reduce screen brightness",
                    checked = screenDimming,
                    onCheckedChange = { screenDimming = it },
                )

                WindDownToggleRow(
                    icon = "🔕",
                    title = "Do Not Disturb",
                    subtitle = "Silence notifications",
                    checked = doNotDisturb,
                    onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            if (!nm.isNotificationPolicyAccessGranted) {
                                Toast.makeText(context, "Please grant Do Not Disturb permission in system settings", Toast.LENGTH_LONG).show()
                                try {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {}
                            }
                        }
                        doNotDisturb = checked
                    },
                )

                WindDownToggleRow(
                    icon = "🔔",
                    title = "Show reminder",
                    subtitle = "Notify before wind-down starts",
                    checked = showReminder,
                    onCheckedChange = { showReminder = it },
                )
            }

            Spacer(Modifier.height(10.dp))

            // Reminder time row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { showReminderTimeDialog = true }
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

            Spacer(Modifier.height(24.dp))
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
                        windDownDurationMinutes = durationMinutes,
                        autoStartWindDown = autoStart,
                        appBlockingDuringWindDown = appBlocking,
                        screenDimmingWindDown = screenDimming,
                        doNotDisturbWindDown = doNotDisturb,
                        showWindDownReminder = showReminder,
                        windDownReminderAdvanceMinutes = reminderAdvanceMinutes,
                    )
                    store.saveSleepPreferences(updated)

                    Toast.makeText(context, "Wind-down settings saved", Toast.LENGTH_SHORT).show()
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

    if (showReminderTimeDialog) {
        val options = listOf(5, 10, 15, 30)
        AlertDialog(
            onDismissRequest = { showReminderTimeDialog = false },
            containerColor = CardBg,
            title = { Text("Reminder Lead Time", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { mins ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    reminderAdvanceMinutes = mins
                                    showReminderTimeDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(
                                selected = reminderAdvanceMinutes == mins,
                                onClick = {
                                    reminderAdvanceMinutes = mins
                                    showReminderTimeDialog = false
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
                TextButton(onClick = { showReminderTimeDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
        )
    }
}

@Composable
fun WindDownToggleRow(
    icon: String,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
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
                    Text(icon, fontSize = 18.sp)
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(title, color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(1.dp))
                    Text(subtitle, color = TextMuted, fontSize = 11.sp)
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
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
}
