#include <CommonTools.h>
#include <audio_decoder.h>
#include "sound_service.h"
#include "MediaStatus.h"

#define LOG_TAG "SoundService"

SoundService* SoundService::instance = new SoundService();

SoundService::SoundService() {
    LOGI("SoundService::SoundService()");
    playingState = PLAYING_STATE_STOPPED;
}

SoundService::~SoundService() {
    LOGI("SoundService::~SoundService()");
}

SoundService* SoundService::GetInstance() {
    return instance;
}

void SoundService::setOnCompletionCallback(JavaVM *g_jvm_param, jobject objParam) {
    g_jvm = g_jvm_param;
    obj   = objParam;
}

void SoundService::producePacket() {
    if (playingState != PLAYING_STATE_PLAYING ||
        !decoderController || !mBuffer || !mTarget) {
        return;
    }

    // Which buffer to fill next
    uint8_t* frameBuffer =
            mBuffer + mCurrentFrame * (mPacketBufferSize * sizeof(short));

    int samples = decoderController->readSamples(mTarget, mPacketBufferSize);

    if (samples == MEDIA_STATUS_BUFFERING) {
        // Still decoding / seeking: push silence but let device clock move
        int channels = decoderController->getChannels();
        int frames   = (channels > 0)
                       ? (mPacketBufferSize / channels)
                       : mPacketBufferSize;

        memset(frameBuffer, 0, mPacketBufferSize * sizeof(short));
        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                mPacketBufferSize * sizeof(short));

        mFramesPerBuffer[mCurrentFrame] = frames;
        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;
        return;
    }

    if (samples == MEDIA_STATUS_EOF) {
        // No more data, stop and notify
        playingState = PLAYING_STATE_STOPPED;
        callComplete();
        return;
    }

    if (samples == MEDIA_STATUS_ERROR) {
        LOGE("producePacket: MEDIA_STATUS_ERROR from readSamples");
        // Enqueue one silent buffer just to be safe
        int channels = decoderController->getChannels();
        int frames   = (channels > 0)
                       ? (mPacketBufferSize / channels)
                       : mPacketBufferSize;

        memset(frameBuffer, 0, mPacketBufferSize * sizeof(short));
        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                mPacketBufferSize * sizeof(short));

        mFramesPerBuffer[mCurrentFrame] = frames;
        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;

        playingState = PLAYING_STATE_STOPPED;
        callComplete();
        return;
    }

    // -------- normal PCM path (MEDIA_STATUS_OK, samples > 0) --------
    if (samples > 0) {
        int channels = decoderController->getChannels();
        int frames   = (channels > 0)
                       ? (samples / channels)
                       : samples;

        // First real packet after start/seek: set audio clock base PTS
        if (!startPtsSet) {
            int64_t framePtsMs = decoderController->getProgress(); // startPosition of this buffer (ms)
            setStartPtsMs(framePtsMs);
            startPtsSet = true;
            LOGI("Audio clock base PTS set to %lld ms", (long long)audioBasePtsMs);
        }

        memcpy(frameBuffer, mTarget, samples * sizeof(short));

        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                samples * sizeof(short));

        // Remember how many frames this buffer has
        mFramesPerBuffer[mCurrentFrame] = frames;
        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;
    } else {
        // Should rarely happen; be safe: send silence
        LOGE("producePacket: unexpected samples=%d", samples);
        int channels = decoderController->getChannels();
        int frames   = (channels > 0)
                       ? (mPacketBufferSize / channels)
                       : mPacketBufferSize;

        memset(frameBuffer, 0, mPacketBufferSize * sizeof(short));
        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                mPacketBufferSize * sizeof(short));

        mFramesPerBuffer[mCurrentFrame] = frames;
        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;
    }
}

SLresult SoundService::RegisterPlayerCallback() {
    return (*audioPlayerBufferQueue)->RegisterCallback(
            audioPlayerBufferQueue, PlayerCallback, this);
}

SLresult SoundService::stop() {
    LOGI("enter SoundService::stop()");

    playingState = PLAYING_STATE_STOPPED;
    if (initedSoundTrack && audioPlayerPlay) {
        SLresult result = SetAudioPlayerStateStoped();
        if (SL_RESULT_SUCCESS != result) {
            LOGI("Set the audio player state stopped return false");
            return result;
        }
    }

    DestroyContext();
    LOGI("out SoundService::stop()");
    return SL_RESULT_SUCCESS;
}

SLresult SoundService::play() {
    LOGE("play ----  11111");
    if(!initedSoundTrack){
        initSoundTrack();
    }
    if (!audioPlayerPlay || !audioPlayerBufferQueue || !decoderController) {
        LOGE("play: missing components");
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }

    SLresult result = (*audioPlayerPlay)->SetPlayState(
            audioPlayerPlay, SL_PLAYSTATE_PLAYING);
    if (result != SL_RESULT_SUCCESS) return result;

    playingState = PLAYING_STATE_PLAYING;

    // Pre-fill the OpenSL queue to avoid initial underflow
    for (int i = 0; i < QUEUE_BUFFER_COUNT; ++i) {
        producePacket();
        if (playingState != PLAYING_STATE_PLAYING) break;
    }
    LOGE("play ----  22222");
    return SL_RESULT_SUCCESS;
}

SLresult SoundService::pause() {
    LOGI("enter SoundService::pause()...");
    SLresult result = SetAudioPlayerStatePaused();
    if (SL_RESULT_SUCCESS != result) {
        return result;
    }
    playingState = PLAYING_STATE_PAUSE;
    return result;
}

SLresult SoundService::resume() {
    LOGI("enter SoundService::resume()...");

    if (!audioPlayerPlay || !audioPlayerBufferQueue || !decoderController) {
        LOGE("resume: missing components");
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }

    // 1) mark playing so producePacket() passes the state check
    playingState = PLAYING_STATE_PLAYING;

    // 2) pre-fill the OpenSL queue
    for (int i = 0; i < QUEUE_BUFFER_COUNT; ++i) {
        producePacket();
        if (playingState != PLAYING_STATE_PLAYING) {
            break; // in case EOF/error fired inside producePacket
        }
    }

    // 3) start AudioTrack
    SLresult result = SetAudioPlayerStatePlaying();
    if (result != SL_RESULT_SUCCESS) {
        LOGE("resume: SetAudioPlayerStatePlaying failed: %d", result);
        return result;
    }

    return result;
}

void SoundService::seek(const long seek_time) {
    if (!decoderController) return;

    // 1) ask decoder to seek
    decoderController->seek(seek_time);

    // 2) stop and clear OpenSL queue
    if (audioPlayerPlay && audioPlayerBufferQueue) {
        (*audioPlayerPlay)->SetPlayState(audioPlayerPlay, SL_PLAYSTATE_STOPPED);
        (*audioPlayerBufferQueue)->Clear(audioPlayerBufferQueue);
    }

    // 3) reset clock-related state
    pthread_mutex_lock(&clockMutex);
    audioBasePtsMs = seek_time;
    playedFrames   = 0;
    pthread_mutex_unlock(&clockMutex);

    // reset indices
    mCurrentFrame   = 0;
    memset(mFramesPerBuffer, 0, sizeof(mFramesPerBuffer));
    startPtsSet = false;

    // 4) restart if we are in PLAYING state
    if (audioPlayerPlay && audioPlayerBufferQueue &&
        playingState == PLAYING_STATE_PLAYING) {

        for (int i = 0; i < mBufferNums; ++i) {
            producePacket();
        }
        (*audioPlayerPlay)->SetPlayState(audioPlayerPlay, SL_PLAYSTATE_PLAYING);
    }
}

bool SoundService::initSongDecoder(const char* accompanyPath) {
    LOGI("enter SoundService::initSongDecoder");

    if (!accompanyPath || strlen(accompanyPath) == 0) {
        LOGE("initSongDecoder: invalid path");
        return false;
    }

    if (decoderController) {
        decoderController->destroy();
        SAFE_DELETE(decoderController);
    }
    SAFE_DELETE_ARRAY(mTarget);
    SAFE_DELETE_ARRAY(mBuffer);

    decoderController = new AudioDecoderController();
    int metaData[3] = {0};
    int ret = decoderController->getMusicMeta(accompanyPath, metaData);
    if (ret != 0) {
        LOGE("getMusicMeta failed, ret=%d", ret);
        SAFE_DELETE(decoderController);
        return false;
    }

    accompanySampleRate = metaData[0];
    mPacketBufferSize   = metaData[1];  // number of SHORT samples per packet
    duration            = metaData[2];

    LOGI("meta: sampleRate=%d, packetBufferSize(samples)=%d, duration=%ld",
         accompanySampleRate, mPacketBufferSize, duration);

    // Allocate one packet-sized short buffer for readSamples()
    mTarget = new short[mPacketBufferSize];

    // Allocate ring buffer for OpenSL (QUEUE_BUFFER_COUNT packets)
    int bytesPerFrame = mPacketBufferSize * sizeof(short);
    int bufferSize    = bytesPerFrame * QUEUE_BUFFER_COUNT;
    mBuffer           = new uint8_t[bufferSize];
    memset(mBuffer, 0, bufferSize);

    // Reset clock state & indices
    pthread_mutex_lock(&clockMutex);
    mCurrentFrame    = 0;
    mPlayFrameIndex  = 0;
    playedFrames     = 0;
    audioBasePtsMs   = 0;
    startPtsSet      = false;
    memset(mFramesPerBuffer, 0, sizeof(mFramesPerBuffer));
    pthread_mutex_unlock(&clockMutex);

    // Initialize & start decoder
    if (decoderController->prepare(accompanyPath) != 0) {
        LOGE("initSongDecoder: decoder init failed");
        SAFE_DELETE_ARRAY(mTarget);
        SAFE_DELETE_ARRAY(mBuffer);
        decoderController->destroy();
        SAFE_DELETE(decoderController);
        return false;
    }

    callReady();
    LOGI("initSongDecoder: OK");
    return true;
}

// --------------------- Java callbacks ------------------------

void SoundService::callReady() {
    JNIEnv *env = nullptr;
    bool needDetach = false;
    LOGE("callReady");
    if (!g_jvm || !obj) {
        LOGE("%s: g_jvm or obj is null", __FUNCTION__);
        return;
    }
    jint getEnvResult = g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
            return;
        }
        needDetach = true;
    } else if (getEnvResult != JNI_OK) {
        LOGE("%s: GetEnv() failed, result=%d", __FUNCTION__, getEnvResult);
        return;
    }

    jclass cls = env->GetObjectClass(obj);
    if (!cls) {
        LOGE("%s: GetObjectClass() returned null", __FUNCTION__);
        if (needDetach) g_jvm->DetachCurrentThread();
        return;
    }

    jmethodID mid = env->GetMethodID(cls, "onReady", "()V");
    if (!mid) {
        LOGE("%s: GetMethodID(onReady) failed", __FUNCTION__);
        env->DeleteLocalRef(cls);
        if (needDetach) g_jvm->DetachCurrentThread();
        return;
    }

    LOGI("before CallVoidMethod onReady");
    env->CallVoidMethod(obj, mid);
    LOGI("after CallVoidMethod onReady");

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    env->DeleteLocalRef(cls);
    if (needDetach) {
        g_jvm->DetachCurrentThread();
    }
}

void SoundService::callComplete() {
    JNIEnv* env = nullptr;
    bool needDetach = false;
    LOGE("callComplete");
    if (!g_jvm || !obj) {
        LOGE("%s: g_jvm or obj is null", __FUNCTION__);
        return;
    }

    jint getEnvResult = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
            return;
        }
        needDetach = true;
    } else if (getEnvResult != JNI_OK) {
        LOGE("%s: GetEnv() failed, result=%d", __FUNCTION__, getEnvResult);
        return;
    }

    jclass cls = env->GetObjectClass(obj);
    if (!cls) {
        LOGE("%s: GetObjectClass() returned null", __FUNCTION__);
        if (needDetach) g_jvm->DetachCurrentThread();
        return;
    }

    jmethodID mid = env->GetMethodID(cls, "onCompletion", "()V");
    if (!mid) {
        LOGE("%s: GetMethodID(onCompletion) failed", __FUNCTION__);
        env->DeleteLocalRef(cls);
        if (needDetach) g_jvm->DetachCurrentThread();
        return;
    }

    LOGI("before CallVoidMethod onCompletion");
    env->CallVoidMethod(obj, mid);
    LOGI("after CallVoidMethod onCompletion");

    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    env->DeleteLocalRef(cls);
    if (needDetach) {
        g_jvm->DetachCurrentThread();
    }
}

// --------------------- OpenSL init ------------------------

SLresult SoundService::initSoundTrack() {
    LOGI("enter SoundService::initSoundTrack");
    if(initedSoundTrack){
        LOGE("initSoundTrack: has inited");
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }
    OpenSLESContext* openSLESContext = OpenSLESContext::GetInstance();
    engineEngine = openSLESContext->getEngine();
    if (!engineEngine) {
        LOGE("initSoundTrack: engineEngine is null");
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }

    SLresult result;

    LOGI("Create output mix object");
    result = CreateOutputMix();
    if (SL_RESULT_SUCCESS != result) return result;

    LOGI("Create the buffer queue audio player object");
    result = CreateBufferQueueAudioPlayer();
    if (SL_RESULT_SUCCESS != result) return result;

    LOGI("Realize audio player object");
    result = RealizeObject(audioPlayerObject);
    if (SL_RESULT_SUCCESS != result) return result;

    LOGI("Get audio player buffer queue interface");
    result = GetAudioPlayerBufferQueueInterface();
    if (SL_RESULT_SUCCESS != result) return result;

    LOGI("Registers the player callback");
    result = RegisterPlayerCallback();
    if (SL_RESULT_SUCCESS != result) return result;

    LOGI("Get audio player play interface");
    result = GetAudioPlayerPlayInterface();
    if (SL_RESULT_SUCCESS != result) return result;

    LOGI("leave init");
    initedSoundTrack = true;
    return SL_RESULT_SUCCESS;
}

// DataSource + player creation
SLresult SoundService::CreateBufferQueueAudioPlayer() {
    SLDataLocator_AndroidSimpleBufferQueue dataSourceLocator = {
            SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
            QUEUE_BUFFER_COUNT   // allow up to QUEUE_BUFFER_COUNT buffers queued
    };

    uint samplesPerSec = opensl_get_sample_rate(accompanySampleRate);
    SLDataFormat_PCM dataSourceFormat = {
            SL_DATAFORMAT_PCM,
            2,
            samplesPerSec,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_PCMSAMPLEFORMAT_FIXED_16,
            SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT,
            SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource dataSource = {
            &dataSourceLocator,
            &dataSourceFormat
    };

    SLDataLocator_OutputMix dataSinkLocator = {
            SL_DATALOCATOR_OUTPUTMIX,
            outputMixObject
    };

    SLDataSink dataSink = { &dataSinkLocator, nullptr };

    SLInterfaceID interfaceIds[] = { SL_IID_BUFFERQUEUE };
    SLboolean requiredInterfaces[] = { SL_BOOLEAN_TRUE };

    return (*engineEngine)->CreateAudioPlayer(
            engineEngine,
            &audioPlayerObject,
            &dataSource,
            &dataSink,
            ARRAY_LEN(interfaceIds),
            interfaceIds,
            requiredInterfaces
    );
}

int64_t SoundService::getCurrentTimeMills() {
    return decoderController ? decoderController->getProgress() : 0;
}

int64_t SoundService::getAudioClockMs() {
    pthread_mutex_lock(&clockMutex);
    int64_t frames = playedFrames;
    int     sr     = accompanySampleRate;
    int64_t base   = audioBasePtsMs;
    pthread_mutex_unlock(&clockMutex);

    if (sr <= 0) return -1;

    // CHANGED: when no frames consumed yet, use base PTS, not -1
    if (frames <= 0) {
        return base;   // 0 at start, or seek_time after seek
    }

    int64_t playedMs = frames * 1000LL / sr;
    int64_t result   = base + playedMs;

    LOGI("getAudioClockMs frames=%lld sr=%d base=%lld result:%lld",
         (long long)frames, sr, (long long)base, (long long)result);
    return result;
}

void SoundService::setStartPtsMs(int64_t startPtsMs) {
    pthread_mutex_lock(&clockMutex);
    audioBasePtsMs = startPtsMs;
    playedFrames   = 0;
    pthread_mutex_unlock(&clockMutex);
}

void SoundService::onBufferConsumed(int bufferFrames) {
    pthread_mutex_lock(&clockMutex);
    playedFrames += bufferFrames;
    pthread_mutex_unlock(&clockMutex);
    LOGI("onBufferConsumed bufferFrames:%d playedFrames:%lld",
         bufferFrames, (long long)playedFrames);
}

bool SoundService::isPlaying() {
    return playingState != PLAYING_STATE_STOPPED;
}

void SoundService::DestroyContext() {
    LOGI("DestroyContext");
    playingState = PLAYING_STATE_STOPPED;
    initedSoundTrack = false;

    if (audioPlayerObject) {
        (*audioPlayerObject)->Destroy(audioPlayerObject);
        audioPlayerObject = nullptr;
        audioPlayerPlay   = nullptr;
        audioPlayerBufferQueue = nullptr;
    }

    if (outputMixObject) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = nullptr;
    }

    SAFE_DELETE_ARRAY(mBuffer);
    SAFE_DELETE_ARRAY(mTarget);
    SAFE_DELETE(decoderController);

    pthread_mutex_lock(&clockMutex);
    playedFrames   = 0;
    audioBasePtsMs = 0;
    mCurrentFrame  = 0;
    mPlayFrameIndex = 0;
    memset(mFramesPerBuffer, 0, sizeof(mFramesPerBuffer));
    startPtsSet = false;
    pthread_mutex_unlock(&clockMutex);

    LOGI("DestroyContext~~~~~");
}

int SoundService::getDurationTimeMills() {
    return (int)duration;
}

void SoundService::setVisualizerEnabled(bool enabled) {
    if (decoderController) {
        decoderController->setVisualizerEnabled(enabled);
    }
}