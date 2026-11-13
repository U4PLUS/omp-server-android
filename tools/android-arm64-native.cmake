# Native arm64 Android toolchain for aarch64 host (system clang-15 + NDK27 sysroot).
# Compilers are wrapper scripts that embed --target=aarch64-linux-android26 so that
# project CMakeLists overriding CMAKE_CXX_FLAGS cannot drop the target.
set(CMAKE_SYSTEM_NAME Generic)
set(CMAKE_SYSTEM_VERSION 26)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_CROSSCOMPILING TRUE)

set(CMAKE_C_COMPILER /opt/armtc/bin/clang)
set(CMAKE_CXX_COMPILER /opt/armtc/bin/clang++)
set(CMAKE_AR /opt/armtc/bin/llvm-ar)
set(CMAKE_RANLIB /opt/armtc/bin/llvm-ranlib)
set(CMAKE_LINKER /opt/armtc/bin/ld.lld)

# NDK libc++ (shared) on the sysroot.
set(CMAKE_CXX_STDLIB libc++ CACHE STRING "C++ standard library")

set(NDK_SYSROOT "/opt/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/sysroot")
set(BUILTINS_DIR "/opt/android-sdk/ndk/27.0.12077973/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/18/lib/linux")
set(UNWIND_DIR "${BUILTINS_DIR}/aarch64")
set(CMAKE_SYSROOT "${NDK_SYSROOT}")

set(_LIBS "-lc++_shared -lc++abi -lunwind -l:libclang_rt.builtins-aarch64-android.a")
set(_LINK "-fuse-ld=lld -nostdlib++ -Wl,-rpath,'$ORIGIN' -L${NDK_SYSROOT}/usr/lib/aarch64-linux-android/26 -L${UNWIND_DIR} -L${BUILTINS_DIR} -L/opt/fakegcc ${_LIBS}")
set(CMAKE_EXE_LINKER_FLAGS "${_LINK}")
set(CMAKE_SHARED_LINKER_FLAGS "${_LINK}")
set(CMAKE_MODULE_LINKER_FLAGS "${_LINK}")
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Generic disables shared libs; re-enable after platform load.

# Android NDK info for open.mp's conan integration (os/arch detection)
set(CMAKE_ANDROID_NDK "/opt/android-sdk/ndk/27.0.12077973")
set(ANDROID_ABI "arm64-v8a")
set(CMAKE_ANDROID_ARCH_ABI "arm64-v8a")
set(ANDROID_PLATFORM "android-26")

set(CMAKE_PROJECT_INCLUDE "${CMAKE_CURRENT_LIST_DIR}/android-arm64-enable-shared.cmake")
