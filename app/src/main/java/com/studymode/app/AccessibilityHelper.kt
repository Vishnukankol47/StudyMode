package com.studymode.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun hasOverlayPermission(context: Context): Boolean =
    Settings.canDrawOverlays(context)

internal fun openUsageAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppInfoSettings(context)
    }
}

internal fun openOverlaySettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppInfoSettings(context)
    }
}

internal fun openAppInfoSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}

internal fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppInfoSettings(context)
    }
}

internal fun isBlockingServiceEnabled(context: Context): Boolean = false

/**
 * Dialog shown upon opening the app to ensure safe app-blocking permissions
 * (Usage Access and Display Over Other Apps) are active, and to warn if
 * Accessibility Service is enabled (which triggers BHIM / banking alerts).
 */
@Composable
internal fun AppBlockingPermissionsDialog(
    usageAccessEnabled: Boolean,
    overlayEnabled: Boolean,
    accessibilityEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
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
                Column {
                    Text(
                        text = "App Blocker Setup",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = "Safe Focus Enforcement (UPI Compatible)",
                        color = Color(0xFF69ED70),
                        fontSize = 12.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // 2. Usage Access Card (UPI & Banking Safe)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (usageAccessEnabled) Color(0xFF132215) else Color(0xFF1C1914),
                    border = BorderStroke(1.dp, if (usageAccessEnabled) Color(0xFF28542D) else Color(0xFF5E451C)),
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
                                Text("📊", fontSize = 16.sp)
                                Text(
                                    "Usage Access",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (usageAccessEnabled) Color(0xFF1E3821) else Color(0xFF382914),
                            ) {
                                Text(
                                    text = if (usageAccessEnabled) "Granted ✅" else "Required ❌",
                                    color = if (usageAccessEnabled) Color(0xFF69ED70) else Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Text(
                            "Detects when a blocked app opens without inspecting screen content. 100% safe for BHIM & UPI.",
                            color = Color(0xFFA4AAA4),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )

                        if (!usageAccessEnabled) {
                            Button(
                                onClick = { openUsageAccessSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF69ED70),
                                    contentColor = Color(0xFF080A08),
                                ),
                            ) {
                                Text("Grant Usage Access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // 3. Display Over Other Apps Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (overlayEnabled) Color(0xFF132215) else Color(0xFF1C1914),
                    border = BorderStroke(1.dp, if (overlayEnabled) Color(0xFF28542D) else Color(0xFF5E451C)),
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
                                Text("🪟", fontSize = 16.sp)
                                Text(
                                    "Display Over Other Apps",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (overlayEnabled) Color(0xFF1E3821) else Color(0xFF382914),
                            ) {
                                Text(
                                    text = if (overlayEnabled) "Enabled ✅" else "Required ❌",
                                    color = if (overlayEnabled) Color(0xFF69ED70) else Color(0xFFFFB74D),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }

                        Text(
                            "Displays an elegant distraction-blocking bottom sheet over YouTube, Chess, and other blocked apps.",
                            color = Color(0xFFA4AAA4),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )

                        if (!overlayEnabled) {
                            Button(
                                onClick = { openOverlaySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB74D),
                                    contentColor = Color(0xFF101312),
                                ),
                            ) {
                                Text("Enable 'Display Over Other Apps'", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (usageAccessEnabled && overlayEnabled && !accessibilityEnabled) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF69ED70),
                        contentColor = Color(0xFF080A08),
                    ),
                ) {
                    Text("All Set (UPI Safe) • Continue", fontWeight = FontWeight.Bold)
                }
            } else if (usageAccessEnabled && overlayEnabled) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E4030),
                        contentColor = Color(0xFFE0E0E0),
                    ),
                ) {
                    Text("Continue", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFA4AAA4)),
                ) {
                    Text("Remind Me Later")
                }
            }
        },
    )
}

@Composable
internal fun AccessibilitySetupDialog(
    onDismiss: () -> Unit,
) {
    // Zero-Accessibility mode: No accessibility service setup needed.
}
