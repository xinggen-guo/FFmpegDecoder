//
// Created by guoxinggen on 2022/7/6.
//

#include "MyGLRenderContext.h"


MyGLRenderContext::MyGLRenderContext() {
    LOGI("MyGLRenderContext");
    currentSample = new GLTriangleSample();
}

MyGLRenderContext::~MyGLRenderContext() {
    LOGI("~MyGLRenderContext");
    if(NULL != currentSample){
        delete currentSample;
        currentSample = NULL;
    }
}

void MyGLRenderContext::surfaceCreate() {
    LOGI("surfaceCreate");
    glClearColor(1.0f,1.0f,1.0f, 1.0f);
}

void MyGLRenderContext::surfaceChange(int width, int height) {
    LOGI("surfaceChange--->width:%1d---->height:%2d", width, height);
    glViewport(0, 0, width, height);
    m_ScreenW = width;
    m_ScreenH = height;
}

void MyGLRenderContext::drawFrame() {
    LOGI("drawFrame");

    glClear(GL_DEPTH_BUFFER_BIT | GL_COLOR_BUFFER_BIT);
    if (NULL != currentSample){
        currentSample->init();
        currentSample->draw(m_ScreenW, m_ScreenH);
    }
}

void MyGLRenderContext::destroy() {
    LOGI("destroy");
    if (NULL != currentSample){
        delete currentSample;
        currentSample = NULL;
    }
}
