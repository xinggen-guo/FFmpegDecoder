package com.audio.study.ffmpegdecoder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.SeekBar
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.audio.study.ffmpegdecoder.audiotracke.AudioDecoderImpl
import com.audio.study.ffmpegdecoder.databinding.ActivityLiveAudioBinding
import com.audio.study.ffmpegdecoder.live.engine.OpenSlLiveAudioEngine
import com.audio.study.ffmpegdecoder.utils.FileUtil
import com.audio.study.ffmpegdecoder.utils.WavWriter
import java.io.File

class LiveAudioActivity : ComponentActivity() {

    private lateinit var binding: ActivityLiveAudioBinding

    private val liveEngine = OpenSlLiveAudioEngine()
    private val bgmDecoder = AudioDecoderImpl()

    // --- WAV recording state ---

    private var wavWriter: WavWriter? = null

    private var totalPcmBytes: Long = 0

    private val recordSampleRate = 44100
    private val recordChannels = 1
    private val recordBitsPerSample = 16

    // make it nullable, because FileUtil may return null / empty
    private var bgmPath: String? = null

    // BGM ring buffer: stereo (L/R interleaved), ~4s @ 44.1k
    // 4 seconds * 1ch? we store shorts, but we treat as stereo frames
    private val bgmRing = ShortArray(44100 * 4)
    private var bgmReadPos = 0          // index to read from (short index)
    private var bgmWritePos = 0         // index to write to
    private var bgmCount = 0            // how many valid *shorts* in ring

    // number of valid shorts in ring
    private var micGain: Float = 0.8f   // default 80%
    private var bgmGain: Float = 0.6f   // default 60%

    // temp buffer for decoder output
    private val bgmTemp = ShortArray(4096)

    // mixed output (mono, because mic is mono)
    private val mixedBuffer = ShortArray(4096)

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

        binding.btnSetBgmPath.setOnClickListener {
            val path = FileUtil.getTheAudioPath(this)
            bgmPath = path

            val metaArray = intArrayOf(0, 0, 0)
            bgmDecoder.getMusicMetaByPath(path, metaArray)

            binding.tvStatus.text = if (!path.isNullOrEmpty()) {
                "BGM: $path"
            } else {
                "BGM not selected"
            }
        }

        setGain()
    }

    private fun setGain() {
        // init defaults
        micGain = 0.8f
        bgmGain = 0.6f

        binding.seekMicGain.max = 100
        binding.seekMicGain.progress = 80
        binding.seekBgmGain.max = 100
        binding.seekBgmGain.progress = 60

        binding.tvMicGainLabel.text = "Mic Gain: 80%"
        binding.tvBgmGainLabel.text = "BGM Gain: 60%"

        // Mic gain seekbar
        binding.seekMicGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                micGain = progress / 100f
                binding.tvMicGainLabel.text = "Mic Gain: $progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // BGM gain seekbar
        binding.seekBgmGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                bgmGain = progress / 100f
                binding.tvBgmGainLabel.text = "BGM Gain: $progress%"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
        val path = bgmPath
        if (path.isNullOrEmpty()) {
            binding.tvStatus.text = "No BGM selected"
            return
        }

        liveEngine.setSpeakerMonitorEnabled(true)

        // 1) live engine
        val ok = liveEngine.prepare(
            sampleRate = recordSampleRate,
            channels = recordChannels,
            bufferMs = 20
        )
        if (!ok) {
            binding.tvStatus.text = "Status: init failed"
            return
        }


        // 2) BGM decoder
        val bgmOk = bgmDecoder.prepare(path)
        if (!bgmOk) {
            binding.tvStatus.text = "BGM decode prepare failed"
            return
        }

        try {
            val output = File(FileUtil.getFileExternalCachePath(this), "mixed_record_${System.currentTimeMillis()}.wav")
            wavWriter = WavWriter(output, recordSampleRate, recordChannels, recordBitsPerSample)
            wavWriter?.start()

            binding.tvStatus.text = "Recording PCM to: ${output?.absolutePath}"
        } catch (e: Exception) {
            binding.tvStatus.text = "Failed to open PCM file: ${e.message}"
        }

        // Optional: user of mixed PCM (recording / streaming)
        liveEngine.setOnMixedPcm { mixedPcm, size ->
            // TODO: send over network / save file, etc.
        }

        // 3) MIC callback – we do ALL mixing here
        // 3) MIC callback – ALL mixing here
        liveEngine.setOnPcmCaptured { micPcm, micSize ->
            // micSize: mono samples (44.1kHz, ~20ms)

            val micFrames = micSize                           // 1 frame = 1 mono sample
            val neededBgmShorts = micFrames * 2               // BGM is stereo → need L+R per frame

            // ----------------------------------------------------------
            // 1) Ensure ring buffer has enough BGM samples for this callback
            // ----------------------------------------------------------
            while (bgmCount < neededBgmShorts) {
                val read = bgmDecoder.readSamples(bgmTemp)    // bgmTemp also contains stereo PCM
                if (read <= 0) break                          // EOF or read error
                pushBgmToRing(bgmTemp, read)                  // push into stereo ring buffer
            }

            val framesToMix = micFrames

            // ----------------------------------------------------------
            // 2) Mix MIC (mono) + BGM (stereo → mono)
            //    Correct speed: 1 BGM frame per 1 mic frame.
            // ----------------------------------------------------------
            for (i in 0 until framesToMix) {
                val mic = micPcm[i].toInt()

                val bgmMono = if (bgmCount >= 2) {
                    popBgmFrame()                             // returns (L+R)/2 as mono
                } else {
                    0                                         // BGM not ready → silent
                }

                // apply gain
                val mixed = (mic * micGain + bgmMono * bgmGain).toInt()
                mixedBuffer[i] = mixed
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            // ----------------------------------------------------------
            // 3) Send mixed PCM to user listener + speaker
            // ----------------------------------------------------------
            liveEngine.dispatchMixedPcm(mixedBuffer, framesToMix)

            wavWriter?.writeSamples(mixedBuffer, framesToMix)

            liveEngine.pushMixedPcmToSpeaker(mixedBuffer, framesToMix)

            // ----------------------------------------------------------
            // 4) Update UI level using the mixed signal
            // ----------------------------------------------------------
            var sum = 0L
            for (i in 0 until framesToMix) {
                val v = mixedBuffer[i].toInt()
                sum += v * v
            }
            val rms = kotlin.math.sqrt(sum / framesToMix.toDouble())
            val level = (rms / 32768.0 * 100).toInt().coerceIn(0, 100)

            runOnUiThread {
                binding.progressLevel.progress = level
            }
        }
        liveEngine.startLoopback()
        binding.tvStatus.text = "Status: running"
    }

    private fun stopLive() {
        // stop BGM
        bgmDecoder.destory()

        // stop live engine
        liveEngine.stopLoopback()
        liveEngine.release()

        binding.tvStatus.text = "Status: ${wavWriter?.getFilePath()}"
        // --- close PCM stream ---
        wavWriter?.stop()
        wavWriter = null

        totalPcmBytes = 0L

        binding.progressLevel.progress = 0
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    // --------------------------------------------------------------
    // pushBgmToRing: append `size` shorts from decoder into ring
    // --------------------------------------------------------------
    private fun pushBgmToRing(src: ShortArray, size: Int) {
        val n = size.coerceAtMost(src.size)
        val cap = bgmRing.size

        for (i in 0 until n) {
            bgmRing[bgmWritePos] = src[i]
            bgmWritePos = (bgmWritePos + 1) % cap

            if (bgmCount < cap) {
                bgmCount++
            } else {
                // full → drop oldest one short
                bgmReadPos = (bgmReadPos + 1) % cap
            }
        }
    }

    /**
     * Pop one *frame* of BGM (stereo -> mono).
     * Returns mono sample (L+R)/2, or 0 if not enough data.
     */
    private fun popBgmFrame(): Int {
        // need at least L + R
        if (bgmCount < 2) return 0

        val cap = bgmRing.size
        val L = bgmRing[bgmReadPos].toInt()
        val R = bgmRing[(bgmReadPos + 1) % cap].toInt()

        bgmReadPos = (bgmReadPos + 2) % cap
        bgmCount -= 2

        return (L + R) / 2      // stereo -> mono
    }

}