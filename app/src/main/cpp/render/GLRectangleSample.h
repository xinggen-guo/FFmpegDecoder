//
// Created by guoxinggen on 2022/7/8.
//

#ifndef FFMPEGDECODER_GLRECTANGLESAMPLE_H
#define FFMPEGDECODER_GLRECTANGLESAMPLE_H

#include "GLSampleBase.h"

#define LOG_TAG "GLRectangleSample"
class GLRectangleSample: public GLSampleBase{

public:
    virtual void init();

    virtual void draw(int screenW, int screenH);

    virtual void destroy();
};


#endif //FFMPEGDECODER_GLRECTANGLESAMPLE_H
