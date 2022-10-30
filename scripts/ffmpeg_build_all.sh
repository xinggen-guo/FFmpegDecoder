#!/bin/bash

echo "进入FFmpeg编译脚本"


HOST_OS_ARCH=darwin-x86_64

# NDK环境    
export ANDROID_NDK=/Users/guoxinggen/android-ndk-r15c
PREFIX_DIR=/Users/guoxinggen/audio_lib/ffmpeg-4.0.2
OUT_DIR=$PREFIX_DIR/android_build


function build_android {

    echo "开始编译FFmpeg..."

    #armeabi-v7a
    echo "开始编译FFmpeg(armeabi-v7a)"

    TOOLCHAIN_PREFIX=arm-linux-androideabi
    ARCH=armv7-a
    API_LEVEL=16

    TOOLCHAIN_PATH=$ANDROID_NDK/toolchains/$TOOLCHAIN_PREFIX-4.9/prebuilt/$HOST_OS_ARCH
    CROSS_PREFIX=$TOOLCHAIN_PATH/bin/$TOOLCHAIN_PREFIX-
    CROSS_GCC_LIB=$TOOLCHAIN_PATH/lib/gcc/$TOOLCHAIN_PREFIX/4.9.x

    SYSROOT=$ANDROID_NDK/platforms/android-$API_LEVEL/arch-arm
    SYSROOT_INC=$SYSROOT/usr/include

    ADDI_CFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -mcpu=cortex-a8"
    ADDI_CXXFLAGS="-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -mcpu=cortex-a8"
    ADDI_LDFLAGS="-Wl,--fix-cortex-a8 -lm -lz"
    EXTRA_CONFIG="--arch=arm --enable-neon --enable-asm --enable-inline-asm"

    make clean
    ./configure \
    --prefix=${PREFIX_DIR}/android \
    --target-os=android \
    --enable-postproc \
    --enable-gpl \
    --enable-runtime-cpudetect \
    --enable-small \
    --enable-static \
    --enable-cross-compile \
    --disable-shared \
    --disable-debug \
    --disable-ffmpeg \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-doc \
    --disable-symver \
    --disable-asm \
    --disable-stripping \
    --disable-armv5te \
    --libdir=${OUT_DIR}/libs/armeabi-v7a \
    --incdir=${OUT_DIR}/include/armeabi-v7a \
    --pkgconfigdir=${OUT_DIR}/pkgconfig/armeabi-v7a \
    --sysroot=$SYSROOT \
    --cross-prefix=$CROSS_PREFIX \
    --target-os=android \
    --arch=$ARCH \
    --extra-cflags="-O3 -ffast-math -fPIC $ADDI_CFLAGS -I$X264_LIB_PATH/include -I$SYSROOT_INC -isysroot -I$ANDROID_NDK/sysroot -I$ANDROID_NDK/sysroot/usr/include -I$ANDROID_NDK/sysroot/usr/include/arm-linux-androideabi -DANDROID_API=29" \
    --extra-cxxflags="-O3 -ffast-math -fPIC $ADDI_CXXFLAGS -I$X264_LIB_PATH/include -I$SYSROOT_INC" \
    --extra-ldflags="$ADDI_LDFLAGS -L$X264_LIB_PATH/lib" \
    --extra-ldexeflags="-pie -fPIC $ADDI_LDFLAGS -L$X264_LIB_PATH/lib" \
    ${EXTRA_CONFIG}

    make -j4 && make install
    echo "结束编译FFmpeg(armeabi-v7a)"

    # arm64-v8a
    echo "开始编译FFmpeg(arm64-v8a)"
    echo "${PREFIX}"
    echo "${OUT_DIR}"


    TOOLCHAIN_PREFIX=aarch64-linux-android
    ARCH=aarch64
    API_LEVEL=21

    TOOLCHAIN_PATH=$ANDROID_NDK/toolchains/$TOOLCHAIN_PREFIX-4.9/prebuilt/$HOST_OS_ARCH
    CROSS_PREFIX=$TOOLCHAIN_PATH/bin/$TOOLCHAIN_PREFIX-
    CROSS_GCC_LIB=$TOOLCHAIN_PATH/lib/gcc/$TOOLCHAIN_PREFIX/4.9.x

    SYSROOT=$ANDROID_NDK/platforms/android-$API_LEVEL/arch-arm64
    SYSROOT_INC=$SYSROOT/usr/include

    ADDI_CFLAGS="-march=armv8-a"
    ADDI_CXXFLAGS="-march=armv8-a"
    ADDI_LDFLAGS="-lm -lz -nostdlib"

    make clean
    ./configure \
    --prefix=${PREFIX_DIR}/android \
    --enable-postproc \
    --enable-gpl \
    --enable-runtime-cpudetect \
    --enable-small \
    --enable-static \
    --enable-cross-compile \
    --disable-shared \
    --disable-debug \
    --disable-ffmpeg \
    --disable-ffplay \
    --disable-ffprobe \
    --disable-doc \
    --disable-symver \
    --disable-asm \
    --disable-stripping \
    --disable-armv5te \
    --libdir=${OUT_DIR}/libs/arm64-v8a \
    --incdir=${OUT_DIR}/include/arm64-v8a \
    --pkgconfigdir=${OUT_DIR}/pkgconfig/arm64-v8a \
    --sysroot=$SYSROOT \
    --cross-prefix=$CROSS_PREFIX \
    --target-os=android \
    --arch=$ARCH \
    --extra-ldexeflags=-pie

    make -j4 && make install
    echo "结束编译FFmpeg(arm64-v8a)"
   

    echo "编译结束"

};

build_android