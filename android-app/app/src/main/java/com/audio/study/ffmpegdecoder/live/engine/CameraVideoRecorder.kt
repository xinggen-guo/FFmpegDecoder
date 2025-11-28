package com.audio.study.ffmpegdecoder.live.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import android.view.TextureView
import androidx.core.app.ActivityCompat
import java.io.File

/**
 * Camera + GL-filtered video recorder.
 *
 * Pipeline:
 *   Camera → cameraSurface (from GlFilterRenderer)
 *   GlFilterRenderer → encoderSurface (MediaCodec input) + previewSurface (TextureView)
 */
class CameraVideoRecorder(
    private val context: Context
) {

    // Camera2
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var glRenderer: GlFilterRenderer? = null

    // Encoder + muxer
    private var codec: MediaCodec? = null
    private var codecInputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var videoTrackIndex: Int = -1
    private var muxerStarted = false
    @Volatile private var running = false

    // Config
    var width: Int = 1280
    var height: Int = 720
    var bitRate: Int = 4_000_000
    var frameRate: Int = 30

    var isRecording: Boolean = false
        private set

    fun start(previewView: TextureView, outputFile: File) {
        if (isRecording) return
        isRecording = true

        setupVideoEncoder(outputFile)

        if (previewView.isAvailable) {
            openCameraAndCreateSession(previewView)
        } else {
            previewView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCameraAndCreateSession(previewView)
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        running = false

        // stop GL renderer first
        glRenderer?.stop()
        glRenderer = null

        // signal EOS to encoder
        try {
            codec?.signalEndOfInputStream()
        } catch (_: Exception) {}

        // close camera session
        try { captureSession?.close() } catch (_: Exception) {}
        captureSession = null

        try { cameraDevice?.close() } catch (_: Exception) {}
        cameraDevice = null
    }

    // --------------------------------------------------
    // Encoder
    // --------------------------------------------------

    private fun setupVideoEncoder(outputFile: File) {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            width,
            height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        codecInputSurface = codec!!.createInputSurface()
        muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        codec!!.start()
        running = true
        startDrainThread()
    }

    private fun startDrainThread() {
        Thread {
            val bufferInfo = MediaCodec.BufferInfo()
            val c = codec ?: return@Thread
            val m = muxer ?: return@Thread

            while (running) {
                val outIndex = c.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    continue
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        // should not happen
                    }
                    val newFormat = c.outputFormat
                    videoTrackIndex = m.addTrack(newFormat)
                    m.start()
                    muxerStarted = true
                } else if (outIndex >= 0) {
                    val encoded = c.getOutputBuffer(outIndex) ?: continue
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        m.writeSampleData(videoTrackIndex, encoded, bufferInfo)
                    }
                    c.releaseOutputBuffer(outIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        break
                    }
                }
            }

            try { c.stop() } catch (_: Exception) {}
            c.release()

            try {
                if (muxerStarted) m.stop()
            } catch (_: Exception) {}
            m.release()
        }.start()
    }

    // --------------------------------------------------
    // Camera2 + preview
    // --------------------------------------------------

    private fun openCameraAndCreateSession(previewView: TextureView) {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = manager.cameraIdList.firstOrNull() ?: return

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // caller must have requested permission
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
        val encSurface = codecInputSurface ?: return

//        val renderer = GlFilterRenderer(
//            encoderSurface = encSurface,
//            previewSurface = previewSurface,
//            width = width,
//            height = height
//        )
//        glRenderer = renderer
//        renderer.start()
//        val cameraSurface = renderer.getCameraSurfaceBlocking(1000) ?: return


        val surfaces = listOf(previewSurface,encSurface)
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
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            null
        )
    }
}