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

typedef unsigned char byte;
typedef signed short SInt16;

//把一个short转换为一个长度为2的byte数组
inline void converttobytearray(SInt16 source, byte* bytes2) {
    bytes2[0] = (byte) (source & 0xff);
    bytes2[1] = (byte) ((source >> 8) & 0xff);
}


//将一个short数组转换为一个byte数组---清唱时由于不需要和伴奏合成，所以直接转换;还有一个是当解码完成之后，需要将short变为byte数组，写入文件
inline void convertByteArrayFromShortArray(SInt16 *shortarray, int size, byte *bytearray) {
    byte* tmpbytearray = new byte[2];
    for (int i = 0; i < size; i++) {
        converttobytearray(shortarray[i], tmpbytearray);
        bytearray[i * 2] = tmpbytearray[0];
        bytearray[i * 2 + 1] = tmpbytearray[1];
    }
    delete[] tmpbytearray;
}


#endif //FFMPEGDECODER_COMMONTOOLS_H
