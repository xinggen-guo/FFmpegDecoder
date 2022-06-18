//
// Created by guoxinggen on 2022/6/6.
//

#include <CommonTools.h>
#include "audio_decoder_controller.h"

int AudioDecoderController::init(const char *audioPath, int *metaArray) {
    int result = 0;
    audioDecoder = new AudioDecoder();
    result = audioDecoder->initAudioDecoder(audioPath,metaArray);
    LOGI("init---->result:%1d", result);
    return result;
}

int AudioDecoderController::prepare() {
    LOGI("prepare");
    int result = 0;
    audioDecoder->prepare();
    initDecoderThread();
    return result;
}

int AudioDecoderController::readSapmles(short *samples, int size) {
    LOGI("readSapmles----->size:%d",size);
    int result = 0;
    if(audioQueueData.size() > 0){
        AudioPacket *audioPacket = audioQueueData.front();
        LOGI("audioPacket----->audioPacketSize:%d",audioPacket->audioSize);
        short *dataSamples = audioPacket->audioBuffer;
        memcpy(samples, dataSamples, audioPacket->audioSize * 2);
        audioQueueData.pop();
        result = audioPacket->audioSize;
        delete audioPacket;
    } else{
        result = -2;
    }
    if(audioQueueData.size() < QUEUE_SIZE_MIN_THRESHOLD){
        int getLockCode = pthread_mutex_lock(&mLock);
        LOGI("readSapmles----->result:%1d", result);
        if (result != -1) {
            pthread_cond_signal(&mCondition);
        }
        pthread_mutex_unlock (&mLock);
    }
    LOGI("readSapmles----->result:%d",result);
    return result;
}

void AudioDecoderController::destroy() {
    if(NULL != audioDecoder) {
        audioDecoder->destroy();
        delete audioDecoder;
        audioDecoder = NULL;
    }
    delete (&audioQueueData);
    destroyDecoderThread();
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
        LOGI("startDecoderThread----->start");
        decoderController->decodeSongPacket();
        if (decoderController->audioQueueData.size() >= QUEUE_SIZE_MAX_THRESHOLD) {
            LOGI("startDecoderThread----->wait");
            pthread_cond_wait(&decoderController->mCondition, &decoderController->mLock);
        }
        LOGI("startDecoderThread----->end");
    }
    pthread_mutex_unlock(&decoderController->mLock);
}

void AudioDecoderController::decodeSongPacket() {
    AudioPacket* audioPacket = audioDecoder->decoderAudioPacket();
    audioQueueData.push(audioPacket);
}

void AudioDecoderController::destroyDecoderThread() {
    isRunning = false;
    int getLockCode = pthread_mutex_lock(&mLock);
    pthread_cond_signal(&mCondition);
    pthread_mutex_unlock(&mLock);
    pthread_mutex_destroy(&mLock);
    pthread_cond_destroy(&mCondition);
}

