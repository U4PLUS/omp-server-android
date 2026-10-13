# open.mp Server (Android) — 完整移植工程

在**安卓手机**上运行 **open.mp 1.5.8** 服务器的完整开源工程：open.mp 内核源码、
安卓 APP 端源码（Kotlin）、pawn 编译器源码、打包脚本，以及中文 UTF-8 昵称支持补丁。

## 特性

- open.mp 1.5.8 服务器内核（arm64，exec 方式原生运行，22 个官方组件）
- **中文昵称支持**：服务端连接校验放行 UTF-8 非 ASCII 字节（`open.mp/Server/Source/player_pool.hpp`）
- 崩溃自动重启（可开关，区分人为停止）、后台保活引导（电池优化白名单）
- 服务器日志：落盘 server_log.txt + 自动跟随滚动/手动固定/内容变化检测
- 文件接口（MT 管理器可浏览/新建/上传/编译 pawncc）、内置 FTP（匿名，PORT/EPSV）
- `plugins/` 目录自由添加 aarch64 SA-MP 插件（`config.json` → `pawn.legacy_plugins`）
- 深色/浅色主题、运行时长、服务器关闭自动清日志

## 目录结构

```
open.mp/                open.mp 服务器内核源码（含 lib/pawn 编译器源码）
android-app/            安卓 APP 工程（Kotlin 源码/清单/资源/打包脚本）
android-app/assets/server/   服务器运行时资源（config.json、组件清单、pawncc/include、gamemode 样例）
pawn-include/           open.mp 官方 Pawn include 集
tools/                  Android 交叉编译工具链配置（android-toolchain.cmake）
AirportSpawn.pwn        示例游戏模式（机场出生 + /c 刷车）
```

## 构建

### APP（android-app）
```bash
cd android-app
./build-kotlin-apk.sh        # 依赖: NDK 26.3 / build-tools 34 / kotlinc / box64
# 产物: build/omp-server-android.apk
```

### 服务器内核（libompserver.so）
```bash
cmake -B build-android -DCMAKE_TOOLCHAIN_FILE=tools/android-toolchain.cmake -S open.mp
cmake --build build-android -j4
# 产物: build-android/Output/Release/Server/omp-server → 改名 libompserver.so
```

## Release

每个版本附带：
- `omp-server-android.apk` 可直接安装的服务器 APP
- `omp-app-src.zip` / `omp-lib-src.zip` 源码快照包

## 许可

本项目自身代码 MIT；第三方组件保留各自许可（open.mp Apache-2.0、Pawn CompuPhase 条款），详见 LICENSE。
