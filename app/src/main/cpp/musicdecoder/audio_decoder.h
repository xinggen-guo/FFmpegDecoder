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

#define LOG_TAG "AudioDecoderLog"

typedef struct AudioPacket{
    short *audioBuffer;
    int audioSize;
    float duration;
    float startPosition;
    AudioPacket(){
        audioBuffer = NULL;
        audioSize = 0;
        duration = 0;
        startPosition = 0;
    }
    ~AudioPacket(){
        if (NULL != audioBuffer) {
            delete[] audioBuffer;
            audioSize = 0;
            duration = 0;
            startPosition = 0;
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

    int audioIndex = AVERROR_STREAM_NOT_FOUND;
    int sampleRate;
    int packetBufferSize;
    int duration;
    float time_base;

    /** 解码数据 **/
    short* audioBuffer;
    int audioBufferCursor;
    int audioBufferSize;
    float audioDuration;
    float audioStartPosition;

    /** seek **/
    bool need_seek = false;
    long time_seek;

public:
    int initAudioDecoder(const char *string);
    bool audioCodecIsSupported();
    void destroy();
    void prepare();
    AudioPacket* decoderAudioPacket();
    int readSampleData(short *pInt, int size);
    int readFrame();
    int getDuration();
    int getSampleRate();
    int getPacketBufferSize();
    void seek(const long seek_time);
    void seekFrame();
};


#endif //FFMPEGDECODER_AUDIO_DECODER_H

