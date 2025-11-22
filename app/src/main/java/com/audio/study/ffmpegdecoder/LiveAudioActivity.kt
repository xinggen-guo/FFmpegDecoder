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
import kotlin.math.min

class LiveAudioActivity : ComponentActivity() {

    private lateinit var binding: ActivityLiveAudioBinding

    private val liveEngine = OpenSlLiveAudioEngine()
    private val bgmDecoder = AudioDecoderImpl()

    // make it nullable, because FileUtil may return null / empty
    private var bgmPath: String? = null

    // BGM ring buffer: stereo (L/R interleaved), ~4s @ 44.1k
    private val bgmRing = ShortArray(44100 * 4)   // 4 seconds * 1ch? we store shorts, but we treat as stereo frames
    private var bgmWrite = 0
    private var bgmRead = 0
    private var bgmCount = 0                     // number of valid shorts in ring

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
            bgmDecoder.getMusicMetaByPath(path,metaArray)

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
        val ok = liveEngine.prepare(sampleRate = 44100, channels = 1, bufferMs = 20)
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

        // Optional: user of mixed PCM (recording / streaming)
        liveEngine.setOnMixedPcm { mixedPcm, size ->
            // TODO: send over network / save file, etc.
        }

        // 3) MIC callback – we do ALL mixing here
        liveEngine.setOnPcmCaptured { micPcm, micSize ->
            // micSize = number of mono samples from mic (44.1kHz, ~20ms)
            val micFrames = micSize
            val neededBgmShorts = micFrames * 2   // stereo: L+R per frame

            // 1) Make sure ring buffer has enough BGM for this callback
            while (bgmCount < neededBgmShorts) {
                val read = bgmDecoder.readSamples(bgmTemp)
                if (read <= 0) break          // EOF or no more data
                pushBgmToRing(bgmTemp, read)
            }

            val framesToMix = micFrames

            // 2) Mix mic + BGM (1 BGM frame per mic frame → correct speed)
            for (i in 0 until framesToMix) {
                val mic = micPcm[i].toInt()

                val bgmMono = if (bgmCount >= 2) {
                    popBgmFrame()
                } else {
                    0
                }

                val mixed = (mic * this.micGain + bgmMono * this.bgmGain).toInt()
                mixedBuffer[i] = mixed
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }

            // 3) Send mixed PCM out
            liveEngine.dispatchMixedPcm(mixedBuffer, framesToMix)
            liveEngine.pushMixedPcmToSpeaker(mixedBuffer, framesToMix)

            // 4) UI level from mixed signal
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

        binding.tvStatus.text = "Status: stopped"
        binding.progressLevel.progress = 0
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Push `count` shorts from src into the BGM ring buffer.
     * If there is not enough space, we drop the oldest samples.
     */
    private fun pushBgmToRing(src: ShortArray, count: Int) {
        if (count <= 0) return

        // we only keep at most ring.size shorts
        var toWrite = count.coerceAtMost(bgmRing.size)

        val free = bgmRing.size - bgmCount
        if (toWrite > free) {
            // no space → drop the oldest data
            val drop = toWrite - free
            bgmRead = (bgmRead + drop) % bgmRing.size
            bgmCount -= drop
        }

        var remaining = toWrite
        var srcIndex = count - toWrite   // keep the newest part

        while (remaining > 0) {
            val chunk = min(remaining, bgmRing.size - bgmWrite)
            src.copyInto(
                destination = bgmRing,
                destinationOffset = bgmWrite,
                startIndex = srcIndex,
                endIndex = srcIndex + chunk
            )
            bgmWrite = (bgmWrite + chunk) % bgmRing.size
            srcIndex += chunk
            remaining -= chunk
        }

        bgmCount += toWrite
    }

    /**
     * Pop one *frame* of BGM (stereo → mono).
     * Returns mono sample (L+R)/2, or 0 if not enough data.
     */
    private fun popBgmFrame(): Int {
        if (bgmCount < 2) return 0

        val l = bgmRing[bgmRead].toInt()
        val rIndex = (bgmRead + 1) % bgmRing.size
        val r = bgmRing[rIndex].toInt()

        bgmRead = (bgmRead + 2) % bgmRing.size
        bgmCount -= 2

        return (l + r) / 2
    }
}