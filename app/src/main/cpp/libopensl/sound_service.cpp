#include <CommonTools.h>
#include <audio_decoder.h>
#include "sound_service.h"
#define LOG_TAG "SoundService"

SoundService::SoundService(){
    LOGI("SoundService::SoundService()");
    playingState = PLAYING_STATE_STOPPED;
}

SoundService::~SoundService() {
	LOGI("SoundService::~SoundService()");
}

SoundService* SoundService::instance = new SoundService();

SoundService* SoundService::GetInstance() {
	return instance;
}

void SoundService::setOnCompletionCallback(JavaVM *g_jvm_param, jobject objParam) {
	g_jvm = g_jvm_param;
	obj = objParam;
}
void SoundService::producePacket() {
    if (playingState != PLAYING_STATE_PLAYING ||
        !decoderController || !mBuffer || !mTarget) {
        return;
    }

    uint8_t* frameBuffer =
            mBuffer + mCurrentFrame * (mPacketBufferSize * sizeof(short));

    int samples = decoderController->readSamples(mTarget, mPacketBufferSize);

    if (samples > 0) {
        memcpy(frameBuffer, mTarget, samples * sizeof(short));

        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                samples * sizeof(short));

        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;

    } else if (samples == -2) {
        memset(frameBuffer, 0, mPacketBufferSize * sizeof(short));

        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                mPacketBufferSize * sizeof(short));

        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;

    } else if (samples == -3) {
        playingState = PLAYING_STATE_STOPPED;
        callComplete();

    } else {
        LOGE("producePacket: readSamples error=%d", samples);
        memset(frameBuffer, 0, mPacketBufferSize * sizeof(short));

        (*audioPlayerBufferQueue)->Enqueue(
                audioPlayerBufferQueue,
                frameBuffer,
                mPacketBufferSize * sizeof(short));

        mCurrentFrame = (mCurrentFrame + 1) % QUEUE_BUFFER_COUNT;
    }
}

SLresult SoundService::RegisterPlayerCallback() {
	// Register the player callback
	return (*audioPlayerBufferQueue)->RegisterCallback(audioPlayerBufferQueue, PlayerCallback, this); // player context
}

SLresult SoundService::stop() {
	LOGI("enter SoundService::stop()");

	playingState = PLAYING_STATE_STOPPED;
	LOGI("Set the audio player state paused");
	// Set the audio player state playing
    if(initedSoundTrack) {
        SLresult result = SetAudioPlayerStateStoped();
        if (SL_RESULT_SUCCESS != result) {
            LOGI("Set the audio player state paused return false");
            return result;
        }
    }
	DestroyContext();
	LOGI("out SoundService::stop()");
    return SL_RESULT_SUCCESS;
}

SLresult SoundService::play() {
    if (!audioPlayerPlay || !audioPlayerBufferQueue || !decoderController) {
        LOGE("play: missing components");
        return SL_RESULT_PRECONDITIONS_VIOLATED;
    }

    SLresult result = (*audioPlayerPlay)->SetPlayState(
            audioPlayerPlay, SL_PLAYSTATE_PLAYING);

    if (result != SL_RESULT_SUCCESS) return result;

    playingState = PLAYING_STATE_PLAYING;

    // 播放前预塞满所有 buffer，避免卡顿
    for (int i = 0; i < QUEUE_BUFFER_COUNT; ++i) {
        producePacket();
        if (playingState != PLAYING_STATE_PLAYING) break;
    }

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

	// Set the audio player state playing
	LOGI("Set the audio resume state playing");
	SLresult result = SetAudioPlayerStatePlaying();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}
	playingState = PLAYING_STATE_PLAYING;
	return result;
}

void SoundService::seek(const long seek_time) {
	decoderController->seek(seek_time);
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
        return false;
    }
    accompanySampleRate = metaData[0];
    mPacketBufferSize   = metaData[1];  // 注意：这里视为“short 的个数”
    duration            = metaData[2];

    LOGI("meta: sampleRate=%d, packetBufferSize(samples)=%d, duration=%d",
         accompanySampleRate, mPacketBufferSize, duration);

    // 分配一个 packet 的 short 缓冲，用于 readSamples
    mTarget = new short[mPacketBufferSize];

    int bytesPerFrame = mPacketBufferSize * sizeof(short);
    int bufferSize = bytesPerFrame * QUEUE_BUFFER_COUNT;
    mBuffer = new uint8_t[bufferSize];

    memset(mBuffer, 0, bufferSize);
    mCurrentFrame = 0;

    // 初始化/打开解码器

    if ((decoderController->prepare(accompanyPath)) != 0) {
        LOGE("initSongDecoder: decoder init failed");
        return false;
    }
    callReady();
    LOGI("initSongDecoder: OK");
    return true;
}

void SoundService::callReady() {
    JNIEnv* env = nullptr;
    bool needDetach = false;

    // 1. 尝试获取当前线程的 JNIEnv
    jint getEnvResult = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        // 当前线程还没附加 → 附加一下
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
            return;
        }
        needDetach = true;
    } else if (getEnvResult != JNI_OK) {
        LOGE("%s: GetEnv() failed, result=%d", __FUNCTION__, getEnvResult);
        return;
    }

    // 2. 调 Java 方法
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

    // 处理潜在异常（建议加上）
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    env->DeleteLocalRef(cls);

    if (needDetach) {
        if (g_jvm->DetachCurrentThread() != JNI_OK) {
            LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
        }
    }
}

void SoundService::callComplete() {
    JNIEnv* env = nullptr;
    bool needDetach = false;

    // 1. 先检查当前线程是否已经附加到 JVM
    jint getEnvResult = g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (getEnvResult == JNI_EDETACHED) {
        // 还没附加 → Attach 一次
        if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
            LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
            return;
        }
        needDetach = true;  // 只对自己 attach 的线程执行 detach
    } else if (getEnvResult != JNI_OK) {
        LOGE("%s: GetEnv() failed, result=%d", __FUNCTION__, getEnvResult);
        return;
    }

    // 2. 调用 Java 层 onCompletion()
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

    // 如果 Java 抛异常，打印一下避免一直挂着
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }

    env->DeleteLocalRef(cls);

    // 3. 只在自己 Attach 的情况下 Detach
    if (needDetach) {
        if (g_jvm->DetachCurrentThread() != JNI_OK) {
            LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
        }
    }
}

SLresult SoundService::initSoundTrack() {
	LOGI("enter SoundService::initSoundTrack");
//	isRunning = true;
//	pthread_mutex_init(&mLock, NULL);
//	pthread_cond_init(&mCondition, NULL);

	LOGI("get open sl es Engine");
	SLresult result;
	OpenSLESContext* openSLESContext = OpenSLESContext::GetInstance();
	engineEngine = openSLESContext->getEngine();

	LOGI("Create output mix object");
	// Create output mix object
	result = CreateOutputMix();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}

	LOGI("Initialize buffer");
	// Initialize buffer
	InitPlayerBuffer();

	LOGI("Create the buffer queue audio player object");
	// Create the buffer queue audio player object
	result = CreateBufferQueueAudioPlayer();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}

	LOGI("Realize audio player object");
	// Realize audio player object
	result = RealizeObject(audioPlayerObject);
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}

	LOGI("Get audio player buffer queue interface");
	// Get audio player buffer queue interface
	result = GetAudioPlayerBufferQueueInterface();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}

	LOGI("Registers the player callback");
	// Registers the player callback
	result = RegisterPlayerCallback();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}
	LOGI("Get audio player play interface");
	// Get audio player play interface
	result = GetAudioPlayerPlayInterface();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}
	LOGI("leave init");

    initedSoundTrack = true;
	return SL_RESULT_SUCCESS;
}

int SoundService::getCurrentTimeMills() {
	return decoderController->getProgress();
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
        audioPlayerPlay = nullptr;
        audioPlayerBufferQueue = nullptr;
    }

    if (outputMixObject) {
        (*outputMixObject)->Destroy(outputMixObject);
        outputMixObject = nullptr;
    }

    // 先释放解码器内部资源
    if (decoderController) {
        decoderController->destroy();   // 只做内部资源释放，不 delete this
    }
    // 缓冲区
    SAFE_DELETE_ARRAY(mBuffer);
    SAFE_DELETE_ARRAY(mTarget);

    // 再释放控制器对象本身
    SAFE_DELETE(decoderController);

    LOGI("DestroyContext~~~~~");
}

int SoundService::getDurationTimeMills() {
	return duration;
}

void SoundService::setVisualizerEnabled(bool enabled) {
    decoderController->setVisualizerEnabled(enabled);
}

