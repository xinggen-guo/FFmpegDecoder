package com.audio.study.ffmpegdecoder.video

import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/14 19:43
 * @description
 */
class VideoPlayer {

    init {
        System.loadLibrary("ffmpegdecoder")
    }

    // JNI
    external fun nativeOpenVideo(path: String): Boolean
    external fun nativeCloseVideo()
    external fun nativeGetWidth(): Int
    external fun nativeGetHeight(): Int
    external fun nativeDecodeToRgba(buffer: ByteBuffer): Int

    external fun nativeDecodeToRgbaWithPts(buffer: ByteBuffer, ptsOutMs: LongArray): Int

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Called when prepare finished (decoder opened, width/height known). */
    var onPrepared: ((width: Int, height: Int) -> Unit)? = null

    /** (Optional) error callback if you want later. */
    var onError: ((String) -> Unit)? = null

    fun prepareAsync(path: String) {
        Thread {
            val ok = nativeOpenVideo(path)
            if (!ok) {
                mainHandler.post {
                    onError?.invoke("Failed to open video: $path")
                }
                return@Thread
            }

            val w = nativeGetWidth()
            val h = nativeGetHeight()

            mainHandler.post {
                onPrepared?.invoke(w, h)
            }
        }.start()
    }

    fun release() {
        nativeCloseVideo()
    }
}