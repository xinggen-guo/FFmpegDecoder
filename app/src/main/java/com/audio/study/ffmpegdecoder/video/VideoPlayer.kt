package com.audio.study.ffmpegdecoder.video

import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/14 19:43
 * @description
 */
class VideoPlayer {

    init {
        // Make sure your native lib name matches CMake add_library()
        System.loadLibrary("ffmpegdecoder")
    }

    external fun nativeOpenVideo(path: String): Boolean
    external fun nativeCloseVideo()

    external fun nativeGetWidth(): Int
    external fun nativeGetHeight(): Int

    /**
     * Decode one frame and write RGBA into buffer.
     * @return >0: bytes written, 0: EOF, <0: error
     */
    external fun nativeDecodeToRgba(buffer: ByteBuffer): Int
}