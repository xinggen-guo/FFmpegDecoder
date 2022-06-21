//
// Created by guoxinggen on 2022/6/6.
//

#ifndef FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H
#define FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H


#define QUEUE_SIZE_MAX_THRESHOLD 30
#define QUEUE_SIZE_MIN_THRESHOLD 25

#include "audio_decoder.h"
#include <pthread.h>
#include <queue>

#define LOG_TAG "AudioDecoderController"

class AudioDecoderController{
    /** 伴奏的解码器 **/
    private:
        AudioDecoder* audioDecoder;
        pthread_t audioDecoderThread;
        std::queue<AudioPacket*> audioQueueData;
        bool isRunning;
        pthread_mutex_t mLock;
        pthread_cond_t mCondition;

        static void* startDecoderThread(void* ptr);
        /** 开启解码线程 **/
        virtual void initDecoderThread();
        void decodeSongPacket();
        /** 销毁解码线程 **/
        virtual void destroyDecoderThread();

    public:
        int dataSize;
        int getMusicMeta(const char *audioPath, int *metaArray);
        int prepare(const char *audioPath);

    void destroy();

    int readSapmles(short *pInt, int i);

};


#endif //FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H