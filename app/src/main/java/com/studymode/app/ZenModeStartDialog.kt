package com.studymode.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ZenModeStartDialog(
    store: LocalStudyStore,
    onDismiss: () -> Unit,
    onZenStarted: () -> Unit,
) {
    val context = LocalContext.current
    var selectedMinutes by remember { mutableIntStateOf(45) }
    val presets = listOf(15, 30, 45, 60, 90, 120)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF09120C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1B3D26)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF132F1D))
                        .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_shield),
                        contentDescription = "Zen Mode",
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Zen Mode • Exam Lockout",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Ultra-strict lockdown. Only emergency calls and essential study are allowed. Normal unblock bypasses are disabled.",
                    color = Color(0xFF8BA393),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Duration Selector
                Text(
                    text = "Select Lockout Duration",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.take(3).forEach { mins ->
                        val isSelected = selectedMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1B4D2E) else Color(0xFF111E16))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF4ADE80) else Color(0xFF1E3326),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedMinutes = mins }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${mins}m",
                                color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.drop(3).forEach { mins ->
                        val isSelected = selectedMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF1B4D2E) else Color(0xFF111E16))
                                .border(
                                    1.dp,
                                    if (isSelected) Color(0xFF4ADE80) else Color(0xFF1E3326),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedMinutes = mins }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${mins}m",
                                color = if (isSelected) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Warning Note
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF26180E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB45309).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⚠️ Early emergency exit requires holding down a confirmation button for 60 continuous seconds.",
                            color = Color(0xFFFDE68A),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Start Button
                Button(
                    onClick = {
                        store.startZenMode(selectedMinutes)
                        ZenLockOverlayActivity.start(context)
                        onZenStarted()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Begin Zen Lockout (${selectedMinutes}m)",
                        color = Color(0xFF031407),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        }
    }
}
