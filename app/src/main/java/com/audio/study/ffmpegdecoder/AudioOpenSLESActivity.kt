package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioOpenSlesactivityBinding
import com.audio.study.ffmpegdecoder.opensles.SoundTrackController
import com.audio.study.ffmpegdecoder.utils.formatSecond
import java.io.File

class AudioOpenSLESActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioOpenSlesactivityBinding

    private var handler = Handler()

    val path by lazy { application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "input.mp3" }
    var songTrackController: SoundTrackController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioOpenSlesactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.jniPlayerPlay.setOnClickListener {
            playAudioByOpenSLTest(path)
        }

        binding.jniPlayerStop.setOnClickListener {
            stopAudioByOpenSLTest()
        }

        binding.openslEsPrepare.setOnClickListener {
            songTrackController = SoundTrackController()
            songTrackController?.setAudioDataSource(path)
            songTrackController?.setOnSoundTrackListener(object : SoundTrackController.OnSoundTrackListener {
                override fun onCompletion() {
                    stopUpdateAudioProgress()
                    songTrackController?.stop()
                }

                override fun onReady() {

                }
            })
            val duration = songTrackController?.getDuration() ?: 0
            binding.audioProgress.max = duration
            binding.duration.text = formatSecond(duration.toLong())
        }

        binding.openslEsStart.setOnClickListener {
            songTrackController?.play()
            startUpdateAudioProgress()
        }

        binding.openslEsPause.setOnClickListener {
            stopUpdateAudioProgress()
            songTrackController?.pause()
        }

        binding.openslEsDestroy.setOnClickListener {
            stopUpdateAudioProgress()
            songTrackController?.stop()
        }

    }

    private fun stopUpdateAudioProgress() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun startUpdateAudioProgress() {
        handler.postDelayed({
            val progress = songTrackController?.getProgress() ?: 0
            binding.progress.text = formatSecond(progress.toLong())
            binding.audioProgress.progress = progress
            startUpdateAudioProgress()
        }, 50)
    }

    override fun onDestroy() {
        super.onDestroy()
        songTrackController?.stop()
    }

    private external fun playAudioByOpenSLTest(audioPath: String)

    private external fun stopAudioByOpenSLTest()

    companion object {

        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}