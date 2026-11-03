package com.studymode.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import java.util.Calendar
import java.util.Locale

data class LaunchableApp(
    val packageName: String,
    val label: String,
)

data class AppUsageRow(
    val packageName: String,
    val label: String,
    val foregroundMillis: Long,
)

object AppListCache {
    @Volatile private var cachedApps: List<LaunchableApp>? = null
    @Volatile private var cachedPackages: Set<String>? = null

    fun getApps(context: Context): List<LaunchableApp> {
        cachedApps?.let { return it }
        val loaded = loadInstalledApps(context)
        cachedApps = loaded
        cachedPackages = loaded.map { it.packageName }.toSet()
        return loaded
    }

    fun getPackages(context: Context): Set<String> {
        cachedPackages?.let { return it }
        return getApps(context).map { it.packageName }.toSet()
    }

    fun invalidate() {
        cachedApps = null
        cachedPackages = null
    }

    private fun loadInstalledApps(context: Context): List<LaunchableApp> {
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val protectedPackages = packageManager.queryIntentActivities(homeIntent, 0)
            .mapNotNullTo(mutableSetOf()) { it.activityInfo?.packageName }
            .apply {
                add("com.android.systemui")
            }

        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { it.activityInfo?.applicationInfo }
            .filter { it.packageName !in protectedPackages }
            .distinctBy { it.packageName }
            .map {
                LaunchableApp(
                    packageName = it.packageName,
                    label = packageManager.getApplicationLabel(it).toString(),
                )
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }
}

fun installedLaunchableApps(context: Context): List<LaunchableApp> {
    return AppListCache.getApps(context)
}

@Suppress("DEPRECATION")
fun hasUsageAccess(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName,
    ) == AppOpsManager.MODE_ALLOWED
}

/**
 * Returns accurate today foreground usage for launchable apps from 00:00 midnight to now.
 * Uses linear timeline playback from UsageEvents (matching Android Digital Wellbeing).
 */
fun todayAccurateUsageMap(context: Context): Map<String, Long> {
    if (!hasUsageAccess(context)) return emptyMap()
    val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
    val now = System.currentTimeMillis()
    val startOfDay = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val launchable = AppListCache.getPackages(context)
    if (launchable.isEmpty()) return emptyMap()

    val usageMap = mutableMapOf<String, Long>()

    try {
        val events = usageManager.queryEvents(startOfDay, now)
        if (events != null) {
            val event = UsageEvents.Event()
            var currentForegroundPkg: String? = null
            var currentSessionStart = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val eventPkg = event.packageName ?: continue
                val eventTime = event.timeStamp

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        // Close previous foreground session if package changed
                        if (currentForegroundPkg != null && currentSessionStart > 0L) {
                            val duration = eventTime - currentSessionStart
                            if (duration > 0 && currentForegroundPkg in launchable) {
                                usageMap[currentForegroundPkg] = (usageMap[currentForegroundPkg] ?: 0L) + duration
                            }
                        }
                        currentForegroundPkg = eventPkg
                        currentSessionStart = eventTime
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED -> {
                        if (currentForegroundPkg == eventPkg && currentSessionStart > 0L) {
                            val duration = eventTime - currentSessionStart
                            if (duration > 0 && currentForegroundPkg in launchable) {
                                usageMap[currentForegroundPkg] = (usageMap[currentForegroundPkg] ?: 0L) + duration
                            }
                            currentForegroundPkg = null
                            currentSessionStart = 0L
                        }
                    }
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                        // Screen turned off: pause any active foreground session
                        if (currentForegroundPkg != null && currentSessionStart > 0L) {
                            val duration = eventTime - currentSessionStart
                            if (duration > 0 && currentForegroundPkg in launchable) {
                                usageMap[currentForegroundPkg] = (usageMap[currentForegroundPkg] ?: 0L) + duration
                            }
                        }
                        currentForegroundPkg = null
                        currentSessionStart = 0L
                    }
                }
            }

            // If an app is still in foreground right now
            if (currentForegroundPkg != null && currentSessionStart > 0L && now >= currentSessionStart) {
                val duration = (now - currentSessionStart).coerceAtMost(4 * 3600 * 1000L)
                if (duration > 0 && currentForegroundPkg in launchable) {
                    usageMap[currentForegroundPkg] = (usageMap[currentForegroundPkg] ?: 0L) + duration
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.w("StudyMode", "queryEvents failed: ${e.message}")
    }

    // Fallback if queryEvents yielded no records
    if (usageMap.isEmpty()) {
        try {
            val statsMap = usageManager.queryAndAggregateUsageStats(startOfDay, now)
            val maxPossible = now - startOfDay
            for (pkg in launchable) {
                val stats = statsMap[pkg] ?: continue
                if (stats.lastTimeUsed >= startOfDay && stats.totalTimeInForeground > 0) {
                    val duration = stats.totalTimeInForeground.coerceAtMost(maxPossible)
                    if (duration > 0) {
                        usageMap[pkg] = duration
                    }
                }
            }
        } catch (_: Exception) {}
    }

    return usageMap
}

fun todayAppUsage(context: Context, apps: List<LaunchableApp>): List<AppUsageRow> {
    val usageMap = todayAccurateUsageMap(context)
    if (usageMap.isEmpty()) return emptyList()
    val names = apps.associateBy { it.packageName }

    return usageMap.entries
        .mapNotNull { (pkg, millis) ->
            val app = names[pkg] ?: return@mapNotNull null
            if (millis <= 0L) return@mapNotNull null
            AppUsageRow(
                packageName = pkg,
                label = app.label,
                foregroundMillis = millis,
            )
        }
        .sortedByDescending { it.foregroundMillis }
        .take(20)
}

fun todayLaunchableAppUsageMillis(context: Context, apps: List<LaunchableApp>): Long {
    val usageMap = todayAccurateUsageMap(context)
    val packageNames = apps.asSequence().map { it.packageName }.toHashSet()
    return usageMap.entries
        .filter { it.key in packageNames }
        .sumOf { it.value }
}

fun todayAppUsageMillis(context: Context, packageName: String): Long {
    return todayAccurateUsageMap(context)[packageName] ?: 0L
}

fun todayTotalDeviceScreenTimeMillis(context: Context): Long {
    return todayAccurateUsageMap(context).values.sum()
}

data class SleepUsageReport(
    val totalForegroundMillis: Long,
    val topApps: List<AppUsageRow>,
)

fun sleepPhoneUsage(
    context: Context,
    windowStartMillis: Long,
    windowEndMillis: Long,
    apps: List<LaunchableApp> = installedLaunchableApps(context),
): SleepUsageReport {
    if (!hasUsageAccess(context) || windowStartMillis >= windowEndMillis) {
        return SleepUsageReport(0L, emptyList())
    }
    val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val appMap = apps.associateBy { it.packageName }
    val launchablePackages = appMap.keys

    val events = usageManager.queryEvents(windowStartMillis, windowEndMillis)
    val appForegroundMillis = mutableMapOf<String, Long>()
    val appLastResumed = mutableMapOf<String, Long>()

    val event = android.app.usage.UsageEvents.Event()
    while (events.hasNextEvent()) {
        events.getNextEvent(event)
        val pkg = event.packageName ?: continue
        if (pkg !in launchablePackages) continue

        when (event.eventType) {
            android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                appLastResumed[pkg] = event.timeStamp
            }
            android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED -> {
                val resumedTime = appLastResumed.remove(pkg)
                if (resumedTime != null && event.timeStamp >= resumedTime) {
                    val duration = event.timeStamp - resumedTime
                    appForegroundMillis[pkg] = (appForegroundMillis[pkg] ?: 0L) + duration
                }
            }
        }
    }

    val topApps = appForegroundMillis.entries
        .sortedByDescending { it.value }
        .take(5)
        .mapNotNull { entry ->
            appMap[entry.key]?.let {
                AppUsageRow(
                    packageName = it.packageName,
                    label = it.label,
                    foregroundMillis = entry.value,
                )
            }
        }

    return SleepUsageReport(
        totalForegroundMillis = appForegroundMillis.values.sum(),
        topApps = topApps,
    )
}

data class DayUsageReport(
    val dayStartMillis: Long,
    val dayLabel: String,
    val fullDateLabel: String,
    val totalMillis: Long,
    val distractingMillis: Long,
    val productiveMillis: Long,
    val othersMillis: Long,
    val appUsageMap: Map<String, Long>,
)

data class WeekUsageReport(
    val weekStartMillis: Long,
    val weekEndMillis: Long,
    val weekLabel: String,
    val totalMillis: Long,
    val dailyAverageMillis: Long,
    val distractingMillis: Long,
    val productiveMillis: Long,
    val othersMillis: Long,
    val days: List<DayUsageReport>,
    val appUsageMap: Map<String, Long>,
)

fun getPastDayUsageMap(context: Context, startOfDay: Long, endOfDay: Long): Map<String, Long> {
    if (!hasUsageAccess(context)) return emptyMap()
    val usageManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
    val launchable = AppListCache.getPackages(context)
    if (launchable.isEmpty()) return emptyMap()

    val usageMap = mutableMapOf<String, Long>()
    try {
        val statsList = usageManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, endOfDay) ?: emptyList()
        val maxDuration = endOfDay - startOfDay
        for (stat in statsList) {
            val pkg = stat.packageName ?: continue
            if (pkg !in launchable) continue
            if (stat.lastTimeUsed >= startOfDay) {
                val time = stat.totalTimeInForeground.coerceAtMost(maxDuration)
                if (time > 0) {
                    usageMap[pkg] = maxOf(usageMap[pkg] ?: 0L, time)
                }
            }
        }
    } catch (_: Exception) {}
    return usageMap
}

fun getDayUsageReport(context: Context, store: LocalStudyStore, targetDayStart: Long): DayUsageReport {
    val now = System.currentTimeMillis()
    val todayStart = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val dEnd = targetDayStart + 86_400_000L - 1L
    val usageMap: Map<String, Long> = when {
        targetDayStart > now -> emptyMap()
        targetDayStart == todayStart -> todayAccurateUsageMap(context)
        else -> getPastDayUsageMap(context, targetDayStart, dEnd)
    }

    var distracting = 0L
    var productive = 0L
    var others = 0L

    for ((pkg, time) in usageMap) {
        when (store.resolveAppCategory(pkg)) {
            AppCategory.DISTRACTING -> distracting += time
            AppCategory.PRODUCTIVE -> productive += time
            AppCategory.OTHERS -> others += time
        }
    }

    val cal = Calendar.getInstance().apply { timeInMillis = targetDayStart }
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val dayLetters = mapOf(
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S",
        Calendar.SUNDAY to "S"
    )
    val dayLetter = dayLetters[dayOfWeek] ?: "D"

    val fullFormat = java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault())
    val dateLabel = when {
        targetDayStart == todayStart -> "Today - " + fullFormat.format(java.util.Date(targetDayStart))
        targetDayStart == todayStart - 86_400_000L -> "Yesterday - " + fullFormat.format(java.util.Date(targetDayStart))
        else -> fullFormat.format(java.util.Date(targetDayStart))
    }

    return DayUsageReport(
        dayStartMillis = targetDayStart,
        dayLabel = dayLetter,
        fullDateLabel = dateLabel,
        totalMillis = distracting + productive + others,
        distractingMillis = distracting,
        productiveMillis = productive,
        othersMillis = others,
        appUsageMap = usageMap,
    )
}

fun getWeekUsageReport(context: Context, store: LocalStudyStore, targetWeekAnchorMillis: Long): WeekUsageReport {
    val cal = Calendar.getInstance().apply {
        timeInMillis = targetWeekAnchorMillis
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val weekStart = cal.timeInMillis
    val now = System.currentTimeMillis()
    val todayStart = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    val days = mutableListOf<DayUsageReport>()
    val combinedAppUsage = mutableMapOf<String, Long>()

    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    val dateFormat = java.text.SimpleDateFormat("d MMM", Locale.getDefault())
    val fullFormat = java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault())

    for (i in 0..6) {
        val dStart = weekStart + i * 86_400_000L
        val dEnd = dStart + 86_400_000L - 1L

        val usageMap: Map<String, Long> = when {
            dStart > now -> emptyMap()
            dStart == todayStart -> todayAccurateUsageMap(context)
            else -> getPastDayUsageMap(context, dStart, dEnd)
        }

        for ((pkg, time) in usageMap) {
            combinedAppUsage[pkg] = (combinedAppUsage[pkg] ?: 0L) + time
        }

        var distracting = 0L
        var productive = 0L
        var others = 0L

        for ((pkg, time) in usageMap) {
            when (store.resolveAppCategory(pkg)) {
                AppCategory.DISTRACTING -> distracting += time
                AppCategory.PRODUCTIVE -> productive += time
                AppCategory.OTHERS -> others += time
            }
        }

        val total = distracting + productive + others
        val dateLabel = when {
            dStart == todayStart -> "Today - " + fullFormat.format(java.util.Date(dStart))
            dStart == todayStart - 86_400_000L -> "Yesterday - " + fullFormat.format(java.util.Date(dStart))
            else -> fullFormat.format(java.util.Date(dStart))
        }

        days.add(
            DayUsageReport(
                dayStartMillis = dStart,
                dayLabel = dayLetters[i],
                fullDateLabel = dateLabel,
                totalMillis = total,
                distractingMillis = distracting,
                productiveMillis = productive,
                othersMillis = others,
                appUsageMap = usageMap,
            )
        )
    }

    val totalWeek = combinedAppUsage.values.sum()
    val elapsedDaysCount = days.count { it.dayStartMillis <= now }.coerceAtLeast(1)
    val avg = totalWeek / elapsedDaysCount

    var totalDistracting = 0L
    var totalProductive = 0L
    var totalOthers = 0L
    for ((pkg, time) in combinedAppUsage) {
        when (store.resolveAppCategory(pkg)) {
            AppCategory.DISTRACTING -> totalDistracting += time
            AppCategory.PRODUCTIVE -> totalProductive += time
            AppCategory.OTHERS -> totalOthers += time
        }
    }

    val weekEnd = weekStart + 6 * 86_400_000L
    val weekLabel = "${dateFormat.format(java.util.Date(weekStart))} - ${dateFormat.format(java.util.Date(weekEnd))}"

    return WeekUsageReport(
        weekStartMillis = weekStart,
        weekEndMillis = weekEnd,
        weekLabel = weekLabel,
        totalMillis = totalWeek,
        dailyAverageMillis = avg,
        distractingMillis = totalDistracting,
        productiveMillis = totalProductive,
        othersMillis = totalOthers,
        days = days,
        appUsageMap = combinedAppUsage,
    )
}
