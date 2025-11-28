package com.audio.study.ffmpegdecoder.player.interfaces

import android.view.Surface
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/17 14:41
 * Renderer abstraction.
 *
 * Implementations:
 *  - SoftwareCanvasRenderer: uses Surface.lockCanvas() + drawBitmap
 *  - SurfaceOnlyRenderer: for MediaCodec where codec renders directly to surface
 */
interface VideoRenderer {
    fun setSurface(surface: Surface?)
    fun setVideoSize(width: Int, height: Int)

    /**
     * Render a single frame.
     *
     * For software mode, [buffer] contains RGBA data (w*h*4).
     * For hardware mode, [buffer] is usually null and renderer
     * may do nothing (MediaCodec already rendered to Surface).
     */
    fun renderFrame(buffer: ByteBuffer?, width: Int, height: Int)
    fun surfaceChanged(surface: Surface?, format: Int, width: Int, height: Int)
}