package com.audio.study.ffmpegdecoder.live.interfaces

/**
 * @author xinggen.guo
 * @date 2025/11/25 15:45
 * Generic sink that receives encoded H.264 video and AAC audio frames,
 * ready to be sent to a streaming server or another device.
 */
interface LiveStreamSink {

    /**
     * H.264 configuration: SPS & PPS NAL units (without 0x00000001 start codes).
     */
    fun onVideoConfig(sps: ByteArray, pps: ByteArray)

    /**
     * Encoded H.264 frame.
     *
     * @param data     raw NAL unit data (you can prepend FLV/RTMP/RTP headers later).
     * @param ptsUs    presentation timestamp in microseconds since stream start.
     * @param isKeyFrame true if this frame is an IDR (key) frame.
     */
    fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean)

    /**
     * AAC configuration: AudioSpecificConfig (csd-0).
     */
    fun onAudioConfig(aacConfig: ByteArray)

    /**
     * Encoded AAC frame payload (no container or ADTS header yet).
     *
     * @param data  AAC frame bytes.
     * @param ptsUs presentation timestamp in microseconds since stream start.
     */
    fun onAudioFrame(data: ByteArray, ptsUs: Long)

    /**
     * Called when streaming is finished or an unrecoverable error occurs.
     * Implementations should close sockets / connections here.
     */
    fun close()
}