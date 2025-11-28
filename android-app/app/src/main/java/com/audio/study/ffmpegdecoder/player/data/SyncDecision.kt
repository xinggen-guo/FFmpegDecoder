package com.audio.study.ffmpegdecoder.player.data

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:42
 * Decision from AVSyncController: what to do with this frame.
 */
data class SyncDecision(
    val action: Action,
    val sleepMs: Long = 0L
) {
    enum class Action {
        DRAW_NOW,
        SLEEP_THEN_DRAW,
        DROP_FRAME
    }
}
