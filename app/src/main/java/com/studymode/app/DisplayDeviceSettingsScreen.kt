package com.studymode.app

import android.content.Intent
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
fun DisplayDeviceSettingsScreen(
    store: LocalStudyStore,
    onNavigateToCalmingDisplay: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var nightMode by remember { mutableStateOf(prefs.enableNightMode) }
    var nightModeSchedule by remember { mutableStateOf(prefs.nightModeSchedule) }
    var reduceBlueLight by remember { mutableStateOf(prefs.reduceBlueLight) }
    var screenDimming by remember { mutableStateOf(prefs.screenDimmingDuringSleep) }
    var keepScreenOff by remember { mutableStateOf(prefs.keepScreenOff) }
    var lockDevice by remember { mutableStateOf(prefs.lockDeviceDuringSleep) }

    var showBlueLightDialog by remember { mutableStateOf(false) }
    var showLockDeviceDialog by remember { mutableStateOf(false) }

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
                        text = "Display & Device",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Adjust device settings for better sleep",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Enable night mode
                WindDownToggleRow(
                    icon = "🌙",
                    title = "Enable night mode",
                    subtitle = "Use dark theme automatically",
                    checked = nightMode,
                    onCheckedChange = { nightMode = it },
                )

                // Night mode schedule
                WindDownToggleRow(
                    icon = "⏰",
                    title = "Night mode schedule",
                    subtitle = "Follow your sleep schedule",
                    checked = nightModeSchedule,
                    onCheckedChange = { nightModeSchedule = it },
                )

                // Reduce blue light
                WindDownToggleRow(
                    icon = "👁️",
                    title = "Reduce blue light",
                    subtitle = "Apply warm screen filter",
                    checked = reduceBlueLight,
                    onCheckedChange = { checked ->
                        reduceBlueLight = checked
                        if (checked) {
                            showBlueLightDialog = true
                        }
                    },
                )

                // Screen dimming during sleep
                WindDownToggleRow(
                    icon = "🔅",
                    title = "Screen dimming during sleep",
                    subtitle = "Lower brightness automatically",
                    checked = screenDimming,
                    onCheckedChange = { screenDimming = it },
                )

                // Keep screen off
                WindDownToggleRow(
                    icon = "📱",
                    title = "Keep screen off",
                    subtitle = "Prevent screen from turning on",
                    checked = keepScreenOff,
                    onCheckedChange = { keepScreenOff = it },
                )

                // Lock device during sleep
                WindDownToggleRow(
                    icon = "🔒",
                    title = "Lock device during sleep",
                    subtitle = "Avoid accidental phone use",
                    checked = lockDevice,
                    onCheckedChange = { checked ->
                        lockDevice = checked
                        if (checked) {
                            showLockDeviceDialog = true
                        }
                    },
                )

                // Show sleep screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onNavigateToCalmingDisplay() }
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
                                Text("✨", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Show sleep screen", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(1.dp))
                                Text("Display a calming screen when locked", color = TextMuted, fontSize = 11.sp)
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
                        enableNightMode = nightMode,
                        nightModeSchedule = nightModeSchedule,
                        reduceBlueLight = reduceBlueLight,
                        screenDimmingDuringSleep = screenDimming,
                        keepScreenOff = keepScreenOff,
                        lockDeviceDuringSleep = lockDevice,
                    )
                    store.saveSleepPreferences(updated)

                    Toast.makeText(context, "Display & device settings saved", Toast.LENGTH_SHORT).show()
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

    if (showBlueLightDialog) {
        AlertDialog(
            onDismissRequest = { showBlueLightDialog = false },
            containerColor = CardBg,
            title = { Text("Blue Light Filter", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "StudyMode applies a warm amber tone during sleep hours. To enable device-wide Night Light / Eye Comfort shield, tap Open Display Settings.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBlueLightDialog = false
                        try {
                            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {}
                    }
                ) {
                    Text("Open Display Settings", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBlueLightDialog = false }) {
                    Text("OK", color = TextMuted)
                }
            },
        )
    }

    if (showLockDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showLockDeviceDialog = false },
            containerColor = CardBg,
            title = { Text("Lock Device During Sleep", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "StudyMode keeps your display off and prompts locking when bedtime starts to minimize accidental wake-ups and distraction.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLockDeviceDialog = false }) {
                    Text("Understood", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
