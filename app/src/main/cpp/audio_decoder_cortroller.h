//
// Created by guoxinggen on 2022/6/6.
//

#ifndef FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H
#define FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H

#include <jni.h>

extern "C" {
#include <libavformat/avformat.h>
#include <libswresample/swresample.h>
}


class AudioDecoder {

public:
    AVFormatContext *avFormatContext;
    AVCodecContext *avCodecContext;

    AVPacket *avPacket;
    AVFrame *avFrame;

    SwrContext *swrContext;

    int audioIndex = AVERROR_STREAM_NOT_FOUND;
    int sampleRate;

    int initAudioDecoder(const char *string, int *metaArray);

    bool audioCodecIsSupported();

    void destroy();

    int readSapmles(short *pInt,int size);

    void prepare();

    int readSapmlesAndPlay(short *pInt, int i, _JNIEnv *pEnv);
};


#endif //FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H