package com.studymode.app

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
fun MyAudioLibraryScreen(
    store: LocalStudyStore,
    onBack: () -> Unit,
    onNavigateToAddAudio: () -> Unit,
    onNavigateToPlayer: (WindDownAudioTrack) -> Unit,
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }

    val allTracks = remember(refreshKey) { store.windDownTracks() }

    val filteredTracks = remember(allTracks, selectedCategory, searchQuery) {
        allTracks.filter { track ->
            (selectedCategory == "All" || track.category.equals(selectedCategory, true)) &&
                    (searchQuery.isBlank() || track.title.contains(searchQuery, true) || track.artist.contains(searchQuery, true))
        }
    }

    var editTargetTrack by remember { mutableStateOf<WindDownAudioTrack?>(null) }
    var deleteConfirmTrack by remember { mutableStateOf<WindDownAudioTrack?>(null) }

    BackHandler(enabled = true) {
        onBack()
    }

    // Edit details dialog
    if (editTargetTrack != null) {
        var newTitle by remember { mutableStateOf(editTargetTrack!!.title) }
        var newArtist by remember { mutableStateOf(editTargetTrack!!.artist) }
        var newCategory by remember { mutableStateOf(editTargetTrack!!.category) }

        AlertDialog(
            onDismissRequest = { editTargetTrack = null },
            containerColor = CardBg,
            title = {
                Text("Edit Audio Details", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = CardBorder,
                        ),
                    )
                    OutlinedTextField(
                        value = newArtist,
                        onValueChange = { newArtist = it },
                        label = { Text("Artist / Creator", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = NeonGreen,
                            unfocusedBorderColor = CardBorder,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Music", "Podcasts", "Audiobooks", "Other").forEach { cat ->
                            val isSel = newCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) NeonGreen else CardInner)
                                    .clickable { newCategory = cat }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    cat,
                                    fontSize = 11.sp,
                                    color = if (isSel) Color(0xFF07120D) else TextWhite,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = editTargetTrack!!.copy(
                            title = newTitle.ifBlank { "Untitled Audio" },
                            artist = newArtist,
                            category = newCategory,
                        )
                        store.saveWindDownTrack(updated)
                        editTargetTrack = null
                        refreshKey++
                        Toast.makeText(context, "Track updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                ) {
                    Text("Save", color = Color(0xFF07120D), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editTargetTrack = null }) {
                    Text("Cancel", color = TextMuted)
                }
            },
        )
    }

    // Delete confirmation dialog
    if (deleteConfirmTrack != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmTrack = null },
            containerColor = CardBg,
            title = {
                Text("Remove from Library?", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "This removes '${deleteConfirmTrack!!.title}' from your StudyMode Audio Library. Your original file on your phone will NOT be deleted.",
                    color = TextMuted,
                    fontSize = 13.5.sp,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        store.deleteWindDownTrack(deleteConfirmTrack!!.id)
                        deleteConfirmTrack = null
                        refreshKey++
                        Toast.makeText(context, "Removed from library", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                ) {
                    Text("Remove", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteConfirmTrack = null }) {
                    Text("Keep", color = TextMuted)
                }
            },
        )
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
                    text = "My Audio",
                    color = TextWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Your uploaded music, podcasts and audio",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // Category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("All", "Music", "Podcasts", "Audiobooks").forEach { cat ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) NeonGreen else CardBg)
                        .border(1.dp, if (isSelected) NeonGreen else CardBorder, RoundedCornerShape(20.dp))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color(0xFF07120D) else TextWhite,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // + Add Audio Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg)
                .border(1.dp, NeonGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .clickable { onNavigateToAddAudio() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("+", color = NeonGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Add Audio",
                    color = NeonGreen,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Tracks list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            items(filteredTracks, key = { it.id }) { track ->
                AudioTrackItemRow(
                    track = track,
                    onPlay = {
                        WindDownAudioPlaybackManager.playTrack(context, track, filteredTracks)
                        onNavigateToPlayer(track)
                    },
                    onMenuAction = { action ->
                        when (action) {
                            "Play Next" -> {
                                Toast.makeText(context, "Playing next: ${track.title}", Toast.LENGTH_SHORT).show()
                            }
                            "Add to Queue" -> {
                                Toast.makeText(context, "Added to queue: ${track.title}", Toast.LENGTH_SHORT).show()
                            }
                            "Favourite" -> {
                                store.setWindDownTrackFavourite(track.id, !track.isFavourite)
                                refreshKey++
                            }
                            "Edit" -> {
                                editTargetTrack = track
                            }
                            "Remove" -> {
                                deleteConfirmTrack = track
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun AudioTrackItemRow(
    track: WindDownAudioTrack,
    onPlay: () -> Unit,
    onMenuAction: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isPlayingThis = WindDownAudioPlaybackManager.isPlaying.value &&
            WindDownAudioPlaybackManager.currentTrack.value?.id == track.id

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail Art
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF1E3A2B), Color(0xFF0F2318))
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when (track.category) {
                    "Podcasts" -> "🎙️"
                    "Audiobooks" -> "📖"
                    else -> "🎵"
                },
                fontSize = 20.sp,
            )
        }

        Spacer(Modifier.width(12.dp))

        // Title and meta
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isPlayingThis) NeonGreenLight else TextWhite,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val durMin = (track.durationMs / 60000L).coerceAtLeast(1)
            val subtitle = when {
                track.category == "Podcasts" -> "Podcast • ${durMin} min"
                track.category == "Audiobooks" -> "Audiobook • ${durMin} min"
                else -> "${if (track.artist.isNotBlank()) track.artist else "Track"} • ${durMin} min"
            }
            Text(
                text = subtitle,
                color = TextMuted,
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Play Button
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(SurfaceGreen)
                .clickable { onPlay() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isPlayingThis) "⏸" else "▶",
                color = NeonGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(8.dp))

        // Three-dot menu
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(32.dp),
            ) {
                Text("⋮", color = TextMuted, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(CardBg),
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = TextWhite) },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Play Next")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = TextWhite) },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Add to Queue")
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (track.isFavourite) "Remove from Favourites" else "Add to Favourites",
                            color = TextWhite
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Favourite")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Edit Details", color = TextWhite) },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Edit")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove from Library", color = Color(0xFFEF4444)) },
                    onClick = {
                        menuExpanded = false
                        onMenuAction("Remove")
                    },
                )
            }
        }
    }
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
