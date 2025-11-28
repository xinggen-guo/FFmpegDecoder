package com.audio.study.ffmpegdecoder

import android.os.Bundle
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import com.audio.study.ffmpegdecoder.databinding.ActivityLiveWatchBinding
import com.audio.study.ffmpegdecoder.player.XMediaPlayer
import com.audio.study.ffmpegdecoder.player.XMediaPlayerFactory
import com.audio.study.ffmpegdecoder.player.interfaces.XMediaPlayerListener
import com.audio.study.ffmpegdecoder.utils.LogUtil

/**
 * @author xinggen.guo
 * @date 2025/11/28 18:37
 * Simple live viewer Activity.
 * Connects to FLV-over-TCP server and plays the stream using FFmpeg-based player.
 */
class LiveWatchActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLiveWatchBinding
    private lateinit var xPlayer: XMediaPlayer

    private val host: String = BuildConfig.STREAM_HOST
    private val port: Int = BuildConfig.STREAM_PORT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLiveWatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        xPlayer = XMediaPlayerFactory.createSoftwarePlayerWithView(binding.surfaceView)

        binding.surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                xPlayer.setSurface(holder.surface)
                xPlayer.prepareLiveTcp(host, port)

                xPlayer.listener = object : XMediaPlayerListener {
                    override fun onPrepared(durationMs: Long) {
                        LogUtil.i("LiveWatch", "prepared (live), duration=$durationMs")
                        xPlayer.play()
                    }

                    override fun onProgress(positionMs: Long) {
                        // For live, you may ignore or show "live" badge
                    }

                    override fun onCompletion() {
                        // Live ended
                    }

                    override fun onError(what: Int, extra: Int) {
                        LogUtil.e("LiveWatch", "error: what=$what extra=$extra")
                    }

                    override fun onEndResume() {
                        // Not very relevant for live
                    }
                }
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                xPlayer.surfaceChanged(holder.surface, format, width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                xPlayer.pause()
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        xPlayer.release()
    }
}