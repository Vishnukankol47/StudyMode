package com.studymode.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Slide 7 of Pro Benefits Carousel: Block Phone home screen.
 * Displays phone mockup with 24:07 timer dial and home screen lock indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockHomeScreenShowcaseSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141614),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF424742)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Yellow Presentation Frame
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                shape = RoundedCornerShape(28.dp),
                color = Color(0xFFF9CF45),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Dark Phone Mockup Frame
                    Surface(
                        modifier = Modifier
                            .width(230.dp)
                            .height(310.dp)
                            .padding(top = 18.dp),
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = Color(0xFF101411),
                        border = BorderStroke(3.dp, Color(0xFF2C302C)),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Phone Screen Content
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Top status bar simulation & Tag pill
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E2820),
                                    border = BorderStroke(1.dp, Color(0xFF2F4532)),
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text("⚡", fontSize = 11.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Ultra Focus",
                                            color = Color(0xFF69ED70),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Center: Dial with 24:07
                                Box(
                                    modifier = Modifier.size(150.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    // Circular Progress Track
                                    Canvas(modifier = Modifier.size(140.dp)) {
                                        drawCircle(
                                            color = Color(0xFF1A2A1E),
                                            style = Stroke(width = 8.dp.toPx()),
                                        )
                                        drawArc(
                                            brush = Brush.sweepGradient(
                                                listOf(Color(0xFF69ED70), Color(0xFF2ECC71), Color(0xFF69ED70)),
                                            ),
                                            startAngle = -90f,
                                            sweepAngle = 260f,
                                            useCenter = false,
                                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "24:07",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 30.sp,
                                        )
                                        Text(
                                            "FOCUSING",
                                            color = Color(0xFF69ED70),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 1.sp,
                                        )
                                    }
                                }

                                // Home Screen Blocked banner over bottom
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF181D19),
                                    border = BorderStroke(1.dp, Color(0xFFE53935)),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Text("🚫", fontSize = 11.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "Home screen locked",
                                            color = Color(0xFFFF7B7B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title & Subtitle
            Text(
                "Block Phone home screen",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Home screen will be blocked during focus",
                fontSize = 14.sp,
                color = Color(0xFFA5B2A5),
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 7 Pagination Dots (7th / last dot selected)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 7) {
                    val isCurrent = i == 6
                    Box(
                        modifier = Modifier
                            .size(if (isCurrent) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (isCurrent) Color.White else Color(0xFF555B55)),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Got it! Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF111411),
                ),
            ) {
                Text("Got it!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
