#include <jni.h>
#include <string>
#include <zlib.h>
#include <android/log.h>
#include "audio_decoder_cortroller.h"
#include "sys/time.h"

const char *TAG = "FFmpegDecorder";

#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {
    #include <libavformat/avformat.h>
    #include <libswresample/swresample.h>
}


long getCurrentTime(){
    struct timeval tv;
    gettimeofday(&tv,NULL);
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_audio_study_ffmpegdecoder_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"


void decodeSample(JNIEnv *env, jobject thiz) {
//    //2  注册协议格式
//    avformat_network_init();
//    avcodec_register_all();
//
//
//    //3  打开媒体文件源 并设置超时时间
//    AVFormatContext *formatCtx = avformat_alloc_context();
//    AVIOInterruptCB int_cb = {interrupt_callback, operateCallback};
//    formatCtx->interrupt_callback = int_cb;
//    avformat_open_input(formatCtx, path, NULL, NULL);
//    avformat_find_stream_info(formatCtx, NULL);
//
//    //4   找到对应的视频流或者音频流
//    for (int i = 0; i < formatCtx->nb_streams; i++) {
//        AVStream *stream = formatCtx->streams[i];
//        if (AVMEDIA_TYPE_VIDEO == stream->codec->codec_type) {
//            videoStreamIndex = i;
//        } else if (AVMEDIA_TYPE_AUDIO == stream->codec->codec_type) {
//            audioStreamIndex = i;
//        }
//    }
//
//    //   找到对应的音频解码器
//    AVCodecContext *audioCodecCtx = audioStream->codec;
//    AVCodec *codec = avcodec_find_decoder(audioCodecCtx->codec_id);
//    if (!codec) {
//        // 找不到对应的音频解码器
//    }
//    int openCodecErrCode = 0;
//    if ((openCodecErrCode = avcodec_open2(codecCtx, codec, NULL)) < 0) {
//        // 打开音频解码器失败
//    }
//
//    //找到对应的视频解码器
//    AVCodecContext *videoCodecCtx = videoStream->codec;
//    AVCodec *codec = avcodec_find_decoder(videoCodecCtx->codec_id);
//    if (!codec) {
//        // 找不的视频解码器
//    }
//    int openCodecErrCode = 0;
//    if ((openCodecErrCode = avcodec_open2(codecCtx, codec, NULL)) < 0) {
//        // 打开视频解码器失败
//    }
//
//
//    //5   初始化解码后的数据结构体
//
//    //构建音频的呃格式转换对象以及音频解码后的数据存放的对象
//    SwrContext *swrContext = NULL;
//    if (audioCodecCtx->sample_fmt != AV_SAMPLE_FMT_S16) {
//        // 这不是我们需要的数据格式
//        swrContext = swr_alloc_set_opts(NULL,
//                                        outputChannel, AV_SAMPLE_FMT_S16, outSampleRate,
//                                        in_ch_layout, in_sample_fmt, in_sample_rate, 0, NULL);
//        if (!swrContext || swr_init(swrContext)) {
//            if (swrContext) {
//                swr_free(&swrContext);
//            }
//        }
//        audioFrame = avcodec_alloc_frame();
//    }
//
//    //构建视频的格式转换对象以及视频解码后数据存放对象
//    AVPicture picture;
//    bool pictureValid = avpicture_alloc(&picture,
//                                        PIX_FMT_YUV420P,
//                                        videoCodecCtx->width,
//                                        videoCodecCtx->height) == 0;
//    if (!pictureValid) {
//        // 分配失败
//        return false;
//    }
//    swsContext = sws_getCachedContext(swsContext,
//                                      videoCodecCtx->width,
//                                      videoCodecCtx->height,
//                                      videoCodecCtx->pix_fmt,
//                                      videoCodecCtx->width,
//                                      videoCodecCtx->height,
//                                      PIX_FMT_YUV420P,
//                                      SWS_FAST_BILINEAR,
//                                      NULL, NULL, NULL);
//    videoFrame = avcodec_alloc_frame();
//
//
//    //6    读取流内容并解码
//    AVPacket packet;
//    int gotFrame = 0;
//    while (true) {
//        if (av_read_frame(formatContext, &packet)) {
//            // End Of File
//            break;
//        }
//        int packetStreamIndex = packet.stream_index;
//        if (packetStreamIndex == videoStreamIndex) {
//            int len = avcodec_decode_video2(videoCodecCtx, videoFrame,
//                                            &gotFrame, &packet);
//            if (len < 0) {
//                break;
//            }
//            if (gotFrame) {
//                self->handleVideoFrame();
//            }
//        } else if (packetStreamIndex == audioStreamIndex) {
//            int len = avcodec_decode_audio4(audioCodecCtx, audioFrame,
//                                            &gotFrame, &packet);
//            if (len < 0) {
//                break;
//            }
//            if (gotFrame) {
//                self->handleVideoFrame();
//            }
//
//        }
//    }
//
//
//    //7 处理解码后的裸数据
//
//    //音频裸数据的处理
//    void *audioData;
//    int numFrames;
//    if (swrContext) {
//        int bufSize = av_samples_get_buffer_size(NULL, channels,
//                                                 (int) (audioFrame->nb_samples * channels),
//                                                 AV_SAMPLE_FMT_S16, 1);
//        if (!_swrBuffer || _swrBufferSize < bufSize) {
//            swrBufferSize = bufSize;
//            swrBuffer = realloc(_swrBuffer, _swrBufferSize);
//        }
//        Byte *outbuf[2] = {_swrBuffer, 0};
//        numFrames = swr_convert(_swrContext, outbuf,
//                                (int) (audioFrame->nb_samples * channels),
//                                (const uint8_t **) _audioFrame->data,
//                                audioFrame->nb_samples);
//        audioData = swrBuffer;
//    } else {
//        audioData = audioFrame->data[0];
//        numFrames = audioFrame->nb_samples;
//    }
//
//    //视频裸数据的处理
//    uint8_t *luma;
//    uint8_t *chromaB;
//    uint8_t *chromaR;
//    if (videoCodecCtx->pix_fmt == AV_PIX_FMT_YUV420P ||
//        videoCodecCtx->pix_fmt == AV_PIX_FMT_YUVJ420P) {
//        luma = copyFrameData(videoFrame->data[0],
//                             videoFrame->linesize[0],
//                             videoCodecCtx->width,
//                             videoCodecCtx->height);
//        chromaB = copyFrameData(videoFrame->data[1],
//                                videoFrame->linesize[1],
//                                videoCodecCtx->width / 2,
//                                videoCodecCtx->height / 2);
//        chromaR = copyFrameData(videoFrame->data[2],
//                                videoFrame->linesize[2],
//                                videoCodecCtx->width / 2,
//                                videoCodecCtx->height / 2);
//    } else {
//        sws_scale(_swsContext,
//
//    }
//    (const uint8_t **) videoFrame->data,
//            videoFrame->linesize,
//            0,
//            videoCodecCtx->height,
//            picture.data,
//            picture.linesize);
//
//    luma = copyFrameData(picture.data[0],
//                         picture.linesize[0],
//                         videoCodecCtx->width,
//                         videoCodecCtx->height);
//    chromaB = copyFrameData(picture.data[1],
//                            picture.linesize[1],
//                            videoCodecCtx->width / 2,
//                            videoCodecCtx->height / 2);
//    chromaR = copyFrameData(picture.data[2],
//                            picture.linesize[2],
//                            videoCodecCtx->width / 2,
//                            videoCodecCtx->height / 2);
//
//    //8 关闭所有资源
//
//    //关闭音频资源
//    if (swrBuffer) {
//        free(swrBuffer);
//        swrBuffer = NULL;
//        swrBufferSize = 0;
//    }
//    if (swrContext) {
//        swr_free(&swrContext);
//        swrContext = NULL;
//    }
//    if (audioFrame) {
//        av_free(audioFrame);
//        audioFrame = NULL;
//    }
//    if (audioCodecCtx) {
//        avcodec_close(audioCodecCtx);
//        audioCodecCtx = NULL;
//    }
//    //关闭视频资源
//    if (swsContext) {
//        sws_freeContext(swsContext);
//        swsContext = NULL;
//
//    }
//    if (pictureValid) {
//        avpicture_free(&picture);
//        pictureValid = false;
//    }
//    if (videoFrame) {
//        av_free(videoFrame);
//        videoFrame = NULL;
//    }
//    if (videoCodecCtx) {
//        avcodec_close(videoCodecCtx);
//        videoCodecCtx = NULL;
//    }
//
//    //关闭连接资源
//    if (formatCtx) {
//        avformat_close_input(&formatCtx);
//        formatCtx = NULL;
//    }
}


bool stop = false;

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_MainActivity_playAudioTest(JNIEnv *env, jobject thiz, jstring audioInputPath) {

    if (audioInputPath == NULL) return;
    int result = -1;
    const char *audioPath = env->GetStringUTFChars(audioInputPath, NULL);

    //第一步先注册
    avcodec_register_all();

    //打开媒体文件源
    AVFormatContext *formatCtx = avformat_alloc_context();
    result = avformat_open_input(&formatCtx, audioPath, NULL, NULL);
    avformat_find_stream_info(formatCtx, NULL);

    //找到音频数据流
    int audioStreamIndex = -1;
    for (int i = 0; i < formatCtx->nb_streams; i++) {
        AVStream *stream = formatCtx->streams[i];
        if (AVMEDIA_TYPE_AUDIO == stream->codec->codec_type) {
            audioStreamIndex = i;
        }
    }
    //打开音频解码器
    AVStream *audioStream = formatCtx->streams[audioStreamIndex];
    AVCodecContext *codecContext = audioStream->codec;
    AVCodec *codec = avcodec_find_decoder(codecContext->codec_id);
    if (!codec) {
        result = 10000;
    }
    if (avcodec_open2(audioStream->codec, codec, NULL) < 0) {
        result = 10001;
    }

    SwrContext * swrContext;

    int dataSize = av_samples_get_buffer_size(NULL, av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO) , codecContext->frame_size,AV_SAMPLE_FMT_S16, 0);

    uint8_t *resampleOutBuffer = (uint8_t *) malloc(dataSize);

    //判断是否需要重采样
    if (codecContext->sample_fmt != AV_SAMPLE_FMT_S16) {

        __android_log_print(ANDROID_LOG_INFO, TAG, "11111");
        /**
          * 初始化resampler
          * @param s               Swr context, can be NULL
          * @param out_ch_layout   output channel layout (AV_CH_LAYOUT_*)
          * @param out_sample_fmt  output sample format (AV_SAMPLE_FMT_*).
          * @param out_sample_rate output sample rate (frequency in Hz)
          * @param in_ch_layout    input channel layout (AV_CH_LAYOUT_*)
          * @param in_sample_fmt   input sample format (AV_SAMPLE_FMT_*).
          * @param in_sample_rate  input sample rate (frequency in Hz)
          * @param log_offset      logging level offset
          * @param log_ctx         parent logging context, can be NULL
          */
         swrContext = swr_alloc_set_opts(NULL,
                           av_get_default_channel_layout(2), AV_SAMPLE_FMT_S16, codecContext->sample_rate,
                           av_get_default_channel_layout(codecContext->channels), codecContext->sample_fmt, codecContext->sample_rate,
                           0, NULL);
        if (!swrContext || swr_init(swrContext)) {
            if (swrContext) {
                swr_free(&swrContext);
            }
            avcodec_close(codecContext);
        } else {
            result = 10002;
        }
    }
    //数据包
    AVFrame *frame;
    AVPacket *pkt;

    frame = av_frame_alloc();
    pkt = av_packet_alloc();

    jclass jAudioTrackClass = env->FindClass("android/media/AudioTrack");
    jmethodID jAudioTrackCMid = env->GetMethodID(jAudioTrackClass,"<init>","(IIIIII)V"); //构造

    //  public static final int STREAM_MUSIC = 3;
    int streamType = 3;
    int sampleRateInHz = 44100;
    // public static final int CHANNEL_OUT_STEREO = (CHANNEL_OUT_FRONT_LEFT | CHANNEL_OUT_FRONT_RIGHT);
    int channelConfig = (0x4 | 0x8);
    // public static final int ENCODING_PCM_16BIT = 2;
    int audioFormat = 2;
    // getMinBufferSize(int sampleRateInHz, int channelConfig, int audioFormat)
    jmethodID jGetMinBufferSizeMid = env->GetStaticMethodID(jAudioTrackClass, "getMinBufferSize", "(III)I");
    int bufferSizeInBytes = env->CallStaticIntMethod(jAudioTrackClass, jGetMinBufferSizeMid, sampleRateInHz, channelConfig, audioFormat);
    // public static final int MODE_STREAM = 1;
    int mode = 1;

    //创建了AudioTrack
    jobject jAudioTrack = env->NewObject(jAudioTrackClass,jAudioTrackCMid, streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);

    //play方法
    jmethodID jPlayMid = env->GetMethodID(jAudioTrackClass,"play","()V");
    env->CallVoidMethod(jAudioTrack,jPlayMid);

    // write method
    jmethodID jAudioTrackWriteMid = env->GetMethodID(jAudioTrackClass, "write", "([BII)I");

    stop = false;
    while (av_read_frame(formatCtx, pkt) >= 0 && !stop) {
        if(pkt -> stream_index == audioStreamIndex){

            //发送数据包到解码器
            avcodec_send_packet(codecContext,pkt);

            //清理
            av_packet_unref(pkt);

            for (;;){
                long currentTime = getCurrentTime();
                int re = avcodec_receive_frame(codecContext, frame);
                if (re != 0) {
                    break;
                } else {
                    if (swrContext) {
                        swr_convert(swrContext,
                                    &resampleOutBuffer, frame->nb_samples,
                                    (const u_int8_t **) frame->data, frame->nb_samples);
                        __android_log_print(ANDROID_LOG_INFO, TAG, "222222");
                    } else {
                        __android_log_print(ANDROID_LOG_INFO, TAG, "333333");
                        resampleOutBuffer = *frame->data;
                    }

                    long duration = getCurrentTime() - currentTime;
                    __android_log_print(ANDROID_LOG_INFO, TAG, "duration:%d", duration);
                    jbyteArray jPcmDataArray = env->NewByteArray(dataSize);
                    jbyte *jPcmData = env->GetByteArrayElements(jPcmDataArray, NULL);

                    memcpy(jPcmData, resampleOutBuffer, dataSize);

                    env -> ReleaseByteArrayElements(jPcmDataArray, jPcmData, 0);

                    env -> CallIntMethod(jAudioTrack, jAudioTrackWriteMid, jPcmDataArray, 0, dataSize);
                    env -> DeleteLocalRef(jPcmDataArray);
                }
            }
        }
    }
    avcodec_free_context(&codecContext);
    av_frame_free(&frame);
    av_packet_free(&pkt);

    avformat_free_context(formatCtx);

    env->ReleaseStringUTFChars(audioInputPath, audioPath);
}

AudioDecoder *audioDecoder;


extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_MainActivity_stopAudioTest(JNIEnv *env, jobject thiz) {
    stop = true;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_getMusicMeta(JNIEnv *env, jobject thiz, jstring music_path, jintArray meta_array) {
    const char* audioPath = env -> GetStringUTFChars(music_path,NULL);
    jint* metaArray = env -> GetIntArrayElements(meta_array,NULL);
    audioDecoder = new AudioDecoder();
    int result = audioDecoder -> initAudioDecoder(audioPath,metaArray);
    env->ReleaseIntArrayElements(meta_array, metaArray, NULL);
    env->ReleaseStringUTFChars(music_path, audioPath);
    return result == 0;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_closeFile(JNIEnv *env, jobject thiz) {
    if(NULL != audioDecoder) {
        audioDecoder->destroy();
        delete audioDecoder;
        audioDecoder = NULL;
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_prepareDecoder(JNIEnv *env, jobject thiz) {
    if(NULL != audioDecoder) {
        audioDecoder->prepare();
    }
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_audio_study_ffmpegdecoder_audiotracke_AudioDecoderImpl_readSamples(JNIEnv *env, jobject thiz, jshortArray samples, jint size) {
    if(NULL != audioDecoder) {
        short * samplesArray = env->GetShortArrayElements(samples,NULL);
        int result = audioDecoder->readSapmles(samplesArray, size);
//        int result = audioDecoder->readSapmlesAndPlay(samplesArray, size,env);
        env->ReleaseShortArrayElements(samples, samplesArray, 0);
        return result;
    }
}
