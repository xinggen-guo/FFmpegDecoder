package com.audio.study.ffmpegdecoder.utils

import java.io.File
import java.io.FileOutputStream

/**
 * @author xinggen.guo
 * @date 2025/11/22 18:37
 * Simple streamed WAV writer:
 *  - write 44-byte placeholder header on start()
 *  - append PCM samples via writeSamples()
 *  - on stop(), seek back to 0 and write the real WAV header
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int = 44100,
    private val channels: Int = 1,
    private val bitsPerSample: Int = 16
) {
    private var fos: FileOutputStream? = null
    private var totalDataBytes: Long = 0L   // number of PCM bytes written (not counting header)

    fun start() {
        // open file and reserve 44 bytes for header
        fos = FileOutputStream(file).also { out ->
            out.write(ByteArray(44)) // placeholder; real header written in stop()
            totalDataBytes = 0L
        }
    }

    /**
     * Write `size` PCM samples (16-bit) into the file.
     */
    fun writeSamples(pcm: ShortArray, size: Int) {
        val out = fos ?: return
        if (size <= 0) return

        val buf = ByteArray(size * 2)
        var bi = 0
        for (i in 0 until size) {
            val v = pcm[i].toInt()
            buf[bi++] = (v and 0xFF).toByte()
            buf[bi++] = ((v shr 8) and 0xFF).toByte()
        }
        out.write(buf)
        totalDataBytes += size * 2L
    }

    /**
     * Finalize file: write correct WAV header at the beginning, then close.
     */
    fun stop() {
        val out = fos ?: return
        try {
            // seek to beginning and write real header
            out.channel.position(0L)
            writeWavHeader(
                out = out,
                totalAudioLen = totalDataBytes,
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = bitsPerSample
            )
        } finally {
            out.close()
            fos = null
        }
    }

    // ---------------- WAV header helpers ----------------

    /**
     * Write a standard PCM WAV header to the given FileOutputStream.
     *
     * totalAudioLen = number of PCM bytes (not including this 44-byte header)
     */
    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val totalDataLen = totalAudioLen + 36           // 36 + subchunk2Size
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = (channels * bitsPerSample / 8).toShort()

        val header = ByteArray(44)

        // RIFF
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()

        // file size - 8
        writeIntLE(header, 4, totalDataLen.toInt())

        // WAVE
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        // fmt chunk
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()

        // Subchunk1Size (16 for PCM)
        writeIntLE(header, 16, 16)

        // AudioFormat = 1 (PCM)
        writeShortLE(header, 20, 1)

        // NumChannels
        writeShortLE(header, 22, channels.toShort())

        // SampleRate
        writeIntLE(header, 24, sampleRate)

        // ByteRate
        writeIntLE(header, 28, byteRate)

        // BlockAlign
        writeShortLE(header, 32, blockAlign)

        // BitsPerSample
        writeShortLE(header, 34, bitsPerSample.toShort())

        // data chunk
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()

        // Subchunk2Size = totalAudioLen
        writeIntLE(header, 40, totalAudioLen.toInt())

        out.write(header, 0, 44)
    }

    private fun writeIntLE(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset]     = (value and 0xFF).toByte()
        buffer[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buffer[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buffer[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buffer: ByteArray, offset: Int, value: Short) {
        val v = value.toInt()
        buffer[offset]     = (v and 0xFF).toByte()
        buffer[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    public fun getFilePath(): String {
        return file.absolutePath
    }
}