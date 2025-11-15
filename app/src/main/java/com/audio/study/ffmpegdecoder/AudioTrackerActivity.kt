package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.SeekBar
import com.audio.study.ffmpegdecoder.audiotracke.NativePlayController
import com.audio.study.ffmpegdecoder.audiotracke.AudioPlayer
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioTrackeBinding
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.formatMillisecond

class AudioTrackerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioTrackeBinding

    private var nativePlayController: NativePlayController? = null

    private var handler = Handler()

    val path by lazy { FileUtil.getTheAudioPath(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioTrackeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.audioTrackPrepare.setOnClickListener {
            nativePlayController = NativePlayController()
            nativePlayController?.setPlayListener(object : AudioPlayer.OnPlayListener {
                override fun onReady() {
                    val duration = nativePlayController?.getDuration() ?: 0
                    binding.audioProgress.max = duration.toInt()
                    binding.duration.text = formatMillisecond(duration)
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
            var pendingProgress = 0L

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isSeek && fromUser) {
                    pendingProgress = progress.toLong()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeek = true
                nativePlayController?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                if(isSeek){
                    nativePlayController?.seek(pendingProgress)
                }
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
            binding.progress.text = formatMillisecond(progress)
            binding.audioProgress.progress = progress.toInt()
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