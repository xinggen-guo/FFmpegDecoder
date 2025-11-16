package com.audio.study.ffmpegdecoder

import android.os.Bundle
import android.os.Handler
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.common.AudioClockProvider
import com.audio.study.ffmpegdecoder.databinding.ActivityAudioVideoSyncBinding
import com.audio.study.ffmpegdecoder.opensles.OpenSlesAudioPlayer
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import com.audio.study.ffmpegdecoder.utils.formatMillisecond
import java.io.File

/**
 * @author xinggen.guo
 * @date 2025/11/15 17:36
 * Video + Audio sync demo Activity.
 */
class AudioVideoSyncActivity : AppCompatActivity() {

    private val uiHandler = Handler()

    private lateinit var binding: ActivityAudioVideoSyncBinding

    private val videoPath by lazy { FileUtil.getTheVideoPath(this) }

    private val audioPlayer by lazy { OpenSlesAudioPlayer() }

    private var audioPrepared = false
    private var videoPrepared = false

    private var progressJobRunning = false
    private var isSeek = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAudioVideoSyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPlay.isEnabled = false

        // Video prepared
        binding.audioVideoSyncView.onPrepared = { w, h ->
            ToastUtils.showShort("Video prepared: ${w}x$h")
            videoPrepared = true
            checkPrepared()
        }

        // Audio callbacks
        audioPlayer.onPrepared = { duration ->
            ToastUtils.showShort("Audio prepared: ${duration}ms")
            audioPrepared = true
            val durationMs = audioPlayer.getDuration()
            binding.videoProgress.max = durationMs.toInt()
            binding.duration.text = formatMillisecond(durationMs)
            checkPrepared()
        }

        audioPlayer.onCompletion = {
            ToastUtils.showShort("Playback completed")
            stopProgressUpdate()
        }

        audioPlayer.onError = { msg ->
            ToastUtils.showShort(msg)
            stopProgressUpdate()
        }

        binding.btnPrepare.setOnClickListener {
            val vPath = videoPath
            val aPath = videoPath

            if (!File(vPath).exists()) {
                ToastUtils.showShort("Video not found: $vPath")
                return@setOnClickListener
            }
            if (!File(aPath).exists()) {
                ToastUtils.showShort("Audio not found: $aPath")
                return@setOnClickListener
            }

            binding.btnPrepare.isEnabled = false
            binding.btnPlay.isEnabled = false
            audioPrepared = false
            videoPrepared = false

            // Prepare audio & video separately
            audioPlayer.prepare(aPath)
            binding.audioVideoSyncView.prepare(vPath)
        }

        binding.btnPlay.setOnClickListener {
            // 1) start audio
            audioPlayer.play()

            // 2) start video, synced to audio clock
            binding.audioVideoSyncView.play(
                audioClockProvider = AudioClockProvider {
                    audioPlayer.getAudioClockMs()
                }
            )
            startProgressUpdate()
        }

        binding.btnPause.setOnClickListener {
            audioPlayer.pause()
            binding.audioVideoSyncView.pause()
            stopProgressUpdate()
        }

        binding.btnResume.setOnClickListener {
            audioPlayer.resume()
            binding.audioVideoSyncView.resume(
                audioClockProvider = AudioClockProvider {
                    audioPlayer.getAudioClockMs()
                })
            startProgressUpdate()
        }

        setupSeekBar()
    }

    private fun setupSeekBar() {
        binding.videoProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var pendingProgress = 0

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isSeek && fromUser) {
                    pendingProgress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeek = true
                audioPlayer.pause()
                binding.audioVideoSyncView.pause()
                stopProgressUpdate()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeek = false
                audioPlayer.seek(pendingProgress)
                binding.audioVideoSyncView.seek(pendingProgress.toLong())
                audioPlayer.resume()
                binding.audioVideoSyncView.resume(
                    audioClockProvider = AudioClockProvider {
                        audioPlayer.getAudioClockMs()
                    })
                startProgressUpdate()
            }
        })
    }

    private fun checkPrepared() {
        if (audioPrepared && videoPrepared) {
            binding.btnPlay.isEnabled = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioPlayer.stop()
        binding.audioVideoSyncView.release()
        stopProgressUpdate()
    }

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (!progressJobRunning) return
            if (!isSeek) {
                val progress = audioPlayer.getProgress()
                binding.progress.text = formatMillisecond(progress.toLong())
                binding.videoProgress.progress = progress
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

}