package com.studymode.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

data class BuiltInSound(
    val name: String,
    val category: String, // Nature, Ambient, Instrumental
    val iconEmoji: String,
    val gradientColors: List<Color>,
)

@Composable
fun RelaxingSoundsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }

    val mixEnabled = WindDownAudioPlaybackManager.mixRelaxingSound.value
    val currentSelectedSound = WindDownAudioPlaybackManager.selectedRelaxingSound.value
    val mixVolume = WindDownAudioPlaybackManager.relaxingSoundVolume.value

    val sounds = remember {
        listOf(
            BuiltInSound("Rain", "Nature", "🌧️", listOf(Color(0xFF1B2A47), Color(0xFF101B2B))),
            BuiltInSound("Forest", "Nature", "🌲", listOf(Color(0xFF1A3A22), Color(0xFF0D2214))),
            BuiltInSound("Ocean", "Nature", "🌊", listOf(Color(0xFF0E3A40), Color(0xFF082024))),
            BuiltInSound("White Noise", "Ambient", "〰️", listOf(Color(0xFF2B303A), Color(0xFF191C22))),
            BuiltInSound("Fireplace", "Ambient", "🔥", listOf(Color(0xFF3D2114), Color(0xFF24130A))),
            BuiltInSound("Night Ambience", "Ambient", "🌙", listOf(Color(0xFF1F1D36), Color(0xFF11101E))),
        )
    }

    val filteredSounds = remember(selectedCategory) {
        if (selectedCategory == "All") sounds
        else sounds.filter { it.category.equals(selectedCategory, true) }
    }

    BackHandler(enabled = true) {
        onBack()
    }

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
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                GreenArrowBackIcon(modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = "Relaxing Sounds",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Soothing sounds for a calmer mind",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // Category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("All", "Nature", "Ambient", "Instrumental").forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) NeonGreen else CardBg)
                        .border(1.dp, if (isSelected) NeonGreen else CardBorder, RoundedCornerShape(20.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color(0xFF07120D) else TextWhite,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // 2-Column Sound Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            items(filteredSounds, key = { it.name }) { sound ->
                val isCurrent = currentSelectedSound == sound.name
                val isPlayingNow = isCurrent && (WindDownAudioPlaybackManager.isPlaying.value || mixEnabled)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = SolidColor(if (isCurrent) NeonGreen else CardBorder)
                    ),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable {
                                WindDownAudioPlaybackManager.updateMixSettings(
                                    context = context,
                                    enabled = true,
                                    sound = sound.name,
                                    volume = mixVolume,
                                )
                            }
                            .padding(12.dp),
                    ) {
                        // Background gradient preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.verticalGradient(sound.gradientColors)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(sound.iconEmoji, fontSize = 28.sp)
                        }

                        // Bottom row: Sound Title & Play Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = sound.name,
                                color = TextWhite,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (isPlayingNow) "⏸" else "▶",
                                    color = NeonGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Mix with my audio Card at Bottom
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardBg),
            border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🎵", fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Mix with my audio",
                                color = TextWhite,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Play relaxing sound together with your selected audio.",
                                color = TextMuted,
                                fontSize = 11.5.sp,
                            )
                        }
                    }

                    Switch(
                        checked = mixEnabled,
                        onCheckedChange = { isEnabled ->
                            WindDownAudioPlaybackManager.updateMixSettings(
                                context = context,
                                enabled = isEnabled,
                                sound = currentSelectedSound,
                                volume = mixVolume,
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF07120D),
                            checkedTrackColor = NeonGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceGreen,
                        ),
                    )
                }

                if (mixEnabled) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Relaxing sound level: ${(mixVolume * 100).toInt()}%",
                        color = TextMuted,
                        fontSize = 11.sp,
                    )
                    Slider(
                        value = mixVolume,
                        onValueChange = { vol ->
                            WindDownAudioPlaybackManager.updateMixSettings(
                                context = context,
                                enabled = true,
                                sound = currentSelectedSound,
                                volume = vol,
                            )
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreen,
                            activeTrackColor = NeonGreen,
                            inactiveTrackColor = CardInner,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
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
