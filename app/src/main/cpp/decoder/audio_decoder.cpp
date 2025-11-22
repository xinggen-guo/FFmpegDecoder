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

    // 1. register all decoders
    avcodec_register_all();

    avFormatContext = avformat_alloc_context();

    result = avformat_open_input(&avFormatContext, string, NULL, NULL);
    if (result != 0) {
        return -1;
    }

    avformat_find_stream_info(avFormatContext, NULL);

    // find audio stream
    for (int i = 0; i < avFormatContext->nb_streams; i++) {
        AVStream *stream = avFormatContext->streams[i];
        if (AVMEDIA_TYPE_AUDIO == stream->codec->codec_type) {
            audioIndex = i;
        }
    }

    if (audioIndex == AVERROR_STREAM_NOT_FOUND) {
        return -1;
    }

    audioStream     = avFormatContext->streams[audioIndex];
    time_base       = av_q2d(audioStream->time_base);
    avCodecContext  = audioStream->codec;
    AVCodec *avCodec = avcodec_find_decoder(avCodecContext->codec_id);
    if (avCodec == NULL) {
        return -1;
    }
    result = avcodec_open2(avCodecContext, avCodec, NULL);
    if (result != 0) {
        return -1;
    }

    channels   = avCodecContext->channels;
    sampleRate = avCodecContext->sample_rate;

    // frames per channel in one packet
    int framesPerPacket = static_cast<int>(sampleRate * AUDIO_PACKET_SEC + 0.5f);
    if (framesPerPacket <= 0) {
        framesPerPacket = sampleRate / 25; // fallback ~40 ms for 1 kHz, ~40 ms for 44.1 kHz/48 kHz
    }

    // packetBufferSize = samples in one packet (interleaved stereo)
    packetBufferSize = framesPerPacket * CHANNEL_PER_FRAME;

    // --------------------------------------------------------

    int64_t durationUs = avFormatContext->duration;
    duration = static_cast<int>(durationUs / 1000); // ms

    LOGI("initAudioDecoder---->sampleRate:%d---->packetBufferSize(samples):%d---->"
         "frame_size:%d--->duration(ms):%lld----chanels:%d",
         sampleRate,
         packetBufferSize,
         avCodecContext->frame_size,
         (long long) duration, channels);

    // resample if needed
    if (!audioCodecIsSupported()) {
        swrContext = swr_alloc_set_opts(
                NULL,
                AV_CH_LAYOUT_STEREO, AV_SAMPLE_FMT_S16, sampleRate,
                av_get_default_channel_layout(avCodecContext->channel_layout),
                avCodecContext->sample_fmt, sampleRate,
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

int AudioDecoder::getChannels() {
    return channels;
}

int64_t AudioDecoder::getDuration() {
    return duration;
}

int AudioDecoder::getPacketBufferSize() {
    // number of samples (shorts) per packet
    return packetBufferSize;
}

void AudioDecoder::prepare() {
    avPacket = av_packet_alloc();
    avFrame  = av_frame_alloc();
}

PcmFrame* AudioDecoder::decoderAudioPacket() {
    audioDuration      = 0;
    audioStartPosition = 0;

    // allocate one packet = packetBufferSize samples (interleaved)
    short *resample = new short[packetBufferSize];

    // read up to packetBufferSize samples
    int stereoSampleSize = readSampleData(resample, packetBufferSize);

    PcmFrame *audioPacket = new PcmFrame();
    if (stereoSampleSize > 0) {
        audioPacket->audioBuffer    = resample;
        audioPacket->audioSize      = stereoSampleSize;
        audioPacket->duration       = audioDuration;
        audioPacket->startPosition  = audioStartPosition;
    } else {
        // no data, mark as EOF/error
        delete[] resample;
        audioPacket->audioBuffer = nullptr;
        audioPacket->audioSize   = -1;
    }
    return audioPacket;
}

void AudioDecoder::seek(const long seek_time) {
    time_seek = seek_time;
    seekFrame();
}

int AudioDecoder::readSampleData(short *samples, int size) {
    // size is requested samples (interleaved)
    int realSamplesSize = size;
    while (size > 0) {
        if (audioBufferCursor < audioBufferSize) {
            int audioBufferDataSize = audioBufferSize - audioBufferCursor;
            int copySize = MIN(size, audioBufferDataSize);

            // copy short samples → multiply by 2 for bytes
            memcpy(samples + (realSamplesSize - size),
                   audioBuffer + audioBufferCursor,
                   copySize * sizeof(short));

            size -= copySize;
            audioBufferCursor += copySize;
        } else {
            if (readFrame() < 0) {
                break;
            }
        }
    }
    int fillSize = realSamplesSize - size;
    if (fillSize == 0) {
        return -1;
    }
    return fillSize;
}

int AudioDecoder::readFrame() {
    int ret = 0;
    avPacket = av_packet_alloc();
    if (av_read_frame(avFormatContext, avPacket) >= 0) {
        if (avPacket->stream_index == audioIndex) {
            avcodec_send_packet(avCodecContext, avPacket);
            av_packet_unref(avPacket);
            int re = avcodec_receive_frame(avCodecContext, avFrame);
            if (re != 0) {
                ret = -1;
            } else {
                int numChannels = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);
                int numFrames = 0;
                int size = av_samples_get_buffer_size(
                        NULL,
                        numChannels,
                        avFrame->nb_samples * numChannels,
                        AV_SAMPLE_FMT_S16,
                        1);
                uint8_t *resampleOutBuffer = (uint8_t *) malloc(size);
                if (swrContext) {
                    numFrames = swr_convert(
                            swrContext,
                            &resampleOutBuffer, avFrame->nb_samples * numChannels,
                            (const u_int8_t **) avFrame->data, avFrame->nb_samples);
                } else {
                    resampleOutBuffer = *avFrame->data;
                    numFrames = avFrame->nb_samples;
                }
                audioBuffer       = (short*) resampleOutBuffer;
                audioBufferCursor = 0;
                audioBufferSize   = numFrames * numChannels;  // samples

                audioDuration += av_frame_get_pkt_duration(avFrame) * time_base;
                if (audioStartPosition == 0) {
                    audioStartPosition = avFrame->pts * time_base;
                }
            }
        }
    } else {
        ret = -1;
    }
    av_packet_free(&avPacket);
    return ret;
}

void AudioDecoder::seekFrame() {
    LOGI("seekFrame--start");

    if (time_seek >= 0 && audioStream != nullptr) {
        // time_seek: milliseconds → convert ms → stream time_base
        AVRational srcTimeBase = {1, 1000};              // ms
        AVRational dstTimeBase = audioStream->time_base; // stream time_base

        int64_t seek_pts = av_rescale_q(time_seek, srcTimeBase, dstTimeBase);

        int ret = av_seek_frame(avFormatContext,
                                audioIndex,
                                seek_pts,
                                AVSEEK_FLAG_BACKWARD);

        if (ret < 0) {
            LOGI("seekFrame-- failed! time_position=%f s",
                 (double) time_seek / 1000.0);
        } else {
            LOGI("seekFrame-- success, time_position=%f s",
                 (double) time_seek / 1000.0);
        }

        // flush decoder buffers after seek
        if (avCodecContext) {
            avcodec_flush_buffers(avCodecContext);
        }

        time_seek = -1;
    }

    LOGI("seekFrame--end");
}

bool AudioDecoder::audioCodecIsSupported() {
    return avCodecContext->sample_fmt == AV_SAMPLE_FMT_S16;
}

void AudioDecoder::destroy() {
    if (avCodecContext) {
        avcodec_close(avCodecContext);
        avCodecContext = nullptr;
    }
    if (avFormatContext) {
        avformat_close_input(&avFormatContext);
        avFormatContext = nullptr;
    }
    audioStream = nullptr;

    swrContext = nullptr;
    avPacket   = nullptr;
    avFrame    = nullptr;
}