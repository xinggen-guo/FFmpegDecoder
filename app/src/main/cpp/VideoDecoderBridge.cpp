//
// Created by guoxinggen on 2025/11/15.
//

#include <jni.h>
#include <cstring>  // for memcpy

#include "video_decoder_controller.h"  // <-- new controller
#include "video_frame.h"               // <-- VideoFrame struct
#include "MediaStatus.h"

// Single global controller for this demo.
static VideoDecoderController* gVideoController = nullptr;

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeOpenVideo(
        JNIEnv* env, jobject /*thiz*/, jstring path_) {

    const char* path = env->GetStringUTFChars(path_, nullptr);
    if (!path) return JNI_FALSE;

    // If already have a controller, destroy it first
    if (gVideoController) {
        gVideoController->destroy();
        delete gVideoController;
        gVideoController = nullptr;
    }

    gVideoController = new VideoDecoderController();
    int ret = gVideoController->init(path);

    env->ReleaseStringUTFChars(path_, path);

    if (ret != 0) {
        delete gVideoController;
        gVideoController = nullptr;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeCloseVideo(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    if (gVideoController) {
        gVideoController->destroy();
        delete gVideoController;
        gVideoController = nullptr;
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeGetWidth(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    if (!gVideoController) return 0;
    return gVideoController->getWidth();
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeGetHeight(
        JNIEnv* /*env*/, jobject /*thiz*/) {
    if (!gVideoController) return 0;
    return gVideoController->getHeight();
}

/**
 * Decode one frame from the controller's queue and copy RGBA into Java DirectByteBuffer.
 *
 * @param byteBuffer DirectByteBuffer with capacity >= width * height * 4
 * @return
 *   >0: bytes written (frameSize)
 *    0: EOF (no more frames)
 *   -2: no frame available yet (buffering)
 *   <0: other error
 */
extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeDecodeToRgba(
        JNIEnv* env, jobject /*thiz*/, jobject byteBuffer) {

    if (!gVideoController || !byteBuffer) return -1;

    // Get native pointer and capacity from DirectByteBuffer
    uint8_t* dst = static_cast<uint8_t*>(env->GetDirectBufferAddress(byteBuffer));
    jlong cap = env->GetDirectBufferCapacity(byteBuffer);
    if (!dst || cap <= 0) return -1;

    VideoFrame* frame = nullptr;
    int ret = gVideoController->getFrame(frame);

    if (ret == MEDIA_STATUS_EOF) {
        // EOF: no more frames; nothing to copy
        return MEDIA_STATUS_EOF;
    } else if (ret == MEDIA_STATUS_BUFFERING) {
        // Buffering: no frame yet, but not EOF
        return MEDIA_STATUS_BUFFERING;
    } else if (ret < MEDIA_STATUS_EOF) {
        // Other error
        if (frame) {
            VideoDecoderController::freeFrame(frame);
        }
        return ret;
    }

    // ret == 1: we have a valid frame
    if (!frame || !frame->data || frame->dataSize <= 0) {
        if (frame) {
            VideoDecoderController::freeFrame(frame);
        }
        return MEDIA_STATUS_ERROR;
    }

    // Ensure buffer is big enough
    if (cap < frame->dataSize) {
        // Java buffer too small: this is a usage/config error
        VideoDecoderController::freeFrame(frame);
        return MEDIA_STATUS_ERROR;
    }

    // Copy RGBA data into Java ByteBuffer
    std::memcpy(dst, frame->data, static_cast<size_t>(frame->dataSize));
    int written = frame->dataSize;

    // Free frame back on native side
    VideoDecoderController::freeFrame(frame);

    return written;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeDecodeToRgbaWithPts(
        JNIEnv* env, jobject /*thiz*/, jobject byteBuffer, jlongArray ptsOutMs) {

    if (!gVideoController || !byteBuffer || !ptsOutMs) return -1;

    uint8_t* dst = static_cast<uint8_t*>(env->GetDirectBufferAddress(byteBuffer));
    jlong cap = env->GetDirectBufferCapacity(byteBuffer);
    if (!dst || cap <= 0) return -1;

    VideoFrame* frame = nullptr;
    int ret = gVideoController->getFrame(frame);

    if (ret == MEDIA_STATUS_EOF) {
        // EOF
        return MEDIA_STATUS_EOF;
    } else if (ret == MEDIA_STATUS_BUFFERING) {
        // BUFFERING (you mapped -2 → 2 already)
        return MEDIA_STATUS_BUFFERING;
    } else if (ret < MEDIA_STATUS_EOF) {
        if (frame) {
            VideoDecoderController::freeFrame(frame);
        }
        return MEDIA_STATUS_ERROR; // ERROR
    }

    // ret == 1: got a frame
    if (!frame || !frame->data || frame->dataSize <= 0) {
        if (frame) {
            VideoDecoderController::freeFrame(frame);
        }
        return MEDIA_STATUS_ERROR;
    }

    if (cap < frame->dataSize) {
        VideoDecoderController::freeFrame(frame);
        return MEDIA_STATUS_ERROR;
    }

    // copy RGBA
    std::memcpy(dst, frame->data, static_cast<size_t>(frame->dataSize));

    // write PTS to ptsOutMs[0]
    jlong pts = static_cast<jlong>(frame->ptsMs); // ptsMs is double, but ms fits in long
    env->SetLongArrayRegion(ptsOutMs, 0, 1, &pts);

    int written = frame->dataSize;
    VideoDecoderController::freeFrame(frame);

    return written;
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativePause(JNIEnv *env, jobject thiz) {
    if (gVideoController) {
        gVideoController->pause();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeResume(JNIEnv *env, jobject thiz) {
    if (gVideoController) {
        gVideoController->resume();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativePlay(JNIEnv *env, jobject thiz) {
    if (gVideoController) {
        gVideoController->play();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_video_VideoPlayer_nativeSeek(JNIEnv *env, jobject thiz,
                                                                jlong pending_progress) {
    if (gVideoController) {
        gVideoController->seek(pending_progress);
    }
}