package com.audio.study.ffmpegdecoder.opengl

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MyGLRenderer : GLSurfaceView.Renderer {

    private val nativeRender: MyNativeRender = MyNativeRender()
    private val mSampleType = GLType.SAMPLE_TYPE

    init {
        nativeRender.init()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set the background frame color
        nativeRender.onSurfaceCreated()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        nativeRender.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        // Redraw background color
        nativeRender.onDrawFrame()
    }

    fun onDestroy() {
       nativeRender.onDestroy()
    }

    fun setRenderType(sampleType: Int) {
        nativeRender.setRenderType(sampleType)
    }

    fun setImageData(format: Int, width: Int, height: Int, byteArray: ByteArray) {
        nativeRender.setImageData(format, width, height, byteArray)
    }
}