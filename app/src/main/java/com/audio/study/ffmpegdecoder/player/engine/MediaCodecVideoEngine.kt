package com.audio.study.ffmpegdecoder.player.engine

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.view.Surface
import com.audio.study.ffmpegdecoder.common.MediaStatus
import com.audio.study.ffmpegdecoder.player.enum.DecodeType
import com.audio.study.ffmpegdecoder.player.interfaces.VideoEngine
import com.audio.study.ffmpegdecoder.utils.LogUtil
import java.io.IOException
import java.nio.ByteBuffer


/**
 * @author xinggen.guo
 * @date 2025/11/17 15:37
 * VideoEngine implementation using Android MediaCodec (hardware decode).
 *
 * - Decodes frames and renders directly to a Surface.
 * - XMediaPlayer still calls readFrameInto(...), but we ignore the ByteBuffer,
 *   and simply:
 *      * feed input buffers (Extractor -> Codec)
 *      * dequeue output buffers
 *      * release output buffers with render=true
 *      * return PTS (ms) for the displayed frame
 *
 * For this engine, should use a VideoRenderer that only manages the Surface.
 */
class MediaCodecVideoEngine : VideoEngine {

    companion object {
        private const val TAG = "MediaCodecVideoEngine"
    }

    override val decodeType: DecodeType = DecodeType.MEDIACODEC

    private var extractor: MediaExtractor? = null
    private var codec: MediaCodec? = null
    private var trackIndex: Int = -1

    private var width: Int = 0
    private var height: Int = 0
    private var durationMs: Long = 0L

    private var outputSurface: Surface? = null

    private var inputEOS = false
    private var outputEOS = false

    @Volatile
    private var prepared = false

    @Volatile
    private var started = false      // NEW: codec.start() state

    override fun setOutputSurface(surface: Surface?) {
        LogUtil.i(TAG, "setOutPutSurface: $surface")
       outputSurface = surface
    }

    override fun prepare(path: String): Boolean {
        LogUtil.i(TAG, "prepare: $path")

        val surface = outputSurface
        if (surface == null) {
            LogUtil.e(TAG, "prepare() called but outputSurface is null")
            return false
        }

        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
        } catch (e: IOException) {
            LogUtil.e(TAG, "MediaExtractor setDataSource failed: ${e.message}")
            extractor.release()
            return false
        }

        var videoTrackIdx = -1
        var videoFormat: MediaFormat? = null

        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) {
                videoTrackIdx = i
                videoFormat = format
                break
            }
        }

        if (videoTrackIdx < 0 || videoFormat == null) {
            LogUtil.e(TAG, "No video track found in file")
            extractor.release()
            return false
        }

        extractor.selectTrack(videoTrackIdx)
        trackIndex = videoTrackIdx

        // use helper (handles crop + rotation)
        updateSizeFromFormat(videoFormat)

        durationMs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
            videoFormat.getLong(MediaFormat.KEY_DURATION) / 1000L
        } else {
            0L
        }

        val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: return false

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: IOException) {
            LogUtil.e(TAG, "createDecoderByType failed: ${e.message}")
            extractor.release()
            return false
        }

        codec.configure(videoFormat, surface, null, 0)

        this.extractor = extractor
        this.codec = codec

        inputEOS = false
        outputEOS = false
        prepared = true

        LogUtil.i(
            TAG,
            "MediaCodecVideoEngine prepared: $mime ${width}x$height duration=${durationMs}ms"
        )

        return true
    }
    override fun start() {
        if (!prepared) {
            LogUtil.w(TAG, "start() called before prepare()")
            return
        }
        if (started) {
            // XMediaPlayer may call play() again (after EOF + seek),
            // but codec was already started → ignore, like FFmpeg path.
            LogUtil.d(TAG, "start() ignored, codec already started")
            return
        }

        try {
            codec?.start()
            started = true
            inputEOS = false
            outputEOS = false
            LogUtil.i(TAG, "MediaCodec started")
        } catch (e: IllegalStateException) {
            LogUtil.e(TAG, "codec.start() failed: ${e.message}")
        }
    }

    override fun pause() {
        // Pause is handled by XMediaPlayer (it just stops calling readFrameInto()).
        LogUtil.i(TAG, "pause() - no-op (handled by XMediaPlayer)")
    }

    override fun resume() {
        LogUtil.i(TAG, "resume() - no-op (handled by XMediaPlayer)")
    }

    override fun seekTo(positionMs: Long) {
        val extractor = this.extractor ?: return
        val codec = this.codec ?: return
        if (!prepared) return
        LogUtil.i(TAG, "seekTo: $positionMs ms")

        if(inputEOS || outputEOS){
            codec.flush()
        }
        // Reset EOS state so decoding can continue after seek (including after EOF)
        inputEOS = false
        outputEOS = false

        // Reposition extractor
        extractor.seekTo(positionMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
    }

    override fun release() {
        LogUtil.i(TAG, "release")
        releaseInternal()
        outputSurface = null
        prepared = false
        started = false
        inputEOS = false
        outputEOS = false
    }

    private fun releaseInternal() {
        try {
            if (started) {
                codec?.stop()
            }
        } catch (_: Exception) {
        }

        try {
            codec?.release()
        } catch (_: Exception) {
        }
        codec = null

        try {
            extractor?.release()
        } catch (_: Exception) {
        }
        extractor = null

        started = false
    }

    override fun getVideoSize(): Pair<Int, Int> = width to height

    override fun readFrameInto(buffer: ByteBuffer?, ptsOut: LongArray): Int {
        val extractor = this.extractor ?: return MediaStatus.ERROR
        val codec = this.codec ?: return MediaStatus.ERROR

        if (!started) {
            LogUtil.w(TAG, "readFrameInto() called before codec started")
            return MediaStatus.BUFFERING
        }

        if (outputEOS) {
            return MediaStatus.EOF
        }

        // 1) Feed input to MediaCodec
        if (!inputEOS) {
            val inIndex = try {
                codec.dequeueInputBuffer(0)
            } catch (e: IllegalStateException) {
                LogUtil.e(TAG, "dequeueInputBuffer failed: ${e.message}")
                return MediaStatus.ERROR
            }

            if (inIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        // No more samples -> send EOS to codec
                        LogUtil.d(TAG, "readFrameInto: input EOS")
                        codec.queueInputBuffer(
                            inIndex,
                            0,
                            0,
                            0L,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        inputEOS = true
                    } else {
                        val sampleTimeUs = extractor.sampleTime
                        codec.queueInputBuffer(
                            inIndex,
                            0,
                            sampleSize,
                            sampleTimeUs,
                            0
                        )
                        extractor.advance()
                    }
                }
            }
        }

        // 2) Dequeue output from MediaCodec
        val info = MediaCodec.BufferInfo()
        val outIndex = try {
            codec.dequeueOutputBuffer(info, 0)
        } catch (e: IllegalStateException) {
            LogUtil.e(TAG, "dequeueOutputBuffer failed: ${e.message}")
            return MediaStatus.ERROR
        }

        return when {
            outIndex >= 0 -> {
                val isEOS = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                val ptsMs = info.presentationTimeUs / 1000L

                // Render to Surface
                codec.releaseOutputBuffer(outIndex, true)

                if (ptsOut.isNotEmpty()) {
                    ptsOut[0] = ptsMs
                }

                LogUtil.d(TAG, "readFrameInto: OK pts=$ptsMs, eos=$isEOS")

                if (isEOS) {
                    outputEOS = true
                    MediaStatus.EOF
                } else {
                    MediaStatus.OK
                }
            }

            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFormat = codec.outputFormat
                updateSizeFromFormat(newFormat)
                LogUtil.i(
                    TAG,
                    "INFO_OUTPUT_FORMAT_CHANGED -> new display size ${width}x$height"
                )
                MediaStatus.BUFFERING
            }

            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                // No frame ready this time
                // Avoid busy loop: small sleep is handled by XMediaPlayer (it sleeps on BUFFERING)
                MediaStatus.BUFFERING
            }

            else -> {
                LogUtil.e(TAG, "readFrameInto: unexpected outIndex=$outIndex")
                MediaStatus.ERROR
            }
        }
    }
    private fun updateSizeFromFormat(format: MediaFormat) {
        var w = format.getInteger(MediaFormat.KEY_WIDTH)
        var h = format.getInteger(MediaFormat.KEY_HEIGHT)

        // --- handle crop rect if present (real visible size) ---
        val hasCrop =
            format.containsKey("crop-right") &&
                    format.containsKey("crop-left") &&
                    format.containsKey("crop-bottom") &&
                    format.containsKey("crop-top")

        if (hasCrop) {
            val cropRight  = format.getInteger("crop-right")
            val cropLeft   = format.getInteger("crop-left")
            val cropBottom = format.getInteger("crop-bottom")
            val cropTop    = format.getInteger("crop-top")

            w = cropRight - cropLeft + 1
            h = cropBottom - cropTop + 1
        }

        // --- handle rotation: 90/270 -> swap width & height for display ---
        var rotation = 0
        if (format.containsKey("rotation-degrees")) {
            rotation = format.getInteger("rotation-degrees")
        }

        if (rotation == 90 || rotation == 270) {
            val tmp = w
            w = h
            h = tmp
        }

        width = w
        height = h
        LogUtil.i(
            TAG,
            "updateSizeFromFormat -> display size=${width}x$height, rotation=$rotation, hasCrop=$hasCrop"
        )
    }
}