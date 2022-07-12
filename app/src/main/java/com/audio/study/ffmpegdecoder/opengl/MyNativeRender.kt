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

    fun setRenderType(sampleType: Int) {
        nativeSetRenderType(sampleType)
    }

    fun setImageData(format: Int, width: Int, height: Int, byteArray: ByteArray) {
        nativeSetImageData(format,width,height,byteArray)
    }

    private external fun nativeInit()

    private external fun nativeSurfaceCreate()

    private external fun nativeSurfaceChange(width: Int, height: Int)

    private external fun nativeDrawFrame()

    private external fun nativeDestroy()

    private external fun nativeSetRenderType(sampleType: Int)

    private external fun nativeSetImageData(format: Int, width: Int, height: Int, byteArray: ByteArray)

    companion object {
        init {
            System.loadLibrary("ffmpegdecoder")
        }
    }
}