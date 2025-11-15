//
// Created by guoxinggen on 2022/6/6.
//

#include <CommonTools.h>
#include "audio_decoder_controller.h"
#include "audio_visualizer.h"
#include "MediaStatus.h"

int AudioDecoderController::getMusicMeta(const char *audioPath, int *metaArray) {
    int result = 0;
    audioDecoder = new AudioDecoder();
    result = audioDecoder->initAudioDecoder(audioPath);
    metaArray[0] = audioDecoder->getSampleRate();
    metaArray[1] = audioDecoder->getPacketBufferSize();
    metaArray[2] = audioDecoder->getDuration();
    audioDecoder->destroy();
    return result;
}

int AudioDecoderController::prepare(const char *audioPath) {
    LOGI("prepare");
    int result = 0;
    audioDecoder = new AudioDecoder();
    result = audioDecoder->initAudioDecoder(audioPath);
    audioDecoder->prepare();
    initDecoderThread();
    return result;
}

void AudioDecoderController::seek(const long seek_time) {

    pthread_mutex_lock(&mLock);

    needSeek = true;
    seekTime = seek_time;

    //Immediately update “public” time so UI gets the new value
    progress = seekTime;

    // wake decode thread to apply the real seek
    pthread_cond_signal(&mCondition);
    pthread_mutex_unlock(&mLock);
}

int64_t AudioDecoderController::getProgress() {
    return progress;
}

void AudioDecoderController::setVisualizerEnabled(bool enabled) {
    visualizerEnabled = enabled;
}

bool AudioDecoderController::isVisualizerEnabled() const {
    return visualizerEnabled;
}

int AudioDecoderController::readSamples(short *samples, int size) {
    LOGI("readSamples");
    int result = 0;
    if (!audioFrameQueue.empty() && !needSeek) {
        PcmFrame *audioPacket = audioFrameQueue.front();
        if (audioPacket->audioSize == -1) {
            result = MEDIA_STATUS_ERROR;
        } else {
            short *dataSamples = audioPacket->audioBuffer;
            memcpy(samples, dataSamples, audioPacket->audioSize * 2);
            if (audioDecoder->getSampleRate() > 0) {
                int packetMs = (audioPacket->audioSize / audioDecoder->getChannels()) * 1000 / audioDecoder->getSampleRate();
                progress += packetMs;
            }
            audioFrameQueue.pop();
            result = audioPacket->audioSize;

            if (visualizerEnabled) {
                AudioVisualizer::instance().onPcmData(
                        dataSamples,
                        audioPacket->audioSize,
                        audioDecoder->getSampleRate());
            }
        }
        delete audioPacket;
    } else {
        if (isRunning || needSeek) {
            result = MEDIA_STATUS_BUFFERING;
        } else {
            result = -3;
        }
    }
    if (audioFrameQueue.size() < QUEUE_SIZE_MIN_THRESHOLD && isRunning) {
        int getLockCode = pthread_mutex_lock(&mLock);
        if (result != -1) {
            pthread_cond_signal(&mCondition);
        }
        pthread_mutex_unlock(&mLock);
    }
    return result;
}

void AudioDecoderController::destroy() {
    LOGI("destroy");
    if (audioDecoder != nullptr) {
        audioDecoder->destroy();
        delete audioDecoder;
        audioDecoder = nullptr;
    }
    isRunning = false;
}


void AudioDecoderController::initDecoderThread() {
    LOGI("initDecoderThread");
    isRunning = true;
    pthread_mutex_init(&mLock, NULL);
    pthread_cond_init(&mCondition, NULL);
    pthread_create(&audioDecoderThread, NULL, startDecoderThread, this);
}

void *AudioDecoderController::startDecoderThread(void *ptr) {
    AudioDecoderController *decoderController =
            (AudioDecoderController *) ptr;
    int getLockCode = pthread_mutex_lock(&decoderController->mLock);
    while (decoderController->isRunning) {
        if (decoderController->needSeek) {
            if (decoderController->seekTime >= 0) {
                int dataSize = decoderController->audioFrameQueue.size();
                if (dataSize > 0) {
                    for (int i = 0; i < dataSize; i++) {
                        PcmFrame *audioPacket = decoderController->audioFrameQueue.front();
                        decoderController->audioFrameQueue.pop();
                        delete audioPacket;
                    }
                }
                decoderController->audioDecoder->seek(decoderController->seekTime);
                decoderController->seekTime = -1;
            }
        }
        int result = decoderController->decodeSongPacket();
        if (result == -1) {
            break;
        }
        if (decoderController->needSeek) {
            decoderController->needSeek = false;
        }
        if (decoderController->audioFrameQueue.size() >= QUEUE_SIZE_MAX_THRESHOLD) {
            pthread_cond_wait(&decoderController->mCondition, &decoderController->mLock);
        }
    }
    LOGI("startDecoderThread----->11111");
    decoderController->isRunning = false;
    pthread_mutex_unlock(&decoderController->mLock);
    decoderController->destroyDecoderThread();
    pthread_exit(0);
}

int AudioDecoderController::decodeSongPacket() {
    PcmFrame *audioPacket = audioDecoder->decoderAudioPacket();
    if (audioPacket->audioSize == -1) {
        int ret = audioPacket->audioSize;
        delete audioPacket;
        return ret;
    } else {
        if (isVisualizerEnabled()) {
            AudioVisualizer::instance().onPcmData(
                    audioPacket->audioBuffer,
                    audioPacket->audioSize,
                    audioDecoder->getSampleRate());
        }
        audioFrameQueue.push(audioPacket);
        return 1;
    }
}

void AudioDecoderController::destroyDecoderThread() {
    LOGI("destroyDecoderThread");
    isRunning = false;
    int getLockCode = pthread_mutex_lock(&mLock);
    pthread_cond_signal(&mCondition);
    pthread_mutex_unlock(&mLock);
    pthread_mutex_destroy(&mLock);
    pthread_cond_destroy(&mCondition);
}

