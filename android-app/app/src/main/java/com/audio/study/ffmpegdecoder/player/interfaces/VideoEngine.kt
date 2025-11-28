package com.audio.study.ffmpegdecoder.player.interfaces

import android.view.Surface
import com.audio.study.ffmpegdecoder.player.enum.DecodeType
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:27
 * Abstract video engine.
 *
 * Implementations:
 *  - FfmpegVideoEngine: software decode using FFmpeg to RGBA buffer
 *  - MediaCodecVideoEngine: hardware decode using MediaCodec
 */
interface VideoEngine {
    val decodeType: DecodeType

    fun prepare(path: String): Boolean
    fun start()
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun release()

    /** Width/height of video stream. */
    fun getVideoSize(): Pair<Int, Int>

    /**
     * Fetch next video frame.
     *
     * For DecodeType.FFMPEG:
     *   - [buffer] is non-null; implementation writes RGBA bytes into it.
     *   - [ptsOut][0] = frame PTS (ms).
     *
     * For DecodeType.MEDIACODEC:
     *   - [buffer] may be ignored and can be null.
     *   - Implementation still sets [ptsOut][0] for sync.
     *
     * Returns one of MediaStatus.* values.
     */
    fun readFrameInto(buffer: ByteBuffer?, ptsOut: LongArray): Int
    fun setOutputSurface(surface: Surface?)
}