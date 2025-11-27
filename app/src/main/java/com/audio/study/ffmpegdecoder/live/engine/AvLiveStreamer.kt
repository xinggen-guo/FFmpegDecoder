package com.audio.study.ffmpegdecoder.live.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import com.audio.study.ffmpegdecoder.live.interfaces.LiveStreamSink
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author xinggen.guo
 * @date 2025/11/25 15:46
 * Live encoder that captures camera video + mic audio,
 * encodes to H.264 + AAC and pushes frames to a LiveStreamSink.
 */
class AvLiveStreamer(
    private val context: Context,
    private val sink: LiveStreamSink,
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 1
) {

    // -------- Camera --------
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    // -------- Video encoder --------
    private var videoCodec: MediaCodec? = null
    private var videoSurface: Surface? = null

    private var glRenderer: GlFilterRenderer? = null

    // -------- Audio encoder + capture --------
    private var audioCodec: MediaCodec? = null
    private var audioRecord: AudioRecord? = null

    // -------- Recording state --------
    private val recording = AtomicBoolean(false)

    var enableBeauty: Boolean = true
    var isRecording: Boolean
        get() = recording.get()
        private set(value) = recording.set(value)

    // Shared time base for sync
    private var startTimeNs: Long = 0L

    // Track when encoders are finished to close sink only once
    private val finishedEncoders = AtomicInteger(0)

    // Config
    var width: Int = 1280
    var height: Int = 720
    var videoBitrate: Int = 4_000_000
    var frameRate: Int = 30
    var audioBitrate: Int = 128_000

    // --------------------------------------------------
    // Public API
    // --------------------------------------------------

    /**
     * Start live encoding & camera preview.
     */
    fun start(previewView: TextureView) {
        if (isRecording) return
        isRecording = true

        startTimeNs = System.nanoTime()

        // 1) Setup encoders
        setupVideoEncoder()
        setupAudioEncoder()
        setupAudioRecord()

        // 2) Start encoder threads
        startVideoDrainThread()
        startAudioThread()

        // 3) Start camera preview + encoder surface
        if (previewView.isAvailable) {
            openCamera(previewView)
        } else {
            previewView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera(previewView)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    /**
     * Stop streaming. Threads will finish draining and then close the sink.
     */
    fun stop() {
        if (!isRecording) return
        isRecording = false

        // 1) Stop audio capture
        audioRecord?.run {
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        audioRecord = null

        // 2) Signal EOS to video encoder (surface input)
        try {
            videoCodec?.signalEndOfInputStream()
        } catch (_: Exception) {
        }

        // 3) Close camera
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {
        }
        cameraDevice = null

        glRenderer?.stop()
        glRenderer = null
    }

    // --------------------------------------------------
    // Video encoder
    // --------------------------------------------------

    private fun setupVideoEncoder() {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        videoCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        videoSurface = videoCodec!!.createInputSurface()
        videoCodec!!.start()
    }

    private fun startVideoDrainThread() {
        Thread {
            val codec = videoCodec ?: return@Thread
            val bufferInfo = MediaCodec.BufferInfo()

            var sps: ByteArray? = null
            var pps: ByteArray? = null
            var configSent = false

            try {
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!isRecording) {
                                // no new input, but still wait for EOS
                            }
                        }

                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = codec.outputFormat
                            val csd0 = format.getByteBuffer("csd-0")
                            val csd1 = format.getByteBuffer("csd-1")
                            if (csd0 != null && csd1 != null) {
                                sps = csd0.toByteArray()
                                pps = csd1.toByteArray()
                                if (!configSent) {
                                    sink.onVideoConfig(sps!!, pps!!)
                                    configSent = true
                                }
                            }
                        }

                        outIndex >= 0 -> {
                            val encoded = codec.getOutputBuffer(outIndex) ?: continue

                            if (bufferInfo.size > 0) {
                                val isConfig =
                                    (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                                if (!isConfig) {
                                    val data = ByteArray(bufferInfo.size)
                                    encoded.position(bufferInfo.offset)
                                    encoded.limit(bufferInfo.offset + bufferInfo.size)
                                    encoded.get(data)

                                    val ptsUs =
                                        (System.nanoTime() - startTimeNs) / 1_000L
                                    val isKeyFrame =
                                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                                    if (!configSent && sps != null && pps != null) {
                                        sink.onVideoConfig(sps!!, pps!!)
                                        configSent = true
                                    }

                                    sink.onVideoFrame(data, ptsUs, isKeyFrame)
                                }
                            }

                            val eos =
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            codec.releaseOutputBuffer(outIndex, false)
                            if (eos) break
                        }
                    }
                }
            } catch (_: Exception) {
                // ignore, sink will be closed when both encoders finish
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {
                }
                codec.release()
                encoderFinished()
            }
        }.start()
    }

    // --------------------------------------------------
    // Audio encoder + capture
    // --------------------------------------------------

    private fun setupAudioEncoder() {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channelCount
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }

        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        audioCodec!!.start()
    }

    @SuppressLint("MissingPermission")
    private fun setupAudioRecord() {
        val channelConfig = if (channelCount == 1)
            AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            AudioFormat.ENCODING_PCM_16BIT,
            minBufSize
        )

        audioRecord!!.startRecording()
    }

    private fun startAudioThread() {
        Thread {
            val codec = audioCodec ?: return@Thread
            val record = audioRecord ?: return@Thread

            val pcmBuffer = ByteArray(4096)
            val bufferInfo = MediaCodec.BufferInfo()
            val inputBuffers = codec.inputBuffers

            var aacConfigSent = false
            var aacConfig: ByteArray? = null

            try {
                // Main capture loop
                while (isRecording) {
                    val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read <= 0) continue

                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuf = inputBuffers[inputIndex]
                        inputBuf.clear()
                        inputBuf.put(pcmBuffer, 0, read)

                        val ptsUs =
                            (System.nanoTime() - startTimeNs) / 1_000L

                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            read,
                            ptsUs,
                            0
                        )
                    }

                    // Drain available output
                    drainAudioCodec(codec, bufferInfo) { data, pts ->
                        if (!aacConfigSent && aacConfig != null) {
                            sink.onAudioConfig(aacConfig!!)
                            aacConfigSent = true
                        }
                        sink.onAudioFrame(data, pts)
                    }.also { cfg ->
                        if (cfg != null) {
                            aacConfig = cfg
                            if (!aacConfigSent) {
                                sink.onAudioConfig(aacConfig!!)
                                aacConfigSent = true
                            }
                        }
                    }
                }

                // After stop() is called: send EOS
                val eosIndex = codec.dequeueInputBuffer(10_000)
                if (eosIndex >= 0) {
                    val ptsUs = (System.nanoTime() - startTimeNs) / 1_000L
                    codec.queueInputBuffer(
                        eosIndex,
                        0,
                        0,
                        ptsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }

                // Drain until EOS
                drainAudioCodec(codec, bufferInfo) { data, pts ->
                    sink.onAudioFrame(data, pts)
                }
            } catch (_: Exception) {
                // ignore, close below
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {
                }
                codec.release()
                encoderFinished()
            }
        }.start()
    }

    /**
     * Drain AAC encoder.
     *
     * @param onFrame called for each encoded AAC frame
     * @return if AudioSpecificConfig (csd-0) is discovered, returns it once
     */
    private fun drainAudioCodec(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        onFrame: (data: ByteArray, ptsUs: Long) -> Unit
    ): ByteArray? {
        var config: ByteArray? = null

        while (true) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    break
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val format = codec.outputFormat
                    val csd0 = format.getByteBuffer("csd-0")
                    if (csd0 != null) {
                        config = csd0.toByteArray()
                    }
                }

                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex) ?: break
                    if (bufferInfo.size > 0) {
                        val data = ByteArray(bufferInfo.size)
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        encoded.get(data)

                        val ptsUs = bufferInfo.presentationTimeUs
                        onFrame(data, ptsUs)
                    }

                    val eos =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) break
                }
            }
        }

        return config
    }

    // --------------------------------------------------
    // Encoder completion & sink closing
    // --------------------------------------------------

    private fun encoderFinished() {
        val finished = finishedEncoders.incrementAndGet()
        // We have exactly 2 encoders: video + audio
        if (finished >= 2) {
            // All done, safe to close sink
            sink.close()
        }
    }

    // --------------------------------------------------
    // Camera2
    // --------------------------------------------------

    private fun openCamera(previewView: TextureView) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull() ?: return

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                createCaptureSession(device, previewView)
            }

            override fun onDisconnected(device: CameraDevice) {
                device.close()
                cameraDevice = null
            }

            override fun onError(device: CameraDevice, error: Int) {
                device.close()
                cameraDevice = null
            }
        }, null)
    }

    private fun createCaptureSession(device: CameraDevice, previewView: TextureView) {
        val texture = previewView.surfaceTexture ?: return
        texture.setDefaultBufferSize(width, height)
        val previewSurface = Surface(texture)
        val encSurface = videoSurface ?: return

        if (enableBeauty) {
            // ============= BEAUTY (GL) MODE =============
            // Camera → cameraSurface (from GlFilterRenderer)
            // GlFilterRenderer → encSurface + previewSurface

            val renderer = GlFilterRenderer(
                encoderSurface = encSurface,
                previewSurface = previewSurface,
                width = width,
                height = height
            )
            glRenderer = renderer
            renderer.start()

            // Wait until GL thread creates cameraSurface
            val cameraSurface = renderer.getCameraSurfaceBlocking(1000) ?: return

            // Camera writes ONLY into cameraSurface
            val surfaces = listOf(cameraSurface)

            val requestBuilder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(cameraSurface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(requestBuilder.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // ignore for now
                    }
                },
                null
            )
        } else {
            // ============= SIMPLE (NO GL) MODE =============
            // Camera → previewSurface + encSurface
            // No glRenderer, direct camera output.

            glRenderer?.stop()
            glRenderer = null

            val surfaces = listOf(previewSurface, encSurface)

            val requestBuilder =
                device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                    addTarget(previewSurface)
                    addTarget(encSurface)
                    set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                }

            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session
                        session.setRepeatingRequest(requestBuilder.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // ignore for now
                    }
                },
                null
            )
        }
    }
}

// small helper
private fun ByteBuffer.toByteArray(): ByteArray {
    val data = ByteArray(remaining())
    get(data)
    return data
}