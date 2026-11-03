// omp_android_fix.h
// open.mp SDK 的 anim.hpp 使用 GLM_COUNTOF，但该宏在 glm 的标准/实验性接口里
// 并未对当前构建暴露。这里提供一个与 GLM 文档一致的最小定义。
#ifndef OMP_ANDROID_FIX_H
#define OMP_ANDROID_FIX_H

#ifndef GLM_COUNTOF
#define GLM_COUNTOF(arr) (sizeof(arr) / sizeof((arr)[0]))
#endif

#endif
