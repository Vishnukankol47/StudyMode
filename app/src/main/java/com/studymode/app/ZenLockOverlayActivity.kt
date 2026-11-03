package com.studymode.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Ultra-strict fullscreen lockout activity for Zen Mode / Exam Lockdown.
 * Prevents navigation away from deep study and only permits essential emergency access.
 */
class ZenLockOverlayActivity : ComponentActivity() {

    private lateinit var store: LocalStudyStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LocalStudyStore(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Intercept back button - prevent dismissal
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(this@ZenLockOverlayActivity, "Zen Mode is active. Focus on your study.", Toast.LENGTH_SHORT).show()
            }
        })

        setContent {
            ZenLockScreen(
                store = store,
                onEmergencyCall = {
                    // Open the real dialer so user can call anyone (not just emergency numbers)
                    val telecom = try {
                        getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                    } catch (_: Exception) { null }
                    val dialerPkg = telecom?.defaultDialerPackage
                        ?: "com.samsung.android.dialer"
                    val dialIntent = packageManager.getLaunchIntentForPackage(dialerPkg)
                        ?: Intent(Intent.ACTION_DIAL).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(dialIntent)
                    } catch (_: Exception) {
                        // Fallback: generic dialer
                        try {
                            startActivity(Intent(Intent.ACTION_DIAL).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            })
                        } catch (_: Exception) {}
                    }
                },
                onExitZenMode = {
                    store.saveZenModeAsSession()  // Save partial session to Focus Analysis
                    store.cancelZenMode()
                    Toast.makeText(this, "Zen Mode ended early.", Toast.LENGTH_SHORT).show()
                    finish()
                },
                onComplete = {
                    store.saveZenModeAsSession()  // Save completed session to Focus Analysis
                    store.cancelZenMode()
                    Toast.makeText(this, "🎉 Zen Session Completed! Great discipline.", Toast.LENGTH_LONG).show()
                    finish()
                }
            )
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, ZenLockOverlayActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun ZenLockScreen(
    store: LocalStudyStore,
    onEmergencyCall: () -> Unit,
    onExitZenMode: () -> Unit,
    onComplete: () -> Unit,
) {
    var remainingMillis by remember { mutableLongStateOf(store.zenModeRemainingMillis()) }

    // Live countdown ticker
    LaunchedEffect(Unit) {
        while (true) {
            val remaining = store.zenModeRemainingMillis()
            remainingMillis = remaining
            if (remaining <= 0) {
                onComplete()
                break
            }
            delay(1000L)
        }
    }

    val minutes = (remainingMillis / 60_000L)
    val seconds = (remainingMillis % 60_000L) / 1000L
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    // Breathing pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "zenPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // 60-Second Hold State for Emergency Exit
    var isHolding by remember { mutableStateOf(false) }
    var holdSecondsElapsed by remember { mutableFloatStateOf(0f) }
    val totalRequiredHoldSeconds = 60f

    LaunchedEffect(isHolding) {
        if (isHolding) {
            while (isHolding && holdSecondsElapsed < totalRequiredHoldSeconds) {
                delay(100L)
                holdSecondsElapsed += 0.1f
            }
            if (holdSecondsElapsed >= totalRequiredHoldSeconds) {
                onExitZenMode()
            }
        } else {
            holdSecondsElapsed = 0f
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF030704),
                        Color(0xFF07140B),
                        Color(0xFF050B07),
                    )
                )
            )
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F2618))
                    .border(1.dp, Color(0xFF1B4D2E), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4ADE80))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ZEN MODE • EXAM LOCKOUT",
                    color = Color(0xFF4ADE80),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Center Breathing Ring and Timer
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(scale),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow aura
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF22C55E).copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Ring border
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color(0xFF22C55E).copy(alpha = 0.4f), CircleShape)
                        .background(Color(0xFF0B1B10)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = timeFormatted,
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "REMAINING",
                            color = Color(0xFF8BA393),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Mindful affirmation
            Text(
                text = "“Deep focus creates deep understanding.”",
                color = Color(0xFFE2E8F0),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Notifications, feeds, and apps are strictly held outside. Put your phone down and immerse in your study.",
                color = Color(0xFF7E9A88),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Emergency Phone Call Button
            Button(
                onClick = onEmergencyCall,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF14291D)),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E402B)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.ic_phone),
                        contentDescription = "Phone Call",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Phone Call",
                        color = Color(0xFF4ADE80),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 60-Second Hold Button for Emergency Exit
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            isHolding = true
                            do {
                                val event = awaitPointerEvent()
                                val isStillDown = event.changes.any { it.pressed }
                                if (!isStillDown) {
                                    isHolding = false
                                    break
                                }
                            } while (true)
                        }
                    },
                shape = RoundedCornerShape(14.dp),
                color = if (isHolding) Color(0xFF2A1515) else Color(0xFF171212),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isHolding) Color(0xFFEF4444) else Color(0xFF332020)
                ),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val progress = (holdSecondsElapsed / totalRequiredHoldSeconds).coerceIn(0f, 1f)
                    val remainingHold = (totalRequiredHoldSeconds - holdSecondsElapsed).toInt().coerceAtLeast(0)

                    Text(
                        text = if (isHolding) "Keep holding: ${remainingHold}s remaining..." else "Hold 60s for Emergency Abort",
                        color = if (isHolding) Color(0xFFFCA5A5) else Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )

                    if (isHolding) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFFEF4444),
                            trackColor = Color(0xFF451A1A),
                        )
                    }
                }
            }
        }
    }
}
