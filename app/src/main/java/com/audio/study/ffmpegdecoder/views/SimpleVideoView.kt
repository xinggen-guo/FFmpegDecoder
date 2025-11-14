package com.audio.study.ffmpegdecoder.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import com.audio.study.ffmpegdecoder.video.VideoPlayer
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/14 19:44
 * @description
 */
class SimpleVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val videoPlayer = VideoPlayer()

    @Volatile
    private var renderThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var surfaceReady = false

    @Volatile
    private var videoPath: String? = null

    init {
        holder.addCallback(this)
    }

    /**
     * Set the video file path.
     * Call this from Activity after user chooses / sets file.
     */
    fun setVideoPath(path: String) {
        videoPath = path
        // If surface already created, we can start render now
        if (surfaceReady) {
            startRenderThread()
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        startRenderThread()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceReady = false
        stopRenderThread()
    }

    override fun surfaceChanged(
        holder: SurfaceHolder,
        format: Int,
        width: Int,
        height: Int
    ) {
        // not used in this simple demo
    }

    private fun startRenderThread() {
        val path = videoPath ?: return
        if (renderThread != null) return

        running = true

        renderThread = Thread {
            renderLoop(path)
        }.apply { start() }
    }

    private fun stopRenderThread() {
        running = false
        renderThread?.join()
        renderThread = null
        videoPlayer.nativeCloseVideo()
    }

    private fun renderLoop(path: String) {
        // 1. Open video
        if (!videoPlayer.nativeOpenVideo(path)) {
            ToastUtils.showShort("Failed to open video: $path")
            return
        }

        val w = videoPlayer.nativeGetWidth()
        val h = videoPlayer.nativeGetHeight()
        if (w <= 0 || h <= 0) {
            ToastUtils.showShort("Invalid video size: $w x $h")
            return
        }

        val frameSizeBytes = w * h * 4
        val buffer = ByteBuffer.allocateDirect(frameSizeBytes)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val srcRect = Rect(0, 0, w, h)
        var dstRect: Rect

        while (running && surfaceReady) {
            buffer.clear()
            val bytes = videoPlayer.nativeDecodeToRgba(buffer)
            if (bytes <= 0) {
                // 0: EOF, <0: error – just stop for demo
                break
            }

            // Copy RGBA buffer into Bitmap
            buffer.position(0)
            bitmap.copyPixelsFromBuffer(buffer)

            // Draw to Surface
            val canvas: Canvas? = holder.lockCanvas()
            if (canvas != null) {
                try {
                    // Letterbox: center video in view
                    val viewW = width
                    val viewH = height
                    val viewRatio = viewW.toFloat() / viewH
                    val videoRatio = w.toFloat() / h

                    dstRect = if (videoRatio > viewRatio) {
                        // limited by width
                        val scaledH = (viewW / videoRatio).toInt()
                        val top = (viewH - scaledH) / 2
                        Rect(0, top, viewW, top + scaledH)
                    } else {
                        // limited by height
                        val scaledW = (viewH * videoRatio).toInt()
                        val left = (viewW - scaledW) / 2
                        Rect(left, 0, left + scaledW, viewH)
                    }

                    canvas.drawColor(0xFF000000.toInt()) // clear black
                    canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                } finally {
                    holder.unlockCanvasAndPost(canvas)
                }
            }

            // For now, simple sleep to slow down. Later we use real PTS.
            try {
                Thread.sleep(33) // ~30 FPS
            } catch (_: InterruptedException) {
            }
        }

        videoPlayer.nativeCloseVideo()
    }
}