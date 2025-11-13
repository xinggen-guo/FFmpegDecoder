package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.SeekBar
import com.audio.study.ffmpegdecoder.audiotracke.NativePlayController
import com.audio.study.ffmpegdecoder.audiotracke.NativePlayer
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioTrackeBinding
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.formatSecond
import java.io.File

class AudioTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioTrackeBinding

    private var nativePlayController: NativePlayController? = null

    private var handler = Handler()

    val path by lazy { application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "input.mp3" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread {
            FileUtil.copyFilesAssets(this, "input.mp3", path)
        }.start()

        binding = ActivityAudioTrackeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.audioTrackPrepare.setOnClickListener {
            nativePlayController = NativePlayController()
            nativePlayController?.setPlayListener(object : NativePlayer.OnPlayListener {
                override fun onReady() {
                    val duration = nativePlayController?.getDuration() ?: 0
                    binding.audioProgress.max = duration
                    binding.duration.text = formatSecond(duration.toLong())
                }

                override fun onPlayComplete() {
                    stopUpdateAudioProgress()
                    nativePlayController?.stop()
                }
            })
            nativePlayController?.setAudioDataSource(path)
        }

        binding.audioTrackStart.setOnClickListener {
            nativePlayController?.start()
            startUpdateAudioProgress()
        }

        binding.audioTrackPause.setOnClickListener {
            nativePlayController?.pause()
        }

        binding.audioTrackDestroy.setOnClickListener {
            stopUpdateAudioProgress()
            nativePlayController?.stop()
            nativePlayController = null
        }

        binding.audioProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            var isSeek = false

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isSeek) {
                    nativePlayController?.seek(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeek = true
                nativePlayController?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                nativePlayController?.resume()
                isSeek = false
            }

        })
    }

    private fun stopUpdateAudioProgress() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun startUpdateAudioProgress() {
        handler.postDelayed({
            val progress = nativePlayController?.getProgress() ?: 0
            binding.progress.text = formatSecond(progress.toLong())
            binding.audioProgress.progress = progress
            startUpdateAudioProgress()
        }, 50)
    }

    override fun onDestroy() {
        super.onDestroy()
        nativePlayController?.stop()
    }

    companion object {

        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}