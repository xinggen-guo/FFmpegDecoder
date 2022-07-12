//
// Created by guoxinggen on 2022/7/6.
//


#include "GLTriangleSample.h"

void GLTriangleSample::init() {
    if(m_ProgramObj != 0)
        return;
    char vShaderStr[] =
            "#version 300 es                                    \n"
            "layout(location = 0) in vec3 position;             \n"
            "out vec4 vertexColor;                              \n"
            "void main()                                        \n"
            "{                                                  \n"
            "   gl_Position = vec4(position,1.0f);              \n"
            "   vertexColor = vec4(0.5f, 0.0f, 0.0f, 1.0f);     \n"
            "}                                                  \n";

    char fShaderStr[] =
            "#version 300 es                                \n"
            "in vec4 vertexColor;                           \n"
            "out vec4 color;                                \n"
            "void main()                                    \n"
            "{                                              \n"
            "   color = vertexColor;                           \n"
            "}                                              \n";

    m_ProgramObj = GLUtils::CreateProgram(vShaderStr, fShaderStr, m_VertexShader, m_FragmentShader);
}

void GLTriangleSample::draw(int width, int height) {

    m_SurfaceHeight = height;
    m_SurfaceWidth = width;

    LOGI("TriangleSample::Draw");
    GLfloat vVertices[] = {
            0.0f,  0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
            0.5f, -0.5f, 0.0f,
    };

    if(m_ProgramObj == 0)
        return;
    glClear(GL_STENCIL_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glClearColor(1.0, 1.0, 1.0, 1.0);

//    GLint vertexColorLocation = glGetUniformLocation(m_ProgramObj,"ourColor");

    // Use the program object
    glUseProgram (m_ProgramObj);

//    glUniform4f(vertexColorLocation, 0.0f, 1.0f, 0.0f, 0.0f);

    // Load the vertex data
    glVertexAttribPointer (0, 3, GL_FLOAT, GL_FALSE, 0, vVertices );
    glEnableVertexAttribArray (0);

    glDrawArrays (GL_TRIANGLES, 0, 3);
    glUseProgram (GL_NONE);

}

void GLTriangleSample::destroy() {
    if (m_ProgramObj)
    {
        glDeleteProgram(m_ProgramObj);
        m_ProgramObj = GL_NONE;
    }
}