#!/bin/bash
# FileManager Android arm64 构建脚本
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"
CLANGPP=/opt/armtc/bin/clang++
OUT="$SRC/filemanager.so"

echo "=== 编译 FileManager (arm64) ==="
"$CLANGPP" \
    -std=c++11 \
    -O2 \
    -fPIC \
    -fvisibility=hidden \
    -DLINUX \
    -DAARCH64 \
    -DHAVE_STDINT_H \
    -DFILEMANAGER_VERSION='"1.4-arm64"' \
    -I"$SRC" \
    "$SRC/FileManager/filemanager.cpp" \
    "$SRC/SDK/amxplugin.cpp" \
    -shared \
    -o "$OUT"

echo "输出: $OUT"
ls -lh "$OUT" | awk '{print "大小:", $5}'
