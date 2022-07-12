//
// Created by guoxinggen on 2022/7/6.
//

#ifndef FFMPEGDECODER_MYGLRENDERCONTEXT_H
#define FFMPEGDECODER_MYGLRENDERCONTEXT_H

#include <GLES3/gl3.h>
#include <CommonTools.h>
#include "GLSampleBase.h"
#include "GLTriangleSample.h"

#define LOG_TAG "MyGLRenderContext"

class MyGLRenderContext {

public:

    MyGLRenderContext();

    ~MyGLRenderContext();

    void surfaceCreate();

    void surfaceChange(int width, int height);

    void drawFrame();

    void destroy();

    void setRenderType(int renderType);

    void setImageData(int format, int width, int height, uint8_t *imageData);

private:
    int m_ScreenW;
    int m_ScreenH;
    int currentType;
    GLSampleBase* currentSample;
};


#endif //FFMPEGDECODER_MYGLRENDERCONTEXT_H
