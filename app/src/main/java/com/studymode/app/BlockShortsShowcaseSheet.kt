package com.studymode.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Slide 9 of Pro Benefits: Block Shorts & Reels.
 * Displays phone mockup illustrating short-form video blocking on YouTube and Instagram.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockShortsShowcaseSheet(onDismiss: () -> Unit) {
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
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                // Top tag pill
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
                                        Text("🚫", fontSize = 11.sp)
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "Micro-Blocker",
                                            color = Color(0xFF69ED70),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                // Center: App Icons with Shield
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_short_video),
                                            contentDescription = "YouTube Shorts",
                                            modifier = Modifier.size(52.dp).clip(CircleShape),
                                            contentScale = ContentScale.Fit,
                                        )
                                        Text("⚡", fontSize = 18.sp)
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_reel_video),
                                            contentDescription = "Instagram Reels",
                                            modifier = Modifier.size(52.dp).clip(CircleShape),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFF2B1619),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFFE53935)),
                                    ) {
                                        Text(
                                            text = "Feed Scrolling Blocked",
                                            color = Color(0xFFFF7B7B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }

                                // Subtext inside phone mockup
                                Text(
                                    text = "Long-form study videos allowed",
                                    color = Color(0xFF8E998E),
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title & Subtitle
            Text(
                "Block Shorts & Reels",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Block Shorts & Reels completely for distraction-free days.",
                fontSize = 14.sp,
                color = Color(0xFFA5B2A5),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 7 Pagination Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 7) {
                    val isCurrent = i == 2 // 3rd dot
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
