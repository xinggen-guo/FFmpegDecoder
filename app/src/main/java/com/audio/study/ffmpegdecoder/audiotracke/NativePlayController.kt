package com.audio.study.ffmpegdecoder.audiotracke

class NativePlayController {

    var nativePlayer = AudioPlayer()

    fun setPlayListener(playListener: AudioPlayer.OnPlayListener){
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

    fun getDuration(): Long {
        return nativePlayer.getDuration()
    }

    fun getProgress(): Long {
        return nativePlayer.getProgress()
    }

    fun resume() {
        nativePlayer.resume()
    }

    fun seek(progress: Long) {
        nativePlayer.seek(progress)
    }

}