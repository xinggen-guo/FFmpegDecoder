package com.audio.study.ffmpegdecoder.utils

import com.audio.study.ffmpegdecoder.R

private const val ONE_SECOND_IN_MS = 1000L
private const val ONE_MINUTE_IN_MS = ONE_SECOND_IN_MS * 60

/**
 * Format milliseconds into human-readable form:
 * Examples:
 *  - 30000 → "30 secs"
 *  - 60000 → "1 min"
 *  - 80000 → "1 min 20 secs"
 *  - 120000 → "2 mins"
 *  - 220000 → "3 mins 40 secs"
 *
 * @param timeMs duration in milliseconds
 */
fun formatMillisecond(timeMs: Long): String {

    val totalSeconds = timeMs / ONE_SECOND_IN_MS
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return when {
        totalSeconds < 60 -> {
            // less than one minute
            ResourceUtils.getString(
                R.string.time_format_seconds,
                totalSeconds.toString()
            )
        }

        seconds == 0L -> {
            // exact minute: 1min or Xmins
            if (minutes == 1L) {
                ResourceUtils.getString(R.string.time_format_one_minute)
            } else {
                ResourceUtils.getString(R.string.time_format_minutes, minutes.toString())
            }
        }

        minutes == 1L -> {
            // 1 minute + seconds
            ResourceUtils.getString(
                R.string.time_format_one_minute_multi_seconds,
                seconds.toString()
            )
        }

        else -> {
            // multi minutes + seconds
            ResourceUtils.getString(
                R.string.time_format_multi_minutes_multi_seconds,
                minutes.toString(),
                seconds.toString()
            )
        }
    }
}