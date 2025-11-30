package com.audio.study.ffmpegdecoder.net

import com.audio.study.ffmpegdecoder.live.engine.FlvMuxSink
import com.audio.study.ffmpegdecoder.live.interfaces.LiveStreamSink
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
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
    private val host: String,
    private val port: Int,
    private val connectTimeoutMs: Int = 5000
) : LiveStreamSink {

    // ---- internal command model for worker thread ----
    private sealed interface Command {
        data class VideoConfig(val sps: ByteArray, val pps: ByteArray) : Command
        data class VideoFrame(val data: ByteArray, val ptsUs: Long, val isKeyFrame: Boolean) : Command
        data class AudioConfig(val aacConfig: ByteArray) : Command
        data class AudioFrame(val data: ByteArray, val ptsUs: Long) : Command
        object Close : Command
    }

    private val closed = AtomicBoolean(false)
    private val queue = LinkedBlockingQueue<Command>()

    // Worker thread + network resources (used only inside worker)
    private val workerThread: Thread

    init {
        // Start background worker: connects socket lazily and flushes all commands
        workerThread = Thread({
            var socket: Socket? = null
            var output: OutputStream? = null
            var muxer: FlvMuxSink? = null

            try {
                while (true) {
                    val cmd = queue.take()

                    if (cmd is Command.Close) {
                        // graceful shutdown
                        break
                    }

                    // lazy connect: only when first command arrives
                    if (socket == null) {
                        socket = Socket()
                        socket!!.connect(InetSocketAddress(host, port), connectTimeoutMs)
                        output = socket!!.getOutputStream()
                        muxer = FlvMuxSink(output!!)
                    }

                    try {
                        when (cmd) {
                            is Command.VideoConfig ->
                                muxer!!.onVideoConfig(cmd.sps, cmd.pps)

                            is Command.VideoFrame ->
                                muxer!!.onVideoFrame(cmd.data, cmd.ptsUs, cmd.isKeyFrame)

                            is Command.AudioConfig ->
                                muxer!!.onAudioConfig(cmd.aacConfig)

                            is Command.AudioFrame ->
                                muxer!!.onAudioFrame(cmd.data, cmd.ptsUs)

                            Command.Close -> {
                                // already handled above, unreachable here
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        // On any I/O error, stop streaming and exit worker
                        break
                    }
                }
            } catch (e: InterruptedException) {
                // thread interrupted, just exit
            } finally {
                try {
                    muxer?.close()
                } catch (_: Exception) {
                }
                try {
                    output?.close()
                } catch (_: Exception) {
                }
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }, "NetworkFlvSink-Worker")

        workerThread.start()
    }

    // --------------------------------------------------
    // LiveStreamSink implementation (called from encoder threads)
    // --------------------------------------------------

    override fun onVideoConfig(sps: ByteArray, pps: ByteArray) {
        if (closed.get()) return
        // copy arrays to avoid reuse bugs
        queue.offer(
            Command.VideoConfig(
                sps.copyOf(),
                pps.copyOf()
            )
        )
    }

    override fun onVideoFrame(data: ByteArray, ptsUs: Long, isKeyFrame: Boolean) {
        if (closed.get()) return
        queue.offer(
            Command.VideoFrame(
                data.copyOf(),
                ptsUs,
                isKeyFrame
            )
        )
    }

    override fun onAudioConfig(aacConfig: ByteArray) {
        if (closed.get()) return
        queue.offer(
            Command.AudioConfig(
                aacConfig.copyOf()
            )
        )
    }

    override fun onAudioFrame(data: ByteArray, ptsUs: Long) {
        if (closed.get()) return
        queue.offer(
            Command.AudioFrame(
                data.copyOf(),
                ptsUs
            )
        )
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        // Send Close command to stop worker
        queue.offer(Command.Close)

        try {
            workerThread.join(1000)
        } catch (_: InterruptedException) {
        }
    }

    // We no longer need a separate handleError(e) because
    // I/O exceptions are handled inside the worker loop.
}