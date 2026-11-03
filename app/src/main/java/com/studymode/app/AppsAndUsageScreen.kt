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
) {
    val context = LocalContext.current
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var apps by remember { mutableStateOf(installedLaunchableApps(context)) }
    var pickerPurpose by remember { mutableStateOf<AppPickerPurpose?>(null) }
    var limitDialogApps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var showShortsShowcase by rememberSaveable { mutableStateOf(false) }
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
        AppBlockingAccessibilityService.updateConfiguration(context)
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Blocks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Block distracting apps and stay focused.", color = AppsScreenMuted)
                }
                OutlinedButton(
                    onClick = { showHelp = true },
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("💬  Help", fontSize = 13.sp)
                }
            }
        }

        item {
            BankingCompatibilityStatusCard(
                accessibilityEnabled = accessibilityEnabled,
                onOpenAccessibility = { openAccessibilitySettings(context) },
            )
        }

        item {
            SectionHeading(
                title = "App Limits",
                subtitle = "Set daily time limits for specific apps.",
                action = "+ Add App",
                onAction = { pickerPurpose = AppPickerPurpose.LIMIT },
            )
        }
        if (configuredLimitApps.isEmpty()) {
            item {
                EmptyCard(
                    title = "No app limits yet",
                    subtitle = "Tap here or + Add App to set daily usage limits.",
                    onClick = { pickerPurpose = AppPickerPurpose.LIMIT },
                )
            }
        } else {
            items(configuredLimitApps, key = { "limit:${it.packageName}" }) { app ->
                val limit = dailyLimits[app.packageName] ?: return@items
                AppLimitCard(
                    app = app,
                    limit = limit,
                    usedMillis = usage[app.packageName],
                    onEdit = { limitDialogApps = listOf(app) },
                    onToggleBlocking = {
                        store.setAppDailyLimit(
                            app.packageName,
                            app.label,
                            limit.minutes,
                            limit.warningPercent,
                            !limit.blockingEnabled,
                        )
                        refreshData()
                    },
                    onBlockNow = {
                        store.setAppBlocked(app.packageName, app.label, true)
                        refreshData()
                    },
                    onRemove = {
                        store.setAppDailyLimit(app.packageName, app.label, null)
                        refreshData()
                    },
                )
            }
        }

        item {
            SectionHeading(
                title = "App Blocks",
                action = "+ Add",
                onAction = { pickerPurpose = AppPickerPurpose.BLOCKLIST },
            )
        }
        item {
            InfoCard(
                title = if (focusOnlyBlocking) "Focus-only blocking is on" else "Selected apps are blocked any time",
                description = if (focusOnlyBlocking) {
                    "Manual app blocks apply while a focus timer is running. Schedule-selected apps follow their schedule."
                } else {
                    "Manual app blocks apply whenever App Protection is enabled."
                },
            )
        }
        if (blockedLaunchableApps.isNotEmpty() && !overlayEnabled && !accessibilityEnabled) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF281C10),
                    border = BorderStroke(1.dp, Color(0xFF5E3C18)),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Display Permission Needed",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D),
                                fontSize = 14.sp,
                            )
                        }
                        Text(
                            "To display the block screen when you open YouTube or other blocked apps, enable 'Display over other apps'. It works safely without triggering BHIM or banking alerts.",
                            color = Color(0xFFDCC8B4),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                        Button(
                            onClick = { openOverlaySettings(context) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB74D),
                                contentColor = Color(0xFF101312),
                            ),
                        ) {
                            Text("Enable 'Display over other apps'", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        if (blockedLaunchableApps.isEmpty()) {
            item {
                EmptyCard(
                    title = "No apps selected",
                    subtitle = "Tap here or + Add to choose apps to block.",
                    onClick = { pickerPurpose = AppPickerPurpose.BLOCKLIST },
                )
            }
        } else {
            items(blockedLaunchableApps, key = { "blocked:${it.packageName}" }) { app ->
                BlockedAppCard(
                    app = app,
                    blocked = true,
                    onCheckedChange = { enabled ->
                        store.setAppBlocked(app.packageName, app.label, enabled)
                        if (enabled && !overlayEnabled && !accessibilityEnabled) {
                            showOverlayPermissionPrompt = true
                        }
                        refreshData()
                    },
                )
            }
        }

        item {
            val frictionEnabled = remember(refreshKey) { store.isMindfulFrictionEnabled() }
            val defaultTab = remember(refreshKey) { store.mindfulFrictionDefaultTab() }
            val pauseSeconds = remember(refreshKey) { store.mindfulFrictionPauseDurationSeconds() }
            val targetBreaths = remember(refreshKey) { store.mindfulFrictionBreathsTarget() }

            SectionHeading(
                title = "Mindful Pre-Open Friction",
                subtitle = "Intervene with a 10s reflection pause or deep breathing before opening restricted apps.",
                action = "Test Preview",
                onAction = {
                    AppBlockOverlayManager.show(
                        context = context,
                        appLabel = "Sample App",
                        reason = "Mindful Friction Preview",
                        packageName = "com.google.android.youtube",
                    )
                },
            )

            MindfulFrictionCard(
                enabled = frictionEnabled,
                defaultTab = defaultTab,
                pauseSeconds = pauseSeconds,
                targetBreaths = targetBreaths,
                onToggleEnabled = { enabled ->
                    store.setMindfulFrictionEnabled(enabled)
                    refreshData()
                },
                onSelectTab = { tab ->
                    store.setMindfulFrictionDefaultTab(tab)
                    refreshData()
                },
                onSelectPauseSeconds = { secs ->
                    store.setMindfulFrictionPauseDurationSeconds(secs)
                    refreshData()
                },
                onSelectBreaths = { breaths ->
                    store.setMindfulFrictionBreathsTarget(breaths)
                    refreshData()
                },
                onPreview = {
                    AppBlockOverlayManager.show(
                        context = context,
                        appLabel = "Sample App",
                        reason = "Mindful Friction Preview",
                        packageName = "com.google.android.youtube",
                    )
                }
            )
        }

        item {
            SectionHeading(
                title = "Strict Unblock Delay",
                subtitle = "Require a 15–60s reflection cooldown before pausing or unblocking apps to break impulse loops.",
            )
            StrictUnblockSettingsCard(
                store = store,
                onSettingsChanged = { refreshData() }
            )
        }

        item {
            SectionHeading(
                title = "Notification Silencer & Digest",
                subtitle = "Batch and hold non-essential notifications during focus sessions, releasing them in a peaceful digest.",
            )
            NotificationBatchingSettingsCard(
                store = store,
                onSettingsChanged = { refreshData() }
            )
        }

        item {
            SectionHeading(
                title = "Zen Mode • Deep Exam Lockout",
                subtitle = "Strict full-device study lockdown. All distracting apps and feeds are locked out, allowing only emergency phone calls.",
            )
            ZenModeSettingsCard(
                store = store,
                refreshKey = refreshKey,
                onSettingsChanged = { refreshData() },
            )
        }

        item {
            val shortsBlockedCount = remember(refreshKey) { store.todayShortsBlockedCount() }
            val reelsScrolledToday = remember(refreshKey) { store.todayReelsScrolledCount() }
            SectionHeading(
                title = "Shorts & Reels Micro-Blocker",
                subtitle = when {
                    shortsBlockedCount > 0 && reelsScrolledToday > 0 ->
                        "⚡ $reelsScrolledToday reels watched • $shortsBlockedCount attempts blocked today!"
                    shortsBlockedCount > 0 ->
                        "⚡ $shortsBlockedCount scrolling attempts blocked today! Search & study videos remain accessible."
                    reelsScrolledToday > 0 ->
                        "⚡ $reelsScrolledToday reels watched today with mindful floating HUD."
                    else ->
                        "Block doom-scrolling video feeds or display live floating counter while keeping study videos open."
                },
                action = "Preview",
                onAction = { showShortsShowcase = true },
            )
        }
        items(shortFormFeatures, key = { it.packageName }) { feature ->
            val app = apps.find { it.packageName == feature.packageName }
            val isAppFullyBlocked = blockedApps.containsKey(feature.packageName)
            val isShortsBlocked = remember(refreshKey) {
                when (feature.packageName) {
                    "com.google.android.youtube" -> store.isYouTubeShortsBlocked()
                    "com.instagram.android" -> store.isInstagramReelsBlocked()
                    "com.snapchat.android" -> store.isSnapchatSpotlightBlocked()
                    "com.facebook.katana" -> store.isFacebookReelsBlocked()
                    else -> false
                }
            }
            FeatureBlockCard(
                feature = feature,
                app = app,
                isShortsBlocked = isShortsBlocked,
                isAppFullyBlocked = isAppFullyBlocked,
                accessibilityEnabled = accessibilityEnabled,
                focusOnly = focusOnlyBlocking,
                onToggle = { enabled ->
                    when (feature.packageName) {
                        "com.google.android.youtube" -> store.setYouTubeShortsBlocked(enabled)
                        "com.instagram.android" -> store.setInstagramReelsBlocked(enabled)
                        "com.snapchat.android" -> store.setSnapchatSpotlightBlocked(enabled)
                        "com.facebook.katana" -> store.setFacebookReelsBlocked(enabled)
                    }
                    val anyBlocked = store.isYouTubeShortsBlocked() || store.isInstagramReelsBlocked() ||
                            store.isSnapchatSpotlightBlocked() || store.isFacebookReelsBlocked()
                    store.setBlockShortsReelsEnabled(anyBlocked)
                    if (enabled && !accessibilityEnabled) {
                        showAccessibilitySetupDialog = true
                    }
                    refreshData()
                },
                onBlockEntireApp = {
                    val label = app?.label ?: feature.featureName
                    store.setAppBlocked(feature.packageName, label, true)
                    refreshData()
                },
            )
        }

        item {
            val floatingCounterEnabled = remember(refreshKey) { store.isFloatingScrollCounterEnabled() }
            val currentScrollLimit = remember(refreshKey) { store.getShortsScrollLimit() }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = AppsScreenPanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppsScreenDivider),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = AppsScreenGreenPanel,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("⏱️", fontSize = 18.sp)
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Live Floating Scroll Counter", fontWeight = FontWeight.SemiBold, color = Color.White)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2C2210),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFB74D).copy(alpha = 0.5f)),
                                ) {
                                    Text(
                                        "Dual Stat",
                                        color = Color(0xFFFFB74D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                "Draggable HUD over Reels & Shorts showing live swipe count + timer (e.g. 12 Reels • 3m)",
                                color = if (floatingCounterEnabled) AppsScreenGreen else AppsScreenMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        Switch(
                            checked = floatingCounterEnabled,
                            onCheckedChange = { enabled ->
                                store.setFloatingScrollCounterEnabled(enabled)
                                if (enabled && !overlayEnabled) {
                                    showOverlayPermissionPrompt = true
                                }
                                refreshData()
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AppsScreenGreen,
                            ),
                        )
                    }

                    if (floatingCounterEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 1.dp)
                                .background(AppsScreenDivider)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Scroll Session Limit:",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                listOf(
                                    0 to "No Limit",
                                    5 to "5 Reels",
                                    10 to "10 Reels",
                                    15 to "15 Reels",
                                    25 to "25 Reels",
                                ).forEach { (limit, label) ->
                                    val selected = currentScrollLimit == limit
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) AppsScreenGreenPanel else Color(0xFF222222),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selected) AppsScreenGreen else AppsScreenDivider
                                        ),
                                        onClick = {
                                            store.setShortsScrollLimit(limit)
                                            refreshData()
                                        },
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            color = if (selected) AppsScreenGreen else Color.LightGray,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeading(title = "Other Blocks", subtitle = "Block specific websites to stay focused.", action = null, onAction = null)
        }
        item {
            WebsiteCard(
                filterActive = filterActive,
                filterSupported = filterSupported,
                enabledRuleCount = domainRules.count { it.enabled },
                onManage = { showWebsites = true },
            )
        }
        if (!filterSupported) {
            item {
                Text(
                    "Website filtering is unavailable on Android 9 so StudyMode can preserve that version's Private DNS behavior.",
                    color = AppsScreenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        vpnError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        filterError?.let { error ->
            item { Text(error, color = MaterialTheme.colorScheme.error) }
        }

        item {
            SectionHeading(title = "Strict Mode Protections", subtitle = "Prevent workarounds and keep your focus intact.", action = null, onAction = null)
        }
        item {
            StrictModeCard(
                title = "Block selected apps only during focus",
                description = "Manual app restrictions apply only while a focus timer is actively running.",
                status = if (usageAccess || accessibilityEnabled) "Available" else "Setup required",
                checked = focusOnlyBlocking,
                onCheckedChange = {
                    store.setFocusOnlyBlockingEnabled(it)
                    refreshData()
                },
                setupLabel = if (usageAccess || accessibilityEnabled) null else "Enable app blocking",
                onSetup = {
                    if (!usageAccess) {
                        usageSettings.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } else {
                        showAccessibilitySetupDialog = true
                    }
                },
            )
        }
        item {
            UnsupportedProtectionCard(
                title = "Protect focus mode",
                description = "Android does not let ordinary apps prevent their own uninstall or disablement. Turn on the Accessibility blocker to use app restrictions; you can always turn it off in Android settings.",
            )
        }
        item {
            UnsupportedProtectionCard(
                title = "Split-screen protection",
                description = "A regular app cannot globally disable Android split-screen mode. StudyMode does not claim to enforce this restriction.",
            )
        }
        item {
            UnsupportedProtectionCard(
                title = "Floating-window protection",
                description = "Android does not provide StudyMode a supported way to control other apps' floating windows. This option is unavailable.",
            )
        }

        item {
            if (!notificationsEnabled) {
                PermissionCard(
                    title = "Limit notifications",
                    enabled = false,
                    description = "Optional. Enables warning and daily-limit notifications when the app-blocking service detects the threshold. App restrictions can still work without notifications.",
                    buttonLabel = "Enable notifications",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    },
                )
            }
        }
        item {
            PermissionCard(
                title = "Usage access",
                enabled = usageAccess,
                description = if (usageAccess) {
                    "Shows today's app usage and enables background focus enforcement and daily limits. Data stays on-device."
                } else {
                    "Required to measure app usage and block distracting apps. Data stays completely on-device."
                },
                buttonLabel = if (usageAccess) "Manage Usage Access" else "Open Usage Access settings",
                onClick = { usageSettings.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
        }
        item {
            PermissionCard(
                title = "Display over other apps",
                enabled = overlayEnabled,
                description = if (overlayEnabled) {
                    "Enables instant blocking screen over restricted apps. 100% compatible with BHIM, Google Pay, and banking apps."
                } else {
                    "Recommended for UPI-safe app blocking without triggering BHIM or banking accessibility alerts."
                },
                buttonLabel = if (overlayEnabled) "Manage Overlay settings" else "Enable Overlay permission",
                onClick = {
                    openOverlaySettings(context)
                },
            )
        }
        item {
            PermissionCard(
                title = "Accessibility blocker (Optional)",
                enabled = accessibilityEnabled,
                description = if (accessibilityEnabled) {
                    "Active. Window content inspection is disabled and banking apps are excluded. If BHIM or bank apps show security alerts, keep this OFF."
                } else {
                    "Optional. Standard blocking works safely via Usage Access & Overlay without triggering UPI security warnings."
                },
                buttonLabel = if (accessibilityEnabled) "Manage Accessibility settings" else "Setup Accessibility (Optional)",
                onClick = {
                    if (accessibilityEnabled) {
                        openAccessibilitySettings(context)
                    } else {
                        showAccessibilitySetupDialog = true
                    }
                },
            )
        }

        item {
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
                    Text("Quick control", fontWeight = FontWeight.SemiBold)
                    if (pausedUntil > System.currentTimeMillis()) {
                        Text(
                            "Selected app blocks are paused until ${java.text.DateFormat.getTimeInstance().format(Date(pausedUntil))}.",
                            color = AppsScreenMuted,
                        )
                        TextButton(
                            onClick = {
                                store.resumeBlockingNow()
                                refreshData()
                            },
                        ) { Text("Resume blocking", color = AppsScreenGreen) }
                    } else {
                        Text("Pause selected app blocks for five minutes.", color = AppsScreenMuted)
                        OutlinedButton(
                            onClick = {
                                if (store.isStrictUnblockDelayEnabled()) {
                                    showStrictDelayDialog = true
                                    return@OutlinedButton
                                }
                                store.pauseBlockingForFiveMinutes()
                                refreshData()
                            },
                        ) { Text("Pause for 5 minutes") }
                    }
                }
            }
        }

        item {
            SectionHeading(
                title = "Today's usage",
                action = if (usageAccess) "Refresh" else null,
                onAction = if (usageAccess) {
                    { refreshData() }
                } else {
                    null
                },
            )
        }
        if (!usageAccess) {
            item {
                Text("Grant Usage Access above to view today's locally measured app totals.", color = AppsScreenMuted)
            }
        } else if (usage.isEmpty()) {
            item {
                Text("No app usage totals are available yet today.", color = AppsScreenMuted)
            }
        } else {
            items(
                usage.entries.sortedByDescending { it.value }.take(20),
                key = { "usage:${it.key}" },
            ) { entry ->
                val app = apps.find { it.packageName == entry.key }
                UsageCard(
                    label = app?.label ?: entry.key,
                    duration = formatUsageDuration(entry.value),
                )
            }
        }
        item { Spacer(Modifier.size(16.dp)) }
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
                                color = if (accessibilityEnabled) AppsScreenGreenPanel else Color(0xFF382613),
                                border = androidx.compose.foundation.BorderStroke(
                                    0.5.dp,
                                    if (accessibilityEnabled) AppsScreenGreen.copy(alpha = 0.4f) else Color(0xFFFFB74D).copy(alpha = 0.4f),
                                ),
                            ) {
                                Text(
                                    if (accessibilityEnabled) "Micro-block" else "Needs Accessibility",
                                    color = if (accessibilityEnabled) AppsScreenGreen else Color(0xFFFFB74D),
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
                            isShortsBlocked && !accessibilityEnabled -> "Needs Accessibility to filter in-app tabs • Or block entire app to keep BHIM safe"
                            isShortsBlocked -> "Doom-scrolling video feed blocked • Study videos & chats open"
                            app == null -> "Install ${feature.featureName} to block feeds"
                            else -> "Allowed (No distraction blocking active)"
                        },
                        color = when {
                            isAppFullyBlocked -> Color(0xFFFFB74D)
                            isShortsBlocked && !accessibilityEnabled -> Color(0xFFFFB74D)
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

            if (isShortsBlocked && !isAppFullyBlocked && !accessibilityEnabled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF132215),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF28542D)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Prefer 100% UPI-safe blocking?",
                            color = Color(0xFF86EFAC),
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.OutlinedButton(
                            onClick = onBlockEntireApp,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF4ADE80)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF28542D)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Block Full App", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
