package com.audio.study.ffmpegdecoder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.common.Constants
import com.audio.study.ffmpegdecoder.databinding.ActivityMainBinding
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.LogUtil
import com.audio.study.ffmpegdecoder.utils.ToastUtils
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ToastUtils.init(this.applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mp3CreateFile.setOnClickListener {
            val path = application.externalCacheDir?.absolutePath + File.separator + Constants.NAME_DIR
            val result = FileUtil.createOrExistsDir(path)
            LogUtil.i(TAG, "createResult:${result}---->path:${path}")

            Thread {
                val pathAudio = FileUtil.getTheAudioPath(this)
                val pathVideo = FileUtil.getTheVideoPath(this)
                FileUtil.copyFilesAssets(this, Constants.NAME_AUDIO, pathAudio)
                FileUtil.copyFilesAssets(this, Constants.NAME_VIDEO, pathVideo)
            }.start()
        }

        binding.audioTrackerPlay.setOnClickListener {
            startActivity(Intent(this, AudioTrackerActivity::class.java))
        }

        binding.audioOpenSLES.setOnClickListener {
            startActivity(Intent(this, AudioOpenSLESActivity::class.java))
        }
        binding.audioOpenGL.setOnClickListener {
            startActivity(Intent(this, OpenGLActivity::class.java))
        }
        binding.videoSample.setOnClickListener {
            startActivity(Intent(this, VideoPlayerActivity::class.java))
        }
        binding.videoAudioSyncSample.setOnClickListener {
            startActivity(Intent(this, AudioVideoSyncActivity::class.java))
        }
        binding.XMediaPlayerSample.setOnClickListener {
            startActivity(Intent(this, XMediaPlayerActivity::class.java))
        }
    }

    /**
     * A native method that is implemented by the 'ffmpegdecoder' native library,
     * which is packaged with this application.
     */
    companion object {
        private const val TAG = "MainActivity"
    }
}