//
// Created by xinggen guo on 2025/11/15.
//

#pragma once

#include <cstdint>

struct VideoFrame {
    int width = 0;
    int height = 0;
    double ptsMs = 0.0;      // presentation timestamp in ms

    int dataSize = 0;        // bytes in buffer
    uint8_t* data = nullptr; // RGBA data (width * height * 4)

    bool eof = false;        // true when this is an EOF marker

    VideoFrame() = default;
    ~VideoFrame() = default;
};
