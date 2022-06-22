package com.audio.study.ffmpegdecoder.audiotracke

class NativePlayController {

    var nativePlayer = NativePlayer()

    fun setAudioDataSource(path: String): Boolean {
        val result: Boolean = nativePlayer.setDataSource(path)
        if (result) {
            nativePlayer.prepare()
        }
        return result
    }

    fun start(){
        nativePlayer.play()
    }

    fun stop() {
        nativePlayer.stop()
    }

    fun getDuration(): Int {
        return nativePlayer.getDuration()
    }

    fun getProgress(): Int {
        return nativePlayer.getProgress()
    }

}