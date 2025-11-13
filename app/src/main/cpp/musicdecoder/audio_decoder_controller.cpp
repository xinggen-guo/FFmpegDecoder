//
// Created by guoxinggen on 2022/6/6.
//

#include <CommonTools.h>
#include "audio_decoder_controller.h"

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
    needSeek = true;
    seekTime = seek_time;

    int getLockCode = pthread_mutex_lock(&mLock);
    pthread_cond_signal(&mCondition);
    pthread_mutex_unlock(&mLock);
}

int AudioDecoderController::getProgress() {
    return progress;
}

int AudioDecoderController::readSapmles(short *samples, int size) {
    LOGI("readSapmles");
    int result = 0;
    if(!audioQueueData.empty() && !needSeek){
        AudioPacket *audioPacket = audioQueueData.front();
        if(audioPacket->audioSize == -1){
            result = -1;
        } else {
            short *dataSamples = audioPacket->audioBuffer;
            memcpy(samples, dataSamples, audioPacket->audioSize * 2);
            progress = audioPacket->startPosition;
            audioQueueData.pop();
            result = audioPacket->audioSize;
        }
        delete audioPacket;
    } else {
        if(isRunning || needSeek){
            result = -2;
        } else{
            result = -3;
        }
    }
    if(audioQueueData.size() < QUEUE_SIZE_MIN_THRESHOLD && isRunning){
        int getLockCode = pthread_mutex_lock(&mLock);
        if (result != -1) {
            pthread_cond_signal(&mCondition);
        }
        LOGI("pthread_mutex_unlock----->222222");
        pthread_mutex_unlock (&mLock);
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

void* AudioDecoderController::startDecoderThread(void *ptr) {
    AudioDecoderController* decoderController =
            (AudioDecoderController *) ptr;
    int getLockCode = pthread_mutex_lock(&decoderController->mLock);
    while (decoderController->isRunning) {
        if (decoderController->needSeek) {
            if (decoderController->seekTime > 0) {
                int dataSize = decoderController->audioQueueData.size();
                if (dataSize > 0) {
                    for (int i = 0; i < dataSize; i++) {
                        AudioPacket *audioPacket = decoderController->audioQueueData.front();
                        decoderController->audioQueueData.pop();
                        delete audioPacket;
                    }
                }
                decoderController->audioDecoder->seek(decoderController->seekTime);
                decoderController->seekTime = 0;
            }
        }
        int result = decoderController->decodeSongPacket();
        if (result == -1) {
            break;
        }
        if (decoderController->needSeek) {
            decoderController->needSeek = false;
        }
        if (decoderController->audioQueueData.size() >= QUEUE_SIZE_MAX_THRESHOLD) {
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
    AudioPacket* audioPacket = audioDecoder->decoderAudioPacket();
    if(audioPacket->audioSize == -1){
        return audioPacket->audioSize;
    } else {
        audioQueueData.push(audioPacket);
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

