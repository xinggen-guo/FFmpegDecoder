//
// Created by guoxinggen on 2022/7/8.
//

#include "GLRectangleSample.h"

void GLRectangleSample::init() {
    LOGI("init");
    if (m_ProgramObj != 0)
        return;
    char vShaderStr[] =
            "#version 300 es                                    \n"
            "layout(location = 0) in vec3 position;             \n"
            "layout(location = 1) in vec4 a_color;                \n"
            "out vec4 vertexColor;                              \n"
            "void main()                                        \n"
            "{                                                  \n"
            "   gl_Position = vec4(position,1.0f);              \n"
            "   vertexColor = a_color;                          \n"
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


void GLRectangleSample::draw(int screenW, int screenH) {
    LOGI("TriangleSample::Draw");

//    GLfloat color_triangles[12 * 4] = {  //for 1 color_triangles
//            1.0f, 0.0f, 0.0f, 1.0f,
//            0.0f, 1.0f, 0.0f, 1.0f,
//            0.0f, 0.0f, 1.0f, 1.0f,
//
//            1.0f, 0.0f, 0.0f, 1.0f,
//            0.0f, 1.0f, 0.0f, 1.0f,
//            0.0f, 0.0f, 1.0f, 1.0f,
//    };


    GLfloat color_strip[4 * 4] = {  //for 2 color_strip
            1.0f, 0.0f, 0.0f, 1.0f,
            0.0f, 1.0f, 0.0f, 1.0f,
            0.0f, 0.0f, 1.0f, 1.0f,
            1.0f, 0.0f, 0.0f, 1.0f,
    };

//    GLfloat color_fan[12 * 4] = {   // for 3 color_fan
//            1.0f, 0.0f, 0.0f, 1.0f,
//            0.0f, 1.0f, 0.0f, 1.0f,
//            0.0f, 0.0f, 1.0f, 1.0f,
//
//            1.0f, 0.0f, 0.0f, 1.0f,
//            0.0f, 1.0f, 0.0f, 1.0f,
//            0.0f, 0.0f, 1.0f, 1.0f,
//
//            1.0f, 0.0f, 0.0f, 1.0f,
//            0.0f, 1.0f, 0.0f, 1.0f,
//            0.0f, 0.0f, 1.0f, 1.0f,
//
//            1.0f, 0.0f, 0.0f, 1.0f,
//            0.0f, 1.0f, 0.0f, 1.0f,
//            0.0f, 0.0f, 1.0f, 1.0f,
//    };

//    GLfloat vVertices_Triangles[6 * 3] = {  //for 1 GL_TRIANGLES
//            -0.5f,  0.5f, 0.0f,
//            -0.5f, -0.5f, 0.0f,
//            0.5f, 0.5f, 0.0f,
//
//            0.5f, 0.5f, 0.0f,
//            0.5f, -0.5f, 0.0f,
//            -0.5f, -0.5f, 0.0f
//    };

    GLfloat vVertices_strip[4 * 3] = {   //for 2 GL_TRIANGLE_STRIP
            -0.5f, 0.5f, 0.0f,
            -0.5f, -0.5f, 0.0f,
            0.5f, 0.5f, 0.0f,
            0.5f, -0.5f, 0.0f
    };

//    GLfloat vVertices_Fan[12 * 3] = {   //for 3 GL_TRIANGLE_FAN
//            0.0f, 0.0f, 0.0f,
//            -0.5f, 0.5f, 0.0f,
//            0.5f, 0.5f, 0.0f,
//
//            0.0f, 0.0f, 0.0f,
//            0.5f, 0.5f, 0.0f,
//            0.5f, -0.5f, 0.0f,
//
//            0.0f, 0.0f, 0.0f,
//            0.5f, -0.5f, 0.0f,
//            -0.5f, -0.5f, 0.0f,
//
//            0.0f, 0.0f, 0.0f,
//            -0.5f, -0.5f, 0.0f,
//            -0.5f, 0.5f, 0.0f
//    };

    if (m_ProgramObj == 0)
        return;
    glClear(GL_STENCIL_BUFFER_BIT | GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
    glClearColor(1.0, 1.0, 1.0, 1.0);

//    GLint vertexColorLocation = glGetUniformLocation(m_ProgramObj,"ourColor");

    // Use the program object
    glUseProgram(m_ProgramObj);

//    glUniform4f(vertexColorLocation, 0.0f, 1.0f, 0.0f, 0.0f);

    // Load the vertex data
//    glVertexAttribPointer (0, 3, GL_FLOAT, GL_FALSE, 0, vVertices_Triangles );  //for 1 GL_TRIANGLES
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, vVertices_strip);   //for 2 GL_TRIANGLE_STRIP
//    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 0, vVertices_Fan);   //for 3 GL_TRIANGLE_FAN

//    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 0, color_triangles);   //for color
    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 0, color_strip);   //for color
//    glVertexAttribPointer(1, 4, GL_FLOAT, GL_FALSE, 0, color_fan);   //3 for color

    glEnableVertexAttribArray(0);
    glEnableVertexAttribArray(1);

//    glDrawArrays (GL_TRIANGLES, 0, 6);  // //for 1 GL_TRIANGLES
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);   //for 2 GL_TRIANGLE_STRIP
//    glDrawArrays(GL_TRIANGLE_FAN, 0, 12);   //for 3 GL_TRIANGLE_FAN
    glUseProgram(GL_NONE);
}


void GLRectangleSample::destroy() {
    LOGI("destroy");
    if (m_ProgramObj) {
        glDeleteProgram(m_ProgramObj);
        m_ProgramObj = GL_NONE;
    }
}
