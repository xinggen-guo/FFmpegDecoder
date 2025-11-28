#include <jni.h>
#include <sound_service.h>
#include "audio_visualizer.h"

//
// Created by guoxinggen on 2022/6/29.
//

SoundService *soundService = NULL;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_setAudioDataSource(JNIEnv *env,
                                                                                    jobject thiz,
                                                                                    jstring audio_path,
                                                                                    jobject callBack) {
    const char *audioPath = env->GetStringUTFChars(audio_path, NULL);
    soundService = SoundService::GetInstance();
    JavaVM *g_jvm;
    env->GetJavaVM(&g_jvm);
    soundService->setOnCompletionCallback(g_jvm,  env->NewGlobalRef(callBack));
    return soundService->initSongDecoder(audioPath);
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getAudioSampleRate(JNIEnv *env,
                                                                                    jobject thiz) {
    if (NULL != soundService) {
        return soundService->getAccompanySampleRate();
    } else {
        return -1;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_play(JNIEnv *env, jobject thiz) {
    if (NULL != soundService) {
        soundService->initSoundTrack();
        soundService->play();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_pause(JNIEnv *env, jobject thiz) {
    if (NULL != soundService) {
        soundService->pause();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_resume(JNIEnv *env, jobject thiz) {
    if (soundService != NULL) {
        soundService->resume();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_stop(JNIEnv *env, jobject thiz) {
    if (NULL != soundService) {
        soundService->stop();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_seek(JNIEnv *env, jobject thiz,
                                                                      jint progress) {
    if (NULL != soundService) {
        soundService->seek(progress);
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getProgress(JNIEnv *env,
                                                                             jobject thiz) {
    if (NULL != soundService) {
        return soundService->getCurrentTimeMills();
    }
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getAudioClockMs(JNIEnv *env,
                                                                                 jobject thiz) {
    if (NULL != soundService) {
        return soundService->getAudioClockMs();
    }
}
extern "C"
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getDuration(JNIEnv *env,
                                                                             jobject thiz) {
    if (NULL != soundService) {
        return soundService->getDurationTimeMills();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_setVisualizerEnable(JNIEnv *env,
                                                                                     jobject thiz,
                                                                                     jboolean d) {
    if (NULL != soundService) {
        soundService->setVisualizerEnabled(d);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_nativeGetSpectrum(JNIEnv *env,
                                                                                   jobject thiz,
                                                                                   jfloatArray outArray) {
    if (NULL != soundService) {
        jsize len = env->GetArrayLength(outArray);
        std::vector<float> tmp(len);
        AudioVisualizer::instance().getSpectrum(tmp.data(), len);
        env->SetFloatArrayRegion(outArray, 0, len, tmp.data());
    }

}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_nativeGetWaveform(JNIEnv *env,
                                                                                   jobject thiz,
                                                                                   jfloatArray outArray) {
    if (NULL != soundService) {
        jsize len = env->GetArrayLength(outArray);
        std::vector<float> tmp(len);
        AudioVisualizer::instance().getWaveform(tmp.data(), len);
        env->SetFloatArrayRegion(outArray, 0, len, tmp.data());
    }
}
