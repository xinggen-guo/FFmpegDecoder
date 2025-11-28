package com.audio.study.ffmpegdecoder.player.engine

import com.audio.study.ffmpegdecoder.player.data.SyncDecision


/**
 *
 * @author xinggen.guo
 * @date 2025/11/17 15:06
 * Pure A/V sync controller.
 *
 * Assumptions:
 *  - videoPtsMs: absolute video PTS in milliseconds (from decoder/FFmpeg).
 *  - audioClockMs: absolute audio clock in milliseconds (from AudioEngine / SoundService),
 *    already aligned to the same timeline as video PTS.
 *
 * Audio is the master clock.
 */
class AvSyncController(
    /**
     * How much video is allowed to be late (behind audio) before we drop the frame.
     * e.g. 80ms means: if videoPts < audioClock - 80 => drop frame.
     */
    private val maxLateMs: Long = 80L,

    /**
     * How much video is allowed to be early (ahead of audio) before we sleep.
     * e.g. 120ms means: if videoPts > audioClock + 120 => sleep a bit.
     */
    private val maxEarlyMs: Long = 120L
) {

    fun reset() {
        // No internal state for now, but kept for future extension.
    }

    /**
     * Decide how to handle one video frame with the current audio clock.
     *
     * @param videoPtsMs   absolute video PTS in ms.
     * @param audioClockMs absolute audio clock in ms, or null if audio clock not ready yet.
     */
    fun decide(videoPtsMs: Long, audioClockMs: Long?): SyncDecision {
        // If we don't have a valid audio clock yet -> just render normally.
        if (audioClockMs == null || audioClockMs <= 0L) {
            return SyncDecision(SyncDecision.Action.DRAW_NOW)
        }

        val diff = videoPtsMs - audioClockMs
        // diff > 0 : video ahead of audio
        // diff < 0 : video behind audio

        return when {
            // Video too early -> sleep a bit before drawing.
            diff > maxEarlyMs -> {
                SyncDecision(
                    action = SyncDecision.Action.SLEEP_THEN_DRAW,
                    sleepMs = diff.coerceAtMost(50L) // cap to avoid long stalls
                )
            }

            // Video too late -> drop this frame and try the next.
            diff < -maxLateMs -> {
                SyncDecision(
                    action = SyncDecision.Action.DROP_FRAME
                )
            }

            // Small difference -> draw now.
            else -> {
                SyncDecision(SyncDecision.Action.DRAW_NOW)
            }
        }
    }
}