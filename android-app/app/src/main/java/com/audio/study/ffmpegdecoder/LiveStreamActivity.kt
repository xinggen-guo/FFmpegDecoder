package com.audio.study.ffmpegdecoder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.audio.study.ffmpegdecoder.databinding.ActivityLiveStreamBinding
import com.audio.study.ffmpegdecoder.live.engine.AvLiveStreamer
import com.audio.study.ffmpegdecoder.live.engine.FlvMuxSink
import com.audio.study.ffmpegdecoder.live.interfaces.LiveStreamSink
import com.audio.study.ffmpegdecoder.net.NetworkFlvSink
import com.audio.study.ffmpegdecoder.utils.FileUtil
import java.io.File

/**
 * @author xinggen.guo
 * @date 2025/11/25 15:49
 * @description
 */
class LiveStreamActivity : ComponentActivity() {

    private lateinit var binding: ActivityLiveStreamBinding
    private var streamer: AvLiveStreamer? = null
    private var liveSink: LiveStreamSink? = null

    private var setNetwork = true

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true
        val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true

        if (cameraGranted && audioGranted) {
            startStreaming()
        } else {
            // You can show a Toast or text here if you like
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveStreamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStartLive.setOnClickListener {
            checkPermissionsAndStart()
        }

        binding.btnStopLive.setOnClickListener {
            stopLive()
        }
    }

    private fun checkPermissionsAndStart() {
        val missing = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.CAMERA
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.RECORD_AUDIO
        }

        if (missing.isEmpty()) {
            startStreaming()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startStreaming() {
        if (streamer != null && streamer!!.isRecording) return
        if (setNetwork) {
            val host = BuildConfig.STREAM_HOST
            val port = BuildConfig.STREAM_PORT
            liveSink = NetworkFlvSink(host, port)
        } else {
            val outputFile = File(FileUtil.getFileExternalCachePath(this), "flv_mux_${System.currentTimeMillis()}.flv")
            liveSink = FlvMuxSink(outputFile.outputStream())
        }

        streamer = AvLiveStreamer(this, liveSink!!)
        streamer!!.start(binding.previewView)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLive()
    }

    private fun stopLive() {
        streamer?.stop()
        streamer = null

        liveSink?.close()
        liveSink = null
    }
}