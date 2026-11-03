package com.studymode.app

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ProfileInk = Color(0xFF0B0B0B)
private val ProfileCard = Color(0xFF171717)
private val ProfileGreen = Color(0xFF80D957)
private val ProfileMuted = Color(0xFF929292)
private val ProfileBorder = Color(0xFF282828)

@Composable
fun ProfileScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onViewAchievements: () -> Unit,
) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }
    val profile = remember(refresh) { store.profile() }
    val overview = remember(refresh) { store.focusOverview() }
    val milestones = remember(refresh) { store.focusMilestones() }
    val latest = milestones
        .filter { it.unlockedAtMillis != null }
        .maxByOrNull { it.unlockedAtMillis!! }
    val weeklySummary = remember(refresh) { store.weeklyFocusSummary() }
    val reportAvailableAt = remember(refresh) { store.firstWeeklyReportAvailableAt() }
    var showEdit by rememberSaveable { mutableStateOf(false) }
    var avatar by remember(profile.avatarUri) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(profile.avatarUri) {
        avatar = profile.avatarUri?.let { uri ->
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            store.updateProfileAvatar(uri.toString())
            refresh += 1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileInk)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        ProfileHero(
            name = profile.name,
            avatar = avatar,
            onBack = onBack,
            onSettings = onSettings,
            onChangeAvatar = { avatarPicker.launch(arrayOf("image/*")) },
        )
        Column(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        profile.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = { showEdit = true }) {
                        Text("Edit", color = ProfileGreen)
                    }
                }
                Text(
                    profile.email.ifBlank { "Private local profile · no account email" },
                    color = ProfileMuted,
                )
                Text(
                    "Focusing since ${monthYear(profile.startedAtMillis)}",
                    color = ProfileMuted,
                )
            }

            ProfileSectionCard(title = "Overview") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OverviewStat(
                        kind = ProfileIcon.BOOK,
                        value = formatFocusDuration(overview.totalSeconds),
                        label = "Total Time Focused",
                    )
                    OverviewStat(
                        kind = ProfileIcon.FLAME,
                        value = "${overview.currentStreakDays} ${if (overview.currentStreakDays == 1) "day" else "days"}",
                        label = "Current Streak",
                    )
                    OverviewStat(
                        kind = ProfileIcon.CALENDAR,
                        value = overview.completedSessions.toString(),
                        label = "Sessions",
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Your Achievements", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onViewAchievements) {
                    Text("View all  ›", color = ProfileGreen)
                }
            }
            if (latest != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    FocusBadge(
                        hours = latest.hours,
                        unlocked = true,
                        modifier = Modifier.size(132.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${latest.hours}H FOCUS", color = ProfileGreen, fontWeight = FontWeight.Bold)
                        Text(
                            "Unlocked ${dateLabel(latest.unlockedAtMillis!!)}",
                            color = ProfileMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Text("Complete focus sessions to unlock your first badge.", color = ProfileMuted)
            }

            Text("Weekly Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (System.currentTimeMillis() < reportAvailableAt) {
                ProfileSectionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ReportIcon(Modifier.size(40.dp))
                        Text(
                            "Your first weekly report will be available on ${dateLabel(reportAvailableAt)}.",
                            color = ProfileMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (weeklySummary.completedSessions == 0) {
                ProfileSectionCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ReportIcon(Modifier.size(36.dp))
                        Text("No completed focus sessions this week yet.", color = ProfileMuted)
                    }
                }
            } else {
                ProfileSectionCard(title = "Last 7 days") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        WeeklyMetric("Focus time", formatFocusDuration(weeklySummary.totalSeconds))
                        WeeklyMetric("Completed sessions", weeklySummary.completedSessions.toString())
                        WeeklyMetric("Daily average", formatFocusDuration(weeklySummary.averageSecondsPerDay))
                        WeeklyMetric("Weekly goal progress", "${weeklySummary.goalProgressPercent}%")
                    }
                }
            }
        }
    }

    if (showEdit) {
        EditProfileDialog(
            name = profile.name,
            email = profile.email,
            onDismiss = { showEdit = false },
            onSave = { name, email ->
                store.updateProfile(name, email)
                refresh += 1
                showEdit = false
            },
        )
    }
}

@Composable
private fun ProfileHero(
    name: String,
    avatar: ImageBitmap?,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onChangeAvatar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(232.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF15371B), Color(0xFF0C2212), ProfileInk),
                ),
            )
            val firstWave = Path().apply {
                moveTo(0f, size.height * 0.57f)
                cubicTo(size.width * 0.24f, size.height * 0.32f, size.width * 0.35f, size.height * 0.82f, size.width * 0.56f, size.height * 0.57f)
                cubicTo(size.width * 0.77f, size.height * 0.33f, size.width * 0.86f, size.height * 0.72f, size.width, size.height * 0.47f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(firstWave, Color(0xFF1B4924).copy(alpha = 0.78f))
            val secondWave = Path().apply {
                moveTo(0f, size.height * 0.72f)
                cubicTo(size.width * 0.2f, size.height * 0.51f, size.width * 0.36f, size.height * 0.95f, size.width * 0.58f, size.height * 0.7f)
                cubicTo(size.width * 0.78f, size.height * 0.49f, size.width * 0.91f, size.height * 0.83f, size.width, size.height * 0.63f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(secondWave, Color(0xFF102D17))
            drawPlant(size.width * 0.18f, size.height * 0.74f, 0.7f)
            drawPlant(size.width * 0.81f, size.height * 0.69f, 0.8f)
            drawPlant(size.width * 0.3f, size.height * 0.61f, 0.5f)
        }

        CircleAction("‹", Modifier.align(Alignment.TopStart).padding(16.dp), onBack)
        CircleAction("⚙", Modifier.align(Alignment.TopEnd).padding(16.dp), onSettings)

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
                .size(124.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A110B)),
            contentAlignment = Alignment.Center,
        ) {
            if (avatar != null) {
                Image(
                    bitmap = avatar,
                    contentDescription = "Profile picture",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    name.take(1).uppercase(Locale.getDefault()),
                    color = ProfileGreen,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 92.dp, bottom = 2.dp)
                .size(38.dp),
            shape = CircleShape,
            color = Color(0xFF061108),
            onClick = onChangeAvatar,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CameraGlyph(Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CircleAction(
    label: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.size(44.dp),
        shape = CircleShape,
        color = Color(0xCC071009),
        onClick = onClick,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 23.sp)
        }
    }
}

@Composable
private fun CameraGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val body = Path().apply {
            moveTo(size.width * 0.1f, size.height * 0.28f)
            lineTo(size.width * 0.34f, size.height * 0.28f)
            lineTo(size.width * 0.45f, size.height * 0.14f)
            lineTo(size.width * 0.7f, size.height * 0.14f)
            lineTo(size.width * 0.8f, size.height * 0.28f)
            lineTo(size.width * 0.92f, size.height * 0.28f)
            lineTo(size.width * 0.92f, size.height * 0.85f)
            lineTo(size.width * 0.1f, size.height * 0.85f)
            close()
        }
        drawPath(body, ProfileGreen)
        drawCircle(
            ProfileInk,
            radius = size.minDimension * 0.17f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.51f, size.height * 0.55f),
        )
        drawCircle(
            Color.White,
            radius = size.minDimension * 0.1f,
            center = androidx.compose.ui.geometry.Offset(size.width * 0.51f, size.height * 0.55f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlant(
    x: Float,
    y: Float,
    scale: Float,
) {
    val stem = Color(0xFF69C75B).copy(alpha = 0.8f)
    drawLine(stem, androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x, y - 28f * scale), 2f * scale)
    val leftLeaf = Path().apply {
        moveTo(x, y - 14f * scale)
        cubicTo(
            x - 16f * scale,
            y - 34f * scale,
            x - 11f * scale,
            y - 32f * scale,
            x - 15f * scale,
            y - 10f * scale,
        )
        close()
    }
    val rightLeaf = Path().apply {
        moveTo(x, y - 21f * scale)
        cubicTo(
            x + 16f * scale,
            y - 40f * scale,
            x + 12f * scale,
            y - 38f * scale,
            x + 16f * scale,
            y - 16f * scale,
        )
        close()
    }
    drawPath(leftLeaf, stem)
    drawPath(rightLeaf, stem.copy(alpha = 0.65f))
}

@Composable
private fun ProfileSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ProfileCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ProfileBorder),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            title?.let { Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            content()
        }
    }
}

private enum class ProfileIcon {
    BOOK,
    FLAME,
    CALENDAR,
}

@Composable
private fun OverviewStat(
    kind: ProfileIcon,
    value: String,
    label: String,
) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color(0xFF20351D)),
            contentAlignment = Alignment.Center,
        ) {
            ProfileStatIcon(kind, Modifier.size(26.dp))
        }
        Text(label, color = ProfileMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Text(value, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileStatIcon(
    kind: ProfileIcon,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        when (kind) {
            ProfileIcon.BOOK -> drawOpenBook(ProfileGreen)
            ProfileIcon.FLAME -> {
                val flame = Path().apply {
                    moveTo(size.width * 0.52f, size.height * 0.03f)
                    cubicTo(size.width * 0.58f, size.height * 0.33f, size.width * 0.9f, size.height * 0.37f, size.width * 0.82f, size.height * 0.75f)
                    cubicTo(size.width * 0.76f, size.height * 1.05f, size.width * 0.24f, size.height * 1.04f, size.width * 0.18f, size.height * 0.74f)
                    cubicTo(size.width * 0.1f, size.height * 0.46f, size.width * 0.42f, size.height * 0.37f, size.width * 0.52f, size.height * 0.03f)
                    close()
                }
                drawPath(flame, ProfileGreen)
            }
            ProfileIcon.CALENDAR -> {
                drawRoundRect(
                    ProfileGreen,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.72f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.08f),
                    style = Stroke(size.width * 0.12f),
                )
                drawLine(ProfileGreen, androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.4f), androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.4f), size.width * 0.1f)
                drawLine(ProfileGreen, androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.08f), androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.28f), size.width * 0.1f)
                drawLine(ProfileGreen, androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.08f), androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.28f), size.width * 0.1f)
            }
        }
    }
}

@Composable
private fun FocusBadge(
    hours: Int,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val cy = size.height / 2
            val radius = size.minDimension * 0.48f
            val path = Path().apply {
                for (index in 0 until 6) {
                    val angle = Math.PI / 3.0 * index - Math.PI / 2.0
                    val x = cx + radius * kotlin.math.cos(angle).toFloat()
                    val y = cy + radius * kotlin.math.sin(angle).toFloat()
                    if (index == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(
                path,
                if (unlocked) {
                    Brush.radialGradient(
                        colors = listOf(Color(0xFFB9FF9B), Color(0xFF58C950), Color(0xFF227C36)),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = radius,
                    )
                } else {
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF333333), Color(0xFF202020), Color(0xFF151515)),
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        radius = radius,
                    )
                },
            )
            drawPath(
                path,
                if (unlocked) Color.White else Color(0xFF4A4A4A),
                style = Stroke(if (unlocked) 2.dp.toPx() else 1.dp.toPx()),
            )
        }
        OpenBookGlyph(
            Modifier
                .align(Alignment.Center)
                .size(40.dp)
                .padding(bottom = 24.dp),
            tint = if (unlocked) Color(0xFF268B32) else ProfileGreen.copy(alpha = 0.22f),
        )
        Text(
            "${hours}H FOCUS",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 24.dp),
            color = if (unlocked) Color.White else Color(0xFF777777),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun OpenBookGlyph(
    modifier: Modifier = Modifier,
    tint: Color = ProfileGreen,
) {
    Canvas(modifier) { drawOpenBook(tint) }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOpenBook(color: Color) {
    val book = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.2f)
        cubicTo(size.width * 0.34f, size.height * 0.13f, size.width * 0.46f, size.height * 0.23f, size.width * 0.5f, size.height * 0.34f)
        cubicTo(size.width * 0.54f, size.height * 0.23f, size.width * 0.66f, size.height * 0.13f, size.width * 0.92f, size.height * 0.2f)
        lineTo(size.width * 0.92f, size.height * 0.83f)
        cubicTo(size.width * 0.68f, size.height * 0.76f, size.width * 0.55f, size.height * 0.79f, size.width * 0.5f, size.height * 0.9f)
        cubicTo(size.width * 0.45f, size.height * 0.79f, size.width * 0.32f, size.height * 0.76f, size.width * 0.08f, size.height * 0.83f)
        close()
    }
    drawPath(book, color)
    drawLine(
        Color.White.copy(alpha = 0.7f),
        androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.36f),
        androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.87f),
        size.minDimension * 0.035f,
    )
}

@Composable
private fun ReportIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val barWidth = size.width * 0.18f
        val gap = size.width * 0.1f
        val heights = listOf(0.35f, 0.62f, 0.88f)
        heights.forEachIndexed { index, fraction ->
            val left = index * (barWidth + gap)
            drawRoundRect(
                ProfileMuted,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height * (1f - fraction)),
                size = androidx.compose.ui.geometry.Size(barWidth, size.height * fraction),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2),
            )
        }
    }
}

@Composable
private fun WeeklyMetric(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = ProfileMuted)
        Text(value, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditProfileDialog(
    name: String,
    email: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var editedName by rememberSaveable { mutableStateOf(name) }
    var editedEmail by rememberSaveable { mutableStateOf(email) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = editedName,
                    onValueChange = { editedName = it.take(60); error = null },
                    label = { Text("Display name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = editedEmail,
                    onValueChange = { editedEmail = it.take(254); error = null },
                    label = { Text("Email (optional)") },
                    singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Profile details are stored only on this device.", color = ProfileMuted)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (editedName.isBlank()) error = "Enter a display name."
                    else if (editedEmail.isNotBlank() &&
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(editedEmail).matches()
                    ) error = "Enter a valid email address or leave it blank."
                    else onSave(editedName, editedEmail)
                },
            ) { Text("Save", color = ProfileGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AchievementsScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
) {
    val milestones = remember { store.focusMilestones() }
    val overview = remember { store.focusOverview() }
    val latest = milestones
        .filter { it.unlockedAtMillis != null }
        .maxByOrNull { it.unlockedAtMillis!! }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val collected = milestones.count { it.unlockedAtMillis != null }
    val totalHours = overview.totalSeconds / 3_600.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileInk)
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("‹  Back", color = Color.White, fontSize = 16.sp)
            }
            TextButton(
                onClick = { showHelp = true },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.textButtonColors(containerColor = ProfileCard),
            ) {
                Text("?  Help", color = ProfileMuted)
            }
        }
        Text(
            "Your Achievements",
            modifier = Modifier.padding(bottom = 16.dp),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (latest != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                FocusBadge(latest.hours, true, Modifier.size(132.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LATEST ACHIEVEMENT", color = ProfileMuted, style = MaterialTheme.typography.labelMedium)
                    Text("${latest.hours}H FOCUS", color = ProfileGreen, fontWeight = FontWeight.Bold)
                    Text("Unlocked ${dateLabel(latest.unlockedAtMillis!!)}", color = ProfileMuted)
                }
            }
        } else {
            ProfileSectionCard(Modifier.padding(bottom = 18.dp)) {
                Text("Complete a focus session to unlock your first achievement.", color = ProfileMuted)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Focus milestones", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("$collected/${FOCUS_MILESTONE_HOURS.size} collected", color = ProfileMuted)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(milestones.take(15), key = { it.hours }) { milestone ->
                MilestoneTile(milestone, totalHours)
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    MilestoneTile(milestones[15], totalHours, Modifier.width(112.dp))
                }
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("How achievements work") },
            text = {
                Text(
                    "Completed focus sessions add to your cumulative focus time. Each milestone unlocks automatically when your completed sessions reach its hour target. Paused sessions do not count until completed; sessions ended early are not included.",
                    color = ProfileMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Close", color = ProfileGreen) }
            },
        )
    }
}

@Composable
private fun MilestoneTile(
    milestone: FocusMilestone,
    totalHours: Double,
    modifier: Modifier = Modifier,
) {
    val unlocked = milestone.unlockedAtMillis != null
    val progress = (totalHours / milestone.hours).toFloat().coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FocusBadge(
            hours = milestone.hours,
            unlocked = unlocked,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        if (unlocked) {
            Text(
                "on ${dateLabel(milestone.unlockedAtMillis!!)}",
                color = ProfileMuted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .height(4.dp)
                    .clip(CircleShape),
                color = ProfileGreen,
                trackColor = Color(0xFF353535),
            )
            Text(
                "${formatFocusDuration((totalHours * 3_600).toLong())} / ${milestone.hours}h focused",
                color = ProfileMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

private fun formatFocusDuration(totalSeconds: Long): String {
    val totalMinutes = totalSeconds.coerceAtLeast(0) / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun dateLabel(timeMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timeMillis))

private fun monthYear(timeMillis: Long): String =
    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(timeMillis))
