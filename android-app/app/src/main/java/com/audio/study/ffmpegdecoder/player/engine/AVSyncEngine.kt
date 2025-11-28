package com.audio.study.ffmpegdecoder.player.engine

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:28
 * @description
 */
class AVSyncEngine {
    private var baseAudioClock = 0L
    private var baseVideoPts = 0L

    fun start() { /* do nothing for now */ }

    fun pause() { /* do nothing */ }

    fun reset() {
        baseAudioClock = 0
        baseVideoPts = 0
    }

    fun onSeek(ms: Long) {
        reset()
    }

    fun sync(videoPtsMs: Long, audioClockMs: Long): Long {
        return videoPtsMs - audioClockMs
    }

    fun release() { }
}