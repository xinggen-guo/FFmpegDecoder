//
// Created by guoxinggen on 2022/6/21.
//

#include <jni.h>
#include "audio_decoder_controller.h"

AudioDecoderController *audioDecoderController;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_getMusicMeta(JNIEnv *env, jobject thiz, jstring music_path, jintArray meta_array) {
    const char *audioPath = env->GetStringUTFChars(music_path, NULL);
    jint *metaArray = env->GetIntArrayElements(meta_array, NULL);
    AudioDecoderController *audioDecoderController = new AudioDecoderController();
    int result = audioDecoderController->getMusicMeta(audioPath, metaArray);
    delete audioDecoderController;
    env->ReleaseIntArrayElements(meta_array, metaArray, NULL);
    env->ReleaseStringUTFChars(music_path, audioPath);
    return result == 0;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_prepareDecoder(JNIEnv *env, jobject thiz, jstring music_path) {
    const char *audioPath = env->GetStringUTFChars(music_path, NULL);
    audioDecoderController = new AudioDecoderController();
    int result = audioDecoderController->prepare(audioPath);
    return result == 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_closeFile(JNIEnv *env, jobject thiz) {
    if (NULL != audioDecoderController) {
        audioDecoderController->destroy();
        delete audioDecoderController;
        audioDecoderController = NULL;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_readSamples(JNIEnv *env, jobject thiz, jshortArray samples, jint size) {
    if (NULL != audioDecoderController) {
        short *samplesArray = env->GetShortArrayElements(samples, NULL);
        int result = audioDecoderController->readSamples(samplesArray, size);
        env->ReleaseShortArrayElements(samples, samplesArray, 0);
        return result;
    } else {
        return -1;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_nativeSeekPlay(JNIEnv *env, jobject thiz, jlong seek_position) {
    if(NULL != audioDecoderController){
        audioDecoderController->seek(seek_position);
    }
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_nativeGetProgress(JNIEnv *env, jobject thiz) {
    if(NULL != audioDecoderController){
        return (jlong)(audioDecoderController->getProgress());
    }
}
