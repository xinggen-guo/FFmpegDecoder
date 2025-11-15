//
// Created by xinggen guo on 2025/11/15.
//

#include "video_decoder_controller.h"
#include "MediaStatus.h"
#include <cstring>

VideoDecoderController::VideoDecoderController() {
    pthread_mutex_init(&queueMutex, nullptr);
    pthread_cond_init(&queueCond, nullptr);
}

VideoDecoderController::~VideoDecoderController() {
    destroy();
    pthread_mutex_destroy(&queueMutex);
    pthread_cond_destroy(&queueCond);
}

int VideoDecoderController::init(const char* path) {
    destroy();  // clean old if any

    videoDecoder = new VideoDecoder();
    int ret = videoDecoder->open(path);
    if (ret < 0) {
        delete videoDecoder;
        videoDecoder = nullptr;
        return ret;
    }

    width = videoDecoder->getWidth();
    height = videoDecoder->getHeight();

    isRunning = true;
    isFinished = false;

    int createRet = pthread_create(&decodeThread, nullptr, &VideoDecoderController::decodeThreadEntry, this);
    if (createRet != 0) {
        isRunning = false;
        delete videoDecoder;
        videoDecoder = nullptr;
        return -1;
    }

    return 0;
}

void VideoDecoderController::destroy() {
    // 1. stop thread
    if (isRunning) {
        isRunning = false;
        pthread_mutex_lock(&queueMutex);
        pthread_cond_broadcast(&queueCond);
        pthread_mutex_unlock(&queueMutex);

        pthread_join(decodeThread, nullptr);
    }

    // 2. clear queue
    pthread_mutex_lock(&queueMutex);
    while (!frameQueue.empty()) {
        VideoFrame* f = frameQueue.front();
        frameQueue.pop();
        freeFrame(f);
    }
    pthread_mutex_unlock(&queueMutex);

    // 3. close decoder
    if (videoDecoder) {
        videoDecoder->close();
        delete videoDecoder;
        videoDecoder = nullptr;
    }

    isFinished = false;
    width = height = 0;
}

void* VideoDecoderController::decodeThreadEntry(void* arg) {
    auto* self = static_cast<VideoDecoderController*>(arg);
    self->decodeLoop();
    return nullptr;
}

void VideoDecoderController::decodeLoop() {
    if (!videoDecoder) return;

    const int frameSizeBytes = width * height * 4;

    while (isRunning) {

        // back-pressure: wait if queue is full
        pthread_mutex_lock(&queueMutex);
        while (isRunning && frameQueue.size() >= QUEUE_MAX_SIZE) {
            pthread_cond_wait(&queueCond, &queueMutex);
        }
        pthread_mutex_unlock(&queueMutex);

        if (!isRunning) break;

        // 1) decode next frame
        int ret = videoDecoder->decodeFrame();
        if (ret <= 0) {
            // EOF or error
            pthread_mutex_lock(&queueMutex);
            // push EOF marker once
            if (!isFinished) {
                VideoFrame* eofFrame = new VideoFrame();
                eofFrame->eof = true;
                frameQueue.push(eofFrame);
                pthread_cond_broadcast(&queueCond);
            }
            isFinished = true;
            pthread_mutex_unlock(&queueMutex);
            break;
        }

        // 2) allocate RGBA buffer and convert
        VideoFrame* vf = new VideoFrame();
        vf->width = width;
        vf->height = height;
        vf->ptsMs = videoDecoder->getFramePtsMs();
        vf->dataSize = frameSizeBytes;
        vf->data = new uint8_t[frameSizeBytes];

        int convRet = videoDecoder->toRGBA(vf->data, frameSizeBytes);
        if (convRet <= 0) {
            // conversion failed, drop frame
            freeFrame(vf);
            continue;
        }

        // 3) push to queue
        pushFrame(vf);
    }
}

void VideoDecoderController::pushFrame(VideoFrame* frame) {
    pthread_mutex_lock(&queueMutex);
    frameQueue.push(frame);
    pthread_cond_broadcast(&queueCond);
    pthread_mutex_unlock(&queueMutex);
}

VideoFrame* VideoDecoderController::popFrameInternal() {
    if (frameQueue.empty()) return nullptr;
    VideoFrame* f = frameQueue.front();
    frameQueue.pop();
    return f;
}

int VideoDecoderController::getFrame(VideoFrame*& frameOut) {
    frameOut = nullptr;

    pthread_mutex_lock(&queueMutex);

    // no frames yet
    if (frameQueue.empty()) {
        if (isFinished) {
            pthread_mutex_unlock(&queueMutex);
            return MEDIA_STATUS_EOF;  // EOF
        } else {
            pthread_mutex_unlock(&queueMutex);
            return MEDIA_STATUS_BUFFERING; // buffering
        }
    }

    VideoFrame* f = popFrameInternal();

    // wake producer if queue was full
    if (frameQueue.size() < QUEUE_MAX_SIZE) {
        pthread_cond_signal(&queueCond);
    }

    pthread_mutex_unlock(&queueMutex);

    if (f->eof) {
        // EOF marker
        freeFrame(f);
        return MEDIA_STATUS_EOF;
    }

    frameOut = f;
    return MEDIA_STATUS_OK;
}

void VideoDecoderController::freeFrame(VideoFrame* frame) {
    if (!frame) return;
    if (frame->data) {
        delete[] frame->data;
        frame->data = nullptr;
    }
    delete frame;
}