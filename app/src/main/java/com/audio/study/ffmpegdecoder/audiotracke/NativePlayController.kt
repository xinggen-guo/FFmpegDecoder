package com.audio.study.ffmpegdecoder.audiotracke

class NativePlayController {

    var nativePlayer = NativePlayer()

    fun setPlayListener(playListener: NativePlayer.OnPlayListener){
        nativePlayer.setPlayListener(playListener)
    }

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

    fun pause() {
        nativePlayer.pause()
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

    fun resume() {
        nativePlayer.resume()
    }

    fun seek(progress: Int) {
        nativePlayer.seek(progress.toLong())
    }

}