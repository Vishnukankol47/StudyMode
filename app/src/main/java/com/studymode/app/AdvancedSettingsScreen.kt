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
fun AdvancedSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var smartSuggestions by remember { mutableStateOf(prefs.smartBedtimeSuggestions) }
    var adjustForWeekends by remember { mutableStateOf(prefs.adjustForWeekends) }
    var minimumSleepHours by remember { mutableFloatStateOf(prefs.minimumSleepGoalHours) }
    var vibrationOnWakeup by remember { mutableStateOf(prefs.vibrationOnWakeup) }
    var adaptiveReminders by remember { mutableStateOf(prefs.adaptiveReminders) }
    var betaFeatures by remember { mutableStateOf(prefs.betaFeaturesEnabled) }

    var showMinGoalDialog by remember { mutableStateOf(false) }
    var showBetaDialog by remember { mutableStateOf(false) }

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
                        text = "Advanced Settings",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Additional options",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Smart bedtime suggestions
                WindDownToggleRow(
                    icon = "✨",
                    title = "Smart bedtime suggestions",
                    subtitle = "Get AI recommendations",
                    checked = smartSuggestions,
                    onCheckedChange = { smartSuggestions = it },
                )

                // Adjust for weekends
                WindDownToggleRow(
                    icon = "📅",
                    title = "Adjust for weekends",
                    subtitle = "Use different schedule on weekends",
                    checked = adjustForWeekends,
                    onCheckedChange = { adjustForWeekends = it },
                )

                // Minimum sleep goal row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { showMinGoalDialog = true }
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
                                Text("🌙", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Minimum sleep goal", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(1.dp))
                                Text("Set minimum target", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val minStr = if (minimumSleepHours % 1f == 0f) "${minimumSleepHours.toInt()} hr" else "$minimumSleepHours hr"
                            Text(minStr, color = AccentLightGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(6.dp))
                            Text("›", color = TextMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Vibration on wake-up
                WindDownToggleRow(
                    icon = "📳",
                    title = "Vibration on wake-up",
                    subtitle = "Vibrate along with alarm",
                    checked = vibrationOnWakeup,
                    onCheckedChange = { vibrationOnWakeup = it },
                )

                // Adaptive reminders
                WindDownToggleRow(
                    icon = "🔄",
                    title = "Adaptive reminders",
                    subtitle = "Adjust based on your behavior",
                    checked = adaptiveReminders,
                    onCheckedChange = { adaptiveReminders = it },
                )

                // Beta features row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { showBetaDialog = true }
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
                                Text("🧪", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Beta features", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(1.dp))
                                Text("Try experimental features", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Text("›", color = TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
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
                        smartBedtimeSuggestions = smartSuggestions,
                        adjustForWeekends = adjustForWeekends,
                        minimumSleepGoalHours = minimumSleepHours,
                        vibrationOnWakeup = vibrationOnWakeup,
                        adaptiveReminders = adaptiveReminders,
                        betaFeaturesEnabled = betaFeatures,
                    )
                    store.saveSleepPreferences(updated)

                    SleepAlarms.reschedule(context)

                    Toast.makeText(context, "Advanced settings saved", Toast.LENGTH_SHORT).show()
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

    // Min Goal Dialog
    if (showMinGoalDialog) {
        val options = listOf(5.0f, 6.0f, 6.5f, 7.0f, 7.5f, 8.0f, 8.5f, 9.0f)
        AlertDialog(
            onDismissRequest = { showMinGoalDialog = false },
            containerColor = CardBg,
            title = { Text("Minimum Sleep Target", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    options.forEach { hours ->
                        val label = if (hours % 1f == 0f) "${hours.toInt()} hours" else "$hours hours"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    minimumSleepHours = hours
                                    showMinGoalDialog = false
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            RadioButton(
                                selected = minimumSleepHours == hours,
                                onClick = {
                                    minimumSleepHours = hours
                                    showMinGoalDialog = false
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
                TextButton(onClick = { showMinGoalDialog = false }) {
                    Text("Close", color = TextMuted)
                }
            },
        )
    }

    // Beta Features Dialog
    if (showBetaDialog) {
        AlertDialog(
            onDismissRequest = { showBetaDialog = false },
            containerColor = CardBg,
            title = { Text("Experimental Beta Features", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Preview cutting-edge sleep intelligence capabilities:",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Binaural Beats Engine", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Delta/Theta frequency mixing", color = TextMuted, fontSize = 11.sp)
                        }
                        Switch(
                            checked = betaFeatures,
                            onCheckedChange = { betaFeatures = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentGreen,
                                uncheckedTrackColor = IconBg,
                            ),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sleep Chronotype Quiz", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                            Text("Lion, Bear, Wolf, or Dolphin analysis", color = TextMuted, fontSize = 11.sp)
                        }
                        Text("Active", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBetaDialog = false }) {
                    Text("Done", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
