//
// Created by xinggen guo on 2025/11/17.
//

#include <jni.h>
#include <string>
#include "video_decoder_controller.h"  // your existing class
#include "CommonTools.h"
#include "MediaStatus.h"

static VideoDecoderController* gVideoController = nullptr;

extern "C" {

// jstring → std::string again
static std::string JStringToStdString(JNIEnv* env, jstring jstr) {
    if (!jstr) return {};
    const char* utf = env->GetStringUTFChars(jstr, nullptr);
    std::string result(utf ? utf : "");
    env->ReleaseStringUTFChars(jstr, utf);
    return result;
}

/**
 * boolean nativePrepare(String path)
 */
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativePrepare(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring jPath) {

    std::string path = JStringToStdString(env, jPath);
    LOGI("FfmpegVideoEngine.nativePrepare path=%s", path.c_str());

    if (gVideoController) {
        delete gVideoController;
        gVideoController = nullptr;
    }

    gVideoController = new VideoDecoderController();
    if (!gVideoController) {
        LOGE("Failed to allocate VideoDecoderController");
        return JNI_FALSE;
    }

    int result = gVideoController->init(path.c_str());   // adapt to your init method
    if (result != MEDIA_STATUS_OK) {
        LOGE("VideoDecoderController init failed");
        delete gVideoController;
        gVideoController = nullptr;
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

/**
 * void nativeStart()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeStart(
        JNIEnv* env,
        jobject /*thiz*/) {
    if (!gVideoController) return;
    LOGI("FfmpegVideoEngine.nativeStart");
    gVideoController->play();  // internally start decode thread or set running=true
}

/**
 * void nativePause()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativePause(
        JNIEnv* env,
        jobject /*thiz*/) {
    if (!gVideoController) return;
    LOGI("FfmpegVideoEngine.nativePause");
    gVideoController->pause();  // implement if not exists
}

/**
 * void nativeResume()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeResume(
        JNIEnv* env,
        jobject /*thiz*/) {
    if (!gVideoController) return;
    LOGI("FfmpegVideoEngine.nativeResume");
    gVideoController->resume(); // implement if not exists
}

/**
 * void nativeSeekTo(long positionMs)
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeSeekTo(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong positionMs) {
    if (!gVideoController) return;
    LOGI("FfmpegVideoEngine.nativeSeekTo: %lld ms", (long long)positionMs);
    gVideoController->seek(positionMs); // wrap your av_seek_frame logic
}

/**
 * void nativeRelease()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeRelease(
        JNIEnv* env,
        jobject /*thiz*/) {
    LOGI("FfmpegVideoEngine.nativeRelease");
    if (gVideoController) {
        gVideoController->stop();   // stop decode thread, etc.
        delete gVideoController;
        gVideoController = nullptr;
    }
}

/**
 * int nativeGetVideoWidth()
 */
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeGetVideoWidth(
        JNIEnv* env,
        jobject /*thiz*/) {
    if (!gVideoController) return 0;
    return (jint)gVideoController->getWidth();
}

/**
 * int nativeGetVideoHeight()
 */
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeGetVideoHeight(
        JNIEnv* env,
        jobject /*thiz*/) {
    if (!gVideoController) return 0;
    return (jint)gVideoController->getHeight();
}

/**
 * int nativeReadFrame(ByteBuffer buffer, long[] ptsOut)
 *
 * 1) decode/dequeue next frame
 * 2) convert to RGBA (width*height*4)
 * 3) copy into buffer
 * 4) set ptsOut[0] = PTS in ms
 * 5) return MediaStatus.OK / EOF / BUFFERING / ERROR
 */
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_FfmpegVideoEngine_nativeReadFrame(
        JNIEnv* env,
        jobject /*thiz*/,
        jobject jBuffer,
        jlongArray jPtsOut) {

    if (!gVideoController || !jBuffer || !jPtsOut) {
        return (jint)MEDIA_STATUS_ERROR; // define these codes in a shared header
    }

    uint8_t* dst = (uint8_t*)env->GetDirectBufferAddress(jBuffer);
    if (!dst) {
        LOGE("nativeReadFrame: buffer is not direct");
        return (jint)MEDIA_STATUS_ERROR;
    }

    // decode & convert:
    // This is pseudocode: adapt to your actual methods
    int64_t ptsMs = 0;

    // e.g.:
    // result = gVideoController->decodeAndConvertToRGBA(dst, capacity, &ptsMs);

    int result = gVideoController->readFrameRGBA(dst, &ptsMs); // you define this

    // write pts to ptsOut[0]
    jlong ptsValue = (jlong)ptsMs;
    env->SetLongArrayRegion(jPtsOut, 0, 1, &ptsValue);

    return result;
}

} // extern "C"