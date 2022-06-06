package com.audio.study.ffmpegdecoder

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sampleText.text = stringFromJNI()

        binding.mp3CreateFile.setOnClickListener {
            val path = application.externalCacheDir?.absolutePath + File.separator + "audio_study"
            val result = FileUtil.createOrExistsDir(path)
            Log.i(TAG,"createResult:${result}---->path:${path}")
        }

        binding.audioTrackStart.setOnClickListener {
            Thread {
//                val path = application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "input.mp3"
                val path = application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "AlizBonita.mp4"
                playAudioTest(path)
            }.start()

        }

        binding.audioTrackEnd.setOnClickListener {
            stopAudioTest()
        }
    }


    /**
     * A native method that is implemented by the 'ffmpegdecoder' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    external fun playAudioTest(audioPath: String)

    external fun stopAudioTest()

    companion object {

        private const val TAG = "MainActivity"

        // Used to load the 'ffmpegdecoder' library on application startup.
        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}