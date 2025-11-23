package com.audio.study.ffmpegdecoder.utils

import android.annotation.SuppressLint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/23 16:34
 * @description
 */
object AvFileMixer {

    /**
     * Public API:
     * Mix an existing video file (with video track)
     * and an existing audio file (WAV / AAC / other audio) into a final MP4.
     *
     * - If audio is already compressed (e.g. AAC), mux directly.
     * - If audio is RAW PCM (audio/raw, e.g. WAV), convert to AAC first, then mux.
     *
     * @param videoFile   MP4 with video track
     * @param audioFile   audio file (WAV / AAC / etc.)
     * @param outputFile  final MP4
     * @param onProgress  optional progress callback, 0f..1f
     */
    @SuppressLint("WrongConstant")
    fun mixFiles(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ) {
        // 1) Ensure audio is AAC (or other muxer-supported compressed format)
        val aacAudioFile = ensureAacAudioFile(audioFile)

        try {
            // 2) Do the actual muxing (your original logic moved into a private method)
            doMixFiles(videoFile, aacAudioFile, outputFile, onProgress)
        } finally {
            // 3) If we created a temp AAC file, clean it up
            if (aacAudioFile != audioFile) {
                aacAudioFile.delete()
            }
        }
    }

    // ------------------------------------------------------------------------
    // INTERNAL: actual mux logic (your original mixFiles implementation)
    // ------------------------------------------------------------------------

    @SuppressLint("WrongConstant")
    private fun doMixFiles(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val videoExtractor = MediaExtractor()
        videoExtractor.setDataSource(videoFile.absolutePath)

        val audioExtractor = MediaExtractor()
        audioExtractor.setDataSource(audioFile.absolutePath)

        val muxer = MediaMuxer(
            outputFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        // ---- select video track ----
        val videoTrackIndex = (0 until videoExtractor.trackCount).first { i ->
            videoExtractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)!!
                .startsWith("video/")
        }
        videoExtractor.selectTrack(videoTrackIndex)
        val videoFormat = videoExtractor.getTrackFormat(videoTrackIndex)
        val muxerVideoIndex = muxer.addTrack(videoFormat)

        // ---- select audio track ----
        val audioTrackIndex = (0 until audioExtractor.trackCount).first { i ->
            audioExtractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)!!
                .startsWith("audio/")
        }
        audioExtractor.selectTrack(audioTrackIndex)
        val audioFormat = audioExtractor.getTrackFormat(audioTrackIndex)
        val muxerAudioIndex = muxer.addTrack(audioFormat)

        // ---- durations (for progress) ----
        val videoDurationUs = if (videoFormat.containsKey(MediaFormat.KEY_DURATION)) {
            videoFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        val audioDurationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
            audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L

        muxer.start()

        val bufferSize = 1024 * 1024
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        // -------- Phase 1: write video (0% ~ 50%) --------
        var lastVideoPts = 0L
        while (true) {
            buffer.clear()
            val sampleSize = videoExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = videoExtractor.sampleTime
            bufferInfo.flags = videoExtractor.sampleFlags

            lastVideoPts = bufferInfo.presentationTimeUs
            muxer.writeSampleData(muxerVideoIndex, buffer, bufferInfo)
            videoExtractor.advance()

            if (videoDurationUs > 0) {
                val videoProgress = (lastVideoPts.toFloat() / videoDurationUs).coerceIn(0f, 1f)
                // map to 0f..0.5f
                onProgress?.invoke(0.5f * videoProgress)
            }
        }

        // -------- Phase 2: write audio (50% ~ 100%) --------
        var lastAudioPts = 0L
        while (true) {
            buffer.clear()
            val sampleSize = audioExtractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = audioExtractor.sampleTime
            bufferInfo.flags = audioExtractor.sampleFlags

            lastAudioPts = bufferInfo.presentationTimeUs
            muxer.writeSampleData(muxerAudioIndex, buffer, bufferInfo)
            audioExtractor.advance()

            if (audioDurationUs > 0) {
                val audioProgress = (lastAudioPts.toFloat() / audioDurationUs).coerceIn(0f, 1f)
                // map to 0.5f..1f
                onProgress?.invoke(0.5f + 0.5f * audioProgress)
            }
        }

        // ensure 100%
        onProgress?.invoke(1f)

        videoExtractor.release()
        audioExtractor.release()
        muxer.stop()
        muxer.release()
    }

    // ------------------------------------------------------------------------
    // INTERNAL: ensure audio file is AAC (or return original if already OK)
    // ------------------------------------------------------------------------

    /**
     * If [source] is already compressed audio (e.g. AAC → audio/mp4a-latm),
     * return it unchanged.
     * If it is RAW PCM (audio/raw, e.g. WAV), convert to AAC in a temp file and return that.
     */
    private fun ensureAacAudioFile(source: File): File {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)

        val audioTrackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            // No audio track at all
            return source
        }

        val format = extractor.getTrackFormat(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
            format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } else 48000
        val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
            format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        } else 1

        extractor.release()

        // Already AAC or some compressed audio → use as is
        if (mime != "audio/raw") {
            return source
        }

        // Need to convert RAW PCM → AAC into a temp file
        val parentDir = source.parentFile ?: File(".")
        val tempAac = File(parentDir, source.nameWithoutExtension + "_tmp_aac.m4a")

//        convertRawLikeToAac(source, tempAac, sampleRate, channels)
        convertPcmToAac(source, tempAac, sampleRate, channels)

        return tempAac
    }

    // ------------------------------------------------------------------------
    // INTERNAL: convert WAV/PCM (audio/raw) into AAC file
    // ------------------------------------------------------------------------

    /**
     * Convert a "audio/raw" file (e.g. WAV/PCM) into AAC in an MP4 container.
     *
     * This uses MediaExtractor to read PCM samples, MediaCodec to encode AAC,
     * and MediaMuxer to write to [outFile].
     */
    private fun convertRawLikeToAac(
        source: File,
        outFile: File,
        sampleRate: Int,
        channels: Int
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(source.absolutePath)

        val trackIndex = (0 until extractor.trackCount).first { i ->
            extractor.getTrackFormat(i)
                .getString(MediaFormat.KEY_MIME)!!
                .startsWith("audio/")
        }
        extractor.selectTrack(trackIndex)

        val pcmFormat = extractor.getTrackFormat(trackIndex)

        val aacFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channels
        ).apply {
            setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(
            outFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        val bufferSize = 16 * 1024
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        val inputBuffers = encoder.inputBuffers
        var audioTrackIndex = -1
        var muxerStarted = false

        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            // ---- feed PCM into encoder ----
            if (!sawInputEOS) {
                val inputIndex = encoder.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = inputBuffers[inputIndex]
                    inputBuffer.clear()

                    buffer.clear()
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) {
                        // End of stream
                        sawInputEOS = true
                        encoder.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                    } else {
                        // Copy PCM into encoder input
                        val tmp = ByteArray(sampleSize)
                        buffer.get(tmp)
                        inputBuffer.put(tmp)

                        val presentationTimeUs = extractor.sampleTime
                        encoder.queueInputBuffer(
                            inputIndex,
                            0,
                            sampleSize,
                            presentationTimeUs,
                            0
                        )
                        extractor.advance()
                    }
                }
            }

            // ---- drain AAC output ----
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // nothing yet
                }

                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) {
                        // should not happen normally
                    }
                    val newFormat = encoder.outputFormat
                    audioTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }

                outIndex >= 0 -> {
                    val encodedBuffer = encoder.getOutputBuffer(outIndex) ?: continue
                    if (bufferInfo.size > 0 && muxerStarted) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(audioTrackIndex, encodedBuffer, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawOutputEOS = true
                    }
                }
            }
        }

        // cleanup
        try { encoder.stop() } catch (_: Exception) {}
        encoder.release()

        try { muxer.stop() } catch (_: Exception) {}
        muxer.release()

        extractor.release()
    }

    private fun convertPcmToAac(
        pcmFile: File,
        outFile: File,
        sampleRate: Int,
        channels: Int
    ) {
        val aacFormat = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            channels
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        val muxer = MediaMuxer(
            outFile.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )

        val inputBuffers = encoder.inputBuffers
        val bufferInfo = MediaCodec.BufferInfo()

        val pcmInput = pcmFile.inputStream()
        val tempBuffer = ByteArray(16384)

        var audioTrackIndex = -1
        var muxerStarted = false
        var presentationUs = 0L
        val bytesPerFrame = channels * 2  // 16-bit PCM

        var read: Int

        while (true) {
            read = pcmInput.read(tempBuffer)
            if (read <= 0) {
                // send EOS
                val inputIndex = encoder.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    encoder.queueInputBuffer(
                        inputIndex,
                        0,
                        0,
                        presentationUs,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
                break
            }

            val inputIndex = encoder.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuf = inputBuffers[inputIndex]
                inputBuf.clear()
                inputBuf.put(tempBuffer, 0, read)

                encoder.queueInputBuffer(
                    inputIndex,
                    0,
                    read,
                    presentationUs,
                    0
                )

                val frames = read / bytesPerFrame
                presentationUs += (1_000_000L * frames / sampleRate)
            }

            // drain
            var outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10000)
            while (outIndex >= 0) {
                val outBuf = encoder.getOutputBuffer(outIndex)!!

                if (!muxerStarted) {
                    audioTrackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }

                if (bufferInfo.size > 0) {
                    outBuf.position(bufferInfo.offset)
                    outBuf.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(audioTrackIndex, outBuf, bufferInfo)
                }

                encoder.releaseOutputBuffer(outIndex, false)
                outIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
            }
        }

        // finalize
        pcmInput.close()
        encoder.stop()
        encoder.release()
        muxer.stop()
        muxer.release()
    }
}