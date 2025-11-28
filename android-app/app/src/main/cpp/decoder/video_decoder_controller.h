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


    /**
    * Consumer side: read one decoded frame as RGBA.
    *
    * Called by JNI (FfmpegVideoEngine.nativeReadFrame).
    *
    * @param dst    output RGBA buffer, size >= width * height * 4
    * @param ptsMs  [out] presentation timestamp in milliseconds
    *
    * @return MediaStatus_OK / MediaStatus_EOF / MediaStatus_BUFFERING / MediaStatus_ERROR
    */
    int readFrameRGBA(uint8_t* dst, int64_t* ptsMs);

    // After rendering, caller must free frame buffer
    static void freeFrame(VideoFrame* frame);

    void seek(int64_t positionMs);

    // For later A/V sync
    int getWidth() const { return width; }
    int getHeight() const { return height; }

    // Play from start or current seek position
    void play();

    // Resume from pause (do NOT seek or recreate thread)
    void resume();

    // Pause decode (keep thread alive)
    void pause();

    /**
     * Stop decode thread, clear resources (but does not delete the controller itself).
     */
    void stop();

private:
    static void* decodeThreadEntry(void* arg);
    void decodeLoop();

    void pushFrame(VideoFrame* frame);
    VideoFrame* popFrameInternal();
    void clearFrameQueue();
private:
    VideoDecoder* videoDecoder = nullptr;

    pthread_t decodeThread{};
    bool isFinished = false;

    // Producer-consumer queue
    std::queue<VideoFrame*> frameQueue;
    pthread_mutex_t queueMutex{};
    pthread_cond_t  queueCond{};

    std::atomic<bool>  needSeek{false};
    std::atomic<int64_t> pendingSeekMs{0};

    std::atomic<bool> running{false};   // decode thread exists
    std::atomic<bool> playing{false};   // currently playing (not paused)

    int width = 0;
    int height = 0;

    // queue thresholds
    static const int QUEUE_MAX_SIZE = 30;   // max buffered frames
    static const int QUEUE_MIN_SIZE = 5;    // wake producer when low
};
