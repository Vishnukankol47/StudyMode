package com.studymode.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ScheduleInk = Color(0xFF0B0B0B)
private val ScheduleCard = Color(0xFF181818)
private val ScheduleElevated = Color(0xFF202020)
private val ScheduleGreen = Color(0xFF80D957)
private val ScheduleSoftGreen = Color(0xFF233A20)
private val ScheduleText = Color.White
private val ScheduleMuted = Color(0xFF929292)
private val ScheduleDivider = Color(0xFF292929)
private val WEEKDAYS = listOf(
    Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
    Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY,
)

private enum class SchedulePage { LIST, EDITOR, PROGRESS }

@Composable
fun ScheduleSystemScreen(
    store: LocalStudyStore,
    onSchedulesChanged: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SchedulePage.LIST.name) }
    var editScheduleId by rememberSaveable { mutableLongStateOf(0L) }
    var refresh by rememberSaveable { mutableIntStateOf(0) }
    var successMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        store.schedules()
            .filter { it.enabled && it.reminderEnabled }
            .forEach { StudyScheduleAlarms.schedule(context, it) }
    }

    when (SchedulePage.valueOf(page)) {
        SchedulePage.LIST -> ScheduleListScreen(
            schedules = remember(refresh) { store.schedules() },
            store = store,
            successMessage = successMessage,
            onClearMessage = { successMessage = null },
            onAdd = {
                editScheduleId = 0L
                page = SchedulePage.EDITOR.name
            },
            onEdit = {
                editScheduleId = it
                page = SchedulePage.EDITOR.name
            },
            onProgress = { page = SchedulePage.PROGRESS.name },
            onChanged = {
                refresh += 1
                onSchedulesChanged()
            },
        )
        SchedulePage.EDITOR -> {
            val schedule = remember(editScheduleId, refresh) {
                if (editScheduleId > 0) store.schedule(editScheduleId) else null
            }
            ScheduleEditorScreen(
                store = store,
                initial = schedule,
                onBack = { page = SchedulePage.LIST.name },
                onSave = { updated ->
                    val id = store.saveSchedule(updated)
                    val saved = store.schedule(id)
                        ?: throw IllegalStateException("Saved schedule could not be reloaded.")
                    StudyScheduleAlarms.cancel(context, id)
                    StudyScheduleAlarms.schedule(context, saved)
                    refresh += 1
                    onSchedulesChanged()
                    successMessage = if (schedule == null) "Schedule created." else "Schedule updated."
                    page = SchedulePage.LIST.name
                },
            )
        }
        SchedulePage.PROGRESS -> ScheduleProgressScreen(
            store = store,
            onBack = { page = SchedulePage.LIST.name },
        )
    }
}

@Composable
private fun ScheduleListScreen(
    schedules: List<StudySchedule>,
    store: LocalStudyStore,
    successMessage: String?,
    onClearMessage: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onProgress: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val today = remember { startOfDay(System.currentTimeMillis()) }
    val monday = remember { startOfWeek(today) }
    var selectedDay by rememberSaveable { mutableLongStateOf(today) }
    var deleteTarget by remember { mutableStateOf<StudySchedule?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScheduleInk)
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Focus Schedules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Plan your study time and stay consistent.", color = ScheduleMuted)
            }
            TextButton(onClick = onProgress) {
                Text("▥  Progress", color = ScheduleGreen)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WEEKDAYS.forEachIndexed { index, weekday ->
                val day = Calendar.getInstance().apply {
                    timeInMillis = monday
                    add(Calendar.DAY_OF_YEAR, index)
                }
                val dayStart = day.timeInMillis
                val containsSchedules = schedules.any { weekday in it.activeDays }
                val selected = selectedDay == dayStart
                Surface(
                    modifier = Modifier
                        .width(62.dp)
                        .height(82.dp)
                        .clickable { selectedDay = dayStart },
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) ScheduleSoftGreen else ScheduleCard,
                    border = BorderStroke(
                        if (selected) 1.5.dp else 1.dp,
                        if (selected) ScheduleGreen else ScheduleDivider,
                    ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) ?: "", color = if (selected) ScheduleGreen else ScheduleMuted)
                        Text(day.get(Calendar.DAY_OF_MONTH).toString(), color = ScheduleText, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (containsSchedules) ScheduleGreen else Color.Transparent),
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                val dayTitle = if (selectedDay == today) {
                    "Today's Schedule"
                } else {
                    "${Calendar.getInstance().apply { timeInMillis = selectedDay }
                        .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault())}'s Schedule"
                }
                Text(dayTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(selectedDay)), color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onAdd, shape = RoundedCornerShape(24.dp)) {
                Text("+ Add Schedule", color = ScheduleGreen)
            }
        }

        val selectedSchedules = remember(schedules, selectedDay) {
            store.schedulesForDate(selectedDay)
        }
        if (selectedSchedules.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("No schedules for this day", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Create a study block to start planning your week.", color = ScheduleMuted, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = ScheduleGreen, contentColor = ScheduleInk)) {
                    Text("Create Schedule")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(selectedSchedules, key = { it.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onOpen = { onEdit(schedule.id) },
                        onEnabledChange = { enabled ->
                            store.setScheduleEnabled(schedule.id, enabled)
                            store.schedule(schedule.id)?.let { StudyScheduleAlarms.schedule(context, it) }
                            onChanged()
                        },
                        onEdit = { onEdit(schedule.id) },
                        onDuplicate = {
                                val copyId = store.saveSchedule(schedule.copy(id = 0, title = "${schedule.title} copy"))
                                store.schedule(copyId)?.let { StudyScheduleAlarms.schedule(context, it) }
                                onChanged()
                            },
                        onDelete = { deleteTarget = schedule },
                    )
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ScheduleGreen, contentColor = ScheduleInk),
        ) {
            Text("+  Create Schedule", fontWeight = FontWeight.Bold)
        }
        successMessage?.let {
            TextButton(onClick = onClearMessage) {
                Text(it, color = ScheduleGreen)
            }
        }
    }

    deleteTarget?.let { schedule ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete schedule?") },
            text = { Text("“${schedule.title}” and its reminder will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteSchedule(schedule.id)
                    StudyScheduleAlarms.cancel(context, schedule.id)
                    deleteTarget = null
                    onChanged()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ScheduleCard(
    schedule: StudySchedule,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = ScheduleCard,
        border = BorderStroke(1.dp, ScheduleDivider),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(5.dp)
                    .height(104.dp)
                    .background(if (schedule.enabled) ScheduleGreen else Color(0xFF4D4D4D)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(schedule.title, fontWeight = FontWeight.SemiBold)
                Text(
                    "${formatTime(schedule.startMinute)} – ${formatTime(schedule.endMinute)}",
                    color = ScheduleMuted,
                )
                Text(repeatDaysLabel(schedule.activeDays), color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = onEnabledChange,
            )
            Box {
                TextButton(onClick = { menuExpanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("⋮", color = ScheduleMuted, fontSize = 22.sp)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit schedule") }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditorScreen(
    store: LocalStudyStore,
    initial: StudySchedule?,
    onBack: () -> Unit,
    onSave: (StudySchedule) -> Unit,
) {
    val context = LocalContext.current
    val calendarToday = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var startMinute by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.startMinute ?: 20 * 60) }
    var endMinute by rememberSaveable(initial?.id) { mutableIntStateOf(initial?.endMinute ?: 22 * 60) }
    var activeDays by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.activeDays ?: setOf(calendarToday))
    }
    var reminderEnabled by rememberSaveable(initial?.id) {
        mutableStateOf(
            initial?.reminderEnabled ?: StudyScheduleAlarms.notificationsEnabled(context),
        )
    }
    var breaksEnabled by rememberSaveable(initial?.id) { mutableStateOf(initial?.breaksEnabled ?: true) }
    var focusInterval by rememberSaveable(initial?.id) { mutableStateOf((initial?.focusIntervalMinutes ?: 25).toString()) }
    var shortBreak by rememberSaveable(initial?.id) { mutableStateOf((initial?.shortBreakMinutes ?: 5).toString()) }
    var longBreak by rememberSaveable(initial?.id) { mutableStateOf((initial?.longBreakMinutes ?: 15).toString()) }
    var intervalsBeforeLongBreak by rememberSaveable(initial?.id) { mutableStateOf((initial?.intervalsBeforeLongBreak ?: 4).toString()) }
    var blockedPackages by rememberSaveable(initial?.id) { mutableStateOf(initial?.blockedPackages ?: emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showBreakSettings by remember { mutableStateOf(false) }
    var showBlockedApps by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && StudyScheduleAlarms.notificationsEnabled(context)) {
            reminderEnabled = true
            error = null
        } else {
            reminderEnabled = false
            error = "Notification permission is off. Enable it in Android Settings to use reminders."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScheduleInk)
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹  Back", color = ScheduleText) }
            Text(
                if (initial == null) "Create Schedule" else initial.title,
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60); error = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Schedule name") },
                singleLine = true,
            )

            TimeRangeCard(
                startMinute = startMinute,
                endMinute = endMinute,
                onStartChange = { startMinute = it },
                onEndChange = { endMinute = it },
            )

            EditorCard {
                Text("Active days", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    WEEKDAYS.forEach { day ->
                        val selected = day in activeDays
                        val label = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, day) }
                            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
                            .orEmpty()
                            .take(1)
                        Surface(
                            modifier = Modifier.size(38.dp).clickable {
                                activeDays = if (selected) activeDays - day else activeDays + day
                                error = null
                            },
                            shape = CircleShape,
                            color = if (selected) ScheduleSoftGreen else ScheduleElevated,
                            border = if (selected) BorderStroke(1.dp, ScheduleGreen) else null,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, color = if (selected) ScheduleGreen else ScheduleMuted, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { activeDays = WEEKDAYS.take(5).toSet() }) { Text("Weekdays", color = ScheduleGreen) }
                    TextButton(onClick = { activeDays = setOf(Calendar.SATURDAY, Calendar.SUNDAY) }) { Text("Weekend", color = ScheduleGreen) }
                    TextButton(onClick = { activeDays = WEEKDAYS.toSet() }) { Text("Every day", color = ScheduleGreen) }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = ScheduleCard,
                border = BorderStroke(1.dp, ScheduleDivider),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ScheduleSoftGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("🔔", fontSize = 20.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Alarm Reminder", fontWeight = FontWeight.SemiBold)
                        Text("Get notified when it's time to focus", color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && !StudyScheduleAlarms.notificationsEnabled(context)) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.startActivity(
                                        android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            .setData(android.net.Uri.parse("package:${context.packageName}")),
                                    )
                                    error = "Enable StudyMode notifications in Android Settings, then turn on the reminder."
                                }
                            } else {
                                reminderEnabled = enabled
                            }
                        },
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ScheduleGreen,
                        ),
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBreakSettings = true },
                shape = RoundedCornerShape(22.dp),
                color = ScheduleCard,
                border = BorderStroke(1.dp, ScheduleDivider),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ScheduleSoftGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("☕", fontSize = 20.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Breaks", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (breaksEnabled) "$shortBreak min · $intervalsBeforeLongBreak breaks"
                            else "Break reminders off",
                            color = ScheduleMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("›", color = ScheduleMuted, fontSize = 18.sp)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBlockedApps = true },
                shape = RoundedCornerShape(22.dp),
                color = ScheduleCard,
                border = BorderStroke(1.dp, ScheduleDivider),
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ScheduleSoftGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🛡", fontSize = 20.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Blocked apps", fontWeight = FontWeight.SemiBold)
                            Text("Prevent distractions during this schedule", color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", color = ScheduleMuted, fontSize = 18.sp)
                    }
                    if (blockedPackages.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${blockedPackages.size} app${if (blockedPackages.size == 1) "" else "s"} blocked",
                                color = ScheduleGreen,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Button(
            onClick = {
                if (saving) return@Button
                val interval = focusInterval.toIntOrNull()
                val short = shortBreak.toIntOrNull()
                val long = longBreak.toIntOrNull()
                val intervals = intervalsBeforeLongBreak.toIntOrNull()
                error = when {
                    title.isBlank() -> "Enter a schedule name."
                    activeDays.isEmpty() -> "Select at least one active day."
                    startMinute == endMinute -> "Start and end times cannot be the same."
                    reminderEnabled && !StudyScheduleAlarms.notificationsEnabled(context) ->
                        "Enable notifications in Android Settings or turn off Alarm Reminder."
                    interval == null || interval !in 5..180 -> "Focus interval must be 5–180 minutes."
                    short == null || short !in 1..60 -> "Short break must be 1–60 minutes."
                    long == null || long !in 1..120 -> "Long break must be 1–120 minutes."
                    intervals == null || intervals !in 2..12 -> "Choose 2–12 intervals before a long break."
                    else -> null
                }
                if (error == null) {
                    saving = true
                    try {
                        val candidate = StudySchedule(
                                id = initial?.id ?: 0,
                                title = title.trim(),
                                dayOfWeek = activeDays.minOrNull() ?: calendarToday,
                                startMinute = startMinute,
                                durationMinutes = (endMinute - startMinute + 1440) % 1440,
                                enabled = initial?.enabled ?: true,
                                activeDays = activeDays,
                                endMinute = endMinute,
                                reminderEnabled = reminderEnabled,
                                breaksEnabled = breaksEnabled,
                                focusIntervalMinutes = interval!!,
                                shortBreakMinutes = short!!,
                                longBreakMinutes = long!!,
                                intervalsBeforeLongBreak = intervals!!,
                                blockedPackages = blockedPackages,
                                createdAtMillis = initial?.createdAtMillis ?: System.currentTimeMillis(),
                            )
                        if (store.hasSameNameTimingConflict(candidate)) {
                            error = "A schedule with this name already overlaps on one of these days."
                        } else {
                            onSave(candidate)
                        }
                    } catch (exception: IllegalArgumentException) {
                        error = exception.message ?: "Could not save the schedule."
                    } catch (exception: IllegalStateException) {
                        error = exception.message ?: "Could not save the schedule."
                    } catch (exception: android.database.SQLException) {
                        error = exception.message ?: "The local schedule database could not be updated."
                    } catch (exception: SecurityException) {
                        error = exception.message ?: "Could not save the schedule."
                    } finally {
                        saving = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(56.dp),
            enabled = !saving,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ScheduleGreen, contentColor = ScheduleInk),
        ) {
            Text(if (saving) "Saving…" else "Save Schedule", fontWeight = FontWeight.Bold)
        }
    }

    if (showBreakSettings) {
        BreakSettingsDialog(
            enabled = breaksEnabled,
            focusInterval = focusInterval,
            shortBreak = shortBreak,
            longBreak = longBreak,
            intervals = intervalsBeforeLongBreak,
            onDismiss = { showBreakSettings = false },
            onSave = { enabled, focus, short, long, intervals ->
                breaksEnabled = enabled
                focusInterval = focus
                shortBreak = short
                longBreak = long
                intervalsBeforeLongBreak = intervals
                showBreakSettings = false
            },
        )
    }
    if (showBlockedApps) {
        BlockedAppsDialog(
            selected = blockedPackages,
            onDismiss = { showBlockedApps = false },
            onSave = {
                blockedPackages = it
                showBlockedApps = false
            },
        )
    }
}

@Composable
private fun TimeRangeCard(
    startMinute: Int,
    endMinute: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ScheduleCard,
        border = BorderStroke(1.dp, ScheduleDivider),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ScheduleGreen),
                )
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(40.dp)
                        .background(ScheduleGreen.copy(alpha = 0.35f)),
                )
                Box(
                    modifier = Modifier.size(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(12.dp)) {
                        drawCircle(
                            color = ScheduleGreen,
                            radius = size.minDimension / 2 - 2.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeSettingRow("From", startMinute, onStartChange)
                TimeSettingRow("To", endMinute, onEndChange)
            }
        }
    }
}

@Composable
private fun TimeSettingRow(
    label: String,
    minute: Int,
    onSelected: (Int) -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().height(60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ScheduleMuted)
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, selectedMinute -> onSelected(hour * 60 + selectedMinute) },
                    minute / 60,
                    minute % 60,
                    android.text.format.DateFormat.is24HourFormat(context),
                ).show()
            },
            shape = RoundedCornerShape(24.dp),
        ) {
            Text(formatTime(minute), color = ScheduleText)
        }
    }
}

@Composable
private fun EditorCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ScheduleCard,
        border = BorderStroke(1.dp, ScheduleDivider),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun BreakSettingsDialog(
    enabled: Boolean,
    focusInterval: String,
    shortBreak: String,
    longBreak: String,
    intervals: String,
    onDismiss: () -> Unit,
    onSave: (Boolean, String, String, String, String) -> Unit,
) {
    var enabledState by rememberSaveable { mutableStateOf(enabled) }
    var focusState by rememberSaveable { mutableStateOf(focusInterval) }
    var shortState by rememberSaveable { mutableStateOf(shortBreak) }
    var longState by rememberSaveable { mutableStateOf(longBreak) }
    var intervalsState by rememberSaveable { mutableStateOf(intervals) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Break settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingSwitch("Breaks", "Use breaks with this schedule", enabledState, { enabledState = it })
                NumericField("Focus interval (5–180 min)", focusState) { focusState = it }
                NumericField("Short break (1–60 min)", shortState) { shortState = it }
                NumericField("Long break (1–120 min)", longState) { longState = it }
                NumericField("Intervals before long break (2–12)", intervalsState) { intervalsState = it }
                TextButton(onClick = {
                    enabledState = true
                    focusState = "25"
                    shortState = "5"
                    longState = "15"
                    intervalsState = "4"
                    error = null
                }) { Text("Reset to defaults", color = ScheduleGreen) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val focus = focusState.toIntOrNull()
                val short = shortState.toIntOrNull()
                val long = longState.toIntOrNull()
                val count = intervalsState.toIntOrNull()
                error = when {
                    focus == null || focus !in 5..180 -> "Focus interval must be 5–180 minutes."
                    short == null || short !in 1..60 -> "Short break must be 1–60 minutes."
                    long == null || long !in 1..120 -> "Long break must be 1–120 minutes."
                    count == null || count !in 2..12 -> "Choose 2–12 focus intervals."
                    else -> null
                }
                if (error == null) onSave(enabledState, focusState, shortState, longState, intervalsState)
            }) { Text("Save", color = ScheduleGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumericField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit).take(3)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}

@Composable
private fun BlockedAppsDialog(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { installedLaunchableApps(context) }
    var selection by rememberSaveable { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule blocked apps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Only apps you select here are added during this schedule. The global Blocklist still applies. Enable StudyMode's Accessibility service to enforce app blocking.",
                    color = ScheduleMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                selection = if (app.packageName in selection) selection - app.packageName
                                else selection + app.packageName
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = app.packageName in selection,
                                onCheckedChange = { checked ->
                                    selection = if (checked) selection + app.packageName else selection - app.packageName
                                },
                            )
                            Column {
                                Text(app.label)
                                Text(app.packageName, color = ScheduleMuted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selection) }) { Text("Save", color = ScheduleGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScheduleProgressScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    val progress = remember(weekOffset) { store.scheduleProgress(weekOffset) }
    val reports = progress.weeklyReport
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScheduleInk)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Back", color = ScheduleText) }
            Text("Schedule Progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = { weekOffset -= 1 }) { Text("‹ Previous", color = ScheduleGreen) }
            Text(
                weekRange(reports.firstOrNull()?.dayStartMillis ?: System.currentTimeMillis()),
                color = ScheduleMuted,
            )
            TextButton(
                onClick = { weekOffset += 1 },
                enabled = weekOffset < 0,
            ) {
                Text("Next ›", color = if (weekOffset < 0) ScheduleGreen else ScheduleMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressMetric("Schedules", progress.totalSchedules.toString(), Modifier.weight(1f))
            ProgressMetric("Enabled", progress.enabledSchedules.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressMetric("Completed sessions", progress.completedFocusSessions.toString(), Modifier.weight(1f))
            ProgressMetric("Focus time", formatDuration(progress.totalFocusSeconds), Modifier.weight(1f))
        }
        EditorCard {
            Text("Scheduled vs focused", fontWeight = FontWeight.SemiBold)
            Text("Scheduled: ${formatDuration(progress.scheduledSeconds)}", color = ScheduleMuted)
            Text("Completed focus: ${formatDuration(progress.totalFocusSeconds)}", color = ScheduleGreen)
            val ratio = if (progress.scheduledSeconds == 0L) 0f
                else (progress.totalFocusSeconds.toFloat() / progress.scheduledSeconds).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = ScheduleGreen,
                trackColor = ScheduleElevated,
            )
        }
        EditorCard {
            Text("Weekly focus", fontWeight = FontWeight.SemiBold)
            if (reports.all { it.elapsedSeconds == 0L }) {
                Text("No completed focus sessions this week yet.", color = ScheduleMuted)
            } else {
                WeeklyBarChart(reports)
            }
        }
        Text("Schedule history", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (reports.all { it.sessionCount == 0 && it.scheduledSeconds == 0L }) {
            Text("No schedule or focus activity for this week.", color = ScheduleMuted)
        } else {
            reports.forEach { day ->
                EditorCard {
                    Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(day.dayStartMillis)), fontWeight = FontWeight.SemiBold)
                    Text("Scheduled ${formatDuration(day.scheduledSeconds)} · Focused ${formatDuration(day.elapsedSeconds)} · ${day.sessionCount} sessions", color = ScheduleMuted)
                }
            }
        }
    }
}

@Composable
private fun ProgressMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    EditorCard(modifier = modifier) {
        Text(value, color = ScheduleGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(title, color = ScheduleMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun WeeklyBarChart(reports: List<DailyFocusReport>) {
    val maxSeconds = reports.maxOfOrNull { it.elapsedSeconds }?.coerceAtLeast(1L) ?: 1L
    Row(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        reports.forEach { report ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .width(20.dp)
                        .height((100f * report.elapsedSeconds / maxSeconds).coerceAtLeast(if (report.elapsedSeconds > 0) 6f else 2f).dp)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (report.elapsedSeconds > 0) ScheduleGreen else ScheduleElevated),
                )
                Text(dayShort(report.dayStartMillis), color = ScheduleMuted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun startOfDay(timeMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timeMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun startOfWeek(timeMillis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = timeMillis
        while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
    }.timeInMillis

private fun formatTime(minute: Int): String =
    DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(
        Date(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
        }.timeInMillis),
    )

private fun repeatDaysLabel(days: Set<Int>): String {
    val ordered = WEEKDAYS.filter { it in days }
        .mapNotNull { Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, it) }
            .getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()) }
    return if (ordered.size == 7) "Every day" else ordered.joinToString(" · ")
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds.coerceAtLeast(0) / 60
    val hours = minutes / 60
    return if (hours > 0) "${hours}h ${minutes % 60}m" else "${minutes}m"
}

private fun weekRange(startMillis: Long): String {
    val start = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(startMillis))
    val end = Calendar.getInstance().apply {
        timeInMillis = startMillis
        add(Calendar.DAY_OF_YEAR, 6)
    }.time
    return "$start – ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(end)}"
}

private fun dayShort(startMillis: Long): String =
    SimpleDateFormat("EE", Locale.getDefault()).format(Date(startMillis))
