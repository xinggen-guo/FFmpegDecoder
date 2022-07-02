package com.audio.study.ffmpegdecoder.opensles

class SoundTrackController {

    /**
     * 设置播放文件地址，有可能是伴唱原唱都要进行设置
     */
    external fun setAudioDataSource(audioPath: String): Boolean

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

    /**
     * 获取文件时长
     */
    external fun getDuration(): Int

    fun onCompletion() {
        onSoundTrackListener?.onCompletion()
    }

    fun onReady(){
        onSoundTrackListener?.onReady()
    }

    private var onSoundTrackListener: OnSoundTrackListener? = null
    fun setOnSoundTrackListener(onSoundTrackListener: OnSoundTrackListener?) {
        this.onSoundTrackListener = onSoundTrackListener
    }

    interface OnSoundTrackListener {
        fun onCompletion()
        fun onReady()
    }

}