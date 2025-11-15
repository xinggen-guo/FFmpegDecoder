package com.audio.study.ffmpegdecoder.opensles

import com.audio.study.ffmpegdecoder.utils.LogUtil

class SoundTrackController : NativeOnSoundTrackListener {

    /**
     * 设置播放文件地址，有可能是伴唱原唱都要进行设置
     */
    external fun setAudioDataSource(audioPath: String, nativeOnSoundTrackListener: NativeOnSoundTrackListener): Boolean

    /**
     * 获得伴奏的采样频率
     */
    external fun getAudioSampleRate(): Int

    /**
     * 播放伴奏
     */
    external fun play()

    /**
     * 暂停
     */
    external fun pause()

    /**
     * 恢复播放
     */
    external fun resume()
    /**
     * 停止伴奏
     */
    external fun stop()

    /**
     * seek
     */
    external fun seek(progress: Int)

    /**
     * 获得播放伴奏的当前时间
     */
    external fun getProgress(): Int

    external fun setVisualizerEnable(d: Boolean)

    external fun nativeGetSpectrum(spectrum: FloatArray)

    external fun nativeGetWaveform(spectrum: FloatArray)

    /**
     * 获取文件时长
     */
    external fun getDuration(): Long

    override fun onCompletion() {
        LogUtil.i("onCompletion---1111")
        onSoundTrackListener?.onCompletion()
        LogUtil.i("onCompletion---22222")
    }

    override fun onReady(){
        LogUtil.i("onReady---1111")
        onSoundTrackListener?.onReady()
        LogUtil.i("onReady---22222")
    }

    private var onSoundTrackListener: OnSoundTrackListener? = null
    fun setOnSoundTrackListener(onSoundTrackListener: OnSoundTrackListener?) {
        this.onSoundTrackListener = onSoundTrackListener
    }
}

interface NativeOnSoundTrackListener {
    fun onCompletion()
    fun onReady()
}