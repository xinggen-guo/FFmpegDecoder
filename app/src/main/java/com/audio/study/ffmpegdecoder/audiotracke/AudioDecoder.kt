package com.audio.study.ffmpegdecoder.audiotracke

interface AudioDecoder {

    fun destory()

    /** optional PCM callback (called after readSamples succeeds). */
    fun setOnPcmDecoded(listener: ((ShortArray, Int) -> Unit)?)

    fun readSamples(samples: ShortArray): Int

    fun getMusicMetaByPath(musicPath: String, metaArray: IntArray):Boolean

    fun prepare(musicPath: String): Boolean

    fun seek(seekPosition: Long)

    fun getProgress(): Long?

}