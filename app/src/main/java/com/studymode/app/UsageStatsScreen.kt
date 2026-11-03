package com.studymode.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

enum class StatsTabMode {
    DAILY,
    WEEKLY,
    UNLOCKS,
}

private val BackgroundColor = Color(0xFF0E0E10)
private val CardBackgroundColor = Color(0xFF161618)
private val ChipSelectedColor = Color(0xFF28282C)
private val ChipUnselectedColor = Color(0xFF1C1C1F)
private val ChipBorderColor = Color(0xFF333338)
private val MutedTextColor = Color(0xFF8E8E93)
private val DistractingColor = Color(0xFFFF7A00)
private val ProductiveColor = Color(0xFF4CAF50)
private val OthersColor = Color(0xFF8E8E93)

@Composable
fun UsageStatsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    initialMode: StatsTabMode = StatsTabMode.DAILY,
    onOpenAppsAndUsage: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(initialMode) }
    var selectedCategoryFilter by remember { mutableStateOf<AppCategory?>(null) } // null = All apps
    var searchQuery by remember { mutableStateOf("") }
    var selectedDayOffset by remember { mutableIntStateOf(0) } // 0 = today, -1 = yesterday...
    var selectedWeekOffset by remember { mutableIntStateOf(0) } // 0 = this week, -1 = last week...
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var editingApp by remember { mutableStateOf<LaunchableApp?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val launchableApps = remember { installedLaunchableApps(context) }
    val appMap = remember(launchableApps) { launchableApps.associateBy { it.packageName } }

    var currentWeekReport by remember { mutableStateOf<WeekUsageReport?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val now = remember { System.currentTimeMillis() }
    val todayStartMillis = remember(now) {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    LaunchedEffect(mode, selectedDayOffset, selectedWeekOffset, refreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val weekAnchor = if (mode == StatsTabMode.DAILY) {
                todayStartMillis + (selectedDayOffset * 86_400_000L)
            } else {
                todayStartMillis + (selectedWeekOffset * 7 * 86_400_000L)
            }
            try {
                val report = getWeekUsageReport(context, store, weekAnchor)
                withContext(Dispatchers.Main) {
                    currentWeekReport = report
                    isLoading = false
                }
            } catch (e: Exception) {
                android.util.Log.e("StudyMode", "Failed to load usage report", e)
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    val report = currentWeekReport
    val targetDayStart = todayStartMillis + (selectedDayOffset * 86_400_000L)
    val currentDayReport = report?.days?.find { it.dayStartMillis == targetDayStart }
        ?: report?.days?.lastOrNull { it.dayStartMillis <= now }

    // Summary data according to mode
    val heroDurationMillis = if (mode == StatsTabMode.DAILY) {
        currentDayReport?.totalMillis ?: 0L
    } else {
        report?.totalMillis ?: 0L
    }

    val heroDateLabel = if (mode == StatsTabMode.DAILY) {
        currentDayReport?.fullDateLabel ?: "Today"
    } else {
        report?.weekLabel ?: ""
    }

    val distractingMillis = if (mode == StatsTabMode.DAILY) {
        currentDayReport?.distractingMillis ?: 0L
    } else {
        report?.distractingMillis ?: 0L
    }

    val productiveMillis = if (mode == StatsTabMode.DAILY) {
        currentDayReport?.productiveMillis ?: 0L
    } else {
        report?.productiveMillis ?: 0L
    }

    val othersMillis = if (mode == StatsTabMode.DAILY) {
        currentDayReport?.othersMillis ?: 0L
    } else {
        report?.othersMillis ?: 0L
    }

    val activeAppUsageMap = if (mode == StatsTabMode.DAILY) {
        currentDayReport?.appUsageMap ?: emptyMap()
    } else {
        report?.appUsageMap ?: emptyMap()
    }

    // Filter app list
    val filteredAppList = remember(activeAppUsageMap, selectedCategoryFilter, searchQuery, refreshTrigger) {
        activeAppUsageMap.entries
            .asSequence()
            .filter { it.value > 0L }
            .mapNotNull { entry ->
                val app = appMap[entry.key] ?: LaunchableApp(entry.key, entry.key.substringAfterLast('.'))
                val category = store.resolveAppCategory(entry.key)
                Triple(app, category, entry.value)
            }
            .filter { (_, cat, _) ->
                selectedCategoryFilter == null || cat == selectedCategoryFilter
            }
            .filter { (app, _, _) ->
                searchQuery.isBlank() || app.label.contains(searchQuery, ignoreCase = true)
            }
            .sortedByDescending { it.third }
            .toList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // 1. Top Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(onClick = onBack) {
                            Text("←", fontSize = 24.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Usage Stats",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }

                    OutlinedButton(
                        onClick = { showHelpDialog = true },
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text("💬 Help", fontSize = 13.sp, color = Color.White)
                    }
                }
            }

            // 2. Tabs: Daily vs Weekly
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    // Daily Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = StatsTabMode.DAILY }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Daily",
                            fontSize = 15.sp,
                            fontWeight = if (mode == StatsTabMode.DAILY) FontWeight.Bold else FontWeight.Medium,
                            color = if (mode == StatsTabMode.DAILY) Color.White else MutedTextColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (mode == StatsTabMode.DAILY) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.White),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.Transparent),
                            )
                        }
                    }

                    // Weekly Tab
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { mode = StatsTabMode.WEEKLY }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Weekly",
                            fontSize = 15.sp,
                            fontWeight = if (mode == StatsTabMode.WEEKLY) FontWeight.Bold else FontWeight.Medium,
                            color = if (mode == StatsTabMode.WEEKLY) Color.White else MutedTextColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (mode == StatsTabMode.WEEKLY) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.White),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.Transparent),
                            )
                        }
                    }

                    // Unlocks & Pickups Tab
                    Column(
                        modifier = Modifier
                            .weight(1.3f)
                            .clickable { mode = StatsTabMode.UNLOCKS }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "🔓 Unlocks",
                            fontSize = 15.sp,
                            fontWeight = if (mode == StatsTabMode.UNLOCKS) FontWeight.Bold else FontWeight.Medium,
                            color = if (mode == StatsTabMode.UNLOCKS) Color.White else MutedTextColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (mode == StatsTabMode.UNLOCKS) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.White),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(Color.Transparent),
                            )
                        }
                    }
                }
            }

            if (mode == StatsTabMode.UNLOCKS) {
                item {
                    PhoneUnlockView(
                        store = store,
                        selectedDayOffset = selectedDayOffset,
                        onDayOffsetChange = { selectedDayOffset = it },
                        onNavigateToFrictionSettings = onOpenAppsAndUsage
                    )
                }
            } else {
            // 3. Category Filter Chips (Horizontal Scroll)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip(
                        label = "All apps",
                        isSelected = selectedCategoryFilter == null,
                        onClick = { selectedCategoryFilter = null },
                    )
                    CategoryChip(
                        label = "Distracting",
                        isSelected = selectedCategoryFilter == AppCategory.DISTRACTING,
                        onClick = { selectedCategoryFilter = AppCategory.DISTRACTING },
                    )
                    CategoryChip(
                        label = "Productive",
                        isSelected = selectedCategoryFilter == AppCategory.PRODUCTIVE,
                        onClick = { selectedCategoryFilter = AppCategory.PRODUCTIVE },
                    )
                    CategoryChip(
                        label = "Others",
                        isSelected = selectedCategoryFilter == AppCategory.OTHERS,
                        onClick = { selectedCategoryFilter = AppCategory.OTHERS },
                    )
                }
            }

            // 4. Hero Date Navigator & Duration
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                if (mode == StatsTabMode.DAILY) {
                                    selectedDayOffset -= 1
                                } else {
                                    selectedWeekOffset -= 1
                                }
                            },
                        ) {
                            Text("‹", fontSize = 34.sp, color = Color.White, fontWeight = FontWeight.Light)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = formatStatsDuration(heroDurationMillis),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = heroDateLabel,
                                fontSize = 14.sp,
                                color = MutedTextColor,
                            )
                        }

                        IconButton(
                            onClick = {
                                if (mode == StatsTabMode.DAILY && selectedDayOffset < 0) {
                                    selectedDayOffset += 1
                                } else if (mode == StatsTabMode.WEEKLY && selectedWeekOffset < 0) {
                                    selectedWeekOffset += 1
                                }
                            },
                            enabled = if (mode == StatsTabMode.DAILY) selectedDayOffset < 0 else selectedWeekOffset < 0,
                        ) {
                            Text(
                                text = "›",
                                fontSize = 34.sp,
                                color = if ((mode == StatsTabMode.DAILY && selectedDayOffset < 0) ||
                                    (mode == StatsTabMode.WEEKLY && selectedWeekOffset < 0)) Color.White else Color(0xFF3A3A3C),
                                fontWeight = FontWeight.Light,
                            )
                        }
                    }
                }
            }

            // 5. Bar Chart
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    UsageStatsBarChart(
                        report = report,
                        selectedDayStart = targetDayStart,
                        isWeeklyMode = mode == StatsTabMode.WEEKLY,
                        onSelectDay = { dayStart ->
                            val diffDays = ((dayStart - todayStartMillis) / 86_400_000L).toInt()
                            selectedDayOffset = diffDays
                            mode = StatsTabMode.DAILY
                        },
                    )
                }
            }

            // 6. Category Breakdown Legend
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CategoryLegendItem(
                        dotColor = DistractingColor,
                        duration = formatStatsDuration(distractingMillis),
                        label = "Distracting",
                    )
                    CategoryLegendItem(
                        dotColor = ProductiveColor,
                        duration = formatStatsDuration(productiveMillis),
                        label = "Productive",
                    )
                    CategoryLegendItem(
                        dotColor = OthersColor,
                        duration = formatStatsDuration(othersMillis),
                        label = "Others",
                    )
                }
            }

            // Quick Unlocks & Pickups card when on Daily tab
            if (mode == StatsTabMode.DAILY) {
                item {
                    val todayUnlocks = store.todayUnlockCount()
                    val todayPickups = store.todayPickupCount()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { mode = StatsTabMode.UNLOCKS },
                        shape = RoundedCornerShape(16.dp),
                        color = CardBackgroundColor,
                        border = BorderStroke(1.dp, ChipBorderColor),
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("🔓", fontSize = 22.sp)
                                Column {
                                    Text(
                                        text = "Phone Unlocks & Pickups",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$todayUnlocks unlocks · $todayPickups pickups today",
                                        color = MutedTextColor,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Text("View details ›", color = ProductiveColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 7. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search apps", color = MutedTextColor, fontSize = 14.sp) },
                    leadingIcon = { Text("🔍", fontSize = 14.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Text("✕", color = MutedTextColor, fontSize = 14.sp)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardBackgroundColor,
                        unfocusedContainerColor = CardBackgroundColor,
                        focusedBorderColor = Color(0xFF333338),
                        unfocusedBorderColor = Color(0xFF222226),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
            }

            // 8. Categorized App List
            if (filteredAppList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching apps found" else "No app usage for this period",
                            color = MutedTextColor,
                            fontSize = 14.sp,
                        )
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBackgroundColor),
                    ) {
                        filteredAppList.forEachIndexed { index, (app, category, millis) ->
                            AppUsageRowItem(
                                app = app,
                                category = category,
                                durationMillis = millis,
                                onClick = { editingApp = app },
                            )
                            if (index < filteredAppList.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 68.dp),
                                    color = Color(0xFF222226),
                                    thickness = 0.8.dp,
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

    // Category Selection Dialog
    editingApp?.let { app ->
        val currentCategory = store.resolveAppCategory(app.packageName)
        CategorySelectionDialog(
            app = app,
            currentCategory = currentCategory,
            onDismiss = { editingApp = null },
            onSelectCategory = { newCategory ->
                store.setAppCategory(app.packageName, newCategory)
                refreshTrigger += 1
                editingApp = null
            },
        )
    }

    // Help Dialog
    if (showHelpDialog) {
        UsageHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
private fun CategoryChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) ChipSelectedColor else ChipUnselectedColor)
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFF55555C) else ChipBorderColor,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color.White else MutedTextColor,
            )
            if (isSelected) {
                Text(
                    text = "✓",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun UsageStatsBarChart(
    report: WeekUsageReport?,
    selectedDayStart: Long,
    isWeeklyMode: Boolean,
    onSelectDay: (Long) -> Unit,
) {
    val days = report?.days ?: emptyList()
    // Find max day usage, at least 12h
    val maxDayMillis = maxOf(
        12 * 3600 * 1000L,
        days.maxOfOrNull { it.totalMillis } ?: (12 * 3600 * 1000L),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // Y-Axis labels
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 8.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("12h", fontSize = 11.sp, color = MutedTextColor)
                Text("6h", fontSize = 11.sp, color = MutedTextColor)
                Text("0h", fontSize = 11.sp, color = MutedTextColor)
            }

            // 7 Days Bars
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val isSelected = day.dayStartMillis == selectedDayStart
                    val totalFraction = (day.totalMillis.toFloat() / maxDayMillis.toFloat()).coerceIn(0f, 1f)

                    val distractingFraction = (day.distractingMillis.toFloat() / maxDayMillis.toFloat()).coerceIn(0f, 1f)
                    val productiveFraction = (day.productiveMillis.toFloat() / maxDayMillis.toFloat()).coerceIn(0f, 1f)
                    val othersFraction = (day.othersMillis.toFloat() / maxDayMillis.toFloat()).coerceIn(0f, 1f)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { onSelectDay(day.dayStartMillis) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        // Slot track
                        Canvas(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(Color(0xFF18181C)),
                        ) {
                            val totalH = size.height
                            val barH = (totalH * totalFraction).coerceIn(0f, totalH)
                            if (barH <= 0f) return@Canvas

                            if (isSelected || isWeeklyMode) {
                                val distH = (totalH * distractingFraction).coerceIn(0f, barH)
                                val prodH = (totalH * productiveFraction).coerceIn(0f, (barH - distH).coerceAtLeast(0f))
                                val otherH = (barH - distH - prodH).coerceAtLeast(0f)

                                var curBottom = totalH

                                // Bottom: Distracting (Orange)
                                if (distH > 0f) {
                                    drawRect(
                                        color = DistractingColor,
                                        topLeft = Offset(0f, curBottom - distH),
                                        size = Size(size.width, distH),
                                    )
                                    curBottom -= distH
                                }

                                // Middle: Productive (Green)
                                if (prodH > 0f) {
                                    drawRect(
                                        color = ProductiveColor,
                                        topLeft = Offset(0f, curBottom - prodH),
                                        size = Size(size.width, prodH),
                                    )
                                    curBottom -= prodH
                                }

                                // Top: Others (Gray)
                                if (otherH > 0f) {
                                    drawRect(
                                        color = OthersColor,
                                        topLeft = Offset(0f, curBottom - otherH),
                                        size = Size(size.width, otherH),
                                    )
                                }
                            } else {
                                // Neutral gray bar
                                drawRect(
                                    color = Color(0xFF2E2E34),
                                    topLeft = Offset(0f, totalH - barH),
                                    size = Size(size.width, barH),
                                )
                            }
                        }
                    }
                }
            }
        }

        // X-Axis Day Letters
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 28.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            days.forEach { day ->
                val isSelected = day.dayStartMillis == selectedDayStart
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.dayLabel,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MutedTextColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLegendItem(
    dotColor: Color,
    duration: String,
    label: String,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Text(
                text = duration,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MutedTextColor,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

@Composable
private fun AppUsageRowItem(
    app: LaunchableApp,
    category: AppCategory,
    durationMillis: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            app = app,
            modifier = Modifier.size(40.dp),
        )

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))

            when (category) {
                AppCategory.DISTRACTING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(DistractingColor),
                        )
                        Text(
                            text = "Distracting",
                            fontSize = 12.sp,
                            color = DistractingColor,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = formatStatsDuration(durationMillis),
                            fontSize = 12.sp,
                            color = DistractingColor,
                        )
                    }
                }
                AppCategory.PRODUCTIVE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ProductiveColor),
                        )
                        Text(
                            text = "Productive",
                            fontSize = 12.sp,
                            color = ProductiveColor,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = formatStatsDuration(durationMillis),
                            fontSize = 12.sp,
                            color = ProductiveColor,
                        )
                    }
                }
                AppCategory.OTHERS -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(OthersColor),
                        )
                        Text(
                            text = "Others",
                            fontSize = 12.sp,
                            color = OthersColor,
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = formatStatsDuration(durationMillis),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
            )
            Text(
                text = "›",
                fontSize = 20.sp,
                color = Color(0xFF666666),
            )
        }
    }
}

@Composable
private fun CategorySelectionDialog(
    app: LaunchableApp,
    currentCategory: AppCategory,
    onDismiss: () -> Unit,
    onSelectCategory: (AppCategory) -> Unit,
) {
    var selectedCategory by remember { mutableStateOf(currentCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppIcon(app = app, modifier = Modifier.size(32.dp))
                Column {
                    Text(app.label, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    Text("Select App Category", fontSize = 12.sp, color = MutedTextColor)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryOptionRow(
                    category = AppCategory.DISTRACTING,
                    title = "Distracting",
                    description = "Social media, streaming, games & time-wasters",
                    dotColor = DistractingColor,
                    isSelected = selectedCategory == AppCategory.DISTRACTING,
                    onSelect = { selectedCategory = AppCategory.DISTRACTING },
                )
                CategoryOptionRow(
                    category = AppCategory.PRODUCTIVE,
                    title = "Productive",
                    description = "Study, work, education, reading & utilities",
                    dotColor = ProductiveColor,
                    isSelected = selectedCategory == AppCategory.PRODUCTIVE,
                    onSelect = { selectedCategory = AppCategory.PRODUCTIVE },
                )
                CategoryOptionRow(
                    category = AppCategory.OTHERS,
                    title = "Others",
                    description = "Communication, tools & general browsing",
                    dotColor = OthersColor,
                    isSelected = selectedCategory == AppCategory.OTHERS,
                    onSelect = { selectedCategory = AppCategory.OTHERS },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSelectCategory(selectedCategory) },
            ) {
                Text("Save", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedTextColor)
            }
        },
    )
}

@Composable
private fun CategoryOptionRow(
    category: AppCategory,
    title: String,
    description: String,
    dotColor: Color,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFF28282E) else Color(0xFF161618),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) dotColor else Color(0xFF2A2A30),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text(description, fontSize = 11.sp, color = MutedTextColor)
            }
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = dotColor,
                    unselectedColor = MutedTextColor,
                ),
            )
        }
    }
}

@Composable
private fun UsageHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E22),
        title = {
            Text("Usage Stats & Categories", fontWeight = FontWeight.Bold, color = Color.White)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "StudyMode categorizes your daily screen time to help you identify healthy habits and eliminate distractions:",
                    color = Color.White,
                    fontSize = 13.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🟠", fontSize = 14.sp)
                    Column {
                        Text("Distracting", fontWeight = FontWeight.Bold, color = DistractingColor, fontSize = 13.sp)
                        Text("Entertainment, social media, and games that interrupt your focus.", color = MutedTextColor, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🟢", fontSize = 14.sp)
                    Column {
                        Text("Productive", fontWeight = FontWeight.Bold, color = ProductiveColor, fontSize = 13.sp)
                        Text("Educational apps, notes, documents, and work tools.", color = MutedTextColor, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("⚪", fontSize = 14.sp)
                    Column {
                        Text("Others", fontWeight = FontWeight.Bold, color = OthersColor, fontSize = 13.sp)
                        Text("System tools, communication, and general browser usage.", color = MutedTextColor, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "💡 Tip: Tap any app in the list below to change its category at any time.",
                    color = Color(0xFF69ED70),
                    fontSize = 12.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it", color = Color(0xFF69ED70), fontWeight = FontWeight.Bold)
            }
        },
    )
}

private fun formatStatsDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}
