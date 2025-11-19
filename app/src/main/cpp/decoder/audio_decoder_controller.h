//
// Created by guoxinggen on 2022/6/6.
//

#ifndef FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H
#define FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H

#include "audio_decoder.h"
#include <pthread.h>
#include <queue>

#define LOG_TAG "AudioDecoderControllerLog"

// So queue size in packets ~= queue time / 40ms.
// 10 * 40ms ≈ 400ms, 4 * 40ms ≈ 160ms.
#define QUEUE_SIZE_MAX_THRESHOLD 10
#define QUEUE_SIZE_MIN_THRESHOLD 4

class AudioDecoderController {

private:
    AudioDecoder *audioDecoder = nullptr;
    bool mutexValid = false;
    pthread_t     audioDecoderThread{};
    std::queue<PcmFrame *> audioFrameQueue;
    bool          isRunning = false;
    pthread_mutex_t mLock{};
    pthread_cond_t  mCondition{};

    int64_t progressMs = 0;

    // Clock fields (all ms) – used by getAudioClockMs()
    int64_t audioClockStartMs    = 0; // media time when current buffer started
    int64_t audioClockUpdateMs   = 0; // monotonic time when we last refilled
    int     lastBufferDurationMs = 0; // duration of current buffer in ms

    int64_t seekTime = -1;
    bool    needSeek = false;

    bool visualizerEnabled = false;  // default: no visualizer

    static void* startDecoderThread(void *ptr);

    void   initDecoderThread();
    int    decodeSongPacket();
    void   destroyDecoderThread();

public:
    int dataSize = 0;

    AudioDecoderController() = default;
    virtual ~AudioDecoderController();
    int      getMusicMeta(const char *audioPath, int *metaArray);
    int      prepare(const char *audioPath);
    void     seek(const long seek_time);
    int64_t  getProgress();
    int64_t  getAudioClockMs() const;
    int      getChannels();
    void     destroy();
    int      readSamples(short *samples, int size);

    // Visualizer on/off
    void     setVisualizerEnabled(bool enabled);
    bool     isVisualizerEnabled() const;
};

#endif //FFMPEGDECODER_MUSIC_DECODER_CORTROLLER_H