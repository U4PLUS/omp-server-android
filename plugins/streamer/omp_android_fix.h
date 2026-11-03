#ifndef OMP_ANDROID_FIX_H
#define OMP_ANDROID_FIX_H
// omp-sdk anim.hpp uses GLM_COUNTOF which current glm doesn't expose
#ifndef GLM_COUNTOF
#define GLM_COUNTOF(arr) (sizeof(arr)/sizeof((arr)[0]))
#endif
#endif
