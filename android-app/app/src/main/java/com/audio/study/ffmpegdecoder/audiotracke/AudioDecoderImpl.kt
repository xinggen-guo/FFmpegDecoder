package com.audio.study.ffmpegdecoder.audiotracke

class AudioDecoderImpl : AudioDecoder {


    private var pcmListener: ((ShortArray, Int) -> Unit)? = null

    override fun setOnPcmDecoded(listener: ((ShortArray, Int) -> Unit)?) {
        pcmListener = listener
    }

    override fun destory() {
        closeFile()
    }

    override fun readSamples(samples: ShortArray): Int {
        val ret = readSamples(samples, samples.size)
        if (ret > 0) {
            pcmListener?.invoke(samples, ret)
        }
        return ret
    }

    override fun getMusicMetaByPath(musicPath: String, metaArray: IntArray): Boolean {
        return getMusicMeta(musicPath, metaArray)
    }

    override fun prepare(musicPath: String): Boolean {
        return prepareDecoder(musicPath)
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

    private external fun prepareDecoder(musicPath: String): Boolean

    private external fun nativeSeekPlay(seekPosition: Long)
}