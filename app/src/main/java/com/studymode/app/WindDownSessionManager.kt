package com.studymode.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat

object WindDownSessionManager {

    private const val NOTIFICATION_ID = 5002
    private const val CHANNEL_ID = "winddown_session_channel"

    private val _isSessionActive = mutableStateOf(false)
    val isSessionActive: State<Boolean> = _isSessionActive

    private val _isPaused = mutableStateOf(false)
    val isPaused: State<Boolean> = _isPaused

    private val _totalDurationMinutes = mutableIntStateOf(30)
    val totalDurationMinutes: State<Int> = _totalDurationMinutes

    private val _remainingSeconds = mutableIntStateOf(30 * 60)
    val remainingSeconds: State<Int> = _remainingSeconds

    private val _progress = mutableFloatStateOf(1f)
    val progress: State<Float> = _progress

    private val _blockAppsEnabled = mutableStateOf(true)
    val blockAppsEnabled: State<Boolean> = _blockAppsEnabled

    private val _screenDimmingEnabled = mutableStateOf(false)
    val screenDimmingEnabled: State<Boolean> = _screenDimmingEnabled

    private val _doNotDisturbEnabled = mutableStateOf(false)
    val doNotDisturbEnabled: State<Boolean> = _doNotDisturbEnabled

    private val handler = Handler(Looper.getMainLooper())
    private var sessionRunnable: Runnable? = null
    private var targetEndTimeMillis: Long = 0L
    private var pausedRemainingSeconds: Int = 0

    fun startSession(
        context: Context,
        durationMinutes: Int,
        blockApps: Boolean,
        screenDimming: Boolean,
        doNotDisturb: Boolean,
        trackToPlay: WindDownAudioTrack? = null,
    ) {
        _totalDurationMinutes.intValue = durationMinutes
        _remainingSeconds.intValue = durationMinutes * 60
        _progress.floatValue = 1f
        _blockAppsEnabled.value = blockApps
        _screenDimmingEnabled.value = screenDimming
        _doNotDisturbEnabled.value = doNotDisturb
        _isSessionActive.value = true
        _isPaused.value = false

        targetEndTimeMillis = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)

        // Save session config to store
        val store = LocalStudyStore(context)
        store.saveWindDownSessionConfig(
            WindDownSessionConfig(
                durationMinutes = durationMinutes,
                blockAppsEnabled = blockApps,
                screenDimmingEnabled = screenDimming,
                doNotDisturbEnabled = doNotDisturb,
                selectedAudioTrackId = trackToPlay?.id ?: WindDownAudioPlaybackManager.currentTrack.value?.id,
            )
        )

        // Start playing audio if specified
        if (trackToPlay != null) {
            WindDownAudioPlaybackManager.playTrack(context, trackToPlay)
        } else if (!WindDownAudioPlaybackManager.isPlaying.value) {
            WindDownAudioPlaybackManager.resume(context)
        }

        createNotificationChannel(context)
        startSessionCountdown(context)
        updateSessionNotification(context)
    }

    private fun startSessionCountdown(context: Context) {
        sessionRunnable?.let { handler.removeCallbacks(it) }

        sessionRunnable = object : Runnable {
            override fun run() {
                if (!_isSessionActive.value || _isPaused.value) return

                val now = System.currentTimeMillis()
                val diffSeconds = ((targetEndTimeMillis - now) / 1000L).toInt()

                if (diffSeconds <= 0) {
                    _remainingSeconds.intValue = 0
                    _progress.floatValue = 0f
                    onSessionExpired(context)
                } else {
                    _remainingSeconds.intValue = diffSeconds
                    val totalSec = _totalDurationMinutes.intValue * 60f
                    _progress.floatValue = (diffSeconds / totalSec).coerceIn(0f, 1f)
                    updateSessionNotification(context)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(sessionRunnable!!)
    }

    fun pauseSession(context: Context) {
        if (!_isSessionActive.value || _isPaused.value) return
        _isPaused.value = true
        pausedRemainingSeconds = _remainingSeconds.intValue
        sessionRunnable?.let { handler.removeCallbacks(it) }

        // Also pause audio
        WindDownAudioPlaybackManager.pause(context)
        updateSessionNotification(context)
    }

    fun resumeSession(context: Context) {
        if (!_isSessionActive.value || !_isPaused.value) return
        _isPaused.value = false
        targetEndTimeMillis = System.currentTimeMillis() + (pausedRemainingSeconds * 1000L)

        // Also resume audio
        WindDownAudioPlaybackManager.resume(context)
        startSessionCountdown(context)
        updateSessionNotification(context)
    }

    fun resetSession(context: Context) {
        _remainingSeconds.intValue = _totalDurationMinutes.intValue * 60
        _progress.floatValue = 1f
        targetEndTimeMillis = System.currentTimeMillis() + (_totalDurationMinutes.intValue * 60 * 1000L)
        pausedRemainingSeconds = _remainingSeconds.intValue
        updateSessionNotification(context)
    }

    fun adjustTime(secondsDelta: Int) {
        if (!_isSessionActive.value) return
        val newSec = (_remainingSeconds.intValue + secondsDelta).coerceAtLeast(0)
        _remainingSeconds.intValue = newSec
        targetEndTimeMillis += (secondsDelta * 1000L)
        if (_isPaused.value) {
            pausedRemainingSeconds = newSec
        }
        val totalSec = _totalDurationMinutes.intValue * 60f
        _progress.floatValue = (newSec / totalSec).coerceIn(0f, 1f)
    }

    fun endSession(context: Context) {
        _isSessionActive.value = false
        _isPaused.value = false
        sessionRunnable?.let { handler.removeCallbacks(it) }
        sessionRunnable = null

        // Trigger fade out and stop audio
        WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_FADE_OUT)

        // Cancel session notification
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun onSessionExpired(context: Context) {
        _isSessionActive.value = false
        _isPaused.value = false
        sessionRunnable?.let { handler.removeCallbacks(it) }

        val settings = LocalStudyStore(context).windDownSettings()
        if (settings.fadeOutAtEnd && !WindDownAudioPlaybackManager.continueUntilFinished.value) {
            WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_FADE_OUT)
        } else if (!WindDownAudioPlaybackManager.continueUntilFinished.value) {
            WindDownAudioPlaybackManager.stop(context)
        }

        // Post completion notification
        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("studymode://open/sleep")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle("Wind-down Completed")
            .setContentText("You are ready for a restful sleep. Goodnight!")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wind-down Session Status",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows live remaining countdown for wind-down sessions."
                setShowBadge(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateSessionNotification(context: Context) {
        if (!_isSessionActive.value) return

        val m = _remainingSeconds.intValue / 60
        val s = _remainingSeconds.intValue % 60
        val timeStr = "%02d:%02d".format(m, s)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("studymode://open/winddown_session")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(context, WindDownActionReceiver::class.java).apply {
            action = if (_isPaused.value) "ACTION_SESSION_RESUME" else "ACTION_SESSION_PAUSE"
        }
        val pauseResumePending = PendingIntent.getBroadcast(
            context,
            1,
            pauseResumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endIntent = Intent(context, WindDownActionReceiver::class.java).apply {
            action = "ACTION_SESSION_END"
        }
        val endPending = PendingIntent.getBroadcast(
            context,
            2,
            endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_studymode)
            .setContentTitle("Wind-down Mode")
            .setContentText("$timeStr remaining • Tap to open StudyMode")
            .setContentIntent(pendingIntent)
            .setOngoing(!_isPaused.value)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                if (_isPaused.value) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (_isPaused.value) "Resume" else "Pause",
                pauseResumePending
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Session", endPending)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }
}
