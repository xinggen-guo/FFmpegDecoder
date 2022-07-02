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
    if(NULL != soundService) {
        return soundService->getAccompanySampleRate();
    } else {
        return -1;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_play(JNIEnv *env, jobject thiz) {
    if(NULL != soundService) {
        soundService->initSoundTrack();
        soundService->play();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_pause(JNIEnv *env, jobject thiz) {
    if(NULL != soundService) {
        soundService->pause();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_resume(JNIEnv *env, jobject thiz) {
    if(soundService != NULL) {
        soundService->resume();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_stop(JNIEnv *env, jobject thiz) {
    if(NULL != soundService) {
        soundService->stop();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opensles_SoundTrackController_seek(JNIEnv *env, jobject thiz, jint progress) {
    if(NULL != soundService){
        soundService->seek(progress);
    }
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