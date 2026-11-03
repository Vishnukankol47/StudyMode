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

internal fun isBlockingServiceEnabled(context: Context): Boolean {
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val expected = ComponentName(context, AppBlockingAccessibilityService::class.java).flattenToString()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

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
                // 1. Prominent Warning if Accessibility Service is turned ON (Causes BHIM Alert)
                if (accessibilityEnabled) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF2B1414),
                        border = BorderStroke(1.dp, Color(0xFF8B2525)),
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
                                        "Accessibility Service is ON",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8B8B),
                                        fontSize = 13.sp,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF4A1E1E),
                                ) {
                                    Text(
                                        text = "Triggers BHIM Alert",
                                        color = Color(0xFFFFB4B4),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }

                            Text(
                                "BHIM and Indian banking apps scan for active accessibility services and show security alerts. StudyMode blocks apps safely using Usage Access (standard Android protocol), so you can safely turn OFF Accessibility Service to fix BHIM!",
                                color = Color(0xFFFFD4D4),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )

                            Button(
                                onClick = { openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF6B6B),
                                    contentColor = Color(0xFF101312),
                                ),
                            ) {
                                Text("Turn OFF in Accessibility Settings", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                Text(
                    "StudyMode uses Usage Access and Display Over Other Apps to block distracting apps without touching your banking apps or triggering security alerts.",
                    color = Color(0xFFA4AAA4),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )

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
    val context = LocalContext.current
    val isModernAndroid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

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
                        text = if (isModernAndroid) "Enable Restricted Setting" else "Enable App Blocker",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = "Accessibility Permission Guide",
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF132215),
                    border = BorderStroke(1.dp, Color(0xFF28542D)),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "💳 Using BHIM, GPay, or UPI apps?",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF80D957),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "You do NOT need Accessibility enabled! StudyMode includes a built-in background blocker (Usage Access + Display Over Other Apps) that works safely without triggering BHIM or banking security warnings. Only enable Accessibility if you prefer instant zero-delay blocking.",
                            color = Color(0xFFBCE3BE),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    }
                }

                if (isModernAndroid) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1F1A12),
                        border = BorderStroke(1.dp, Color(0xFF4A3B22)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "🔒 Why is the setting grayed out?",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D),
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "On Android 13, 14 & 15 (especially Samsung Galaxy), Android automatically marks apps installed from APKs as 'Restricted settings' until you allow it in App Info.",
                                color = Color(0xFFD4C8B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }

                    // Step 1
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF171D17),
                        border = BorderStroke(1.dp, Color(0xFF252C25)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF69ED70), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("1", color = Color(0xFF080A08), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Allow Restricted Settings",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                            Text(
                                "1. Tap 'Open App Info' below.\n" +
                                "2. Tap the 3 dots (⋮) in the top-right corner.\n" +
                                "3. Tap 'Allow restricted settings'.\n" +
                                "4. Verify with your fingerprint or PIN.",
                                color = Color(0xFFA4AAA4),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                            Button(
                                onClick = { openAppInfoSettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF233A25),
                                    contentColor = Color(0xFF69ED70),
                                ),
                            ) {
                                Text("Open App Info (Step 1)", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Step 2
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF171D17),
                        border = BorderStroke(1.dp, Color(0xFF252C25)),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF69ED70), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("2", color = Color(0xFF080A08), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Turn On StudyMode Blocker",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                            Text(
                                "1. Tap 'Open Accessibility' below.\n" +
                                "2. Tap 'Installed apps' (or 'Downloaded services').\n" +
                                "3. Tap 'StudyMode app blocker' (now clickable!) and turn it ON.",
                                color = Color(0xFFA4AAA4),
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            )
                            Button(
                                onClick = { openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF69ED70),
                                    contentColor = Color(0xFF080A08),
                                ),
                            ) {
                                Text("Open Accessibility (Step 2)", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF131713),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "💡 Tip if 3 dots (⋮) are missing:",
                                color = Color(0xFF69ED70),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Go to Accessibility first, tap the grayed-out 'StudyMode app blocker' once so Android shows the warning, then go back to App Info. The 3 dots will now appear!",
                                color = Color(0xFFA4AAA4),
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                } else {
                    Text(
                        "StudyMode uses Android Accessibility to detect when blocked apps are opened and return you to the home screen.",
                        color = Color(0xFFA4AAA4),
                        fontSize = 13.sp,
                    )
                    Button(
                        onClick = { openAccessibilitySettings(context) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF69ED70),
                            contentColor = Color(0xFF080A08),
                        ),
                    ) {
                        Text("Open Accessibility Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF69ED70)),
            ) {
                Text("Got It", fontWeight = FontWeight.Bold)
            }
        },
    )
}
