package com.studymode.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val DialogBg = Color(0xFF101912)
private val CardBorder = Color(0xFF1E3A26)
private val MintAccent = Color(0xFF4ADE80)
private val MintTrack = Color(0xFF193322)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF8BA393)

/**
 * Cooldown reflection dialog that intercepts unblocking / pausing actions,
 * forcing the user to pause for 15-60 seconds before allowing an unblock.
 */
@Composable
fun StrictUnblockDelayDialog(
    totalSeconds: Int = 30,
    targetActionDescription: String = "pause blocking",
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit
) {
    var secondsRemaining by remember { mutableIntStateOf(totalSeconds.coerceAtLeast(5)) }
    val progress = (secondsRemaining.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "unblock_cooldown")

    LaunchedEffect(Unit) {
        while (secondsRemaining > 0) {
            delay(1_000L)
            secondsRemaining -= 1
        }
    }

    val isUnlocked = secondsRemaining == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("🧘", fontSize = 22.sp)
                Text(
                    text = "Pause & Reflect",
                    color = TextWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Is opening this app intentional, or an unconscious habit loop? Take a slow, deep breath while the cooldown completes.",
                    color = TextMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                // Animated Circular Countdown
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(100.dp)) {
                        val strokeW = 8.dp.toPx()
                        // Background track
                        drawCircle(
                            color = MintTrack,
                            radius = (size.minDimension - strokeW) / 2f,
                            style = Stroke(width = strokeW)
                        )
                        // Active countdown sweep
                        drawArc(
                            color = if (isUnlocked) MintAccent else Color(0xFF22C55E),
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isUnlocked) "✓" else "${secondsRemaining}s",
                            color = if (isUnlocked) MintAccent else TextWhite,
                            fontSize = if (isUnlocked) 32.sp else 26.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isUnlocked) {
                            Text(
                                text = "cooldown",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                if (isUnlocked) {
                    Text(
                        text = "🌿 Cooldown complete. If you still need to $targetActionDescription, you may proceed.",
                        color = MintAccent,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "🔒 Unlocking button is locked during cooldown.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary positive button: stay focused
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MintAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stay Focused & Keep Blocked", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Secondary unblock button: locked until countdown reaches 0
                OutlinedButton(
                    onClick = onConfirmed,
                    enabled = isUnlocked,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, if (isUnlocked) Color(0xFF285436) else Color(0xFF212E24)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isUnlocked) MintAccent else Color(0xFF5A6E60),
                        disabledContentColor = Color(0xFF4A5A4F)
                    )
                ) {
                    Text(
                        text = if (isUnlocked) "Proceed to Unblock" else "Unlocks in ${secondsRemaining}s",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        },
        dismissButton = null
    )
}

/**
 * Settings Card for configuring the Strict Unblock Delay.
 */
@Composable
fun StrictUnblockSettingsCard(
    store: LocalStudyStore,
    onSettingsChanged: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEnabled by remember { mutableStateOf(store.isStrictUnblockDelayEnabled()) }
    var delaySecs by remember { mutableIntStateOf(store.strictUnblockDelaySeconds()) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF111C15),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🛡️", fontSize = 18.sp)
                    Column {
                        Text(
                            text = "Strict Unblock Delay",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cooldown timer before pausing blocks",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { checked ->
                        isEnabled = checked
                        store.setStrictUnblockDelayEnabled(checked)
                        onSettingsChanged()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = MintAccent,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = Color(0xFF1B281F)
                    )
                )
            }

            if (isEnabled) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Cooldown Duration: ${delaySecs}s",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60).forEach { seconds ->
                        val isSelected = delaySecs == seconds
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    delaySecs = seconds
                                    store.setStrictUnblockDelaySeconds(seconds)
                                    onSettingsChanged()
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MintAccent else Color(0xFF192A1F),
                            border = BorderStroke(1.dp, if (isSelected) MintAccent else Color(0xFF263D2E))
                        ) {
                            Text(
                                text = "${seconds}s",
                                color = if (isSelected) Color.Black else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
