//
// Created by xinggen guo on 2025/11/20.
//

#pragma once

#include <SLES/OpenSLES.h>
#include <jni.h>
#include <SLES/OpenSLES_Android.h>
#include <cstring>
#include <atomic>
#include <vector>
#include <mutex>
#include <deque>

// Mic → Speaker loopback using OpenSL ES, with PCM callback to Kotlin.
class LiveAudioEngineImpl {
public:
    LiveAudioEngineImpl(int sampleRate, int channels, int bufferMs,
                        JNIEnv* env, jobject jEngineObj);
    ~LiveAudioEngineImpl();

    void startLoopback();
    void stopLoopback();

    /** BGM PCM input from FFmpeg PCM path */
    void pushBgmPcm(const short* buffer, int samples);

    void pushMixedPcm(const short* buffer, int samples);
private:
    void initOpenSL();
    void createOutputMix();
    void createRecorder();
    void createPlayer();
    void destroyAll();

    // callbacks from OpenSL
    static void RecorderCallback(SLAndroidSimpleBufferQueueItf bq, void* context);
    static void PlayerCallback(SLAndroidSimpleBufferQueueItf bq, void* context);

    void handleRecorderCallback();
    void handlePlayerCallback();

    // Java callback
    void dispatchPcmToJava(short* buffer, int samples);

private:
    // OpenSL engine
    SLObjectItf engineObj_ = nullptr;
    SLEngineItf engineItf_ = nullptr;

    SLObjectItf outputMixObj_ = nullptr;

    // Recorder (mic)
    SLObjectItf recorderObj_ = nullptr;
    SLRecordItf recorderItf_ = nullptr;
    SLAndroidSimpleBufferQueueItf recorderQueueItf_ = nullptr;

    // Player (speaker)
    SLObjectItf playerObj_ = nullptr;
    SLPlayItf playerItf_ = nullptr;
    SLAndroidSimpleBufferQueueItf playerQueueItf_ = nullptr;

    // Buffers (ring)
    static constexpr int kBufferCount = 4;
    std::vector<short> buffers_[kBufferCount];
    int currentRecordBufferIndex_ = 0;

    // Config
    int sampleRate_ = 44100;
    int channels_   = 1;
    int bufferMs_   = 20;
    int bufferSamples_ = 0; // per buffer, per channel

    // Java
    JavaVM* jvm_ = nullptr;
    jobject jEngineObjGlobal_ = nullptr; // OpenSlLiveAudioEngine (global ref)

    // State
    std::atomic<bool> running_{false};

    // NEW: BGM ring buffer ----------
    std::vector<short> bgmBuffer_;
    size_t bgmWritePos_ = 0;
    size_t bgmReadPos_  = 0;
    std::mutex bgmMutex_;

    //  player buffer management for mixed PCM
    std::mutex playerMutex_;
    std::deque<short*> playerBuffers_;  // pointers to delete in callback
};
