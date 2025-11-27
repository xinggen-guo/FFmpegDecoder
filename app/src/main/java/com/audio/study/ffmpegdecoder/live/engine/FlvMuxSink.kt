package com.audio.study.ffmpegdecoder.live.engine

import com.audio.study.ffmpegdecoder.live.interfaces.LiveStreamSink
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/25 19:04
 * A simple FLV muxer that receives encoded H.264 + AAC
 * from AvLiveStreamer and writes a valid FLV stream
 * (audio + video interleaved) to an OutputStream.
 *
 * This is suitable for:
 *  - writing .flv files (for debugging with ffplay / VLC)
 *  - later wrapping in an RTMP publisher
 *
 * Limitations:
 *  - We assume H.264 Annex-B (start-code) style NAL units.
 *  - We do a minimal AVCDecoderConfigurationRecord from SPS/PPS.
 *  - We don't write FLV script metadata (optional).
 */
class FlvMuxSink(private val output: OutputStream) : LiveStreamSink {

    private val lock = Any()

    private var headerWritten = false

    // FLV timestamps use ms; we keep a base PTS (in microseconds)
    private var basePtsUs: Long = -1L

    // Remember configs
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    private var aacConfig: ByteArray? = null

    // ------------------------------------------------------------
    // LiveStreamSink API
    // ------------------------------------------------------------

    override fun onVideoConfig(sps: ByteArray, pps: ByteArray) {
        synchronized(lock) {
            this.sps = sps
            this.pps = pps

            ensureHeader()
            writeAvcSequenceHeaderTag(sps, pps)
        }
    }

    override fun onAudioConfig(aacConfig: ByteArray) {
        synchronized(lock) {
            this.aacConfig = aacConfig

            ensureHeader()
            writeAacSequenceHeaderTag(aacConfig)
        }
    }

    override fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean) {
        synchronized(lock) {
            ensureHeader()
            ensureBasePts(ptsUs)
            val tsMs = toFlvTimestamp(ptsUs)

            writeVideoTag(data, tsMs, isKeyFrame)
        }
    }

    override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        synchronized(lock) {
            ensureHeader()
            ensureBasePts(ptsUs)
            val tsMs = toFlvTimestamp(ptsUs)

            writeAudioTag(data, tsMs)
        }
    }

    override fun close() {
        synchronized(lock) {
            try {
                output.flush()
            } catch (_: Exception) {
            }
            try {
                output.close()
            } catch (_: Exception) {
            }
        }
    }

    // ------------------------------------------------------------
    // Header / timestamp management
    // ------------------------------------------------------------

    /**
     * Write FLV header once.
     *
     * For simplicity we always set "has audio" + "has video" flags.
     * Even if one track is missing, most players still accept it.
     */
    private fun ensureHeader() {
        if (headerWritten) return

        // FLV header (9 bytes)
        // Signature "FLV"
        output.write(byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte()))
        // Version
        output.write(0x01)
        // Flags: 0x01 = video, 0x04 = audio, 0x05 = both
        output.write(0x05)
        // DataOffset: header size (9)
        writeUI32(9)

        // First PreviousTagSize0 = 0
        writeUI32(0)

        headerWritten = true
    }

    private fun ensureBasePts(ptsUs: Long) {
        if (basePtsUs < 0) {
            basePtsUs = ptsUs
        }
    }

    /**
     * Convert our microsecond PTS to FLV timestamp (milliseconds, 32-bit).
     */
    private fun toFlvTimestamp(ptsUs: Long): Int {
        val deltaUs = if (basePtsUs >= 0) ptsUs - basePtsUs else ptsUs
        val ms = deltaUs / 1000L
        return ms.coerceIn(0L, 0x0FFFFFFFL.toLong()).toInt()
    }

    // ------------------------------------------------------------
    // FLV tag writers
    // ------------------------------------------------------------

    /**
     * Write AVC sequence header (SPS/PPS) as a video tag with AVCPacketType=0.
     */
    private fun writeAvcSequenceHeaderTag(sps: ByteArray, pps: ByteArray) {
        val spsNoStart = stripStartCode(sps)
        val ppsNoStart = stripStartCode(pps)

        val avcConfig = buildAvcDecoderConfigurationRecord(spsNoStart, ppsNoStart)

        val body = ByteArray(avcConfig.size + 5)
        var i = 0

        // FrameType (1=keyframe) + CodecID (7=AVC)
        body[i++] = ((1 shl 4) or 7).toByte()
        // AVCPacketType = 0 (sequence header)
        body[i++] = 0
        // CompositionTime = 0
        body[i++] = 0
        body[i++] = 0
        body[i++] = 0

        // AVCDecoderConfigurationRecord
        System.arraycopy(avcConfig, 0, body, i, avcConfig.size)

        writeTag(tagType = 0x09, timestampMs = 0, body = body)
    }

    /**
     * Write AAC AudioSpecificConfig as an audio tag with AACPacketType=0.
     */
    private fun writeAacSequenceHeaderTag(aacConfig: ByteArray) {
        // SoundFormat(4) | SoundRate(2) | SoundSize(1) | SoundType(1)
        // SoundFormat=10(AAC), SoundRate=3(44kHz, approximate for 48k),
        // SoundSize=1(16-bit), SoundType=0(mono)/1(stereo)
        val soundType = 0 // mono; change to 1 if you use stereo
        val firstByte = ((10 shl 4) or (3 shl 2) or (1 shl 1) or soundType).toByte()

        val body = ByteArray(aacConfig.size + 2)
        var i = 0
        body[i++] = firstByte
        // AACPacketType = 0 (sequence header)
        body[i++] = 0
        // AudioSpecificConfig
        System.arraycopy(aacConfig, 0, body, i, aacConfig.size)

        writeTag(tagType = 0x08, timestampMs = 0, body = body)
    }

    /**
     * Write a normal video frame (AVCPacketType=1) as a FLV video tag.
     * We convert Annex-B data (start codes) to length-prefixed NAL units.
     */
    private fun writeVideoTag(data: ByteArray, timestampMs: Int, isKeyFrame: Boolean) {
        val nals = annexBToAvcNalus(data)
        if (nals.isEmpty()) return

        // Build AVC video payload: [FrameHeader][AVC Header][NALUs...]
        // FrameHeader: FrameType + CodecID
        val frameType = if (isKeyFrame) 1 else 2
        val header = ByteArray(5)
        header[0] = ((frameType shl 4) or 7).toByte() // CodecID=7 AVC
        header[1] = 1 // AVCPacketType=1 (NALU)
        header[2] = 0 // CompositionTime = 0
        header[3] = 0
        header[4] = 0

        // Convert NALUs to length-prefixed
        val nalPayload = ByteBuffer.allocate(
            nals.sumOf { it.size + 4 }
        )
        for (nal in nals) {
            nalPayload.putInt(nal.size)
            nalPayload.put(nal)
        }

        val body = ByteArray(header.size + nalPayload.position())
        System.arraycopy(header, 0, body, 0, header.size)
        nalPayload.flip()
        nalPayload.get(body, header.size, nalPayload.remaining())

        writeTag(tagType = 0x09, timestampMs = timestampMs, body = body)
    }

    /**
     * Write a normal AAC frame as a FLV audio tag with AACPacketType=1.
     */
    private fun writeAudioTag(data: ByteArray, timestampMs: Int) {
        if (aacConfig == null) {
            // We haven't seen config yet; some players require it first.
            // You could choose to buffer until config arrives.
        }

        val soundType = 0 // mono; change to 1 if stereo
        val firstByte = ((10 shl 4) or (3 shl 2) or (1 shl 1) or soundType).toByte()

        val body = ByteArray(data.size + 2)
        var i = 0
        body[i++] = firstByte
        // AACPacketType = 1 (raw frame)
        body[i++] = 1
        System.arraycopy(data, 0, body, i, data.size)

        writeTag(tagType = 0x08, timestampMs = timestampMs, body = body)
    }

    /**
     * Generic FLV tag writer.
     */
    private fun writeTag(tagType: Int, timestampMs: Int, body: ByteArray) {
        val dataSize = body.size
        val tagHeaderSize = 11
        val tagSize = tagHeaderSize + dataSize

        // Tag header
        output.write(tagType)                   // TagType
        writeUI24(dataSize)                     // DataSize (3 bytes)
        writeUI24(timestampMs and 0xFFFFFF)     // Timestamp lower 24 bits
        output.write((timestampMs shr 24) and 0xFF)  // TimestampExtended
        writeUI24(0)                            // StreamID = 0

        // Tag body
        output.write(body)

        // PreviousTagSize (includes header + body)
        writeUI32(tagSize)
    }

    // ------------------------------------------------------------
    // AVCDecoderConfigurationRecord builder
    // ------------------------------------------------------------

    /**
     * Build minimal AVCDecoderConfigurationRecord from SPS/PPS.
     *
     * SPS/PPS must NOT contain start codes, only NALU payload.
     */
    private fun buildAvcDecoderConfigurationRecord(
        sps: ByteArray,
        pps: ByteArray
    ): ByteArray {
        if (sps.size < 4) throw IllegalArgumentException("SPS too small")

        // SPS NALU: [nal_header][rbsp...]
        // rbsp[0] = profile_idc
        // rbsp[1] = constraint_set_flags + reserved
        // rbsp[2] = level_idc

        val profileIdc = sps[1].toInt() and 0xFF
        val constraints = sps[2].toInt() and 0xFF
        val levelIdc = sps[3].toInt() and 0xFF

        val spsCount = 1
        val ppsCount = 1

        val spsLength = sps.size
        val ppsLength = pps.size

        val record = ByteBuffer.allocate(
            11 + 2 + spsLength + 1 + 2 + ppsLength
        )

        record.put(0x01) // configurationVersion
        record.put(profileIdc.toByte()) // AVCProfileIndication
        record.put(constraints.toByte()) // profile_compatibility
        record.put(levelIdc.toByte()) // AVCLevelIndication
        // lengthSizeMinusOne (0b111111 + 2-bit length minus one; we use 4 bytes => value = 3)
        record.put(0xFF.toByte())

        // numOfSequenceParameterSets (lower 5 bits)
        record.put((0xE0 or (spsCount and 0x1F)).toByte())
        // SPS length
        record.putShort(spsLength.toShort())
        record.put(sps)

        // numOfPictureParameterSets
        record.put(ppsCount.toByte())
        // PPS length
        record.putShort(ppsLength.toShort())
        record.put(pps)

        record.flip()
        val out = ByteArray(record.remaining())
        record.get(out)
        return out
    }

    // ------------------------------------------------------------
    // Helper: Annex-B → NAL lists / strip start code
    // ------------------------------------------------------------

    /**
     * Strip leading 0x00000001 or 0x000001 start code if present.
     */
    private fun stripStartCode(nal: ByteArray): ByteArray {
        var offset = 0
        // skip leading zeros
        while (offset + 3 < nal.size && nal[offset].toInt() == 0 &&
            nal[offset + 1].toInt() == 0
        ) {
            if (nal[offset + 2].toInt() == 1) {
                offset += 3
                break
            } else if (offset + 4 < nal.size && nal[offset + 2].toInt() == 0 &&
                nal[offset + 3].toInt() == 1
            ) {
                offset += 4
                break
            }
            offset++
        }
        if (offset <= 0) return nal
        if (offset >= nal.size) return nal
        return nal.copyOfRange(offset, nal.size)
    }

    /**
     * Split Annex-B by start codes and return raw NAL payloads (without start codes).
     */
    private fun annexBToAvcNalus(data: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()

        var i = 0
        while (i + 3 < data.size) {
            // find start code
            var startCodeLen = 0
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) {
                    startCodeLen = 3
                } else if (i + 4 < data.size &&
                    data[i + 2].toInt() == 0 &&
                    data[i + 3].toInt() == 1
                ) {
                    startCodeLen = 4
                }
            }

            if (startCodeLen > 0) {
                val nalStart = i + startCodeLen
                // find next start code
                var nalEnd = nalStart
                var j = nalStart
                while (j + 3 < data.size) {
                    if (data[j].toInt() == 0 && data[j + 1].toInt() == 0 &&
                        ((data[j + 2].toInt() == 1) ||
                                (j + 4 < data.size &&
                                        data[j + 2].toInt() == 0 &&
                                        data[j + 3].toInt() == 1))
                    ) {
                        break
                    }
                    j++
                }
                nalEnd = j

                if (nalEnd > nalStart) {
                    val nal = data.copyOfRange(nalStart, nalEnd)
                    result.add(nal)
                }
                i = j
            } else {
                i++
            }
        }

        // Fallback: if no start codes detected, treat entire buffer as one NAL
        if (result.isEmpty() && data.isNotEmpty()) {
            result.add(data)
        }

        return result
    }

    // ------------------------------------------------------------
    // Helper: write big-endian unsigned integers
    // ------------------------------------------------------------

    private fun writeUI24(value: Int) {
        output.write((value shr 16) and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write(value and 0xFF)
    }

    private fun writeUI32(value: Int) {
        output.write((value shr 24) and 0xFF)
        output.write((value shr 16) and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write(value and 0xFF)
    }
}