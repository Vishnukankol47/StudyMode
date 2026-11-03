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
import kotlin.math.roundToInt

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val IconBg = Color(0xFF16281F)
private val AccentGreen = Color(0xFF22C55E)
private val AccentLightGreen = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)

@Composable
fun SleepSoundsSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var rememberLastSound by remember { mutableStateOf(prefs.rememberLastPlayedSound) }
    var defaultVolume by remember { mutableFloatStateOf(prefs.soundDefaultVolume) }
    var fadeOutAtEnd by remember { mutableStateOf(prefs.soundFadeOutAtEnd) }
    var mixWithAudio by remember { mutableStateOf(prefs.mixWithMyAudio) }
    var mixVolume by remember { mutableFloatStateOf(prefs.soundMixVolume) }
    var playThroughSpeaker by remember { mutableStateOf(prefs.playThroughSpeaker) }

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
                        text = "Sleep Sounds Settings",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Manage your sleep audio preferences",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Remember last played sound
                WindDownToggleRow(
                    icon = "🔄",
                    title = "Remember last played sound",
                    subtitle = "Resume the last sound when opened",
                    checked = rememberLastSound,
                    onCheckedChange = { rememberLastSound = it },
                )

                // Default volume slider card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(IconBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("🔊", fontSize = 16.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Default volume",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "${(defaultVolume * 100).roundToInt()}%",
                                color = AccentLightGreen,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Slider(
                            value = defaultVolume,
                            onValueChange = { defaultVolume = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGreen,
                                activeTrackColor = AccentGreen,
                                inactiveTrackColor = IconBg,
                            ),
                        )
                    }
                }

                // Fade out at end
                WindDownToggleRow(
                    icon = "🌙",
                    title = "Fade out at end",
                    subtitle = "Gradually lower volume in last 2 minutes",
                    checked = fadeOutAtEnd,
                    onCheckedChange = { fadeOutAtEnd = it },
                )

                // Mix with my audio
                WindDownToggleRow(
                    icon = "🎵",
                    title = "Mix with my audio",
                    subtitle = "Allow background sound with your music or podcast",
                    checked = mixWithAudio,
                    onCheckedChange = { mixWithAudio = it },
                )

                // Default mix volume slider card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(IconBg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("🍃", fontSize = 16.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "Default mix volume",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                "${(mixVolume * 100).roundToInt()}%",
                                color = AccentLightGreen,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Slider(
                            value = mixVolume,
                            onValueChange = { mixVolume = it },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = AccentGreen,
                                activeTrackColor = AccentGreen,
                                inactiveTrackColor = IconBg,
                            ),
                        )
                    }
                }

                // Play through phone speaker
                WindDownToggleRow(
                    icon = "📱",
                    title = "Play through phone speaker",
                    subtitle = "Use device speaker when no headphones",
                    checked = playThroughSpeaker,
                    onCheckedChange = { playThroughSpeaker = it },
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
                        rememberLastPlayedSound = rememberLastSound,
                        soundDefaultVolume = defaultVolume,
                        soundFadeOutAtEnd = fadeOutAtEnd,
                        mixWithMyAudio = mixWithAudio,
                        soundMixVolume = mixVolume,
                        playThroughSpeaker = playThroughSpeaker,
                    )
                    store.saveSleepPreferences(updated)

                    Toast.makeText(context, "Sleep sounds settings saved", Toast.LENGTH_SHORT).show()
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
}
