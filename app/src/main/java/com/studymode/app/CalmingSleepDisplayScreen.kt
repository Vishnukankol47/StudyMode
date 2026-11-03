package com.studymode.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CalmingSleepDisplayScreen(
    onDismiss: () -> Unit,
) {
    BackHandler {
        onDismiss()
    }

    // Live clock
    var currentTimeStr by remember { mutableStateOf(SimpleDateFormat("h:mm a", Locale.US).format(Date())) }
    var currentDateStr by remember { mutableStateOf(SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date())) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTimeStr = SimpleDateFormat("h:mm a", Locale.US).format(Date())
            currentDateStr = SimpleDateFormat("EEEE, MMM d", Locale.US).format(Date())
        }
    }

    // Breathing pulse animation for moon glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020704))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        // Starry night canvas background
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Crescent moon with breathing glow
            val moonCenter = Offset(w * 0.5f, h * 0.32f)
            val moonR = 36.dp.toPx()

            // Outer glow
            drawCircle(
                color = Color(0xFF22C55E).copy(alpha = glowAlpha * 0.2f),
                radius = moonR * 1.8f,
                center = moonCenter,
            )

            // Moon body
            drawCircle(
                color = Color(0xFFE8F5E9).copy(alpha = 0.85f),
                radius = moonR,
                center = moonCenter,
            )
            // Moon cut
            drawCircle(
                color = Color(0xFF020704),
                radius = moonR * 0.9f,
                center = Offset(moonCenter.x - moonR * 0.45f, moonCenter.y - moonR * 0.25f),
            )

            // Ambient background stars
            val stars = listOf(
                Offset(w * 0.15f, h * 0.15f) to 1.5.dp.toPx(),
                Offset(w * 0.25f, h * 0.25f) to 1.8.dp.toPx(),
                Offset(w * 0.82f, h * 0.18f) to 2.0.dp.toPx(),
                Offset(w * 0.75f, h * 0.35f) to 1.4.dp.toPx(),
                Offset(w * 0.18f, h * 0.45f) to 1.6.dp.toPx(),
                Offset(w * 0.88f, h * 0.52f) to 1.8.dp.toPx(),
                Offset(w * 0.12f, h * 0.68f) to 1.5.dp.toPx(),
                Offset(w * 0.85f, h * 0.72f) to 1.4.dp.toPx(),
                Offset(w * 0.30f, h * 0.82f) to 1.6.dp.toPx(),
                Offset(w * 0.70f, h * 0.85f) to 1.8.dp.toPx(),
            )

            stars.forEach { (pos, r) ->
                drawCircle(
                    color = Color(0xFF4ADE80).copy(alpha = glowAlpha),
                    radius = r,
                    center = pos,
                )
            }
        }

        // Center Content: Clock & Calming Note
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 180.dp),
        ) {
            Text(
                text = currentTimeStr,
                color = Color(0xFFE8F5E9).copy(alpha = 0.9f),
                fontSize = 44.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = 2.sp,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = currentDateStr,
                color = Color(0xFF8BA396).copy(alpha = 0.7f),
                fontSize = 14.sp,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Rest well · StudyMode is protecting your sleep",
                color = Color(0xFF22C55E).copy(alpha = 0.75f),
                fontSize = 12.sp,
            )
        }

        // Tap to exit footer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Tap anywhere to exit",
                color = Color(0xFF8BA396).copy(alpha = 0.4f),
                fontSize = 11.sp,
            )
        }
    }
}
