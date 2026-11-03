package com.studymode.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val IconBg = Color(0xFF16281F)
private val AccentGreen = Color(0xFF22C55E)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)

@Composable
fun SleepNotificationsSettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var notifyBedtime by remember { mutableStateOf(prefs.notifyBedtime) }
    var notifyWindDown by remember { mutableStateOf(prefs.notifyWindDown) }
    var notifyWakeup by remember { mutableStateOf(prefs.notifyWakeup) }
    var notifySleepSummary by remember { mutableStateOf(prefs.notifySleepSummary) }
    var notifyTipsInsights by remember { mutableStateOf(prefs.notifyTipsInsights) }
    var notifyMarketing by remember { mutableStateOf(prefs.notifyMarketing) }

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CardBg)
                        .border(1.dp, CardBorder, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("←", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Notifications",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Control sleep related alerts",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WindDownToggleRow(
                    icon = "🔔",
                    title = "Bedtime reminder",
                    subtitle = "Notify when it's time for bed",
                    checked = notifyBedtime,
                    onCheckedChange = { notifyBedtime = it },
                )

                WindDownToggleRow(
                    icon = "✨",
                    title = "Wind-down start reminder",
                    subtitle = "Alert before wind-down session starts",
                    checked = notifyWindDown,
                    onCheckedChange = { notifyWindDown = it },
                )

                WindDownToggleRow(
                    icon = "⏰",
                    title = "Wake-up alarm",
                    subtitle = "Sound alarm at wake-up time",
                    checked = notifyWakeup,
                    onCheckedChange = { notifyWakeup = it },
                )

                WindDownToggleRow(
                    icon = "☀️",
                    title = "Sleep summary",
                    subtitle = "Show sleep summary in the morning",
                    checked = notifySleepSummary,
                    onCheckedChange = { notifySleepSummary = it },
                )

                WindDownToggleRow(
                    icon = "💡",
                    title = "Tips and insights",
                    subtitle = "Receive helpful sleep tips",
                    checked = notifyTipsInsights,
                    onCheckedChange = { notifyTipsInsights = it },
                )

                WindDownToggleRow(
                    icon = "📣",
                    title = "Marketing and updates",
                    subtitle = "Product updates and new features",
                    checked = notifyMarketing,
                    onCheckedChange = { notifyMarketing = it },
                )
            }

            Spacer(Modifier.height(28.dp))
        }

        // Save Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Button(
                onClick = {
                    val updated = prefs.copy(
                        notifyBedtime = notifyBedtime,
                        notifyWindDown = notifyWindDown,
                        notifyWakeup = notifyWakeup,
                        notifySleepSummary = notifySleepSummary,
                        notifyTipsInsights = notifyTipsInsights,
                        notifyMarketing = notifyMarketing,
                    )
                    store.saveSleepPreferences(updated)

                    // Also sync sleep schedule reminder flags
                    val sched = store.sleepSchedule()
                    store.saveSleepSchedule(
                        sched.copy(
                            reminderEnabled = notifyBedtime,
                            wakeupReminderEnabled = notifyWakeup,
                        )
                    )

                    SleepAlarms.reschedule(context)

                    Toast.makeText(context, "Notification preferences saved", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(
                    text = "Save",
                    color = Color(0xFF06150D),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
