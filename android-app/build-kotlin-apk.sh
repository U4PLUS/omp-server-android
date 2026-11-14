#!/bin/bash
set -e
# ============================================================
# open.mp Android APK build script
# 组件二进制（.so/omp-server/pawncc/frpc/第三方库）不在 git 里，
# 通过 components-bundle.zip 提供：优先本地目录，缺失则从 GitHub Release 自动下载。
# 用法：./build-kotlin-apk.sh [--release-url <url>]
# ============================================================
REPO=U4PLUS/omp-server-android
RELEASE_TAG=${RELEASE_TAG:-v2.13.12}
BUNDLE_ZIP=components-bundle.zip
# 允许外部传 --release-url（默认 GitHub Release 资产）
RELEASE_URL=""
for arg in "$@"; do
  case "$arg" in
    --release-url=*) RELEASE_URL="${arg#*=}" ;;
  esac
done

# 仓库根（脚本在 android-app/ 下）
MAIN="$(cd "$(dirname "$0")/.." && pwd)"
APP="$MAIN/android-app"
B="$APP/build"

# ---- 组件包定位：本地 components-bundle/ 优先，缺失则下载 ----
BUNDLE_DIR="$MAIN/components-bundle"
if [ ! -d "$BUNDLE_DIR" ] || [ ! -f "$BUNDLE_DIR/server/omp-server" ]; then
  echo "--- [0/6] 组件包缺失，自动下载 ---"
  if [ -z "$RELEASE_URL" ]; then
    RELEASE_URL="https://github.com/$REPO/releases/download/$RELEASE_TAG/$BUNDLE_ZIP"
  fi
  echo "  下载: $RELEASE_URL"
  rm -f "$MAIN/$BUNDLE_ZIP"
  curl -fL --retry 3 -o "$MAIN/$BUNDLE_ZIP" "$RELEASE_URL"
  rm -rf "$BUNDLE_DIR"
  # zip 顶层已是 components-bundle/，解压到 $MAIN 让其正好落成 $BUNDLE_DIR
  cd "$MAIN" && unzip -q -o "$BUNDLE_ZIP" -d "$MAIN" && cd - >/dev/null
fi
SRC="$BUNDLE_DIR/server"          # omp-server + components/
PAWNTOOLS="$BUNDLE_DIR/pawn-tools"
FRPC="$BUNDLE_DIR/frpc-bin/frpc"
CPPREST="$BUNDLE_DIR/cpprest/libcpprest.so.2.10"
LIBCXX="$BUNDLE_DIR/libcxx/libc++_shared.so"

# ---- 工具链（本机路径，构建环境需具备）----
BT=/opt/android-sdk/build-tools/34.0.0
PLATFORM=/opt/android-sdk/platforms/android-34/android.jar
KOTLINC=/opt/kotlinc
[ -x "$BT/d8" ] || { echo "缺失 Android build-tools: $BT"; exit 1; }
[ -d "$KOTLINC" ] || { echo "缺失 kotlinc: $KOTLINC"; exit 1; }
mkdir -p "$B/dex"

echo "--- [1/6] assets (components) ---"
rm -rf "$APP/assets/server/components"
mkdir -p "$APP/assets/server/components"
cp "$SRC"/components/*.so "$APP/assets/server/components/"
for f in "$APP/assets/server/components/"*.so; do /usr/bin/llvm-strip-14 "$f" 2>/dev/null || true; done

echo "--- [1b] native lib ---"
rm -rf "$APP/lib"; mkdir -p "$APP/lib/arm64-v8a"
cp "$SRC/omp-server" "$APP/lib/arm64-v8a/libompserver.so"
/usr/bin/llvm-strip-14 "$APP/lib/arm64-v8a/libompserver.so" 2>/dev/null || true
cp "$PAWNTOOLS/pawncc" "$APP/lib/arm64-v8a/libpawncc.so" 2>/dev/null || true
[ -f "$PAWNTOOLS/libpawnc.so" ] && cp "$PAWNTOOLS/libpawnc.so" "$APP/lib/arm64-v8a/libpawnc.so" 2>/dev/null || true
/usr/bin/llvm-strip-14 "$APP/lib/arm64-v8a/libpawncc.so" 2>/dev/null || true
# 插件运行时依赖
cp "$CPPREST" "$APP/lib/arm64-v8a/"
cp "$LIBCXX" "$APP/lib/arm64-v8a/" 2>/dev/null || true
# frpc (FRP 内网穿透, arm64) - 从 nativeLibraryDir exec
if [ -f "$FRPC" ]; then cp "$FRPC" "$APP/lib/arm64-v8a/libfrpc.so"; fi

echo "--- [2/6] kotlinc ---"
rm -rf "$B/dex"/*
java -cp "$KOTLINC/compiler.jar:$KOTLINC/stdlib.jar:$KOTLINC/trove4j.jar:$KOTLINC/script-runtime.jar:$KOTLINC/annotations.jar:$KOTLINC/kotlinx-coroutines.jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath "$KOTLINC/stdlib.jar:$PLATFORM" \
  -d "$B/app.jar" "$APP"/kt/src/*.kt

echo "--- [3/6] d8 (app + stdlib) ---"
"$BT/d8" --release --min-api 26 --output "$B/dex" "$B/app.jar" "$KOTLINC/stdlib.jar"

echo "--- [4/6] aapt2 link ---"
rm -f "$B/app.unaligned.apk"
/opt/arm64-bt/wrap-aapt2 compile --dir "$APP/res" -o "$B/res.zip"
/opt/arm64-bt/wrap-aapt2 link -o "$B/app.unaligned.apk" --manifest "$APP/AndroidManifest.xml" -I "$PLATFORM" -A "$APP/assets" -R "$B/res.zip" --auto-add-overlay

echo "--- [5/6] dex + native lib + zipalign ---"
( cd "$B/dex" && zip -q -j "$B/app.unaligned.apk" classes.dex )
( cd "$APP" && zip -q -r "$B/app.unaligned.apk" lib )
/opt/arm64-bt/wrap-zipalign -f 4 "$B/app.unaligned.apk" "$B/app.aligned.apk"

echo "--- [6/6] sign ---"
KS="$B/debug.keystore"
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias androiddebugkey -storepass android -keypass android -dname "CN=Debug,O=Debug,C=US" -keyalg RSA -keysize 2048 -validity 10000
"$BT/apksigner" sign --ks "$KS" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out "$B/omp-server-android.apk" "$B/app.aligned.apk"
"$BT/apksigner" verify "$B/omp-server-android.apk"
/opt/arm64-bt/wrap-zipalign -c 4 "$B/omp-server-android.apk"
echo "APK READY: $B/omp-server-android.apk"
