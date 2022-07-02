#include <jni.h>
#include <sound_service.h>

//
// Created by guoxinggen on 2022/6/29.
//

SoundService* soundService = NULL;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_setAudioDataSource(JNIEnv *env, jobject thiz, jstring audio_path) {
    const char *audioPath = env->GetStringUTFChars(audio_path, NULL);
    soundService = SoundService::GetInstance();
    return soundService->initSongDecoder(audioPath);
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getAudioSampleRate(JNIEnv *env, jobject thiz) {
    return soundService->getAccompanySampleRate();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_play(JNIEnv *env, jobject thiz) {
    soundService->initSoundTrack();
    soundService->play();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_pause(JNIEnv *env, jobject thiz) {
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_stop(JNIEnv *env, jobject thiz) {
    soundService->stop();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getProgress(JNIEnv *env, jobject thiz) {
    return soundService->getCurrentTimeMills();
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_getDuration(JNIEnv *env, jobject thiz) {
    return soundService->getDurationTimeMills();
}