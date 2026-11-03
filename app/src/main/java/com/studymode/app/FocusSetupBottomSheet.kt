package com.studymode.app

import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

enum class FocusSessionMode(val label: String) {
    TIMER("Timer"),
    STOPWATCH("Stopwatch"),
    POMODORO("Pomodoro"),
}

data class UltraFocusConfig(
    val strictMode: Boolean = true,
    val youtubeStudyMode: Boolean = true,
    val browserStudyMode: Boolean = false,
    val uninstallProtection: Boolean = false,
    val blockNotifications: Boolean = true,
    val blockHomeScreen: Boolean = true,
    val blockShortsReels: Boolean = true,
)

object UltraFocusPreferences {
    private const val PREFS_NAME = "ultra_focus_config"
    private const val KEY_STRICT = "strict_mode"
    private const val KEY_YOUTUBE = "youtube_mode"
    private const val KEY_BROWSER = "browser_mode"
    private const val KEY_UNINSTALL = "uninstall_protection"
    private const val KEY_NOTIFS = "block_notifications"
    private const val KEY_HOMESCREEN = "block_homescreen"
    private const val KEY_SHORTS = "block_shorts_reels"

    fun load(context: Context): UltraFocusConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return UltraFocusConfig(
            strictMode = prefs.getBoolean(KEY_STRICT, true),
            youtubeStudyMode = prefs.getBoolean(KEY_YOUTUBE, true),
            browserStudyMode = prefs.getBoolean(KEY_BROWSER, false),
            uninstallProtection = prefs.getBoolean(KEY_UNINSTALL, false),
            blockNotifications = prefs.getBoolean(KEY_NOTIFS, true),
            blockHomeScreen = prefs.getBoolean(KEY_HOMESCREEN, true),
            blockShortsReels = prefs.getBoolean(KEY_SHORTS, true),
        )
    }

    fun save(context: Context, config: UltraFocusConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_STRICT, config.strictMode)
            .putBoolean(KEY_YOUTUBE, config.youtubeStudyMode)
            .putBoolean(KEY_BROWSER, config.browserStudyMode)
            .putBoolean(KEY_UNINSTALL, config.uninstallProtection)
            .putBoolean(KEY_NOTIFS, config.blockNotifications)
            .putBoolean(KEY_HOMESCREEN, config.blockHomeScreen)
            .putBoolean(KEY_SHORTS, config.blockShortsReels)
            .apply()
    }
}

@Composable
fun AppPackageIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(packageName) { AppIconCache.getIcon(context, packageName) }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(icon)
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(icon)
        },
    )
}

/**
 * Dialog that allows the user to customize the focus duration in minutes.
 * Includes presets (15m, 25m, 30m, 45m, 60m, 90m, 120m) and fine-tuning steppers.
 */
@Composable
fun FocusDurationPickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes.coerceIn(1, 720)) }
    val presets = listOf(15, 25, 30, 45, 60, 90, 120)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141715),
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("⏱️", fontSize = 20.sp)
                Text(
                    text = "Set Focus Duration",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val hours = minutes / 60
                val remainderMins = minutes % 60
                val displayTime = if (hours > 0 && remainderMins > 0) {
                    "${hours}h ${remainderMins}m"
                } else if (hours > 0) {
                    "${hours}h"
                } else {
                    "${minutes} min"
                }

                Surface(
                    color = Color(0xFF1B261D),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF28482A)),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(
                        text = displayTime,
                        color = Color(0xFF69ED70),
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }

                // Fine-tuning Steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DurationStepButton("-15m") { minutes = (minutes - 15).coerceAtLeast(1) }
                    DurationStepButton("-5m") { minutes = (minutes - 5).coerceAtLeast(1) }
                    DurationStepButton("+5m") { minutes = (minutes + 5).coerceAtMost(720) }
                    DurationStepButton("+15m") { minutes = (minutes + 15).coerceAtMost(720) }
                }

                // Quick Presets
                Text(
                    text = "Quick presets",
                    color = Color(0xFFA4AAA4),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Start),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEach { preset ->
                        val isSelected = minutes == preset
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) Color(0xFF1E3A22) else Color(0xFF1E221F),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) Color(0xFF69ED70) else Color(0xFF2E3530),
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { minutes = preset },
                        ) {
                            Text(
                                text = "${preset}m",
                                color = if (isSelected) Color(0xFF69ED70) else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(minutes) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF69ED70),
                    contentColor = Color(0xFF081C09),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Set Duration", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFA4AAA4))
            }
        },
    )
}

@Composable
private fun DurationStepButton(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF222824),
        border = BorderStroke(1.dp, Color(0xFF2F3B32)),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * Focus Configuration Bottom Sheet.
 * - Shows main Focus Setup (Photo 1 / 2) with NO "PRO" text on Ultra Focus.
 * - Clicking "Ultra focus" navigates to "Ultra Focus Settings" (Photo 2 / Second image).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSetupBottomSheet(
    store: LocalStudyStore,
    initialMode: FocusSessionMode = FocusSessionMode.TIMER,
    focusMinutes: Int,
    onOpenDurationPicker: () -> Unit,
    onDismiss: () -> Unit,
    onStartSession: (mode: FocusSessionMode, durationMinutes: Int, breakMinutes: Int, ultraFocus: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var selectedMode by rememberSaveable { mutableStateOf(initialMode) }
    var breakMinutes by rememberSaveable { mutableIntStateOf(5) }
    var ultraFocusActive by rememberSaveable { mutableStateOf(false) }
    var isViewingUltraSettings by rememberSaveable { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var showHomeScreenShowcase by remember { mutableStateOf(false) }
    var showShortsShowcase by remember { mutableStateOf(false) }

    var ultraConfig by remember { mutableStateOf(UltraFocusPreferences.load(context)) }

    val blockedAppsList = remember(showAppPicker) {
        store.blockedApps().filter { it.blocked }
    }
    val blockedCount = blockedAppsList.size
    val previewPackages = remember(blockedAppsList) {
        blockedAppsList.map { it.packageName }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showHomeScreenShowcase) {
        BlockHomeScreenShowcaseSheet(onDismiss = { showHomeScreenShowcase = false })
    }

    if (showShortsShowcase) {
        BlockShortsShowcaseSheet(onDismiss = { showShortsShowcase = false })
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF121316),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        AnimatedContent(
            targetState = isViewingUltraSettings,
            transitionSpec = {
                if (targetState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            label = "SheetViewTransition",
        ) { viewingSettings ->
            if (viewingSettings) {
                // =================================================================
                // VIEW 2: ULTRA FOCUS SETTINGS (MATCHING SECOND IMAGE EXACTLY)
                // =================================================================
                UltraFocusSettingsView(
                    config = ultraConfig,
                    onConfigChange = { updated ->
                        ultraConfig = updated
                        UltraFocusPreferences.save(context, updated)
                    },
                    onBack = {
                        isViewingUltraSettings = false
                    },
                    onStartFocusNow = {
                        ultraFocusActive = true
                        UltraFocusPreferences.save(context, ultraConfig)
                        onStartSession(selectedMode, focusMinutes, breakMinutes, true)
                    },
                    onShowHomeScreenShowcase = {
                        showHomeScreenShowcase = true
                    },
                    onShowShortsShowcase = {
                        showShortsShowcase = true
                    },
                )
            } else {
                // =================================================================
                // VIEW 1: MAIN SETUP SHEET (WITH NO "PRO" ON ULTRA FOCUS)
                // =================================================================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Drag handle capsule
                    Box(
                        modifier = Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF484B52)),
                    )

                    Spacer(Modifier.height(18.dp))

                    // Segmented Pill Selector: Timer | Stopwatch | Pomodoro
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xFF1E2024),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FocusSessionMode.entries.forEach { mode ->
                                val isSelected = selectedMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) Color(0xFF384F31) else Color.Transparent)
                                        .clickable { selectedMode = mode },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = mode.label,
                                        color = if (isSelected) Color(0xFF75E385) else Color(0xFFB0B3B8),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Duration & Break Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF1C1D21),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            // Row 1: Total duration
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectedMode != FocusSessionMode.STOPWATCH) {
                                        onOpenDurationPicker()
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Total duration",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    val durationText = when (selectedMode) {
                                        FocusSessionMode.STOPWATCH -> "0 → ∞"
                                        FocusSessionMode.TIMER -> "${focusMinutes}m"
                                        FocusSessionMode.POMODORO -> "${focusMinutes}m (4 intervals)"
                                    }
                                    Text(
                                        text = durationText,
                                        color = if (selectedMode == FocusSessionMode.STOPWATCH) Color(0xFF8E9096) else Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    if (selectedMode != FocusSessionMode.STOPWATCH) {
                                        Text(
                                            text = "›",
                                            color = Color(0xFF8E9096),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = Color(0xFF26272C),
                                thickness = 1.dp,
                            )

                            // Row 2: Break
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        breakMinutes = when (breakMinutes) {
                                            0 -> 5
                                            5 -> 10
                                            10 -> 15
                                            else -> 0
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Break",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    val breakLabel = if (breakMinutes > 0) "1 break" else "None"
                                    Text(
                                        text = breakLabel,
                                        color = Color(0xFF8E9096),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = "›",
                                        color = Color(0xFF8E9096),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Two Feature Cards Side-by-Side: Blocked apps & Ultra focus
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Left Card: Blocked apps
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(134.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { showAppPicker = true },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF102113),
                            border = BorderStroke(1.dp, Color(0xFF1B3820)),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Top row: shield icon + chevron + app icons
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF19381E),
                                        modifier = Modifier.size(26.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("🛡️", fontSize = 13.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Text("›", color = Color(0xFF69ED70), fontSize = 13.sp)
                                    Spacer(Modifier.weight(1f))

                                    // Overlapping circular app icons
                                    if (previewPackages.isNotEmpty()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                            previewPackages.take(3).forEach { pkg ->
                                                Box(
                                                    modifier = Modifier
                                                        .size(22.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF1B1C20))
                                                        .border(1.dp, Color(0xFF102113), CircleShape),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    AppPackageIcon(
                                                        packageName = pkg,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clip(CircleShape),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Middle: Pill badge for app count
                                Surface(
                                    color = Color(0xFF19381E),
                                    shape = RoundedCornerShape(10.dp),
                                ) {
                                    Text(
                                        text = "$blockedCount apps",
                                        color = Color(0xFF69ED70),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    )
                                }

                                // Bottom: Title
                                Text(
                                    text = "Blocked apps",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }

                        // Right Card: Ultra focus (CLEAN THEME - NO "PRO" TEXT)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(134.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    // Clicking opens Ultra Focus Settings (Photo 2)
                                    isViewingUltraSettings = true
                                    ultraFocusActive = true
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFFFFCF26),
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Background lightning bolt watermark
                                Text(
                                    text = "⚡",
                                    fontSize = 68.sp,
                                    color = Color(0xFFECC116).copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = 6.dp, y = (-2).dp),
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    // Top Row: Active indicator if enabled (NO "PRO" BADGE)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (ultraFocusActive) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFF141414),
                                            ) {
                                                Text(
                                                    text = "✓ ACTIVE",
                                                    color = Color(0xFFFFCF26),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                                )
                                            }
                                        }
                                    }

                                    // Bottom title & subtitle in Ultra Focus theme
                                    Column {
                                        Text(
                                            text = "Ultra focus",
                                            color = Color(0xFF141414),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "Achieve non stop focus",
                                            color = Color(0xFF383838),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Bottom Continue Button (White full width pill)
                    Button(
                        onClick = {
                            onStartSession(selectedMode, focusMinutes, breakMinutes, ultraFocusActive)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(27.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                    ) {
                        Text(
                            text = "Continue",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    // App Picker Dialog if user clicks "Blocked apps" card
    if (showAppPicker) {
        val allApps = remember { AppListCache.getApps(context) }
        val initiallySelected = remember {
            store.blockedApps().filter { it.blocked }.map { it.packageName }.toSet()
        }

        AppPickerDialog(
            title = "Blocked Apps during Focus",
            apps = allApps,
            initiallySelected = initiallySelected,
            onDismiss = { showAppPicker = false },
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
                showAppPicker = false
            },
        )
    }
}

/**
 * Ultra Focus Settings View (Photo 2 / Second image):
 * - Strict mode
 * - YouTube Study Mode (with "Students' favorite feature" badge)
 * - Browser Study Mode
 * - Uninstall Protection ("Cannot uninstall StudyMode app")
 * - Block Notifications
 * - Block home screen
 * - "Start Focus Now" button
 */
@Composable
private fun UltraFocusSettingsView(
    config: UltraFocusConfig,
    onConfigChange: (UltraFocusConfig) -> Unit,
    onBack: () -> Unit,
    onStartFocusNow: () -> Unit,
    onShowHomeScreenShowcase: () -> Unit,
    onShowShortsShowcase: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag handle capsule
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF484B52)),
        )

        Spacer(Modifier.height(16.dp))

        // Title row with Back arrow
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp),
            ) {
                Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Ultra Focus Settings",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
        }

        Spacer(Modifier.height(16.dp))

        // 6 Ultra Focus Settings Cards
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 1. Strict mode
            UltraSettingCard(
                icon = { StrictModeIcon() },
                title = "Strict mode",
                subtitle = "You cannot quit till timer ends",
                checked = config.strictMode,
                onCheckedChange = { onConfigChange(config.copy(strictMode = it)) },
            )

            // 2. YouTube Study Mode
            UltraSettingCard(
                icon = { YouTubeIcon() },
                title = "YouTube Study Mode",
                badgeContent = {
                    Surface(
                        color = Color(0xFF381523),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("💗", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Students' favorite feature",
                                color = Color(0xFFFF668C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                },
                checked = config.youtubeStudyMode,
                onCheckedChange = { onConfigChange(config.copy(youtubeStudyMode = it)) },
            )

            // 3. Browser Study Mode
            UltraSettingCard(
                icon = { BrowserModeIcon() },
                title = "Browser Study Mode",
                checked = config.browserStudyMode,
                onCheckedChange = { onConfigChange(config.copy(browserStudyMode = it)) },
            )

            // 4. Uninstall Protection
            UltraSettingCard(
                icon = { TrashIcon() },
                title = "Uninstall Protection",
                subtitle = "Cannot uninstall StudyMode app",
                checked = config.uninstallProtection,
                onCheckedChange = { onConfigChange(config.copy(uninstallProtection = it)) },
            )

            // 5. Block Notifications
            UltraSettingCard(
                icon = { BlockNotificationIcon() },
                title = "Block Notifications",
                subtitle = "Block notifications from apps",
                checked = config.blockNotifications,
                onCheckedChange = { onConfigChange(config.copy(blockNotifications = it)) },
            )

            // 6. Block Phone home screen (Slide 7 feature with tap-to-showcase)
            UltraSettingCard(
                icon = { BlockHomeScreenIcon() },
                title = "Block Phone home screen",
                subtitle = "Home screen will be blocked during focus",
                checked = config.blockHomeScreen,
                onCheckedChange = { onConfigChange(config.copy(blockHomeScreen = it)) },
                onClick = onShowHomeScreenShowcase,
            )

            // 7. Block Shorts & Reels (Micro-Blocker for YouTube & Instagram)
            UltraSettingCard(
                icon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_short_video),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                title = "Block Shorts & Reels",
                subtitle = "Block doom-scrolling in YouTube & Instagram",
                checked = config.blockShortsReels,
                onCheckedChange = { onConfigChange(config.copy(blockShortsReels = it)) },
                onClick = onShowShortsShowcase,
            )
        }

        Spacer(Modifier.height(20.dp))

        // "Start Focus Now" Button (White full width pill)
        Button(
            onClick = onStartFocusNow,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
        ) {
            Text(
                text = "Start Focus Now",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun UltraSettingCard(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String? = null,
    badgeContent: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1D21),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(26.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    if (onClick != null) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF28382B),
                            modifier = Modifier.size(18.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "ⓘ",
                                    color = Color(0xFF69ED70),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (badgeContent != null) {
                    Spacer(Modifier.height(4.dp))
                    badgeContent()
                } else if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFF8E9096),
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF384F31),
                    uncheckedThumbColor = Color(0xFF888B93),
                    uncheckedTrackColor = Color(0xFF2C2D32),
                    uncheckedBorderColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

// =============================================================================
// VECTOR ICONS FOR ULTRA FOCUS SETTINGS
// =============================================================================

@Composable
private fun StrictModeIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.55f, 0f)
            lineTo(w * 0.15f, h * 0.55f)
            lineTo(w * 0.45f, h * 0.55f)
            lineTo(w * 0.35f, h)
            lineTo(w * 0.85f, h * 0.42f)
            lineTo(w * 0.55f, h * 0.42f)
            close()
        }
        drawPath(path, Color(0xFF9E9EA4))
    }
}

@Composable
private fun YouTubeIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color(0xFF9E9EA4),
            topLeft = Offset(0f, h * 0.22f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.56f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
        )
        val playPath = Path().apply {
            moveTo(w * 0.42f, h * 0.36f)
            lineTo(w * 0.64f, h * 0.5f)
            lineTo(w * 0.42f, h * 0.64f)
            close()
        }
        drawPath(playPath, Color(0xFF1C1D21))
    }
}

@Composable
private fun BrowserModeIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color(0xFF75757D),
            topLeft = Offset(w * 0.24f, h * 0.1f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
        )
        drawRoundRect(
            color = Color(0xFF9E9EA4),
            topLeft = Offset(w * 0.08f, h * 0.26f),
            size = androidx.compose.ui.geometry.Size(w * 0.68f, h * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
        )
    }
}

@Composable
private fun TrashIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        drawLine(
            Color(0xFF9E9EA4),
            Offset(w * 0.15f, h * 0.24f),
            Offset(w * 0.85f, h * 0.24f),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            Color(0xFF9E9EA4),
            Offset(w * 0.36f, h * 0.15f),
            Offset(w * 0.64f, h * 0.15f),
            strokeWidth = 2.dp.toPx(),
        )
        val body = Path().apply {
            moveTo(w * 0.24f, h * 0.28f)
            lineTo(w * 0.28f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.88f)
            lineTo(w * 0.76f, h * 0.28f)
        }
        drawPath(body, Color(0xFF9E9EA4), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawLine(
            Color(0xFF9E9EA4),
            Offset(w * 0.42f, h * 0.38f),
            Offset(w * 0.42f, h * 0.78f),
            strokeWidth = 1.5.dp.toPx(),
        )
        drawLine(
            Color(0xFF9E9EA4),
            Offset(w * 0.58f, h * 0.38f),
            Offset(w * 0.58f, h * 0.78f),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun BlockNotificationIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val bell = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            cubicTo(w * 0.3f, h * 0.15f, w * 0.25f, h * 0.5f, w * 0.2f, h * 0.72f)
            lineTo(w * 0.8f, h * 0.72f)
            cubicTo(w * 0.75f, h * 0.5f, w * 0.7f, h * 0.15f, w * 0.5f, h * 0.15f)
            close()
        }
        drawPath(bell, Color(0xFF9E9EA4), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawCircle(Color(0xFF9E9EA4), radius = 2.dp.toPx(), center = Offset(w * 0.5f, h * 0.84f))
        drawLine(
            Color(0xFF9E9EA4),
            Offset(w * 0.15f, h * 0.15f),
            Offset(w * 0.85f, h * 0.85f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
private fun BlockHomeScreenIcon() {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val roof = Path().apply {
            moveTo(w * 0.5f, h * 0.15f)
            lineTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.24f, h * 0.52f)
            lineTo(w * 0.24f, h * 0.88f)
            lineTo(w * 0.76f, h * 0.88f)
            lineTo(w * 0.76f, h * 0.52f)
            lineTo(w * 0.88f, h * 0.52f)
            close()
        }
        drawPath(roof, Color(0xFF9E9EA4))
    }
}
