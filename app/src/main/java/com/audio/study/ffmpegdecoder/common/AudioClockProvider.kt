package com.audio.study.ffmpegdecoder.common

/**
 * @author xinggen.guo
 * @date 2025/11/15 17:27
 * @description
 */
fun interface AudioClockProvider {
    fun getAudioClockMs(): Long
}