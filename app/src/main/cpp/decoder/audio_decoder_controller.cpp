//
// Created by guoxinggen on 2022/6/6.
//

#include <CommonTools.h>
#include "audio_decoder_controller.h"
#include "audio_visualizer.h"
#include "MediaStatus.h"

AudioDecoderController::~AudioDecoderController() {
    destroy();
}


int AudioDecoderController::getMusicMeta(const char *audioPath, int *metaArray) {
    int result = 0;
    audioDecoder = new AudioDecoder();
    result = audioDecoder->initAudioDecoder(audioPath);
    if (result == 0) {
        metaArray[0] = audioDecoder->getSampleRate();
        metaArray[1] = audioDecoder->getPacketBufferSize(); // samples per packet
        metaArray[2] = (int)audioDecoder->getDuration();    // ms
    }
    audioDecoder->destroy();
    delete audioDecoder;
    audioDecoder = nullptr;
    return result;
}

int AudioDecoderController::prepare(const char *audioPath) {
    LOGI("prepare");
    int result = 0;

    if (audioDecoder != nullptr) {
        audioDecoder->destroy();
        delete audioDecoder;
        audioDecoder = nullptr;
    }

    audioDecoder = new AudioDecoder();
    result = audioDecoder->initAudioDecoder(audioPath);
    if (result != 0) {
        delete audioDecoder;
        audioDecoder = nullptr;
        return result;
    }

    audioDecoder->prepare();

    // Reset timeline & clock
    progressMs            = 0;
    audioClockStartMs     = 0;
    audioClockUpdateMs    = 0;
    lastBufferDurationMs  = 0;
    needSeek              = false;
    seekTime              = -1;

    initDecoderThread();
    return result;
}

void AudioDecoderController::seek(const long seek_time) {
    // If decoder thread was already torn down, just ignore the seek
    if (!mutexValid) {
        LOGE("AudioDecoderController::seek called after destroy, ignore");
        return;
    }

    if (!isRunning) {
        LOGI("seek: decoder thread is not running, restart it");
        initDecoderThread();   // re-create mutex/cond + thread
    }

    pthread_mutex_lock(&mLock);

    needSeek = true;
    seekTime = seek_time;

    // Immediately update “public” time so UI gets value
    progressMs = seekTime;

    pthread_cond_signal(&mCondition);
    pthread_mutex_unlock(&mLock);
}

int64_t AudioDecoderController::getProgress() {
    return progressMs;
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
    PcmFrame *audioPacket = nullptr;

    // ---- 1) Take one packet from queue under lock ----
    pthread_mutex_lock(&mLock);

    bool localNeedSeek = needSeek;
    if (!audioFrameQueue.empty() && !localNeedSeek) {
        audioPacket = audioFrameQueue.front();
        audioFrameQueue.pop();

        // If queue is going low, wake decoder thread (to refill)
        if (audioFrameQueue.size() < QUEUE_SIZE_MIN_THRESHOLD && isRunning) {
            pthread_cond_signal(&mCondition);
        }
    } else {
        // No packet to consume
        if (isRunning || needSeek) {
            result = MEDIA_STATUS_BUFFERING;
        } else {
            // decoder finished and queue empty
            result = MEDIA_STATUS_EOF;
        }
    }

    pthread_mutex_unlock(&mLock);
    // ---- end of queue critical section ----

    if (!audioPacket) {
        // No audioPacket pulled -> return status decided above
        return result;
    }

    // ---- 2) Use the packet outside the lock ----
    if (audioPacket->audioSize == -1) {
        // EOF / error
        result = MEDIA_STATUS_ERROR;
        delete audioPacket;
        return result;
    }

    // audioSize is number of samples (shorts)
    const int packetSamples = audioPacket->audioSize;
    if (size < packetSamples) {
        // Safety check: caller buffer too small
        LOGE("readSamples: caller buffer too small. size=%d packetSamples=%d",
             size, packetSamples);
    }

    short *dataSamples = audioPacket->audioBuffer;
    memcpy(samples, dataSamples, packetSamples * sizeof(short));

    // Base PTS in ms: startPosition is seconds (float)
    int64_t baseMs = (int64_t)(audioPacket->startPosition * 1000.0f + 0.5f);

    // Duration of this packet in ms
    int channels   = audioDecoder->getChannels();
    int sampleRate = audioDecoder->getSampleRate();
    int samplesPerChannel = (channels > 0)
                            ? (packetSamples / channels)
                            : packetSamples;

    int packetMs = 0;
    if (sampleRate > 0) {
        packetMs = (int)((int64_t)samplesPerChannel * 1000 / sampleRate);
    }

    // Update global "progress" (UI timeline)
    progressMs = baseMs;

    // Update clock state used by getAudioClockMs()
    audioClockStartMs     = baseMs;
    audioClockUpdateMs    = nowMonotonicMs();  // monotonic time now
    lastBufferDurationMs  = packetMs;

    if (visualizerEnabled) {
        AudioVisualizer::instance().onPcmData(
                dataSamples,
                packetSamples,
                audioDecoder->getSampleRate());
    }

    delete audioPacket;
    result = packetSamples;
    return result;
}

void AudioDecoderController::destroy() {
    LOGI("destroy");

    // If already cleaned up, do nothing
    if (!mutexValid && !isRunning && audioDecoder == nullptr && audioFrameQueue.empty()) {
        return;
    }

    // 1) Ask thread to stop
    if (isRunning) {
        isRunning = false;

        if (mutexValid) {
            pthread_mutex_lock(&mLock);
            pthread_cond_signal(&mCondition);
            pthread_mutex_unlock(&mLock);
        }

        // 2) Join the decoder thread to ensure it's fully exited
        if (audioDecoderThread) {
            pthread_join(audioDecoderThread, nullptr);
            audioDecoderThread = 0;
        }
    }

    // 3) Clean remaining packets in queue
    if (mutexValid) {
        pthread_mutex_lock(&mLock);
    }
    while (!audioFrameQueue.empty()) {
        PcmFrame* packet = audioFrameQueue.front();
        audioFrameQueue.pop();
        delete packet;
    }
    if (mutexValid) {
        pthread_mutex_unlock(&mLock);
    }

    // 4) Destroy underlying decoder
    if (audioDecoder != nullptr) {
        audioDecoder->destroy();
        delete audioDecoder;
        audioDecoder = nullptr;
    }

    // 5) Now it's safe to destroy mutex & cond
    if (mutexValid) {
        pthread_mutex_destroy(&mLock);
        pthread_cond_destroy(&mCondition);
        mutexValid = false;
    }

    LOGI("destroy -- done");
}

void AudioDecoderController::initDecoderThread() {
    LOGI("initDecoderThread--start");
    isRunning = true;
    if (!mutexValid) {
        pthread_mutex_init(&mLock, NULL);
        pthread_cond_init(&mCondition, NULL);
        mutexValid = true;
    }
    pthread_create(&audioDecoderThread, nullptr, startDecoderThread, this);
}

void* AudioDecoderController::startDecoderThread(void *ptr) {
    auto *decoderController = (AudioDecoderController *) ptr;

    while (decoderController->isRunning) {

        long localSeekTime = -1;

        pthread_mutex_lock(&decoderController->mLock);
        if (decoderController->needSeek && decoderController->seekTime >= 0) {
            localSeekTime = decoderController->seekTime;

            while (!decoderController->audioFrameQueue.empty()) {
                PcmFrame *audioPacket = decoderController->audioFrameQueue.front();
                decoderController->audioFrameQueue.pop();
                delete audioPacket;
            }

            decoderController->seekTime = -1;
            decoderController->needSeek = false;
        }
        pthread_mutex_unlock(&decoderController->mLock);

        if (localSeekTime >= 0) {
            // Perform seek outside the lock (can be slow)
            decoderController->audioDecoder->seek(localSeekTime);
            continue; // then continue decoding
        }

        pthread_mutex_lock(&decoderController->mLock);
        while (decoderController->isRunning &&
               decoderController->audioFrameQueue.size() >= QUEUE_SIZE_MAX_THRESHOLD &&
               !decoderController->needSeek) {
            pthread_cond_wait(&decoderController->mCondition,
                              &decoderController->mLock);
        }
        bool stillRunning = decoderController->isRunning;
        pthread_mutex_unlock(&decoderController->mLock);

        if (!stillRunning) {
            break;
        }

        int result = decoderController->decodeSongPacket();
        if (result == -1) {
            // EOF or error
            break;
        }
    }

    LOGI("startDecoderThread----->exit loop");
    decoderController->isRunning = false;
    pthread_exit(nullptr);
}

int64_t AudioDecoderController::getAudioClockMs() const {
    // If we never played anything yet, or no valid packet duration
    if (lastBufferDurationMs <= 0) {
        return progressMs; // fallback to known file position
    }

    int64_t now   = nowMonotonicMs();     // CLOCK_MONOTONIC
    int64_t delta = now - audioClockUpdateMs;

    if (delta < 0) delta = 0;
    if (delta > lastBufferDurationMs) delta = lastBufferDurationMs;

    return audioClockStartMs + delta;
}

int AudioDecoderController::getChannels() {
    return audioDecoder ? audioDecoder->getChannels() : 0;
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

        // Push into queue under lock
        if (mutexValid) {
            pthread_mutex_lock(&mLock);
            audioFrameQueue.push(audioPacket);
            pthread_mutex_unlock(&mLock);
        } else {
            // Fallback, should not really happen if properly initialized
            audioFrameQueue.push(audioPacket);
        }
        return 1;
    }
}

void AudioDecoderController::destroyDecoderThread() {
    LOGI("destroyDecoderThread");
    isRunning = false;
}