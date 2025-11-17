package com.audio.study.ffmpegdecoder.player.enum

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:39
 * Audio output backend type.
 * Not used directly inside XMediaPlayer, but useful for construction.
*/
enum class AudioBackend {
    OPENSL,
    AUDIOTRACK
}