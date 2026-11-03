package com.studymode.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
fun WindDownMainScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToAddAudio: () -> Unit,
    onNavigateToPlayer: (WindDownAudioTrack) -> Unit,
    onNavigateToActiveSession: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRelaxingSounds: () -> Unit,
    onNavigateToBlocks: () -> Unit,
) {
    val context = LocalContext.current

    // Session settings
    var sessionDuration by remember { mutableIntStateOf(30) }
    var blockAppsEnabled by remember { mutableStateOf(true) }
    var screenDimmingEnabled by remember { mutableStateOf(false) }
    var doNotDisturbEnabled by remember { mutableStateOf(false) }

    // Audio category tab
    var selectedAudioTab by remember { mutableStateOf("My Audio") }

    val tracks = remember { store.windDownTracks() }
    val blockedAppCount = remember { store.blockedApps().count { it.blocked } }

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
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CardBg),
                ) {
                    WindDownGreenArrowBack(modifier = Modifier.size(20.dp))
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Wind-down Mode",
                        color = TextWhite,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "A calmer you, a better tomorrow",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            // Settings gear icon
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                WindDownGearIcon(modifier = Modifier.size(20.dp))
            }
        }

        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Take a break before bed card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SurfaceGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        WindDownLeafIcon(color = NeonGreen, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Take a break before bed",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Reduce distractions, relax your mind and prepare for better sleep.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }

            // Session duration section
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Session duration",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Set how long you want your wind-down session to be.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(15, 30, 60).forEach { mins ->
                        val isSelected = sessionDuration == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NeonGreen else CardBg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) NeonGreen else CardBorder,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { sessionDuration = mins }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "⏱",
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color(0xFF07120D) else TextMuted,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "$mins min",
                                    color = if (isSelected) Color(0xFF07120D) else TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }

            // Circular Dial & Controls Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CardBg)
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Dial
                Box(
                    modifier = Modifier.size(136.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 8.dp.toPx()
                        val r = (size.minDimension - stroke) / 2
                        drawCircle(
                            color = CardBorder,
                            radius = r,
                            style = Stroke(width = stroke),
                        )
                        drawArc(
                            brush = Brush.sweepGradient(listOf(NeonGreenLight, NeonGreen, NeonGreenLight)),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(r * 2, r * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WindDownLeafIcon(color = NeonGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "%02d:00".format(sessionDuration),
                            color = TextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Wind-down time",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                // Quick Controls Column
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Block apps
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardInner)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .clickable { onNavigateToBlocks() }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛡", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Block apps", color = TextWhite, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (blockedAppCount > 0) "$blockedAppCount apps selected" else "Select apps",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                            Text("›", color = TextMuted, fontSize = 18.sp)
                        }
                    }

                    // Screen dimming
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardInner)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🌙", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Screen dimming", color = TextWhite, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Reduce brightness", color = TextMuted, fontSize = 10.sp)
                                }
                            }
                            Switch(
                                checked = screenDimmingEnabled,
                                onCheckedChange = { screenDimmingEnabled = it },
                                modifier = Modifier.height(24.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF07120D),
                                    checkedTrackColor = NeonGreen,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceGreen,
                                ),
                            )
                        }
                    }

                    // Do not disturb
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardInner)
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔕", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Do not disturb", color = TextWhite, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Mute notifications", color = TextMuted, fontSize = 10.sp)
                                }
                            }
                            Switch(
                                checked = doNotDisturbEnabled,
                                onCheckedChange = { isEnabled ->
                                    if (isEnabled) {
                                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
                                            Toast.makeText(context, "Grant DND access in Settings to mute notifications", Toast.LENGTH_LONG).show()
                                            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                            return@Switch
                                        }
                                    }
                                    doNotDisturbEnabled = isEnabled
                                },
                                modifier = Modifier.height(24.dp),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF07120D),
                                    checkedTrackColor = NeonGreen,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceGreen,
                                ),
                            )
                        }
                    }
                }
            }

            // Start Wind-down Session Button
            Button(
                onClick = {
                    WindDownSessionManager.startSession(
                        context = context,
                        durationMinutes = sessionDuration,
                        blockApps = blockAppsEnabled,
                        screenDimming = screenDimmingEnabled,
                        doNotDisturb = doNotDisturbEnabled,
                        trackToPlay = tracks.firstOrNull(),
                    )
                    onNavigateToActiveSession()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreen,
                    contentColor = Color(0xFF051B0F),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▶", fontSize = 16.sp, color = Color(0xFF051B0F))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Start Wind-down Session",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF051B0F),
                    )
                }
            }

            // Choose audio section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose audio",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Play your favourite podcast, music or relaxing sounds.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )

                // Category Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("My Audio", "Relaxing Sounds", "Recently Played").forEach { tab ->
                        val isSelected = selectedAudioTab == tab
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) NeonGreen else CardBg)
                                .border(1.dp, if (isSelected) NeonGreen else CardBorder, RoundedCornerShape(20.dp))
                                .clickable {
                                    selectedAudioTab = tab
                                    if (tab == "Relaxing Sounds") {
                                        onNavigateToRelaxingSounds()
                                    } else if (tab == "My Audio") {
                                        onNavigateToLibrary()
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = tab,
                                color = if (isSelected) Color(0xFF07120D) else TextWhite,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }

                // Horizontal Audio Carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // + Add Audio Card
                    Box(
                        modifier = Modifier
                            .width(110.dp)
                            .height(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg)
                            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                            .clickable { onNavigateToAddAudio() }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("+", color = NeonGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Add Audio",
                                color = NeonGreenLight,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    // Display sample / imported tracks
                    tracks.take(6).forEach { track ->
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .height(130.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(CardBg)
                                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                                .clickable {
                                    WindDownAudioPlaybackManager.playTrack(context, track)
                                    onNavigateToPlayer(track)
                                }
                                .padding(10.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color(0xFF1E3A2B), Color(0xFF0F2318))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        when (track.category) {
                                            "Podcasts" -> "🎙️"
                                            "Audiobooks" -> "📖"
                                            else -> "🎵"
                                        },
                                        fontSize = 24.sp,
                                    )
                                }

                                Column {
                                    Text(
                                        text = track.title,
                                        color = TextWhite,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val durMin = track.durationMs / 60000L
                                    Text(
                                        text = "${track.category} • ${durMin}m",
                                        color = TextMuted,
                                        fontSize = 9.5.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Pre-sleep activities section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pre-sleep activities",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Optional activities to calm your mind.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    listOf(
                        "Block apps" to "🛡️",
                        "Read" to "📖",
                        "Stretch" to "🧘",
                        "Journal" to "✍️",
                    ).forEach { (activity, icon) ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(CardBg)
                                .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                                .clickable {
                                    if (activity == "Block apps") onNavigateToBlocks()
                                    else Toast.makeText(context, "$activity activity noted for bedtime.", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 20.sp)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = activity,
                                    color = TextWhite,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WindDownGreenArrowBack(modifier: Modifier = Modifier) {
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

@Composable
private fun WindDownGearIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = NeonGreen,
            radius = w * 0.32f,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = 2.dp.toPx()),
        )
        drawCircle(
            color = NeonGreen,
            radius = w * 0.12f,
            center = Offset(w * 0.5f, h * 0.5f),
        )
    }
}

@Composable
private fun WindDownLeafIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.85f)
            cubicTo(w * 0.1f, h * 0.45f, w * 0.45f, h * 0.15f, w * 0.85f, h * 0.15f)
            cubicTo(w * 0.85f, h * 0.55f, w * 0.55f, h * 0.85f, w * 0.15f, h * 0.85f)
            close()
        }
        drawPath(path = path, color = color)
        // Vein
        drawLine(
            color = Color(0xFF07120D),
            start = Offset(w * 0.15f, h * 0.85f),
            end = Offset(w * 0.65f, h * 0.35f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}
