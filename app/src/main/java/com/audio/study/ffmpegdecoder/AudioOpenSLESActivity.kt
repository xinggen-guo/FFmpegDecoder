package com.audio.study.ffmpegdecoder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.widget.SeekBar
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioOpenSlesactivityBinding
import com.audio.study.ffmpegdecoder.opensles.OpenSlesAudioPlayer
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import com.audio.study.ffmpegdecoder.utils.formatMillisecond
import java.io.File

class AudioOpenSLESActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAudioOpenSlesactivityBinding

    private val path by lazy { FileUtil.getTheAudioPath(this) }
    private val audioPlayer by lazy { OpenSlesAudioPlayer() }

    private val uiHandler = Handler()

    private val spectrumSize = 32
    private val spectrum = FloatArray(spectrumSize)

    private val waveSize = 512
    private val waveArray = FloatArray(waveSize)

    private var visualizerRunning = false
    private var progressJobRunning = false
    private var isSeek = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioOpenSlesactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // --- callbacks ---
        audioPlayer.onPrepared = { durationMs ->
            binding.audioProgress.max = durationMs.toInt()
            binding.duration.text = formatMillisecond(durationMs)
        }

        audioPlayer.onCompletion = {
            stopProgressUpdate()
            visualizerRunning = false
            checkOpenVisualizer(false)
        }

        audioPlayer.onError = { msg ->
            ToastUtils.showShort(msg)
        }

        // --- UI wires ---
        binding.openslEsPrepare.setOnClickListener {
            if (!File(path).exists()) {
                ToastUtils.showShort("File not found: $path")
                return@setOnClickListener
            }
            audioPlayer.prepare(path)
            visualizerRunning = binding.openVisualizer.isChecked
            audioPlayer.setVisualizerEnable(visualizerRunning)
        }

        binding.openslEsStart.setOnClickListener {
            audioPlayer.play()
            startProgressUpdate()
            checkOpenVisualizer(visualizerRunning)
        }

        binding.openslEsPause.setOnClickListener {
            audioPlayer.pause()
            stopProgressUpdate()
        }

        binding.openslEsResume.setOnClickListener {
            audioPlayer.resume()
            startProgressUpdate()
        }

        binding.openslEsDestroy.setOnClickListener {
            audioPlayer.stop()
            stopProgressUpdate()
            checkOpenVisualizer(false)
        }

        binding.openVisualizer.setOnCheckedChangeListener { _, isChecked ->
            visualizerRunning = isChecked
            audioPlayer.setVisualizerEnable(isChecked)
            checkOpenVisualizer(isChecked)
        }

        setupSeekBar()
    }

    private fun setupSeekBar() {
        binding.audioProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var pendingProgress = 0

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isSeek && fromUser) {
                    pendingProgress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeek = true
                audioPlayer.pause()
                stopProgressUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeek = false
                audioPlayer.seek(pendingProgress)
                audioPlayer.resume()
                startProgressUpdate()
            }
        })
    }

    // --- progress UI ---

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!progressJobRunning) return
            if (!isSeek) {
                val progress = audioPlayer.getProgress()
                binding.progress.text = formatMillisecond(progress.toLong())
                binding.audioProgress.progress = progress
            }
            uiHandler.postDelayed(this, 100)
        }
    }

    private fun startProgressUpdate() {
        progressJobRunning = true
        uiHandler.post(progressRunnable)
    }

    private fun stopProgressUpdate() {
        progressJobRunning = false
        uiHandler.removeCallbacks(progressRunnable)
    }

    // --- visualizer ---

    private val visualizerRunnable = object : Runnable {
        override fun run() {
            if (!visualizerRunning) return
            audioPlayer.getSpectrum(spectrum)
            binding.visualizerView.updateSpectrum(spectrum)
            uiHandler.postDelayed(this, 50)
        }
    }

    private val waveRunnable = object : Runnable {
        override fun run() {
            if (!visualizerRunning) return
            audioPlayer.getWaveform(waveArray)
            binding.visualizerWaveView.updateWaveform(waveArray)
            uiHandler.postDelayed(this, 16)
        }
    }

    private fun checkOpenVisualizer(enabled: Boolean) {
        if (enabled) {
            uiHandler.postDelayed(visualizerRunnable, 50)
            uiHandler.postDelayed(waveRunnable, 16)
        } else {
            uiHandler.removeCallbacks(visualizerRunnable)
            uiHandler.removeCallbacks(waveRunnable)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
        stopProgressUpdate()
    }

    companion object {
        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}