package com.audio.study.ffmpegdecoder.opengl

class MyNativeRender {

    fun init() {
        nativeInit()
    }

    fun onSurfaceCreated() {
        nativeSurfaceCreate()
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        nativeSurfaceChange(width, height)
    }

    fun onDrawFrame() {
        nativeDrawFrame()
    }

    fun onDestroy() {
        nativeDestroy()
    }

    private external fun nativeInit()

    private external fun nativeSurfaceCreate()

    private external fun nativeSurfaceChange(width: Int, height: Int)

    private external fun nativeDrawFrame()

    private external fun nativeDestroy()

    companion object {
        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}