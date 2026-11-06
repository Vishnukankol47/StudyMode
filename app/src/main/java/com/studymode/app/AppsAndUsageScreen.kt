package com.studymode.app

import android.app.Activity
import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Calendar
import java.util.Locale

private val AppsScreenInk = Color(0xFF0B0B0B)
private val AppsScreenPanel = Color(0xFF181818)
private val AppsScreenMuted = Color(0xFF929292)
private val AppsScreenGreen = Color(0xFF80D957)
private val AppsScreenGreenPanel = Color(0xFF233A20)
private val AppsScreenDivider = Color(0xFF292929)

private enum class AppPickerPurpose { LIMIT, BLOCKLIST }

private data class AppFeature(
    val title: String,
    val packageName: String,
    val featureName: String,
    val iconRes: Int? = null,
)

private val shortFormFeatures = listOf(
    AppFeature("YouTube Shorts", "com.google.android.youtube", "YouTube", R.drawable.ic_short_video),
    AppFeature("Instagram Reels", "com.instagram.android", "Instagram", R.drawable.ic_reel_video),
    AppFeature("Snapchat Spotlight", "com.snapchat.android", "Snapchat"),
    AppFeature("Facebook Reels", "com.facebook.katana", "Facebook"),
)

@Composable
fun AppsAndUsageScreen(
    store: LocalStudyStore,
    onDataChanged: () -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenReelsAnalysis: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(installedLaunchableApps(context)) }
    var pickerPurpose by remember { mutableStateOf<AppPickerPurpose?>(null) }
    var limitDialogApps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var showShortsShowcase by rememberSaveable { mutableStateOf(false) }
    var showReelsAnalysisInternal by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var showStrictDelayDialog by rememberSaveable { mutableStateOf(false) }
    var showAccessibilitySetupDialog by rememberSaveable { mutableStateOf(false) }
    var showOverlayPermissionPrompt by rememberSaveable { mutableStateOf(false) }
    var showWebsites by rememberSaveable { mutableStateOf(false) }
    var domainScheduleTarget by remember { mutableStateOf<BlockedDomain?>(null) }

    val usageAccess = remember(refreshKey) { hasUsageAccess(context) }
    val overlayEnabled = remember(refreshKey) { hasOverlayPermission(context) }
    val accessibilityEnabled = remember(refreshKey) { isBlockingServiceEnabled(context) }
    val blockedApps = remember(refreshKey) {
        store.blockedApps().filter { it.blocked }.associateBy { it.packageName }
    }
    val dailyLimits = remember(refreshKey) { store.appDailyLimits().associateBy { it.packageName } }
    val configuredLimitApps = remember(apps, dailyLimits) {
        dailyLimits.keys.mapNotNull { packageName -> apps.find { it.packageName == packageName } }
    }
    val blockedLaunchableApps = remember(apps, blockedApps) {
        blockedApps.keys.mapNotNull { packageName -> apps.find { it.packageName == packageName } }
    }
    val usage = remember(refreshKey, usageAccess, apps, dailyLimits) {
        if (usageAccess) {
            val usageRows = todayAppUsage(context, apps).associate { it.packageName to it.foregroundMillis }
            usageRows + dailyLimits.keys
                .filterNot { it in usageRows }
                .mapNotNull { packageName ->
                    apps.find { it.packageName == packageName }?.let {
                        packageName to todayAppUsageMillis(context, packageName)
                    }
                }
        } else {
            emptyMap()
        }
    }
    val focusOnlyBlocking = remember(refreshKey) { store.focusOnlyBlockingEnabled() }
    val pausedUntil = remember(refreshKey) { store.blockingPausedUntil() }
    var filterActive by remember { mutableStateOf(LocalDnsFilterService.isActive(context)) }
    var filterError by remember { mutableStateOf(LocalDnsFilterService.lastError(context)) }
    var vpnError by rememberSaveable { mutableStateOf<String?>(null) }
    val domainRules = remember(refreshKey) { store.blockedDomains() }
    val filterSupported = Build.VERSION.SDK_INT != Build.VERSION_CODES.P
    val notificationsEnabled = remember(refreshKey) { FocusTimerAlarms.notificationsEnabled(context) }

    fun refreshData() {
        refreshKey += 1
        apps = installedLaunchableApps(context)
        if (hasUsageAccess(context)) {
            AppMonitorService.start(context)
        }
        onDataChanged()
    }

    val usageSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshData()
    }
    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vpnError = null
            LocalDnsFilterService.start(context)
        } else {
            vpnError = "VPN access was not granted. Website filtering remains off."
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        refreshData()
    }

    LaunchedEffect(Unit) {
        while (true) {
            filterActive = LocalDnsFilterService.isActive(context)
            filterError = LocalDnsFilterService.lastError(context)
            delay(1_000)
        }
    }

    val frictionEnabled = remember(refreshKey) { store.isMindfulFrictionEnabled() }
    val strictDelayEnabled = remember(refreshKey) { store.isStrictUnblockDelayEnabled() }

    if (showReelsAnalysisInternal) {
        ReelShortsAnalysisScreen(
            store = store,
            onBack = { showReelsAnalysisInternal = false },
            onSetDailyLimit = {
                val shortApps = apps.filter { it.packageName in setOf(
                    "com.google.android.youtube",
                    "com.instagram.android",
                    "com.instagram.lite",
                    "com.facebook.katana",
                    "com.facebook.lite",
                    "com.snapchat.android"
                )}
                if (shortApps.isNotEmpty()) {
                    limitDialogApps = shortApps
                } else {
                    pickerPurpose = AppPickerPurpose.LIMIT
                }
                showReelsAnalysisInternal = false
            },
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E0A))
    ) {
        // Subtle organic green glow in the upper-right corner
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF143820).copy(alpha = 0.55f),
                            Color(0xFF0E2516).copy(alpha = 0.2f),
                            Color.Transparent,
                        ),
                        center = Offset(850f, -50f),
                        radius = 800f,
                    )
                )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp),
        ) {
            // Header: Back Arrow + Blocks + Help Pill
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { onBack?.invoke() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Text(
                            text = "←",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Blocks",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Surface(
                        onClick = { showHelp = true },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF131915),
                        border = BorderStroke(1.dp, Color(0xFF243528)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("💬", fontSize = 12.sp)
                            Text(
                                text = "Help",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
                Text(
                    text = "Block distracting apps and stay focused.",
                    color = Color(0xFF8FA393),
                    fontSize = 13.5.sp,
                    modifier = Modifier.padding(start = 42.dp, bottom = 20.dp),
                )
            }

            // SECTION 1: App Controls
            item {
                BlocksSectionHeader(
                    iconRes = R.drawable.ic_blocks_grid,
                    title = "App Controls",
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_hourglass,
                    title = "App Limits",
                    subtitle = "Set daily time limits for specific apps.",
                    onClick = { pickerPurpose = AppPickerPurpose.LIMIT },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "+ Add App",
                                color = Color(0xFF4ADE80),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = " >",
                                color = Color(0xFF4ADE80),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_prohibited,
                    title = "App Blocks",
                    subtitle = "Block selected apps any time.",
                    onClick = { pickerPurpose = AppPickerPurpose.BLOCKLIST },
                    trailingContent = {
                        Text(
                            text = ">",
                            color = Color(0xFF9CA3AF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_lungs,
                    title = "Mindful Pre-Open Friction",
                    subtitle = "Add a short pause or breathing exercise before opening restricted apps.",
                    onClick = {
                        val next = !frictionEnabled
                        store.setMindfulFrictionEnabled(next)
                        refreshData()
                    },
                    trailingContent = {
                        Switch(
                            checked = frictionEnabled,
                            onCheckedChange = { enabled ->
                                store.setMindfulFrictionEnabled(enabled)
                                refreshData()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF22C55E),
                                uncheckedThumbColor = Color(0xFF9EABA1),
                                uncheckedTrackColor = Color(0xFF2B362E),
                                checkedBorderColor = Color.Transparent,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    },
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_shield_lock,
                    title = "Strict Unlock Delay",
                    subtitle = "Require a 15–60s cooldown before pausing or unblocking apps.",
                    onClick = {
                        val next = !strictDelayEnabled
                        store.setStrictUnblockDelayEnabled(next)
                        refreshData()
                    },
                    trailingContent = {
                        Switch(
                            checked = strictDelayEnabled,
                            onCheckedChange = { enabled ->
                                store.setStrictUnblockDelayEnabled(enabled)
                                refreshData()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF22C55E),
                                uncheckedThumbColor = Color(0xFF9EABA1),
                                uncheckedTrackColor = Color(0xFF2B362E),
                                checkedBorderColor = Color.Transparent,
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    },
                )
            }

            // Divider 1
            item {
                HorizontalDivider(
                    color = Color(0xFF18231C),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            // SECTION 2: Web & Content
            item {
                BlocksSectionHeader(
                    iconRes = R.drawable.ic_blocks_globe,
                    title = "Web & Content",
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_globe,
                    title = "Block Websites",
                    subtitle = "Manage domains and local VPN filtering.",
                    onClick = { showWebsites = true },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "Manage",
                                color = Color(0xFF4ADE80),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = " >",
                                color = Color(0xFF4ADE80),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_clapperboard,
                    title = "Reel/Shorts Analysis",
                    subtitle = "Track and analyze your short-form video usage (reels, shorts, etc.) with insights and trends.",
                    onClick = {
                        if (onOpenReelsAnalysis != null) {
                            onOpenReelsAnalysis()
                        } else {
                            showReelsAnalysisInternal = true
                        }
                    },
                    trailingContent = {
                        Text(
                            text = ">",
                            color = Color(0xFF9CA3AF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }

            // Divider 2
            item {
                HorizontalDivider(
                    color = Color(0xFF18231C),
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }

            // SECTION 3: Access & Permissions
            item {
                BlocksSectionHeader(
                    iconRes = R.drawable.ic_blocks_gear,
                    title = "Access & Permissions",
                )
            }

            item {
                BlocksSettingRow(
                    iconRes = R.drawable.ic_blocks_gear,
                    title = "App Permissions",
                    subtitle = "Manage required permissions for blocking, tracking, and overlays.",
                    onClick = { showOverlayPermissionPrompt = true },
                    trailingContent = {
                        Text(
                            text = ">",
                            color = Color(0xFF9CA3AF),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }
        }
    }

    val selectableApps = remember(apps) {
        apps.filter { it.packageName != context.packageName && it.packageName != "com.android.settings" }
    }

    when (pickerPurpose) {
        AppPickerPurpose.LIMIT -> AppPickerDialog(
            title = "Select apps for daily limits",
            apps = selectableApps,
            initiallySelected = emptySet(),
            onDismiss = { pickerPurpose = null },
            onConfirm = { selected ->
                pickerPurpose = null
                if (selected.isNotEmpty()) limitDialogApps = selected
            },
        )
        AppPickerPurpose.BLOCKLIST -> AppPickerDialog(
            title = "Select apps to block",
            apps = selectableApps,
            initiallySelected = blockedApps.keys,
            onDismiss = { pickerPurpose = null },
            onConfirm = { selected ->
                pickerPurpose = null
                val selectedPackages = selected.mapTo(mutableSetOf()) { it.packageName }
                blockedApps.keys.forEach { packageName ->
                    val app = apps.find { it.packageName == packageName }
                    if (packageName !in selectedPackages && app != null) {
                        store.setAppBlocked(packageName, app.label, false)
                    }
                }
                selected.forEach { app ->
                    store.setAppBlocked(app.packageName, app.label, true)
                }
                if (selected.isNotEmpty() && !overlayEnabled && !accessibilityEnabled) {
                    showOverlayPermissionPrompt = true
                }
                refreshData()
            },
        )
        null -> Unit
    }

    if (showOverlayPermissionPrompt) {
        AlertDialog(
            onDismissRequest = { showOverlayPermissionPrompt = false },
            containerColor = Color(0xFF101510),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF233A25), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🛡️", fontSize = 18.sp)
                    }
                    Text(
                        text = "Enable Display Permission",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp,
                    )
                }
            },
            text = {
                Text(
                    text = "To show the block screen over restricted apps (like YouTube and Brave), please enable 'Display over other apps'.\n\nUnlike Accessibility, this permission is 100% safe for BHIM, Google Pay, and banking apps without triggering security warnings.",
                    color = Color(0xFFA4AAA4),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverlayPermissionPrompt = false
                        openOverlaySettings(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF80D957),
                        contentColor = Color(0xFF080A08),
                    ),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showOverlayPermissionPrompt = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFA4AAA4)),
                ) {
                    Text("Later")
                }
            },
        )
    }

    limitDialogApps?.let { selectedApps ->
        val existingLimit = selectedApps.firstOrNull()?.let { dailyLimits[it.packageName] }
        AppDailyLimitDialog(
            apps = selectedApps,
            currentLimit = existingLimit?.minutes,
            currentWarningPercent = existingLimit?.warningPercent ?: 0,
            currentBlockingEnabled = existingLimit?.blockingEnabled ?: true,
            onDismiss = { limitDialogApps = null },
            onSave = { minutes, warningPercent, blockingEnabled ->
                selectedApps.forEach { app ->
                    store.setAppDailyLimit(
                        app.packageName,
                        app.label,
                        minutes,
                        warningPercent,
                        blockingEnabled,
                    )
                }
                limitDialogApps = null
                refreshData()
            },
        )
    }

    if (showShortsShowcase) {
        BlockShortsShowcaseSheet(onDismiss = { showShortsShowcase = false })
    }

    if (showWebsites) {
        WebsiteManagerDialog(
            store = store,
            rules = domainRules,
            filterActive = filterActive,
            filterSupported = filterSupported,
            filterError = vpnError ?: filterError,
            onDismiss = { showWebsites = false },
            onChanged = { refreshData() },
            onSchedule = { domainScheduleTarget = it },
            onToggleFilter = {
                if (filterActive) {
                    LocalDnsFilterService.stop(context)
                } else if (filterSupported) {
                    val consentIntent = VpnService.prepare(context)
                    if (consentIntent == null) {
                        LocalDnsFilterService.start(context)
                    } else {
                        vpnConsent.launch(consentIntent)
                    }
                }
                refreshKey += 1
            },
        )
    }

    domainScheduleTarget?.let { rule ->
        DomainScheduleDialog(
            rule = rule,
            onDismiss = { domainScheduleTarget = null },
            onSave = { days, startMinute, endMinute ->
                store.setBlockedDomainSchedule(rule.domain, days, startMinute, endMinute)
                domainScheduleTarget = null
                refreshData()
            },
            onClear = {
                store.clearBlockedDomainSchedule(rule.domain)
                domainScheduleTarget = null
                refreshData()
            },
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("About Blocks") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App limits use Android Usage Access to measure today's foreground time. The Accessibility service is needed to return a restricted app to Home.")
                    Text("Shorts and Reels cannot be identified reliably from other app screens. Their fallback blocks the whole YouTube or Instagram app.")
                    Text("Website rules filter hostnames while the local VPN is active. Apps using encrypted DNS or direct IP connections may bypass the filter.")
                    Text("Optional app-limit warning notifications require Android notification permission.")
                    Text("Android does not let an ordinary app prevent uninstalling, disable split-screen globally, or control other apps' floating windows.")
                    Text("All settings and usage checks stay on this device. You can remove restrictions or turn off the optional services at any time.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Close", color = AppsScreenGreen) }
            },
        )
    }

    if (showStrictDelayDialog) {
        StrictUnblockDelayDialog(
            totalSeconds = store.strictUnblockDelaySeconds(),
            targetActionDescription = "pause blocking for 5 minutes",
            onDismiss = { showStrictDelayDialog = false },
            onConfirmed = {
                showStrictDelayDialog = false
                store.pauseBlockingForFiveMinutes()
                refreshData()
            }
        )
    }

    if (showAccessibilitySetupDialog) {
        AccessibilitySetupDialog(
            onDismiss = {
                showAccessibilitySetupDialog = false
                refreshData()
            },
        )
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (action != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(
                        action,
                        color = AppsScreenGreen,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        if (subtitle != null) {
            Text(subtitle, color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EmptyCard(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InfoCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, color = AppsScreenGreen, fontWeight = FontWeight.SemiBold)
            Text(description, color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun AppLimitCard(
    app: LaunchableApp,
    limit: AppDailyLimit,
    usedMillis: Long?,
    onEdit: () -> Unit,
    onToggleBlocking: () -> Unit,
    onBlockNow: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember(app.packageName) { mutableStateOf(false) }
    var confirmRemoval by remember(app.packageName) { mutableStateOf(false) }
    val limitMillis = limit.minutes * 60_000L

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app, Modifier.size(48.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(app.label, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatUsageDuration(limitMillis)} Limit",
                    color = AppsScreenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (usedMillis != null) {
                    val remaining = (limitMillis - usedMillis).coerceAtLeast(0L)
                    Text(
                        "${formatUsageDuration(usedMillis)} used · ${formatUsageDuration(remaining)} left",
                        color = if (usedMillis >= limitMillis) Color(0xFFFF5252) else AppsScreenGreen,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (usedMillis >= limitMillis) FontWeight.Bold else FontWeight.Normal,
                    )
                } else {
                    Text("Usage requires Usage Access", color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (!limit.blockingEnabled) {
                    Text("Limit tracking only · blocking off", color = AppsScreenMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
            OutlinedButton(
                onClick = onEdit,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("Edit", fontSize = 12.sp)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Text("⋮", color = AppsScreenMuted, fontSize = 22.sp)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit limit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Block app now (Instant)") },
                        onClick = {
                            menuExpanded = false
                            onBlockNow()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (limit.blockingEnabled) "Disable limit blocking" else "Enable limit blocking") },
                        onClick = {
                            menuExpanded = false
                            onToggleBlocking()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Remove limit") },
                        onClick = {
                            menuExpanded = false
                            confirmRemoval = true
                        },
                    )
                }
            }
        }
    }
    if (confirmRemoval) {
        AlertDialog(
            onDismissRequest = { confirmRemoval = false },
            title = { Text("Remove app limit?") },
            text = { Text("The daily usage limit for ${app.label} will be removed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoval = false
                        onRemove()
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoval = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BlockedAppCard(
    app: LaunchableApp,
    blocked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app, Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Medium)
                Text(
                    if (blocked) "Selected for blocking" else "Not blocked",
                    color = if (blocked) AppsScreenGreen else AppsScreenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = blocked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun FeatureBlockCard(
    feature: AppFeature,
    app: LaunchableApp?,
    isShortsBlocked: Boolean,
    isAppFullyBlocked: Boolean,
    accessibilityEnabled: Boolean,
    focusOnly: Boolean,
    onToggle: (Boolean) -> Unit,
    onBlockEntireApp: () -> Unit = {},
    onEnableAccessibility: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    if (feature.iconRes != null) {
                        Image(
                            painter = painterResource(feature.iconRes),
                            contentDescription = feature.title,
                            modifier = Modifier.size(36.dp),
                            contentScale = ContentScale.Fit,
                        )
                    } else if (app != null) {
                        AppIcon(app, Modifier.size(38.dp))
                    } else {
                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = AppsScreenGreenPanel,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⚡", fontSize = 18.sp)
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            feature.title,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isShortsBlocked && !isAppFullyBlocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = AppsScreenGreenPanel,
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    AppsScreenGreen.copy(alpha = 0.4f),
                                ),
                            ) {
                                Text(
                                    "Micro-block",
                                    color = AppsScreenGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Text(
                        when {
                            isAppFullyBlocked -> "Entire ${feature.featureName} app is blocked (100% UPI safe)"
                            isShortsBlocked && false -> ""
                            isShortsBlocked -> "Doom-scrolling video feed blocked • Study videos & chats open"
                            app == null -> "Install ${feature.featureName} to block feeds"
                            else -> "Allowed (No distraction blocking active)"
                        },
                        color = when {
                            isAppFullyBlocked -> Color(0xFFFFB74D)
                            isShortsBlocked && false -> Color(0xFFFFB74D)
                            isShortsBlocked -> AppsScreenGreen
                            else -> AppsScreenMuted
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Switch(
                    checked = isShortsBlocked,
                    enabled = !isAppFullyBlocked,
                    onCheckedChange = onToggle,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AppsScreenGreen,
                    ),
                )
            }

            if (false) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E1810),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFB74D).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "In-app tab filtering requires Accessibility Service.",
                            color = Color(0xFFFFB74D),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            androidx.compose.material3.Button(
                                onClick = onEnableAccessibility,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB74D),
                                    contentColor = Color(0xFF1E1810),
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Enable Access", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = onBlockEntireApp,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF86EFAC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28542D)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Block Full App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsiteCard(
    filterActive: Boolean,
    filterSupported: Boolean,
    enabledRuleCount: Int,
    onManage: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
        onClick = onManage,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = AppsScreenGreenPanel) {
                Box(contentAlignment = Alignment.Center) {
                    Text("◎", color = AppsScreenGreen, fontSize = 26.sp)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Block Websites", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        !filterSupported -> "Unavailable on Android 9"
                        filterActive -> "$enabledRuleCount active domain rules"
                        else -> "$enabledRuleCount rules · VPN filter off"
                    },
                    color = if (filterActive) AppsScreenGreen else AppsScreenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Manage domains and local VPN filtering", color = AppsScreenMuted, style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = onManage) { Text("Manage") }
        }
    }
}

@Composable
private fun StrictModeCard(
    title: String,
    description: String,
    status: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    setupLabel: String?,
    onSetup: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold)
                    Text(status, color = if (status == "Available") AppsScreenGreen else AppsScreenMuted, style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            }
            Text(description, color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
            if (setupLabel != null) {
                TextButton(onClick = onSetup) { Text(setupLabel, color = AppsScreenGreen) }
            }
        }
    }
}

@Composable
private fun UnsupportedProtectionCard(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                Text("Unavailable", color = AppsScreenMuted, style = MaterialTheme.typography.labelMedium)
            }
            Text(description, color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun UsageCard(label: String, duration: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = AppsScreenPanel,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Text(duration, color = AppsScreenGreen)
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    enabled: Boolean,
    description: String,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(if (enabled) "On" else "Off", color = if (enabled) AppsScreenGreen else AppsScreenMuted)
            }
            Text(description, color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onClick) { Text(buttonLabel) }
        }
    }
}

@Composable
internal fun AppPickerDialog(
    title: String,
    apps: List<LaunchableApp>,
    initiallySelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<LaunchableApp>) -> Unit,
) {
    var search by rememberSaveable(title) { mutableStateOf("") }
    var selectedPackages by remember(title) { mutableStateOf(initiallySelected) }
    val filtered = remember(apps, search) {
        val query = search.trim().lowercase(Locale.getDefault())
        if (query.isEmpty()) apps else apps.filter {
            it.label.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.lowercase(Locale.ROOT).contains(query)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search installed apps") },
                    singleLine = true,
                )
                Text("${selectedPackages.size} selected", color = AppsScreenGreen)
                if (filtered.isEmpty()) {
                    Text("No installed apps match this search.", color = AppsScreenMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 390.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val selected = app.packageName in selectedPackages
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = if (selected) AppsScreenGreenPanel else AppsScreenPanel,
                                onClick = {
                                    selectedPackages = if (selected) {
                                        selectedPackages - app.packageName
                                    } else {
                                        selectedPackages + app.packageName
                                    }
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    AppIcon(app, Modifier.size(38.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.label, fontWeight = FontWeight.Medium)
                                        Text(app.packageName, color = AppsScreenMuted, style = MaterialTheme.typography.labelSmall)
                                    }
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedPackages = if (it) {
                                                selectedPackages + app.packageName
                                            } else {
                                                selectedPackages - app.packageName
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(apps.filter { it.packageName in selectedPackages })
                },
            ) { Text("Confirm", color = AppsScreenGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AppDailyLimitDialog(
    apps: List<LaunchableApp>,
    currentLimit: Int?,
    currentWarningPercent: Int,
    currentBlockingEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Boolean) -> Unit,
) {
    val appNames = apps.joinToString { it.label }
    var selectedMinutes by rememberSaveable(appNames) { mutableIntStateOf(currentLimit ?: 60) }
    var warningPercent by rememberSaveable(appNames) { mutableIntStateOf(currentWarningPercent) }
    var blockingEnabled by rememberSaveable(appNames) { mutableStateOf(currentBlockingEnabled) }
    val minuteOptions = listOf(15, 30, 60, 100, 120, 180, 240, 360, 600)
    val warningOptions = listOf(0, 50, 75, 90)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (apps.size == 1) "Daily app limit" else "Set daily app limits") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (apps.size == 1) appNames else "Apply the same daily limit to ${apps.size} selected apps.",
                    color = AppsScreenMuted,
                )
                Text("Daily time limit", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    minuteOptions.forEach { minutes ->
                        OutlinedButton(onClick = { selectedMinutes = minutes }) {
                            Text(
                                if (selectedMinutes == minutes) "✓ ${formatUsageDuration(minutes * 60_000L)}"
                                else formatUsageDuration(minutes * 60_000L),
                                color = if (selectedMinutes == minutes) AppsScreenGreen else AppsScreenMuted,
                            )
                        }
                    }
                }
                Text("Warn me at", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    warningOptions.forEach { percent ->
                        OutlinedButton(onClick = { warningPercent = percent }) {
                            Text(
                                if (warningPercent == percent) {
                                    if (percent == 0) "✓ Off" else "✓ $percent%"
                                } else {
                                    if (percent == 0) "Off" else "$percent%"
                                },
                                color = if (warningPercent == percent) AppsScreenGreen else AppsScreenMuted,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block when limit is reached", fontWeight = FontWeight.Medium)
                        Text("Usage resets at local midnight.", color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = blockingEnabled, onCheckedChange = { blockingEnabled = it })
                }
                Text(
                    "Requires Usage Access and the StudyMode Accessibility blocker. Android can delay usage updates.",
                    color = AppsScreenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selectedMinutes, warningPercent, blockingEnabled) }) {
                Text("Save", color = AppsScreenGreen)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@Composable
private fun WebsiteManagerDialog(
    store: LocalStudyStore,
    rules: List<BlockedDomain>,
    filterActive: Boolean,
    filterSupported: Boolean,
    filterError: String?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit,
    onSchedule: (BlockedDomain) -> Unit,
    onToggleFilter: () -> Unit,
) {
    var domainInput by rememberSaveable { mutableStateOf("") }
    var editingDomain by rememberSaveable { mutableStateOf<String?>(null) }
    var domainError by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Block websites") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text(
                    if (filterActive) "Local VPN filter is active." else "Add domains, then enable the local VPN filter.",
                    color = if (filterActive) AppsScreenGreen else AppsScreenMuted,
                )
                Button(
                    onClick = onToggleFilter,
                    enabled = filterActive || filterSupported,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppsScreenGreen,
                        contentColor = AppsScreenInk,
                    ),
                ) {
                    Text(if (filterActive) "Turn off website filter" else "Enable website filter")
                }
                if (!filterSupported) {
                    Text("Unavailable on Android 9 to preserve its Private DNS behavior.", color = AppsScreenMuted)
                }
                OutlinedTextField(
                    value = domainInput,
                    onValueChange = {
                        domainInput = it.take(253)
                        domainError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("example.com") },
                    singleLine = true,
                )
                Button(
                    onClick = {
                        try {
                            if (editingDomain == null) {
                                store.addBlockedDomain(domainInput)
                            } else {
                                store.renameBlockedDomain(editingDomain!!, domainInput)
                            }
                            domainInput = ""
                            editingDomain = null
                            domainError = null
                            onChanged()
                        } catch (exception: IllegalArgumentException) {
                            domainError = exception.message ?: "Enter a valid hostname."
                        } catch (exception: android.database.SQLException) {
                            domainError = exception.message ?: "The website rule could not be saved."
                        } catch (exception: IllegalStateException) {
                            domainError = exception.message ?: "The website rule could not be saved."
                        }
                    },
                    enabled = domainInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppsScreenGreen,
                        contentColor = AppsScreenInk,
                    ),
                ) {
                    Text(if (editingDomain == null) "Add domain" else "Save domain")
                }
                domainError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (rules.isEmpty()) {
                    Text("No domains configured.", color = AppsScreenMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(rules, key = { it.domain }) { rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rule.domain, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (rule.activeDays.isEmpty()) {
                                            if (rule.enabled) "Always active" else "Rule disabled"
                                        } else {
                                            "${if (rule.enabled) "Enabled" else "Disabled"} · ${formatDomainSchedule(rule)}"
                                        },
                                        color = if (rule.enabled) AppsScreenGreen else AppsScreenMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                TextButton(onClick = { onSchedule(rule) }) {
                                    Text("Time", color = AppsScreenGreen)
                                }
                                Switch(
                                    checked = rule.enabled,
                                    onCheckedChange = {
                                        store.setBlockedDomainEnabled(rule.domain, it)
                                        onChanged()
                                    },
                                )
                                TextButton(
                                    onClick = {
                                        editingDomain = rule.domain
                                        domainInput = rule.domain
                                    },
                                ) { Text("Edit", color = AppsScreenGreen) }
                                TextButton(onClick = { deleteTarget = rule.domain }) {
                                    Text("Remove", color = AppsScreenMuted)
                                }
                            }
                        }
                    }
                }
                filterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text(
                    "Filters hostnames and subdomains only while this VPN is active, and only during each rule's selected days and times. It may cover browsers and apps that use system DNS, but apps using encrypted DNS or direct IP connections may bypass it. It does not filter URL paths or distinguish Shorts from YouTube. Android allows one VPN at a time.",
                    color = AppsScreenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = AppsScreenGreen) } },
    )

    deleteTarget?.let { domain ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Remove blocked domain?") },
            text = { Text("$domain will be removed from your local website rules.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        store.removeBlockedDomain(domain)
                        deleteTarget = null
                        if (editingDomain == domain) {
                            editingDomain = null
                            domainInput = ""
                        }
                        onChanged()
                    },
                ) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DomainScheduleDialog(
    rule: BlockedDomain,
    onDismiss: () -> Unit,
    onSave: (Set<Int>, Int, Int) -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val allDays = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY,
    )
    var selectedDays by remember(rule.domain) {
        mutableStateOf(rule.activeDays.ifEmpty { allDays.toSet() })
    }
    var startMinute by remember(rule.domain) {
        mutableIntStateOf(rule.startMinute.takeIf { it in 0 until 1440 } ?: 9 * 60)
    }
    var endMinute by remember(rule.domain) {
        mutableIntStateOf(rule.endMinute.takeIf { it in 0 until 1440 } ?: 17 * 60)
    }
    var error by rememberSaveable(rule.domain) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule ${rule.domain}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Block this domain only during the selected local times.", color = AppsScreenMuted)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    allDays.forEach { day ->
                        val dayLabel = Calendar.getInstance().apply {
                            set(Calendar.DAY_OF_WEEK, day)
                        }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                            ?.take(2)
                            .orEmpty()
                        OutlinedButton(
                            onClick = {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                                error = null
                            },
                        ) {
                            Text(
                                dayLabel,
                                color = if (day in selectedDays) AppsScreenGreen else AppsScreenMuted,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, startMinute / 60)
                            set(Calendar.MINUTE, startMinute % 60)
                        }
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> startMinute = hour * 60 + minute },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            android.text.format.DateFormat.is24HourFormat(context),
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Starts at ${formatMinute(startMinute)}") }
                OutlinedButton(
                    onClick = {
                        val calendar = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, endMinute / 60)
                            set(Calendar.MINUTE, endMinute % 60)
                        }
                        TimePickerDialog(
                            context,
                            { _, hour, minute -> endMinute = hour * 60 + minute },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            android.text.format.DateFormat.is24HourFormat(context),
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Ends at ${formatMinute(endMinute)}") }
                Text("Overnight ranges are supported.", color = AppsScreenMuted, style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selectedDays.isEmpty()) {
                        error = "Select at least one day."
                    } else if (startMinute == endMinute) {
                        error = "Choose different start and end times."
                    } else {
                        onSave(selectedDays, startMinute, endMinute)
                    }
                },
            ) { Text("Save schedule", color = AppsScreenGreen) }
        },
        dismissButton = {
            Row {
                if (rule.activeDays.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("Always", color = AppsScreenMuted) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun formatDomainSchedule(rule: BlockedDomain): String {
    val weekOrder = listOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY,
    )
    val days = weekOrder.filter { it in rule.activeDays }.joinToString(", ") { day ->
        Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, day) }
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
            ?.take(3)
            .orEmpty()
    }
    if (rule.startMinute !in 0 until 1440 || rule.endMinute !in 0 until 1440) return days
    return "$days · ${formatMinute(rule.startMinute)}–${formatMinute(rule.endMinute)}"
}

private fun formatMinute(minute: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60)
        set(Calendar.MINUTE, minute % 60)
    }
    return java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(calendar.time)
}

internal object AppIconCache {
    private val cache = android.util.LruCache<String, android.graphics.drawable.Drawable>(200)

    fun getIcon(context: Context, packageName: String): android.graphics.drawable.Drawable {
        val cached = cache.get(packageName)
        if (cached != null) return cached
        val drawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            context.packageManager.defaultActivityIcon
        }
        cache.put(packageName, drawable)
        return drawable
    }
}

@Composable
fun AppIcon(app: LaunchableApp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val icon = remember(app.packageName) { AppIconCache.getIcon(context, app.packageName) }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            ImageView(viewContext).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = app.label
                setImageDrawable(icon)
            }
        },
        update = { imageView ->
            imageView.setImageDrawable(icon)
        },
    )
}



@Composable
private fun MindfulFrictionCard(
    enabled: Boolean,
    defaultTab: String,
    pauseSeconds: Int,
    targetBreaths: Int,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectTab: (String) -> Unit,
    onSelectPauseSeconds: (Int) -> Unit,
    onSelectBreaths: (Int) -> Unit,
    onPreview: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = AppsScreenPanel,
        border = BorderStroke(1.dp, AppsScreenDivider),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color(0xFF132B1C), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF265236), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🫁", fontSize = 20.sp)
                    }
                    Column {
                        Text("Pre-Open Mindful Friction", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        Text(
                            if (enabled) "Pause before restricted apps open" else "Disabled (Immediate block)",
                            color = if (enabled) Color(0xFF4ADE80) else AppsScreenMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF07150C),
                        checkedTrackColor = Color(0xFF4ADE80),
                        uncheckedThumbColor = AppsScreenMuted,
                        uncheckedTrackColor = Color(0xFF242424),
                    )
                )
            }

            if (enabled) {
                HorizontalDivider(color = AppsScreenDivider, thickness = 0.5.dp)

                // 1. Default Mode
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default Intervention Mode", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppsScreenMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "BREATHE" to "🫁 Deep Breath",
                            "PAUSE" to "⏳ 10s Pause",
                            "STATS" to "📊 Usage Stats"
                        ).forEach { (id, label) ->
                            val isSelected = defaultTab == id
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isSelected) Color(0xFF193B26) else Color(0xFF111713),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) Color(0xFF4ADE80) else Color(0xFF223628),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelectTab(id) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF8E9993),
                                )
                            }
                        }
                    }
                }

                // 2. Breathing Cycles & Pause Timer configuration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Breaths Target
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Breaths Target", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppsScreenMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3).forEach { count ->
                                val isSelected = targetBreaths == count
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) Color(0xFF193B26) else Color(0xFF111713),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF4ADE80) else Color(0xFF223628),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectBreaths(count) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF8E9993),
                                    )
                                }
                            }
                        }
                    }

                    // Pause Seconds
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Pause Timer", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AppsScreenMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5, 10, 15).forEach { secs ->
                                val isSelected = pauseSeconds == secs
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(
                                            if (isSelected) Color(0xFF193B26) else Color(0xFF111713),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) Color(0xFF4ADE80) else Color(0xFF223628),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectPauseSeconds(secs) }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${secs}s",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF8E9993),
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Test Preview Action
                Button(
                    onClick = onPreview,
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF162E20),
                        contentColor = Color(0xFF4ADE80),
                    ),
                    border = BorderStroke(1.dp, Color(0xFF2E6342)),
                ) {
                    Text("🌿 Test Mindful Friction Overlay", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatUsageDuration(millis: Long): String {
    val minutes = millis / 60_000
    return if (minutes >= 60) {
        "%dh %02dm".format(Locale.getDefault(), minutes / 60, minutes % 60)
    } else {
        "${minutes}m"
    }
}


@Composable
fun ZenModeSettingsCard(
    store: LocalStudyStore,
    refreshKey: Int,
    onSettingsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showZenStartDialog by remember { mutableStateOf(false) }
    val isZenActive = remember(refreshKey) { store.isZenModeActive() }
    val remainingZenMinutes = remember(refreshKey) { (store.zenModeRemainingMillis() / 60_000L).toInt() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0C1610),
        border = BorderStroke(1.dp, Color(0xFF1B3825)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF132F1D)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_shield),
                            contentDescription = "Zen Lockout",
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (isZenActive) "Zen Mode Active (${remainingZenMinutes}m left)" else "Zen Mode Inactive",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (isZenActive) "Device is locked down for exam study" else "Unbreakable digital detox for exams",
                            color = Color(0xFF8BA393),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (isZenActive) {
                        ZenLockOverlayActivity.start(context)
                    } else {
                        showZenStartDialog = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isZenActive) Color(0xFF22C55E) else Color(0xFF142E1F)
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = if (isZenActive) "View Fullscreen Zen Lockout" else "⚡ Launch Zen Exam Lockout",
                    color = if (isZenActive) Color(0xFF031407) else Color(0xFF4ADE80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }

    if (showZenStartDialog) {
        ZenModeStartDialog(
            store = store,
            onDismiss = { showZenStartDialog = false },
            onZenStarted = {
                showZenStartDialog = false
                onSettingsChanged()
            }
        )
    }
}

@Composable
private fun BankingCompatibilityStatusCard(
    accessibilityEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!accessibilityEnabled) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0D1F12),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E4624)),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFF1B3D23), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🛡️", fontSize = 18.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "100% Banking & UPI Safe",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4ADE80),
                            fontSize = 14.sp,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1B3D23),
                        ) {
                            Text(
                                "BHIM / GPay Ready",
                                color = Color(0xFF86EFAC),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "StudyMode is running with zero accessibility permissions. BHIM, Google Pay, PhonePe, and all banking apps run with zero security alerts.",
                        color = Color(0xFFBCE3BE),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF26180B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF5E3914)),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Text(
                            "Accessibility is Active",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB74D),
                            fontSize = 14.sp,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF4A2B0F),
                    ) {
                        Text(
                            "May Flag BHIM",
                            color = Color(0xFFFFD180),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    "Accessibility is enabled for YouTube Shorts / Reels micro-blocking. If BHIM shows a security alert, you can turn OFF Accessibility below — app limits and Zen Mode will still work perfectly with zero alerts.",
                    color = Color(0xFFE8D4C0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )
                Button(
                    onClick = onOpenAccessibility,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD97706),
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    Text(
                        "Turn OFF Accessibility in Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BlocksSectionHeader(
    iconRes: Int,
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 6.dp, start = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
        )
        Text(
            text = title,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BlocksSettingRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color(0xFF8FA393),
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
            )
        }
        trailingContent()
    }
}
