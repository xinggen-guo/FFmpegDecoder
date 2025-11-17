package com.audio.study.ffmpegdecoder.player.engine

import com.audio.study.ffmpegdecoder.common.MediaStatus
import com.audio.study.ffmpegdecoder.player.enum.DecodeType
import com.audio.study.ffmpegdecoder.player.interfaces.VideoEngine
import com.audio.study.ffmpegdecoder.utils.LogUtil
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/17 15:02
 * VideoEngine implementation using FFmpeg software decode.
 *
 * Native side should:
 *  - open file
 *  - find video stream
 *  - decode frames to some internal buffer
 *  - convert to RGBA when readFrame() is called
 */
class FfmpegVideoEngine : VideoEngine {

    companion object {
        private const val TAG = "FfmpegVideoEngine"

        init {
            try {
                System.loadLibrary("ffmpegdecoder")
            } catch (e: UnsatisfiedLinkError) {
                LogUtil.e(TAG, "Failed to load native library: ${e.message}")
            }
        }
    }

    override val decodeType: DecodeType = DecodeType.FFMPEG

    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    // --------- JNI declarations (implement in C/C++) ---------

    private external fun nativePrepare(path: String): Boolean
    private external fun nativeStart()
    private external fun nativePause()
    private external fun nativeResume()
    private external fun nativeSeekTo(positionMs: Long)
    private external fun nativeRelease()

    private external fun nativeGetVideoWidth(): Int
    private external fun nativeGetVideoHeight(): Int

    /**
     * Read one decoded frame into RGBA buffer.
     *
     * @param buffer   direct ByteBuffer, capacity >= w*h*4
     * @param ptsOut   length >= 1, ptsOut[0] set to PTS in ms
     * @return MediaStatus.* code
     */
    private external fun nativeReadFrame(buffer: ByteBuffer?, ptsOut: LongArray): Int

    // --------- VideoEngine interface implementation ---------

    override fun prepare(path: String): Boolean {
        LogUtil.i(TAG, "prepare: $path")
        val ok = nativePrepare(path)
        if (!ok) {
            LogUtil.e(TAG, "nativePrepare failed")
            return false
        }
        videoWidth = nativeGetVideoWidth()
        videoHeight = nativeGetVideoHeight()
        LogUtil.i(TAG, "video size: ${videoWidth}x$videoHeight")
        return true
    }

    override fun start() {
        LogUtil.i(TAG, "start")
        nativeStart()
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

    override fun getVideoSize(): Pair<Int, Int> {
        return videoWidth to videoHeight
    }

    override fun readFrameInto(buffer: ByteBuffer?, ptsOut: LongArray): Int {
        // For FFMPEG, we expect a non-null RGBA buffer
        if (buffer == null) {
            LogUtil.e(TAG, "readFrameInto: buffer is null for software decode")
            return MediaStatus.ERROR
        }
        if (ptsOut.isEmpty()) {
            LogUtil.e(TAG, "readFrameInto: ptsOut is empty")
            return MediaStatus.ERROR
        }

        val status = nativeReadFrame(buffer, ptsOut)
        // Optionally log:
         LogUtil.d(TAG, "readFrameInto -> status=$status pts=${ptsOut[0]}")
        return status
    }
}