//
// Created by xinggen guo on 2025/11/15.
//

#pragma once

#include <queue>
#include <pthread.h>
#include "video_decoder.h"   // your FFmpeg-based VideoDecoder
#include "video_frame.h"

class VideoDecoderController {
public:
    VideoDecoderController();
    ~VideoDecoderController();

    // Open file and start decode thread
    int init(const char* path);

    // Stop decode thread and release everything
    void destroy();

    // Consumer API: pop one frame from queue
    // return:
    //   >0 : got frame, frameOut is valid
    //   0  : EOF, no more frames (frameOut=nullptr)
    //  -2  : no frame available yet (buffering)
    int getFrame(VideoFrame*& frameOut);

    // After rendering, caller must free frame buffer
    static void freeFrame(VideoFrame* frame);

    // For later A/V sync
    int getWidth() const { return width; }
    int getHeight() const { return height; }

private:
    static void* decodeThreadEntry(void* arg);
    void decodeLoop();

    void pushFrame(VideoFrame* frame);
    VideoFrame* popFrameInternal();

private:
    VideoDecoder* videoDecoder = nullptr;

    pthread_t decodeThread{};
    bool isRunning = false;
    bool isFinished = false;

    // Producer-consumer queue
    std::queue<VideoFrame*> frameQueue;
    pthread_mutex_t queueMutex{};
    pthread_cond_t  queueCond{};

    int width = 0;
    int height = 0;

    // queue thresholds
    static const int QUEUE_MAX_SIZE = 30;   // max buffered frames
    static const int QUEUE_MIN_SIZE = 5;    // wake producer when low
};
