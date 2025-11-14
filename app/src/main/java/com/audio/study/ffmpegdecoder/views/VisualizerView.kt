package com.audio.study.ffmpegdecoder.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * @author xinggen.guo
 * @date 2025/11/14 15:49
 * @description
 */
class VisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
    }

    private var spectrum: FloatArray = FloatArray(0)

    fun updateSpectrum(data: FloatArray) {
        if (spectrum.size != data.size) {
            spectrum = FloatArray(data.size)
        }
        System.arraycopy(data, 0, spectrum, 0, data.size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (spectrum.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val barWidth = w / spectrum.size

        for (i in spectrum.indices) {
            val value = spectrum[i].coerceIn(0f, 3f)  // clamp
            val barHeight = (value / 3f) * h
            val left = i * barWidth
            val top = h - barHeight
            val right = left + barWidth * 0.8f
            canvas.drawRect(left, top, right, h, paint)
        }
    }
}