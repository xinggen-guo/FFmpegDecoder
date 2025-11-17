//
// Created by xinggen guo on 2025/11/17.
//
#include <jni.h>
#include <string>
#include "audio_decoder_controller.h"// if needed
#include "CommonTools.h"
#include "sound_service.h"

// Optional: namespace or static global if you want
// For now we assume SoundService is a singleton with getInstance().

extern "C" {

// Helper: convert jstring to std::string
static std::string JStringToStdString(JNIEnv *env, jstring jstr) {
    if (!jstr) return {};
    const char *utf = env->GetStringUTFChars(jstr, nullptr);
    std::string result(utf ? utf : "");
    env->ReleaseStringUTFChars(jstr, utf);
    return result;
}

/**
 * boolean nativePrepare(String path)
 */
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativePrepare(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring jPath) {

    std::string path = JStringToStdString(env, jPath);
    LOGI("OpenSlAudioEngine.nativePrepare path=%s", path.c_str());

    SoundService *service = SoundService::GetInstance();  // or getInstance()
    if (!service) {
        LOGE("SoundService instance is null");
        return JNI_FALSE;
    }

    // 1) init decoder with path
    // 2) create OpenSL engine & player
    // 3) prepare metadata (duration, sample rate, etc.)

    bool ok = service->initSongDecoder(path.c_str());
    if (!ok) {
        LOGE("initSongDecoder failed");
        return JNI_FALSE;
    }

    // If you have duration metadata:
    // service->getDuration(); or store inside service.

    return JNI_TRUE;
}

/**
 * void nativePlay()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativePlay(
        JNIEnv *env,
        jobject /*thiz*/) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return;

    LOGI("OpenSlAudioEngine.nativePlay");
    service->initSoundTrack();
    service->play();   // adapt to your real method, e.g. startPlay / play
}

/**
 * void nativePause()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativePause(
        JNIEnv *env,
        jobject /*thiz*/) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return;

    LOGI("OpenSlAudioEngine.nativePause");
    service->pause();   // adapt: set playingState, stop queue, etc.
}

/**
 * void nativeResume()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativeResume(
        JNIEnv *env,
        jobject /*thiz*/) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return;

    LOGI("OpenSlAudioEngine.nativeResume");
    service->resume();  // or start() if you handle paused state internally
}

/**
 * void nativeSeekTo(long positionMs)
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativeSeekTo(
        JNIEnv *env,
        jobject /*thiz*/,
        jlong positionMs) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return;

    LOGI("OpenSlAudioEngine.nativeSeekTo: %lld ms", (long long) positionMs);
    // Inside SoundService, you probably:
    // - flush decoder queue
    // - av_seek_frame on audio stream
    // - reset internal progress clock
    service->seek(positionMs);
}

/**
 * void nativeRelease()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativeRelease(
        JNIEnv *env,
        jobject /*thiz*/) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return;

    LOGI("OpenSlAudioEngine.nativeRelease");
    service->stop();
    service->DestroyContext();  // if you have this
}

/**
 * long nativeGetDurationMs()
 */
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativeGetDurationMs(
        JNIEnv *env,
        jobject /*thiz*/) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return 0;

    // have duration in ms from decoder metadata
    jlong durationMs = (jlong) service->getDurationTimeMills(); // implement this if missing
    return durationMs;
}

/**
 * long nativeGetAudioClockMs()
 *
 * This is the "master clock" used by XMediaPlayer to sync video.
 * It should represent "how much audio has actually been played", not
 * just decoded. Usually you update it in your OpenSL buffer callback code.
 */
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_OpenSlAudioEngine_nativeGetAudioClockMs(
        JNIEnv *env,
        jobject /*thiz*/) {
    SoundService *service = SoundService::GetInstance();
    if (!service) return 0;

    jlong clockMs = (jlong) service->getAudioClockMs(); // implement if missing
    return clockMs;
}
}// extern "C"