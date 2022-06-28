//
// Created by guoxinggen on 2022/6/18.
//

#ifndef FFMPEGDECODER_COMMONTOOLS_H
#define FFMPEGDECODER_COMMONTOOLS_H

#include <jni.h>
#include <android/log.h>

#define MAX(a, b)  (((a) > (b)) ? (a) : (b))
#define MIN(a, b)  (((a) < (b)) ? (a) : (b))
#define UINT64_C        uint64_t
#define INT16_MAX        32767
#define INT16_MIN       -32768
#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define ARRAY_LEN(a) (sizeof(a) / sizeof(a[0]))
#endif //FFMPEGDECODER_COMMONTOOLS_H
