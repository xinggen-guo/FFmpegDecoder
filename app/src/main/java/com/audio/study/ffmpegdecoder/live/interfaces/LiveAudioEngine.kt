package com.audio.study.ffmpegdecoder.live.interfaces

/**
 * @author xinggen.guo
 * @date 2025/11/20 16:22
 * Simple live audio engine:
 *  - Captures mic PCM
 *  - Plays back to speaker (loopback)
 *  - Optionally exposes raw PCM for visualization / streaming / mixing.
 */
interface LiveAudioEngine {

    /**
     * Initialize OpenSL engine, recorder, player, buffers.
     *
     * @param sampleRate   e.g. 44100
     * @param channels     1 = mono, 2 = stereo (your playback path is already stereo)
     * @param bufferMs     per-buffer duration in ms (e.g. 20)
     */
    fun prepare(
        sampleRate: Int = 44100,
        channels: Int = 1,
        bufferMs: Int = 20
    ): Boolean

    /** Start mic capture + speaker playback (loopback). */
    fun startLoopback()

    /** Stop mic capture + speaker playback. */
    fun stopLoopback()

    /** Release all native resources. */
    fun release()

    /**
     * Optional callback: called from native when PCM is captured.
     * Used for:
     *  - visualizer
     *  - streaming encoder
     *  - mixing BGM + mic
     */
    fun setOnPcmCaptured(listener: ((buffer: ShortArray, size: Int) -> Unit)?)

    fun setOnMixedPcm(listener: ((ShortArray, Int) -> Unit)?)    // NEW callback

    /** Feed decoded BGM PCM (from FFmpeg) into live engine. */
    fun pushBgmPcm(buffer: ShortArray, size: Int)

    /** Enable/disable sending mixed audio to speaker (monitor / earback). */
    fun setSpeakerMonitorEnabled(enabled: Boolean)
}