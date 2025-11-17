//
// Created by xinggen guo on 2025/11/17.
//

#include <jni.h>
#include <string>
#include <cstdint>
#include <cstring>
#include "audio_decoder_controller.h"// if needed
#include "CommonTools.h"

struct AudioMeta {
    int sampleRate = 44100;
    int channels = 2;
    int durationMs = 0;
};

// Global state for this demo backend.
static AudioDecoderController *gAudioDecoder = nullptr;
static AudioMeta gAudioMeta;
static int64_t gDecodedSamples = 0;  // total *samples* decoded (all channels)

/** Helper: jstring -> std::string */
static std::string JStringToStdString(JNIEnv *env, jstring jstr) {
    if (!jstr) return {};
    const char *utf = env->GetStringUTFChars(jstr, nullptr);
    std::string s(utf ? utf : "");
    env->ReleaseStringUTFChars(jstr, utf);
    return s;
}

extern "C" {

/**
 * boolean nativePrepareDecoder(String path)
 */
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativePrepareDecoder(
        JNIEnv *env,
        jobject /*thiz*/,
        jstring jPath) {

    std::string path = JStringToStdString(env, jPath);
    LOGI("AudioTrackAudioEngine.nativePrepareDecoder path=%s", path.c_str());

    // Clean old decoder if exists
    if (gAudioDecoder) {
        delete gAudioDecoder;
        gAudioDecoder = nullptr;
    }

    gAudioDecoder = new AudioDecoderController();
    if (!gAudioDecoder) {
        LOGE("Failed to allocate AudioDecoderController");
        return JNI_FALSE;
    }

    int metaData[3] = {0};
    bool ok = gAudioDecoder->getMusicMeta(path.c_str(), metaData);
    if (!ok) {
        LOGE("AudioDecoderController.init failed");
        delete gAudioDecoder;
        gAudioDecoder = nullptr;
        return JNI_FALSE;
    }

    gAudioMeta.sampleRate = metaData[0];
    gAudioMeta.channels = gAudioDecoder->getChannels();
    gAudioMeta.durationMs = metaData[2];

    if (gAudioMeta.sampleRate <= 0) gAudioMeta.sampleRate = 44100;
    if (gAudioMeta.channels <= 0) gAudioMeta.channels = 2;

    gDecodedSamples = 0; // reset clock base

    LOGI("Audio meta: sr=%d ch=%d duration=%dms",
         gAudioMeta.sampleRate, gAudioMeta.channels, gAudioMeta.durationMs);

    return JNI_TRUE;
}

/**
 * void nativeSeekTo(long positionMs)
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativeSeekTo(
        JNIEnv *env,
        jobject /*thiz*/,
        jlong positionMs) {

    if (!gAudioDecoder) {
        LOGE("nativeSeekTo: decoder is null");
        return;
    }

    LOGI("AudioTrackAudioEngine.nativeSeekTo: %lld ms", (long long) positionMs);

// 1. Ask decoder to seek internally (av_seek_frame + flush)
//    Adapt this to your decoder's API.
    gAudioDecoder->seek((int64_t) positionMs);

// 2. Reset decodedSamples so our "clock" is near the new position.
//    This is optional, but recommended.
//    We approximate: frames = ms * sampleRate / 1000
    int64_t frames = (int64_t) positionMs * gAudioMeta.sampleRate / 1000;
    gDecodedSamples = frames * gAudioMeta.channels;
}

/**
 * void nativeReleaseDecoder()
 */
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativeReleaseDecoder(
        JNIEnv *env,
        jobject /*thiz*/) {

    LOGI("AudioTrackAudioEngine.nativeReleaseDecoder");

    if (gAudioDecoder) {
// gAudioDecoder->destroy();
        delete gAudioDecoder;
        gAudioDecoder = nullptr;
    }

    gDecodedSamples = 0;
    gAudioMeta = AudioMeta{};
}

/**
 * long nativeGetDurationMs()
 */
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativeGetDurationMs(
        JNIEnv *env,
        jobject /*thiz*/) {

    if (!gAudioDecoder) return 0;

    // If duration can change or is lazy-loaded, you can re-query here:
    // gAudioMeta.durationMs = gAudioDecoder->getDurationMs();
    return (jlong) gAudioMeta.durationMs;
}

/**
 * int nativeGetSampleRate()
 */
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativeGetSampleRate(
        JNIEnv *env,
        jobject /*thiz*/) {

    return (jint) gAudioMeta.sampleRate;
}

/**
 * int nativeGetChannelCount()
 */
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativeGetChannelCount(
        JNIEnv *env,
        jobject /*thiz*/) {

    return (jint) gAudioMeta.channels;
}

/**
 * int nativeReadSamples(short[] bufferShorts, int maxSamples, long[] ptsOut)
 *
 * @return
 *   >0 : number of samples decoded
 *    0 : EOF
 *   -1 : error
 */
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_player_engine_AudioTrackAudioEngine_nativeReadSamples(
        JNIEnv *env,
        jobject /*thiz*/,
        jshortArray jBuffer,
        jint maxSamples,
        jlongArray jPtsOut) {

    if (!gAudioDecoder || !jBuffer || maxSamples <= 0 || !jPtsOut) {
        LOGE("nativeReadSamples: invalid arguments");
        return -1;
    }

    jsize bufLen = env->GetArrayLength(jBuffer);
    if (bufLen < maxSamples) {
        LOGE("nativeReadSamples: buffer too small: len=%d, maxSamples=%d",
             bufLen, maxSamples);
        return -1;
    }

// Temporary C++ buffer to hold PCM (or you can decode directly to jshortArray)
// Here we decode directly into a stack / heap buffer and then copy to Java array.
    std::unique_ptr<short[]> buffer(new(std::nothrow) short[maxSamples]);
    if (!buffer) {
        LOGE("nativeReadSamples: memory alloc failed");
        return -1;
    }

// ---- Decode one chunk ----
// must adapt this to your actual API.
// Typical pattern:
//
//   int decodedSamples = gAudioDecoder->readSamples(buffer.get(), maxSamples);
//   // decodedSamples = number of *samples* (not bytes)
//
// For EOF: decodedSamples == 0
// For error: decodedSamples < 0
//
    int decodedSamples = gAudioDecoder->readSamples(buffer.get(), maxSamples); // TODO
    if (decodedSamples < 0) {
        LOGE("nativeReadSamples: decoder error=%d", decodedSamples);
        return -1;
    }
    if (decodedSamples == 0) {
// EOF
        return 0;
    }

// ---- Copy PCM to Java array ----
    env->SetShortArrayRegion(jBuffer, 0, decodedSamples, buffer.get());

// ---- Compute PTS for the FIRST sample of this buffer ----
// We use a simple "decodedSamples counter" clock:
//   framesBefore = gDecodedSamples / channels
//   ptsMs = framesBefore * 1000 / sampleRate
//
// Then we increase gDecodedSamples by decodedSamples.
    int channels = (gAudioMeta.channels > 0) ? gAudioMeta.channels : 2;
    int sampleRate = (gAudioMeta.sampleRate > 0) ? gAudioMeta.sampleRate : 44100;

    int64_t framesBefore = (channels > 0)
                           ? (gDecodedSamples / channels)
                           : 0;
    int64_t ptsMs = (sampleRate > 0)
                    ? (framesBefore * 1000LL / sampleRate)
                    : 0;

    gDecodedSamples += decodedSamples;

// write ptsOut[0]
    jlong ptsVal = (jlong) ptsMs;
    env->SetLongArrayRegion(jPtsOut, 0, 1, &ptsVal);

    return decodedSamples;
}

} // extern "C"