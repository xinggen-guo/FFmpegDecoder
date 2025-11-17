package com.audio.study.ffmpegdecoder.player.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.audio.study.ffmpegdecoder.player.interfaces.AudioEngine
import com.audio.study.ffmpegdecoder.utils.LogUtil
import kotlin.concurrent.thread

/**
 * @author xinggen.guo
 * @date 2025/11/17 15:36
 * AudioEngine implementation that:
 *  - Uses native FFmpeg to decode PCM (short[]).
 *  - Uses Android AudioTrack to play the PCM.
 *
 * Native side is responsible only for:
 *  - Opening file / building FFmpeg pipeline
 *  - Decoding PCM frames
 *  - Seeking
 *  - Reporting duration
 *
 * Kotlin side is responsible for:
 *  - AudioTrack creation & playback
 *  - Playback thread
 *  - Audio clock calculation (progressMs)
 */
class AudioTrackAudioEngine : AudioEngine {

    companion object {
        private const val TAG = "AudioTrackAudioEngine"

        init {
            try {
                System.loadLibrary("ffmpegdecoder")
            } catch (e: UnsatisfiedLinkError) {
                LogUtil.e(TAG, "Failed to load native library: ${e.message}")
            }
        }

        private const val BUFFERED_SECONDS = 1 // roughly 1 second buffer
    }

    // -------- Native methods --------

    /** Open file / prepare FFmpeg audio decoding pipeline. */
    private external fun nativePrepareDecoder(path: String): Boolean

    /** Seek decoder to position (ms). */
    private external fun nativeSeekTo(positionMs: Long)

    /** Release FFmpeg decoder resources. */
    private external fun nativeReleaseDecoder()

    /** Duration in ms from metadata. */
    private external fun nativeGetDurationMs(): Long

    /**
     * Decode PCM samples into the provided buffer.
     *
     * @param bufferShorts short[] output buffer
     * @param maxSamples   max samples to write (bufferShorts.size)
     * @param ptsOut       [0] = PTS in ms for the *first* sample of this buffer
     * @return number of samples decoded, or:
     *         -1 = error
     *          0 = EOF
     */
    private external fun nativeReadSamples(
        bufferShorts: ShortArray,
        maxSamples: Int,
        ptsOut: LongArray
    ): Int

    private external fun nativeGetSampleRate(): Int
    private external fun nativeGetChannelCount(): Int

    // -------- State --------

    private var audioTrack: AudioTrack? = null
    @Volatile private var playThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var playing = false

    private var sampleRate = 44100
    private var channelCount = 2
    private var durationMs: Long = 0L

    // Playback position tracking (our audio clock)
    @Volatile private var playedSamples: Long = 0L

    override fun prepare(path: String): Boolean {
        LogUtil.i(TAG, "prepare: $path")

        if (!nativePrepareDecoder(path)) {
            LogUtil.e(TAG, "nativePrepareDecoder failed")
            return false
        }

        sampleRate = nativeGetSampleRate().takeIf { it > 0 } ?: 44100
        channelCount = nativeGetChannelCount().takeIf { it > 0 } ?: 2
        durationMs = nativeGetDurationMs()

        createAudioTrack()
        playedSamples = 0

        return true
    }

    private fun createAudioTrack() {
        val channelConfig = when (channelCount) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> AudioFormat.CHANNEL_OUT_STEREO
        }

        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSizeBytes = (sampleRate * channelCount * 2 * BUFFERED_SECONDS)
            .coerceAtLeast(minBufferBytes)

        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        audioTrack?.release()
        audioTrack = AudioTrack(
            attrs,
            format,
            bufferSizeBytes,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        LogUtil.i(TAG, "AudioTrack created: sr=$sampleRate, ch=$channelCount, buf=$bufferSizeBytes")
    }

    override fun play() {
        if (playing) {
            LogUtil.d(TAG, "play() ignored: already playing")
            return
        }
        val track = audioTrack ?: run {
            LogUtil.e(TAG, "play() called but audioTrack is null")
            return
        }

        running = true
        playing = true

        track.play()

        if (playThread == null || !playThread!!.isAlive) {
            startPlayThread()
        }
    }

    private fun startPlayThread() {
        val track = audioTrack ?: return

        val channels = channelCount
        val bufferSamples = sampleRate * channels / 10  // ~100ms of audio
        val buffer = ShortArray(bufferSamples)
        val ptsOut = LongArray(1)

        playThread = thread(start = true, name = "AudioTrackAudioEngine-Thread") {
            LogUtil.i(TAG, "Audio play thread started")
            while (running) {
                if (!playing) {
                    Thread.sleep(10)
                    continue
                }

                ptsOut[0] = 0L
                val samples = nativeReadSamples(buffer, buffer.size, ptsOut)

                if (samples < 0) {
                    LogUtil.e(TAG, "nativeReadSamples error")
                    break
                }
                if (samples == 0) {
                    // EOF
                    LogUtil.i(TAG, "nativeReadSamples EOF")
                    break
                }

                val bytesToWrite = samples * 2 // 16bit PCM
                var offset = 0
                while (offset < bytesToWrite && running && playing) {
                    val written = track.write(
                        buffer,
                        offset / 2,                 // short index
                        (bytesToWrite - offset) / 2 // short count
                    )
                    if (written <= 0) break
                    offset += written * 2
                }

                playedSamples += samples.toLong()
            }

            LogUtil.i(TAG, "Audio play thread exit")
        }
    }

    override fun pause() {
        if (!playing) return
        playing = false
        audioTrack?.pause()
    }

    override fun resume() {
        if (playing) return
        val track = audioTrack ?: return
        playing = true
        track.play()
        if (playThread == null || !playThread!!.isAlive) {
            startPlayThread()
        }
    }

    override fun seekTo(positionMs: Long) {
        LogUtil.i(TAG, "seekTo: $positionMs ms")
        // Reset clock
        playedSamples = (positionMs * sampleRate / 1000L) * channelCount
        nativeSeekTo(positionMs)
    }

    override fun release() {
        LogUtil.i(TAG, "release")
        running = false
        playing = false

        playThread?.join(80)
        playThread = null

        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null

        nativeReleaseDecoder()
    }

    override fun getDurationMs(): Long = durationMs

    override fun getAudioClockMs(): Long {
        // Convert playedSamples to ms
        if (sampleRate <= 0 || channelCount <= 0) return 0
        val frames = playedSamples / channelCount
        return frames * 1000L / sampleRate
    }
}