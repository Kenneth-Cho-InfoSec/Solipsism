package com.krystelligence.solipsism.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Plays a short stereo test: left channel, pause, then right channel. */
object ChannelTestPlayer {
    private const val SAMPLE_RATE = 44100
    private const val TONE_MILLIS = 700
    private const val PAUSE_MILLIS = 250
    private const val FREQUENCY = 440.0
    private const val AMPLITUDE = 0.16

    @Volatile
    private var currentTrack: AudioTrack? = null

    fun play() {
        stop()
        Thread {
            val minimumBuffer = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minimumBuffer <= 0) return@Thread
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(minimumBuffer.coerceAtLeast(SAMPLE_RATE / 4))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            currentTrack = track
            try {
                track.play()
                writeTone(track, left = true)
                Thread.sleep(PAUSE_MILLIS.toLong())
                writeTone(track, left = false)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } finally {
                if (currentTrack === track) currentTrack = null
                track.stopSafely()
                track.release()
            }
        }.start()
    }

    fun stop() {
        currentTrack?.stopSafely()
        currentTrack = null
    }

    private fun writeTone(track: AudioTrack, left: Boolean) {
        val samples = SAMPLE_RATE * TONE_MILLIS / 1000
        val buffer = ShortArray(2048)
        var sampleIndex = 0
        while (sampleIndex < samples && currentTrack === track) {
            val frames = minOf(buffer.size / 2, samples - sampleIndex)
            for (frame in 0 until frames) {
                val value = (sin(2.0 * PI * FREQUENCY * (sampleIndex + frame) / SAMPLE_RATE) *
                    Short.MAX_VALUE * AMPLITUDE).toInt().toShort()
                buffer[frame * 2] = if (left) value else 0
                buffer[frame * 2 + 1] = if (left) 0 else value
            }
            track.write(buffer, 0, frames * 2)
            sampleIndex += frames
        }
    }

    private fun AudioTrack.stopSafely() {
        runCatching { pause() }
        runCatching { flush() }
    }
}
