//
// Created by guoxinggen on 2022/6/21.
//

#include <jni.h>
#include "video_decoder.h"

static VideoDecoder* gVideoDecoder = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeOpenVideo(
        JNIEnv* env, jobject thiz, jstring path_) {

    const char* path = env->GetStringUTFChars(path_, nullptr);
    if (!path) return JNI_FALSE;

    if (gVideoDecoder) {
        delete gVideoDecoder;
        gVideoDecoder = nullptr;
    }
    gVideoDecoder = new VideoDecoder();
    int ret = gVideoDecoder->open(path);

    env->ReleaseStringUTFChars(path_, path);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeCloseVideo(
        JNIEnv* env, jobject thiz) {
    if (gVideoDecoder) {
        delete gVideoDecoder;
        gVideoDecoder = nullptr;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeGetWidth(
        JNIEnv* env, jobject thiz) {
    return gVideoDecoder ? gVideoDecoder->getWidth() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeGetHeight(
        JNIEnv* env, jobject thiz) {
    return gVideoDecoder ? gVideoDecoder->getHeight() : 0;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeDecodeToRgba(
        JNIEnv* env, jobject thiz, jobject byteBuffer) {

    if (!gVideoDecoder || !byteBuffer) return -1;

    uint8_t* dst = (uint8_t*) env->GetDirectBufferAddress(byteBuffer);
    jlong cap = env->GetDirectBufferCapacity(byteBuffer);
    if (!dst || cap <= 0) return -1;

    int ret = gVideoDecoder->decodeFrame();
    if (ret <= 0) {
        return ret; // 0 EOF, <0 error
    }

    int written = gVideoDecoder->toRGBA(dst, (int)cap);
    return written; // bytes
}