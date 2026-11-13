#!/bin/bash
set -e
APP=/home/dsh/omp-server-android/omp-server-android/android-app
SRC=/home/dsh/omp-server-android/omp-server-android/build-android/Output/Release/Server
BT=/opt/android-sdk/build-tools/34.0.0
PLATFORM=/opt/android-sdk/platforms/android-34/android.jar
BOX64=/opt/box64-src/build/box64
B=$APP/build
mkdir -p $B/classes $B/dex

echo "--- [1/7] assets: copy + strip ---"
rm -rf $APP/assets/server/components
mkdir -p $APP/assets/server/components
cp $SRC/omp-server $APP/assets/server/omp-server
cp $SRC/components/*.so $APP/assets/server/components/
/usr/bin/llvm-strip-14 $APP/assets/server/omp-server
for f in $APP/assets/server/components/*.so; do /usr/bin/llvm-strip-14 "$f"; done
# 插件运行时依赖（由 app 提供）：libcpprest + libc++_shared 进 lib/arm64-v8a/
mkdir -p $APP/lib/arm64-v8a
cp /home/dsh/omp-server-android/plugins/cpprestsdk-build/Binaries/libcpprest.so.2.10 $APP/lib/arm64-v8a/ 2>/dev/null || true
if [ ! -f $APP/lib/arm64-v8a/libc++_shared.so ]; then cp /home/dsh/omp-server-android/plugins/release/requests-omp-android/libc++_shared.so $APP/lib/arm64-v8a/ 2>/dev/null || true; fi
du -sh $APP/assets/server

echo "--- [2/7] javac ---"
rm -rf $B/classes/* $B/dex/*
javac --release 11 -cp $PLATFORM -d $B/classes $APP/src/com/dsh/openmpserver/*.java

echo "--- [3/7] d8 dex ---"
$BT/d8 --release --lib $PLATFORM --output $B/dex $B/classes/com/dsh/openmpserver/*.class

echo "--- [4/7] aapt2 link ---"
rm -f $B/app.unaligned.apk
$BOX64 $BT/aapt2 link -o $B/app.unaligned.apk --manifest $APP/AndroidManifest.xml -I $PLATFORM -A $APP/assets --auto-add-overlay

echo "--- [5/7] add classes.dex ---"
cd $B/dex && zip -q -j $B/app.unaligned.apk classes.dex && cd - >/dev/null

echo "--- [6/7] zipalign ---"
sh $BT/zipalign -f 4 $B/app.unaligned.apk $B/app.aligned.apk

echo "--- [7/7] sign ---"
KS=$B/debug.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -alias androiddebugkey -storepass android -keypass android -dname "CN=Debug,O=Debug,C=US" -keyalg RSA -keysize 2048 -validity 10000
$BT/apksigner sign --ks $KS --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $B/omp-server-android.apk $B/app.aligned.apk
$BT/apksigner verify $B/omp-server-android.apk
echo "APK READY: $B/omp-server-android.apk"
