//
// Created by guoxinggen on 2022/6/6.
//

#ifndef FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H
#define FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H


#define QUEUE_SIZE_MAX_THRESHOLD 25
#define QUEUE_SIZE_MIN_THRESHOLD 20

#include "audio_decoder.h"
#include <pthread.h>
#include <queue>

class AudioDecoderController{
    /** 伴奏的解码器 **/
    private:
        AudioDecoder* audioDecoder;
        pthread_t audioDecoderThread;

        std::queue<short *> audioQueueData;

        bool isRunning;
        pthread_mutex_t mLock;
        pthread_cond_t mCondition;

        static void* startDecoderThread(void* ptr);
        /** 开启解码线程 **/
        virtual void initDecoderThread();
        virtual int decodeSongPacket();
        /** 销毁解码线程 **/
        virtual void destroyDecoderThread();

    public:
        int dataSize;
        int init(const char *audioPath, int *metaArray);
        int prepare();

    void destroy();

    int readSapmles(short *pInt, int i);
};


#endif //FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H