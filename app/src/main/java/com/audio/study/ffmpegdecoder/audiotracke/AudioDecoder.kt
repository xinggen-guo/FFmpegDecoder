package com.audio.study.ffmpegdecoder.audiotracke

interface AudioDecoder {

    fun init(accompanyPath: String?, packetBufferTimePercent: Float)

    fun destory()

    fun readSamples(samples: ShortArray): Int

    fun initMusicMetaByPath(musicPath: String, metaArray: IntArray):Boolean

    fun prepare()

}