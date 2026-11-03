#!/bin/bash
# Streamer Android arm64 构建脚本
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN="${TOOLCHAIN:-/home/dsh/omp-server-android/omp-server-android/tools/android-arm64-native.cmake}"
rm -rf "$SRC/android-build"
cmake -S "$SRC" -B "$SRC/android-build" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-DLINUX=1 -DHAVE_STDINT_H=1" \
  -DCMAKE_CXX_FLAGS="-DLINUX=1 -DHAVE_STDINT_H=1 -DGLM_FORCE_PURE -D_LIBCPP_ENABLE_CXX17_REMOVED_UNARY_BINARY_FUNCTION -include $SRC/omp_android_fix.h"
cmake --build "$SRC/android-build" -j2
echo "输出: $SRC/android-build/bin/streamer.so"
