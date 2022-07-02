#ifndef _MEDIA_SOUND_SERVICE_
#define _MEDIA_SOUND_SERVICE_

#include <audio_decoder_controller.h>
#include "opensl_es_util.h"
#include "opensl_es_context.h"

#define PLAYING_STATE_STOPPED (0x00000001)
#define PLAYING_STATE_PLAYING (0x00000002)
#define PLAYING_STATE_PAUSE (0x00000003)

class SoundService {
private:
	SoundService(); //注意:构造方法私有
	static SoundService* instance; //惟一实例

    int playingState;
	//播放完成回调的时候需要用到的参数
	JavaVM *g_jvm;
	jobject obj;

	//解码Mp3的解码的controller
	AudioDecoderController* decoderController;
	//伴奏的采样频率
	int accompanySampleRate;
	int duration;

	SLEngineItf engineEngine;
	SLObjectItf outputMixObject;
	SLObjectItf audioPlayerObject;
	SLAndroidSimpleBufferQueueItf audioPlayerBufferQueue;
	SLPlayItf audioPlayerPlay;

	int packetBufferSize;
	short* target;

	/**
	 * Realize the given object. Objects needs to be
	 * realized before using them.
	 * @param object object instance.
	 */
	SLresult RealizeObject(SLObjectItf object) {
		// Realize the engine object
		return (*object)->Realize(object, SL_BOOLEAN_FALSE); // No async, blocking call
	};
	/**
	 * Destroys the given object instance.
	 * @param object object instance. [IN/OUT]
	 */
	void DestroyObject(SLObjectItf& object) {
		if (0 != object)
			(*object)->Destroy(object);
		object = 0;
	};
	/**
	 * Creates and output mix object.
	 */
	SLresult CreateOutputMix() {
		// Create output mix object
		return (*engineEngine)->CreateOutputMix(engineEngine, &outputMixObject, 0, // no interfaces
				0, // no interfaces
				0); // no required
	};
	/**
	 * Free the player buffer.
	 * @param buffers buffer instance. [OUT]
	 */
	void FreePlayerBuffer() {
		if (NULL != target) {
			delete[] target;
			target = NULL;
		}
	};
	/**
	 * Initializes the player buffer.
	 * @param buffers buffer instance. [OUT]
	 * @param bufferSize buffer size. [OUT]
	 */
	void InitPlayerBuffer() {
		//Initialize buffer
		target = new short[packetBufferSize];
	};
	/**
	 * Creates buffer queue audio player.
	 */
	SLresult CreateBufferQueueAudioPlayer() {
		// Android simple buffer queue locator for the data source
		SLDataLocator_AndroidSimpleBufferQueue dataSourceLocator = { SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, // locator type
				1 // buffer count
				};

		//PCM data source format
//		SLDataFormat_PCM dataSourceFormat = { SL_DATAFORMAT_PCM, // format type
//				2, // channel count
//				accompanySampleRate, // samples per second in millihertz
//				16, // bits per sample
//				16, // container size
//				SL_SPEAKER_FRONT_CENTER, // channel mask
//				SL_BYTEORDER_LITTLEENDIAN // endianness
//				};

		uint samplesPerSec = opensl_get_sample_rate(accompanySampleRate);
		SLDataFormat_PCM dataSourceFormat = { SL_DATAFORMAT_PCM, // format type
				2, // channel count
				samplesPerSec, // samples per second in millihertz
				SL_PCMSAMPLEFORMAT_FIXED_16, // bits per sample
				SL_PCMSAMPLEFORMAT_FIXED_16, // container size
				SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, // channel mask
				SL_BYTEORDER_LITTLEENDIAN // endianness
				};

		// Data source is a simple buffer queue with PCM format
		SLDataSource dataSource = { &dataSourceLocator, // data locator
				&dataSourceFormat // data format
				};

		// Output mix locator for data sink
		SLDataLocator_OutputMix dataSinkLocator = { SL_DATALOCATOR_OUTPUTMIX, // locator type
				outputMixObject // output mix
				};

		// Data sink is an output mix
		SLDataSink dataSink = { &dataSinkLocator, // locator
				0 // format
				};

		// Interfaces that are requested
		SLInterfaceID interfaceIds[] = { SL_IID_BUFFERQUEUE };

		// Required interfaces. If the required interfaces
		// are not available the request will fail
		SLboolean requiredInterfaces[] = { SL_BOOLEAN_TRUE // for SL_IID_BUFFERQUEUE
				};

		// Create audio player object
		return (*engineEngine)->CreateAudioPlayer(engineEngine, &audioPlayerObject, &dataSource, &dataSink, ARRAY_LEN(interfaceIds), interfaceIds, requiredInterfaces);
	};


	/**
	 * Gets the audio player buffer queue interface.
	 */
	SLresult GetAudioPlayerBufferQueueInterface() {
		// Get the buffer queue interface
		return (*audioPlayerObject)->GetInterface(audioPlayerObject, SL_IID_BUFFERQUEUE, &audioPlayerBufferQueue);
	};

	/**
	 * Gets called when a buffer finishes playing.
	 */
	static void PlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
		SoundService* service = (SoundService*) context;
		service->producePacket(false);
	};

	/**
	 * Registers the player callback.
	 */
	SLresult RegisterPlayerCallback();

	SLresult RegisterSlientPlayerCallback();

	/**
	 * Gets the audio player play interface.
	 */
	SLresult GetAudioPlayerPlayInterface() {
		// Get the play interface
		return (*audioPlayerObject)->GetInterface(audioPlayerObject, SL_IID_PLAY, &audioPlayerPlay);
	};

	/**
	 * Sets the audio player state playing.
	 */
	SLresult SetAudioPlayerStatePlaying() {
		// Set audio player state to playing
		return (*audioPlayerPlay)->SetPlayState(audioPlayerPlay, SL_PLAYSTATE_PLAYING);
	};

	/**
	 * Sets the audio player state paused.
	 */
	SLresult SetAudioPlayerStatePaused() {
		// Set audio player state to paused
		return (*audioPlayerPlay)->SetPlayState(audioPlayerPlay, SL_PLAYSTATE_PAUSED);
	};

	SLresult SetAudioPlayerStateStoped() {
		// Set audio player state to paused
		return (*audioPlayerPlay)->SetPlayState(audioPlayerPlay, SL_PLAYSTATE_STOPPED);
	};

public:
	static SoundService* GetInstance(); //工厂方法(用来获得实例)
	virtual ~SoundService();
	void setOnCompletionCallback(JavaVM *g_jvm, jobject obj);

	bool initSongDecoder(const char* accompanyPath);
	SLresult initSoundTrack();
	int getAccompanySampleRate() {
		return accompanySampleRate;
	};
	SLresult play();
	SLresult stop();
	SLresult pause();
	SLresult resume();

	void producePacket(bool isPlayInit);
	bool isPlaying();
	int getCurrentTimeMills();

	/**
	 * Destroy the player context.
	 */
	void DestroyContext();
	int getDurationTimeMills();

};
#endif	//_MEDIA_SOUND_SERVICE_
