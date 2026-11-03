package com.studymode.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf

object WindDownAudioPlaybackManager {

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _currentTrack = mutableStateOf<WindDownAudioTrack?>(null)
    val currentTrack: State<WindDownAudioTrack?> = _currentTrack

    private val _currentPositionMs = mutableLongStateOf(0L)
    val currentPositionMs: State<Long> = _currentPositionMs

    private val _durationMs = mutableLongStateOf(0L)
    val durationMs: State<Long> = _durationMs

    private val _isShuffle = mutableStateOf(false)
    val isShuffle: State<Boolean> = _isShuffle

    // 0 = off, 1 = repeat all, 2 = repeat one
    private val _repeatMode = mutableIntStateOf(0)
    val repeatMode: State<Int> = _repeatMode

    private val _volume = mutableFloatStateOf(0.85f)
    val volume: State<Float> = _volume

    private val _sleepTimerMinutes = mutableIntStateOf(30)
    val sleepTimerMinutes: State<Int> = _sleepTimerMinutes

    private val _sleepTimerRemainingSeconds = mutableStateOf<Int?>(null)
    val sleepTimerRemainingSeconds: State<Int?> = _sleepTimerRemainingSeconds

    private val _continueUntilFinished = mutableStateOf(false)
    val continueUntilFinished: State<Boolean> = _continueUntilFinished

    private val _mixRelaxingSound = mutableStateOf(false)
    val mixRelaxingSound: State<Boolean> = _mixRelaxingSound

    private val _selectedRelaxingSound = mutableStateOf("Rain")
    val selectedRelaxingSound: State<String> = _selectedRelaxingSound

    private val _relaxingSoundVolume = mutableFloatStateOf(0.5f)
    val relaxingSoundVolume: State<Float> = _relaxingSoundVolume

    private val _queue = mutableStateOf<List<WindDownAudioTrack>>(emptyList())
    val queue: State<List<WindDownAudioTrack>> = _queue

    private val _queueIndex = mutableIntStateOf(0)
    val queueIndex: State<Int> = _queueIndex

    private val timerHandler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    fun initialize(context: Context) {
        val store = LocalStudyStore(context)
        val settings = store.windDownSettings()
        _sleepTimerMinutes.intValue = settings.defaultSleepTimerMinutes
        _volume.floatValue = settings.audioVolume
        _mixRelaxingSound.value = settings.mixRelaxingSound
        _selectedRelaxingSound.value = settings.selectedRelaxingSound
        _relaxingSoundVolume.floatValue = settings.relaxingSoundVolume

        // Load tracks into default queue
        val tracks = store.windDownTracks()
        if (tracks.isNotEmpty()) {
            _queue.value = tracks
            _currentTrack.value = tracks.first()
            _durationMs.longValue = tracks.first().durationMs
            _currentPositionMs.longValue = tracks.first().playbackPositionMs
        }
    }

    fun playTrack(context: Context, track: WindDownAudioTrack, newQueue: List<WindDownAudioTrack>? = null) {
        if (newQueue != null && newQueue.isNotEmpty()) {
            _queue.value = newQueue
            _queueIndex.intValue = newQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        } else if (_queue.value.none { it.id == track.id }) {
            _queue.value = _queue.value + track
            _queueIndex.intValue = _queue.value.size - 1
        } else {
            _queueIndex.intValue = _queue.value.indexOfFirst { it.id == track.id }
        }

        _currentTrack.value = track
        _currentPositionMs.longValue = track.playbackPositionMs
        _durationMs.longValue = track.durationMs

        // Start native service
        WindDownAudioService.startPlay(context, track.id)
    }

    fun togglePlayPause(context: Context) {
        val track = _currentTrack.value
        if (track == null) {
            val q = _queue.value
            if (q.isNotEmpty()) {
                playTrack(context, q.first())
            }
            return
        }
        WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_TOGGLE_PLAY)
    }

    fun pause(context: Context) {
        WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_PAUSE)
    }

    fun resume(context: Context) {
        WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_PLAY)
    }

    fun stop(context: Context) {
        cancelSleepTimer()
        WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_STOP)
    }

    fun seekTo(context: Context, posMs: Long) {
        _currentPositionMs.longValue = posMs
        WindDownAudioService.seekTo(context, posMs)
    }

    fun skipForward10s(context: Context) {
        val newPos = (_currentPositionMs.longValue + 10000L).coerceAtMost(_durationMs.longValue)
        seekTo(context, newPos)
    }

    fun skipBackward10s(context: Context) {
        val newPos = (_currentPositionMs.longValue - 10000L).coerceAtLeast(0L)
        seekTo(context, newPos)
    }

    fun skipNext(context: Context) {
        val q = _queue.value
        if (q.isEmpty()) return
        val nextIdx = if (_isShuffle.value) {
            (q.indices).random()
        } else {
            (_queueIndex.intValue + 1) % q.size
        }
        _queueIndex.intValue = nextIdx
        playTrack(context, q[nextIdx])
    }

    fun skipPrev(context: Context) {
        val q = _queue.value
        if (q.isEmpty()) return
        val prevIdx = if (_queueIndex.intValue > 0) _queueIndex.intValue - 1 else q.size - 1
        _queueIndex.intValue = prevIdx
        playTrack(context, q[prevIdx])
    }

    fun onTrackCompleted(context: Context) {
        if (_continueUntilFinished.value) {
            stop(context)
            return
        }
        val q = _queue.value
        if (q.isEmpty()) {
            stop(context)
            return
        }
        when (_repeatMode.intValue) {
            0 -> {
                if (_queueIndex.intValue < q.size - 1) {
                    skipNext(context)
                } else {
                    stop(context)
                }
            }
            1 -> skipNext(context)
            2 -> {
                _currentTrack.value?.let { playTrack(context, it) }
            }
        }
    }

    fun setShuffle(enabled: Boolean) {
        _isShuffle.value = enabled
    }

    fun toggleRepeat() {
        _repeatMode.intValue = (_repeatMode.intValue + 1) % 3
    }

    fun setVolume(context: Context, volume: Float) {
        _volume.floatValue = volume.coerceIn(0f, 1f)
        WindDownAudioService.setVolume(context, _volume.floatValue)
    }

    fun setSleepTimer(context: Context, minutes: Int) {
        _sleepTimerMinutes.intValue = minutes
        cancelSleepTimer()

        if (minutes <= 0) {
            _sleepTimerRemainingSeconds.value = null
            return
        }

        var remaining = minutes * 60
        _sleepTimerRemainingSeconds.value = remaining

        timerRunnable = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    _sleepTimerRemainingSeconds.value = null
                    if (!_continueUntilFinished.value) {
                        WindDownAudioService.sendAction(context, WindDownAudioService.ACTION_FADE_OUT)
                    }
                } else {
                    _sleepTimerRemainingSeconds.value = remaining
                    timerHandler.postDelayed(this, 1000)
                }
            }
        }
        timerHandler.postDelayed(timerRunnable!!, 1000)
    }

    fun cancelSleepTimer() {
        timerRunnable?.let { timerHandler.removeCallbacks(it) }
        timerRunnable = null
        _sleepTimerRemainingSeconds.value = null
    }

    fun setContinueUntilFinished(enabled: Boolean) {
        _continueUntilFinished.value = enabled
    }

    fun updateMixSettings(context: Context, enabled: Boolean, sound: String, volume: Float) {
        _mixRelaxingSound.value = enabled
        _selectedRelaxingSound.value = sound
        _relaxingSoundVolume.floatValue = volume
        WindDownAudioService.updateMix(context, enabled, sound, volume)
    }

    // Callbacks from Service
    fun setPlayingState(playing: Boolean, track: WindDownAudioTrack?, duration: Long) {
        _isPlaying.value = playing
        if (track != null) {
            _currentTrack.value = track
        }
        if (duration > 0) {
            _durationMs.longValue = duration
        }
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        _currentPositionMs.longValue = positionMs
        if (durationMs > 0) {
            _durationMs.longValue = durationMs
        }
    }
}
