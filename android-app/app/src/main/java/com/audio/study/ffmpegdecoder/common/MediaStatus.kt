package com.audio.study.ffmpegdecoder.common

/**
 * @author xinggen.guo
 * @date 2025/11/15 16:24
 * @description
 */

object MediaStatus {

    /** A valid frame / buffer was returned (bytes > 0) */
    const val OK = 1

    /** End of stream – no more data (EOF) */
    const val EOF = 0

    /** No data available right now, but decoder is still running / buffering */
    const val BUFFERING = 2

    /** Generic error during decode / convert / I/O */
    const val ERROR = -1
}