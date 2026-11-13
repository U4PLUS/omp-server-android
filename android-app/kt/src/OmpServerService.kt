package com.openmp.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.os.Environment
import android.os.IBinder
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class OmpServerService : Service() {
    @Volatile private var hbRun = false
    @Volatile private var manualStop = false
    private var hbThread: Thread? = null
    companion object {
        const val CHANNEL_ID = "omp_channel"
        const val ACTION_START = "com.openmp.app.START"
        const val ACTION_STOP = "com.openmp.app.STOP"
        const val ACTION_RESTART = "com.openmp.app.RESTART"
        const val ACTION_FRPC_START = "com.openmp.app.FRPC_START"
        const val ACTION_FRPC_STOP = "com.openmp.app.FRPC_STOP"
        const val ACTION_FTP_TOGGLE = "com.openmp.app.FTP_TOGGLE"
    }

    private var serverProc: Process? = null
    private var frpcProc: Process? = null
    private var frpcOn = false
    private var ftpServer: FtpServer? = null
    private var logFile: File? = null
    private var serverLogOut: File? = null
    private var stateFile: File? = null
    private var frpcStateFile: File? = null
    private var lastNotifyAt = 0L

    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL_ID, "open.mp Server", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        frpcStateFile = File(getExternalFilesDir(null), "frpc_state.txt")
        loadFrpcState()
        // 服务创建时自动启动 FTP（若设置页开关开着）；独立于 omp-server 是否运行
        Thread { startFtpServer() }.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopServer(); log("Stopped by user."); stopSelf(); return START_NOT_STICKY }
            ACTION_RESTART -> { stopServer(); thread { startServer() }; return START_STICKY }
            ACTION_FRPC_START -> { startForeground(1, buildNotification("FRP starting...")); thread { startFrpc() }; return START_STICKY }
            ACTION_FRPC_STOP -> { stopFrpc(); log("FRP stopped by user."); return START_STICKY }
            ACTION_FTP_TOGGLE -> { thread { startFtpServer() }; return START_STICKY }
            else -> {
                if (serverProc?.isAlive == true) { log("Server already running."); return START_STICKY }
                startForeground(1, buildNotification("Starting..."))
                thread { startServer() }
                return START_STICKY
            }
        }
    }

    private fun buildNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("open.mp Server")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()

    private fun thread(block: () -> Unit) = Thread(block).start()

    private fun startServer() {
        manualStop = false
        try {
            val dir = File(filesDir, "server").apply { mkdirs() }
            File(dir, "plugins").mkdirs()
            File(dir, "scriptfiles").mkdirs()
            logFile = File(getExternalFilesDir(null), "app_log.txt")
            stateFile = File(filesDir, "state.txt")
            log("=== session ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ===")
            log("Data dir : ${dir.absolutePath}")

            val assetVer = "243"
            val verFile = File(dir, ".asset_ver")
            val needRefresh = try { !verFile.exists() || verFile.readText().trim() != assetVer } catch (e: Exception) { true }
            if (needRefresh) {
                // 只补缺失，绝不覆盖已有（保护用户改过的 config.json / gamemodes / frpc.toml 等）
                copyMissing("server", dir)
                try { verFile.writeText(assetVer) } catch (e: Exception) {}
                log("assets refreshed to v$assetVer (missing-only, user edits kept)")
            } else {
                log("assets up to date (v$assetVer), keeping user edits")
            }
            // 每次启动：检查 components / pawncc 与 assets 对比，缺失的补上（不覆盖用户已有文件）
            ensureRequiredAssets(dir)
            syncExternal(dir)
            mergeGamemodes(dir)

            val exe = File(dir, "omp-server")
            AppState.phase = "starting"
            AppState.running = true
            writeState("1")
            serverLogOut = File(getExternalFilesDir(null), "server_log.txt")
            try { if (serverLogOut!!.exists()) serverLogOut!!.delete() } catch (e: Exception) {}
            log("server log -> " + serverLogOut!!.absolutePath + " (cleared on restart)")
            log("Launching omp-server...")
            val ok = launch(exe, dir)
            if (!ok) {
                AppState.lastError = "exec failed (see log)"
                log("LAUNCH FAILED.")
                writeState("0")
                notifyLog("Launch failed - see log")
                return
            }
            AppState.running = true
            Thread.sleep(800)
            AppState.pid = sh("pidof omp-server").toLongOrNull() ?: 0L
            startFtpServer()
            AppState.startMillis = System.currentTimeMillis()
            AppState.port = readPort(File(dir, "config.json"))
            AppState.gamemode = readGamemode(dir)
            AppState.phase = "starting"
            writeState("1")
            log("PID ${AppState.pid} running. Port=${AppState.port} Gamemode=${AppState.gamemode}")
            hbRun = true
            hbThread = Thread {
                var i = 0
                try {
                    while (hbRun) {
                        AppState.lastBeat = System.currentTimeMillis()
                        if (i < 4) AppState.phase = "starting" else AppState.phase = "running"
                        i++
                        writeState(if (AppState.running) "1" else "0")
                        Thread.sleep(2000)
                    }
                } catch (e: InterruptedException) {}
            }.apply { isDaemon = true; start() }
            readStream(serverProc!!.inputStream)
            val rc = serverProc!!.waitFor()
            hbRun = false
            AppState.running = false
            AppState.phase = "stopped"
            writeState("0")
            log("Server exited, code=$rc")
            exportLogToExternal(dir)
            notifyLog("Server exited ($rc)")
            // 崩溃/意外退出自动重启（区分人为手动停止）
            try {
                val autoRestart = getSharedPreferences("omp", Context.MODE_PRIVATE).getBoolean("auto_restart", false)
                if (!manualStop && autoRestart) {
                    log("Unexpected exit detected - auto restarting in 1s")
                    notifyLog("Auto restarting...")
                    Thread.sleep(1000)
                    thread { startServer() }
                    return
                }
            } catch (e: Exception) { log("auto-restart check: $e") }
        } catch (e: Exception) {
            AppState.lastError = e.toString()
            log("FAILED: $e")
            writeState("0")
            notifyLog("Failed: $e")
        }
    }

    private fun launch(exe: File, dir: File): Boolean {
        log("DIAG android=${android.os.Build.VERSION.RELEASE} sdk=${android.os.Build.VERSION.SDK_INT}")
        val p = exe.absolutePath
        log("DIAG exists=${exe.exists()} size=${exe.length()} canExec=${exe.canExecute()}")

        // 0) nativeLibraryDir (extractNativeLibs): the only system-sanctioned executable spot
        try {
            val libDir = File(applicationInfo.nativeLibraryDir)
            if (libDir.isDirectory) {
                log("DIAG nativeLibraryDir: " + libDir.absolutePath)
                log("DIAG " + sh("ls -lZ " + libDir.absolutePath))
                val serverBin = File(libDir, "libompserver.so")
                if (serverBin.isFile) {
                    log("DIAG try native lib exec: ${serverBin.absolutePath} canExec=${serverBin.canExecute()}")
                    if (tryExec(serverBin.absolutePath, dir)) return true
                } else {
                    log("DIAG libompserver.so missing in " + libDir.absolutePath)
                }
            } else {
                log("DIAG nativeLibraryDir missing: " + applicationInfo.nativeLibraryDir)
            }
        } catch (e: Exception) { log("DIAG libdir: ${e.message}") }

        if (tryExec(p, dir)) return true

        sh("chmod 755 '$p'")
        log("DIAG after chmod canExec=${exe.canExecute()}")
        if (tryExec(p, dir)) return true

        val ls = sh("ls -lZ '$p'")
        log("DIAG ls -lZ: $ls")
        if (tryExec(p, dir)) return true

        // sh -c exec: capture exit code to distinguish real exec vs shell error
        try {
            val pb = ProcessBuilder("/system/bin/sh", "-c", "exec '$p' 2>&1")
            pb.directory(dir)
            serverProc = pb.start()
            log("DIAG sh-exec process started, waiting 2s...")
            val exited = serverProc!!.waitFor(2, TimeUnit.SECONDS)
            if (!exited || serverProc!!.isAlive) {
                log("DIAG sh-exec appears alive (real exec) -> keep as server process")
                return true
            }
            val code = serverProc!!.exitValue()
            val out = readAll(serverProc!!.inputStream)
            log("DIAG sh-exec exited code=$code out='${out.trim()}'")
            serverProc = null
        } catch (e: IOException) {
            log("DIAG sh-exec threw: ${e.message}")
        }

        // extra candidate dirs
        for (cand in listOf(codeCacheDir, File(filesDir, "app_webview"))) {
            var ok = false
            try {
                cand.mkdirs()
                val c = File(cand, "omp-server")
                exe.copyTo(c, overwrite = true)
                c.setExecutable(true, false)
                log("DIAG try dir: ${c.absolutePath} canExec=${c.canExecute()}")
                ok = tryExec(c.absolutePath, dir)
                if (ok) { log("Running from ${c.absolutePath}"); return true }
            } catch (e: Exception) { log("DIAG cand ${cand.absolutePath}: ${e.message}") }
        }

        File(cacheDir, "omp-server").let { c ->
            try {
                exe.copyTo(c, overwrite = true)
                c.setExecutable(true, false)
                log("DIAG retry from cache: $c canExec=${c.canExecute()}")
                if (tryExec(c.absolutePath, dir)) { log("Running from cache dir"); return true }
            } catch (e: IOException) { log("DIAG cache copy: ${e.message}") }
        }
        return false
    }

    private fun tryExec(path: String, dir: File): Boolean {
        return try {
            val pb = ProcessBuilder(path)
            pb.directory(dir)
            pb.redirectErrorStream(true)
            serverLogOut?.let { pb.redirectOutput(it) }
            serverProc = pb.start()
            log("Launched: $path")
            true
        } catch (e: IOException) {
            log("DIAG exec('$path') -> ${e.message}")
            false
        }
    }

    private fun sh(cmd: String): String = try {
        val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", cmd))
        val s = readAll(proc.inputStream)
        proc.waitFor(5, TimeUnit.SECONDS)
        s.trim()
    } catch (e: Exception) { "sh-err:$e" }

    private fun readAll(input: InputStream): String {
        val r = BufferedReader(InputStreamReader(input))
        val sb = StringBuilder()
        var line: String?
        while (true) { line = r.readLine() ?: break; sb.append(line).append('\n') }
        return sb.toString()
    }

    /** 启动 FTP 服务器（独立于 omp-server；由设置页开关控制，开→自动启动）。 */
    private fun startFtpServer() {
        try {
            val dir = File(filesDir, "server").apply { mkdirs() }
            val prefs = getSharedPreferences("omp", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("ftp_enabled", true)) { log("FTP disabled by settings"); stopFtpServer(); return }
            if (ftpServer?.running == true) { log("FTP already running"); return }
            val ftpPort = prefs.getInt("ftp_port", 2122)
            var started = false
            for (attempt in 0..4) {
                try {
                    val trial = if (attempt == 0) ftpPort else ftpPort + attempt
                    val ftpUser = prefs.getString("ftp_user", "").orEmpty()
                    val ftpPass = prefs.getString("ftp_pass", "").orEmpty()
                    val fs = FtpServer(dir, trial, ftpUser, ftpPass)
                    // FTP 命令日志（诊断 MT 等客户端握手/登录问题）
                    val ftpLogFile = File(getExternalFilesDir(null), "ftp_log.txt")
                    fs.logSink = { s ->
                        try { ftpLogFile.appendText("[" + SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) + "] $s\n") } catch (e: Exception) {}
                    }
                    try { ftpLogFile.writeText("") } catch (e: Exception) {}
                    fs.start()
                    ftpServer = fs
                    log("FTP server listening on :" + trial + " (root " + dir.absolutePath + ", user='" + ftpUser + "')")
                    started = true
                    break
                } catch (e: Exception) { log("FTP start :" + (ftpPort + attempt) + " failed: " + e.message) }
            }
            if (!started) log("FTP could not bind any port")
        } catch (e: Exception) { log("FTP start error: " + e.message) }
    }

    /** 停止 FTP 服务器。 */
    private fun stopFtpServer() {
        ftpServer?.let { try { it.stop() } catch (e: Exception) {} }
        ftpServer = null
        log("FTP stopped")
    }

    private fun stopServer() {
        manualStop = true
        // FTP 独立于服务器：停止服务器时保留 FTP（只要设置页开关开着）
        AppState.running = false
        serverProc?.let { proc ->            proc.destroy()
            try { proc.waitFor(3000, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) {}
            if (proc.isAlive) proc.destroyForcibly()
        }
        serverProc = null
        writeState("0")
    }

    private fun readStream(input: InputStream) {
        thread {
            try {
                BufferedReader(InputStreamReader(input)).useLines { lines ->
                    lines.forEach { log(it) }
                }
            } catch (e: IOException) {}
        }
    }

    // ---------- FRP (frpc 内网穿透) ----------
    private fun frpcDir(): File = File(filesDir, "server").apply { mkdirs() }.let { File(it, "frpc") }

    private fun frpcConfig(): File = File(frpcDir(), "frpc.toml")

    private fun startFrpc() {
        manualStop = false
        try {
            if (frpcProc?.isAlive == true) { log("FRP already running."); return }
            val cfg = frpcConfig()
            val dir = frpcDir()
            log("=== FRP start ===")
            log("cfg : ${cfg.absolutePath} exists=${cfg.exists()}")
            if (!cfg.exists()) {
                try { copyAssets("server/frpc/frpc.toml", frpcDir()) } catch (e: Exception) { log("FRP cfg restore: ${e.message}") }
            }
            if (!cfg.exists()) { log("FRP: frpc.toml missing."); return }

            // 从 nativeLibraryDir 找 libfrpc.so（系统认可的可执行位置，与 omp-server 一致）
            val exe = File(applicationInfo.nativeLibraryDir, "libfrpc.so")
            if (!exe.exists()) {
                log("FRP: libfrpc.so missing in " + applicationInfo.nativeLibraryDir)
                return
            }
            log("frpc: ${exe.absolutePath} size=${exe.length()}")
            val pb = ProcessBuilder(exe.absolutePath, "-c", cfg.absolutePath)
            pb.directory(dir)
            pb.redirectErrorStream(true)
            val out = File(getExternalFilesDir(null), "frpc_log.txt")
            try { if (out.exists()) out.delete() } catch (e: Exception) {}
            pb.redirectOutput(out)
            frpcProc = pb.start()
            // 等 2 秒判断是否真正起来（frpc 若配置错误会立即退出）
            val exited = frpcProc!!.waitFor(2, TimeUnit.SECONDS)
            if (exited && !frpcProc!!.isAlive) {
                val code = frpcProc!!.exitValue()
                val err = try { out.readText().take(2000) } catch (e: Exception) { "" }
                log("FRP exited code=$code\n$err")
                frpcProc = null
                frpcOn = false
                writeFrpcState()
                notifyLog("FRP failed (code $code) - see frpc_log")
                return
            }
            frpcOn = true
            writeFrpcState()
            log("FRP running (pid via pidof)")
            notifyLog("FRP running")
            // 读日志流（frpc 持续输出）
            readStream(FileInputStream(out))
            frpcProc!!.waitFor()
            frpcOn = false
            writeFrpcState()
            log("FRP exited")
            notifyLog("FRP exited")
        } catch (e: Exception) {
            frpcOn = false
            writeFrpcState()
            log("FRP FAILED: $e")
            notifyLog("FRP failed: $e")
        }
    }

    private fun stopFrpc() {
        try { frpcProc?.let { proc -> proc.destroy(); try { proc.waitFor(2000, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) {}; if (proc.isAlive) proc.destroyForcibly() } } catch (e: Exception) {}
        frpcProc = null
        frpcOn = false
        writeFrpcState()
        log("FRP stopped")
    }

    private fun frpcRunning(): Boolean {
        // 持久状态 + 实际 pidof 双查
        try {
            val pid = sh("pidof frpc").trim().toLongOrNull() ?: 0L
            return pid > 0
        } catch (e: Exception) { return false }
    }

    private fun writeFrpcState() {
        val text = "running=${if (frpcOn) "1" else "0"}\n"
        frpcStateFile?.let { f -> try { f.writeText(text) } catch (e: Exception) {} }
        try { val ext = getExternalFilesDir(null)?.let { File(it, "frpc_state.txt") }; ext?.writeText(text) } catch (e: Exception) {}
    }

    private fun loadFrpcState() {
        val ext = getExternalFilesDir(null)?.let { File(it, "frpc_state.txt") }
        if (ext != null && ext.exists()) {
            try {
                val m = HashMap<String, String>()
                ext.readLines().forEach { l -> val i = l.indexOf('='); if (i > 0) m[l.substring(0, i)] = l.substring(i + 1) }
                frpcOn = m["running"] == "1"
            } catch (e: Exception) {}
        }
    }

    @Synchronized
    private fun log(s: String) {
        AppState.append(s)
        logFile?.let { f ->
            try { FileWriter(f, true).use { it.write(s + "\n") } } catch (e: IOException) {}
        }
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt > 1500) {  // throttle notifications
            lastNotifyAt = now
            notifyLog("Running...")
        }
    }

    private fun notifyLog(text: String) {
        getSystemService(NotificationManager::class.java).notify(1, buildNotification(text))
    }

    private fun copyAssets(path: String, targetDir: File) {
        val am = assets
        val entries = am.list(path) ?: return
        for (e in entries) {
            val out = File(targetDir, e)
            val child = "$path/$e"
            try {
                am.open(child).use { input -> FileOutputStream(out).use { os ->
                    val buf = ByteArray(65536)
                    while (true) { val n = input.read(buf); if (n <= 0) break; os.write(buf, 0, n) }
                } }
            } catch (e: IOException) {
                log("Extracting dir: $child")
                if (!out.mkdirs()) log("mkdir failed: $out (maybe exists)")
                copyAssets(child, out)
            }
        }
    }

    /**
     * 只补缺失的资源：对比 assets 的子目录与目标，仅把目标里【不存在】的文件补上。
     * 已存在的文件一律保留（不覆盖），从而避免覆盖用户修改过的 config.json 等文件。
     */
    private fun copyMissing(path: String, targetDir: File) {
        val am = assets
        val entries = am.list(path) ?: return
        for (e in entries) {
            val out = File(targetDir, e)
            val child = "$path/$e"
            if (out.exists()) {
                if (out.isDirectory) copyMissing(child, out)
                continue // 文件已存在 → 保留用户版本，不覆盖
            }
            try {
                am.open(child).use { input -> FileOutputStream(out).use { os ->
                    val buf = ByteArray(65536)
                    while (true) { val n = input.read(buf); if (n <= 0) break; os.write(buf, 0, n) }
                } }
            } catch (e: IOException) {
                if (!out.mkdirs()) log("mkdir failed: $out (maybe exists)")
                copyMissing(child, out)
            }
        }
    }

    /**
     * 每次启动时确保运行必需的资源齐全：components（组件库）与 pawncc（编译器）。
     * 只对比 assets 里同名资源是否缺失，缺失则补上；其它目录（config、gamemodes 等）不检查、不覆盖。
     */
    private fun ensureRequiredAssets(dir: File) {
        copyMissing("server/components", File(dir, "components"))
        copyMissing("server/pawncc", File(dir, "pawncc"))
        log("ensured required assets (components/pawncc)")
    }

    private fun hasStoragePerm(): Boolean =
        checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun externalDir(): File? = try {
        val base = Environment.getExternalStorageDirectory()
        val d = File(base, "openmp-server")
        if (!d.exists()) d.mkdirs()
        d
    } catch (e: Exception) { null }

    /** Mirror server content (config/gamemodes/filterscripts/logs) to public /sdcard/openmp-server
     *  so MT manager / FTP can edit them directly; import edits back on every start. */
    private fun syncExternal(dir: File) {
        try {
            val ext = externalDir() ?: return
            if (!hasStoragePerm()) {
                log("External sync skipped: WRITE_EXTERNAL_STORAGE not granted. Path: " + ext.absolutePath)
                return
            }
            File(ext, "gamemodes").mkdirs()
            File(ext, "filterscripts").mkdirs()
            File(dir, "gamemodes").mkdirs()
            File(dir, "filterscripts").mkdirs()
            // 1) export defaults (inner -> outer) only when outer file missing
            File(dir, "config.json").let { if (it.exists() && !File(ext, "config.json").exists()) it.copyTo(File(ext, "config.json")) }
            File(dir, "gamemodes").listFiles()?.forEach { f -> if (f.isFile) { val t = File(File(ext, "gamemodes"), f.name); if (!t.exists()) f.copyTo(t) } }
            File(dir, "filterscripts").listFiles()?.forEach { f -> if (f.isFile) { val t = File(File(ext, "filterscripts"), f.name); if (!t.exists()) f.copyTo(t) } }
            // 2) import edits (outer -> inner, overwrite)
            File(ext, "config.json").let { if (it.exists()) it.copyTo(File(dir, "config.json"), overwrite = true) }
            File(ext, "gamemodes").listFiles()?.forEach { f -> if (f.isFile && f.name.endsWith(".amx")) f.copyTo(File(File(dir, "gamemodes"), f.name), overwrite = true) }
            File(ext, "filterscripts").listFiles()?.forEach { f -> if (f.isFile && f.name.endsWith(".amx")) f.copyTo(File(File(dir, "filterscripts"), f.name), overwrite = true) }
            log("External sync OK: " + ext.absolutePath)
        } catch (e: Exception) { log("syncExternal: " + e) }
    }

    private fun exportLogToExternal(dir: File) {
        try {
            val ext = externalDir() ?: return
            if (!hasStoragePerm()) return
            File(dir, "server_log.txt").let { if (it.exists()) it.copyTo(File(ext, "server_log.txt"), overwrite = true) }
        } catch (e: Exception) {}
    }

    private fun mergeGamemodes(dir: File) {
        try {
            val ext = getExternalFilesDir("gamemodes") ?: return
            if (!ext.exists()) return
            val gm = File(dir, "gamemodes").apply { mkdirs() }
            var n = 0
            ext.listFiles()?.forEach { f -> if (f.isFile) { f.copyTo(File(gm, f.name), overwrite = true); n++ } }
            log("Gamemodes merged: $n file(s)")
        } catch (e: Exception) { log("mergeGamemodes: $e") }
    }

    private fun readGamemode(dir: File): String {
        return try {
            val j = org.json.JSONObject(File(dir, "config.json").readText())
            val pawn = j.optJSONObject("pawn")
            val ms = pawn?.optJSONArray("main_scripts") ?: return ""
            (0 until ms.length()).joinToString(", ") { ms.optString(it) }
        } catch (e: Exception) { "" }
    }

    private fun readPort(cfg: File): Int {
        if (!cfg.exists()) return 7777
        return try {
            val txt = cfg.readText()
            val m = Regex("\"port\"\\s*:\\s*(\\d+)").find(txt)
            m?.groupValues?.get(1)?.toInt() ?: 7777
        } catch (e: Exception) { 7777 }
    }

    private fun writeState(running: String) {
        val text = "running=$running\nphase=${AppState.phase}\npid=${AppState.pid}\nport=${AppState.port}\nstart=${AppState.startMillis}\nlastbeat=${AppState.lastBeat}\ngamemode=${AppState.gamemode}\nerror=${AppState.lastError.replace("\n", " | ")}\n"
        stateFile?.let { f ->
            try { f.writeText(text) } catch (e: IOException) {}
        }
        try {
            val ext = getExternalFilesDir(null)?.let { File(it, "state.txt") }
            ext?.writeText(text)
        } catch (e: IOException) {}
    }

    override fun onDestroy() { hbRun = false; stopServer(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

object AppState {
    var running = false
    var pid = 0L
    var port = 7777
    var startMillis = 0L
    var lastError = ""
    var phase = "stopped"
    var lastBeat = 0L
    var gamemode = ""
    private val buf = StringBuilder()

    @Synchronized
    fun append(s: String) {
        if (buf.length > 80000) buf.delete(0, 20000)
        if (buf.isNotEmpty() && buf.last() != '\n') buf.append('\n')
        buf.append(s).append('\n')
    }

    @Synchronized
    fun text(): String = buf.toString()

    @Synchronized
    fun clear() { buf.setLength(0) }
}
