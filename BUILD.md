# 构建完整版 APK (open.mp Android)

本仓库只含**源码**。构建所需的组件二进制（open.mp 核心、22 个组件 .so、pawncc、frpc、libcpprest、libc++_shared，约 42MB）不在 git 里，由 `components-bundle.zip` 提供。

## 前置工具链（构建环境需自备）
- Android SDK build-tools 34.0.0 + platform android-34（`/opt/android-sdk/`）
- kotlinc（`/opt/kotlinc/`）
- aapt2/zipalign 的 arm64 构建（`/opt/arm64-bt/wrap-aapt2`、`/opt/arm64-bt/wrap-zipalign`）
- llvm-strip-14

## 构建
```bash
# 1. 克隆源码
git clone https://github.com/U4PLUS/omp-server-android.git
cd omp-server-android

# 2. 运行构建脚本（首次会自动从 GitHub Release 下载 components-bundle.zip）
./android-app/build-kotlin-apk.sh
# 产物: android-app/build/omp-server-android.apk
```

若组件包已本地存在（`components-bundle/` 目录），脚本直接复用，不重复下载。
也可用 `--release-url=<url>` 指定组件包下载地址。

## Release 资产
- `omp-server-android.apk` —— 完整版成品（含 FRPC、pawncc、全部组件），装即用
- `components-bundle.zip` —— 构建输入（clone 源码后构建用）
