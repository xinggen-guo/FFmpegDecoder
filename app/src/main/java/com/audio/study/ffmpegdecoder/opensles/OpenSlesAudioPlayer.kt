package com.audio.study.ffmpegdecoder.opensles

import android.os.Handler
import android.os.Looper
import com.audio.study.ffmpegdecoder.utils.LogUtil

/**
 *
 * @author xinggen.guo
 * @date 2025/11/15 17:15
 *
 * High-level audio player wrapper over native SoundTrackController.
 *
 * Usage:
 *   val player = OpenSlesAudioPlayer()
 *   player.onPrepared = { durationMs -> ... }
 *   player.onCompletion = { ... }
 *   player.prepare(path)
 *   player.play()
 */
class OpenSlesAudioPlayer {

    private val native = SoundTrackController()
    private val mainHandler = Handler(Looper.getMainLooper())

    var onPrepared: ((durationMs: Long) -> Unit)? = null
    var onCompletion: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var prepared = false
    private var dataSourcePath: String? = null

    /** Prepare audio asynchronously – when ready, onPrepared will be called. */
    fun prepare(path: String) {
        dataSourcePath = path

        // Set listener before calling setAudioDataSource
        native.setOnSoundTrackListener(object : OnSoundTrackListener {
            override fun onCompletion() {
                LogUtil.i("OpenSlesAudioPlayer onCompletion")
                mainHandler.post {
                    onCompletion?.invoke()
                }
            }

            override fun onReady() {
                LogUtil.i("OpenSlesAudioPlayer onReady")
                prepared = true
                val duration = native.getDuration()
                mainHandler.post {
                    onPrepared?.invoke(duration)
                }
            }
        })

        // Call native to set the data source
        val ok = native.setAudioDataSource(path, native)
        if (!ok) {
            prepared = false
            mainHandler.post {
                onError?.invoke("Failed to set audio data source: $path")
            }
        }
    }

    /** Start playback (no-op if not prepared). */
    fun play() {
        if (!prepared) {
            LogUtil.e("OpenSlesAudioPlayer", "play() called before prepared")
            return
        }
        native.play()
    }

    fun pause() {
        native.pause()
    }

    fun resume() {
        native.resume()
    }

    fun stop() {
        native.stop()
        prepared = false
    }

    fun seek(progressMs: Int) {
        native.seek(progressMs)
    }

    fun getProgress(): Int {
        return native.getProgress()
    }

    fun getAudioClockMs(): Long {
        return native.getAudioClockMs()
    }

    fun getDuration(): Long {
        return native.getDuration()
    }

    // ---- Visualizer wrappers ----

    fun setVisualizerEnable(enabled: Boolean) {
        native.setVisualizerEnable(enabled)
    }

    fun getSpectrum(out: FloatArray) {
        native.nativeGetSpectrum(out)
    }

    fun getWaveform(out: FloatArray) {
        native.nativeGetWaveform(out)
    }

}