package com.audio.study.ffmpegdecoder.player.engine

import com.audio.study.ffmpegdecoder.player.interfaces.AudioEngine
import com.audio.study.ffmpegdecoder.utils.LogUtil

/**
 * @author xinggen.guo
 * @date 2025/11/17 15:01
 * AudioEngine implementation based on native OpenSL ES backend.
 *
 * This class assumes there is a native singleton or handle that manages:
 *  - FFmpeg audio decode
 *  - OpenSL ES playback
 *  - audio clock (progress in ms)
 *
 * You need to implement the native_* functions in C/C++ and hook them into
 * your existing SoundService / AudioDecoderController.
 */
class OpenSlAudioEngine : AudioEngine {

    companion object {
        private const val TAG = "OpenSlAudioEngine"

        init {
            // Make sure your native library is loaded
            try {
                System.loadLibrary("ffmpegdecoder")
            } catch (e: UnsatisfiedLinkError) {
                // adjust lib name if needed
                LogUtil.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    // --------- JNI declarations (implement in C/C++) ---------

    private external fun nativePrepare(path: String): Boolean
    private external fun nativePlay()
    private external fun nativePause()
    private external fun nativeResume()
    private external fun nativeSeekTo(positionMs: Long)
    private external fun nativeRelease()

    private external fun nativeGetDurationMs(): Long
    private external fun nativeGetAudioClockMs(): Long

    // --------- AudioEngine interface implementation ---------

    override fun prepare(path: String): Boolean {
        LogUtil.i(TAG, "prepare: $path")
        return nativePrepare(path)
    }

    override fun play() {
        LogUtil.i(TAG, "play")
        nativePlay()
    }

    override fun pause() {
        LogUtil.i(TAG, "pause")
        nativePause()
    }

    override fun resume() {
        LogUtil.i(TAG, "resume")
        nativeResume()
    }

    override fun seekTo(positionMs: Long) {
        LogUtil.i(TAG, "seekTo: $positionMs ms")
        nativeSeekTo(positionMs)
    }

    override fun release() {
        LogUtil.i(TAG, "release")
        nativeRelease()
    }

    override fun getDurationMs(): Long {
        return nativeGetDurationMs()
    }

    override fun getAudioClockMs(): Long {
        return nativeGetAudioClockMs()
    }
}