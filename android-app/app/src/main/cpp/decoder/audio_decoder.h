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

#define CHANNEL_PER_FRAME   2
#define BITS_PER_CHANNEL    16
#define BITS_PER_BYTE       8

// Duration of one decoded audio packet (in seconds).
// 0.04f → 40 ms per packet.
static const float AUDIO_PACKET_SEC = 0.04f;

#define LOG_TAG "AudioDecoderLog"

struct PcmFrame {
    short *audioBuffer;
    int   audioSize;       // number of samples (interleaved)
    float duration;
    float startPosition;
    PcmFrame() {
        audioBuffer    = NULL;
        audioSize      = 0;
        duration       = 0;
        startPosition  = 0;
    }
    ~PcmFrame() {
        if (NULL != audioBuffer) {
            delete[] audioBuffer;
            audioBuffer    = NULL;
            audioSize      = 0;
            duration       = 0;
            startPosition  = 0;
        }
    }
};

class AudioDecoder {

private:
    AVFormatContext *avFormatContext;
    AVCodecContext  *avCodecContext;
    AVPacket        *avPacket;
    AVFrame         *avFrame;
    SwrContext      *swrContext;
    AVStream        *audioStream = nullptr;

    int     audioIndex = AVERROR_STREAM_NOT_FOUND;
    int     sampleRate = 0;
    int     channels   = 0;
    // packetBufferSize is **samples per packet** (not bytes!)
    int     packetBufferSize = 0;
    int64_t duration = 0;
    float   time_base = 0.0f;

    /** decoded data currently buffered in audioBuffer */
    short  *audioBuffer       = nullptr;
    int     audioBufferCursor = 0;  // in samples
    int     audioBufferSize   = 0;  // in samples
    float   audioDuration     = 0;
    float   audioStartPosition = 0;

    /** seek **/
    bool    need_seek = false;
    int64_t time_seek = -1;

    void seekFrame();

public:
    int   initAudioDecoder(const char *string);
    bool  audioCodecIsSupported();
    void  destroy();
    void  prepare();
    PcmFrame* decoderAudioPacket();
    int   readSampleData(short *pInt, int size);
    int   readFrame();
    int64_t getDuration();
    int   getSampleRate();
    int   getChannels();

    // samples per packet (interleaved stereo)
    int   getPacketBufferSize();

    // helper for SoundService: frames per packet (per channel)
    int   getFramesPerPacket() { return packetBufferSize / CHANNEL_PER_FRAME; }

    void  seek(const long seek_time);
};

#endif //FFMPEGDECODER_AUDIO_DECODER_H