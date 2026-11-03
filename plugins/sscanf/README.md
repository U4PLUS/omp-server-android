# sscanf 2.15.1 — open.mp Android (arm64) 移植版

原版：https://github.com/Y-Less/sscanf （Y_Less 的 SA:MP/open.mp 字符串解析插件）

本移植版编译为 **open.mp 组件**（`COMPONENT_ENTRY_POINT` / `SScanFComponent`），
可在 open.mp Android 服务器上以 arm64 运行。

---

## 与上游的差异

原版 sscanf 有两种加载方式：
- **SA:MP legacy 插件**：`Supports/Load/AmxLoad`，用 **subhook 拦截 `amx_Register`**（x86 汇编实现，arm64 不支持）。
- **open.mp 组件**：`SScanFComponent` + `COMPONENT_ENTRY_POINT`，通过 `onAmxLoad` 注册 natives、`onPlayerNameChange` 事件维护玩家名，**不需要 subhook**。

本移植版采用 **open.mp 组件方式**（arm64 可行），改动如下：

1. **sscanf.cpp**：加 `SSCANF_OMPSERVER` 条件编译，把 SA:MP 插件方式（`Supports/Load/AmxLoad` + subhook 段）隔离掉，只编译组件方式。
2. **subhook/subhook_stub.c**：提供 subhook 符号的空实现（仅让插件方式的符号链接通过；组件方式不调用）。subhook 本身只支持 x86/x64，arm64 无实现，故用 stub。
3. **omp_android_fix.h**：open.mp SDK 的 `anim.hpp` 使用 `GLM_COUNTOF` 宏（当前 glm 未暴露），提供 `#define GLM_COUNTOF(arr) (sizeof(arr)/sizeof((arr)[0]))`。
4. **CMakeLists-android.txt**：arm64 构建配置（编译 `error.cpp` —— 上游 UNIX 分支漏列，导致 `SetErrorCode` 等符号未定义，组件 dlopen 失败，已修复）。

---

## 构建

依赖（需在仓库根目录，构建前就绪）：
- `open.mp/`（open.mp-sdk，submodule，`github.com/openmultiplayer/open.mp-sdk`）
- `glm/`（submodule，`github.com/g-truc/glm`）
- `robin-hood-hashing/`（submodule，`github.com/martinus/robin-hood-hashing`）
- `span-lite/`（submodule，`github.com/martinmoene/span-lite`）
- `string-view-lite/`（submodule，`github.com/martinmoene/string-view-lite`）

> 说明：本仓库提交的是**核心源码 + 移植文件**，不含 submodule 内容（体积大，且 subhook 原仓库 `github.com/Zeex/subhook` 已不可用，组件方式不需要它）。
> 构建前需在 sscanf 源码目录 `git submodule update --init` 获取上述依赖头文件。

构建命令（arm64 Android toolchain，见 `omp-server-android/tools/android-arm64-native.cmake`）：

```bash
cmake -S . -B android-build \
  -DCMAKE_TOOLCHAIN_FILE=/path/to/omp-server-android/tools/android-arm64-native.cmake \
  -DCMAKE_BUILD_TYPE=Release
cmake --build android-build --target sscanf -j2
# 产物: android-build/sscanf.so
```

> 构建时用 `CMakeLists-android.txt` 作为 `CMakeLists.txt`（原版 `CMakeLists.txt` 是 SA:MP 构建配置，含 `-m32` 与 subhook，arm64 不可用）。

---

## 部署

- `sscanf.so` → open.mp 服务器目录 `files/server/components/sscanf.so`（与 `Pawn.so`、`Console.so` 等组件放一起）
- `sscanf2.inc` → `files/server/pawncc/include/sscanf2.inc`（pawn 脚本头文件）

重启服务器，open.mp 自动加载 sscanf 组件（日志：`Successfully loaded component sscanf (2.15.1.0)`）。

pawn 脚本使用：

```pawn
#include <sscanf2>

// 解析
new id, name[32];
sscanf("42 player", "is[32]", id, name);
```

---

## 测试

`sscanf_test.pwn`（filterscript）在 `OnFilterScriptInit` 里跑 8 个解析用例（整数/负数/浮点/字符串/组合/十六进制），
输出到服务器日志（`[sscanf_test]`）。玩家连接时测 `u`（玩家名）解析。

预期：`T1~T8` 全部 `OK`，`sscanf test done`。

---

## 变更记录

- **v1（2026-11-03）**：首次移植。组件方式，SSCANF_OMPSERVER 条件编译；arm64 构建；修复 `error.cpp` 未链接导致 dlopen 失败；GLM_COUNTOF 提供；subhook stub。
