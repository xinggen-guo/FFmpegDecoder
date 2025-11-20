package com.audio.study.ffmpegdecoder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.audio.study.ffmpegdecoder.databinding.ActivityLiveAudioBinding
import com.audio.study.ffmpegdecoder.live.engine.OpenSlLiveAudioEngine

class LiveAudioActivity : ComponentActivity() {

    private lateinit var binding:ActivityLiveAudioBinding

    private val liveEngine = OpenSlLiveAudioEngine()

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                setupAndStart()
            } else {
                binding.tvStatus.text = "Status: mic permission denied"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLiveAudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnStart.setOnClickListener { startLive() }
        binding.btnStop.setOnClickListener { stopLive() }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLive()
    }

    private fun startLive() {
        if (!hasMicPermission()) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        setupAndStart()
    }

    private fun setupAndStart() {
        val ok = liveEngine.prepare(sampleRate = 44100, channels = 1, bufferMs = 20)
        if (!ok) {
            binding.tvStatus.text = "Status: init failed"
            return
        }

        liveEngine.setOnPcmCaptured { pcm, size ->
            // Calculate RMS level
            var sum = 0L
            for (i in 0 until size) sum += (pcm[i].toInt() * pcm[i].toInt())
            val rms = kotlin.math.sqrt(sum / size.toDouble())
            val level = (rms / 32768.0 * 100).toInt().coerceIn(0, 100)

            runOnUiThread {
                binding.progressLevel.progress = level
            }
        }

        liveEngine.startLoopback()
        binding.tvStatus.text = "Status: running"
    }

    private fun stopLive() {
        liveEngine.stopLoopback()
        liveEngine.release()
        binding.tvStatus.text = "Status: stopped"
        binding.progressLevel.progress = 0
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}