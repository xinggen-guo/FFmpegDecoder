package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.SeekBar
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioOpenSlesactivityBinding
import com.audio.study.ffmpegdecoder.opensles.SoundTrackController
import com.audio.study.ffmpegdecoder.utils.LogUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import com.audio.study.ffmpegdecoder.utils.formatSecond
import java.io.File

class AudioOpenSLESActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioOpenSlesactivityBinding

    private var handler = Handler()

    val path by lazy { application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "input.mp3" }
    var songTrackController: SoundTrackController? = null

    private val spectrumSize = 32
    private val spectrum = FloatArray(spectrumSize)
    private val uiHandler = Handler()
    private var visualizerRunning = false

    private val waveSize = 512
    private val waveArray = FloatArray(waveSize)

    private val visualizerRunnable = object : Runnable {
        override fun run() {
            if (!visualizerRunning) return
            songTrackController?.nativeGetSpectrum(spectrum)
            binding.visualizerView.updateSpectrum(spectrum) // custom view
            uiHandler.postDelayed(this, 50) // ~20 FPS
        }
    }

    private val waveRunnable = object : Runnable {
        override fun run() {
            if (!visualizerRunning) return
            songTrackController?.nativeGetWaveform(waveArray)
            binding.visualizerWaveView.updateWaveform(waveArray)
            uiHandler.postDelayed(this, 16) // ~60 FPS
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioOpenSlesactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.openslEsPrepare.setOnClickListener {
            songTrackController = SoundTrackController()
            songTrackController?.setOnSoundTrackListener(object : SoundTrackController.OnSoundTrackListener {
                override fun onCompletion() {
                    LogUtil.i("onCompletion")
                    stopUpdateAudioProgress()
                    if(visualizerRunning){
                        uiHandler.removeCallbacksAndMessages(null)
                    }
                    ToastUtils.showLong("complete")
                    songTrackController?.stop()
                }

                override fun onReady() {
                    val duration = songTrackController?.getDuration() ?: 0
                    binding.audioProgress.max = duration
                    binding.duration.text = formatSecond(duration.toLong())
                }
            })
            songTrackController?.apply {
               this.setAudioDataSource(path,this)
            }
            songTrackController?.setVisualizerEnable(binding.openVisualizer.isChecked)
            visualizerRunning = binding.openVisualizer.isChecked
        }

        binding.openVisualizer.setOnCheckedChangeListener { compoundButton, b ->
            songTrackController?.setVisualizerEnable(b)
            visualizerRunning = binding.openVisualizer.isChecked
            checkOpenVisualizer(b)
        }

        binding.openslEsStart.setOnClickListener {
            songTrackController?.play()
            startUpdateAudioProgress()
            checkOpenVisualizer(visualizerRunning)
        }

        binding.openslEsPause.setOnClickListener {
            stopUpdateAudioProgress()
            songTrackController?.pause()
        }

        binding.openslEsResume.setOnClickListener {
            songTrackController?.resume()
            startUpdateAudioProgress()
        }

        binding.openslEsDestroy.setOnClickListener {
            stopUpdateAudioProgress()
            songTrackController?.stop()
        }

        binding.audioProgress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{

            var isSeek = false

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(isSeek) {
                    songTrackController?.seek(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeek = true
                songTrackController?.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                songTrackController?.resume()
                isSeek = false
            }

        })
    }

    private fun checkOpenVisualizer(b: Boolean) {
        if (b) {
            uiHandler.postDelayed(visualizerRunnable, 50)
            uiHandler.postDelayed(waveRunnable,16)
        } else {
            uiHandler.removeCallbacksAndMessages(null)
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

    companion object {

        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}