# MapAndreas Plugin - open.mp Android arm64 移植版

来源：https://github.com/philip1337/samp-plugin-mapandreas
版本：1.2（MapAndreas 1.2）

## 说明

SA:MP legacy 插件（`Supports/Load/AmxLoad` + `amx_Register`），
部署到 `files/server/plugins/mapandreas.so`。

编译目标：Android arm64。

## 部署文件

- `plugins/mapandreas.so`（本插件）
- 高度图数据：`SAmin.hmap`（8MB）/ `SAfull.hmap`（72MB）→ 放 `files/server/`（工作目录，`fopen` 从这里找）
- `pawncc/include/mapandreas.inc`（头文件）

## 依赖

- `samp-plugin-sdk`（提供 `amx/amx.h`、`plugincommon.h`、`amxplugin.cpp`）
  - `amxplugin.cpp` 提供 `pAMXFunctions` + 所有 `amx_*` 函数桥接（open.mp 不导出 amx_* 符号，必须链接）

## 构建（Android arm64）

```
clang++ -std=c++17 -fPIC -shared \
  -DLINUX=1 -DHAVE_STDINT_H=1 -include alloca.h \
  -I src -I <samp-plugin-sdk> -I <samp-plugin-sdk>/amx \
  src/MapAndreas.cpp src/natives.cpp src/plugin.cpp \
  <samp-plugin-sdk>/amxplugin.cpp \
  -o mapandreas.so
```

## 关键修改（相对上游）

1. `src/plugin.cpp`：`extern void *pAMXFunctions;`（保持 extern，由 amxplugin.cpp 提供定义）
2. 链接 `amxplugin.cpp`（提供 pAMXFunctions + amx_GetAddr/amx_Register 等桥接到 open.mp 函数表）
3. 加 `-DHAVE_STDINT_H=1`（修 amx.h 的 uint32_t 重定义）、`-DLINUX=1`（plugincommon.h）、`-include alloca.h`（amx_StrParam 用 alloca）

## 测试

`mapandreas_test.pwn` 验证 Init/FindZ/FindAverageZ/SetZ/Unload，输出真实地形高度。
