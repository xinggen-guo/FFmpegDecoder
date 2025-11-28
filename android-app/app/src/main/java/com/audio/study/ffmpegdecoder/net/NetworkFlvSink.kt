package com.audio.study.ffmpegdecoder.net

import com.audio.study.ffmpegdecoder.live.engine.FlvMuxSink
import com.audio.study.ffmpegdecoder.live.interfaces.LiveStreamSink
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author xinggen.guo
 * @date 2025/11/28 17:56
 * LiveStreamSink implementation that:
 *  - opens a TCP connection to the Go server
 *  - wraps the socket OutputStream with FlvMuxSink
 *  - sends FLV stream (header + tags) over the network
 */
class NetworkFlvSink(
    host: String,
    port: Int,
    connectTimeoutMs: Int = 5000
) : LiveStreamSink {

    private val closed = AtomicBoolean(false)

    private val socket: Socket
    private val output: OutputStream
    private val inner: FlvMuxSink

    init {
        // 1) connect to Go server
        val s = Socket()
        s.connect(InetSocketAddress(host, port), connectTimeoutMs)
        socket = s
        output = s.getOutputStream()

        // 2) wrap with existing FlvMuxSink (it writes FLV to this OutputStream)
        inner = FlvMuxSink(output)
    }

    override fun onVideoConfig(sps: ByteArray, pps: ByteArray) {
        if (closed.get()) return
        try {
            inner.onVideoConfig(sps, pps)
        } catch (e: IOException) {
            handleError(e)
        }
    }

    override fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean) {
        if (closed.get()) return
        try {
            inner.onVideoFrame(data, ptsUs, isKeyFrame)
        } catch (e: IOException) {
            handleError(e)
        }
    }

    override fun onAudioConfig(aacConfig: ByteArray) {
        if (closed.get()) return
        try {
            inner.onAudioConfig(aacConfig)
        } catch (e: IOException) {
            handleError(e)
        }
    }

    override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        if (closed.get()) return
        try {
            inner.onAudioFrame(data, ptsUs)
        } catch (e: IOException) {
            handleError(e)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        try {
            inner.close()   // flush + close OutputStream
        } catch (_: Exception) { }

        try {
            output.close()
        } catch (_: Exception) { }

        try {
            socket.close()
        } catch (_: Exception) { }
    }

    private fun handleError(e: IOException) {
        // You can add logging here if you like.
        // For now, just close everything.
        close()
    }
}