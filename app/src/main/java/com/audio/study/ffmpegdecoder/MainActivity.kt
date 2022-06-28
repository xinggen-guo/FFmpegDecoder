package com.audio.study.ffmpegdecoder

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.TimeUtils
import android.view.DragEvent
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.audiotracke.NativePlayController
import com.audio.study.ffmpegdecoder.audiotracke.NativePlayer
import com.audio.study.ffmpegdecoder.databinding.ActivityMainBinding
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.LogUtil
import com.audio.study.ffmpegdecoder.utils.formatSecond
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mp3CreateFile.setOnClickListener {
            val path = application.externalCacheDir?.absolutePath + File.separator + "audio_study"
            val result = FileUtil.createOrExistsDir(path)
            LogUtil.i(TAG, "createResult:${result}---->path:${path}")
        }

        binding.audioTrackerPlay.setOnClickListener {
            startActivity(Intent(this,AudioTrackerActivity::class.java))
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