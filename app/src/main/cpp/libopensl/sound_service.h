#ifndef _MEDIA_SOUND_SERVICE_
#define _MEDIA_SOUND_SERVICE_

#include <audio_decoder_controller.h>
#include "opensl_es_util.h"
#include "opensl_es_context.h"

#define PLAYING_STATE_STOPPED (0x00000001)
#define PLAYING_STATE_PLAYING (0x00000002)
#define PLAYING_STATE_PAUSE   (0x00000003)

class SoundService {
private:
    SoundService(); // private ctor
    static SoundService* instance; // singleton

    int playingState = PLAYING_STATE_STOPPED;

    // Java callback stuff
    JavaVM *g_jvm = nullptr;
    jobject obj   = nullptr;

    // ---- audio buffer / queue ----
    static const int QUEUE_BUFFER_COUNT = 4;   // 4 buffers queued to OpenSL

    // Multi-buffer ring for OpenSL (bytes)
    uint8_t* mBuffer       = nullptr;          // whole ring buffer
    int      mBufferNums   = QUEUE_BUFFER_COUNT;

    // write index: where we write next PCM block
    int      mCurrentFrame = 0;

    // play index: which buffer OpenSL has just consumed
    int      mPlayFrameIndex = 0;

    // Per-buffer PCM frame count (for each queued buffer)
    int      mFramesPerBuffer[QUEUE_BUFFER_COUNT] = {0};

    // Per-packet PCM sample count (SHORTS, not bytes)
    int      mPacketBufferSize = 0;

    // Temporary PCM buffer used for readSamples (SHORTS)
    short*   mTarget = nullptr;

    // Decoder & metadata
    long                    duration            = 0;
    AudioDecoderController* decoderController   = nullptr;
    int                     accompanySampleRate = 0;

    // OpenSL objects
    SLEngineItf                   engineEngine            = nullptr;
    SLObjectItf                   outputMixObject         = nullptr;
    SLObjectItf                   audioPlayerObject       = nullptr;
    SLAndroidSimpleBufferQueueItf audioPlayerBufferQueue  = nullptr;
    SLPlayItf                     audioPlayerPlay         = nullptr;

    bool initedSoundTrack = false;

    // ---- audio clock based on CONSUMED frames ----
    // frames actually played by the device
    int64_t playedFrames   = 0;
    // base PTS of this playback segment (0, or seek position, in ms)
    int64_t audioBasePtsMs = 0;
    bool    startPtsSet    = false;

    pthread_mutex_t clockMutex = PTHREAD_MUTEX_INITIALIZER;

    // helper: realize & destroy OpenSL objects
    SLresult RealizeObject(SLObjectItf object) {
        return (*object)->Realize(object, SL_BOOLEAN_FALSE);
    }
    void DestroyObject(SLObjectItf& object) {
        if (object != nullptr) {
            (*object)->Destroy(object);
        }
        object = nullptr;
    }

    // Create output mix
    SLresult CreateOutputMix() {
        if (!engineEngine) return SL_RESULT_PRECONDITIONS_VIOLATED;

        SLresult result = (*engineEngine)->CreateOutputMix(
                engineEngine, &outputMixObject, 0, nullptr, nullptr);
        if (result != SL_RESULT_SUCCESS) {
            return result;
        }
        return (*outputMixObject)->Realize(outputMixObject, SL_BOOLEAN_FALSE);
    }

    // Create buffer queue audio player
    SLresult CreateBufferQueueAudioPlayer();

    // Get interfaces
    SLresult GetAudioPlayerBufferQueueInterface() {
        return (*audioPlayerObject)->GetInterface(
                audioPlayerObject, SL_IID_BUFFERQUEUE, &audioPlayerBufferQueue);
    }
    SLresult GetAudioPlayerPlayInterface() {
        return (*audioPlayerObject)->GetInterface(
                audioPlayerObject, SL_IID_PLAY, &audioPlayerPlay);
    }

    // Player states
    SLresult SetAudioPlayerStatePlaying() {
        return (*audioPlayerPlay)->SetPlayState(
                audioPlayerPlay, SL_PLAYSTATE_PLAYING);
    }
    SLresult SetAudioPlayerStatePaused() {
        return (*audioPlayerPlay)->SetPlayState(
                audioPlayerPlay, SL_PLAYSTATE_PAUSED);
    }
    SLresult SetAudioPlayerStateStoped() {
        return (*audioPlayerPlay)->SetPlayState(
                audioPlayerPlay, SL_PLAYSTATE_STOPPED);
    }

    // Buffer queue callback
    static void PlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
        SoundService* service = (SoundService*) context;
        if (!service || !service->decoderController) {
            return;
        }

        int frames   = 0;
        int channels = service->decoderController->getChannels();

        // Index of the buffer that just finished playing
        int playIndex = service->mPlayFrameIndex;
        if (playIndex < 0 || playIndex >= QUEUE_BUFFER_COUNT) {
            playIndex = 0;
        }

        frames = service->mFramesPerBuffer[playIndex];
        if (frames <= 0) {
            // fallback: assume full packet size
            frames = (channels > 0)
                     ? (service->mPacketBufferSize / channels)
                     : service->mPacketBufferSize;
        }

        // update audio clock using "consumed frames"
        service->onBufferConsumed(frames);

        // advance play index (which buffer is next to be "finished" next time)
        service->mPlayFrameIndex =
                (service->mPlayFrameIndex + 1) % SoundService::QUEUE_BUFFER_COUNT;

        // enqueue next packet
        service->producePacket();
    }

    // Register callback
    SLresult RegisterPlayerCallback();

    // clock helpers
    void setStartPtsMs(int64_t startPtsMs);
    void onBufferConsumed(int bufferFrames);

public:
    static SoundService* GetInstance();
    virtual ~SoundService();

    void setOnCompletionCallback(JavaVM *g_jvm, jobject obj);

    bool     initSongDecoder(const char* accompanyPath);
    SLresult initSoundTrack();
    int      getAccompanySampleRate() { return accompanySampleRate; }

    SLresult play();
    SLresult stop();
    SLresult pause();
    SLresult resume();

    void seek(const long seek_time);

    void producePacket();
    bool isPlaying();
    int64_t getCurrentTimeMills();
    int64_t getAudioClockMs();

    void DestroyContext();
    int  getDurationTimeMills();

    void setVisualizerEnabled(bool enabled);

    void callReady();
    void callComplete();
};

#endif  // _MEDIA_SOUND_SERVICE_