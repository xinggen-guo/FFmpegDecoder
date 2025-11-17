package com.audio.study.ffmpegdecoder

import android.os.Bundle
import android.view.SurfaceHolder
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.databinding.ActivityXmediaPlayerBinding
import com.audio.study.ffmpegdecoder.player.XMediaPlayer
import com.audio.study.ffmpegdecoder.player.engine.FfmpegVideoEngine
import com.audio.study.ffmpegdecoder.player.engine.OpenSlAudioEngine
import com.audio.study.ffmpegdecoder.player.interfaces.AudioEngine
import com.audio.study.ffmpegdecoder.player.interfaces.VideoEngine
import com.audio.study.ffmpegdecoder.player.interfaces.XMediaPlayerListener
import com.audio.study.ffmpegdecoder.player.render.SoftwareCanvasRenderer
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.LogUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import com.audio.study.ffmpegdecoder.utils.formatMillisecond
import java.io.File

/**
 * @author xinggen.guo
 * @date 2025/11/17 15:04
 * @description
 */
class XMediaPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityXmediaPlayerBinding
    private lateinit var xPlayer: XMediaPlayer
    private var durationMs: Long = 0L
    private var isUserSeeking = false

    private val demoPath by lazy {
        // Replace with your real path util
        FileUtil.getTheVideoPath(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityXmediaPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val renderer = SoftwareCanvasRenderer(binding.surfaceView)
        val audioEngine: AudioEngine = OpenSlAudioEngine()
        val videoEngine: VideoEngine = FfmpegVideoEngine()
        xPlayer = XMediaPlayer(audioEngine, videoEngine, renderer)

        // Surface is technically not needed by SoftwareCanvasRenderer,
        // but we call setSurface for consistency:
        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                xPlayer.setSurface(holder.surface)
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // optional: pause/stop
            }
        })

        xPlayer.listener = object : XMediaPlayerListener {

            override fun onPrepared(durationMs: Long) {
                LogUtil.i("onPrepared")
                binding.btnPlay.isEnabled = true
                this@XMediaPlayerActivity.durationMs = durationMs
                binding.btnPlay.isEnabled = true
                binding.btnPause.isEnabled = true
                binding.seekBar.max = durationMs.toInt()
                binding.txtDuration.text = formatMillisecond(durationMs)
            }

            override fun onCompletion() {
                LogUtil.i("onCompletion")
                binding.btnPlay.isEnabled = true
                binding.btnPause.isEnabled = false
                binding.btnResume.isEnabled = false
            }

            override fun onProgress(positionMs: Long) {
                LogUtil.i("onProgress--durationMs:$durationMs--positionMs:$positionMs")
                if (!isUserSeeking && durationMs > 0) {
                    binding.seekBar.progress = positionMs.toInt()
                    binding.txtCurrent.text = formatMillisecond(positionMs)
                }
            }
        }

        binding.btnPrepare.setOnClickListener {
            val path = demoPath
            if (!File(path).exists()) {
                ToastUtils.showShort("File not found: $path")
                return@setOnClickListener
            }
            binding.btnPrepare.isEnabled = false
            xPlayer.prepare(path)
        }

        binding.btnPlay.setOnClickListener {
            xPlayer.play()
            binding.btnPlay.isEnabled = false
            binding.btnPause.isEnabled = true
            binding.btnResume.isEnabled = false
        }

        binding.btnPause.setOnClickListener {
            xPlayer.pause()
            binding.btnPause.isEnabled = false
            binding.btnResume.isEnabled = true
        }

        binding.btnResume.setOnClickListener {
            xPlayer.resume()
            binding.btnPause.isEnabled = true
            binding.btnResume.isEnabled = false
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            private var pendingProgress = 0

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (isUserSeeking && fromUser) {
                    pendingProgress = progress
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
                xPlayer.pause()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                xPlayer.seekTo(pendingProgress.toLong())
                xPlayer.resume()
            }

        })
    }

    override fun onDestroy() {
        super.onDestroy()
        xPlayer.release()
    }
}