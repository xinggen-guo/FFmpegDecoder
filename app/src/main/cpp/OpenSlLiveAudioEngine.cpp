#include <jni.h>
#include "live/LiveAudioEngineImpl.h"

//
// Created by xinggen guo on 2025/11/20.
//

extern "C"
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_live_engine_OpenSlLiveAudioEngine_nativeCreateLiveEngine(
        JNIEnv *env, jobject thiz, jint sampleRate, jint channels, jint bufferMs) {
    auto* engine = new LiveAudioEngineImpl(
            static_cast<int>(sampleRate),
            static_cast<int>(channels),
            static_cast<int>(bufferMs),
            env,
            thiz
    );
    return reinterpret_cast<jlong>(engine);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_live_engine_OpenSlLiveAudioEngine_nativeStartLiveLoopback(
        JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    auto* engine = reinterpret_cast<LiveAudioEngineImpl*>(handle);
    if (engine) {
        engine->startLoopback();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_live_engine_OpenSlLiveAudioEngine_nativeStopLiveLoopback(
        JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    auto* engine = reinterpret_cast<LiveAudioEngineImpl*>(handle);
    if (engine) {
        engine->stopLoopback();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_live_engine_OpenSlLiveAudioEngine_nativeReleaseLiveEngine(
        JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    auto* engine = reinterpret_cast<LiveAudioEngineImpl*>(handle);
    delete engine;
}