//
// Created by guoxinggen on 2022/7/6.
//

#ifndef FFMPEGDECODER_GLSAMPLEBASE_H
#define FFMPEGDECODER_GLSAMPLEBASE_H

#define SAMPLE_TYPE                             200
#define SAMPLE_TYPE_KEY_TRIANGLE                SAMPLE_TYPE + 1

#include <GLUtils.h>

class GLSampleBase {

public:
    GLSampleBase() {
        m_ProgramObj = 0;
        m_VertexShader = 0;
        m_FragmentShader = 0;

        m_SurfaceWidth = 0;
        m_SurfaceHeight = 0;
    }

    ~GLSampleBase() {
        m_ProgramObj = 0;
        m_VertexShader = 0;
        m_FragmentShader = 0;

        m_SurfaceWidth = 0;
        m_SurfaceHeight = 0;
    }

    virtual void init() = 0;
    virtual void draw(int width, int height) = 0;
    virtual void destroy() = 0;

protected:
    GLuint m_VertexShader;
    GLuint m_FragmentShader;
    GLuint m_ProgramObj;
    int m_SurfaceWidth;
    int m_SurfaceHeight;
};

#endif //FFMPEGDECODER_GLSAMPLEBASE_H
