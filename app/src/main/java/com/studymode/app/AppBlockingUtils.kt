package com.studymode.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

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
