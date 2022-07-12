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
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeSetRenderType(JNIEnv *env, jobject thiz, jint sample_type) {
    if(NULL != myGlRenderContext){
        myGlRenderContext->setRenderType(sample_type);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_opengl_MyNativeRender_nativeSetImageData(JNIEnv *env, jobject thiz, jint format, jint width, jint height, jbyteArray imageData) {
    if(NULL != myGlRenderContext){
        int len = env->GetArrayLength (imageData);
        uint8_t* buf = new uint8_t[len];
        env->GetByteArrayRegion(imageData, 0, len, reinterpret_cast<jbyte*>(buf));
        myGlRenderContext->setImageData(format, width, height, buf);
        delete[] buf;
        env->DeleteLocalRef(imageData);
    }
}