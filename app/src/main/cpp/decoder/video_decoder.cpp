//
// Created by xinggen guo on 2025/11/14.
//

#include "video_decoder.h"

VideoDecoder::VideoDecoder() = default;

VideoDecoder::~VideoDecoder() {
    close();
}

int VideoDecoder::open(const char* path) {
    close();

    int ret = 0;

    // open container
    if ((ret = avformat_open_input(&fmtCtx, path, nullptr, nullptr)) < 0) {
        return ret;
    }
    if ((ret = avformat_find_stream_info(fmtCtx, nullptr)) < 0) {
        return ret;
    }

    // find best video stream
    videoStreamIndex = av_find_best_stream(fmtCtx, AVMEDIA_TYPE_VIDEO, -1, -1, nullptr, 0);
    if (videoStreamIndex < 0) {
        return videoStreamIndex;
    }

    videoStream = fmtCtx->streams[videoStreamIndex];
    AVCodecParameters* codecpar = videoStream->codecpar;
    const AVCodec* codec = avcodec_find_decoder(codecpar->codec_id);
    if (!codec) {
        return AVERROR_DECODER_NOT_FOUND;
    }

    codecCtx = avcodec_alloc_context3(codec);
    if (!codecCtx) return AVERROR(ENOMEM);

    if ((ret = avcodec_parameters_to_context(codecCtx, codecpar)) < 0) {
        return ret;
    }

    if ((ret = avcodec_open2(codecCtx, codec, nullptr)) < 0) {
        return ret;
    }

    width = codecCtx->width;
    height = codecCtx->height;

    frame = av_frame_alloc();
    packet = av_packet_alloc();

    return 0;
}

void VideoDecoder::close() {
    if (swsCtx) {
        sws_freeContext(swsCtx);
        swsCtx = nullptr;
    }

    if (frame) {
        av_frame_free(&frame);
        frame = nullptr;
    }
    if (packet) {
        av_packet_free(&packet);
        packet = nullptr;
    }
    if (codecCtx) {
        avcodec_free_context(&codecCtx);
        codecCtx = nullptr;
    }
    if (fmtCtx) {
        avformat_close_input(&fmtCtx);
        fmtCtx = nullptr;
    }

    videoStream = nullptr;
    videoStreamIndex = -1;
    width = height = 0;
}

int VideoDecoder::decodeFrame() {
    int ret = 0;

    // read packets until we get a decoded frame or hit EOF
    while (true) {
        ret = av_read_frame(fmtCtx, packet);
        if (ret < 0) {
            // flush decoder
            avcodec_send_packet(codecCtx, nullptr);
        } else if (packet->stream_index != videoStreamIndex) {
            av_packet_unref(packet);
            continue;
        } else {
            ret = avcodec_send_packet(codecCtx, packet);
            av_packet_unref(packet);
            if (ret < 0) {
                return ret;
            }
        }

        ret = avcodec_receive_frame(codecCtx, frame);
        if (ret == AVERROR(EAGAIN)) {
            continue; // need more data
        } else if (ret == AVERROR_EOF) {
            return 0; // EOF
        } else if (ret < 0) {
            return ret;
        }

        // got one frame
        return 1;
    }
}

int VideoDecoder::toRGBA(uint8_t* outBuffer, int bufferSize) {
    if (!frame || !codecCtx || width <= 0 || height <= 0) return -1;
    int needed = width * height * 4;
    if (bufferSize < needed) return -1;

    if (!swsCtx) {
        swsCtx = sws_getContext(
                width, height, codecCtx->pix_fmt,
                width, height, AV_PIX_FMT_RGBA,
                SWS_BILINEAR, nullptr, nullptr, nullptr
        );
        if (!swsCtx) return -1;
    }

    uint8_t* dstData[4] = { outBuffer, nullptr, nullptr, nullptr };
    int dstLinesize[4] = { width * 4, 0, 0, 0 };

    sws_scale(
            swsCtx,
            frame->data,
            frame->linesize,
            0,
            height,
            dstData,
            dstLinesize
    );

    return needed;  // bytes written
}

double VideoDecoder::getFramePtsMs() const {
    if (!videoStream || !frame) return 0.0;
    AVRational tb = videoStream->time_base;
    double pts = (frame->best_effort_timestamp == AV_NOPTS_VALUE)
                 ? 0.0
                 : frame->best_effort_timestamp * av_q2d(tb);
    return pts * 1000.0; // ms
}