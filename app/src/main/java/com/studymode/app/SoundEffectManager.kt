package com.studymode.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

enum class SoundEffect {
    SESSION_START,
    TIMER_END,
    SESSION_END,
    BREAK_START,
    LOCK_SNAP,
    LOCK_UNLOCK,
    LOCK_DIAL,
    CLICK,
    CHIME,
    POP,
    BADGE,
}

/**
 * Low-latency sound effect player for tactile UI and focus feedback.
 * Uses Android SoundPool to play sounds instantly without latency
 * or interfering with background ambient music.
 */
object SoundEffectManager {
    private const val TAG = "SoundEffectManager"

    @Volatile
    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<SoundEffect, Int>()
    @Volatile
    private var isInitialized = false

    @Synchronized
    fun init(context: Context) {
        if (isInitialized && soundPool != null) return

        try {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(6)
                .setAudioAttributes(audioAttributes)
                .build()

            val appContext = context.applicationContext
            soundIds[SoundEffect.SESSION_START] = pool.load(appContext, R.raw.focus_session_start, 1)
            soundIds[SoundEffect.TIMER_END] = pool.load(appContext, R.raw.focus_timer_end, 1)
            soundIds[SoundEffect.SESSION_END] = pool.load(appContext, R.raw.focus_mode_end_sound, 1)
            soundIds[SoundEffect.BREAK_START] = pool.load(appContext, R.raw.focus_break_start, 1)
            soundIds[SoundEffect.LOCK_SNAP] = pool.load(appContext, R.raw.wf_lock_snap, 1)
            soundIds[SoundEffect.LOCK_UNLOCK] = pool.load(appContext, R.raw.wf_lock_unlock, 1)
            soundIds[SoundEffect.LOCK_DIAL] = pool.load(appContext, R.raw.wf_lock_dial, 1)
            soundIds[SoundEffect.CLICK] = pool.load(appContext, R.raw.click_sound, 1)
            soundIds[SoundEffect.CHIME] = pool.load(appContext, R.raw.study_ring_land_chime, 1)
            soundIds[SoundEffect.POP] = pool.load(appContext, R.raw.study_ring_start_pop, 1)
            soundIds[SoundEffect.BADGE] = pool.load(appContext, R.raw.badge_sound, 1)

            soundPool = pool
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing SoundEffectManager", e)
        }
    }

    fun play(context: Context, effect: SoundEffect, volume: Float = 0.85f) {
        try {
            if (!isInitialized || soundPool == null) {
                init(context)
            }
            val pool = soundPool ?: return
            val id = soundIds[effect] ?: return
            if (id > 0) {
                pool.play(id, volume, volume, 1, 0, 1.0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing sound effect: $effect", e)
        }
    }

    fun release() {
        try {
            soundPool?.release()
            soundPool = null
            soundIds.clear()
            isInitialized = false
        } catch (_: Exception) {}
    }
}
