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
 * For this engine,should use a VideoRenderer that does nothing or only
 * manages the Surface (e.g. SurfaceOnlyRenderer).
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

    fun setOutputSurface(surface: Surface?) {
        LogUtil.i(TAG, "setOutputSurface: $surface")
        outputSurface = surface
        // If codec already configured and running, normally can't change surface
        // without recreating codec. So set this before prepare() in practice.
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

        width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)

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
        codec?.start()
        LogUtil.i(TAG, "MediaCodec started")
    }

    override fun pause() {
        // For MediaCodec, we don't have an easy built-in pause.
        // XMediaPlayer's render loop checks playing flag before calling readFrameInto,
        // so we can simply not decode frames while paused.
        LogUtil.i(TAG, "pause() - no-op (handled by upper layer)")
    }

    override fun resume() {
        LogUtil.i(TAG, "resume() - no-op (handled by upper layer)")
    }

    override fun seekTo(positionMs: Long) {
        val extractor = this.extractor ?: return
        val codec = this.codec ?: return

        LogUtil.i(TAG, "seekTo: $positionMs ms")

        // Flush codec + reposition extractor
        codec.flush()
        extractor.seekTo(positionMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

        inputEOS = false
        outputEOS = false
    }

    override fun release() {
        LogUtil.i(TAG, "release")
        try {
            codec?.stop()
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

        outputSurface = null
        prepared = false
        inputEOS = false
        outputEOS = false
    }

    override fun getVideoSize(): Pair<Int, Int> = width to height

    override fun readFrameInto(buffer: ByteBuffer?, ptsOut: LongArray): Int {
        val extractor = this.extractor ?: return MediaStatus.ERROR
        val codec = this.codec ?: return MediaStatus.ERROR
        if (outputEOS) {
            return MediaStatus.EOF
        }

        // 1) Feed input if possible
        if (!inputEOS) {
            val inIndex = codec.dequeueInputBuffer(0)
            if (inIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inIndex)
                if (inputBuffer != null) {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        // No more samples
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

        // 2) Dequeue output
        val info = MediaCodec.BufferInfo()
        val outIndex = codec.dequeueOutputBuffer(info, 0)

        return when {
            outIndex >= 0 -> {
                val isEOS = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                val ptsMs = info.presentationTimeUs / 1000L

                // Render to surface
                codec.releaseOutputBuffer(outIndex, true)

                if (ptsOut.isNotEmpty()) {
                    ptsOut[0] = ptsMs
                }

                if (isEOS) {
                    outputEOS = true
                    MediaStatus.EOF
                } else {
                    MediaStatus.OK
                }
            }

            outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                val newFormat = codec.outputFormat
                width = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                height = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
                LogUtil.i(TAG, "INFO_OUTPUT_FORMAT_CHANGED: new size ${width}x$height")
                MediaStatus.BUFFERING
            }

            outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                // No output ready yet
                MediaStatus.BUFFERING
            }

            else -> {
                // Unexpected status
                MediaStatus.ERROR
            }
        }
    }
}