package com.audio.study.ffmpegdecoder.live.engine

import com.audio.study.ffmpegdecoder.live.interfaces.LiveAudioEngine
import com.audio.study.ffmpegdecoder.utils.LogUtil

/**
 * @author xinggen.guo
 * LiveAudioEngine implementation using OpenSL ES.
 *
 * Native side:
 *  - Creates OpenSL engine
 *  - Creates recorder (mic) + player (speaker)
 *  - Uses an internal ring buffer or double-buffer
 *  - Calls back into Java on each captured PCM chunk.
 */
class OpenSlLiveAudioEngine : LiveAudioEngine {

    companion object {
        private const val TAG = "OpenSlLiveAudioEngine"

        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }

    // Handle to native LiveEngine instance
    private var nativeHandle: Long = 0L

    @Volatile
    private var prepared = false

    @Volatile
    private var looping = false

    private var pcmListener: ((ShortArray, Int) -> Unit)? = null

    var onMixedPcmListener: ((ShortArray, Int) -> Unit)? = null

    @Volatile
    private var speakerMonitorEnabled = true


    override fun prepare(
        sampleRate: Int,
        channels: Int,
        bufferMs: Int
    ): Boolean {
        if (prepared) {
            LogUtil.w(TAG, "prepare() called twice, ignoring")
            return true
        }

        val handle = nativeCreateLiveEngine(sampleRate, channels, bufferMs)
        if (handle == 0L) {
            LogUtil.e(TAG, "nativeCreateLiveEngine failed")
            return false
        }

        nativeHandle = handle
        prepared = true
        LogUtil.i(
            TAG,
            "prepare OK: sampleRate=$sampleRate, channels=$channels, bufferMs=$bufferMs"
        )
        return true
    }

    override fun startLoopback() {
        if (!prepared || nativeHandle == 0L) {
            LogUtil.w(TAG, "startLoopback() called before prepare()")
            return
        }
        if (looping) {
            LogUtil.d(TAG, "startLoopback() ignored, already running")
            return
        }
        looping = true
        nativeStartLiveLoopback(nativeHandle)
        LogUtil.i(TAG, "startLoopback")
    }

    override fun stopLoopback() {
        if (!prepared || nativeHandle == 0L) return
        if (!looping) return
        try {
            nativeStopLiveLoopback(nativeHandle)
        } catch (_: Throwable) {
        }
        looping = false
        LogUtil.i(TAG, "stopLoopback")
    }

    override fun release() {
        LogUtil.i(TAG, "release")
        if (nativeHandle != 0L) {
            try {
                nativeReleaseLiveEngine(nativeHandle)
            } catch (_: Throwable) {
            }
            nativeHandle = 0L
        }
        prepared = false
        looping = false
        pcmListener = null
        onMixedPcmListener = null
    }

    override fun setOnPcmCaptured(listener: ((ShortArray, Int) -> Unit)?) {
        pcmListener = listener
    }

    override fun setOnMixedPcm(listener: ((ShortArray, Int) -> Unit)?) {
        onMixedPcmListener = listener
    }


    /**
     * Called from native layer when a PCM buffer has been captured.
     * This is the "bridge" for visualizer / recording / streaming.
     */
    private fun onPcmCapturedFromNative(data: ShortArray, size: Int) {
        pcmListener?.invoke(data, size)
    }

    /**
     * 🔹 Helper for app-side mixing:
     * After you mix mic + BGM in Kotlin, call this to notify observers.
     */
    fun dispatchMixedPcm(buffer: ShortArray, size: Int) {
        onMixedPcmListener?.invoke(buffer, size)
    }

    override fun setSpeakerMonitorEnabled(enabled: Boolean) {
        speakerMonitorEnabled = enabled
        LogUtil.i(TAG, "setSpeakerMonitorEnabled = $enabled")
    }

    fun pushMixedPcmToSpeaker(buffer: ShortArray, size: Int) {
        if (!prepared || nativeHandle == 0L) return
        if (!speakerMonitorEnabled) return
        nativePushMixedPcm(nativeHandle, buffer, size)
    }

    /** feed BGM PCM from FFmpeg decoder. */
    override fun pushBgmPcm(buffer: ShortArray, size: Int) {
        if (!prepared || nativeHandle == 0L) return
        if (size <= 0 || size > buffer.size) return
        nativePushBgmPcm(nativeHandle, buffer, size)
    }

    // -------- JNI native methods --------
    private external fun nativeCreateLiveEngine(
        sampleRate: Int,
        channels: Int,
        bufferMs: Int
    ): Long

    private external fun nativeStartLiveLoopback(handle: Long)
    private external fun nativeStopLiveLoopback(handle: Long)
    private external fun nativeReleaseLiveEngine(handle: Long)
    private external fun nativePushBgmPcm(handle: Long, data: ShortArray, size: Int)
    private external fun nativePushMixedPcm(handle: Long, buffer: ShortArray, size: Int)
}