package com.audio.study.ffmpegdecoder.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.audio.study.ffmpegdecoder.common.AudioClockProvider
import com.audio.study.ffmpegdecoder.common.MediaStatus
import com.audio.study.ffmpegdecoder.utils.LogUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import com.audio.study.ffmpegdecoder.video.VideoPlayer
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/15 17:35
 * Video+Audio synchronized video renderer.
 */
class AudioVideoSyncView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val videoPlayer = VideoPlayer()

    @Volatile private var renderThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var surfaceReady = false
    @Volatile private var prepared = false
    @Volatile private var isPlaying = false

    private var videoWidth = 0
    private var videoHeight = 0

    /** Audio clock provider (ms since start of audio playback) */
    var audioClockProvider: AudioClockProvider? = null

    /** Notify Activity when video is prepared */
    var onPrepared: ((w: Int, h: Int) -> Unit)? = null

    /** Debug callback to show audio/video time and diff */
    var onTimeDebug: ((videoMs: Long, audioMs: Long, diffMs: Long) -> Unit)? = null

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        holder.addCallback(this)

        videoPlayer.onPrepared = { w, h ->
            videoWidth = w
            videoHeight = h
            prepared = true
            onPrepared?.invoke(w, h)
        }

        videoPlayer.onError = { msg ->
            ToastUtils.showShort("Video error: $msg")
        }
    }

    /** Prepare video decoder only (no playback yet) */
    fun prepare(path: String) {
        prepared = false
        isPlaying = false
        videoWidth = 0
        videoHeight = 0
        videoPlayer.prepareAsync(path)
    }

    /** Start playback; if audioClockProvider != null video will sync to audio. */
    fun play(audioClockProvider: AudioClockProvider? = null) {
        this.audioClockProvider = audioClockProvider

        if (isPlaying) return
        if (!prepared) {
            ToastUtils.showShort("Video not prepared")
            return
        }
        if (!surfaceReady) {
            ToastUtils.showShort("Surface not ready")
            return
        }

        isPlaying = true
        startRenderThread()
    }

    fun pause() {
        isPlaying = false
        stopRenderThread()
    }

    fun release() {
        pause()
        videoPlayer.release()
    }

    // ---------- Surface callbacks ----------

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceReady = true
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

    // ---------- Render thread logic ----------

    private fun startRenderThread() {
        if (renderThread != null) return
        if (!surfaceReady || !prepared) return

        val w = videoWidth
        val h = videoHeight
        if (w <= 0 || h <= 0) return

        running = true
        renderThread = Thread { renderLoop(w, h) }.apply { start() }
    }

    private fun stopRenderThread() {
        running = false
        renderThread?.join()
        renderThread = null
    }

    private fun renderLoop(w: Int, h: Int) {
        val frameBytes = w * h * 4
        val buffer = ByteBuffer.allocateDirect(frameBytes)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val srcRect = Rect(0, 0, w, h)
        var dstRect: Rect

        val ptsOut = LongArray(1)
        val maxLateMs = 80L

        var firstVideoPts = -1L
        var debugFrameCount = 0

        while (running && surfaceReady && isPlaying) {
            buffer.clear()
            ptsOut[0] = 0L

            val status = videoPlayer.nativeDecodeToRgbaWithPts(buffer, ptsOut)
            val framePtsMs = ptsOut[0]

            when (status) {
                MediaStatus.EOF -> break
                MediaStatus.BUFFERING -> {
                    Thread.sleep(10)
                    continue
                }
                MediaStatus.ERROR -> break


                else -> {
                    // 1) First frame → set video PTS base
                    if (firstVideoPts < 0) {
                        firstVideoPts = framePtsMs
                    }

                    // 2) Compute "video time" since first frame
                    val videoTimeMs = framePtsMs - firstVideoPts

                    // 3) Get audio clock (ms since audio playback started)
                    val audioClockMs = audioClockProvider?.getAudioClockMs()

                    val delayMs: Long
                    val audioUsedMs: Long

                    if (audioClockMs != null && audioClockMs > 0) {
                        // AV-sync case: audio is master clock
                        audioUsedMs = audioClockMs
                        delayMs = videoTimeMs - audioClockMs
                    } else {
                        // No audio clock → fallback (no real sync, but still play)
                        audioUsedMs = -1L
                        delayMs = 0L
                    }

                    // ---- DEBUG LOG & CALLBACK ----
                    debugFrameCount++
                    if (debugFrameCount % 5 == 0) { // log every 5 frames to avoid spam
                        LogUtil.i(
                            "AV_SYNC",
                            "video=$videoTimeMs ms, audio=$audioUsedMs ms, diff=${videoTimeMs - audioUsedMs}"
                        )

                        onTimeDebug?.let { cb ->
                            val v = videoTimeMs
                            val a = audioUsedMs
                            val d = if (a >= 0) v - a else 0L
                            mainHandler.post {
                                cb(v, a, d)
                            }
                        }
                    }
                    // ------------------------------

                    if (audioClockMs != null && audioClockMs > 0) {
                        // Sync logic only when audio clock is valid
                        when {
                            delayMs > 1 -> {
                                Thread.sleep(delayMs.coerceAtMost(40))
                            }
                            delayMs < -maxLateMs -> {
                                // too late → drop frame
                                continue
                            }
                            else -> Unit // small early/late, draw now
                        }
                    }
                    // else → no sync, just draw immediately

                    // 4) Draw video frame
                    buffer.position(0)
                    bitmap.copyPixelsFromBuffer(buffer)

                    val canvas = holder.lockCanvas() ?: continue
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
            }
        }
    }
}