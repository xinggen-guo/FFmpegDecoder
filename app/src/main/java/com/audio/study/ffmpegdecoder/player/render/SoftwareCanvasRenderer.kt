package com.audio.study.ffmpegdecoder.player.render

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.audio.study.ffmpegdecoder.player.interfaces.VideoRenderer
import com.audio.study.ffmpegdecoder.utils.LogUtil
import java.nio.ByteBuffer

/**
 * @author xinggen.guo
 * @date 2025/11/17 15:03
 * Software renderer that draws RGBA frames onto a SurfaceView using Canvas.
 *
 * NOTE:
 *  - XMediaPlayer will still call setSurface(), but we don't strictly need it here
 *    because we use surfaceView.holder directly.
 */
class SoftwareCanvasRenderer(
    private val surfaceView: SurfaceView
) : VideoRenderer {

    companion object {
        private const val TAG = "SoftwareCanvasRenderer"
    }

    private var videoWidth: Int = 0
    private var videoHeight: Int = 0

    private var bitmap: Bitmap? = null
    private val srcRect = Rect()
    private var dstRect = Rect()

    override fun setSurface(surface: Surface?) {
        // We can ignore this, since we always use surfaceView.holder
        // But keep it for interface compatibility.
        LogUtil.d(TAG, "setSurface called, surface=$surface")
    }

    override fun setVideoSize(width: Int, height: Int) {
        LogUtil.i(TAG, "setVideoSize: ${width}x$height")
        videoWidth = width
        videoHeight = height

        // Recreate bitmap when size changes
        if (width > 0 && height > 0) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            srcRect.set(0, 0, width, height)
        }
    }

    override fun renderFrame(buffer: ByteBuffer?, width: Int, height: Int) {
        LogUtil.i(TAG, "renderFrame: ${width}x$height")
        if (buffer == null) return
        if (width <= 0 || height <= 0) return

        val bmp = bitmap ?: return
        if (bmp.width != width || bmp.height != height) {
            // size changed unexpectedly, recreate
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }

        val holder: SurfaceHolder = surfaceView.holder
        val canvas = holder.lockCanvas() ?: return
        try {
            // Copy RGBA bytes into bitmap
            buffer.position(0)
            bitmap!!.copyPixelsFromBuffer(buffer)

            val viewW = surfaceView.width
            val viewH = surfaceView.height
            LogUtil.i(TAG, "renderFrame: ${width}x$height")
            if (viewW == 0 || viewH == 0) {
                return
            }
            val videoRatio = width.toFloat() / height
            val viewRatio = viewW.toFloat() / viewH

            // Center-crop with preserving aspect ratio (like most players)
            dstRect = if (videoRatio > viewRatio) {
                // video wider than view → width matches, height letterbox
                val scaledH = (viewW / videoRatio).toInt()
                val top = (viewH - scaledH) / 2
                Rect(0, top, viewW, top + scaledH)
            } else {
                // video narrower than view → height matches, side letterbox
                val scaledW = (viewH * videoRatio).toInt()
                val left = (viewW - scaledW) / 2
                Rect(left, 0, left + scaledW, viewH)
            }

            canvas.drawColor(0xFF000000.toInt())
            canvas.drawBitmap(bitmap!!, srcRect, dstRect, null)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }
}