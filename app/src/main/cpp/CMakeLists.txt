# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

set(MUSIC_DECODER "${CMAKE_SOURCE_DIR}/musicdecoder")
set(FFMPEG_DIR "${CMAKE_SOURCE_DIR}/ffmpeg")

file(GLOB_RECURSE native_srcs "${CMAKE_SOURCE_DIR}/*.cpp")
file(GLOB MUSIC_DECODER_CPP "${MUSIC_DECODER}/*.cpp")

include_directories(BEFORE ${CMAKE_SOURCE_DIR}/ffmpeg/include)
include_directories(${MUSIC_DECODER})

# Declares and names the project.
project("ffmpegdecoder")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library( # Sets the name of the library.
        ffmpegdecoder

        # Sets the library as a shared library.
        SHARED
        ${MUSIC_DECODER_CPP}
        ${native_srcs}
        # Provides a relative path to your source file(s).
        )

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        ffmpegdecoder
        ${FFMPEG_DIR}/armeabi-v7a/libavformat.a
        ${FFMPEG_DIR}/armeabi-v7a/libavcodec.a
        ${FFMPEG_DIR}/armeabi-v7a/libavfilter.a
        ${FFMPEG_DIR}/armeabi-v7a/libavdevice.a
        ${FFMPEG_DIR}/armeabi-v7a/libavutil.a
        ${FFMPEG_DIR}/armeabi-v7a/libpostproc.a
        ${FFMPEG_DIR}/armeabi-v7a/libswresample.a
        ${FFMPEG_DIR}/armeabi-v7a/libswscale.a
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib}
        OpenSLES
        z)