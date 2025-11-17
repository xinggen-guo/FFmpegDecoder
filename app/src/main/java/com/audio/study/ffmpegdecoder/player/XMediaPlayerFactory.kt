package com.audio.study.ffmpegdecoder.player

import android.view.SurfaceView
import com.audio.study.ffmpegdecoder.player.engine.AudioTrackAudioEngine
import com.audio.study.ffmpegdecoder.player.engine.FfmpegVideoEngine
import com.audio.study.ffmpegdecoder.player.engine.MediaCodecVideoEngine
import com.audio.study.ffmpegdecoder.player.engine.OpenSlAudioEngine
import com.audio.study.ffmpegdecoder.player.enum.AudioBackend
import com.audio.study.ffmpegdecoder.player.enum.DecodeType
import com.audio.study.ffmpegdecoder.player.interfaces.AudioEngine
import com.audio.study.ffmpegdecoder.player.interfaces.VideoEngine
import com.audio.study.ffmpegdecoder.player.interfaces.VideoRenderer
import com.audio.study.ffmpegdecoder.player.render.SoftwareCanvasRenderer

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:46
 * Factory responsible for creating configured XMediaPlayer instances.
 *
 * It wires together:
 *  - AudioEngine  (OpenSL / AudioTrack)
 *  - VideoEngine  (FFmpeg / MediaCodec)
 *  - VideoRenderer (software canvas / surface-only)
 */
object XMediaPlayerFactory {

    /**
     * Simplest entry:
     *  - Audio: OpenSL ES
     *  - Video: FFmpeg software decode
     *  - Renderer: SoftwareCanvasRenderer (SurfaceView-based)
     *
     * @param renderer A VideoRenderer that draws to your SurfaceView/TextureView
     */
    fun createDefaultSoftwarePlayer(
        renderer: VideoRenderer
    ): XMediaPlayer {
        val audioEngine: AudioEngine = OpenSlAudioEngine()
        val videoEngine: VideoEngine = FfmpegVideoEngine()
        return XMediaPlayer(audioEngine, videoEngine, renderer)
    }

    /**
     * Full configurable factory:
     *
     * @param audioBackend  OPENSL or AUDIOTRACK
     * @param decodeType    FFMPEG (software) or MEDIACODEC (hardware)
     * @param renderer      A VideoRenderer implementation, matching decodeType
     *
     * Example:
     *   val player = XMediaPlayerFactory.createPlayer(
     *       audioBackend = AudioBackend.OPENSL,
     *       decodeType = DecodeType.FFMPEG,
     *       renderer = SoftwareCanvasRenderer(myView)
     *   )
     */
    fun createPlayer(
        audioBackend: AudioBackend,
        decodeType: DecodeType,
        renderer: VideoRenderer
    ): XMediaPlayer {
        val audioEngine: AudioEngine = when (audioBackend) {
            AudioBackend.OPENSL -> OpenSlAudioEngine()
            AudioBackend.AUDIOTRACK -> AudioTrackAudioEngine()
        }

        val videoEngine: VideoEngine = when (decodeType) {
            DecodeType.FFMPEG -> FfmpegVideoEngine()
            DecodeType.MEDIACODEC -> MediaCodecVideoEngine()
        }

        return XMediaPlayer(audioEngine, videoEngine, renderer)
    }

    /**
     * Convenience helper for:
     *  - Audio: OpenSL
     *  - Video: FFmpeg
     *  - Renderer constructed inside factory from your custom view.
     *
     * This assumes you have a SoftwareCanvasRenderer(view) implementation.
     */
    fun createSoftwarePlayerWithView(
        renderView: SurfaceView // replace with your actual view type, e.g. AudioVideoSyncView
    ): XMediaPlayer {
        val renderer: VideoRenderer = SoftwareCanvasRenderer(renderView)
        val audioEngine: AudioEngine = OpenSlAudioEngine()
        val videoEngine: VideoEngine = FfmpegVideoEngine()
        return XMediaPlayer(audioEngine, videoEngine, renderer)
    }
}