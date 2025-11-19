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
 * @author xinggen.guo
 * XMediaPlayer:
 *
 * Orchestrates AudioEngine + VideoEngine + VideoRenderer + AvSyncController.
 * Does NOT know about OpenSL, AudioTrack, FFmpeg, or MediaCodec directly.
 *
 * provide concrete implementations for:
 *  - AudioEngine
 *  - VideoEngine
 *  - VideoRenderer
 *
 * Then this class handles:
 *  - prepare / play / pause / resume / seek / release
 *  - render loop
 *  - A/V sync using audio as master clock
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
    @Volatile private var isSeeking = false
    @Volatile private var seekTargetMs: Long = -1L
    @Volatile private var lastPositionMs: Long = 0L


    @Volatile private var lastProgressCallbackTime = 0L

    var listener: XMediaPlayerListener? = null

    /** Set or update output surface (SurfaceView / TextureView / SurfaceTexture). */
    fun setSurface(surface: Surface?) {
        videoRenderer.setSurface(surface)
    }

    /**
     * Prepare audio + video engines with the same media source.
     * This is a synchronous prepare; if you want async, call on a background thread.
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
        videoRenderer.setVideoSize(w, h)

        prepared = true
        syncController.reset()

        val duration = audioEngine.getDurationMs()
        mainHandler.post {
            listener?.onPrepared(duration)
        }

        return true
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

        audioEngine.resume()
        videoEngine.resume()

        startRenderThreadIfNeeded()
    }

    /** Seek in ms; both audio & video engines must handle it. */
    fun seekTo(positionMs: Long) {
        val durationMs = audioEngine.getDurationMs()
        val target = positionMs.coerceIn(0L, durationMs)
        seekTargetMs = target
        isSeeking = true

        // 1) Tell engines
        audioEngine.seekTo(target)
        videoEngine.seekTo(target)

        // 2) Reset sync controller
        syncController.reset()

        // 3) For UI: immediately jump
        lastPositionMs = target
        listener?.onProgress(target)
    }

    /** Release all resources and stop threads. */
    fun release() {
        LogUtil.i(TAG, "release")
        running = false
        playing = false

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

        val (w, h) = videoEngine.getVideoSize()
        if (w <= 0 || h <= 0) {
            LogUtil.e(TAG, "renderLoop: invalid video size ${w}x$h")
            return
        }

        val frameBytes = if (videoEngine.decodeType == DecodeType.FFMPEG) w * h * 4 else 0
        val buffer = if (frameBytes > 0) ByteBuffer.allocateDirect(frameBytes) else null
        val ptsOut = LongArray(1)

        while (running) {
            if (!playing) {
                Thread.sleep(10)
                continue
            }

            buffer?.clear()
            ptsOut[0] = 0L

            val audioClock = audioEngine.getAudioClockMs().takeIf { it > 0 }
            val status = videoEngine.readFrameInto(buffer, ptsOut)
            val framePtsMs = ptsOut[0]

            if (status != MediaStatus.OK) {
                when (status) {
                    MediaStatus.EOF -> {
                        LogUtil.i(TAG, "EOF reached in renderLoop")
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
            // Debug log every few frames or when needed
             LogUtil.i(TAG, "AV_SYNC videoPts=$framePtsMs audio=$audioClock decision=$decision")

            when (decision.action) {
                SyncDecision.Action.DROP_FRAME -> {
                    // skip render
                    continue
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

    // ------------------------------------------------------------------------
    // Callbacks helpers
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
}