#!/bin/bash
# MapAndreas Android arm64 构建脚本
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
SDK="${SAMPSDK_DIR:-/home/dsh/omp-server-android/plugins/streamer/lib/samp-plugin-sdk}"
/opt/armtc/bin/clang++ -std=c++17 -fPIC -shared \
  -DLINUX=1 -DHAVE_STDINT_H=1 -include alloca.h \
  -I "$SRC/src" -I "$SDK" -I "$SDK/amx" \
  "$SRC/src/MapAndreas.cpp" "$SRC/src/natives.cpp" "$SRC/src/plugin.cpp" \
  "$SDK/amxplugin.cpp" \
  -o "$SRC/mapandreas.so"
echo "输出: $SRC/mapandreas.so"
