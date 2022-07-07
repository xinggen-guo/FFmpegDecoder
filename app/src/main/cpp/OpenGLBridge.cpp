#include <jni.h>
#include "render/MyGLRenderContext.h"

//
// Created by guoxinggen on 2022/7/5.
//

MyGLRenderContext * myGlRenderContext;


extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeInit(JNIEnv *env, jobject thiz) {
    if(NULL != myGlRenderContext){
        myGlRenderContext->destroy();
    }
    myGlRenderContext = new MyGLRenderContext();
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeSurfaceCreate(JNIEnv *env, jobject thiz) {
    if(NULL != myGlRenderContext){
        myGlRenderContext->surfaceCreate();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeSurfaceChange(JNIEnv *env, jobject thiz, jint width, jint height) {
    if(NULL != myGlRenderContext){
        myGlRenderContext -> surfaceChange(width,height);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeDrawFrame(JNIEnv *env, jobject thiz) {
    if(NULL != myGlRenderContext){
        myGlRenderContext -> drawFrame();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeDestroy(JNIEnv *env, jobject thiz) {
    if(NULL != myGlRenderContext){
        myGlRenderContext->destroy();
    }
}