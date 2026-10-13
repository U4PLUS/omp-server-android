package com.openmp.app

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {
    companion object {
        lateinit var ctx: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        ctx = applicationContext
        // 仅主进程：清空以前的日志，开新会话头（服务进程不覆盖，避免冲掉 UI 标记）
        if (isMainProcess()) {
            for (dir in listOf(getExternalFilesDir(null), cacheDir, filesDir)) {
                if (dir == null) continue
                try {
                    val f = File(dir, "app_log.txt")
                    f.parentFile?.mkdirs()
                    f.writeText("[APP START ${now()}]\n")
                } catch (ignored: Exception) {}
            }
        }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            var written = false
            for (dir in listOf(getExternalFilesDir(null), cacheDir, filesDir)) {
                if (dir == null) continue
                try {
                    val stack = Log.getStackTraceString(e)
                    File(dir, "crash.txt").appendText("==== crash ${now()} thread=${thread.name} ====\n$stack\nprev=$prev\n")
                    File(dir, "app_log.txt").appendText("\n[CRASH] ${now()} thread=${thread.name}\n$stack\n")
                    written = true
                    break
                } catch (ignored: Exception) {}
            }
            if (!written) {
                try { Log.e("omp", "CRASH (no file): ${thread.name}", e) } catch (ignored: Exception) {}
            }
        }
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    private fun isMainProcess(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val myPid = android.os.Process.myPid()
            am.runningAppProcesses?.any { it.pid == myPid && it.processName == packageName } ?: true
        } catch (e: Exception) { true }
    }
}
