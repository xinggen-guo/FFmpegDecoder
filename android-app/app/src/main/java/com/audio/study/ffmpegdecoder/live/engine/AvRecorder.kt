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
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author xinggen.guo
 * @date 2025/11/23 17:09
 * Record camera video + mic audio into a single MP4 file.
 *
 * Usage:
 *   val recorder = AvRecorder(context)
 *   recorder.start(previewView, outputFile)
 *   ...
 *   recorder.stop()
 */
class AvRecorder(
    private val context: Context,
    private val sampleRate: Int = 48000,
    private val channelCount: Int = 1
) {

    // -------- Camera --------
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null

    // -------- Video encoder --------
    private var videoCodec: MediaCodec? = null
    private var videoSurface: Surface? = null
    private var videoTrackIndex: Int = -1
    private val videoEos = AtomicBoolean(false)

    // -------- Audio encoder + capture --------
    private var audioCodec: MediaCodec? = null
    private var audioTrackIndex: Int = -1
    private var audioRecord: AudioRecord? = null
    private val audioEos = AtomicBoolean(false)

    // -------- Muxer --------
    private var muxer: MediaMuxer? = null
    @Volatile private var muxerStarted = false
    private val muxerLock = Any()

    // -------- Recording state --------
    private val recording = AtomicBoolean(false)
    var isRecording: Boolean
        get() = recording.get()
        private set(value) = recording.set(value)

    // Common time base for A/V sync
    private var startTimeNs: Long = 0L

    // Config
    var width: Int = 1280
    var height: Int = 720
    var videoBitrate: Int = 4_000_000
    var frameRate: Int = 30
    var audioBitrate: Int = 128_000

    // --------------------------------------------------
    // Public API
    // --------------------------------------------------

    fun start(previewView: TextureView, outputFile: File) {
        if (isRecording) return
        isRecording = true

        startTimeNs = System.nanoTime()

        // 1) Prepare muxer
        muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        // 2) Setup encoders
        setupVideoEncoder()
        setupAudioEncoder()
        setupAudioRecord()

        // 3) Start encoder threads
        startVideoDrainThread()
        startAudioThread()

        // 4) Setup camera preview + encoder surface
        if (previewView.isAvailable) {
            openCamera(previewView)
        } else {
            previewView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, w: Int, h: Int) {
                    openCamera(previewView)
                }
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false

        // 1) Stop audio capture
        audioRecord?.run {
            try {
                stop()
            } catch (_: Exception) {}
            release()
        }
        audioRecord = null

        // 2) Signal EOS to video encoder (surface input)
        try {
            videoCodec?.signalEndOfInputStream()
        } catch (_: Exception) {}

        // 3) Close camera
        try {
            captureSession?.close()
        } catch (_: Exception) {}
        captureSession = null

        try {
            cameraDevice?.close()
        } catch (_: Exception) {}
        cameraDevice = null
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

            try {
                while (true) {
                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!isRecording) {
                            // no more input coming, but still wait for EOS
                        }
                        continue
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (videoTrackIndex == -1) {
                            val newFormat = codec.outputFormat
                            videoTrackIndex = muxer!!.addTrack(newFormat)
                            tryStartMuxer()
                        }
                    } else if (outIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outIndex) ?: continue

                        if (bufferInfo.size > 0 && muxerStarted) {
                            // Override PTS with our shared clock
                            bufferInfo.presentationTimeUs =
                                (System.nanoTime() - startTimeNs) / 1000L

                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer!!.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                        }

                        val eos =
                            (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outIndex, false)

                        if (eos) {
                            videoEos.set(true)
                            break
                        }
                    }
                }
            } catch (_: Exception) {
                // swallow encoder/muxer exceptions here; release below
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {}
                codec.release()
                tryStopMuxerIfDone()
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
            val bytesPerFrame = channelCount * 2 // 16-bit

            try {
                while (isRecording) {
                    val read = record.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read <= 0) continue

                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuf = inputBuffers[inputIndex]
                        inputBuf.clear()
                        inputBuf.put(pcmBuffer, 0, read)

                        val ptsUs = (System.nanoTime() - startTimeNs) / 1000L

                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            read,
                            ptsUs,
                            0
                        )
                    }

                    // Drain encoded buffers
                    drainAudioCodec(codec, bufferInfo)
                }

                // After stop(): send EOS
                val eosIndex = codec.dequeueInputBuffer(10_000)
                if (eosIndex >= 0) {
                    val ptsUs = (System.nanoTime() - startTimeNs) / 1000L
                    codec.queueInputBuffer(
                        eosIndex,
                        0,
                        0,
                        ptsUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }

                // Drain until EOS output
                drainAudioCodec(codec, bufferInfo, waitForEos = true)
                audioEos.set(true)
            } catch (_: Exception) {
                // ignore codec/muxer errors here, release below
            } finally {
                try {
                    codec.stop()
                } catch (_: Exception) {}
                codec.release()
                tryStopMuxerIfDone()
            }
        }.start()
    }

    private fun drainAudioCodec(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        waitForEos: Boolean = false
    ) {
        var done = false
        while (!done) {
            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!waitForEos) done = true
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (audioTrackIndex == -1) {
                        val newFormat = codec.outputFormat
                        audioTrackIndex = muxer!!.addTrack(newFormat)
                        tryStartMuxer()
                    }
                }

                outIndex >= 0 -> {
                    val encoded = codec.getOutputBuffer(outIndex) ?: break

                    if (bufferInfo.size > 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer!!.writeSampleData(audioTrackIndex, encoded, bufferInfo)
                    }

                    val eos =
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    codec.releaseOutputBuffer(outIndex, false)
                    if (eos) {
                        done = true
                    }
                }
            }
        }
    }

    // --------------------------------------------------
    // Muxer helpers
    // --------------------------------------------------

    private fun tryStartMuxer() {
        synchronized(muxerLock) {
            if (muxerStarted) return
            if (videoTrackIndex < 0 || audioTrackIndex < 0) return
            val m = muxer ?: return

            m.start()
            muxerStarted = true
        }
    }

    private fun tryStopMuxerIfDone() {
        synchronized(muxerLock) {
            if (!muxerStarted) return
            if (!videoEos.get() || !audioEos.get()) return

            try {
                muxer?.stop()
            } catch (_: Exception) {}
            muxer?.release()
            muxer = null
            muxerStarted = false
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

        val surfaces = listOf(previewSurface, encSurface)

        val requestBuilder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
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