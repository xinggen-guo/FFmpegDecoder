//
// Created by guoxinggen on 2022/6/8.
//

#ifndef FFMPEGDECODER_AUDIO_DECODER_H
#define FFMPEGDECODER_AUDIO_DECODER_H


#include "audio_decoder.h"
#include <stdio.h>
#include <stdlib.h>

extern "C" {
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
}

#define CHANNEL_PER_FRAME	2
#define BITS_PER_CHANNEL		16
#define BITS_PER_BYTE		8

#define LOG_TAG "AudioDecoder"

typedef unsigned char byte;
typedef struct AudioPacket{
    short *audioBuffer;
    int audioSize;
    AudioPacket(){
        audioBuffer = NULL;
        audioSize = 0;
    }

    ~AudioPacket(){
        if (NULL != audioBuffer) {
            delete[] audioBuffer;
            audioSize = 0;
        }
    }
} AudioPacket;

class AudioDecoder {

private:
    AVFormatContext *avFormatContext;
    AVCodecContext *avCodecContext;
    AVPacket *avPacket;
    AVFrame *avFrame;
    SwrContext *swrContext;
    void *swrBuffer;
    int swrBufferSize;

    int audioIndex = AVERROR_STREAM_NOT_FOUND;
    int sampleRate;
    int packetBufferSize;

    /** 解码数据 **/
    short* audioBuffer;
    int audioBufferCursor;
    int audioBufferSize;

public:
    int initAudioDecoder(const char *string, int *metaArray);
    bool audioCodecIsSupported();
    void destroy();
    void prepare();
    AudioPacket* decoderAudioPacket();
    int readSampleData(short *pInt, int size);

    int readFrame();
};


#endif //FFMPEGDECODER_AUDIO_DECODER_H

