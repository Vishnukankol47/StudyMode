package com.studymode.app

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val CardInner = Color(0xFF0A1410)
private val NeonGreen = Color(0xFF22C55E)
private val NeonGreenLight = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)
private val SurfaceGreen = Color(0xFF16281F)

@Composable
fun AudioPlayerScreen(
    store: LocalStudyStore,
    track: WindDownAudioTrack,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val currentTrack = WindDownAudioPlaybackManager.currentTrack.value ?: track
    val isPlaying = WindDownAudioPlaybackManager.isPlaying.value
    val currentPos = WindDownAudioPlaybackManager.currentPositionMs.value
    val duration = (if (WindDownAudioPlaybackManager.durationMs.value > 0) WindDownAudioPlaybackManager.durationMs.value else currentTrack.durationMs).coerceAtLeast(1L)

    val isShuffle = WindDownAudioPlaybackManager.isShuffle.value
    val repeatMode = WindDownAudioPlaybackManager.repeatMode.value
    val volume = WindDownAudioPlaybackManager.volume.value
    val continueUntilFinished = WindDownAudioPlaybackManager.continueUntilFinished.value
    val sleepTimerRemaining = WindDownAudioPlaybackManager.sleepTimerRemainingSeconds.value

    var isFav by remember(currentTrack.id) { mutableStateOf(currentTrack.isFavourite) }
    var sleepTimerMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
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

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = currentTrack.title,
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    text = "My Audio • ${currentTrack.category}",
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            }

            IconButton(
                onClick = { /* Menu */ },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                Text("⋮", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))

            // Album Artwork (Night Sky / Moon scenery)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF0A1410))
                        )
                    )
                    .border(1.dp, CardBorder, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                NightSceneryArt(modifier = Modifier.fillMaxSize())
            }

            // Title, Subtitle, Heart Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (currentTrack.artist.isNotBlank()) currentTrack.artist else "StudyMode Relaxing Audio",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }

                IconButton(
                    onClick = {
                        isFav = !isFav
                        store.setWindDownTrackFavourite(currentTrack.id, isFav)
                    },
                ) {
                    Text(
                        text = if (isFav) "💚" else "🤍",
                        fontSize = 22.sp,
                    )
                }
            }

            // Scrubber Slider & Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                val progressFraction = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                Slider(
                    value = progressFraction,
                    onValueChange = { frac ->
                        val target = (frac * duration).toLong()
                        WindDownAudioPlaybackManager.seekTo(context, target)
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonGreen,
                        activeTrackColor = NeonGreen,
                        inactiveTrackColor = CardInner,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(formatMs(currentPos), color = TextMuted, fontSize = 11.5.sp)
                    Text(formatMs(duration), color = TextMuted, fontSize = 11.5.sp)
                }
            }

            // Playback Controls Row: Shuffle, Prev, Play/Pause, Next, Repeat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Shuffle
                IconButton(
                    onClick = { WindDownAudioPlaybackManager.setShuffle(!isShuffle) },
                ) {
                    Text(
                        "🔀",
                        fontSize = 18.sp,
                        color = if (isShuffle) NeonGreen else TextMuted,
                    )
                }

                // Previous
                IconButton(
                    onClick = { WindDownAudioPlaybackManager.skipPrev(context) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardBg),
                ) {
                    Text("⏮", fontSize = 18.sp, color = TextWhite)
                }

                // Play / Pause (Big Neon Circle)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                        .clickable { WindDownAudioPlaybackManager.togglePlayPause(context) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isPlaying) "⏸" else "▶",
                        fontSize = 24.sp,
                        color = Color(0xFF07120D),
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Next
                IconButton(
                    onClick = { WindDownAudioPlaybackManager.skipNext(context) },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(CardBg),
                ) {
                    Text("⏭", fontSize = 18.sp, color = TextWhite)
                }

                // Repeat
                IconButton(
                    onClick = { WindDownAudioPlaybackManager.toggleRepeat() },
                ) {
                    Text(
                        when (repeatMode) {
                            1 -> "🔁"
                            2 -> "🔂"
                            else -> "↩️"
                        },
                        fontSize = 18.sp,
                        color = if (repeatMode > 0) NeonGreen else TextMuted,
                    )
                }
            }

            // Volume Slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔈", fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = volume,
                    onValueChange = { v -> WindDownAudioPlaybackManager.setVolume(context, v) },
                    colors = SliderDefaults.colors(
                        thumbColor = NeonGreen,
                        activeTrackColor = NeonGreen,
                        inactiveTrackColor = CardInner,
                    ),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text("🔊", fontSize = 14.sp)
            }

            // Sleep Timer Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .clickable { sleepTimerMenuExpanded = true }
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🌙", fontSize = 16.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Sleep timer", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val statusText = if (sleepTimerRemaining != null) {
                                val m = sleepTimerRemaining / 60
                                val s = sleepTimerRemaining % 60
                                "Active: %02d:%02d remaining".format(m, s)
                            } else {
                                "Stop audio after a set time"
                            }
                            Text(statusText, color = if (sleepTimerRemaining != null) NeonGreenLight else TextMuted, fontSize = 11.5.sp)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (sleepTimerRemaining != null) "${WindDownAudioPlaybackManager.sleepTimerMinutes.value} min" else "Off",
                            color = NeonGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("▼", color = TextMuted, fontSize = 10.sp)
                    }
                }

                DropdownMenu(
                    expanded = sleepTimerMenuExpanded,
                    onDismissRequest = { sleepTimerMenuExpanded = false },
                    modifier = Modifier.background(CardBg),
                ) {
                    listOf(0 to "Off", 15 to "15 min", 30 to "30 min", 45 to "45 min", 60 to "60 min").forEach { (mins, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = TextWhite) },
                            onClick = {
                                WindDownAudioPlaybackManager.setSleepTimer(context, mins)
                                sleepTimerMenuExpanded = false
                            },
                        )
                    }
                }
            }

            // Continue Until Finished Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("♾️", fontSize = 16.sp, color = NeonGreen)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Continue until finished", color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Play until this track ends", color = TextMuted, fontSize = 11.5.sp)
                        }
                    }

                    Switch(
                        checked = continueUntilFinished,
                        onCheckedChange = { WindDownAudioPlaybackManager.setContinueUntilFinished(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF07120D),
                            checkedTrackColor = NeonGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceGreen,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NightSceneryArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Moon
        drawCircle(
            color = Color(0xFFFFFBEA),
            radius = w * 0.08f,
            center = Offset(w * 0.72f, h * 0.28f),
        )
        // Soft moon glow
        drawCircle(
            color = Color(0x33FFFBEA),
            radius = w * 0.14f,
            center = Offset(w * 0.72f, h * 0.28f),
        )

        // Distant Mountains
        val mountainPath = Path().apply {
            moveTo(0f, h * 0.65f)
            lineTo(w * 0.3f, h * 0.42f)
            lineTo(w * 0.55f, h * 0.58f)
            lineTo(w * 0.8f, h * 0.38f)
            lineTo(w, h * 0.6f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(mountainPath, color = Color(0xFF0F1B24))

        // Water reflection / foreground
        drawRect(
            color = Color(0xFF081216),
            topLeft = Offset(0f, h * 0.68f),
            size = Size(w, h * 0.32f),
        )
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = ms / 1000L
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
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
