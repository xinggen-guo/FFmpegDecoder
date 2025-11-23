package com.audio.study.ffmpegdecoder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.audio.study.ffmpegdecoder.databinding.ActivityAvRecordBinding
import com.audio.study.ffmpegdecoder.live.engine.AvRecorder
import com.audio.study.ffmpegdecoder.utils.FileUtil
import java.io.File

/**
 * @author xinggen.guo
 * @date 2025/11/23 17:07
 * @description
 */
class AvRecordActivity : ComponentActivity() {

    private lateinit var binding: ActivityAvRecordBinding
    private lateinit var avRecorder: AvRecorder

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val cameraGranted = result[Manifest.permission.CAMERA] == true
        val audioGranted = result[Manifest.permission.RECORD_AUDIO] == true

        if (cameraGranted && audioGranted) {
            startAvRecording()
        } else {
            // TODO: show a Toast / label if you like
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAvRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        avRecorder = AvRecorder(this)

        binding.btnStart.setOnClickListener {
            checkPermissionsAndStart()
        }

        binding.btnStop.setOnClickListener {
            stopAvRecording()
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
            startAvRecording()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startAvRecording() {
        val outFile = File(
            FileUtil.getFileExternalCachePath(this),
            "live_av_${System.currentTimeMillis()}.mp4"
        )
        // preview + record into one MP4
        avRecorder.start(binding.previewView, outFile)
    }

    private fun stopAvRecording() {
        avRecorder.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        avRecorder.stop()
    }
}