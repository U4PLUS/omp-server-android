#!/bin/bash
set -e
APP=/home/dsh/main/GTASA/openmp-server/android-app
SRC=/home/dsh/main/GTASA/openmp-server/open.mp/build-android/Output/Release/Server
PAWNTOOLS=/home/dsh/main/GTASA/openmp-server/pawn-tools
BT=/opt/android-sdk/build-tools/34.0.0
PLATFORM=/opt/android-sdk/platforms/android-34/android.jar
KOTLINC=/opt/kotlinc
BOX64=/opt/box64-src/build/box64
B=$APP/build
mkdir -p $B/dex

echo "--- [1/6] assets ---"
rm -rf $APP/assets/server/components
mkdir -p $APP/assets/server/components
cp $SRC/components/*.so $APP/assets/server/components/
for f in $APP/assets/server/components/*.so; do /usr/bin/llvm-strip-14 "$f"; done

echo "--- [1b] native lib (lib/arm64-v8a/libompserver.so) ---"
rm -rf $APP/lib
mkdir -p $APP/lib/arm64-v8a
cp $SRC/omp-server $APP/lib/arm64-v8a/libompserver.so
/usr/bin/llvm-strip-14 $APP/lib/arm64-v8a/libompserver.so
cp $PAWNTOOLS/pawncc $APP/lib/arm64-v8a/libpawncc.so
cp $PAWNTOOLS/libpawnc.so $APP/lib/arm64-v8a/libpawnc.so
/usr/bin/llvm-strip-14 $APP/lib/arm64-v8a/libpawncc.so $APP/lib/arm64-v8a/libpawnc.so

echo "--- [2/6] kotlinc ---"
rm -rf $B/dex/*
java -cp $KOTLINC/compiler.jar:$KOTLINC/stdlib.jar:$KOTLINC/trove4j.jar:$KOTLINC/script-runtime.jar:$KOTLINC/annotations.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -classpath $KOTLINC/stdlib.jar:$PLATFORM \
  -d $B/app.jar $APP/kt/src/*.kt

echo "--- [3/6] d8 (app + stdlib) ---"
$BT/d8 --release --min-api 26 --output $B/dex $B/app.jar $KOTLINC/stdlib.jar

echo "--- [4/6] aapt2 link ---"
rm -f $B/app.unaligned.apk
$BOX64 $BT/aapt2 compile --dir $APP/res -o $B/res.zip
        $BOX64 $BT/aapt2 link -o $B/app.unaligned.apk --manifest $APP/AndroidManifest.xml -I $PLATFORM -A $APP/assets -R $B/res.zip --auto-add-overlay

echo "--- [5/6] dex + native lib + zipalign ---"
cd $B/dex && zip -q -j $B/app.unaligned.apk classes.dex && cd - >/dev/null
cd $APP && zip -q -r $B/app.unaligned.apk lib && cd - >/dev/null
sh $BT/zipalign -f 4 $B/app.unaligned.apk $B/app.aligned.apk

echo "--- [6/6] sign ---"
KS=$B/debug.keystore
[ -f $KS ] || keytool -genkeypair -keystore $KS -alias androiddebugkey -storepass android -keypass android -dname "CN=Debug,O=Debug,C=US" -keyalg RSA -keysize 2048 -validity 10000
$BT/apksigner sign --ks $KS --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $B/omp-server-android.apk $B/app.aligned.apk
$BT/apksigner verify $B/omp-server-android.apk
sh $BT/zipalign -c 4 $B/omp-server-android.apk
echo "APK READY: $B/omp-server-android.apk"
