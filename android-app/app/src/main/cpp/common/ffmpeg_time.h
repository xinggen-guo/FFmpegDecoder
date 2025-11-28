//
// Created by xinggen guo on 2025/11/16.
//

#include <time.h>
#include <stdint.h>

#ifndef FFMPEGDECODER_TIME_H
#define FFMPEGDECODER_TIME_H

static int64_t nowMonotonicMs() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000LL + ts.tv_nsec / 1000000LL;
}

#endif //FFMPEGDECODER_TIME_H
