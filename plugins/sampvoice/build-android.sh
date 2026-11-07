#!/bin/bash
# SampVoice Android arm64 构建脚本（参考 streamer 的 build-android.sh）
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
TOOLCHAIN="${TOOLCHAIN:-/home/dsh/omp-server-android/omp-server-android/tools/android-arm64-native.cmake}"
OMP_SDK="${OMP_SDK:-/home/dsh/omp-server-android/omp-server-android/open.mp/SDK}"

# 1. 获取 omp-sdk / omp-net（git submodule），若缺 glm/robin-hood 用本地 open.mp SDK 的 lib 补
if [ -d "$SRC/.git" ]; then
  git -C "$SRC" submodule update --init --recursive || echo "(submodule init 警告: 继续用本地 SDK 补)"
fi
# 2. lib/omp-sdk/include 用本地 open.mp SDK（组件接口匹配服务器版本）
if [ -d "$OMP_SDK/include" ]; then
  rm -rf "$SRC/lib/omp-sdk/include.vendored"
  if [ -d "$SRC/lib/omp-sdk/include" ] && [ ! -L "$SRC/lib/omp-sdk/include" ]; then
    mv "$SRC/lib/omp-sdk/include" "$SRC/lib/omp-sdk/include.vendored" 2>/dev/null || true
  fi
  rm -rf "$SRC/lib/omp-sdk/include"
  cp -r "$OMP_SDK/include" "$SRC/lib/omp-sdk/include"
fi
# 3. 补 header-only 库（glm 等，submodule 缺失时用本地 open.mp SDK）
for d in glm robin-hood-hashing span-lite string-view-lite; do
  if [ ! -f "$SRC/lib/omp-sdk/lib/$d/CMakeLists.txt" ] && [ -d "$OMP_SDK/lib/$d" ]; then
    rm -rf "$SRC/lib/omp-sdk/lib/$d"
    cp -r "$OMP_SDK/lib/$d" "$SRC/lib/omp-sdk/lib/$d"
  fi
done

# 4. CMake 配置 + 构建
rm -rf "$SRC/android-build"
cmake -S "$SRC" -B "$SRC/android-build" \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_CLIENT=OFF \
  -DCMAKE_CXX_FLAGS="-Wno-format-security -Wno-error=format-security -Wno-format"
cmake --build "$SRC/android-build" -j2
echo "输出: $SRC/android-build/server/sampvoice.so"
