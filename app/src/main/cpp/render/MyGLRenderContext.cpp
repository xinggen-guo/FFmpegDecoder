//
// Created by guoxinggen on 2022/7/6.
//

#include "MyGLRenderContext.h"
#include "GLRectangleSample.h"


MyGLRenderContext::MyGLRenderContext() {
    LOGI("MyGLRenderContext");
    currentType = SAMPLE_TYPE_KEY_TRIANGLE;
    currentSample = new GLTriangleSample();
}

MyGLRenderContext::~MyGLRenderContext() {
    LOGI("~MyGLRenderContext");
    if (NULL != currentSample) {
        delete currentSample;
        currentSample = NULL;
    }
}

void MyGLRenderContext::surfaceCreate() {
    LOGI("surfaceCreate");
    glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
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
    if (NULL != currentSample) {
        currentSample->init();
        currentSample->draw(m_ScreenW, m_ScreenH);
    }
}

void MyGLRenderContext::destroy() {
    LOGI("destroy");
    if (NULL != currentSample) {
        currentSample->destroy();
        delete currentSample;
        currentSample = NULL;
    }
}

void MyGLRenderContext::setRenderType(int renderType) {
    if (currentType == renderType) return;
    if (currentSample != NULL) {
        currentSample->destroy();
        delete currentSample;
        currentSample = NULL;
    }
    switch (renderType) {
        case SAMPLE_TYPE_KEY_TRIANGLE:
            currentSample = new GLTriangleSample();
            break;
        case SAMPLE_TYPE_KEY_RECTANGLE:
            currentSample = new GLRectangleSample();
            break;
        default:
            currentSample = new GLTriangleSample();
            break;

    }
    currentType = renderType;
}
