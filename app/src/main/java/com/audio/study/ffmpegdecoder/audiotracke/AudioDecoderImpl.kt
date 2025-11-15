package com.audio.study.ffmpegdecoder.audiotracke

class AudioDecoderImpl : AudioDecoder {

    override fun init(accompanyPath: String?, packetBufferTimePercent: Float) {

    }

    override fun destory() {
        closeFile()
    }

    override fun readSamples(samples: ShortArray): Int {
        return readSamples(samples,samples.size)
    }

    override fun getMusicMetaByPath(musicPath: String, metaArray: IntArray): Boolean {
        return getMusicMeta(musicPath, metaArray)
    }

    override fun prepare(musicPath: String) {
        prepareDecoder(musicPath)
    }

    override fun getProgress(): Long {
        return nativeGetProgress()
    }

    override fun seek(seekPosition: Long) {
        nativeSeekPlay(seekPosition)
    }

    private external fun nativeGetProgress(): Long

    private external fun getMusicMeta(musicPath: String, metaArray: IntArray): Boolean

    private external fun readSamples(samples: ShortArray, size: Int): Int

    private external fun closeFile()

    private external fun prepareDecoder(musicPath: String)

    private external fun nativeSeekPlay(seekPosition: Long)
}