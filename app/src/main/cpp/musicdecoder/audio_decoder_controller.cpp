//
// Created by guoxinggen on 2022/6/6.
//

#include "audio_decoder_controller.h"

int AudioDecoderController::init(const char *audioPath, int *metaArray) {
    int result = 0;
    audioDecoder = new AudioDecoder();
    result = audioDecoder->initAudioDecoder(audioPath,metaArray);
    dataSize = metaArray[2];
    return result;
}

int AudioDecoderController::prepare() {
    int result = 0;
    audioDecoder->prepare();
    initDecoderThread();
    return result;
}

int AudioDecoderController::readSapmles(short *samples, int size) {
    int result = 0;
    if(audioQueueData.size() > 0){
        short *dataSamples = audioQueueData.front();
       memcpy(samples, dataSamples, size);
       audioQueueData.pop();
    }

    if(audioQueueData.size() < QUEUE_SIZE_MIN_THRESHOLD){
        int getLockCode = pthread_mutex_lock(&mLock);
        if (result != -1) {
            pthread_cond_signal(&mCondition);
        }
        pthread_mutex_unlock (&mLock);
    }
    return 0;
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
        int result = decoderController->decodeSongPacket();
        if (decoderController->audioQueueData.size() >= QUEUE_SIZE_MAX_THRESHOLD || result == -1) {
            pthread_cond_wait(&decoderController->mCondition, &decoderController->mLock);
        }
    }
    pthread_mutex_unlock(&decoderController->mLock);
}

int AudioDecoderController::decodeSongPacket() {
    short *decodeData = new short[dataSize];
    int result = audioDecoder->audioDecoder(decodeData, dataSize);
    if(result == 0) {
        audioQueueData.push(decodeData);
    }
    return result;
}

void AudioDecoderController::destroyDecoderThread() {
    isRunning = false;
    int getLockCode = pthread_mutex_lock(&mLock);
    pthread_cond_signal(&mCondition);
    pthread_mutex_unlock(&mLock);
    pthread_mutex_destroy(&mLock);
    pthread_cond_destroy(&mCondition);
}

