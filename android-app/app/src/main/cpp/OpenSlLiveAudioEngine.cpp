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

/** feed BGM PCM from Java */
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_live_engine_OpenSlLiveAudioEngine_nativePushBgmPcm(
        JNIEnv *env, jobject thiz, jlong handle, jshortArray buffer, jint size) {
    (void)thiz;
    auto* engine = reinterpret_cast<LiveAudioEngineImpl*>(handle);
    if (!engine || !buffer || size <= 0) return;

    jshort* data = env->GetShortArrayElements(buffer, nullptr);
    if (!data) return;

    engine->pushBgmPcm(reinterpret_cast<short*>(data), static_cast<int>(size));

    env->ReleaseShortArrayElements(buffer, data, JNI_ABORT);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_live_engine_OpenSlLiveAudioEngine_nativePushMixedPcm(
        JNIEnv* env, jobject thiz, jlong handle, jshortArray data, jint size) {
    (void)thiz;
    auto* engine = reinterpret_cast<LiveAudioEngineImpl*>(handle);
    if (!engine || !data || size <= 0) return;

    jshort* ptr = env->GetShortArrayElements(data, nullptr);
    if (!ptr) return;

    engine->pushMixedPcm(reinterpret_cast<short*>(ptr), static_cast<int>(size));

    // No need for JNI to copy back to Java
    env->ReleaseShortArrayElements(data, ptr, JNI_ABORT);
}

