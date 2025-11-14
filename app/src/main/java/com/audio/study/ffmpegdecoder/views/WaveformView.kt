package com.audio.study.ffmpegdecoder.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * @author xinggen.guo
 * @date 2025/11/14 17:43
 * @description
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private var samples: FloatArray = FloatArray(0)

    fun updateWaveform(data: FloatArray) {
        if (samples.size != data.size) {
            samples = FloatArray(data.size)
        }
        System.arraycopy(data, 0, samples, 0, data.size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (samples.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2f
        val stepX = w / (samples.size - 1).coerceAtLeast(1)

        var lastX = 0f
        var lastY = centerY

        for (i in samples.indices) {
            val x = i * stepX
            val v = samples[i].coerceIn(-1f, 1f)
            val y = centerY - v * (h / 2f) // [-1,1] -> vertical

            if (i > 0) {
                canvas.drawLine(lastX, lastY, x, y, paint)
            }
            lastX = x
            lastY = y
        }
    }
}