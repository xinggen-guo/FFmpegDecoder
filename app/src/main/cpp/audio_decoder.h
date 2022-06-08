//
// Created by guoxinggen on 2022/6/8.
//

#ifndef FFMPEGDECODER_AUDIO_DECODER_H
#define FFMPEGDECODER_AUDIO_DECODER_H


#include "audio_decoder.h"

extern "C" {
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
}


class AudioDecoder {
private:
    AVFormatContext *avFormatContext;
    AVCodecContext *avCodecContext;
    AVPacket *avPacket;
    AVFrame *avFrame;
    SwrContext *swrContext;
    int audioIndex = AVERROR_STREAM_NOT_FOUND;
    int sampleRate;

public:
    int initAudioDecoder(const char *string, int *metaArray);

    bool audioCodecIsSupported();

    void destroy();

    int audioDecoder(short *pInt, int size);

    void prepare();
};


#endif //FFMPEGDECODER_AUDIO_DECODER_H

