package com.studymode.app

import android.widget.Toast

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Fullscreen Immersive Focus Mode Window.
 * Features:
 * - Dynamic theme wallpapers (Aurora Borealis, Quiet Forest, Deep Space).
 * - Live ambient sound synthesis (Rain, White Noise, Campfire).
 * - Circular frosted glass focus dial with tag, mode, digital countdown, and break trigger.
 * - Circular track with a glowing white bead moving along the perimeter.
 * - Dynamic Island top pill (Community focusing count).
 * - Quick actions row: Left Hamburger Menu, Center Sprout Emblem, Right Strict Mode Lock.
 * - Left Hamburger Menu Bottom Sheet matching the user reference UI:
 *     - [ 🎵 Music           Off > ]
 *     - [ ✨ Themes       Aurora > ]
 *     - [ 🚫 Blocked Apps 65 Apps > ]
 * - Bottom capsule Pause / Resume button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveFocusScreen(
    store: LocalStudyStore,
    timer: ActiveTimer?,
    onMinimize: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: (completed: Boolean) -> Unit,
    onTakeBreak: (breakMinutes: Int) -> Unit = {},
) {
    val context = LocalContext.current

    val isLauncherBlocked = (store.isLauncherBlocked() || store.isUltraFocus())

    // Intercept back gesture: if launcher is blocked, prevent minimizing to home
    BackHandler {
        if (isLauncherBlocked) {
            Toast.makeText(
                context,
                "Hold on! To leave, you’ll need to finish or end your current session",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            onMinimize()
        }
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var currentTag by remember { mutableStateOf(store.focusTag()) }
    var currentTheme by remember { mutableStateOf(store.focusTheme()) }
    var currentMusic by remember { mutableStateOf(store.focusMusic()) }
    var blockedAppsCount by remember { mutableIntStateOf(store.blockedApps().count { it.blocked }) }

    var showTagDialog by rememberSaveable { mutableStateOf(false) }
    var showBreakDialog by rememberSaveable { mutableStateOf(false) }
    var showMenuSheet by rememberSaveable { mutableStateOf(false) }
    var showMusicSheet by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var showBlockedAppsSheet by rememberSaveable { mutableStateOf(false) }
    var showGiveUpDialog by rememberSaveable { mutableStateOf(false) }
    var showStrictInfoSheet by rememberSaveable { mutableStateOf(false) }
    var showThemesShowcaseModal by rememberSaveable { mutableStateOf(false) }
    var showMusicShowcaseModal by rememberSaveable { mutableStateOf(false) }
    var showHomeScreenShowcaseModal by rememberSaveable { mutableStateOf(false) }

    // Motivation words that pop up when clicking the center book/sprout button
    val motivationalQuotes = remember {
        listOf(
            "You don’t have to be perfect, just consistent.",
            "Small daily improvements over time lead to stunning results.",
            "Focus is saying no to good ideas so you can do the great ones.",
            "Discipline is choosing between what you want now and what you want most.",
            "Your future self will thank you for the effort you put in right now.",
            "Deep work is a superpower in our increasingly distracted world.",
            "One pomodoro at a time, one page at a time.",
            "Action creates motivation, not the other way around.",
        )
    }
    var showMotivation by rememberSaveable { mutableStateOf(true) }
    var quoteIndex by rememberSaveable { mutableIntStateOf(0) }

    val isRunning = timer?.running == true

    // Synchronize Ambient Audio playback with focus state
    DisposableEffect(currentMusic, isRunning) {
        if (isRunning && currentMusic != "Off") {
            FocusAudioPlayer.play(context, currentMusic)
        } else {
            FocusAudioPlayer.stop()
        }
        onDispose {
            FocusAudioPlayer.stop()
        }
    }

    val isStopwatch = timer?.goal?.contains("Stopwatch", ignoreCase = true) == true
    val isPomodoro = timer?.goal?.contains("Pomodoro", ignoreCase = true) == true
    val isUltra = timer?.goal?.contains("Ultra", ignoreCase = true) == true

    // Live countdown ticker
    LaunchedEffect(timer?.running, timer?.segmentStartedAtMillis) {
        while (timer?.running == true) {
            delay(500)
            nowMillis = System.currentTimeMillis()
            if (!isStopwatch && (timer.remainingAt(nowMillis) <= 0L)) {
                SoundEffectManager.play(context, SoundEffect.TIMER_END)
                onFinish(true)
                break
            }
        }
    }

    // Compute remaining or elapsed seconds
    val totalSeconds = (timer?.targetMinutes ?: 45) * 60L
    val remainingMillis = timer?.remainingAt(nowMillis) ?: 0L
    val remainingSeconds = remainingMillis / 1000L
    val elapsedSeconds = if (timer != null) timer.elapsedAt(nowMillis) / 1000L else 0L

    val displaySeconds = if (isStopwatch) elapsedSeconds else remainingSeconds
    val hours = displaySeconds / 3600
    val minutes = (displaySeconds % 3600) / 60
    val seconds = displaySeconds % 60

    val timeString = if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }

    // Progress along the circle (1.0 at start, 0.0 at finish)
    val progress = if (isStopwatch) {
        ((elapsedSeconds % 3600).toFloat() / 3600f).coerceIn(0f, 1f)
    } else if (totalSeconds > 0) {
        (remainingSeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }

    // Wallpaper resource or gradient based on selected theme
    val wallpaperRes = when (currentTheme) {
        "Forest", "Quiet Forest" -> R.drawable.bg_focus_forest
        "Starry Sky" -> R.drawable.bg_focus_starrysky
        "Mountain Range" -> R.drawable.bg_focus_mountain
        "Leaf Canopy" -> R.drawable.bg_focus_leaf
        "Ocean Waves" -> R.drawable.bg_focus_ocean
        "Pine Forest" -> R.drawable.bg_focus_pine
        "Green Hills" -> R.drawable.bg_focus_greenhills
        "Sunset Peak" -> R.drawable.bg_focus_sunset
        "Cozy Cafe", "Cafe" -> R.drawable.bg_focus_cafe
        "Study Library", "Library" -> R.drawable.bg_focus_library
        "Study Desk" -> R.drawable.bg_focus_studydesk
        "Pink Petal Room" -> R.drawable.bg_focus_pinkpetal
        "Cherry Blossom" -> R.drawable.bg_focus_cherryblossom
        "Moonlit Field" -> R.drawable.bg_focus_moonlitfield
        "Deep Space" -> R.drawable.bg_focus_space
        "Alpine Serenity" -> R.drawable.bg_focus_alpine
        "Minimal Blue", "Minimal Green", "Minimal Grey" -> null
        else -> R.drawable.bg_focus_aurora
    }

    val solidGradient = when (currentTheme) {
        "Minimal Blue" -> listOf(Color(0xFF0F1E36), Color(0xFF050B14))
        "Minimal Green" -> listOf(Color(0xFF0C2415), Color(0xFF030A05))
        "Minimal Grey" -> listOf(Color(0xFF222624), Color(0xFF0A0C0B))
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF040A06)),
    ) {
        // 1. Dynamic Wallpaper Image or Solid Gradient
        if (solidGradient != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(solidGradient)),
            )
        } else {
            Image(
                painter = painterResource(id = wallpaperRes ?: R.drawable.bg_focus_aurora),
                contentDescription = "Focus Wallpaper Background",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Subtle ambient vignette gradient for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
                ),
        )

        // 2. Main Screen Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.7f))

            // 4. Central Glassmorphic Focus Dial
            Box(
                modifier = Modifier
                    .size(310.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F2615).copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                // Circular ring and glowing progress bead
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidthPx = 1.5.dp.toPx()
                    val diameter = size.minDimension - strokeWidthPx * 2
                    val radius = diameter / 2f
                    val centerOffset = Offset(size.width / 2f, size.height / 2f)

                    // Subtle glowing outer stroke
                    drawCircle(
                        color = Color.White.copy(alpha = 0.75f),
                        radius = radius,
                        center = centerOffset,
                        style = Stroke(width = strokeWidthPx),
                    )

                    // Glowing bead position along circle circumference
                    val angleDeg = if (isStopwatch) {
                        -90f + progress * 360f
                    } else {
                        -90f + (1f - progress) * 360f
                    }
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    val beadX = (centerOffset.x + radius * cos(angleRad)).toFloat()
                    val beadY = (centerOffset.y + radius * sin(angleRad)).toFloat()
                    val beadCenter = Offset(beadX, beadY)

                    // Outer halo glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                Color(0xFF69ED70).copy(alpha = 0.45f),
                                Color.Transparent,
                            ),
                            center = beadCenter,
                            radius = 12.dp.toPx(),
                        ),
                        radius = 12.dp.toPx(),
                        center = beadCenter,
                    )

                    // Inner bright white bead
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = beadCenter,
                    )
                }

                // Dial Content: Tag, Mode, Clock, Take a Break
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    // Tag Selector (e.g. "Untagged ⌵")
                    Surface(
                        color = Color.Transparent,
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                            showTagDialog = true
                        },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = currentTag,
                                color = Color(0xFFE4EDE4),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                            )
                            Text(
                                text = "⌵",
                                color = Color(0xFFB5C8B5),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Mode Caption ("Timer" / "Pomodoro" / "Stopwatch")
                    Text(
                        text = when {
                            isStopwatch -> "Stopwatch"
                            isPomodoro -> "Pomodoro"
                            else -> "Timer"
                        },
                        color = Color(0xFFB0CEB0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Giant Digital Countdown ("58:44")
                    Text(
                        text = timeString,
                        color = Color.White,
                        fontSize = 58.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // "Take a break" Frosted Pill Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF285434).copy(alpha = 0.55f),
                        border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.35f)),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.BREAK_START)
                            showBreakDialog = true
                        },
                    ) {
                        Text(
                            text = "Take a break",
                            color = Color(0xFFF0F7F0),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.6f))

            // Motivation Words Card (Matching Reference Image)
            AnimatedVisibility(
                visible = showMotivation,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF0C190F).copy(alpha = 0.88f),
                    border = BorderStroke(0.8.dp, Color(0xFF285434).copy(alpha = 0.65f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            quoteIndex = (quoteIndex + 1) % motivationalQuotes.size
                        },
                ) {
                    Text(
                        text = motivationalQuotes[quoteIndex % motivationalQuotes.size],
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))

            // 5. Quick Actions Row (Menu | Glowing Sprout Emblem | Strict Lock)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Hamburger Menu Circle (☰) -> Triggers requested menu!
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color(0xFF0F1B12).copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                    onClick = {
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                        showMenuSheet = true
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                            )
                        }
                    }
                }

                // Center: Glowing Sprout / Open Book Emblem (Tap to pop up / cycle motivation words!)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .clickable {
                            SoundEffectManager.play(context, SoundEffect.CHIME)
                            if (!showMotivation) {
                                showMotivation = true
                            } else {
                                quoteIndex = (quoteIndex + 1) % motivationalQuotes.size
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    // Soft radiant green background glow
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFF69ED70).copy(alpha = 0.5f),
                                        Color(0xFF2A8B35).copy(alpha = 0.2f),
                                        Color.Transparent,
                                    ),
                                ),
                                shape = CircleShape,
                            ),
                    )
                    Image(
                        painter = painterResource(id = R.drawable.focus_sprout_transparent),
                        contentDescription = "StudyMode Sprout (Tap for Motivation)",
                        modifier = Modifier.size(52.dp),
                    )
                }

                // Right: Golden Strict Mode Lock Button (🔒)
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.6f)),
                    onClick = {
                        SoundEffectManager.play(context, SoundEffect.LOCK_SNAP)
                        showStrictInfoSheet = true
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        Color(0xFFFFB300).copy(alpha = 0.85f),
                                        Color(0xFF5D3A03).copy(alpha = 0.9f),
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "🔒",
                            fontSize = 18.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 6. Bottom Capsule Button: "Pause" / "Resume"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFF0F1B12).copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                onClick = {
                    if (isRunning) {
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                        onPause()
                    } else {
                        SoundEffectManager.play(context, SoundEffect.SESSION_START)
                        onResume()
                    }
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isRunning) "Pause" else "Resume",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }

    // ------------------------------------------------------------------
    // HAMBURGER MENU BOTTOM SHEET (EXACTLY MATCHING USER SCREENSHOT)
    // ------------------------------------------------------------------
    if (showMenuSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141614),
            contentColor = Color.White,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF424742)),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Unified card with Music, Themes, Blocked Apps (as seen in screenshot)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E211E),
                ) {
                    Column {
                        // ROW 1: Music
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SoundEffectManager.play(context, SoundEffect.CLICK)
                                    showMenuSheet = false
                                    showMusicSheet = true
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("🎵", fontSize = 17.sp, color = Color(0xFFA5B2A5))
                                Text("Music", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(currentMusic, color = Color(0xFF8E9B8E), fontSize = 14.sp)
                                Text(">", color = Color(0xFF6A756A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(
                            color = Color(0xFF282D28),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )

                        // ROW 2: Themes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SoundEffectManager.play(context, SoundEffect.CLICK)
                                    showMenuSheet = false
                                    showThemeSheet = true
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("✨", fontSize = 17.sp, color = Color(0xFFA5B2A5))
                                Text("Themes", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(currentTheme, color = Color(0xFF8E9B8E), fontSize = 14.sp)
                                Text(">", color = Color(0xFF6A756A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(
                            color = Color(0xFF282D28),
                            thickness = 0.8.dp,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )

                        // ROW 3: Blocked Apps
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    SoundEffectManager.play(context, SoundEffect.CLICK)
                                    showMenuSheet = false
                                    showBlockedAppsSheet = true
                                }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Text("🚫", fontSize = 17.sp, color = Color(0xFFA5B2A5))
                                Text("Blocked Apps", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("$blockedAppsCount Apps", color = Color(0xFF8E9B8E), fontSize = 14.sp)
                                Text(">", color = Color(0xFF6A756A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Showcase Previews Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262618),
                        border = BorderStroke(1.dp, Color(0xFF554D20)),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                            showThemesShowcaseModal = true
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text("✨ Themes", color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262618),
                        border = BorderStroke(1.dp, Color(0xFF554D20)),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                            showMusicShowcaseModal = true
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text("🎧 Audio", color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF262618),
                        border = BorderStroke(1.dp, Color(0xFF554D20)),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                            showHomeScreenShowcaseModal = true
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text("🚫 Launcher", color = Color(0xFFFFD54F), fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }
                }

                // Session control secondary actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E211E),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                            showMenuSheet = false
                            if (isLauncherBlocked) {
                                Toast.makeText(
                                    context,
                                    "Hold on! To leave, you’ll need to finish or end your current session",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onMinimize()
                            }
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Minimize to Home", color = Color(0xFFA5B2A5), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF281414),
                        border = BorderStroke(1.dp, Color(0xFF5D2424)),
                        onClick = {
                            SoundEffectManager.play(context, SoundEffect.CLICK)
                            showMenuSheet = false
                            showGiveUpDialog = true
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("End Session", color = Color(0xFFFF8B8B), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // ------------------------------------------------------------------
    // SUB-SHEET: SCIENCE-BACKED MUSIC (IMAGE 2)
    // ------------------------------------------------------------------
    if (showMusicSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val musicTracks = remember {
            listOf(
                MusicTrack("White noise", "Pure sound masking frequencies to block distractions", R.drawable.white_noise),
                MusicTrack("Binaural – Music", "Alpha wave binaural audio engineered for deep flow", R.drawable.binaural_music),
                MusicTrack("Pink noise", "Balanced soothing frequencies for relaxed mental stamina", R.drawable.pink_noise),
                MusicTrack("Binaural – Birdsong", "Gentle nature ambience with uplifting morning birdsong", R.drawable.binaural_bird_rain),
                MusicTrack("Binaural – Brook", "Calming babbling mountain stream & meadow sounds", R.drawable.binaural_bird_brook),
                MusicTrack("Lo-Fi Beats", "Smooth acoustic study rhythms & chill hip-hop", R.drawable.lofi_beats),
                MusicTrack("Cafe Jazz", "Cozy coffeehouse acoustic guitar & jazz vibes", R.drawable.cafe_jazz),
                MusicTrack("Deep Focus Ambience", "Spacious resonant acoustic soundscape for thinking", R.drawable.deep_focus),
                MusicTrack("Rain Focus Ambience", "Steady rhythmic rainfall on glass to dissolve stress", R.drawable.rain_focus),
                MusicTrack("Brown noise", "Low-frequency waterfall rumble for deep immersion", R.drawable.brown_noise),
                MusicTrack("Off", "Quiet focus with no audio playback", R.drawable.no_sound_png, isVerified = false),
            )
        }

        ModalBottomSheet(
            onDismissRequest = { showMusicSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141614),
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Science-Backed Music", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text("Tune your mind into flow with sound built for focus.", color = Color(0xFFA4B2A4), fontSize = 12.sp)

                // Showcase Banner Card (Opens Image 2 modal)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMusicShowcaseModal = true },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1F241C),
                    border = BorderStroke(1.dp, Color(0xFF3B4833)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFD54F)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🎧", fontSize = 16.sp)
                            }
                            Column {
                                Text("Audio Research & Flow Benefits", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFFFD54F))
                                Text("See how scientific sound frequencies boost focus", fontSize = 11.sp, color = Color(0xFFA5B2A5))
                            }
                        }
                        Text("Explore ❯", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Tracks List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    musicTracks.forEach { track ->
                        val isSelected = currentMusic.equals(track.name, ignoreCase = true)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF223A25) else Color(0xFF1A1D1A),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF69ED70) else Color(0xFF282D28)),
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.CLICK)
                                currentMusic = track.name
                                store.setFocusMusic(track.name)
                                if (isRunning && track.name != "Off") {
                                    FocusAudioPlayer.play(context, track.name)
                                } else {
                                    FocusAudioPlayer.stop()
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Image(
                                        painter = painterResource(id = track.iconRes),
                                        contentDescription = track.name,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Column {
                                        Text(track.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                                        Text(track.subtitle, color = Color(0xFFA5B8A5), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (track.isVerified) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE5B832)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        }
                                    }
                                    if (isSelected) {
                                        Text("✓", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // ------------------------------------------------------------------
    // SUB-SHEET: PREMIUM THEMES (IMAGE 1)
    // ------------------------------------------------------------------
    if (showThemeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var selectedCategory by rememberSaveable { mutableStateOf("All") }

        val themeOptions = remember {
            listOf(
                // Nature
                ThemeOption("Aurora", "Nature", "Northern Lights emerald night sky", drawableRes = R.drawable.bg_focus_aurora),
                ThemeOption("Forest", "Nature", "Misty twilight evergreen woods", drawableRes = R.drawable.bg_focus_forest),
                ThemeOption("Starry Sky", "Nature", "Deep night sky with glittering stars", drawableRes = R.drawable.bg_focus_starrysky),
                ThemeOption("Mountain Range", "Nature", "Majestic mountain peaks in morning mist", drawableRes = R.drawable.bg_focus_mountain),
                ThemeOption("Leaf Canopy", "Nature", "Sunlight filtering through vibrant emerald leaves", drawableRes = R.drawable.bg_focus_leaf),
                ThemeOption("Ocean Waves", "Nature", "Calm rhythmic sea tides and sunset coastline", drawableRes = R.drawable.bg_focus_ocean),
                ThemeOption("Pine Forest", "Nature", "Crisp woodland trees and peaceful solitude", drawableRes = R.drawable.bg_focus_pine),
                ThemeOption("Green Hills", "Nature", "Rolling meadows under gentle afternoon clouds", drawableRes = R.drawable.bg_focus_greenhills),
                ThemeOption("Sunset Peak", "Nature", "Warm golden sunset illuminating mountain ridges", drawableRes = R.drawable.bg_focus_sunset),
                // Lo-Fi Aesthetic
                ThemeOption("Cozy Cafe", "Lo-Fi", "Warm rainy coffeehouse window and cafe steam", drawableRes = R.drawable.bg_focus_cafe),
                ThemeOption("Study Library", "Lo-Fi", "Old library sanctuary with towering wooden shelves", drawableRes = R.drawable.bg_focus_library),
                ThemeOption("Study Desk", "Lo-Fi", "Peaceful study desk overlooking rainy city streets", drawableRes = R.drawable.bg_focus_studydesk),
                ThemeOption("Pink Petal Room", "Lo-Fi", "Soft cherry blossom Japanese aesthetic interior", drawableRes = R.drawable.bg_focus_pinkpetal),
                ThemeOption("Cherry Blossom", "Lo-Fi", "Springtime floral dusk with blooming sakura", drawableRes = R.drawable.bg_focus_cherryblossom),
                ThemeOption("Moonlit Field", "Lo-Fi", "Dreamy moonlit lavender landscape at dusk", drawableRes = R.drawable.bg_focus_moonlitfield),
                // Minimal & Solids
                ThemeOption("Deep Space", "Solids", "Cosmic nebula with violet starlight", drawableRes = R.drawable.bg_focus_space),
                ThemeOption("Alpine Serenity", "Nature", "Pristine alpine lake surrounded by evergreen pine peaks", drawableRes = R.drawable.bg_focus_alpine),
                ThemeOption("Minimal Blue", "Solids", "Clean modern slate & dark navy gradient", gradientColors = listOf(Color(0xFF0F1E36), Color(0xFF050B14))),
                ThemeOption("Minimal Green", "Solids", "Subtle organic dark emerald gradient", gradientColors = listOf(Color(0xFF0C2415), Color(0xFF030A05))),
                ThemeOption("Minimal Grey", "Solids", "Minimalist graphite & obsidian gradient", gradientColors = listOf(Color(0xFF222624), Color(0xFF0A0C0B))),
            )
        }

        val filteredThemes = remember(selectedCategory) {
            if (selectedCategory == "All") themeOptions else themeOptions.filter { it.category == selectedCategory }
        }

        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF141614),
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Wallpaper Themes", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text("Personalize your environment and make focus feel rewarding.", color = Color(0xFFA4B2A4), fontSize = 12.sp)

                // Showcase Banner Card (Opens Image 1 modal)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showThemesShowcaseModal = true },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1F241C),
                    border = BorderStroke(1.dp, Color(0xFF3B4833)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFD54F)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("✨", fontSize = 16.sp)
                            }
                            Column {
                                Text("Premium Themes Showcase", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFFFFD54F))
                                Text("Explore Nature, Lo-Fi, and Minimal aesthetics", fontSize = 11.sp, color = Color(0xFFA5B2A5))
                            }
                        }
                        Text("Explore ❯", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Category Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("All", "Nature", "Lo-Fi", "Solids").forEach { cat ->
                        val isCatSelected = selectedCategory == cat
                        Surface(
                            shape = CircleShape,
                            color = if (isCatSelected) Color(0xFF69ED70) else Color(0xFF1E211E),
                            border = BorderStroke(1.dp, if (isCatSelected) Color(0xFF69ED70) else Color(0xFF333B33)),
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.CLICK)
                                selectedCategory = cat
                            },
                        ) {
                            Text(
                                text = cat,
                                color = if (isCatSelected) Color(0xFF0C1F0E) else Color(0xFFD0DDD0),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                            )
                        }
                    }
                }

                // Themes List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    filteredThemes.forEach { theme ->
                        val isSelected = currentTheme.equals(theme.name, ignoreCase = true)
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) Color(0xFF223A25) else Color(0xFF1A1D1A),
                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF69ED70) else Color(0xFF282D28)),
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.CLICK)
                                currentTheme = theme.name
                                store.setFocusTheme(theme.name)
                            },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    // Theme Thumbnail Box
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, Color(0xFF3B4833), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (theme.gradientColors != null) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Brush.verticalGradient(theme.gradientColors)),
                                            )
                                        } else if (theme.drawableRes != null) {
                                            Image(
                                                painter = painterResource(id = theme.drawableRes),
                                                contentDescription = theme.name,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop,
                                            )
                                        }
                                    }

                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(theme.name, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF252B25),
                                            ) {
                                                Text(
                                                    theme.category,
                                                    color = Color(0xFFA5BBA5),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                        Text(theme.subtitle, color = Color(0xFFA5B8A5), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }

                                if (isSelected) {
                                    Text("✓", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // ------------------------------------------------------------------
    // SUB-SHEET: BLOCKED APPS MANAGER
    // ------------------------------------------------------------------
    if (showBlockedAppsSheet) {
        val allApps = remember { AppListCache.getApps(context) }
        val initiallySelected = remember {
            store.blockedApps().filter { it.blocked }.map { it.packageName }.toSet()
        }

        AppPickerDialog(
            title = "Blocked Apps during Focus",
            apps = allApps,
            initiallySelected = initiallySelected,
            onDismiss = { showBlockedAppsSheet = false },
            onConfirm = { confirmedApps ->
                val confirmedPackages = confirmedApps.map { it.packageName }.toSet()
                confirmedApps.forEach { app ->
                    store.setAppBlocked(app.packageName, app.label, true)
                }
                initiallySelected.forEach { pkg ->
                    if (pkg !in confirmedPackages) {
                        val label = allApps.find { it.packageName == pkg }?.label ?: pkg
                        store.setAppBlocked(pkg, label, false)
                    }
                }
                blockedAppsCount = confirmedPackages.size
                showBlockedAppsSheet = false
            },
        )
    }

    // ------------------------------------------------------------------
    // SHOWCASE MODAL BOTTOM SHEETS (IMAGES 1 & 2)
    if (showThemesShowcaseModal) {
        PremiumThemesShowcaseSheet(onDismiss = { showThemesShowcaseModal = false })
    }

    if (showMusicShowcaseModal) {
        ScienceBackedMusicShowcaseSheet(onDismiss = { showMusicShowcaseModal = false })
    }

    if (showHomeScreenShowcaseModal) {
        BlockHomeScreenShowcaseSheet(onDismiss = { showHomeScreenShowcaseModal = false })
    }

    // OTHER DIALOGS
    // ------------------------------------------------------------------

    // Tag Picker Dialog
    if (showTagDialog) {
        TagPickerDialog(
            currentTag = currentTag,
            onDismiss = { showTagDialog = false },
            onSelectTag = { newTag ->
                currentTag = newTag
                store.setFocusTag(newTag)
                showTagDialog = false
            },
        )
    }

    // Take a Break Dialog
    if (showBreakDialog) {
        AlertDialog(
            onDismissRequest = { showBreakDialog = false },
            containerColor = Color(0xFF101912),
            title = {
                Text("Take a Break", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Rest your eyes and recharge your mind before continuing.",
                        color = Color(0xFFA5B8A5),
                        fontSize = 13.sp,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.BREAK_START)
                                showBreakDialog = false
                                onTakeBreak(5)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244828)),
                        ) {
                            Text("5 Mins", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                SoundEffectManager.play(context, SoundEffect.BREAK_START)
                                showBreakDialog = false
                                onTakeBreak(10)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF244828)),
                        ) {
                            Text("10 Mins", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    showBreakDialog = false
                }) {
                    Text("Cancel", color = Color(0xFFA5B8A5))
                }
            },
        )
    }

    // Strict Mode / Ultra Focus Info Sheet
    if (showStrictInfoSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showStrictInfoSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xFF111A13),
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                Brush.radialGradient(listOf(Color(0xFFFFB300), Color(0xFF5D3A03))),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🔒", fontSize = 16.sp)
                    }
                    Column {
                        Text("Strict Focus Protection", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text(
                            if (isUltra) "Ultra Focus Mode Enabled" else "Focus Enforcement Active",
                            color = Color(0xFFFFD54F),
                            fontSize = 12.sp,
                        )
                    }
                }

                Text(
                    "While your focus session is active, StudyMode blocks distracting apps (like Chess, YouTube, and games) so you can build deep discipline without disruptions.",
                    color = Color(0xFFA5B8A5),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF142417),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("🛡️ Banking & UPI Safe", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            "BHIM and banking applications are automatically excluded and protected.",
                            color = Color(0xFFD0E0D0),
                            fontSize = 11.sp,
                        )
                    }
                }

                Button(
                    onClick = {
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                        showStrictInfoSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69ED70), contentColor = Color(0xFF0A1C0C)),
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Give Up Confirmation Dialog
    if (showGiveUpDialog) {
        AlertDialog(
            onDismissRequest = { showGiveUpDialog = false },
            containerColor = Color(0xFF1A1212),
            title = {
                Text("End Session?", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "Are you sure you want to finish your focus session early? Your elapsed time will still be saved to your daily stats.",
                    color = Color(0xFFFFD0D0),
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showGiveUpDialog = false
                        FocusAudioPlayer.stop()
                        SoundEffectManager.play(context, SoundEffect.SESSION_END)
                        onFinish(false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252)),
                ) {
                    Text("End Session", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    SoundEffectManager.play(context, SoundEffect.CLICK)
                    showGiveUpDialog = false
                }) {
                    Text("Stay Focused", color = Color.White)
                }
            },
        )
    }
}

/**
 * Subject / Tag Picker Dialog.
 */
@Composable
private fun TagPickerDialog(
    currentTag: String,
    onDismiss: () -> Unit,
    onSelectTag: (String) -> Unit,
) {
    val context = LocalContext.current
    val predefinedTags = listOf("Untagged", "Study", "Coding", "Reading", "Math", "Deep Work", "Exam Prep", "Writing", "Project")
    var customTag by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101912),
        title = {
            Text("Select Focus Tag", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                predefinedTags.chunked(3).forEach { rowTags ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowTags.forEach { tag ->
                            val isSelected = tag.equals(currentTag, ignoreCase = true)
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF264D2B) else Color(0xFF172419),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF69ED70) else Color(0xFF233626),
                                ),
                                onClick = {
                                    SoundEffectManager.play(context, SoundEffect.CLICK)
                                    onSelectTag(tag)
                                },
                            ) {
                                Text(
                                    text = tag,
                                    color = if (isSelected) Color(0xFF69ED70) else Color(0xFFD0E0D0),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 10.dp),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = customTag,
                    onValueChange = { customTag = it },
                    placeholder = { Text("Or type custom tag...", color = Color(0xFF708572), fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF69ED70),
                        unfocusedBorderColor = Color(0xFF233626),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            if (customTag.isNotBlank()) {
                Button(
                    onClick = {
                        SoundEffectManager.play(context, SoundEffect.CLICK)
                        onSelectTag(customTag.trim())
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF69ED70), contentColor = Color(0xFF081A09)),
                ) {
                    Text("Save Tag", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                SoundEffectManager.play(context, SoundEffect.CLICK)
                onDismiss()
            }) {
                Text("Cancel", color = Color(0xFFA5B8A5))
            }
        },
    )
}

private data class ThemeOption(
    val name: String,
    val category: String, // "Nature", "Lo-Fi", "Solids"
    val subtitle: String,
    val drawableRes: Int? = null,
    val gradientColors: List<Color>? = null,
)

private data class MusicTrack(
    val name: String,
    val subtitle: String,
    val iconRes: Int,
    val isVerified: Boolean = true,
)

/**
 * Premium Themes Showcase Bottom Sheet (Matching Image 1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PremiumThemesShowcaseSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141614),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF424742)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Yellow Presentation Frame
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFF9CF45),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Dark Phone Mockup Frame
                    Surface(
                        modifier = Modifier
                            .width(230.dp)
                            .height(310.dp)
                            .padding(top = 18.dp),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = Color(0xFF121412),
                        border = BorderStroke(3.dp, Color(0xFF2C302C)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Top Row: Starry Sky, Forest, Cherry Blossom
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.bg_focus_starrysky),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).height(125.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.bg_focus_forest),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).height(125.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.bg_focus_cherryblossom),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).height(125.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            // Bottom Row: Cozy Cafe, Study Library, Blue
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.bg_focus_cafe),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).height(125.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Image(
                                    painter = painterResource(id = R.drawable.bg_focus_library),
                                    contentDescription = null,
                                    modifier = Modifier.weight(1f).height(125.dp).clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(125.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Brush.verticalGradient(listOf(Color(0xFF1B3B6F), Color(0xFF091428)))),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title & Subtitle (matching Image 1)
            Text(
                "Premium Themes",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Personalize your environment and make focus\nfeel rewarding.",
                fontSize = 14.sp,
                color = Color(0xFFA5B2A5),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 7 Pagination Dots (5th selected)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 7) {
                    val isCurrent = i == 4
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color.White else Color(0xFF555B55)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Got it! Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF111411),
                ),
            ) {
                Text("Got it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * Science-Backed Music Showcase Bottom Sheet (Matching Image 2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScienceBackedMusicShowcaseSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141614),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF424742)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Yellow Presentation Frame
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFF9CF45),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Dark Phone Mockup Frame
                    Surface(
                        modifier = Modifier
                            .width(230.dp)
                            .height(310.dp)
                            .padding(top = 18.dp),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = Color(0xFF121412),
                        border = BorderStroke(3.dp, Color(0xFF2C302C)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // 4 Showcase Rows (Matching Image 2)
                            val showcaseTracks = listOf(
                                Pair("White noise", R.drawable.white_noise),
                                Pair("Binaural – Music", R.drawable.binaural_music),
                                Pair("Pink noise", R.drawable.pink_noise),
                                Pair("Binaural – Birdsong", R.drawable.binaural_bird_rain),
                            )

                            showcaseTracks.forEach { (name, iconRes) ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E211E),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        ) {
                                            Image(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = name,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                            )
                                            Text(
                                                name,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFE5B832)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("✓", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title & Subtitle (matching Image 2)
            Text(
                "Science-Backed Music",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Tune your mind into flow with sound built for\nfocus.",
                fontSize = 14.sp,
                color = Color(0xFFA5B2A5),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 7 Pagination Dots (6th selected)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 7) {
                    val isCurrent = i == 5
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color.White else Color(0xFF555B55)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Got it! Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF111411),
                ),
            ) {
                Text("Got it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
