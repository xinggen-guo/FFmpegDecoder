package com.audio.study.ffmpegdecoder.opengl

interface GLType {

    companion object {
        //gl类型
        const val SAMPLE_TYPE = 200
        const val SAMPLE_TYPE_TRIANGLE              = SAMPLE_TYPE + 1
        const val SAMPLE_TYPE_KEY_RECTANGLE         = SAMPLE_TYPE_TRIANGLE + 1
        const val SAMPLE_TYPE_TEXTURE_MAP           = SAMPLE_TYPE_KEY_RECTANGLE + 1

        //图片类型
        const val IMAGE_FORMAT_RGBA = 0x01
    }
}