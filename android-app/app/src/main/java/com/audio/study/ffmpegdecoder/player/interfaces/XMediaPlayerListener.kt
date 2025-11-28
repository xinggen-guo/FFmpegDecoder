package com.audio.study.ffmpegdecoder.player.interfaces

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:43
 * Optional listener-style callbacks for UI layer.
 */
interface XMediaPlayerListener {
    /** Called when prepare() finished successfully. */
    fun onPrepared(durationMs: Long) {}

    /** Called when playback reaches EOF. */
    fun onCompletion() {}

    /** Called on error; semantics are up to your implementation. */
    fun onError(what: Int, extra: Int) {}

    /** Periodic callback with current position (ms). */
    fun onProgress(positionMs: Long) {}

    /** before onEnd then resume */
    fun onEndResume() {}
}