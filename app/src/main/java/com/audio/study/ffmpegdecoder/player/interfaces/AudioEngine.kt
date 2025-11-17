package com.audio.study.ffmpegdecoder.player.interfaces

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:27
 * Abstract audio engine.
 * Implementations:
 *  - OpenSlAudioEngine: wraps your OpenSL + FFmpeg audio pipeline
 *  - AudioTrackAudioEngine: wraps AudioTrack + FFmpeg (if you add it later)
 */
interface AudioEngine {
    fun prepare(path: String): Boolean

    fun play()
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()

    /** Total duration of media in ms (or 0 if unknown). */
    fun getDurationMs(): Long

    /**
     * Current audio playback clock in ms.
     * This is the MASTER clock for A/V sync.
     */
    fun getAudioClockMs(): Long
}