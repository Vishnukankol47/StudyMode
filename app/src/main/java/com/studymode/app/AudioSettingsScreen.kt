package com.studymode.app

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val CardInner = Color(0xFF0A1410)
private val NeonGreen = Color(0xFF22C55E)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)
private val SurfaceGreen = Color(0xFF16281F)

@Composable
fun AudioSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val currentSettings = remember { store.windDownSettings() }

    var continueInBackground by remember { mutableStateOf(currentSettings.continueInBackground) }
    var showMediaControls by remember { mutableStateOf(currentSettings.showMediaControls) }
    var fadeOutAtEnd by remember { mutableStateOf(currentSettings.fadeOutAtEnd) }
    var defaultTimerMins by remember { mutableIntStateOf(currentSettings.defaultSleepTimerMinutes) }
    var resumeFromLastPos by remember { mutableStateOf(currentSettings.resumeFromLastPosition) }
    var pauseOnCalls by remember { mutableStateOf(currentSettings.pauseOnIncomingCalls) }
    var allowNotifControls by remember { mutableStateOf(currentSettings.allowNotificationControls) }

    var timerDropdownExpanded by remember { mutableStateOf(false) }

    fun saveSettings() {
        val updated = currentSettings.copy(
            continueInBackground = continueInBackground,
            showMediaControls = showMediaControls,
            fadeOutAtEnd = fadeOutAtEnd,
            defaultSleepTimerMinutes = defaultTimerMins,
            resumeFromLastPosition = resumeFromLastPos,
            pauseOnIncomingCalls = pauseOnCalls,
            allowNotificationControls = allowNotifControls,
        )
        store.saveWindDownSettings(updated)
    }

    BackHandler(enabled = true) {
        saveSettings()
        onBack()
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
                onClick = {
                    saveSettings()
                    onBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                GreenArrowBackIcon(modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text = "Audio Settings",
                color = TextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Playback Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Playback",
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingSwitchRow(
                            title = "Continue playing in background",
                            subtitle = "Audio keeps playing when screen is off or you leave the app.",
                            checked = continueInBackground,
                            onCheckedChange = {
                                continueInBackground = it
                                saveSettings()
                            },
                        )

                        HorizontalDivider(color = CardBorder, thickness = 1.dp)

                        SettingSwitchRow(
                            title = "Show media controls",
                            subtitle = "Display controls on lock screen and notification panel.",
                            checked = showMediaControls,
                            onCheckedChange = {
                                showMediaControls = it
                                saveSettings()
                            },
                        )

                        HorizontalDivider(color = CardBorder, thickness = 1.dp)

                        SettingSwitchRow(
                            title = "Fade out at end",
                            subtitle = "Gradually lower volume in last 2 minutes.",
                            checked = fadeOutAtEnd,
                            onCheckedChange = {
                                fadeOutAtEnd = it
                                saveSettings()
                            },
                        )
                    }
                }
            }

            // Sleep Timer Default Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Sleep timer default",
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { timerDropdownExpanded = true }
                            .padding(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("Default timer", color = TextWhite, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$defaultTimerMins min", color = NeonGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(6.dp))
                                Text("▼", color = TextMuted, fontSize = 10.sp)
                            }
                        }

                        DropdownMenu(
                            expanded = timerDropdownExpanded,
                            onDismissRequest = { timerDropdownExpanded = false },
                            modifier = Modifier.background(CardBg),
                        ) {
                            listOf(15, 30, 45, 60).forEach { mins ->
                                DropdownMenuItem(
                                    text = { Text("$mins min", color = TextWhite) },
                                    onClick = {
                                        defaultTimerMins = mins
                                        timerDropdownExpanded = false
                                        saveSettings()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Audio Behavior Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Audio behavior",
                    color = NeonGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        SettingSwitchRow(
                            title = "Resume from last position",
                            subtitle = "Continue from where you left off.",
                            checked = resumeFromLastPos,
                            onCheckedChange = {
                                resumeFromLastPos = it
                                saveSettings()
                            },
                        )

                        HorizontalDivider(color = CardBorder, thickness = 1.dp)

                        SettingSwitchRow(
                            title = "Pause on incoming calls",
                            subtitle = "Auto pause during calls and resume after.",
                            checked = pauseOnCalls,
                            onCheckedChange = {
                                pauseOnCalls = it
                                saveSettings()
                            },
                        )

                        HorizontalDivider(color = CardBorder, thickness = 1.dp)

                        SettingSwitchRow(
                            title = "Allow notification controls",
                            subtitle = "Show playback controls in notification panel.",
                            checked = allowNotifControls,
                            onCheckedChange = {
                                allowNotifControls = it
                                saveSettings()
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = TextMuted, fontSize = 11.5.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF07120D),
                checkedTrackColor = NeonGreen,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = SurfaceGreen,
            ),
        )
    }
}

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
        drawPath(path = path, color = NeonGreen, style = Stroke(width = 3.dp.toPx()))
    }
}
