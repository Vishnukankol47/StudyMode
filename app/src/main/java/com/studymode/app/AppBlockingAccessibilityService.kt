package com.studymode.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

class AppBlockingAccessibilityService : AccessibilityService() {
    private var store: LocalStudyStore? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMillis = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeInstance = this
        store = LocalStudyStore(this)
        updateTargetPackages()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        FloatingScrollCounterOverlayManager.pause(this@AppBlockingAccessibilityService, isScreenOff = true)
                    }
                    Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                        mainHandler.postDelayed({
                            try {
                                val root = rootInActiveWindow
                                val pkg = root?.packageName?.toString()
                                if (pkg != null && (pkg == "com.instagram.android" ||
                                        pkg == "com.google.android.youtube" ||
                                        pkg == "com.snapchat.android" ||
                                        pkg == "com.facebook.katana" ||
                                        pkg.contains("youtube") ||
                                        pkg.contains("instagram"))
                                ) {
                                    val s = store ?: LocalStudyStore(this@AppBlockingAccessibilityService)
                                    ShortsReelsDetector.checkAndIntercept(this@AppBlockingAccessibilityService, null, s)
                                }
                            } catch (_: Exception) {}
                        }, 350L)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenReceiver = receiver
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        try {
            screenReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        screenReceiver = null
        FloatingScrollCounterOverlayManager.hide(this, resetSession = true)
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Strict Banking & UPI isolation: Zero inspection, zero interaction.
        // Return immediately without inspecting window contents or active views.
        if (isBankingOrUpiPackage(packageName)) {
            if (AppBlockOverlayManager.isShowing()) {
                AppBlockOverlayManager.dismiss(this)
            }
            FloatingScrollCounterOverlayManager.hide(this)
            AppMonitorService.onAppForegroundChanged(this, null)
            return
        }

        // 2. If StudyMode itself, never dismiss overlay or block ourselves
        if (packageName == this.packageName) {
            AppMonitorService.onAppForegroundChanged(this, null)
            return
        }

        val localStore = store ?: LocalStudyStore(this).also { store = it }
        val now = System.currentTimeMillis()
        val focusTimer = localStore.activeTimer()

        val isEntireAppBlocked = localStore.isAppBlocked(packageName)
        val isFocusActive = focusTimer?.let { it.running && it.remainingAt(now) > 0 } == true
        val isAppBlockedNow = shouldBlockManuallyBlockedApp(
            manuallyBlocked = isEntireAppBlocked,
            focusOnlyBlockingEnabled = localStore.focusOnlyBlockingEnabled(),
            focusTimer = focusTimer,
            nowMillis = now,
        )

        // 3. Shorts & Reels Micro-Blocker (YouTube Shorts, Instagram Reels, Snapchat, Facebook)
        // Only run micro-blocker if the whole app is not already marked as blocked
        if (!isAppBlockedNow && ShortsReelsDetector.checkAndIntercept(this, event, localStore)) {
            return
        }

        // For scroll / content change / click events, exit early so we only do full app evaluation on window switches
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        // 3.5 Zen Mode (Deep Exam Lockout) Interception
        if (localStore.isZenModeActive(now)) {
            val defaultDialer = try {
                val telecom = getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                telecom?.defaultDialerPackage
            } catch (_: Exception) { null }
            // Allow: our own package, any dialer / phone / telecom / incallui app
            val isDialer = packageName == defaultDialer
                || packageName == "com.samsung.android.dialer"
                || packageName == "com.google.android.dialer"
                || packageName == "com.android.phone"
                || packageName.contains("dialer", ignoreCase = true)
                || packageName.contains("telecom", ignoreCase = true)
                || packageName.contains("incallui", ignoreCase = true)
                || packageName.contains("phone", ignoreCase = true)
            // Allow: launcher is handled separately below (its own redirect to ZenLockOverlayActivity)
            val isLauncher = isLauncherPackage(this, packageName)
            if (!isLauncher && !isDialer) {
                ZenLockOverlayActivity.start(this)
                return
            }
        }

        // 4. If launcher (Home screen)
        if (isLauncherPackage(this, packageName)) {
            val activeTimer = localStore.activeTimer()
            val isTimerRunning = activeTimer != null && activeTimer.running && activeTimer.remainingAt(now) > 0
            val shouldBlockLauncher = ((localStore.isLauncherBlocked() || localStore.isUltraFocus()) && isTimerRunning)
                || localStore.isZenModeActive(now)

            if (shouldBlockLauncher) {
                if (localStore.isZenModeActive(now)) {
                    // Zen Mode: snap back to the zen lock screen
                    ZenLockOverlayActivity.start(this)
                } else {
                    // Ultra Focus: snap back to active focus screen in MainActivity
                    val focusIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("OPEN_ACTIVE_FOCUS", true)
                        putExtra("LAUNCHER_BLOCKED_NOTICE", true)
                    }
                    startActivity(focusIntent)
                }
                return
            }

            // Normal behavior: user left the blocked app, dismiss overlay
            if (AppBlockOverlayManager.isShowing()) {
                AppBlockOverlayManager.dismiss(this)
            }
            FloatingScrollCounterOverlayManager.hide(this)
            AppMonitorService.onAppForegroundChanged(this, null)
            return
        }

        // 5. System UI, keyboard/IME, and transient system processes - NEVER dismiss overlay
        if (isSystemOrTransientPackage(packageName)) {
            return
        }

        if (packageName != "com.google.android.youtube" &&
            packageName != "app.revanced.android.youtube" &&
            packageName != "app.rvx.android.youtube" &&
            packageName != "com.instagram.android" &&
            packageName != "com.instagram.lite" &&
            packageName != "com.snapchat.android" &&
            packageName != "com.facebook.katana" &&
            packageName != "com.facebook.lite"
        ) {
            FloatingScrollCounterOverlayManager.hide(this)
        }

        // Notify AppMonitorService immediately about foreground app change
        AppMonitorService.onAppForegroundChanged(this, packageName)

        val manuallyBlocked = isAppBlockedNow
        val scheduleBlocked = packageName in localStore.activeScheduleBlockedPackages(now)
        val sleepBlocked = packageName in localStore.sleepBlockedPackages(now)
        val dailyLimit = localStore.appDailyLimit(packageName)
        val usageMillis = if (dailyLimit != null && hasUsageAccess(this)) {
            todayAppUsageMillis(this, packageName)
        } else {
            0L
        }
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

        if (localStore.blockingPausedUntil() > now ||
            (!manuallyBlocked && !scheduleBlocked && !sleepBlocked && !limitReached)
        ) {
            if (AppBlockOverlayManager.isShowing()) {
                val currentBlocked = AppBlockOverlayManager.currentBlockedPackage()
                val isSwitchedToOtherApp = packageName != currentBlocked && !isSystemOrTransientPackage(packageName)
                val isPaused = localStore.blockingPausedUntil() > now
                val isUnblocked = packageName == currentBlocked && !manuallyBlocked && !scheduleBlocked && !sleepBlocked && !limitReached

                if (isPaused || isSwitchedToOtherApp || isUnblocked) {
                    AppBlockOverlayManager.dismiss(this)
                }
            }
            return
        }

        // If the overlay is already showing for this exact package, nothing to do
        if (AppBlockOverlayManager.isShowing() && AppBlockOverlayManager.currentBlockedPackage() == packageName) {
            return
        }

        if (lastBlockedPackage == packageName && AppBlockOverlayManager.isShowing() && now - lastBlockedAtMillis < BLOCK_ACTION_INTERVAL_MILLIS) return

        lastBlockedPackage = packageName
        lastBlockedAtMillis = now

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (_: Exception) {
            packageName
        }

        val reason = when {
            limitReached -> "Daily limit of ${dailyLimit?.minutes ?: 0}m reached"
            sleepBlocked -> "Sleep Mode is currently active"
            manuallyBlocked && isFocusActive -> "Blocked during Focus Session"
            manuallyBlocked -> "Selected as a blocked app in StudyMode"
            scheduleBlocked -> "Blocked by your study schedule"
            else -> "Blocked by StudyMode"
        }

        android.util.Log.i("StudyMode", "Blocking app $packageName ($appLabel) - reason: $reason")

        try {
            localStore.recordAppInterception(packageName)
        } catch (_: Exception) {}

        // Display the blocked screen via Bottom Sheet Overlay
        AppBlockOverlayManager.show(this, appLabel, reason, packageName)
    }

    override fun onInterrupt() = Unit

    fun updateTargetPackages() {
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            val localStore = store ?: LocalStudyStore(this).also { store = it }

            // Strictly register ONLY:
            // 1. The 6 core entertainment platforms (YouTube, Instagram, Snapchat, TikTok, Facebook) + StudyMode
            val targetPackages = mutableSetOf(
                // 1. YouTube
                "com.google.android.youtube",
                "app.revanced.android.youtube",
                "app.rvx.android.youtube",
                // 2. Instagram
                "com.instagram.android",
                "com.instagram.lite",
                // 3. Snapchat
                "com.snapchat.android",
                "com.snap.android",
                // 4. TikTok
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                // 5. Facebook
                "com.facebook.katana",
                "com.facebook.lite",
                // 6. StudyMode itself
                packageName
            )

            // 2. Plus ONLY the specific distracting apps user actively toggled ON in their blocklist
            val userBlocked = try {
                localStore.blockedApps().filter { it.blocked }.map { it.packageName }
            } catch (_: Exception) { emptyList() }

            val scheduledBlocked = try {
                localStore.activeScheduleBlockedPackages()
            } catch (_: Exception) { emptySet() }

            val dailyLimited = try {
                localStore.appDailyLimits().map { it.packageName }
            } catch (_: Exception) { emptyList() }

            (userBlocked + scheduledBlocked + dailyLimited).forEach { pkg ->
                if (!isBankingOrUpiPackage(pkg)) {
                    targetPackages.add(pkg)
                }
            }

            // Android OS strictly limits accessibility event dispatch to only these packages.
            // Banking & UPI apps are NEVER observed or dispatched, preventing false positive security warnings.
            info.packageNames = targetPackages.toTypedArray()

            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_SCROLLED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.notificationTimeout = 50
            serviceInfo = info
            android.util.Log.d("StudyMode", "Accessibility serviceInfo set: ${targetPackages.size} packages (strictly entertainment + active user blocklist, zero banking/unselected apps)")
        } catch (e: Exception) {
            android.util.Log.w("StudyMode", "Failed to update packageNames on accessibility service", e)
        }
    }

    companion object {
        private const val BLOCK_ACTION_INTERVAL_MILLIS = 300L
        @Volatile var activeInstance: AppBlockingAccessibilityService? = null

        fun updateConfiguration(context: Context) {
            activeInstance?.updateTargetPackages()
        }
    }
}

/**
 * Exhaustive list of known UPI, Banking, and Financial package names in India & globally.
 * These are NEVER targeted, intercepted, or monitored by StudyMode to prevent security alerts.
 */
internal val BANKING_AND_UPI_PACKAGES = setOf(
    "in.org.npci.upiapp", // BHIM
    "com.google.android.apps.nbu.paisa.user", // Google Pay
    "com.phonepe.app", // PhonePe
    "net.one97.paytm", // Paytm
    "com.paytmmoney", // Paytm Money
    "com.sbi.upi", // YONO SBI / SBI UPI
    "com.sbi.lotusintouch", // YONO Lite
    "com.statebankofindia.ubp", // SBI Anywhere
    "com.snapwork.hdfc", // HDFC Bank MobileBanking
    "com.hdfcbank.payzapp", // PayZapp
    "com.csam.icici.bank.imobile", // iMobile ICICI
    "com.icicibank.pockets", // ICICI Pockets
    "com.axis.mobile", // Axis Mobile
    "com.upi.axispay", // Axis Pay
    "com.msf.kbank.mobile", // Kotak Mobile Banking
    "com.kotak811mobilebankingapp.instantsavingsupiscanandpayrecharge", // Kotak 811
    "com.dreamplug.androidapp", // CRED
    "com.fampay.in", // FamPay
    "money.jupiter", // Jupiter
    "indwin.c3.shareapp", // Slice
    "com.nextbillion.groww", // Groww
    "com.tatadigital.tcp", // Tata Neu
    "cards.scapia.android", // Scapia
    "money.super.payments", // super.money
    "com.mobikwik_new", // MobiKwik
    "com.freecharge.android", // Freecharge
    "com.naviapp", // Navi
    "com.jio.myjio", // MyJio (UPI)
    "com.samsung.android.spay", // Samsung Pay
    "com.samsung.android.spaymini", // Samsung Pay Mini
    "com.bankofbaroda.upi", // bob World
    "com.canarabank.mobility", // Canara ai1
    "com.infrasoft.uboi", // Union Bank of India (Vyom)
    "com.IndianBank.IndOASIS", // IndOASIS
    "com.idfcfirstbank.optimus", // IDFC FIRST Bank
    "com.YesBank", // YES Bank
    "in.irisbyyes.app", // iris by YES BANK
    "com.fedmobile", // Federal Bank FedMobile
    "com.upi.federalbank.org.lotza", // Lotza UPI
    "com.rblbank.mobank", // RBL MoBank
    "in.gokiwi.kiwitpap", // Kiwi
    "com.popclub.android", // Popclub
    "in.amazon.mShop.android.shopping", // Amazon Pay
    "com.mgs.pnbupi", // PNB ONE
    "com.boi.mobile", // Bank of India
    "com.infrasofttech.CentralBank", // Central Bank of India
    "com.indusind.mobile", // IndusInd Bank
    "com.ausmallfinancebank.amb", // AU Small Finance Bank
    "com.dbs.in.digitalbank", // DBS digibank
    "com.lcode.ucoupi", // UCO Bank
    "com.lcode.csbupi", // CSB Bank
    "com.euronet.iobupi", // Indian Overseas Bank
    "com.sib.sibmirror", // South Indian Bank
    "com.zerodha.kite3", // Zerodha Kite
    "com.zerodha.coin", // Zerodha Coin
    "com.msf.angelmobile", // Angel One
    "in.upstox.app", // Upstox
    "in.bajajfinservmarkets.app", // Bajaj Finserv
    "com.bharatpe.app", // BharatPe
    "com.ippb.mobilebanking", // IPPB Mobile Banking
    "com.paypal.android.p2pmobile", // PayPal
    "com.transferwise.android", // Wise
    "com.revolut.revolut", // Revolut
)

internal fun isBankingOrUpiPackage(packageName: String): Boolean {
    if (packageName.isBlank()) return false
    val lower = packageName.lowercase()
    return packageName in BANKING_AND_UPI_PACKAGES ||
            lower.contains(".upi") ||
            lower.contains(".bank") ||
            lower.contains("banking") ||
            lower.contains("payments") ||
            lower.contains("fintech") ||
            lower.contains("passbook") ||
            lower.contains("cred.app") ||
            lower.contains("fampay") ||
            lower.contains("jupiter") ||
            lower.contains("slice") ||
            lower.contains("groww") ||
            lower.contains("zerodha")
}

internal fun isSystemOrTransientPackage(packageName: String): Boolean {
    if (packageName.isBlank()) return true
    if (packageName == "android" || packageName == "com.android.systemui") return true
    if (packageName == "com.google.android.gms") return true
    if (packageName.contains("permissioncontroller", ignoreCase = true)) return true
    if (packageName.contains("packageinstaller", ignoreCase = true)) return true
    if (packageName.contains("inputmethod", ignoreCase = true) ||
        packageName.contains("keyboard", ignoreCase = true) ||
        packageName.contains("honeyboard", ignoreCase = true) ||
        packageName.contains("swiftkey", ignoreCase = true) ||
        packageName.contains("autofill", ignoreCase = true)
    ) return true
    return false
}

internal fun isLauncherPackage(context: Context, packageName: String): Boolean {
    if (packageName.isBlank()) return false
    try {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val defaultHomePkg = resolveInfo?.activityInfo?.packageName
        if (defaultHomePkg != null && packageName == defaultHomePkg) {
            return true
        }
    } catch (_: Exception) {}

    return packageName.contains("launcher", ignoreCase = true) ||
            packageName == "com.google.android.apps.nexuslauncher" ||
            packageName.startsWith("com.sec.android.app.launcher") ||
            packageName.startsWith("com.miui.home") ||
            packageName.startsWith("com.oppo.launcher") ||
            packageName.startsWith("com.huawei.android.launcher") ||
            packageName.startsWith("com.oneplus.launcher") ||
            packageName == "com.android.launcher" ||
            packageName == "com.android.launcher3"
}

internal fun isAppLimitWarningDue(
    usedMillis: Long,
    limitMinutes: Int,
    warningPercent: Int,
): Boolean {
    if (warningPercent !in setOf(50, 75, 90) || limitMinutes <= 0) return false
    val limitMillis = limitMinutes * 60_000L
    return usedMillis >= limitMillis * warningPercent / 100 && usedMillis < limitMillis
}

internal fun isAppDailyLimitReached(
    usedMillis: Long,
    limitMinutes: Int,
    blockingEnabled: Boolean,
): Boolean =
    blockingEnabled && limitMinutes > 0 && usedMillis >= limitMinutes * 60_000L

internal object AppLimitNotifications {
    private const val CHANNEL_ID = "app_usage_limits"

    fun postOncePerDay(
        context: Context,
        packageName: String,
        title: String,
        message: String,
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!notificationManager.areNotificationsEnabled()) return

        val today = Calendar.getInstance().let {
            "${it.get(Calendar.YEAR)}-${it.get(Calendar.DAY_OF_YEAR)}"
        }
        val preferenceKey = "$packageName:$title"
        val preferences = context.getSharedPreferences("app_limit_notifications", Context.MODE_PRIVATE)
        if (preferences.getString(preferenceKey, null) == today) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            manager.getNotificationChannel(CHANNEL_ID) == null
        ) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App limits", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            packageName.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        try {
            notificationManager.notify(
                31 * packageName.hashCode() + title.hashCode(),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_studymode)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        } catch (exception: SecurityException) {
            android.util.Log.w("StudyMode", "Unable to post an app-limit notification.", exception)
            return
        }
        preferences.edit().putString(preferenceKey, today).apply()
    }
}

internal fun shouldBlockManuallyBlockedApp(
    manuallyBlocked: Boolean,
    focusOnlyBlockingEnabled: Boolean,
    focusTimer: ActiveTimer?,
    nowMillis: Long,
): Boolean {
    if (!manuallyBlocked) return false
    if (!focusOnlyBlockingEnabled) return true
    return focusTimer?.let { it.running && it.remainingAt(nowMillis) > 0 } == true
}
