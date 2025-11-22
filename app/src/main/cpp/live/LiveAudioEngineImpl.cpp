//
// Created by xinggen guo on 2025/11/20.
//

#include "LiveAudioEngineImpl.h"
#include "CommonTools.h"

LiveAudioEngineImpl::LiveAudioEngineImpl(
        int sampleRate,
        int channels,
        int bufferMs,
        JNIEnv* env,
        jobject jEngineObj
) : sampleRate_(sampleRate),
    channels_(channels),
    bufferMs_(bufferMs) {

    if (bufferMs_ <= 0) bufferMs_ = 20;
    if (sampleRate_ <= 0) sampleRate_ = 44100;
    if (channels_ != 1 && channels_ != 2) channels_ = 1;

    bufferSamples_ = (sampleRate_ * bufferMs_) / 1000;
    if (bufferSamples_ <= 0) {
        bufferSamples_ = sampleRate_ / 50; // ~20ms default
    }

    // PCM is interleaved: total samples = bufferSamples_ * channels
    int totalSamples = bufferSamples_ * channels_;

    // Allocate buffers
    for (int i = 0; i < kBufferCount; ++i) {
        buffers_[i].resize(totalSamples);
        std::memset(buffers_[i].data(), 0, totalSamples * sizeof(short));
    }

    // Java VM + global ref
    env->GetJavaVM(&jvm_);
    jEngineObjGlobal_ = env->NewGlobalRef(jEngineObj);

    // Init OpenSL objects
    initOpenSL();
    createOutputMix();
    createRecorder();
    createPlayer();
}

LiveAudioEngineImpl::~LiveAudioEngineImpl() {
    destroyAll();
}

// Create engine object
void LiveAudioEngineImpl::initOpenSL() {
    SLresult res;

    res = slCreateEngine(&engineObj_, 0, nullptr, 0, nullptr, nullptr);
    if (res != SL_RESULT_SUCCESS) {
        engineObj_ = nullptr;
        return;
    }

    res = (*engineObj_)->Realize(engineObj_, SL_BOOLEAN_FALSE);
    if (res != SL_RESULT_SUCCESS) {
        engineObj_ = nullptr;
        return;
    }

    res = (*engineObj_)->GetInterface(engineObj_, SL_IID_ENGINE, &engineItf_);
    if (res != SL_RESULT_SUCCESS) {
        engineItf_ = nullptr;
        return;
    }
}

// Output mix for player
void LiveAudioEngineImpl::createOutputMix() {
    if (!engineItf_) return;
    SLresult res;

    const SLInterfaceID ids[] = {};
    const SLboolean req[] = {};
    res = (*engineItf_)->CreateOutputMix(
            engineItf_, &outputMixObj_, 0, ids, req
    );
    if (res != SL_RESULT_SUCCESS) {
        outputMixObj_ = nullptr;
        return;
    }
    res = (*outputMixObj_)->Realize(outputMixObj_, SL_BOOLEAN_FALSE);
    if (res != SL_RESULT_SUCCESS) {
        outputMixObj_ = nullptr;
    }
}

// Recorder: mic → buffer queue
void LiveAudioEngineImpl::createRecorder() {
    if (!engineItf_) return;
    SLresult res;

    // Source: default audio input device
    SLDataLocator_IODevice loc_dev = {
            SL_DATALOCATOR_IODEVICE,
            SL_IODEVICE_AUDIOINPUT,
            SL_DEFAULTDEVICEID_AUDIOINPUT,
            nullptr
    };
    SLDataSource audioSrc = { &loc_dev, nullptr };

    // Sink: Android simple buffer queue
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            static_cast<SLuint32>(kBufferCount)
    };

    // PCM format
    SLuint32 slSampleRate = static_cast<SLuint32>(sampleRate_) * 1000;
    SLuint32 channelMask = (channels_ == 1)
                           ? SL_SPEAKER_FRONT_CENTER
                           : (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT);

    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM,
            static_cast<SLuint32>(channels_),
            slSampleRate,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            channelMask,
            SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSink audioSnk = { &loc_bq, &format_pcm };

    const SLInterfaceID ids[] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE };
    const SLboolean req[] = { SL_BOOLEAN_TRUE };

    res = (*engineItf_)->CreateAudioRecorder(
            engineItf_,
            &recorderObj_,
            &audioSrc,
            &audioSnk,
            1,
            ids,
            req
    );
    if (res != SL_RESULT_SUCCESS) {
        recorderObj_ = nullptr;
        return;
    }

    res = (*recorderObj_)->Realize(recorderObj_, SL_BOOLEAN_FALSE);
    if (res != SL_RESULT_SUCCESS) {
        recorderObj_ = nullptr;
        return;
    }

    res = (*recorderObj_)->GetInterface(
            recorderObj_, SL_IID_RECORD, &recorderItf_
    );
    if (res != SL_RESULT_SUCCESS) {
        recorderItf_ = nullptr;
        return;
    }

    res = (*recorderObj_)->GetInterface(
            recorderObj_, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &recorderQueueItf_
    );
    if (res != SL_RESULT_SUCCESS) {
        recorderQueueItf_ = nullptr;
        return;
    }

    // Register callback
    res = (*recorderQueueItf_)->RegisterCallback(
            recorderQueueItf_, RecorderCallback, this
    );
    if (res != SL_RESULT_SUCCESS) {
        recorderQueueItf_ = nullptr;
        return;
    }

    // Enqueue all buffers initially
    const SLuint32 bufferSizeBytes =
            static_cast<SLuint32>(buffers_[0].size() * sizeof(short));

    for (int i = 0; i < kBufferCount; ++i) {
        (*recorderQueueItf_)->Enqueue(
                recorderQueueItf_,
                buffers_[i].data(),
                bufferSizeBytes
        );
    }
    currentRecordBufferIndex_ = 0;
}

// Player: buffer queue → output mix
void LiveAudioEngineImpl::createPlayer() {
    if (!engineItf_ || !outputMixObj_) return;
    SLresult res;

    // Source: simple buffer queue
    SLDataLocator_AndroidSimpleBufferQueue loc_bq = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            static_cast<SLuint32>(kBufferCount)
    };

    SLuint32 slSampleRate = static_cast<SLuint32>(sampleRate_) * 1000;
    SLuint32 channelMask = (channels_ == 1)
                           ? SL_SPEAKER_FRONT_CENTER
                           : (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT);

    SLDataFormat_PCM format_pcm = {
            SL_DATAFORMAT_PCM,
            static_cast<SLuint32>(channels_),
            slSampleRate,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            channelMask,
            SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource audioSrc = { &loc_bq, &format_pcm };

    // Sink: output mix
    SLDataLocator_OutputMix loc_outmix = {
            SL_DATALOCATOR_OUTPUTMIX,
            outputMixObj_
    };
    SLDataSink audioSnk = { &loc_outmix, nullptr };

    const SLInterfaceID ids[] = { SL_IID_ANDROIDSIMPLEBUFFERQUEUE };
    const SLboolean req[] = { SL_BOOLEAN_TRUE };

    res = (*engineItf_)->CreateAudioPlayer(
            engineItf_,
            &playerObj_,
            &audioSrc,
            &audioSnk,
            1,
            ids,
            req
    );
    if (res != SL_RESULT_SUCCESS) {
        playerObj_ = nullptr;
        return;
    }

    res = (*playerObj_)->Realize(playerObj_, SL_BOOLEAN_FALSE);
    if (res != SL_RESULT_SUCCESS) {
        playerObj_ = nullptr;
        return;
    }

    res = (*playerObj_)->GetInterface(
            playerObj_, SL_IID_PLAY, &playerItf_
    );
    if (res != SL_RESULT_SUCCESS) {
        playerItf_ = nullptr;
        return;
    }

    res = (*playerObj_)->GetInterface(
            playerObj_, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &playerQueueItf_
    );
    if (res != SL_RESULT_SUCCESS) {
        playerQueueItf_ = nullptr;
        return;
    }

    // Optional: register player callback (not required for simple loopback)
    (*playerQueueItf_)->RegisterCallback(
            playerQueueItf_, PlayerCallback, this
    );
}

void LiveAudioEngineImpl::destroyAll() {
    running_.store(false);

    if (playerObj_) {
        (*playerObj_)->Destroy(playerObj_);
        playerObj_ = nullptr;
        playerItf_ = nullptr;
        playerQueueItf_ = nullptr;
    }

    if (recorderObj_) {
        (*recorderObj_)->Destroy(recorderObj_);
        recorderObj_ = nullptr;
        recorderItf_ = nullptr;
        recorderQueueItf_ = nullptr;
    }

    if (outputMixObj_) {
        (*outputMixObj_)->Destroy(outputMixObj_);
        outputMixObj_ = nullptr;
    }

    if (engineObj_) {
        (*engineObj_)->Destroy(engineObj_);
        engineObj_ = nullptr;
        engineItf_ = nullptr;
    }

    if (jEngineObjGlobal_ && jvm_) {
        JNIEnv* env = nullptr;
        jint res = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
        if (res == JNI_OK && env) {
            env->DeleteGlobalRef(jEngineObjGlobal_);
        }
    }

    std::lock_guard<std::mutex> lock(playerMutex_);
    while (!playerBuffers_.empty()) {
        delete[] playerBuffers_.front();
        playerBuffers_.pop_front();
    }

    jEngineObjGlobal_ = nullptr;
    jvm_ = nullptr;
}

void LiveAudioEngineImpl::startLoopback() {
    if (!recorderItf_ || !recorderQueueItf_ || !playerItf_ || !playerQueueItf_) {
        return;
    }

    running_.store(true);

    // Set player to PLAYING first
    (*playerItf_)->SetPlayState(playerItf_, SL_PLAYSTATE_PLAYING);

    // Start recording
    (*recorderItf_)->SetRecordState(recorderItf_, SL_RECORDSTATE_RECORDING);
}

void LiveAudioEngineImpl::stopLoopback() {
    running_.store(false);

    if (recorderItf_) {
        (*recorderItf_)->SetRecordState(recorderItf_, SL_RECORDSTATE_STOPPED);
    }
    if (playerItf_) {
        (*playerItf_)->SetPlayState(playerItf_, SL_PLAYSTATE_STOPPED);
    }

    // Optional: clear queues
    if (recorderQueueItf_) {
        (*recorderQueueItf_)->Clear(recorderQueueItf_);
    }
    if (playerQueueItf_) {
        (*playerQueueItf_)->Clear(playerQueueItf_);
    }

    // Re-enqueue recorder buffers to be ready for next start
    if (recorderQueueItf_) {
        const SLuint32 bufferSizeBytes =
                static_cast<SLuint32>(buffers_[0].size() * sizeof(short));

        for (int i = 0; i < kBufferCount; ++i) {
            (*recorderQueueItf_)->Enqueue(
                    recorderQueueItf_,
                    buffers_[i].data(),
                    bufferSizeBytes
            );
        }
        currentRecordBufferIndex_ = 0;
    }
}

// ---- OpenSL callbacks ----

void LiveAudioEngineImpl::RecorderCallback(
        SLAndroidSimpleBufferQueueItf bq,
        void* context
) {
    (void)bq;
    auto* self = static_cast<LiveAudioEngineImpl*>(context);
    if (self) self->handleRecorderCallback();
}

void LiveAudioEngineImpl::PlayerCallback(
        SLAndroidSimpleBufferQueueItf bq,
        void* context
) {
    (void)bq;
    auto* self = static_cast<LiveAudioEngineImpl*>(context);
    if (self) self->handlePlayerCallback();
}

void LiveAudioEngineImpl::handleRecorderCallback() {
    const int idx = currentRecordBufferIndex_;
    auto& buf = buffers_[idx];
    const int samples = static_cast<int>(buf.size()); // shorts (mono)

    const SLuint32 bufferSizeBytes =
            static_cast<SLuint32>(samples * sizeof(short));

    if (!running_.load()) {
        // Just keep the recorder running by re-enqueueing this buffer.
        if (recorderQueueItf_) {
            (*recorderQueueItf_)->Enqueue(
                    recorderQueueItf_,
                    buf.data(),
                    bufferSizeBytes
            );
        }
        currentRecordBufferIndex_ =
                (currentRecordBufferIndex_ + 1) % kBufferCount;
        return;
    }

    // 1) send MIC PCM to Java (for mixing with BGM)
    dispatchPcmToJava(buf.data(), samples);

    // 2) immediately re-enqueue this buffer for the next capture
    if (recorderQueueItf_) {
        (*recorderQueueItf_)->Enqueue(
                recorderQueueItf_,
                buf.data(),
                bufferSizeBytes
        );
    }

    currentRecordBufferIndex_ =
            (currentRecordBufferIndex_ + 1) % kBufferCount;
}

void LiveAudioEngineImpl::handlePlayerCallback() {
    // Called when OpenSL has finished with one buffer
    std::lock_guard<std::mutex> lock(playerMutex_);
    if (!playerBuffers_.empty()) {
        short* ptr = playerBuffers_.front();
        playerBuffers_.pop_front();
        delete[] ptr;
    }
}

// ---- Java callback (Kotlin: onPcmCapturedFromNative) ----
void LiveAudioEngineImpl::dispatchPcmToJava(short* buffer, int samples) {
    if (!jvm_ || !jEngineObjGlobal_ || !buffer || samples <= 0) return;

    JNIEnv* env = nullptr;
    bool needDetach = false;

    jint res = jvm_->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (jvm_->AttachCurrentThread(&env, nullptr) != 0) {
            return;
        }
        needDetach = true;
    } else if (res != JNI_OK || env == nullptr) {
        return;
    }

    jclass cls = env->GetObjectClass(jEngineObjGlobal_);
    if (!cls) {
        if (needDetach) jvm_->DetachCurrentThread();
        return;
    }

    jmethodID mid = env->GetMethodID(
            cls,
            "onPcmCapturedFromNative",
            "([SI)V"   // short[] , int
    );
    if (!mid) {
        env->DeleteLocalRef(cls);
        if (needDetach) jvm_->DetachCurrentThread();
        return;
    }

    jshortArray arr = env->NewShortArray(samples);
    if (!arr) {
        env->DeleteLocalRef(cls);
        if (needDetach) jvm_->DetachCurrentThread();
        return;
    }

    env->SetShortArrayRegion(arr, 0, samples, buffer);
    env->CallVoidMethod(jEngineObjGlobal_, mid, arr, samples);

    env->DeleteLocalRef(arr);
    env->DeleteLocalRef(cls);

    if (needDetach) {
        jvm_->DetachCurrentThread();
    }
}

void LiveAudioEngineImpl::pushBgmPcm(const short* data, int samples) {
    if (!data || samples <= 0 || bgmBuffer_.empty()) return;

    std::lock_guard<std::mutex> lock(bgmMutex_);

    const size_t capacity = bgmBuffer_.size();
    for (int i = 0; i < samples; ++i) {
        bgmBuffer_[bgmWritePos_] = data[i];
        bgmWritePos_ = (bgmWritePos_ + 1) % capacity;

        // Simple overwrite if full (drop oldest)
        if (bgmWritePos_ == bgmReadPos_) {
            bgmReadPos_ = (bgmReadPos_ + 1) % capacity;
        }
    }
}

void LiveAudioEngineImpl::pushMixedPcm(const short *buffer, int samples) {
    if (!running_.load() || !playerQueueItf_) return;

    SLuint32 bytes = static_cast<SLuint32>(samples * sizeof(short));
    (*playerQueueItf_)->Enqueue(
            playerQueueItf_,
            buffer,
            bytes
    );
}