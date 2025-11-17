package com.audio.study.ffmpegdecoder.player.engine

import com.audio.study.ffmpegdecoder.player.data.SyncDecision

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:42
 * Pure A/V sync brain (no rendering, no decode).
 * Uses audio as master clock.
 *
 * - Audio clock returned by AudioEngine is "time since start (or since seek)".
 * - We keep firstVideoPtsMs / firstAudioClockMs as local bases
 *   and only compare *relative* times.
 */
class AvSyncController(
    private val maxLateMs: Long = 80L
) {
    private var firstVideoPtsMs: Long = -1L
    private var firstAudioClockMs: Long = -1L

    fun reset() {
        firstVideoPtsMs = -1L
        firstAudioClockMs = -1L
    }

    /**
     * Called when player does a seek.
     * Just treat it as a fresh start for sync:
     * clear internal bases, next frames will re-anchor.
     *
     * Make sure AudioEngine also resets its clock at seek.
     */
    fun onSeek(targetMs: Long) {
        // targetMs not strictly needed here, but kept for extensibility.
        reset()
    }

    /**
     * Decide how to sync one video frame with current audio clock.
     *
     * @param framePtsMs   absolute PTS (ms) of the video frame
     * @param audioClockMs absolute audio clock (ms) from AudioEngine
     */
    fun decide(framePtsMs: Long, audioClockMs: Long?): SyncDecision {
        // Establish video base
        if (firstVideoPtsMs < 0) {
            firstVideoPtsMs = framePtsMs
        }
        val videoTimeMs = framePtsMs - firstVideoPtsMs

        // No audio clock yet → just draw
        if (audioClockMs == null || audioClockMs <= 0) {
            return SyncDecision(SyncDecision.Action.DRAW_NOW)
        }

        // Establish audio base
        if (firstAudioClockMs < 0) {
            firstAudioClockMs = audioClockMs
        }
        val audioTimeMs = audioClockMs - firstAudioClockMs

        val diff = videoTimeMs - audioTimeMs  // >0: video ahead, <0: video behind

        return when {
            // Video ahead → sleep but cap to avoid big stall
            diff > 1 -> SyncDecision(
                action = SyncDecision.Action.SLEEP_THEN_DRAW,
                sleepMs = diff.coerceAtMost(40L)
            )

            // Video too late → drop
            diff < -maxLateMs -> SyncDecision(
                action = SyncDecision.Action.DROP_FRAME
            )

            // Small diff → draw now
            else -> SyncDecision(SyncDecision.Action.DRAW_NOW)
        }
    }
}