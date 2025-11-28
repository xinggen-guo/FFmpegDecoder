package com.audio.study.ffmpegdecoder.player

import android.os.Handler
import android.os.Looper
import android.view.Surface
import com.audio.study.ffmpegdecoder.common.MediaStatus
import com.audio.study.ffmpegdecoder.player.data.SyncDecision
import com.audio.study.ffmpegdecoder.player.engine.AvSyncController
import com.audio.study.ffmpegdecoder.player.enum.DecodeType
import com.audio.study.ffmpegdecoder.player.interfaces.AudioEngine
import com.audio.study.ffmpegdecoder.player.interfaces.VideoEngine
import com.audio.study.ffmpegdecoder.player.interfaces.VideoRenderer
import com.audio.study.ffmpegdecoder.player.interfaces.XMediaPlayerListener
import com.audio.study.ffmpegdecoder.utils.LogUtil
import java.nio.ByteBuffer

/**
 * XMediaPlayer:
 * - Orchestrates AudioEngine + VideoEngine + VideoRenderer + AvSyncController.
 * - Handles: prepare / play / pause / resume / seek / preview / release.
 *
 * Audio is the master clock for normal playback.
 * Preview mode is video-only (no A/V sync).
 */
class XMediaPlayer(
    private val audioEngine: AudioEngine,
    private val videoEngine: VideoEngine,
    private val videoRenderer: VideoRenderer
) {

    companion object {
        private const val TAG = "XMediaPlayer"
        private const val PROGRESS_INTERVAL_MS = 200L
    }

    private val syncController = AvSyncController()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var prepared = false
    @Volatile private var playing = false
    @Volatile private var running = false
    @Volatile private var renderThread: Thread? = null

    // seek (normal playback)
    @Volatile private var isSeeking = false
    @Volatile private var reachedEof = false
    @Volatile private var seekTargetMs: Long = -1L

    @Volatile private var lastPositionMs: Long = 0L
    @Volatile private var lastProgressCallbackTime = 0L

    var listener: XMediaPlayerListener? = null

    // ---------- preview-mode state ----------
    @Volatile private var previewMode = false          // true while user is scrubbing
    @Volatile private var previewPositionMs: Long = 0L // latest thumb position (ms)
    @Volatile private var previewRequested = false     // render thread should process new preview
    @Volatile private var wasPlayingBeforePreview = false

    // ---------- cached video info / buffer ----------
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var frameBuffer: ByteBuffer? = null
    private val ptsOut = LongArray(1)

    /** Set or update output surface (SurfaceView / TextureView / SurfaceTexture). */
    fun setSurface(surface: Surface?) {
        videoRenderer.setSurface(surface)
        videoEngine.setOutputSurface(surface)
    }

    fun surfaceChanged(surface: Surface?, format: Int, width: Int, height: Int) {
        videoRenderer.surfaceChanged(surface, format, width, height)
        videoEngine.setOutputSurface(surface)
    }

    fun isPlayerCompleted(): Boolean = reachedEof

    /**
     * Prepare audio + video engines with the same media source.
     * This is a synchronous prepare; call it off the main thread if file is large.
     */
    fun prepare(path: String): Boolean {
        LogUtil.i(TAG, "prepare path=$path")

        val aOk = audioEngine.prepare(path)
        val vOk = videoEngine.prepare(path)

        if (!aOk || !vOk) {
            LogUtil.e(TAG, "prepare failed: audio=$aOk, video=$vOk")
            notifyError(-1, 0)
            return false
        }

        val (w, h) = videoEngine.getVideoSize()
        LogUtil.i(TAG, "video size: ${w}x$h")
        videoWidth = w
        videoHeight = h
        videoRenderer.setVideoSize(w, h)

        // allocate frame buffer once for FFmpeg mode
        if (videoEngine.decodeType == DecodeType.FFMPEG) {
            val bytes = w * h * 4
            if (bytes > 0) {
                if (frameBuffer == null || frameBuffer!!.capacity() < bytes) {
                    frameBuffer = ByteBuffer.allocateDirect(bytes)
                }
            }
        }

        prepared = true
        reachedEof = false
        syncController.reset()

        val duration = audioEngine.getDurationMs()
        mainHandler.post {
            listener?.onPrepared(duration)
        }

        return true
    }


    /**
     * Convenience: prepare a live TCP FLV stream.
     * Example URL: tcp://192.168.0.10:9000
     */
    fun prepareLiveTcp(host: String, port: Int): Boolean {
        val url = "tcp://$host:$port"
        return prepare(url)
    }

    /** Start playback from current position. */
    fun play() {
        if (!prepared) {
            LogUtil.w(TAG, "play() called before prepare()")
            return
        }
        if (playing) {
            LogUtil.d(TAG, "play() ignored, already playing")
            return
        }

        LogUtil.i(TAG, "play")
        playing = true
        running = true
        previewMode = false
        previewRequested = false
        reachedEof = false

        audioEngine.play()
        videoEngine.start()

        startRenderThreadIfNeeded()
    }

    /** Pause playback, keeping position. */
    fun pause() {
        if (!playing) return
        LogUtil.i(TAG, "pause")

        playing = false
        audioEngine.pause()
        videoEngine.pause()
    }

    /** Resume from paused state. */
    fun resume() {
        if (!prepared) {
            LogUtil.w(TAG, "resume() called before prepare()")
            return
        }
        if (playing) {
            LogUtil.d(TAG, "resume() ignored, already playing")
            return
        }

        LogUtil.i(TAG, "resume")
        playing = true
        previewMode = false
        previewRequested = false

        audioEngine.resume()
        videoEngine.resume()

        startRenderThreadIfNeeded()
    }

    /** Seek in ms; both audio & video engines must handle it. */
    fun seekTo(positionMs: Long) {
        if (!prepared) return
        val durationMs = audioEngine.getDurationMs()
        val target = positionMs.coerceIn(0L, durationMs)

        LogUtil.i(TAG, "seekTo $target ms (normal playback)")

        seekTargetMs = target
        isSeeking = true
        reachedEof = false

        // 1) Tell engines
        audioEngine.seekTo(target)
        videoEngine.seekTo(target)

        // 2) Reset AV-sync
        syncController.reset()

        // 3) Immediate UI update
        lastPositionMs = target
        listener?.onProgress(target)
    }

    // ---------- Preview API (for seek bar / gesture) ----------

    /** Called when user starts dragging the seek bar / timeline. */
    fun beginSeekPreview() {
        if (!prepared) return
        if (previewMode) return

        LogUtil.i(TAG, "beginSeekPreview")

        wasPlayingBeforePreview = playing

        // We want: audio muted/paused, BUT video decoder still running.
        if (playing) {
            // Pause only audio engine here
            audioEngine.pause()
        }

        // Stop normal playback path (renderLoop won't run A/V sync branch),
        // but keep video engine alive (do NOT call videoEngine.pause()).
        playing = false

        // Enter preview mode: renderLoop will go into handlePreviewInRenderLoop()
        previewMode = true
        previewRequested = false
    }

    /**
     * Called as user moves the seek bar.
     * This should be cheap: we just record the target and
     * let the render thread do the heavy work.
     */
    fun updateSeekPreview(positionMs: Long) {
        if (!prepared || !previewMode) return

        val durationMs = audioEngine.getDurationMs()
        val target = positionMs.coerceIn(0L, durationMs)

        previewPositionMs = target
        previewRequested = true

        // UI progress follows thumb immediately
        listener?.onProgress(target)
    }

    /**
     * Called when user releases the seek bar.
     * @param finalPositionMs final thumb position
     * @param resume whether to resume playback after reposition
     */
    fun endSeekPreview(finalPositionMs: Long, resume: Boolean) {
        if (!prepared) return

        val durationMs = audioEngine.getDurationMs()
        val target = finalPositionMs.coerceIn(0L, durationMs)

        LogUtil.i(TAG, "endSeekPreview: target=$target ms, resume=$resume")

        // Leave preview mode
        previewMode = false
        previewRequested = false

        // Perform real A/V seek (this updates audio + video decoders)
        val reachedStatus = reachedEof
        seekTo(target)
        reachedEof = reachedStatus

        if (!resume) {
            // Caller asked to remain paused after scrubbing
            playing = false
            audioEngine.pause()
            // optionally: videoEngine.pause()
            return
        }

        // resume == true
        when {
            // 1) We had finished (EOF) earlier → treat seek as "start from new point"
            reachedEof -> {
                LogUtil.i(TAG, "endSeekPreview: resume && reachedEof -> play() from $target")
                play()
                notifyEndedResume()
            }

            // 2) We were playing before preview → resume playback
            wasPlayingBeforePreview -> {
                LogUtil.i(TAG, "endSeekPreview: resume && wasPlayingBeforePreview -> resume()")
                resume()
            }

            // 3) Previously paused; keep paused even though resume=true
            else -> {
                LogUtil.i(TAG, "endSeekPreview: resume but was paused before -> stay paused")
                playing = false
                audioEngine.pause()
            }
        }
    }

    /** Release all resources and stop threads. */
    fun release() {
        LogUtil.i(TAG, "release")
        running = false
        playing = false
        previewMode = false
        previewRequested = false

        renderThread?.let { t ->
            try {
                t.join(80)
            } catch (_: InterruptedException) {}
        }
        renderThread = null

        videoEngine.release()
        audioEngine.release()
    }

    /** Current position in ms, using audio clock. */
    fun getCurrentPositionMs(): Long = audioEngine.getAudioClockMs()

    /** Media duration in ms (from audio engine). */
    fun getDurationMs(): Long = audioEngine.getDurationMs()

    // ------------------------------------------------------------------------
    // Internal render loop
    // ------------------------------------------------------------------------

    private fun startRenderThreadIfNeeded() {
        val current = renderThread
        if (current != null && current.isAlive) return

        renderThread = Thread { renderLoop() }.apply {
            name = "XMediaPlayer-RenderThread"
            start()
        }
    }

    private fun renderLoop() {
        LogUtil.i(TAG, "renderLoop start")

        val w = videoWidth
        val h = videoHeight
        if (w <= 0 || h <= 0) {
            LogUtil.e(TAG, "renderLoop: invalid video size ${w}x$h")
            return
        }

        val buffer = frameBuffer
        if (videoEngine.decodeType == DecodeType.FFMPEG && buffer == null) {
            LogUtil.e(TAG, "renderLoop: frameBuffer is null for FFmpeg decode")
            return
        }

        while (running) {

            // ---------- Preview mode: video-only, no A/V sync ----------
            if (previewMode) {
                handlePreviewInRenderLoop(buffer, w, h)
                continue
            }

            // ---------- Not playing & not previewing: idle ----------
            if (!playing) {
                Thread.sleep(10)
                continue
            }

            // ---------- Normal playback path ----------
            buffer?.clear()
            ptsOut[0] = 0L

            val audioClock = audioEngine.getAudioClockMs().takeIf { it > 0 }
            val status = videoEngine.readFrameInto(buffer, ptsOut)
            val framePtsMs = ptsOut[0]

            if (status != MediaStatus.OK) {
                when (status) {
                    MediaStatus.EOF -> {
                        LogUtil.i(TAG, "EOF reached in renderLoop")

                        // Mark as not playing, but keep last frame on screen
                        reachedEof = true
                        playing = false
                        isSeeking = false   // reset any pending seek state if you want
                        notifyCompletion()
                        break
                    }

                    MediaStatus.BUFFERING -> {
                        Thread.sleep(10)
                        continue
                    }

                    MediaStatus.ERROR -> {
                        LogUtil.e(TAG, "renderLoop video ERROR")
                        notifyError(-2, 0)
                        break
                    }

                    else -> {
                        // unknown status, treat as recoverable
                        continue
                    }
                }
            }

            val decision = syncController.decide(framePtsMs, audioClock)
            LogUtil.i(TAG, "AV_SYNC videoPts=$framePtsMs audio=$audioClock decision=$decision")

            when (decision.action) {
                SyncDecision.Action.DROP_FRAME -> {
                    // skip drawing this frame
                }
                SyncDecision.Action.SLEEP_THEN_DRAW -> {
                    if (decision.sleepMs > 0) {
                        Thread.sleep(decision.sleepMs)
                    }
                    videoRenderer.renderFrame(buffer, w, h)
                }
                SyncDecision.Action.DRAW_NOW -> {
                    videoRenderer.renderFrame(buffer, w, h)
                }
            }

            maybeDispatchProgress()
        }

        LogUtil.i(TAG, "renderLoop end")
    }

    /**
     * Preview logic executed on render thread when previewMode == true.
     * - Uses latest previewPositionMs.
     * - Seeks **video only** and draws a single frame.
     * - No audio clock, no AvSyncController.
     */
    private fun handlePreviewInRenderLoop(buffer: ByteBuffer?, w: Int, h: Int) {
        if (!previewMode) return

        if (!previewRequested) {
            // No new request from UI; avoid burning CPU
            Thread.sleep(10)
            return
        }

        // consume request
        val target = previewPositionMs
        previewRequested = false

        if (buffer == null && videoEngine.decodeType == DecodeType.FFMPEG) {
            Thread.sleep(10)
            return
        }

        LogUtil.d(TAG, "handlePreviewInRenderLoop: preview seek to $target ms")

        // Video-only seek; audio engine already paused in beginSeekPreview().
        videoEngine.seekTo(target)

        buffer?.clear()
        ptsOut[0] = 0L
        val status = videoEngine.readFrameInto(buffer, ptsOut)
        videoRenderer.renderFrame(frameBuffer, w, h)
        val framePtsMs = ptsOut[0]

        when (status) {
            MediaStatus.OK -> {
                LogUtil.d(TAG, "handlePreviewInRenderLoop: got frame pts=$framePtsMs, render")
                videoRenderer.renderFrame(buffer, w, h)
            }
            MediaStatus.BUFFERING -> {
                LogUtil.d(TAG, "handlePreviewInRenderLoop: BUFFERING at $target ms")
                // keep last drawn preview frame
            }
            MediaStatus.EOF -> {
                LogUtil.d(TAG, "handlePreviewInRenderLoop: EOF at $target ms")
            }
            MediaStatus.ERROR -> {
                LogUtil.e(TAG, "handlePreviewInRenderLoop: ERROR decoding preview frame")
            }
        }

        // No progress callback here; UI progress is driven by updateSeekPreview().
    }

    // ------------------------------------------------------------------------
    // Callback helpers
    // ------------------------------------------------------------------------

    private fun maybeDispatchProgress() {
        val now = System.currentTimeMillis()
        if (now - lastProgressCallbackTime >= PROGRESS_INTERVAL_MS) {
            lastProgressCallbackTime = now
            val pos = getCurrentPositionMs()
            mainHandler.post {
                listener?.onProgress(pos)
            }
        }
    }

    private fun notifyCompletion() {
        mainHandler.post {
            listener?.onCompletion()
        }
    }

    private fun notifyError(what: Int, extra: Int) {
        mainHandler.post {
            listener?.onError(what, extra)
        }
    }

    private fun notifyEndedResume(){
        mainHandler.post {
            listener?.onEndResume()
        }
    }

}