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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun WindDownActiveSessionScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToPlayer: (WindDownAudioTrack) -> Unit,
) {
    val context = LocalContext.current

    val isPaused = WindDownSessionManager.isPaused.value
    val remainingSec = WindDownSessionManager.remainingSeconds.value
    val progress = WindDownSessionManager.progress.value

    val blockApps = WindDownSessionManager.blockAppsEnabled.value
    val screenDimming = WindDownSessionManager.screenDimmingEnabled.value
    val dnd = WindDownSessionManager.doNotDisturbEnabled.value
    val blockedAppCount = remember { store.blockedApps().count { it.blocked } }

    val currentTrack = WindDownAudioPlaybackManager.currentTrack.value
    val isAudioPlaying = WindDownAudioPlaybackManager.isPlaying.value
    val currentPos = WindDownAudioPlaybackManager.currentPositionMs.value
    val duration = (WindDownAudioPlaybackManager.durationMs.value).coerceAtLeast(1L)

    var showEndConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        onBack()
    }

    if (showEndConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showEndConfirmDialog = false },
            containerColor = CardBg,
            title = {
                Text("End Wind-down Session?", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This will stop the session timer, fade out any playing audio, and restore your device settings.",
                    color = TextMuted,
                    fontSize = 13.5.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndConfirmDialog = false
                        WindDownSessionManager.endSession(context)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("End Session", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEndConfirmDialog = false }) {
                    Text("Keep Going", color = TextMuted)
                }
            },
        )
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
                    text = "Wind-down Mode",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (isPaused) "Session paused" else "Session in progress",
                    color = if (isPaused) Color(0xFFFB923C) else NeonGreenLight,
                    fontSize = 12.sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Mini Now Playing Card
            if (currentTrack != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable { onNavigateToPlayer(currentTrack) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF1E3A2B), Color(0xFF0F2318))
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (currentTrack.category) {
                                "Podcasts" -> "🎙️"
                                "Audiobooks" -> "📖"
                                else -> "🎵"
                            },
                            fontSize = 20.sp,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Now Playing",
                            color = NeonGreenLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = currentTrack.title,
                            color = TextWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${formatMs(currentPos)} / ${formatMs(duration)}",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceGreen)
                            .clickable { WindDownAudioPlaybackManager.togglePlayPause(context) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (isAudioPlaying) "⏸" else "▶",
                            color = NeonGreen,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Huge Circular Countdown Timer
            val minLeft = remainingSec / 60
            val secLeft = remainingSec % 60
            val timeString = "%02d:%02d".format(minLeft, secLeft)

            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val r = (size.minDimension - stroke) / 2

                    // Background track
                    drawCircle(
                        color = CardBorder,
                        radius = r,
                        style = Stroke(width = stroke),
                    )

                    // Active progress arc
                    drawArc(
                        brush = Brush.sweepGradient(listOf(NeonGreenLight, NeonGreen, NeonGreenLight)),
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = Offset(stroke / 2, stroke / 2),
                        size = Size(r * 2, r * 2),
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🍃", fontSize = 28.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = timeString,
                        color = TextWhite,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "remaining",
                        color = TextMuted,
                        fontSize = 13.sp,
                    )
                }
            }

            // Interactive Session Controls Row: -10s, Pause/Resume, +10s
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // -10s
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, CardBorder, CircleShape)
                        .clickable { WindDownSessionManager.adjustTime(-10) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "-10s",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Pause / Resume (Big Neon Circle)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(NeonGreen)
                        .clickable {
                            if (isPaused) {
                                WindDownSessionManager.resumeSession(context)
                            } else {
                                WindDownSessionManager.pauseSession(context)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (isPaused) "▶" else "⏸",
                        fontSize = 26.sp,
                        color = Color(0xFF07120D),
                        fontWeight = FontWeight.Bold,
                    )
                }

                // +10s
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, CardBorder, CircleShape)
                        .clickable { WindDownSessionManager.adjustTime(10) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+10s",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Three Status Cards Row: Block apps, Screen dimming, Do not disturb
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Block apps
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🛡️", fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Block apps", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (blockApps) "Active ($blockedAppCount)" else "Off",
                            color = if (blockApps) NeonGreenLight else TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }

                // Screen dimming
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌙", fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Screen dimming", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (screenDimming) "On" else "Off",
                            color = if (screenDimming) NeonGreenLight else TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }

                // Do not disturb
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔕", fontSize = 18.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Do not disturb", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (dnd) "On" else "Off",
                            color = if (dnd) NeonGreenLight else TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // End Session Button
            Button(
                onClick = { showEndConfirmDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardBg,
                    contentColor = TextWhite,
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(CardBorder)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏹", fontSize = 16.sp, color = Color(0xFFEF4444))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "End Session",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextWhite,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
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
