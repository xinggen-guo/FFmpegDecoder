package com.audio.study.ffmpegdecoder.player.enum

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:39
 * Top-level decode type for video engine.
 * FFMPEG  -> software decode to RGBA
 * MEDIACODEC -> hardware decode using MediaCodec
 */
enum class DecodeType {
    FFMPEG,
    MEDIACODEC
}