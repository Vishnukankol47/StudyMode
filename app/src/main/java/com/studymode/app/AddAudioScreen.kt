package com.studymode.app

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBg = Color(0xFF07120D)
private val CardBg = Color(0xFF0E1A14)
private val CardBorder = Color(0xFF1A2B22)
private val CardInner = Color(0xFF0A1410)
private val NeonGreen = Color(0xFF22C55E)
private val NeonGreenLight = Color(0xFF4ADE80)
private val TextWhite = Color(0xFFE8F5E9)
private val TextMuted = Color(0xFF8BA396)
private val SurfaceGreen = Color(0xFF16281F)

@Composable
fun AddAudioScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToLibrary: () -> Unit,
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }
    var isImporting by remember { mutableStateOf(false) }

    val recentTracks = remember(refreshKey) {
        store.windDownTracks().take(6)
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            isImporting = true
            var importedCount = 0
            uris.forEach { uri ->
                try {
                    // Try to persist URI permission
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) {}

                    val metadata = extractAudioMetadata(context, uri)
                    store.saveWindDownTrack(metadata)
                    importedCount++
                } catch (_: Exception) {}
            }
            isImporting = false
            refreshKey++
            Toast.makeText(context, "Added $importedCount audio file(s) to library", Toast.LENGTH_SHORT).show()
            onNavigateToLibrary()
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CardBg),
            ) {
                GreenArrowBackIcon(modifier = Modifier.size(20.dp))
            }

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    text = "Add Audio",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Choose audio files from your device",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            // Big Import Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CardBorder)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Music note icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SurfaceGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("🎵", fontSize = 28.sp)
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Select Audio Files",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Choose music, podcasts or audiobooks from your device",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(20.dp))

                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("audio/*"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreen,
                                contentColor = Color(0xFF07120D),
                            ),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📁", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isImporting) "Importing..." else "Browse Files",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF07120D),
                                )
                            }
                        }
                    }
                }
            }

            // Supported Formats Notice
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Supported formats",
                        color = TextWhite,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "MP3, M4A, AAC, WAV, OGG, FLAC. You can select multiple files at once.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            // Recently Added
            item {
                Text(
                    text = "Recently Added",
                    color = TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(recentTracks, key = { it.id }) { track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when (track.category) {
                                "Podcasts" -> "🎙️"
                                "Audiobooks" -> "📖"
                                else -> "🎵"
                            },
                            fontSize = 18.sp,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = TextWhite,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val sizeMb = "%.1f MB".format((track.fileSizeBytes / (1024f * 1024f)).coerceAtLeast(0.1f))
                        Text(
                            text = "$sizeMb • ${track.category}",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }

                    Text("⋮", color = TextMuted, fontSize = 18.sp)
                }
            }
        }
    }
}

private fun extractAudioMetadata(context: Context, uri: Uri): WindDownAudioTrack {
    var title = "Audio Track"
    var artist = "Unknown Artist"
    var duration = 0L
    var size = 0L

    try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx != -1) title = cursor.getString(nameIdx) ?: "Audio Track"
                if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
            }
        }
    } catch (_: Exception) {}

    try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val extractedTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        val extractedArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
        val extractedDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        if (!extractedTitle.isNullOrBlank()) title = extractedTitle
        if (!extractedArtist.isNullOrBlank()) artist = extractedArtist
        if (!extractedDuration.isNullOrBlank()) duration = extractedDuration.toLongOrNull() ?: 0L
        retriever.release()
    } catch (_: Exception) {}

    val category = when {
        title.contains("podcast", true) || artist.contains("podcast", true) -> "Podcasts"
        title.contains("book", true) || title.contains("chapter", true) -> "Audiobooks"
        else -> "Music"
    }

    return WindDownAudioTrack(
        uri = uri.toString(),
        title = title.substringBeforeLast('.'),
        artist = artist,
        category = category,
        durationMs = if (duration > 0) duration else (30 * 60 * 1000L),
        fileSizeBytes = size,
        addedAt = System.currentTimeMillis(),
    )
}

@Composable
private fun GreenArrowBackIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.75f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.5f)
            moveTo(w * 0.45f, h * 0.3f)
            lineTo(w * 0.25f, h * 0.5f)
            lineTo(w * 0.45f, h * 0.7f)
        }
        drawPath(path = path, color = NeonGreen, style = Stroke(width = 3.dp.toPx()))
    }
}
