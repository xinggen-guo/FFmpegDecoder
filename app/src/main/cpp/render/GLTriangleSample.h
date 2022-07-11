//
// Created by guoxinggen on 2022/7/6.
//

#ifndef FFMPEGDECODER_GLTRIANGLESAMPLE_H
#define FFMPEGDECODER_GLTRIANGLESAMPLE_H

#define LOG_TAG = "GLTriangleSample"

#include "GLSampleBase.h"

class GLTriangleSample : public GLSampleBase {

public:
    virtual void init();

    virtual void draw(int screenW, int screenH);

    virtual void destroy();
};
#endif //FFMPEGDECODER_GLTRIANGLESAMPLE_H
