package com.audio.study.ffmpegdecoder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.databinding.ActivityVideoPlayerBinding
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import java.io.File

/**
 * @author xinggen.guo
 * @date 2025/11/14 19:45
 * @description
 */

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVideoPlayerBinding

    // For demo, we just use a hardcoded path.
    // You should replace this with your actual file path selector.
    val demoVideoPath by lazy { FileUtil.getTheVideoPath(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.simpleVideoView.onPrepared = { w, h ->
            // Called when prepare is finished
            ToastUtils.showShort("Prepared: ${w}x${h}, you can press Play now")
            binding.btnPlay.isEnabled = true
        }

        binding.btnPrepare.setOnClickListener {
            val path = demoVideoPath
            if (!File(path).exists()) {
                ToastUtils.showShort("File not found: $path")
            } else {
                binding.btnPrepare.isEnabled = false
                binding.simpleVideoView.prepare(path)
            }
        }

        binding.btnPlay.setOnClickListener {
            binding.simpleVideoView.play()
        }

        binding.btnPause.setOnClickListener {
            binding.simpleVideoView.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.simpleVideoView.release()
    }
}