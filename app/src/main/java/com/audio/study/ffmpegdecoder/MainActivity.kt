package com.audio.study.ffmpegdecoder

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

    private var nativePlayController: NativePlayController? = null

    private var handler = Handler()

    val path by lazy { application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "input.mp3" }
//    val path = application.externalCacheDir?.absolutePath + File.separator + "audio_study" + File.separator + "AlizBonita.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.sampleText.text = stringFromJNI()

        binding.mp3CreateFile.setOnClickListener {
            val path = application.externalCacheDir?.absolutePath + File.separator + "audio_study"
            val result = FileUtil.createOrExistsDir(path)
            LogUtil.i(TAG, "createResult:${result}---->path:${path}")
        }

//        binding.audioTrackStart.setOnClickListener {
//            Thread {
//                playAudioTest(path)
//            }.start()
//
//        }
//
//        binding.audioTrackEnd.setOnClickListener {
//            stopAudioTest()
//        }

        nativePlayController = NativePlayController()
        nativePlayController?.setPlayListener(object :NativePlayer.OnPlayListener{
            override fun onReady() {
                val duration = nativePlayController?.getDuration() ?: 0
                binding.audioProgress.max = duration
                binding.duration.text = formatSecond(duration.toLong())
            }
        })
        nativePlayController?.setAudioDataSource(path)

        binding.audioTrackStart.setOnClickListener {
            nativePlayController?.start()
            startUpdateAudioProgress()
        }

        binding.audioTrackPause.setOnClickListener {
            nativePlayController?.pause()
        }

        binding.audioTrackStop.setOnClickListener {
            stopUpdateAudioProgress()
            nativePlayController?.stop()
        }


        binding.audioProgress.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener{

            var isSeek = false

            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if(isSeek) {
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