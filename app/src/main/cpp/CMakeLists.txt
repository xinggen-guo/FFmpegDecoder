# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

project("ffmpegdecoder")

set(COMMON "${CMAKE_SOURCE_DIR}/common")
set(FFMPEG_DIR "${CMAKE_SOURCE_DIR}/ffmpeg")
set(MUSIC_DECODER "${CMAKE_SOURCE_DIR}/decoder")
set(OPEN_SL_DIR "${CMAKE_SOURCE_DIR}/libopensl")
set(OPEN_GL_DIR "${CMAKE_SOURCE_DIR}/render")

file(GLOB_RECURSE native_srcs "${CMAKE_SOURCE_DIR}/*.cpp")
file(GLOB COMMON_CPP "${COMMON}/*.cpp")
file(GLOB MUSIC_DECODER_CPP "${MUSIC_DECODER}/*.cpp")
file(GLOB OPEN_SL_DIR_CPP "${OPEN_SL_DIR}/*.cpp")
file(GLOB OPEN_GL_DIR_CPP "${OPEN_GL_DIR}/*.cpp")

include_directories(${CMAKE_SOURCE_DIR}/ffmpeg/include/${ANDROID_ABI})
include_directories(${COMMON})
include_directories(${MUSIC_DECODER})
include_directories(${OPEN_SL_DIR})
include_directories(${OPEN_GL_DIR})

add_library( # Sets the name of the library.
        ffmpegdecoder

        # Sets the library as a shared library.
        SHARED
        ${COMMON_CPP}
        ${MUSIC_DECODER_CPP}
        ${native_srcs}
        ${OPEN_SL_DIR_CPP}
        ${OPEN_GL_DIR_CPP}
        # Provides a relative path to your source file(s).
        )


find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)


target_link_libraries( # Specifies the target library.
        ffmpegdecoder
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libavformat.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libavcodec.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libavfilter.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libavdevice.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libavutil.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libpostproc.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libswresample.a
        ${FFMPEG_DIR}/libs/${ANDROID_ABI}/libswscale.a
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        OpenSLES
        EGL
        GLESv3
        z)