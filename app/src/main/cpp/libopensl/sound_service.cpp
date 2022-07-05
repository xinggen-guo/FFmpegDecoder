#include <CommonTools.h>
#include <audio_decoder.h>
#include "sound_service.h"
#define LOG_TAG "SoundService"

SoundService::SoundService() {
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
void SoundService::producePacket(bool isPlayInit) {
	LOGI("SoundService::producePacket() audio player call back method... ");
	// Read data
	short *audioBuffer = new short[packetBufferSize];
	int result = -1;
	if (NULL != decoderController) {
		result = decoderController->readSapmles(target, packetBufferSize);
		LOGI("enter SoundService::producePacket() PLAYING_STATE_PLAYING packetBufferSize=%d, result=%d, isPlayInit=%d", packetBufferSize, result, isPlayInit);
	}

	// If data is read
	if (0 < result) { //播放数据正常读写
		(*audioPlayerBufferQueue)->Enqueue(audioPlayerBufferQueue, audioBuffer, result * 2);
	} else if (result == -2) {  //需要等待解码数据

	} else if (result == -3) {   //播放完成，需要调用完成回调
//		callComplete();  暂时先注掉，有问题
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
	SLresult result = SetAudioPlayerStateStoped();
	if (SL_RESULT_SUCCESS != result) {
		LOGI("Set the audio player state paused return false");
		return result;
	}
	DestroyContext();
	LOGI("out SoundService::stop()");
}

SLresult SoundService::play() {
	LOGI("enter SoundService::play()...");

	// Set the audio player state playing
	LOGI("Set the audio player state playing");
	SLresult result = SetAudioPlayerStatePlaying();
	if (SL_RESULT_SUCCESS != result) {
		return result;
	}
	LOGI(" Enqueue the first buffer to start");

	playingState = PLAYING_STATE_PLAYING;

	producePacket(true);

	LOGI("out SoundService::play()...");
	return result;
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
	decoderController = new AudioDecoderController();
	int* metaData = new int[2];
	decoderController->getMusicMeta(accompanyPath, metaData);
	accompanySampleRate = metaData[0];
	//这个是解码器解码一个packet的buffer的大小
	packetBufferSize =  metaData[1];
	//我们这里预设置bufferNums个packet的buffer
	duration = metaData[2];
	decoderController->prepare(accompanyPath);
//	callReady();  暂时先注掉，有问题
	return true;
}

void SoundService::callReady(){
	JNIEnv *env;
	//Attach主线程
	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
		LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
	}
	jclass jcls = env->GetObjectClass(obj);
	jmethodID onCompletionCallBack = env->GetMethodID(jcls, "onReady", "()V");
	LOGI("before env->CallVoidMethod");
	env->CallVoidMethod(obj, onCompletionCallBack);
	LOGI("after env->CallVoidMethod");
	//Detach主线程
	if (g_jvm->DetachCurrentThread() != JNI_OK) {
		LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
	}
}

void SoundService::callComplete() {
	JNIEnv *env;
	//Attach主线程
	if (g_jvm->AttachCurrentThread(&env, NULL) != JNI_OK) {
		LOGE("%s: AttachCurrentThread() failed", __FUNCTION__);
	}
	jclass jcls = env->GetObjectClass(obj);
	jmethodID onCompletionCallBack = env->GetMethodID(jcls, "onCompletion", "()V");
	LOGI("before env->CallVoidMethod");
	env->CallVoidMethod(obj, onCompletionCallBack);
	LOGI("after env->CallVoidMethod");
	//Detach主线程
	if (g_jvm->DetachCurrentThread() != JNI_OK) {
		LOGE("%s: DetachCurrentThread() failed", __FUNCTION__);
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

	LOGI("Realize output mix object");
	// Realize output mix object
	result = RealizeObject(outputMixObject);
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
	return SL_RESULT_SUCCESS;
}

int SoundService::getCurrentTimeMills() {
	return decoderController->getProgress();
}

bool SoundService::isPlaying() {
	return playingState != PLAYING_STATE_STOPPED;
}

void SoundService::DestroyContext() {
//	pthread_mutex_lock(&mLock);
	LOGI("enter SoundService::DestroyContext");
	// Destroy audio player object
	DestroyObject(audioPlayerObject);
	LOGI("after destroy audioPlayerObject");
	// Free the player buffer
	FreePlayerBuffer();
	LOGI("after FreePlayerBuffer");
	// Destroy output mix object
	DestroyObject(outputMixObject);
	LOGI("after destroy outputMixObject");
	//destroy mad decoder
	if (NULL != decoderController) {
		decoderController->destroy();
		delete decoderController;
		decoderController = NULL;
	}
	LOGI("leave SoundService::DestroyContext");
}

int SoundService::getDurationTimeMills() {
	return duration;
}

