package com.audio.study.ffmpegdecoder.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.audio.study.ffmpegdecoder.common.MediaStatus
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
    private var prepared = false

    @Volatile
    private var isPlaying = false

    private var videoWidth = 0
    private var videoHeight = 0

    /** Expose prepare callback to Activity */
    var onPrepared: ((width: Int, height: Int) -> Unit)? = null

    init {
        holder.addCallback(this)

        videoPlayer.onPrepared = { w, h ->
            videoWidth = w
            videoHeight = h
            prepared = true
            ToastUtils.showShort("Video prepared: ${w}x${h}")
            // Notify outside (Activity) – Activity decides when to call play()
            onPrepared?.invoke(w, h)
        }
    }

    /**
     * Prepare video; when ready, onPrepared callback will be invoked.
     */
    fun prepare(path: String) {
        prepared = false
        isPlaying = false
        videoWidth = 0
        videoHeight = 0
        videoPlayer.prepareAsync(path)
    }

    /** Caller explicitly starts playback after onPrepared. */
    fun play() {
        if (isPlaying) return
        if (!prepared) {
            ToastUtils.showShort("Video not prepared yet")
            return
        }
        if (!surfaceReady) {
            ToastUtils.showShort("Surface not ready")
            return
        }

        isPlaying = true
        startRenderThread()
    }

    /** Stop render loop but keep decoder and buffered frames. */
    fun pause() {
        isPlaying = false
        stopRenderThread()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
        // If user already called play() after prepared, we can start now
        if (prepared && isPlaying && renderThread == null) {
            startRenderThread()
        }
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
    ) = Unit

    private fun startRenderThread() {
        if (renderThread != null) return
        if (!surfaceReady || !prepared) return

        val w = videoWidth
        val h = videoHeight
        if (w <= 0 || h <= 0) {
            ToastUtils.showShort("Invalid video size")
            return
        }

        running = true
        renderThread = Thread { renderLoop(w, h) }.apply { start() }
    }

    private fun stopRenderThread() {
        running = false
        renderThread?.join()
        renderThread = null
    }

    fun release() {
        pause()
        videoPlayer.release()
    }

    private fun renderLoop(w: Int, h: Int) {
        val frameSizeBytes = w * h * 4
        val buffer = ByteBuffer.allocateDirect(frameSizeBytes)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(0, 0, w, h)
        var dstRect: Rect

        while (running && surfaceReady && isPlaying) {
            buffer.clear()
            val status = videoPlayer.nativeDecodeToRgba(buffer)

            when (status) {
                MediaStatus.EOF -> {
                    // No more frames
                    break
                }
                MediaStatus.BUFFERING -> {
                    // Decoder still running but queue empty – wait a bit
                    try {
                        Thread.sleep(10)
                    } catch (_: InterruptedException) {}
                    continue
                }
                MediaStatus.ERROR -> {
                    // Error – break playback
                    break
                }
                else -> {
                    // status > 0: bytes written
                    buffer.position(0)
                    bitmap.copyPixelsFromBuffer(buffer)

                    val canvas: Canvas? = holder.lockCanvas()
                    if (canvas != null) {
                        try {
                            val viewW = width
                            val viewH = height
                            val viewRatio = viewW.toFloat() / viewH
                            val videoRatio = w.toFloat() / h

                            dstRect = if (videoRatio > viewRatio) {
                                val scaledH = (viewW / videoRatio).toInt()
                                val top = (viewH - scaledH) / 2
                                Rect(0, top, viewW, top + scaledH)
                            } else {
                                val scaledW = (viewH * videoRatio).toInt()
                                val left = (viewW - scaledW) / 2
                                Rect(left, 0, left + scaledW, viewH)
                            }

                            canvas.drawColor(0xFF000000.toInt())
                            canvas.drawBitmap(bitmap, srcRect, dstRect, null)
                        } finally {
                            holder.unlockCanvasAndPost(canvas)
                        }
                    }

                    // TODO: later replace this with PTS-based timing
                    try {
                        Thread.sleep(33) // ~30fps
                    } catch (_: InterruptedException) {}
                }
            }
        }
    }
}