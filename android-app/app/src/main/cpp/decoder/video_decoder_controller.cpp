//
// Created by xinggen guo on 2025/11/15.
//

#include "video_decoder_controller.h"
#include "MediaStatus.h"
#include "CommonTools.h"
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

    running = false;
    isFinished = false;
    return MEDIA_STATUS_OK;
}

void VideoDecoderController::destroy() {
    // 1. stop thread
    if (running) {
        running = false;
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

    while (running) {
        // 1) handle pending seek
        if (needSeek) {
            int64_t target = pendingSeekMs.load();

            videoDecoder->setSeekPosition(target);
            videoDecoder->seekFrame();      // av_seek_frame + flush inside
            clearFrameQueue();              // drop old frames in queue

            // reset EOF state after seek
            pthread_mutex_lock(&queueMutex);
            isFinished = false;
            pthread_mutex_unlock(&queueMutex);

            needSeek = false;
            continue;
        }

        // 2) back-pressure + pause handling
        pthread_mutex_lock(&queueMutex);
        // IMPORTANT: add parentheses to avoid precedence bug
        while (running &&
               (frameQueue.size() >= QUEUE_MAX_SIZE || !playing)) {
            // If queue is full OR we are paused → wait
            pthread_cond_wait(&queueCond, &queueMutex);
        }
        pthread_mutex_unlock(&queueMutex);

        if (!running) break;

        // If we got resumed / woken while still paused, skip decoding this round
        if (!playing) {
            continue;
        }

        // 3) decode next frame
        int ret = videoDecoder->decodeFrame();
        if (ret <= 0) {
            // EOF or error
            pthread_mutex_lock(&queueMutex);
            // push EOF marker once
            if (!isFinished) {
                VideoFrame* eofFrame = new VideoFrame();
                memset(eofFrame, 0, sizeof(VideoFrame)); // safe init
                eofFrame->eof = true;
                frameQueue.push(eofFrame);
                pthread_cond_broadcast(&queueCond);
                isFinished = true;
            }
            pthread_mutex_unlock(&queueMutex);
            break;
        }

        // 4) allocate RGBA buffer and convert
        VideoFrame* vf = new VideoFrame();
        vf->width    = width;
        vf->height   = height;
        vf->ptsMs    = videoDecoder->getFramePtsMs(); // already ms
        vf->dataSize = frameSizeBytes;
        vf->eof      = false;
        vf->data     = new uint8_t[frameSizeBytes];

        int convRet = videoDecoder->toRGBA(vf->data, frameSizeBytes);
        if (convRet <= 0) {
            // conversion failed, drop frame
            freeFrame(vf);
            continue;
        }

        // 5) push to queue
        pushFrame(vf);
    }

    // optional: on thread exit, ensure readers see EOF
    pthread_mutex_lock(&queueMutex);
    if (!isFinished) {
        VideoFrame* eofFrame = new VideoFrame();
        memset(eofFrame, 0, sizeof(VideoFrame));
        eofFrame->eof = true;
        frameQueue.push(eofFrame);
        pthread_cond_broadcast(&queueCond);
        isFinished = true;
    }
    running = false;
    pthread_mutex_unlock(&queueMutex);
}

void VideoDecoderController::clearFrameQueue() {
    // Protect queue while clearing
    pthread_mutex_lock(&queueMutex);

    while (!frameQueue.empty()) {
        VideoFrame* frame = frameQueue.front();
        frameQueue.pop();

        if (frame) {
            // You already use this in decodeLoop, so reuse it here
            freeFrame(frame);
        }
    }

    pthread_mutex_unlock(&queueMutex);

    LOGI("VideoDecoderController::clearFrameQueue() - all frames cleared");
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
    if (frameQueue.size() < QUEUE_MIN_SIZE) {
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

int VideoDecoderController::readFrameRGBA(uint8_t* dst, int64_t* ptsMs) {
    if (!dst || !ptsMs) {
        return MEDIA_STATUS_ERROR;
    }

    // Quick check: nothing running and nothing in queue → EOF
    if (!running && isFinished) {
        pthread_mutex_lock(&queueMutex);
        bool empty = frameQueue.empty();
        pthread_mutex_unlock(&queueMutex);
        if (empty) {
            return MEDIA_STATUS_EOF;
        }
    }

    pthread_mutex_lock(&queueMutex);
    if (frameQueue.empty()) {
        // No frame available yet
        if (isFinished) {
            // decoder has finished, and no more frames in queue
            pthread_mutex_unlock(&queueMutex);
            return MEDIA_STATUS_EOF;
        } else {
            // still decoding, just no frame right now
            pthread_mutex_unlock(&queueMutex);
            return MEDIA_STATUS_BUFFERING;
        }
    }
    // Get front frame
    VideoFrame* vf = frameQueue.front();
    frameQueue.pop();

    size_t currentSize = frameQueue.size();
    if (running && currentSize <= QUEUE_MIN_SIZE) {
        pthread_cond_signal(&queueCond);
    }

    pthread_mutex_unlock(&queueMutex);

    if (!vf) {
        return MEDIA_STATUS_ERROR;
    }

    // EOF marker frame (virtual frame to indicate end)
    if (vf->eof) {
        freeFrame(vf);
        return MEDIA_STATUS_EOF;
    }

    // Normal frame: copy RGBA data
    const int frameSizeBytes = width * height * 4;
    if (vf->dataSize < frameSizeBytes) {
        // Data size mismatch → treat as error and drop
        freeFrame(vf);
        return MEDIA_STATUS_ERROR;
    }

    std::memcpy(dst, vf->data, frameSizeBytes);

    if (ptsMs) {
        *ptsMs = vf->ptsMs;
    }

    freeFrame(vf);

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

void VideoDecoderController::seek(int64_t positionMs) {
    LOGI("VideoDecoderController::seek -> %lld ms", (long long)positionMs);

    if (!videoDecoder) {
        LOGE("VideoDecoderController::seek: videoDecoder is null, ignore");
        return;
    }

    // If decode thread already finished (EOF or after stop),
    // we may need to restart it so that 'needSeek' is actually processed.
    if (!running) {
        LOGI("VideoDecoderController::seek: thread not running, restart decode thread");

        clearFrameQueue();        // safe: decode thread not running
        // Reset EOF / queue state
        pthread_mutex_lock(&queueMutex);
        isFinished = false;
        pthread_mutex_unlock(&queueMutex);

        needSeek = false;

        running = true;
        int createRet = pthread_create(
                &decodeThread,
                nullptr,
                &VideoDecoderController::decodeThreadEntry,
                this
        );
        if (createRet != 0) {
            LOGE("VideoDecoderController::seek: pthread_create failed=%d", createRet);
            running = false;
            return;
        }
    }

    // Now thread is alive, tell it to seek
    needSeek = true;
    pendingSeekMs = positionMs;

    // Wake decodeLoop if it is waiting on the queueCond
    pthread_mutex_lock(&queueMutex);
    pthread_cond_broadcast(&queueCond);
    pthread_mutex_unlock(&queueMutex);
}

void VideoDecoderController::play() {
    if (!running) {
        // First time: start decode thread
        running = true;
        playing = true;

        int createRet = pthread_create(
                &decodeThread,
                nullptr,
                &VideoDecoderController::decodeThreadEntry,
                this
        );
        if (createRet != 0) {
            running = false;

            delete videoDecoder;
            videoDecoder = nullptr;
            return;
        }
    } else {
        // Thread already exists → treat this as "play from start"
        needSeek = true;
//        pendingSeekMs = 0;   // seek to 0ms (beginning)
        playing = true;
    }
}

void VideoDecoderController::resume() {
    playing = true;
    pthread_mutex_lock(&queueMutex);
    pthread_cond_broadcast(&queueCond);
    pthread_mutex_unlock(&queueMutex);
}

void VideoDecoderController::pause() {
    playing = false;
    pthread_mutex_lock(&queueMutex);
    pthread_cond_broadcast(&queueCond);
    pthread_mutex_unlock(&queueMutex);
}

void VideoDecoderController::stop() {
    // 1) Stop decode loop
    running = false;
    needSeek = false;

    // Wake up decodeLoop if it's waiting on queueCond
    pthread_mutex_lock(&queueMutex);
    pthread_cond_broadcast(&queueCond);
    pthread_mutex_unlock(&queueMutex);

    // 2) Join decode thread
    //    (assuming decodeThread was created with pthread_create)
    if (decodeThread) {
        pthread_join(decodeThread, nullptr);
        decodeThread = 0;
    }

    // 3) Clear remaining frames in queue
    clearFrameQueue();

    // 4) Reset state flags
    isFinished = false;

    // 5) Destroy underlying decoder
    if (videoDecoder) {
        delete videoDecoder;
        videoDecoder = nullptr;
    }
}
