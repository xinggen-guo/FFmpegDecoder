//
// Created by guoxinggen on 2022/6/8.
//


#include "audio_decoder.h"
#include <zlib.h>
#include <jni.h>
#include <android/log.h>

const char *TAG1 = "AudioDecoderCortronller";

#define LOGI(...)  __android_log_print(ANDROID_LOG_INFO, TAG1, __VA_ARGS__)

int AudioDecoder::initAudioDecoder(const char *string, int *metaArray) {

    int result = 0;

    //1 注册所有解码器
    avcodec_register_all();

    avFormatContext = avformat_alloc_context();

    result = avformat_open_input(&avFormatContext, string, NULL, NULL);
    if (result != 0) {
        return -1;
    }

    avformat_find_stream_info(avFormatContext, NULL);

    //找到音频数据流
    for (int i = 0; i < avFormatContext->nb_streams; i++) {
        AVStream *stream = avFormatContext->streams[i];
        if (AVMEDIA_TYPE_AUDIO == stream->codec->codec_type) {
            audioIndex = i;
        }
    }

    if (audioIndex == AVERROR_STREAM_NOT_FOUND) {
        return -1;
    }

    AVStream *audioStream = avFormatContext->streams[audioIndex];

    avCodecContext = audioStream->codec;
    AVCodec *avCodec = avcodec_find_decoder(avCodecContext->codec_id);
    if (avCodec == NULL) {
        return -1;
    }
    result = avcodec_open2(avCodecContext, avCodec, NULL);
    if (result != 0) {
        return -1;
    }

    sampleRate = avCodecContext->sample_rate;

//    int dataSize = sampleRate * AV_SAMPLE_FMT_S16 * 2;
    int dataSize = av_samples_get_buffer_size(NULL, av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO) , avCodecContext->frame_size,AV_SAMPLE_FMT_S16, 0);

    metaArray[0] = sampleRate;
    metaArray[1] = avCodecContext->bit_rate;
    metaArray[2] = dataSize;

    LOGI("initAudioDecoder---->sampleRate:%1d---->dataSize:%2d---->frame_size:%3d", sampleRate, dataSize, avCodecContext->frame_size);
    //判断需不需要重采样
    if (!audioCodecIsSupported()) {
        swrContext = swr_alloc_set_opts(NULL,
                                        AV_CH_LAYOUT_STEREO, AV_SAMPLE_FMT_S16, sampleRate,
                                        av_get_default_channel_layout(avCodecContext->channel_layout), avCodecContext->sample_fmt, sampleRate,
                                        0, NULL);
        if (!swrContext || swr_init(swrContext)) {
            if (swrContext) {
                swr_free(&swrContext);
            }
            avcodec_close(avCodecContext);
            result = 10002;
        }
    }

    return result;
}

void AudioDecoder::prepare() {
    avPacket = av_packet_alloc();
    avFrame = av_frame_alloc();
}

int AudioDecoder::audioDecoder(short *pInt, int size) {
    if (av_read_frame(avFormatContext, avPacket) >= 0) {
        if (avPacket->stream_index == audioIndex) {
            avcodec_send_packet(avCodecContext, avPacket);
            LOGI("audioDecoder--->avPacketSize:%d",avPacket->size);
            av_packet_unref(avPacket);
            int re = avcodec_receive_frame(avCodecContext, avFrame);
            if (re != 0) {
                return -1;
            } else {
                uint8_t *resampleOutBuffer = (uint8_t *) malloc(size);
                if (swrContext) {
                    swr_convert(swrContext,
                                &resampleOutBuffer, avFrame->nb_samples,
                                (const u_int8_t **) avFrame->data, avFrame->nb_samples);
                } else {
                    resampleOutBuffer = *avFrame->data;
                }
                memcpy(pInt, resampleOutBuffer, size);
                return 0;
            }
        } else {
            return -1;
        }
    } else {
        return -1;
    }
}

bool AudioDecoder::audioCodecIsSupported() {
    if (avCodecContext->sample_fmt == AV_SAMPLE_FMT_S16) {
        return true;
    }
    return false;
}

void AudioDecoder::destroy() {

    if(NULL != swrContext) {
        swr_close(swrContext);
        swr_free(&swrContext);
        swrContext = NULL;
    }
    if(NULL != avCodecContext) {
        avcodec_close(avCodecContext);
        avcodec_free_context(&avCodecContext);
        avCodecContext = NULL;
    }

    if(NULL != avFormatContext) {
        avformat_close_input(&avFormatContext);
        avformat_free_context(avFormatContext);
        avFormatContext = NULL;
    }
}

