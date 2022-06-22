package com.audio.study.ffmpegdecoder.audiotracke

interface AudioDecoder {

    fun init(accompanyPath: String?, packetBufferTimePercent: Float)

    fun destory()

    fun readSamples(samples: ShortArray): Int

    fun getMusicMetaByPath(musicPath: String, metaArray: IntArray):Boolean

    fun prepare(musicPath: String)

    fun getProgress(): Int?

}