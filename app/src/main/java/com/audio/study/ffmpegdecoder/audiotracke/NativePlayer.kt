package com.audio.study.ffmpegdecoder.audiotracke

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.audio.study.ffmpegdecoder.utils.LogUtil
import java.lang.IllegalArgumentException
import java.lang.NullPointerException

class NativePlayer {

    companion object{
        const val TAG = "NativePlayer"
    }

    private var audioDecoder: AudioDecoder? = null

    private var playPath:String? = null
    private var audioTrack: AudioTrack? = null
    private var sampleRateInHz = 44100
    private var audioDefaultFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioDefaultChannel = AudioFormat.CHANNEL_OUT_STEREO

    private var duration = 0

    private var decoderBufferSize = 0
    private var isPlaying = false
    private var isStop = false
    private var isSeekData = false

    private var playerThread: Thread? = null

    private var listener: OnPlayListener? = null

    fun setPlayListener(onPlayListener: OnPlayListener?){
        this.listener = onPlayListener
    }

    fun setDataSource(path: String): Boolean {
        LogUtil.i("setDataSource")
        this.playPath = path
        audioDecoder = AudioDecoderImpl()
        if (initMetaData(path)) {
            isPlaying = false
            isStop = false
        }
        return true
    }

    fun seek(seekPosition:Long){
        isSeekData = true
        audioDecoder?.seek(seekPosition)
    }

    private fun initAudioTrack(){
        LogUtil.i("initAudioTrack---->sampleRateInHz:${sampleRateInHz}---->audioDefaultChannel:${audioDefaultChannel}--->audioDefaultFormat:${audioDefaultFormat}")
        val bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, audioDefaultChannel, audioDefaultFormat)
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, audioDefaultChannel, audioDefaultFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM)
    }

    private fun initMetaData(path: String): Boolean {
        LogUtil.i("initMetaData")
        val metaArray = intArrayOf(0, 0, 0)
        if (audioDecoder?.getMusicMetaByPath(path, metaArray) == true) {
            sampleRateInHz = metaArray[0]
            if(sampleRateInHz  <= 0){
                throw IllegalArgumentException("sampleRateInHz < 0")
            }
            decoderBufferSize = metaArray[1]
            if(decoderBufferSize  <= 0){
                throw IllegalArgumentException("decoderBufferSize < 0")
            }
            duration = metaArray[2]
            LogUtil.i("sampleRateInHz:${sampleRateInHz}---->decoderBufferSize:${decoderBufferSize}")
            return true
        }
        return false
    }

    fun prepare() {
        LogUtil.i("prepare")
        playPath ?: throw NullPointerException("playPath is null")
        audioDecoder?.prepare(playPath!!)
        initAudioTrack()
        startPlayerThread()
        listener?.onReady()
    }

    private fun startPlayerThread() {
        playerThread = Thread(PlayThread(), "NativeMp3PlayerThread")
        playerThread?.start()
    }

    fun play() {
        synchronized(NativePlayer::class.java) {
            try {
                audioTrack?.play()
            } catch (t: Throwable) {
            }
            isPlaying = true
            isStop = false
        }
    }


    fun pause() {
        isPlaying = false
    }


    fun resume() {
        isPlaying = true
    }

    fun stop() {
        synchronized(NativePlayer::class.java) {
            try {
                audioTrack?.stop()
            } catch (t: Throwable) {
            }
            isPlaying = false
            isStop = true
        }
    }

    fun getDuration(): Int {
        return duration
    }

    fun getProgress(): Int {
        return audioDecoder?.getProgress() ?: 0
    }

    fun destroy() {
        audioDecoder?.destory()
    }

    inner class PlayThread : Runnable {

        override fun run() {
            var isPlayTemp = false.also { isPlaying = it }
            val samples: ShortArray = ShortArray(decoderBufferSize)
            var duration:Long? = null
            try {
                while (!isStop) {
                    duration = System.currentTimeMillis()
                    val sampleCount = audioDecoder?.readSamples(samples)
                    if (sampleCount == -2) {
                        try {
                            Thread.sleep(10)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        LogUtil.i("WARN : no play data")
                        continue
                    }else if(sampleCount == -1){
                        isStop = true
                        isPlaying = false
                        break
                    }else if(sampleCount == -3){
                        isPlaying = false
                        isStop = true
                        listener?.onPlayComplete()
                        break
                    }
                    while (true) {
                        synchronized(NativePlayer::class.java) {
                            isPlayTemp = isPlaying
                        }
                        if (isPlayTemp)
                            break
                        else
                            Thread.yield()
                    }
                    if (audioTrack?.state != AudioTrack.STATE_UNINITIALIZED && !isSeekData && !isStop) {
                        audioTrack?.write(samples, 0, sampleCount!!)
                    }
                    isSeekData = false
                }
            }catch (e:Exception){
                e.printStackTrace()
            }
            audioDecoder?.destory()
            playerThread = null
        }
    }


    interface OnPlayListener{
        fun onReady()

        fun onPlayComplete()
    }

}