package com.studymode.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import java.io.File

class WindDownAudioService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val ACTION_PLAY = "com.studymode.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.studymode.app.ACTION_PAUSE"
        const val ACTION_TOGGLE_PLAY = "com.studymode.app.ACTION_TOGGLE_PLAY"
        const val ACTION_STOP = "com.studymode.app.ACTION_STOP"
        const val ACTION_NEXT = "com.studymode.app.ACTION_NEXT"
        const val ACTION_PREV = "com.studymode.app.ACTION_PREV"
        const val ACTION_SEEK = "com.studymode.app.ACTION_SEEK"
        const val ACTION_SET_VOLUME = "com.studymode.app.ACTION_SET_VOLUME"
        const val ACTION_SET_MIX = "com.studymode.app.ACTION_SET_MIX"
        const val ACTION_FADE_OUT = "com.studymode.app.ACTION_FADE_OUT"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_SEEK_POS = "extra_seek_pos"
        const val EXTRA_VOLUME = "extra_volume"
        const val EXTRA_MIX_ENABLED = "extra_mix_enabled"
        const val EXTRA_MIX_SOUND = "extra_mix_sound"
        const val EXTRA_MIX_VOLUME = "extra_mix_volume"

        const val NOTIFICATION_ID = 5001
        const val CHANNEL_ID = "winddown_media_channel"

        fun startPlay(context: Context, trackId: Long) {
            val intent = Intent(context, WindDownAudioService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_TRACK_ID, trackId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, WindDownAudioService::class.java).apply {
                this.action = action
            }
            context.startService(intent)
        }

        fun seekTo(context: Context, posMs: Long) {
            val intent = Intent(context, WindDownAudioService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_SEEK_POS, posMs)
            }
            context.startService(intent)
        }

        fun setVolume(context: Context, volume: Float) {
            val intent = Intent(context, WindDownAudioService::class.java).apply {
                action = ACTION_SET_VOLUME
                putExtra(EXTRA_VOLUME, volume)
            }
            context.startService(intent)
        }

        fun updateMix(context: Context, enabled: Boolean, soundName: String, volume: Float) {
            val intent = Intent(context, WindDownAudioService::class.java).apply {
                action = ACTION_SET_MIX
                putExtra(EXTRA_MIX_ENABLED, enabled)
                putExtra(EXTRA_MIX_SOUND, soundName)
                putExtra(EXTRA_MIX_VOLUME, volume)
            }
            context.startService(intent)
        }
    }

    private lateinit var mediaSession: MediaSessionCompat
    private var mediaPlayer: MediaPlayer? = null
    private var ambientPlayer: MediaPlayer? = null
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private var currentTrack: WindDownAudioTrack? = null
    private var isPlaying = false
    private var mainVolume = 0.85f
    private var mixEnabled = false
    private var mixSoundName = "Rain"
    private var mixVolume = 0.5f

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && mediaPlayer != null) {
                try {
                    val pos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    val dur = mediaPlayer?.duration?.toLong() ?: currentTrack?.durationMs ?: 0L
                    WindDownAudioPlaybackManager.updatePosition(pos, dur)
                } catch (_: Exception) {}
            }
            progressHandler.postDelayed(this, 500)
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pausePlayback()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        createNotificationChannel()
        initMediaSession()

        registerReceiver(
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
        )
        progressHandler.post(progressRunnable)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wind-down Audio Playback",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows active playback controls on lock screen and notification shade."
                setShowBadge(false)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "WindDownAudioService").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    resumePlayback()
                }

                override fun onPause() {
                    pausePlayback()
                }

                override fun onSkipToNext() {
                    WindDownAudioPlaybackManager.skipNext(this@WindDownAudioService)
                }

                override fun onSkipToPrevious() {
                    WindDownAudioPlaybackManager.skipPrev(this@WindDownAudioService)
                }

                override fun onFastForward() {
                    WindDownAudioPlaybackManager.skipForward10s(this@WindDownAudioService)
                }

                override fun onRewind() {
                    WindDownAudioPlaybackManager.skipBackward10s(this@WindDownAudioService)
                }

                override fun onSeekTo(pos: Long) {
                    seekToPosition(pos)
                }

                override fun onStop() {
                    stopPlayback()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
                if (trackId > 0) {
                    val store = LocalStudyStore(this)
                    val track = store.windDownTrackById(trackId)
                    if (track != null) {
                        playTrack(track)
                    }
                }
            }
            ACTION_PAUSE -> pausePlayback()
            ACTION_TOGGLE_PLAY -> {
                if (isPlaying) pausePlayback() else resumePlayback()
            }
            ACTION_STOP -> stopPlayback()
            ACTION_NEXT -> WindDownAudioPlaybackManager.skipNext(this)
            ACTION_PREV -> WindDownAudioPlaybackManager.skipPrev(this)
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_SEEK_POS, 0L)
                seekToPosition(pos)
            }
            ACTION_SET_VOLUME -> {
                mainVolume = intent.getFloatExtra(EXTRA_VOLUME, 0.85f)
                mediaPlayer?.setVolume(mainVolume, mainVolume)
            }
            ACTION_SET_MIX -> {
                mixEnabled = intent.getBooleanExtra(EXTRA_MIX_ENABLED, false)
                mixSoundName = intent.getStringExtra(EXTRA_MIX_SOUND) ?: "Rain"
                mixVolume = intent.getFloatExtra(EXTRA_MIX_VOLUME, 0.5f)
                updateAmbientPlayer()
            }
            ACTION_FADE_OUT -> startFadeOut()
        }
        return START_NOT_STICKY
    }

    private fun playTrack(track: WindDownAudioTrack) {
        currentTrack = track
        requestAudioFocus()

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )

                // Handle URI types: content://, file://, sample://, or resource
                if (track.uri.startsWith("content://") || track.uri.startsWith("file://")) {
                    setDataSource(this@WindDownAudioService, Uri.parse(track.uri))
                } else if (track.uri.startsWith("sample://")) {
                    // Sample track fallback: map to synthesized or raw ambient stream
                    setupSampleAudio(this, track.title)
                } else {
                    val file = File(track.uri)
                    if (file.exists()) {
                        setDataSource(file.absolutePath)
                    } else {
                        setupSampleAudio(this, track.title)
                    }
                }

                isLooping = (WindDownAudioPlaybackManager.repeatMode.value == 2)
                setVolume(mainVolume, mainVolume)

                setOnPreparedListener { mp ->
                    mp.start()
                    this@WindDownAudioService.isPlaying = true
                    WindDownAudioPlaybackManager.setPlayingState(true, track, mp.duration.toLong())
                    updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, mp.currentPosition.toLong())
                    updateNotification()
                }

                setOnCompletionListener {
                    if (WindDownAudioPlaybackManager.repeatMode.value == 2) {
                        seekTo(0)
                        start()
                    } else {
                        WindDownAudioPlaybackManager.onTrackCompleted(this@WindDownAudioService)
                    }
                }

                setOnErrorListener { _, _, _ ->
                    setupSampleAudio(this, track.title)
                    true
                }

                prepareAsync()
            }
            mediaPlayer = player

            updateAmbientPlayer()
            updateMediaSessionMetadata(track)
            startForegroundServiceNotification()
        } catch (e: Exception) {
            setupFallbackSynthesis(track)
        }
    }

    private fun setupSampleAudio(player: MediaPlayer, title: String) {
        try {
            // Check for raw resource
            val resName = when {
                title.contains("Rain", true) || title.contains("Calm", true) -> "music_rain"
                title.contains("Brook", true) || title.contains("Water", true) -> "music_bird_brook"
                title.contains("Lofi", true) || title.contains("Chill", true) -> "lofi_beats"
                title.contains("Deep", true) || title.contains("Focus", true) -> "deep_focus"
                else -> "music_white_noise"
            }
            val resId = resources.getIdentifier(resName, "raw", packageName)
            if (resId != 0) {
                val afd = resources.openRawResourceFd(resId)
                if (afd != null) {
                    player.reset()
                    player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    afd.close()
                    player.prepareAsync()
                    return
                }
            }
        } catch (_: Exception) {}
    }

    private fun setupFallbackSynthesis(track: WindDownAudioTrack) {
        // Fallback to synthesized ambient focus sounds so audio never fails
        FocusAudioPlayer.play(this, "Deep Focus Ambience")
        isPlaying = true
        WindDownAudioPlaybackManager.setPlayingState(true, track, track.durationMs)
        updateNotification()
    }

    private fun resumePlayback() {
        requestAudioFocus()
        mediaPlayer?.let {
            it.start()
            isPlaying = true
            WindDownAudioPlaybackManager.setPlayingState(true, currentTrack, it.duration.toLong())
            updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PLAYING, it.currentPosition.toLong())
            updateNotification()
        }
        ambientPlayer?.start()
    }

    private fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) it.pause()
            isPlaying = false
            WindDownAudioPlaybackManager.setPlayingState(false, currentTrack, it.duration.toLong())
            updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_PAUSED, it.currentPosition.toLong())
            updateNotification()
        }
        ambientPlayer?.pause()
    }

    private fun stopPlayback() {
        isPlaying = false
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        ambientPlayer?.stop()
        ambientPlayer?.release()
        ambientPlayer = null

        FocusAudioPlayer.stop()

        abandonAudioFocus()
        updateMediaSessionPlaybackState(PlaybackStateCompat.STATE_STOPPED, 0L)
        WindDownAudioPlaybackManager.setPlayingState(false, null, 0L)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun seekToPosition(posMs: Long) {
        mediaPlayer?.seekTo(posMs.toInt())
        updateMediaSessionPlaybackState(
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            posMs
        )
    }

    private fun updateAmbientPlayer() {
        if (!mixEnabled) {
            ambientPlayer?.stop()
            ambientPlayer?.release()
            ambientPlayer = null
            return
        }

        try {
            ambientPlayer?.stop()
            ambientPlayer?.release()
            ambientPlayer = null

            val resName = when {
                mixSoundName.contains("Rain", true) -> "music_rain"
                mixSoundName.contains("Brook", true) -> "music_bird_brook"
                mixSoundName.contains("White", true) -> "music_white_noise"
                mixSoundName.contains("Brown", true) -> "music_brown_noise"
                else -> "music_rain"
            }
            val resId = resources.getIdentifier(resName, "raw", packageName)
            if (resId != 0) {
                ambientPlayer = MediaPlayer.create(this, resId)?.apply {
                    isLooping = true
                    setVolume(mixVolume, mixVolume)
                    if (this@WindDownAudioService.isPlaying) start()
                }
            }
        } catch (_: Exception) {}
    }

    private fun startFadeOut() {
        val handler = Handler(Looper.getMainLooper())
        var currentVol = mainVolume
        val step = currentVol / 20f

        val fadeRunnable = object : Runnable {
            override fun run() {
                currentVol -= step
                if (currentVol > 0.05f) {
                    mediaPlayer?.setVolume(currentVol, currentVol)
                    ambientPlayer?.setVolume(currentVol * mixVolume, currentVol * mixVolume)
                    handler.postDelayed(this, 100)
                } else {
                    stopPlayback()
                }
            }
        }
        handler.post(fadeRunnable)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        val settings = LocalStudyStore(this).windDownSettings()
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> pausePlayback()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (settings.pauseOnIncomingCalls) pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(mainVolume * 0.3f, mainVolume * 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(mainVolume, mainVolume)
                if (settings.pauseOnIncomingCalls && !isPlaying) resumePlayback()
            }
        }
    }

    private fun updateMediaSessionPlaybackState(state: Int, position: Long) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position, 1.0f)

        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateMediaSessionMetadata(track: WindDownAudioTrack) {
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.category)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)

        try {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_studymode)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        } catch (_: Exception) {}

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun startForegroundServiceNotification() {
        val notification = buildMediaNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildMediaNotification()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun buildMediaNotification(): android.app.Notification {
        val track = currentTrack ?: WindDownAudioTrack(title = "Wind-down Audio", artist = "StudyMode", uri = "")

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            data = Uri.parse("studymode://open/winddown_player")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, WindDownAudioService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, WindDownAudioService::class.java).apply { action = ACTION_TOGGLE_PLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, WindDownAudioService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle(track.title)
            .setContentText(if (track.artist.isNotBlank()) track.artist else "Wind-down Session")
            .setSubText(track.category)
            .setContentIntent(contentPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prevIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "Next", nextIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressHandler.removeCallbacks(progressRunnable)
        try {
            unregisterReceiver(noisyReceiver)
        } catch (_: Exception) {}

        mediaSession.isActive = false
        mediaSession.release()

        mediaPlayer?.release()
        mediaPlayer = null

        ambientPlayer?.release()
        ambientPlayer = null

        abandonAudioFocus()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
