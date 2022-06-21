//
// Created by guoxinggen on 2022/6/8.
//


#include "audio_decoder.h"
#include "../common/CommonTools.h"
#include <zlib.h>
#include <jni.h>
#include <android/log.h>
#include <exception>
#include <iostream>

int AudioDecoder::initAudioDecoder(const char *string) {

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
    int bufferSize = sampleRate * CHANNEL_PER_FRAME
                                   * BITS_PER_CHANNEL / BITS_PER_BYTE;
    packetBufferSize = bufferSize / 2 * 0.2;
    duration = avFormatContext->duration;

    LOGI("initAudioDecoder---->sampleRate:%1d---->packetBufferSize:%2d---->frame_size:%3d", sampleRate, packetBufferSize, avCodecContext->frame_size);
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

int AudioDecoder::getSampleRate() {
    return sampleRate;
}

int AudioDecoder::getDuration() {
    return duration;
}

int AudioDecoder::getPacketBufferSize() {
    return packetBufferSize;
}

void AudioDecoder::prepare() {
    avPacket = av_packet_alloc();
    avFrame = av_frame_alloc();
}

AudioPacket* AudioDecoder::decoderAudioPacket() {
    short *resample = new short[packetBufferSize];
    int stereoSampleSize = readSampleData(resample, packetBufferSize);
    AudioPacket *audioPacket = new AudioPacket();
    if (stereoSampleSize > 0) {
        audioPacket->audioBuffer = resample;
        audioPacket->audioSize = stereoSampleSize;
    } else {
        audioPacket->audioSize = -1;
    }
    return audioPacket;
}


int AudioDecoder::readSampleData(short *samples, int size) {
    LOGI("readSampleData------start---->size:%d",size);
    int realSamplesSize = size;
    while (size > 0){
        if(audioBufferCursor < audioBufferSize){
            LOGI("readSampleData------while---->audioBufferSize:%1d----audioBufferCursor:%2d",audioBufferSize,audioBufferCursor);
            int audioBufferDataSize = audioBufferSize - audioBufferCursor;
            LOGI("readSampleData----->:%d",size);
            int copySize = MIN(size, audioBufferDataSize);
            memcpy(samples + (realSamplesSize - size), audioBuffer + audioBufferCursor, copySize * 2);
            size -= copySize;
            audioBufferCursor += copySize;
        } else {
            if (readFrame() < 0) {
                break;
            }
        }
        LOGI("readSampleData------>while---size:%d",size);
    }
    int fillSize = realSamplesSize - size;
    LOGI("readSampleData------>end---fillSize:%d",fillSize);
    if(fillSize == 0){
        return -1;
    }
    return fillSize;
}

int AudioDecoder::readFrame() {
    LOGI("decoderAudioPacket--0000000");
    int ret = 0;
    avPacket = av_packet_alloc();
    if (av_read_frame(avFormatContext, avPacket) >= 0) {
        if (avPacket->stream_index == audioIndex) {
            avcodec_send_packet(avCodecContext, avPacket);
            LOGI("decoderAudioPacket--->avPacketSize:%d",avPacket->size);
            av_packet_unref(avPacket);
            int re = avcodec_receive_frame(avCodecContext, avFrame);
            LOGI("decoderAudioPacket--111111->re:%1d",re);
            if (re != 0) {
                ret = -1;
            } else {
                int numChannels = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);
                int numFrames = 0;
                int size = av_samples_get_buffer_size(NULL, numChannels, avFrame->nb_samples * numChannels,AV_SAMPLE_FMT_S16, 1);
                uint8_t *resampleOutBuffer = (uint8_t *) malloc(size);
                if (swrContext) {
                    numFrames = swr_convert(swrContext,
                                            &resampleOutBuffer, avFrame->nb_samples * numChannels,
                                            (const u_int8_t **) avFrame->data, avFrame->nb_samples);
                } else {
                    resampleOutBuffer = *avFrame->data;
                    numFrames = avFrame->nb_samples;
                }
                audioBuffer =  (short*) resampleOutBuffer;
                audioBufferCursor = 0;
                audioBufferSize = numFrames * numChannels;
                LOGI("decoderAudioPacket--33333---->audioBufferSize:%1d---->numFrames:%2d---->size:%3d", audioBufferSize, numFrames, size);
            }
        }
    } else {
        ret = -1;
    }
    LOGI("decoderAudioPacket--444444");
    av_packet_free(&avPacket);
    return ret;
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
    if (NULL != avPacket) {
        av_packet_free(&avPacket);
        avPacket = NULL;
    }
    if (NULL != avFrame) {
        av_frame_free(&avFrame);
        avFrame = NULL;
    }
    if(NULL != avCodecContext) {
        avcodec_close(avCodecContext);
        avCodecContext = NULL;
    }
    if(NULL != avFormatContext) {
        avformat_close_input(&avFormatContext);
        avFormatContext = NULL;
    }
}


