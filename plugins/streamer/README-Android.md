# Streamer Plugin - open.mp Android arm64 移植版

来源：https://github.com/samp-incognito/samp-streamer-plugin （open.mp 分支）
版本：2.9.6（PLUGIN_VERSION）

## 说明

open.mp 组件（`COMPONENT_ENTRY_POINT` / `OmpStreamerComponent`），
部署到 `files/server/components/streamer.so`。

编译目标：Android arm64，PAWN_CELL_SIZE=32。

## 依赖（需自行获取，不入本仓库）

- `lib/samp-plugin-sdk`（amxplugin.cpp 提供 pAMXFunctions + amx_* 桥接）
- `lib/omp-sdk` + `lib/omp-gdk` + `lib/omp-network`（open.mp 组件 SDK，含 glm/robin-hood-hashing/span-lite/string-view-lite）
- `lib/boost`（boost::bimap / boost::geometry，header-only）
- `lib/eigen`

## 构建（Android arm64）

1. 准备依赖到 `lib/`（submodule 网络不稳，用 gh-proxy / codeload tarball 手动拉取）
2. 用 omp-server-android 的 toolchain 配置：
```
cmake -S . -B android-build \
  -DCMAKE_TOOLCHAIN_FILE=/home/dsh/omp-server-android/omp-server-android/tools/android-arm64-native.cmake \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_C_FLAGS="-DLINUX=1 -DHAVE_STDINT_H=1" \
  -DCMAKE_CXX_FLAGS="-DLINUX=1 -DHAVE_STDINT_H=1 -DGLM_FORCE_PURE -D_LIBCPP_ENABLE_CXX17_REMOVED_UNARY_BINARY_FUNCTION -include .../omp_android_fix.h"
cmake --build android-build -j2
```

## 构建修改（相对上游 open.mp 分支）

1. 主 `CMakeLists.txt`：去掉 `-m32`（CMAKE_C_FLAGS/CXX_FLAGS 前缀）；`lib/cmake-modules/AddSAMPPlugin.cmake` 去掉 `-m32`
2. `src/CMakeLists.txt`：去掉 `-lrt`（Android 无 librt）；加回 `${SAMPSDK_DIR}/amxplugin.cpp`（组件也调用 amx_*，需 pAMXFunctions 桥接）
3. `lib/omp-sdk/CMakeLists.txt`：去掉 `-Werror=format`（arm64 下 StringView `%.*s` 的 size_type=64 位触发）
4. `src/utility/amx.cpp:46`：`reinterpret_cast<cell>(hookedNative)` → `static_cast<cell>(reinterpret_cast<uintptr_t>(hookedNative))`（64 位指针转 32 位 cell）
5. `omp_android_fix.h`：提供 `GLM_COUNTOF`（glm 不暴露），经 `-include` 注入

## 测试

`streamer_test.pwn` 验证动态对象 / 3D 文字标签 / tick 速率。
