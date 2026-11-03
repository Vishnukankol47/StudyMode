package com.studymode.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val IconBg = Color(0xFF16281F)
private val AccentGreen = Color(0xFF22C55E)
private val AccentLightGreen = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)

enum class SleepSettingsPanel {
    HUB,
    SCHEDULE,
    WIND_DOWN,
    SOUNDS,
    TRACKING,
    DISPLAY,
    NOTIFICATIONS,
    DATA_PRIVACY,
    ADVANCED,
    CALMING_DISPLAY,
}

@Composable
fun SleepSettingsHubScreen(
    store: LocalStudyStore,
    onNavigate: (SleepSettingsPanel) -> Unit,
    onBack: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPlan: () -> Unit = {},
    onNavigateToBlocks: () -> Unit = {},
) {
    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        // Main scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // Header with Back button, Title & Night illustration
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
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

                    Spacer(Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Sleep Settings",
                            color = TextWhite,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Customize your sleep experience",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                }

                // Night sky with moon, stars and gear graphic illustration
                SleepSettingsHeaderArt(modifier = Modifier.size(72.dp, 48.dp))
            }

            Spacer(Modifier.height(4.dp))

            // 8 Navigation rows
            val items = listOf(
                SleepHubItemData(
                    panel = SleepSettingsPanel.SCHEDULE,
                    icon = "⏰",
                    title = "Sleep Schedule",
                    subtitle = "Set your bedtime and wake-up time",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.WIND_DOWN,
                    icon = "🍃",
                    title = "Wind-down Settings",
                    subtitle = "Configure pre-sleep routine",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.SOUNDS,
                    icon = "🎵",
                    title = "Sleep Sounds",
                    subtitle = "Manage sounds and audio preferences",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.TRACKING,
                    icon = "🔔",
                    title = "Tracking & Reminders",
                    subtitle = "Sleep tracking and smart reminders",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.DISPLAY,
                    icon = "📱",
                    title = "Display & Device",
                    subtitle = "Screen, brightness and device settings",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.NOTIFICATIONS,
                    icon = "🔔",
                    title = "Notifications",
                    subtitle = "Control sleep related alerts",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.DATA_PRIVACY,
                    icon = "🛡️",
                    title = "Data & Privacy",
                    subtitle = "Manage your sleep data",
                ),
                SleepHubItemData(
                    panel = SleepSettingsPanel.ADVANCED,
                    icon = "⚙️",
                    title = "Advanced Settings",
                    subtitle = "Additional options",
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items.forEach { item ->
                    SleepSettingsHubRow(
                        icon = item.icon,
                        title = item.title,
                        subtitle = item.subtitle,
                        onClick = { onNavigate(item.panel) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        // Bottom Navigation Bar matching mockup: Home, Plan, Sleep (active), Blocks
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF09120D),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF14231A)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavItem(icon = "🏠", label = "Home", isSelected = false, onClick = onNavigateToHome)
                BottomNavItem(icon = "📅", label = "Plan", isSelected = false, onClick = onNavigateToPlan)
                BottomNavItem(icon = "🌙", label = "Sleep", isSelected = true, onClick = onBack)
                BottomNavItem(icon = "🚫", label = "Blocks", isSelected = false, onClick = onNavigateToBlocks)
            }
        }
    }
}

private data class SleepHubItemData(
    val panel: SleepSettingsPanel,
    val icon: String,
    val title: String,
    val subtitle: String,
)

@Composable
private fun SleepSettingsHubRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(IconBg)
                        .border(1.dp, Color(0xFF1E382A), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(icon, fontSize = 20.sp)
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = title,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextMuted,
                        fontSize = 11.5.sp,
                    )
                }
            }

            Text(
                text = "›",
                color = TextMuted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SleepSettingsHeaderArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Dark pill background for illustration
        drawRoundRect(
            color = Color(0xFF112219),
            topLeft = Offset(0f, 0f),
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
        )

        // Crescent moon
        val moonRadius = 11.dp.toPx()
        val moonCenter = Offset(w * 0.62f, h * 0.45f)
        drawCircle(
            color = Color(0xFFE8F5E9),
            radius = moonRadius,
            center = moonCenter,
        )
        drawCircle(
            color = Color(0xFF112219),
            radius = moonRadius * 0.85f,
            center = Offset(moonCenter.x - moonRadius * 0.45f, moonCenter.y - moonRadius * 0.25f),
        )

        // Soft cloud overlay
        drawCircle(color = Color(0x334ADE80), radius = 10.dp.toPx(), center = Offset(w * 0.35f, h * 0.65f))
        drawCircle(color = Color(0x444ADE80), radius = 14.dp.toPx(), center = Offset(w * 0.50f, h * 0.70f))
        drawCircle(color = Color(0x334ADE80), radius = 11.dp.toPx(), center = Offset(w * 0.68f, h * 0.72f))

        // Small stars
        drawCircle(color = AccentLightGreen, radius = 1.5.dp.toPx(), center = Offset(w * 0.22f, h * 0.30f))
        drawCircle(color = Color.White, radius = 1.8.dp.toPx(), center = Offset(w * 0.38f, h * 0.22f))
        drawCircle(color = AccentLightGreen, radius = 1.2.dp.toPx(), center = Offset(w * 0.82f, h * 0.25f))

        // Gear icon badge on the right
        drawCircle(
            color = AccentGreen,
            radius = 6.dp.toPx(),
            center = Offset(w * 0.84f, h * 0.68f),
        )
        drawCircle(
            color = Color(0xFF07120D),
            radius = 2.5.dp.toPx(),
            center = Offset(w * 0.84f, h * 0.68f),
        )
    }
}

@Composable
private fun BottomNavItem(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) {
        Text(
            text = icon,
            fontSize = 18.sp,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) AccentGreen else TextMuted,
        )
        if (isSelected) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(AccentGreen),
            )
        }
    }
}
