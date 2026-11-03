package com.studymode.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.util.Random
import kotlin.math.sin

/**
 * FocusAudioPlayer manages ambient sound and background audio during focus sessions.
 * 100% Offline Procedural Sound Synthesizer:
 * Generates continuous, seamless focus soundscapes (White, Pink, Brown noise,
 * Rain, Stream/Brook, Campfire, Alpha Binaural beats, and Deep Focus drones)
 * mathematically in real-time. Zero external dependencies, zero network requests,
 * and zero copyright liability.
 */
internal object FocusAudioPlayer {
    private const val TAG = "FocusAudioPlayer"
    private const val SAMPLE_RATE = 22050

    @Volatile
    private var isPlaying = false
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var synthesisThread: Thread? = null

    @Volatile
    private var currentSound: String = "Off"

    fun getCurrentSound(): String = currentSound

    @Synchronized
    fun play(context: Context, soundName: String) {
        stop()
        if (soundName.equals("Off", ignoreCase = true) || soundName.isBlank()) {
            currentSound = "Off"
            return
        }
        currentSound = soundName
        isPlaying = true

        // 1. Check for local bundled raw resource if present (e.g. res/raw/focus_rain.mp3)
        val resourceName = "focus_" + soundName.lowercase().replace(" ", "_").replace("&", "and")
        val resId = context.resources.getIdentifier(resourceName, "raw", context.packageName)
        if (resId != 0) {
            try {
                mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                    isLooping = true
                    setVolume(0.55f, 0.55f)
                    start()
                }
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error playing raw audio resource: $resourceName", e)
            }
        }

        // 2. High-fidelity procedural audio synthesis (100% offline, mathematically generated)
        startSynthesizer(soundName)
    }

    private fun startSynthesizer(soundName: String) {
        try {
            val bufferSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(SAMPLE_RATE / 4)

            val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE,
                )
            } else {
                @Suppress("DEPRECATION")
                AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize,
                    AudioTrack.MODE_STREAM,
                )
            }

            audioTrack = track
            track.play()

            val norm = soundName.lowercase()
            val isRain = norm.contains("rain")
            val isBrook = norm.contains("brook") || norm.contains("stream")
            val isCampfire = norm.contains("campfire")
            val isBrown = norm.contains("brown")
            val isWhite = norm.contains("white")
            val isPink = norm.contains("pink")
            val isBinaural = norm.contains("binaural") || norm.contains("alpha")

            synthesisThread = Thread {
                val random = Random()
                val buffer = ShortArray(1024)
                var lastSample = 0.0
                var crackleCounter = 0
                var b0 = 0.0
                var b1 = 0.0
                var b2 = 0.0
                var phaseCarrier = 0.0
                var phaseBeat = 0.0
                var phaseLfo = 0.0

                val carrierDelta = 2.0 * Math.PI * 196.0 / SAMPLE_RATE // 196 Hz carrier (G3)
                val beatDelta = 2.0 * Math.PI * 10.0 / SAMPLE_RATE     // 10 Hz alpha wave oscillation
                val lfoDelta = 2.0 * Math.PI * 0.75 / SAMPLE_RATE     // 0.75 Hz stream undulation

                while (isPlaying) {
                    for (i in buffer.indices) {
                        val white = random.nextDouble() * 2.0 - 1.0

                        val sampleVal: Double = when {
                            isRain -> {
                                // Rain: Low-pass filtered noise floor + randomized raindrop impulses
                                lastSample = (lastSample * 0.91) + (white * 0.09)
                                val drop = if (random.nextDouble() < 0.0025) (random.nextDouble() * 0.35) else 0.0
                                (lastSample * 0.22 + drop) * Short.MAX_VALUE
                            }
                            isBrook -> {
                                // Brook / Stream: Undulating bubbling filter modulated by slow sine LFO
                                phaseLfo += lfoDelta
                                if (phaseLfo > 2.0 * Math.PI) phaseLfo -= 2.0 * Math.PI
                                val lfo = 0.07 + 0.03 * sin(phaseLfo)
                                lastSample = (lastSample * (1.0 - lfo)) + (white * lfo)
                                val bubble = if (random.nextDouble() < 0.0018) (random.nextDouble() * 0.25) else 0.0
                                (lastSample * 0.24 + bubble) * Short.MAX_VALUE
                            }
                            isCampfire -> {
                                // Campfire: Deep brown rumble with wood crackles and pops
                                lastSample = (lastSample * 0.96) + (white * 0.04)
                                crackleCounter++
                                var pop = 0.0
                                if (crackleCounter > 1800 && random.nextDouble() < 0.009) {
                                    pop = (random.nextDouble() * 0.55) - 0.25
                                    crackleCounter = 0
                                }
                                (lastSample * 0.22 + pop) * Short.MAX_VALUE
                            }
                            isBrown -> {
                                // Brown noise: Deep waterfall / low-frequency rumble
                                lastSample = (lastSample * 0.97) + (white * 0.03)
                                lastSample * 0.35 * Short.MAX_VALUE
                            }
                            isWhite -> {
                                // Pure uniform white noise with gentle attenuation
                                white * 0.12 * Short.MAX_VALUE
                            }
                            isPink -> {
                                // 3-Pole IIR Pink noise filter (-3dB/octave slope)
                                b0 = 0.99765 * b0 + white * 0.0990460
                                b1 = 0.96300 * b1 + white * 0.2965164
                                b2 = 0.57000 * b2 + white * 1.0526913
                                val pink = (b0 + b1 + b2 + white * 0.1848) * 0.05
                                pink * Short.MAX_VALUE
                            }
                            isBinaural -> {
                                // Alpha Binaural Beats: 196 Hz tone with 10 Hz amplitude wave modulation + soft pink noise
                                phaseCarrier += carrierDelta
                                if (phaseCarrier > 2.0 * Math.PI) phaseCarrier -= 2.0 * Math.PI
                                phaseBeat += beatDelta
                                if (phaseBeat > 2.0 * Math.PI) phaseBeat -= 2.0 * Math.PI

                                val tone = sin(phaseCarrier) * (0.5 + 0.5 * sin(phaseBeat)) * 0.14
                                lastSample = (lastSample * 0.93) + (white * 0.07)
                                (tone + lastSample * 0.15) * Short.MAX_VALUE
                            }
                            else -> {
                                // Deep Focus / Ambience / Lo-Fi: Resonant harmonic drone with warm noise bed
                                phaseCarrier += carrierDelta * 0.5 // Sub-bass 98 Hz
                                if (phaseCarrier > 2.0 * Math.PI) phaseCarrier -= 2.0 * Math.PI
                                val drone = sin(phaseCarrier) * 0.10
                                lastSample = (lastSample * 0.94) + (white * 0.06)
                                (drone + lastSample * 0.18) * Short.MAX_VALUE
                            }
                        }

                        val clamped = sampleVal.coerceIn(
                            Short.MIN_VALUE.toDouble(),
                            Short.MAX_VALUE.toDouble(),
                        )
                        buffer[i] = clamped.toInt().toShort()
                    }

                    if (isPlaying) {
                        try {
                            track.write(buffer, 0, buffer.size)
                        } catch (_: Exception) {
                            break
                        }
                    }
                }
            }.apply {
                priority = Thread.MIN_PRIORITY
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting synthesizer", e)
        }
    }

    @Synchronized
    fun stop() {
        isPlaying = false
        try {
            synthesisThread?.interrupt()
            synthesisThread = null
        } catch (_: Exception) {}

        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
            audioTrack = null
        } catch (_: Exception) {}

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
    }
}
