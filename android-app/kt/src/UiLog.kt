package com.openmp.app

import android.util.Log
import java.io.File

/** 跨类共享 UI 调试日志（App 主进程内各组件都可写 app_log.txt）。 */
object UiLog {
    @Volatile var dir: File? = null

    fun log(m: String) {
        val d = dir
        if (d != null) {
            try {
                val lf = File(d, "app_log.txt")
                lf.parentFile?.mkdirs()
                lf.appendText("[UI] " + m + "\n")
            } catch (e: Exception) {}
        }
        Log.d("OMP", m)
    }
}
