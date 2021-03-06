package com.audio.study.ffmpegdecoder.opengl

import android.content.Context
import android.opengl.GLSurfaceView

class MyGLSurfaceView(context: Context?) : GLSurfaceView(context) {

    private val renderer: MyGLRenderer

    init {
        // Create an OpenGL ES 2.0 context

        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2)

        renderer = MyGLRenderer()

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer)

        // Render the view only when there is a change i
        // n the drawing data
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun onDestroy() {
        renderer.onDestroy()
    }

    fun setRenderTye(sampleType: Int) {
        renderer.setRenderType(sampleType)
    }

    fun setImageData(format: Int, width: Int, height: Int, byteArray: ByteArray) {
        renderer.setImageData(format, width, height, byteArray)
    }

    fun setImageScale(imageScale: Float) {
        renderer.setImageScale(imageScale)
    }
}