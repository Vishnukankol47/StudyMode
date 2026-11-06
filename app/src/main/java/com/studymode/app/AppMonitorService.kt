package com.studymode.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground monitoring service that enforces app blocks and daily usage limits,
 * while maintaining a dynamic screen time awareness notification.
 *
 * Battery Optimizations:
 * 1. BroadcastReceiver for ACTION_SCREEN_OFF: Coroutine loop completely pauses when the screen
 *    is turned off, allowing Android to enter deep sleep / Doze Mode with 0% standby drain.
 * 2. Adaptive Event-Driven Interval: Relaxes polling loop to 1500ms when the Accessibility Service
 *    is running, since Accessibility pushes 5ms window events instantly.
 * 3. Throttled Caching: Usage queries are cached for 25s to avoid excessive IPC Binder calls.
 * 4. Silent In-Place Notification: Updates live screen time once a minute silently using setOnlyAlertOnce.
 */
class AppMonitorService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)
    private var monitorJob: Job? = null

    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis = 0L

    @Volatile private var isScreenOn = true
    @Volatile private var currentTrackedPackage: String? = null
    @Volatile private var currentAppSessionStartMillis = 0L
    @Volatile private var lastNotificationUpdateMillis = 0L
    @Volatile private var lastNotificationTitle: String? = null
    @Volatile private var lastNotificationText: String? = null
    @Volatile private var cachedUsageMap: Map<String, Long> = emptyMap()
    @Volatile private var lastUsageMapFetchMillis = 0L

    // Zero-Accessibility Safe Mode (BrainPal Audio-Playback Technique)
    private var audioPlaybackCallback: AudioManager.AudioPlaybackCallback? = null
    private var isAudioCallbackRegistered = false
    private var lastAudioReelTickMillis = 0L
    private var lastPlayerInterfaceId: String? = null
    private val piidRegex = Regex("""piid:(\d+)""")
    private val muteRegex = Regex("""mutedState:([^\s]+)""")

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    onScreenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    onScreenOn()
                    try {
                        LocalStudyStore(context ?: this@AppMonitorService).recordPhonePickup()
                    } catch (_: Exception) {}
                }
                Intent.ACTION_USER_PRESENT -> {
                    onScreenOn()
                    try {
                        LocalStudyStore(context ?: this@AppMonitorService).recordPhoneUnlock()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel(this)

        // Register dynamic screen on/off receiver for zero-drain deep sleep
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        val store = LocalStudyStore(this)
        val initialDistracting = formatUsageDuration(getDistractingUsageMillis(store))
        val initialTotal = formatUsageDuration(getTotalUsageMillis())
        val initialNotification = buildTrackerNotification(
            title = "Distracting Usage: $initialDistracting",
            contentText = "Total Usage: $initialTotal",
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            android.util.Log.e("StudyMode", "AppMonitorService startForeground failed", e)
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (powerManager?.isInteractive != false) {
            onScreenOn()
        } else {
            isScreenOn = false
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}

        unregisterFallbackAudioCallback()
        FloatingScrollCounterOverlayManager.hide(this, resetSession = true)

        serviceJob.cancel()
        monitorJob?.cancel()
        monitorJob = null
        isRunning = false
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    private fun onScreenOff() {
        isScreenOn = false
        monitorJob?.cancel()
        monitorJob = null
        unregisterFallbackAudioCallback()
        FloatingScrollCounterOverlayManager.pause(this, isScreenOff = true)
    }

    private fun onScreenOn() {
        isScreenOn = true
        currentTrackedPackage = null
        lastPlayerInterfaceId = null
        startMonitoring()
        updateUsageNotification(null, LocalStudyStore(this))
    }

    fun handleForegroundChanged(newPackage: String?) {
        val store = LocalStudyStore(this)
        val now = System.currentTimeMillis()

        if (newPackage == null || isLauncherOrSystem(newPackage) || newPackage == packageName || isBankingOrUpiPackage(newPackage)) {
            if (currentTrackedPackage != null) {
                currentTrackedPackage = null
                currentAppSessionStartMillis = 0L
                updateUsageNotification(null, store)
            }
            // If leaving short-video app, pause floating badge
            FloatingScrollCounterOverlayManager.pause(this, isScreenOff = false)
            lastPlayerInterfaceId = null
            return
        }

        val platformName = getShortVideoPlatformName(newPackage)
        val isShortVideoHost = platformName != null
        val isPackageChanged = currentTrackedPackage != newPackage

        if (isPackageChanged) {
            currentTrackedPackage = newPackage
            currentAppSessionStartMillis = now
            cachedUsageMap = todayAccurateUsageMap(this)
            lastUsageMapFetchMillis = now
            updateUsageNotification(newPackage, store)
        }

        // Reel & Shorts detection via AudioPlaybackCallback
        if (isShortVideoHost) {
            if (!isAudioCallbackRegistered) {
                registerFallbackAudioCallback()
            }
        } else if (isPackageChanged) {
            FloatingScrollCounterOverlayManager.pause(this, isScreenOff = false)
            lastPlayerInterfaceId = null
        }
    }

    private fun getShortVideoPlatformName(pkg: String?): String? = when (pkg) {
        "com.google.android.youtube", "app.revanced.android.youtube", "app.rvx.android.youtube" -> "YouTube Shorts"
        "com.instagram.android", "com.instagram.lite" -> "Instagram Reels"
        "com.snapchat.android", "com.snap.android" -> "Snapchat Spotlight"
        "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> "TikTok"
        "com.facebook.katana", "com.facebook.lite" -> "Facebook Reels"
        else -> null
    }

    private fun registerFallbackAudioCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || isAudioCallbackRegistered) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    onFallbackAudioPlaybackChanged(configs)
                }
            }
            audioManager.registerAudioPlaybackCallback(callback, Handler(Looper.getMainLooper()))
            audioPlaybackCallback = callback
            isAudioCallbackRegistered = true
            android.util.Log.d("StudyMode", "Zero-Accessibility Fallback: AudioPlaybackCallback registered")
        } catch (e: Exception) {
            android.util.Log.w("StudyMode", "Failed to register AudioPlaybackCallback", e)
        }
    }

    private fun unregisterFallbackAudioCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isAudioCallbackRegistered) return
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            audioPlaybackCallback?.let { audioManager.unregisterAudioPlaybackCallback(it) }
        } catch (_: Exception) {}
        audioPlaybackCallback = null
        isAudioCallbackRegistered = false
        lastPlayerInterfaceId = null
    }

    private fun onFallbackAudioPlaybackChanged(configs: List<AudioPlaybackConfiguration>?) {
        if (!isScreenOn) return
        val currentPkg = currentTrackedPackage ?: return
        val platformName = getShortVideoPlatformName(currentPkg) ?: return
        if (configs.isNullOrEmpty()) return

        val now = System.currentTimeMillis()
        val store = LocalStudyStore(this)

        for (config in configs) {
            val usage = try { config.audioAttributes?.usage } catch (_: Exception) { null }
            if (usage == AudioAttributes.USAGE_MEDIA) {
                val raw = config.toString()
                val piidMatch = piidRegex.find(raw)?.groupValues?.getOrNull(1)
                val muteMatch = muteRegex.find(raw)?.groupValues?.getOrNull(1)

                val isUnmuted = muteMatch == null || muteMatch.equals("none", ignoreCase = true)
                if (piidMatch != null && isUnmuted && piidMatch != lastPlayerInterfaceId) {
                    if (now - lastAudioReelTickMillis >= 1500L) {
                        lastAudioReelTickMillis = now
                        val isSwipe = lastPlayerInterfaceId != null
                        lastPlayerInterfaceId = piidMatch

                        android.util.Log.d("StudyMode", "Zero-Accessibility Fallback: Reel active in $platformName (piid: $piidMatch, isSwipe=$isSwipe)")

                        if (store.isFloatingScrollCounterEnabled() && Settings.canDrawOverlays(this)) {
                            FloatingScrollCounterOverlayManager.updateCounter(
                                context = this,
                                platformName = platformName,
                                isScrollEvent = isSwipe,
                                store = store,
                                onLimitReached = {
                                    ShortsBlockOverlayManager.show(this, "$platformName (Limit Reached)")
                                }
                            )
                        }

                        // Check if shorts micro-blocking is enabled for this platform
                        val isMicroBlocked = store.isBlockShortsReelsEnabled() && when (currentPkg) {
                            "com.google.android.youtube", "app.revanced.android.youtube", "app.rvx.android.youtube" -> store.isYouTubeShortsBlocked()
                            "com.instagram.android", "com.instagram.lite" -> store.isInstagramReelsBlocked()
                            "com.snapchat.android", "com.snap.android" -> store.isSnapchatSpotlightBlocked()
                            "com.facebook.katana", "com.facebook.lite" -> store.isFacebookReelsBlocked()
                            else -> true
                        }

                        if (isMicroBlocked) {
                            ShortsBlockOverlayManager.show(this, platformName)
                        }
                        break
                    }
                }
            }
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        isRunning = true

        val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val store = LocalStudyStore(this)

        monitorJob = serviceScope.launch {
            while (isActive && isScreenOn) {
                // If screen was turned off, pause the loop completely
                if (powerManager?.isInteractive == false) {
                    onScreenOff()
                    break
                }

                if (usageManager != null && hasUsageAccess(this@AppMonitorService)) {
                    try {
                        checkForegroundApp(usageManager, store)
                    } catch (e: Exception) {
                        android.util.Log.w("StudyMode", "Error checking foreground app", e)
                    }
                }

                val now = System.currentTimeMillis()
                if (now - lastNotificationUpdateMillis >= 50_000L) {
                    updateUsageNotification(currentTrackedPackage, store)
                }

                delay(400L)
            }
        }
    }

    private fun checkForegroundApp(usageManager: UsageStatsManager, store: LocalStudyStore) {
        val now = System.currentTimeMillis()
        val events = try {
            usageManager.queryEvents(now - 15_000L, now)
        } catch (_: Exception) {
            null
        }

        var foregroundPackage: String? = null
        if (events != null) {
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                @Suppress("DEPRECATION")
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                    event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
                ) {
                    foregroundPackage = event.packageName
                }
            }
        }

        if (foregroundPackage == null) {
            val stats = try {
                usageManager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - 60_000L, now)
            } catch (_: Exception) {
                null
            }
            foregroundPackage = stats?.maxByOrNull { it.lastTimeUsed }?.packageName
        }

        val packageName = foregroundPackage ?: return

        // 1. If StudyMode itself
        if (packageName == this.packageName) {
            handleForegroundChanged(null)
            return
        }

        // 2. System UI, keyboard/IME, and transient system processes - ignore without dismissing overlays
        if (isSystemOrTransientPackage(packageName)) {
            return
        }

        // 3. Zen Mode (Deep Exam Lockout) Interception
        // Lock out entire device except phone calls (user can call and answer anyone)
        if (store.isZenModeActive(now)) {
            val defaultDialer = try {
                val telecom = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                telecom?.defaultDialerPackage
            } catch (_: Exception) { null }
            val isDialer = packageName == defaultDialer
                || packageName == "com.samsung.android.dialer"
                || packageName == "com.google.android.dialer"
                || packageName == "com.android.phone"
                || packageName.contains("dialer", ignoreCase = true)
                || packageName.contains("telecom", ignoreCase = true)
                || packageName.contains("incallui", ignoreCase = true)
                || packageName.contains("phone", ignoreCase = true)
            if (!isDialer) {
                ZenLockOverlayActivity.start(this)
                return
            } else {
                return // allow phone call
            }
        }

        // 4. Never inspect or intercept banking/UPI apps; dismiss overlay if user switched to banking
        if (isBankingOrUpiPackage(packageName)) {
            if (AppBlockOverlayManager.isShowing()) {
                AppBlockOverlayManager.dismiss(this)
            }
            handleForegroundChanged(null)
            return
        }

        // 5. If launcher (Home screen)
        if (isLauncherPackage(this, packageName)) {
            val activeTimer = store.activeTimer()
            val isTimerRunning = activeTimer != null && activeTimer.running && activeTimer.remainingAt(now) > 0
            val shouldBlockLauncher = (store.isLauncherBlocked() || store.isUltraFocus()) && isTimerRunning

            if (shouldBlockLauncher) {
                // Home screen is blocked during Ultra Focus!
                val focusIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("OPEN_ACTIVE_FOCUS", true)
                    putExtra("LAUNCHER_BLOCKED_NOTICE", true)
                }
                startActivity(focusIntent)
                return
            }

            if (AppBlockOverlayManager.isShowing()) {
                AppBlockOverlayManager.dismiss(this)
            }
            handleForegroundChanged(null)
            return
        }

        // Update ongoing tracker notification for this active app
        handleForegroundChanged(packageName)

        // Check if the current app is subject to blocking
        val focusTimer = store.activeTimer()
        val manuallyBlocked = shouldBlockManuallyBlockedApp(
            manuallyBlocked = store.isAppBlocked(packageName),
            focusOnlyBlockingEnabled = store.focusOnlyBlockingEnabled(),
            focusTimer = focusTimer,
            nowMillis = now,
        )
        val scheduleBlocked = packageName in store.activeScheduleBlockedPackages(now)
        val sleepBlocked = packageName in store.sleepBlockedPackages(now)
        val dailyLimit = store.appDailyLimit(packageName)
        val usageMillis = if (dailyLimit != null) todayAppUsageMillis(this, packageName) else 0L

        if (dailyLimit != null && usageMillis > 0L) {
            if (isAppLimitWarningDue(usageMillis, dailyLimit.minutes, dailyLimit.warningPercent)) {
                AppLimitNotifications.postOncePerDay(
                    this,
                    packageName,
                    "Approaching daily app limit",
                    "${dailyLimit.label} has reached ${dailyLimit.warningPercent}% of today's limit.",
                )
            }
            if (usageMillis >= dailyLimit.minutes * 60_000L) {
                AppLimitNotifications.postOncePerDay(
                    this,
                    packageName,
                    "Daily app limit reached",
                    "${dailyLimit.label} has reached its daily limit.",
                )
            }
        }

        val limitReached = dailyLimit?.let {
            isAppDailyLimitReached(usageMillis, it.minutes, it.blockingEnabled)
        } == true

        if (store.blockingPausedUntil() > now ||
            (!manuallyBlocked && !scheduleBlocked && !sleepBlocked && !limitReached)
        ) {
            if (AppBlockOverlayManager.isShowing()) {
                val currentBlocked = AppBlockOverlayManager.currentBlockedPackage()
                val isSwitchedToOtherApp = packageName != currentBlocked && !isSystemOrTransientPackage(packageName)
                val isPaused = store.blockingPausedUntil() > now
                val isUnblocked = packageName == currentBlocked && !manuallyBlocked && !scheduleBlocked && !sleepBlocked && !limitReached

                if (isPaused || isSwitchedToOtherApp || isUnblocked) {
                    AppBlockOverlayManager.dismiss(this)
                }
            }
            return
        }

        // If overlay is already active for this package, do nothing
        if (AppBlockOverlayManager.isShowing() && AppBlockOverlayManager.currentBlockedPackage() == packageName) {
            return
        }

        // Debounce block triggers for the same package within 1.2 seconds if already showing
        if (lastBlockedPackage == packageName && AppBlockOverlayManager.isShowing() && now - lastBlockedAtMillis < 1200L) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockedAtMillis = now

        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }

        val isFocusActive = focusTimer?.let { it.running && it.remainingAt(now) > 0 } == true
        val reason = when {
            limitReached -> "Daily limit of ${dailyLimit?.minutes ?: 0}m reached"
            sleepBlocked -> "Sleep Mode is currently active"
            manuallyBlocked && isFocusActive -> "Blocked during Focus Session"
            manuallyBlocked -> "Selected as a blocked app in StudyMode"
            scheduleBlocked -> "Blocked by your study schedule"
            else -> "Blocked by StudyMode"
        }

        android.util.Log.i("StudyMode", "AppMonitorService: Blocking $packageName ($appLabel) - reason: $reason")

        AppBlockOverlayManager.show(this, appLabel, reason, packageName)
    }

    private fun updateUsageNotification(currentPackage: String?, store: LocalStudyStore) {
        val now = System.currentTimeMillis()
        val totalUsageMillis = getTotalUsageMillis()
        val totalFormatted = formatUsageDuration(totalUsageMillis)

        val isNormalTime = currentPackage == null ||
            isLauncherOrSystem(currentPackage) ||
            currentPackage == packageName ||
            isBankingOrUpiPackage(currentPackage)

        val (title, text) = if (isNormalTime || currentPackage == null) {
            // Image 3: "Distracting Usage: 3h 12m" / "Total Usage: 3h 48m"
            val distractingMillis = getDistractingUsageMillis(store)
            val distractingFormatted = formatUsageDuration(distractingMillis)
            Pair("Distracting Usage: $distractingFormatted", "Total Usage: $totalFormatted")
        } else {
            // Image 2: "YouTube: 1m" / "Total Usage: 3h 48m"
            val targetPkg = currentPackage
            val appLabel = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(targetPkg, 0)).toString()
            } catch (_: Exception) {
                targetPkg
            }
            val appUsage = getAppUsageMillis(targetPkg)
            val appFormatted = formatUsageDuration(appUsage.coerceAtLeast(60_000L))
            Pair("$appLabel: $appFormatted", "Total Usage: $totalFormatted")
        }

        if (title != lastNotificationTitle || text != lastNotificationText || now - lastNotificationUpdateMillis >= 45_000L) {
            lastNotificationTitle = title
            lastNotificationText = text
            lastNotificationUpdateMillis = now
            val notification = buildTrackerNotification(title, text)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildTrackerNotification(
        title: String,
        contentText: String,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_tracker_notification)
        } catch (_: Exception) {
            null
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .apply {
                if (largeIconBitmap != null) {
                    setLargeIcon(largeIconBitmap)
                }
            }
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .build()
    }

    private fun getCachedOrFreshUsageMap(): Map<String, Long> {
        val now = System.currentTimeMillis()
        if (cachedUsageMap.isEmpty() || now - lastUsageMapFetchMillis > 25_000L) {
            cachedUsageMap = todayAccurateUsageMap(this)
            lastUsageMapFetchMillis = now
        }
        return cachedUsageMap
    }

    private fun getAppUsageMillis(pkg: String): Long {
        val map = getCachedOrFreshUsageMap()
        var usage = map[pkg] ?: 0L
        if (pkg == currentTrackedPackage && currentAppSessionStartMillis > 0L) {
            val session = System.currentTimeMillis() - currentAppSessionStartMillis
            if (session > 0) {
                usage += session
            }
        }
        return usage
    }

    private fun getTotalUsageMillis(): Long {
        val map = getCachedOrFreshUsageMap()
        var total = map.values.sum()
        if (currentTrackedPackage != null && currentAppSessionStartMillis > 0L) {
            val session = System.currentTimeMillis() - currentAppSessionStartMillis
            if (session > 0) {
                total += session
            }
        }
        return total
    }

    private fun getDistractingUsageMillis(store: LocalStudyStore): Long {
        val map = getCachedOrFreshUsageMap()
        val blockedPackages = store.blockedApps().filter { it.blocked }.map { it.packageName }.toSet()
        var distractingTotal = 0L

        for ((pkg, millis) in map) {
            if (millis <= 0L) continue
            if (pkg in blockedPackages || isDistractingPackage(pkg)) {
                distractingTotal += millis
            }
        }

        if (currentTrackedPackage != null && currentAppSessionStartMillis > 0L) {
            val currentPkg = currentTrackedPackage!!
            if (currentPkg in blockedPackages || isDistractingPackage(currentPkg)) {
                val session = System.currentTimeMillis() - currentAppSessionStartMillis
                if (session > 0) {
                    distractingTotal += session
                }
            }
        }
        return distractingTotal
    }

    private fun isDistractingPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (lower.contains("youtube") ||
            lower.contains("instagram") ||
            lower.contains("facebook") ||
            lower.contains("snapchat") ||
            lower.contains("tiktok") ||
            lower.contains("twitter") ||
            lower.contains("reddit") ||
            lower.contains("netflix") ||
            lower.contains("primevideo") ||
            lower.contains("hotstar") ||
            lower.contains("twitch") ||
            lower.contains("chess") ||
            lower.contains("game")
        ) {
            return true
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    appInfo.category == ApplicationInfo.CATEGORY_SOCIAL ||
                    appInfo.category == ApplicationInfo.CATEGORY_VIDEO
                ) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun isLauncherOrSystem(pkg: String): Boolean {
        if (pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg.contains("launcher", ignoreCase = true) ||
            pkg == "com.google.android.apps.nexuslauncher" ||
            pkg.startsWith("com.sec.android.app.launcher") ||
            pkg.startsWith("com.miui.home") ||
            pkg.startsWith("com.oppo.launcher") ||
            pkg.startsWith("com.huawei.android.launcher") ||
            pkg.startsWith("com.oneplus.launcher")
        ) return true
        return false
    }

    private fun formatUsageDuration(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "0m"
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "study_monitor_service"
        private const val ACTION_STOP = "com.studymode.app.action.STOP_MONITOR"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: AppMonitorService? = null

        fun onAppForegroundChanged(context: Context, packageName: String?) {
            instance?.handleForegroundChanged(packageName)
        }

        fun start(context: Context) {
            try {
                createNotificationChannel(context)
                val intent = Intent(context, AppMonitorService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(context, intent)
                    } else {
                        context.startService(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("StudyMode", "Foreground start restricted, trying startService", e)
                    try {
                        context.startService(intent)
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.w("StudyMode", "Failed to start AppMonitorService", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.startService(
                    Intent(context, AppMonitorService::class.java).setAction(ACTION_STOP),
                )
            } catch (e: Exception) {
                android.util.Log.w("StudyMode", "Failed to stop AppMonitorService", e)
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (manager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Screen Time & Usage Tracker",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "Displays live app screen time and daily distracting usage"
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }
}
