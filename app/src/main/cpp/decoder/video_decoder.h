//
// Created by xinggen guo on 2025/11/14.
//

#pragma once

extern "C" {
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
#include <libswscale/swscale.h>
}

#define LOG_TAG "VideoDecoderLog"

class VideoDecoder {
public:
    VideoDecoder();
    ~VideoDecoder();

    // path: UTF-8 file path
    int open(const char* path);
    void close();

    // decode next frame into internal AVFrame (YUV)
    // return 1: got frame, 0: EOF, <0: error
    int decodeFrame();

    // convert last decoded frame to RGBA into caller buffer
    // bufferSize = width * height * 4
    int toRGBA(uint8_t* outBuffer, int bufferSize);

    void setSeekPosition(int64_t positionMs);
    void seekFrame();
    int getWidth() const { return width; }
    int getHeight() const { return height; }
    double getFramePtsMs() const;   // for later A/V sync

private:
    AVFormatContext* fmtCtx = nullptr;
    AVCodecContext*  codecCtx = nullptr;
    AVStream*        videoStream = nullptr;
    int              videoStreamIndex = -1;
    int64_t time_seek_ms = -1;
    AVFrame* frame = nullptr;       // decoded YUV
    AVPacket* packet = nullptr;

    SwsContext* swsCtx = nullptr;
    int width = 0;
    int height = 0;
};
