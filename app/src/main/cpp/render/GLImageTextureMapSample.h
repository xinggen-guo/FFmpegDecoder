//
// Created by guoxinggen on 2022/7/12.
//

#ifndef FFMPEGDECODER_GLIMAGETEXTUREMAPSAMPLE_H
#define FFMPEGDECODER_GLIMAGETEXTUREMAPSAMPLE_H

#include "GLSampleBase.h"
#include <CommonTools.h>


#define LOG_TAG "GLImageTextureMapSample"
class GLImageTextureMapSample : public GLSampleBase{

public:
    GLImageTextureMapSample();

    virtual ~GLImageTextureMapSample();

    virtual void init();

    virtual void loadImageData(NativeImage *pImage);

    virtual void setImageScale(float imageScale);

    virtual void draw(int screenW, int screenH);

    virtual void destroy();


private:
private:
    GLuint m_TextureId;
    GLint m_SamplerLoc;
    NativeImage m_RenderImage;
};


#endif //FFMPEGDECODER_GLIMAGETEXTUREMAPSAMPLE_H
