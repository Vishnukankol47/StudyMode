package com.studymode.app

import android.content.Intent
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
private val DangerRed = Color(0xFFEF4444)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)

@Composable
fun DataPrivacySettingsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { store.sleepPreferences() }

    var storeLocally by remember { mutableStateOf(prefs.storeLocally) }
    var backupToCloud by remember { mutableStateOf(prefs.backupToCloud) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showCloudBackupDialog by remember { mutableStateOf(false) }

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
                        text = "Data & Privacy",
                        color = TextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Manage your sleep data",
                        color = TextMuted,
                        fontSize = 12.sp,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Store sleep data locally
                WindDownToggleRow(
                    icon = "💾",
                    title = "Store sleep data locally",
                    subtitle = "Keep your data on this device",
                    checked = storeLocally,
                    onCheckedChange = { storeLocally = it },
                )

                // Backup to cloud
                WindDownToggleRow(
                    icon = "☁️",
                    title = "Backup to cloud",
                    subtitle = "Save your data to your account",
                    checked = backupToCloud,
                    onCheckedChange = { checked ->
                        backupToCloud = checked
                        if (checked) {
                            showCloudBackupDialog = true
                        }
                    },
                )

                // Export data row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            try {
                                val jsonExport = store.exportSleepDataJson()
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_SUBJECT, "StudyMode Sleep Data Export")
                                    putExtra(Intent.EXTRA_TEXT, jsonExport)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Export Sleep Data"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Export error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(IconBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("📤", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Export data", color = TextWhite, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(1.dp))
                                Text("Export your sleep data", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Text("›", color = TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Delete all data row
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, Color(0xFF3B1D1D), RoundedCornerShape(16.dp))
                        .clickable { showDeleteConfirmDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2E1212)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("🗑️", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Delete all data", color = DangerRed, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(1.dp))
                                Text("Remove all sleep data from device", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        Text("›", color = DangerRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Privacy Footnote Box matching mockup
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF09140E))
                    .border(1.dp, Color(0xFF14291D), RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text("ℹ️", fontSize = 16.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Your sleep data is private and used only to improve your experience. It is not shared without your consent.",
                        color = TextMuted,
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                    )
                }
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
                        storeLocally = storeLocally,
                        backupToCloud = backupToCloud,
                    )
                    store.saveSleepPreferences(updated)

                    Toast.makeText(context, "Privacy settings saved", Toast.LENGTH_SHORT).show()
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

    // Confirmation dialog for Delete All Data
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = CardBg,
            title = {
                Text("Delete All Sleep Data?", color = DangerRed, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This action cannot be undone. All your sleep logs, recorded history, schedules, and custom preferences will be permanently wiped from this device.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        store.deleteAllSleepData()
                        SleepAlarms.reschedule(context)
                        showDeleteConfirmDialog = false
                        Toast.makeText(context, "All sleep data deleted", Toast.LENGTH_LONG).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                ) {
                    Text("Delete Permanently", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            },
        )
    }

    // Info dialog for Cloud Backup
    if (showCloudBackupDialog) {
        AlertDialog(
            onDismissRequest = { showCloudBackupDialog = false },
            containerColor = CardBg,
            title = {
                Text("Cloud Backup Notice", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "StudyMode uses local-first private storage. When Cloud Backup is enabled, your exported data can be saved to your Google Drive or device backup account using Android Backup Service.",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showCloudBackupDialog = false }) {
                    Text("OK", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
        )
    }
}
