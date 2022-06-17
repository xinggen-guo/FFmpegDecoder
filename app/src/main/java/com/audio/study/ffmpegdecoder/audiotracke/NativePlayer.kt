package com.audio.study.ffmpegdecoder.audiotracke

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.audio.study.ffmpegdecoder.utils.LogUtil

class NativePlayer {

    companion object{
        const val TAG = "NativePlayer"
    }

    private var audioDecoder: AudioDecoder? = null

    private var audioTrack: AudioTrack? = null
    private var defaultContentTypeMusic = AudioAttributes.CONTENT_TYPE_MUSIC

    private var sampleRateInHz = 44100
    private var avBitRate:Int? = null
    private var audioDefaultFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioDefaultChannel = AudioFormat.CHANNEL_OUT_STEREO

    private var decoderBufferSize = 0
    private var isPlaying = false
    private var isStop = false

    private var playerThread: Thread? = null

    fun setDataSource(path: String): Boolean {
        audioDecoder = AudioDecoderImpl()
        if (initMetaData(path)) {
            isPlaying = false
            isStop = false
        }
        return true
    }

    private fun initAudioTrack(){
//        val audioAttributes = AudioAttributes.Builder().setContentType(defaultContentTypeMusic).build()
        val bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, audioDefaultChannel, audioDefaultFormat)
//        val avFormat = AudioFormat.Builder().setSampleRate(sampleRateInHz).setChannelMask(audioDefaultChannel).build()
//        audioTrack = AudioTrack(audioAttributes, avFormat, bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, sampleRateInHz, audioDefaultChannel, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes, AudioTrack.MODE_STREAM)
    }

    private fun initMetaData(path: String): Boolean {
        val metaArray = intArrayOf(0, 0, 0)
        if (audioDecoder?.initMusicMetaByPath(path, metaArray) == true) {
            sampleRateInHz = metaArray[0]
            avBitRate = metaArray[1]
            decoderBufferSize = metaArray[2]
            return true
        }
        return false
    }

    fun prepare() {
        initAudioTrack()
        audioDecoder?.prepare()
        startPlayerThread()
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

    inner class PlayThread : Runnable {

        override fun run() {
            var isPlayTemp = false.also { isPlaying = it }
            val samples: ShortArray = ShortArray(decoderBufferSize)
            var duration:Long? = null
            try {
                while (!isStop) {
//                    val needDataSize = decoderBufferSize * 3
//                    var currentSize = 0
//                    var listSample = mutableListOf<Short>()
//                    val samples: ShortArray = ShortArray(decoderBufferSize)
//                    duration = System.currentTimeMillis()
//                    while (currentSize < needDataSize){
//                        val result = audioDecoder?.readSamples(samples)
//                        if(result == -1){
//                            isStop = true
//                            isPlaying = false
//                            break
//                        }
//                        listSample.addAll(samples.toList())
//                        currentSize = listSample.size
//                    }
                    duration = System.currentTimeMillis()
                    val result = audioDecoder?.readSamples(samples)
                    if(result == -1){
                        isStop = true
                        isPlaying = false
                        break
                    }
                    LogUtil.i(TAG, "duration:${System.currentTimeMillis() - duration}")
                    while (true) {
                        synchronized(NativePlayer::class.java) {
                            isPlayTemp = isPlaying
                        }
                        if (isPlayTemp)
                            break
                        else
                            Thread.yield()

                    }
                    if (audioTrack?.state != AudioTrack.STATE_UNINITIALIZED) {
//                        audioTrack?.write(listSample.toShortArray(), 0, listSample.size)
                        audioTrack?.write(samples, 0, samples.size)
                    }
                }
            }catch (e:Exception){
                e.printStackTrace()
            }
            audioDecoder?.destory()
        }

    }


}