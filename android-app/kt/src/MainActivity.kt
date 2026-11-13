package com.openmp.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import java.nio.charset.Charset
import java.util.Locale
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.RandomAccessFile

class MainActivity : Activity() {
    companion object {
        @Volatile var darkOverride = "auto"
        private const val REQ_OPEN = 1001
        private const val REQ_SAVE = 1002
        private const val REQ_IMPORT_FRP = 1003
        @Volatile var userLang: String? = null



        private const val ACTION_START = "com.openmp.app.START"
        private const val ACTION_STOP = "com.openmp.app.STOP"
        private const val ACTION_RESTART = "com.openmp.app.RESTART"
        private const val ACTION_FRPC_START = "com.openmp.app.FRPC_START"
        private const val ACTION_FRPC_STOP = "com.openmp.app.FRPC_STOP"
    }

    // ---------- 深色模式（跟随系统） ----------
    private fun isDark(): Boolean = when (darkOverride) {
        "light" -> false
        "dark" -> true
        else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun col(l: String, d: String): Int = Color.parseColor(if (isDark()) d else l)
    private fun bgC(): Int = col("#FAFAFA", "#121212")
    private fun textC(): Int = col("#212121", "#E0E0E0")
    private fun dimC(): Int = col("#757575", "#9E9E9E")
    private fun accentC(): Int = col("#1976D2", "#90CAF9")
    private fun dividerC(): Int = col("#E0E0E0", "#333333")
    private fun redC(): Int = col("#C62828", "#EF9A9A")
    private fun greenC(): Int = col("#2E7D32", "#81C784")
    private fun surfaceC(): Int = col("#FFFFFF", "#1E1E1E")
    private fun onAccent(): Int = if (isDark()) Color.parseColor("#121212") else Color.WHITE

    private val prefs get() = getSharedPreferences("omp", Context.MODE_PRIVATE)
    private lateinit var serverDir: File
    private lateinit var fileCurrentDir: File
    private lateinit var pendingUploadDir: File
    private lateinit var root: LinearLayout
    private lateinit var pageBox: LinearLayout
    private lateinit var navButtons: MutableList<Button>
    private var currentPage = 0
    private var stateText: TextView? = null
    private var portText: TextView? = null
    private var pidText: TextView? = null
    private var uptimeText: TextView? = null
    private var gmText: TextView? = null
    private var errText: TextView? = null
    private var frpStatusText: TextView? = null
    private var pathText: TextView? = null
    private var fileListBox: LinearLayout? = null
    private var logView: TextView? = null
    private var logFile: File? = null
    private var serverLogView: TextView? = null
    private var serverLogFile: File? = null
    private var serverLogScroll: ScrollView? = null
    private val userScrolled = HashMap<TextView, Boolean>()
    private val lastLog = HashMap<TextView, String>()
    private var prevServerActive = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (MainActivity.userLang != null) L.lang = MainActivity.userLang!! else L.init(resources.configuration.locale.language)
        try {
            val lf = File(getExternalFilesDir(null) ?: filesDir, "app_log.txt")
            lf.parentFile?.mkdirs()
            lf.appendText("[MAIN onCreate]\n")
        } catch (e: Exception) {}
        darkOverride = prefs.getString("dark_mode", "auto") ?: "auto"
        // App 启动：若服务器进程未在运行（心跳过期），清掉旧状态与旧日志；重启服务器后再重新产生
        try {
            val sf = File(filesDir, "state.txt")
            val serverAlive = sf.exists() && try {
                val m = HashMap<String, String>()
                sf.readLines().forEach { l -> val i = l.indexOf('='); if (i > 0) m[l.substring(0, i)] = l.substring(i + 1) }
                System.currentTimeMillis() - (m["lastbeat"]?.toLongOrNull() ?: 0L) < 10000
            } catch (e: Exception) { false }
            if (!serverAlive) {
                sf.delete()
                try { File(getExternalFilesDir(null), "state.txt").delete() } catch (e: Exception) {}
                try { File(getExternalFilesDir(null), "server_log.txt").delete() } catch (e: Exception) {}
            }
        } catch (e: Exception) {}
        try {
            window.statusBarColor = bgC()
            uiLog("nav pre")
            try {
                window.navigationBarColor = bgC()
                val dv = window.decorView
                val flags = dv.systemUiVisibility
                dv.systemUiVisibility = if (isDark()) {
                    flags and android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                } else {
                    flags or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                }
            } catch (e: Exception) {}
            uiLog("nav post")
            // 打开 APP 自动启动 FTP（若设置页开关开着）；独立于 omp-server 是否运行
            try {
                if (prefs.getBoolean("ftp_enabled", true)) send(OmpServerService.ACTION_FTP_TOGGLE)
            } catch (e: Exception) {}
            serverDir = File(filesDir, "server")
            fileCurrentDir = serverDir
            pendingUploadDir = serverDir
            logFile = File(getExternalFilesDir(null), "app_log.txt")
            serverLogFile = File(getExternalFilesDir(null), "server_log.txt")

            root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bgC()) }
            pageBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            root.addView(pageBox)
            navButtons = mutableListOf()
            root.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(surfaceC())
                setElevation(dp(6).toFloat())
                val tabs = arrayOf(L.t("tab_server"), L.t("tab_files"), L.t("tab_log"), L.t("tab_settings"))
                for ((i, name) in tabs.withIndex()) {
                    val b = Button(this@MainActivity)
                    b.text = name
                    b.isAllCaps = false
                    b.textSize = 13f
                    b.typeface = Typeface.DEFAULT_BOLD
                    b.setBackgroundColor(surfaceC())
                    b.setTextColor(dimC())
                    b.layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f)
                    b.setOnClickListener { showPage(i) }
                    addView(b)
                    navButtons.add(b)
                }
            })
            setContentView(root)
            uiLog("showpage0 pre")
            showPage(0)
            uiLog("showpage0 post")
            try {
                val lf = File(getExternalFilesDir(null) ?: filesDir, "app_log.txt")
                lf.parentFile?.mkdirs()
                lf.appendText("[MAIN onCreate done]\n")
            } catch (e: Exception) {}
        } catch (t: Throwable) {
            try {
                val lf = File(getExternalFilesDir(null) ?: filesDir, "app_log.txt")
                lf.parentFile?.mkdirs()
                var lines = 0
                val sb = java.lang.StringBuilder(24000)
                val pw = java.io.PrintWriter(object : java.io.Writer() {
                    override fun write(cbuf: CharArray, off: Int, len: Int) {
                        if (lines < 120) sb.append(cbuf, off, len)
                        for (i in off until off + len) if (cbuf[i] == '\n') lines++
                    }
                    override fun flush() {}
                    override fun close() {}
                })
                t.printStackTrace(pw)
                lf.appendText("[MAIN UI FAILED] " + t + "\n" + sb.toString().take(6000) + "\n")
            } catch (e: Exception) {}
            throw t
        }
        tick()
    }

    private fun uiLog(m: String) {
        try {
            val lf = File(getExternalFilesDir(null) ?: filesDir, "app_log.txt")
            lf.parentFile?.mkdirs()
            lf.appendText("[UI] " + m + "\n")
        } catch (e: Exception) {}
    }

    private fun showPage(i: Int) {
        currentPage = i
        pageBox.removeAllViews()
        when (i) {
            0 -> { uiLog("build page server"); pageBox.addView(buildServerPage()); uiLog("done page server") }
            1 -> { uiLog("build page files"); pageBox.addView(buildFilesPage()); uiLog("done page files") }
            2 -> { uiLog("build page log"); pageBox.addView(buildLogPage()); uiLog("done page log") }
            3 -> { uiLog("build page settings"); pageBox.addView(buildSettingsPage()); uiLog("done page settings") }
        }
        if (i == 0) scrollBottom(serverLogScroll)
        for ((j, b) in navButtons.withIndex()) {
            b.setTextColor(if (j == i) accentC() else dimC())
        }
    }

    // ---------- 服务器页 ----------
    private fun buildServerPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = dp(20)
        uiLog("sp pad " + pad)

        // 右上角 FRP 标志按钮行
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(pad, dp(8), pad, 0)
            frpStatusText = tv(L.t("frp_title"), 11f, dimC())
            frpStatusText!!.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(frpStatusText!!)
            addView(button(L.t("frp_title")) { showFrpDialog() }.apply {
                layoutParams = LinearLayout.LayoutParams(dp(96), dp(38))
            })
        })
        uiLog("sp frp-row-done")

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(32), pad, dp(24))
            uiLog("sp tv1")
            stateText = tv("--", 26f, textC(), true)
            addView(stateText!!)
            portText = tv("", 13f, dimC())
            addView(portText!!)
            pidText = tv("", 13f, dimC())
            addView(pidText!!)
            uptimeText = tv("", 13f, dimC())
            addView(uptimeText!!)
            gmText = tv("", 13f, dimC())
            addView(gmText!!)
            errText = tv("", 13f, redC())
            addView(errText!!)
        })
        uiLog("sp state-done")

        uiLog("bt a")
        val btl = LinearLayout(this)
        btl.orientation = LinearLayout.HORIZONTAL
        btl.setPadding(pad, 0, pad, dp(8))
        val lp2 = LinearLayout.LayoutParams(0, dp(46), 1f)
        lp2.marginEnd = dp(8)
        uiLog("bt b")
        val start = Button(this)
        start.text = L.t("btn_start")
        start.isAllCaps = false
        start.textSize = 13f
        start.setBackgroundColor(accentC())
        start.setTextColor(onAccent())
        start.setOnClickListener { send(ACTION_START) }
        start.layoutParams = lp2
        btl.addView(start)
        uiLog("bt c")
        val stop = Button(this)
        stop.text = L.t("btn_stop")
        stop.isAllCaps = false
        stop.textSize = 13f
        stop.setBackgroundColor(accentC())
        stop.setTextColor(onAccent())
        stop.setOnClickListener { send(ACTION_STOP) }
        stop.layoutParams = lp2
        btl.addView(stop)
        uiLog("bt d")
        val restart = Button(this)
        restart.text = L.t("btn_restart")
        restart.isAllCaps = false
        restart.textSize = 13f
        restart.setBackgroundColor(accentC())
        restart.setTextColor(onAccent())
        restart.setOnClickListener { send(ACTION_RESTART) }
        restart.layoutParams = lp2
        btl.addView(restart)
        uiLog("bt e")
        page.addView(btl)
        uiLog("sp buttons-done")

        page.addView(sectionDivider())
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(4))
            addView(tv(L.t("state_servelog"), 11f, dimC(), true))
        })
        serverLogView = TextView(this).apply {
            setTextColor(textC())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(4), dp(16), dp(12))
            text = ""
        }
        val sls = ScrollView(this)
        sls.addView(serverLogView)
        sls.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        attachAutoFollow(sls, serverLogView!!)
        serverLogScroll = sls
        page.addView(sls)
        uiLog("sp done")
        return page
    }

    // ---------- 文件页 ----------
    private fun buildFilesPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pathText = tv("/", 12f, accentC(), true)
        pathText!!.setPadding(dp(20), dp(16), dp(20), dp(4))
        page.addView(pathText)

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(20), 0, dp(20), 0)
            val lp = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            addView(button(L.t("btn_upload")) {
                pendingUploadDir = fileCurrentDir
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, REQ_OPEN)
            }.apply { layoutParams = lp })
            addView(button(L.t("btn_parent")) { goParent() }.apply { layoutParams = lp })
        })

        val scroll = ScrollView(this)
        fileListBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(fileListBox)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        page.addView(scroll)
        return page
    }

    private fun listFiles() {
        val box = fileListBox ?: return
        box.removeAllViews()
        pathText!!.text = fileCurrentDir.absolutePath.removePrefix(serverDir.absolutePath).let { if (it.isEmpty()) "/" else it }
        val files = fileCurrentDir.listFiles()?.sortedWith(compareBy(
            { if (it.isDirectory) if (it.isHidden) 0 else 1 else if (it.isHidden) 2 else 3 },
            { it.name.lowercase(Locale.US) }
        )) ?: emptyList()
        if (files.isEmpty()) {
            box.addView(tv(L.t("empty_files"), 13f, dimC()).apply { setPadding(dp(20), dp(16), dp(20), dp(8)) })
            return
        }
        for ((k, f) in files.withIndex()) {
            if (k > 0) box.addView(sectionDivider())
            box.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(12), dp(20), dp(12))
                val icon = if (f.isDirectory) "▸ " else "  "
                val meta = if (f.isDirectory) "dir" else "${f.length()} B"
                val nameView = tv(icon + f.name + "\n" + meta, 13f, if (f.isDirectory) accentC() else textC())
                nameView.typeface = Typeface.DEFAULT
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                nameView.layoutParams = lp
                nameView.setOnClickListener {
                    if (f.isDirectory) { fileCurrentDir = f; listFiles() }
                    else if (f.name.endsWith(".json") || f.name.endsWith(".txt") || f.name.endsWith(".log") || f.name.endsWith(".cfg") || f.name.endsWith(".conf")) showFileEditor(f)
                }
                addView(nameView)
                val more = button("⋯")
                more.layoutParams = LinearLayout.LayoutParams(dp(44), dp(36))
                more.setOnClickListener { showFileActions(f) }
                addView(more)
            })
        }
    }

    private fun showFileActions(f: File) {
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        if (f.name.endsWith(".pwn") || f.name.endsWith(".pawn")) {
            items.add(L.t("act_compile"))
            actions.add { compilePawn(f) }
        }
        items.add(L.t("act_edit"))
        actions.add { showFileEditor(f) }
        items.add(L.t("act_download"))
        actions.add { downloadFile(f) }
        items.add(L.t("act_delete"))
        actions.add { deleteFile(f) }
        AlertDialog.Builder(this).setTitle(f.name).setItems(items.toTypedArray()) { _, i -> actions[i]() }.show()
    }

    private fun compilePawn(src: File) {
        val libDir = applicationInfo.nativeLibraryDir
        val pawncc = File(libDir, "libpawncc.so")
        val include = File(serverDir, "pawncc/include")
        val out = File(src.parentFile, src.nameWithoutExtension + ".amx")
        if (!pawncc.exists()) {
            AlertDialog.Builder(this).setTitle("pawncc").setMessage("pawncc missing: $pawncc").setPositiveButton("OK", null).show()
            return
        }
        Thread {
            val sb = StringBuilder()
            var code = -1
            try {
                val pb = ProcessBuilder(pawncc.absolutePath, "-i" + include.absolutePath, "-o" + out.absolutePath, "-d3", src.absolutePath)
                pb.environment()["LD_LIBRARY_PATH"] = libDir
                pb.redirectErrorStream(true)
                val proc = pb.start()
                proc.inputStream.bufferedReader().forEachLine { sb.append(it).append("\n") }
                code = proc.waitFor()
            } catch (t: Throwable) { sb.append("exec failed: ").append(t).append("\n") }
            val ok = code == 0 && out.exists()
            val msg = (if (ok) "→ " + out.name + " (" + out.length() + " B)\n" else "") + sb.toString()
            val finalMsg = msg
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle(if (ok) "pawncc OK ✓" else "pawncc FAILED ($code)")
                    .setMessage(finalMsg)
                    .setNeutralButton(L.t("copy")) { _, _ ->
                        try {
                            (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                .setPrimaryClip(android.content.ClipData.newPlainText("pawncc", finalMsg))
                            android.widget.Toast.makeText(this, L.t("copied"), android.widget.Toast.LENGTH_SHORT).show()
                        } catch (t: Throwable) {}
                    }
                    .setPositiveButton("OK", null)
                    .show()
            }
        }.start()
    }

    private fun showFileEditor(f: File) {
        // 编码处理：优先 UTF-8；含非法 UTF-8 序列（常见于 GBK/GB2312/ANSI 保存的文件）
        // 时自动按 GBK 转码显示，保存统一为 UTF-8（无 BOM）——中文不再乱码/损坏
        var detected = ""
        val text: String = try {
            val bytes = f.readBytes()
            val utf8 = String(bytes, Charsets.UTF_8)
            if (utf8.contains('\uFFFD')) {
                detected = L.t("edit_enc_detected")
                if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
                    detected = L.t("edit_enc_bom") // BOM 也当作 UTF-8 处理，保存时自动去掉
                    String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
                } else {
                    String(bytes, Charset.forName("GBK"))
                }
            } else {
                utf8
            }
        } catch (t: Throwable) {
            detected = L.t("edit_enc_detected")
            String(f.readBytes(), Charset.forName("GBK"))
        }
        val input = EditText(this)
        input.setTextColor(textC())
        input.setTextSize(13f)
        input.typeface = Typeface.MONOSPACE
        input.setBackgroundColor(surfaceC())
        input.setText(text)
        input.setSelection(0)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
            if (detected.isNotEmpty()) addView(tv(detected, 11f, accentC()))
        }
        val scroll = ScrollView(this)
        scroll.addView(box)
        val dlg = AlertDialog.Builder(this)
            .setTitle(L.t("edit_title") + f.name)
            .setView(scroll)
            .setPositiveButton(L.t("save")) { _, _ ->
                try {
                    f.writeText(input.text.toString())
                    listFiles()
                } catch (t: Throwable) {
                    AlertDialog.Builder(this).setMessage("write failed: $t").setPositiveButton("OK", null).show()
                }
            }
            .setNegativeButton(L.t("cancel"), null)
            .create()
        try { dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(surfaceC())) } catch (e: Exception) {}
        dlg.show()
    }

    private fun downloadFile(f: File) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, f.name)
        }
        pendingDownload = f
        startActivityForResult(intent, REQ_SAVE)
    }
    private var pendingDownload: File? = null

    private fun deleteFile(f: File) {
        AlertDialog.Builder(this)
            .setMessage("${L.t("act_delete")} ${f.name}?")
            .setPositiveButton("OK") { _, _ -> if (f.isDirectory) f.deleteRecursively() else f.delete(); listFiles() }
            .setNegativeButton(L.t("cancel"), null)
            .show()
    }

    private fun goParent() {
        if (fileCurrentDir.absolutePath != serverDir.absolutePath) {
            fileCurrentDir = fileCurrentDir.parentFile ?: serverDir
            listFiles()
        }
    }

    // ---------- 日志页 ----------
    private fun buildLogPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logView = TextView(this).apply {
            setTextColor(textC())
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(12), dp(16), dp(12))
            text = ""
        }
        val scroll = ScrollView(this)
        scroll.addView(logView)
        scroll.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        attachAutoFollow(scroll, logView!!)
        page.addView(scroll)
        return page
    }

    private fun pullLog() { pullInto(logView, logFile) }

    private fun pullServerLog() { pullInto(serverLogView, serverLogFile) }

    private fun attachAutoFollow(sc: ScrollView, tv: TextView) {
        // 只有真实手势让视口离开底部时，才标记"用户上滑固定"；程序自动滚到底会清除该标记
        try {
            sc.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                val child = sc.getChildAt(0)
                val atBottom = child.height <= sc.height || child.height - scrollY - sc.height < 240
                userScrolled[tv] = !atBottom
            }
        } catch (e: Exception) {}
    }

    private fun scrollBottom(sc: ScrollView?) {
        if (sc == null) return
        try {
            sc.post { sc.post { sc.fullScroll(ScrollView.FOCUS_DOWN) } }
        } catch (e: Exception) {}
    }

    private fun pullInto(v: TextView?, f: File?) {
        val vv = v ?: return
        val ff = f ?: return
        // 选中/复制时完全暂停刷新，避免打断选取
        try {
            if (vv.selectionStart >= 0 && vv.selectionEnd > vv.selectionStart) return
        } catch (e: Exception) {}
        // 用户真手上滑离开底部 → 固定住不刷新；从未滚动或已在底部 → 每次刷新并自动滚到底（显示最新）
        if (userScrolled[vv] == true) return
        if (!ff.exists()) {
            // 服务器未运行时显示空日志（只在状态变化时更新一次，避免反复清空抖动）
            if (lastLog[vv] != "") { vv.text = ""; lastLog[vv] = "" }
            return
        }
        try {
            val raf = RandomAccessFile(ff, "r")
            val len = raf.length()
            val skip = if (len > 150000) len - 150000 else 0L
            raf.seek(skip)
            val buf = ByteArray((len - skip).toInt())
            raf.readFully(buf)
            raf.close()
            // 服务器日志固定按 GBK 解码（中文旧脚本输出），避免乱码
            val text = String(buf, Charset.forName("GBK"))
            // 内容无变化：不刷新、不滚动（消除上下拉扯）
            if (lastLog[vv] == text) return
            lastLog[vv] = text
            vv.text = text
            scrollBottom(vv.parent as? ScrollView)
        } catch (t: Throwable) { vv.text = "log read: $t" }
    }

    // ---------- 设置页 ----------
    private fun buildSettingsPage(): LinearLayout {
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pad = dp(20)

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(16), pad, dp(8))
            addView(tv(L.t("set_language"), 11f, dimC(), true))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
                val lp = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
                addView(button(L.t("lang_zh")) { setLanguage("zh") }.apply { layoutParams = lp })
                addView(button(L.t("lang_en")) { setLanguage("en") }.apply { layoutParams = lp })
            })
        })
        page.addView(sectionDivider())

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(8))
            addView(tv(L.t("set_darkmode"), 11f, dimC(), true))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
                val lp = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
                for ((key, label) in listOf(
                    "auto" to L.t("set_dark_auto"),
                    "light" to L.t("set_dark_light"),
                    "dark" to L.t("set_dark_dark")
                )) {
                    addView(button(label) {
                        prefs.edit().putString("dark_mode", key).apply()
                        recreate()
                    }.apply { layoutParams = lp })
                }
            })
        })
        page.addView(sectionDivider())

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(8))
            addView(tv(L.t("set_ftp"), 11f, dimC(), true))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                val lbl = tv(L.t("set_ftp_switch"), 14f, textC())
                lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(lbl)
                val sw = Switch(this@MainActivity)
                sw.isChecked = prefs.getBoolean("ftp_enabled", true)
                sw.setOnCheckedChangeListener { _, on ->
                    prefs.edit().putBoolean("ftp_enabled", on).apply()
                    // 立即生效：开→自动启动 FTP，关→停止 FTP（无需重启服务器）
                    try { send(OmpServerService.ACTION_FTP_TOGGLE) } catch (e: Exception) {}
                }
                addView(sw)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
                val lbl = tv(L.t("set_ftp_port"), 14f, textC())
                lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(lbl)
                val portInput = EditText(this@MainActivity).apply { setBackgroundColor(surfaceC()) }
                portInput.inputType = InputType.TYPE_CLASS_NUMBER
                portInput.setText(prefs.getInt("ftp_port", 2122).toString())
                portInput.setTextColor(textC())
                val plp = LinearLayout.LayoutParams(dp(120), dp(42))
                portInput.layoutParams = plp
                addView(portInput)
                val save = button(L.t("save"))
                save.layoutParams = LinearLayout.LayoutParams(dp(64), dp(42))
                save.setOnClickListener {
                    val v = portInput.text.toString().toIntOrNull() ?: 2122
                    prefs.edit().putInt("ftp_port", v).apply()
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(L.t("set_ftp_saved") + " $v (${L.t("set_ftp_restart")})")
                        .setPositiveButton("OK", null).show()
                }
                addView(save)
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, 0)
                val lbl = tv(L.t("set_ftp_account"), 14f, textC())
                lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(lbl)
                val userInput = EditText(this@MainActivity).apply { setBackgroundColor(surfaceC()) }
                userInput.setText(prefs.getString("ftp_user", ""))
                userInput.hint = L.t("set_ftp_user_hint")
                userInput.setTextColor(textC())
                userInput.layoutParams = LinearLayout.LayoutParams(dp(96), dp(42))
                addView(userInput)
                val passInput = EditText(this@MainActivity).apply { setBackgroundColor(surfaceC()) }
                passInput.setText(prefs.getString("ftp_pass", ""))
                passInput.hint = L.t("set_ftp_pass_hint")
                passInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                passInput.setTextColor(textC())
                passInput.layoutParams = LinearLayout.LayoutParams(dp(96), dp(42))
                addView(passInput)
                val saveAcct = button(L.t("save"))
                saveAcct.layoutParams = LinearLayout.LayoutParams(dp(56), dp(42))
                saveAcct.setOnClickListener {
                    val u = userInput.text.toString().trim()
                    val p = passInput.text.toString()
                    prefs.edit().putString("ftp_user", u).putString("ftp_pass", p).apply()
                    AlertDialog.Builder(this@MainActivity)
                        .setMessage(L.t("set_ftp_saved_acct") + " (${L.t("set_ftp_restart")})")
                        .setPositiveButton("OK", null).show()
                }
                addView(saveAcct)
            })
            addView(tv(L.t("set_ftp_note"), 11f, dimC()).apply { setPadding(0, dp(8), 0, 0) })
        })
        page.addView(sectionDivider())

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(8))
            addView(tv(L.t("set_auto_restart"), 11f, dimC(), true))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, 0)
                val lbl = tv(L.t("set_auto_restart_desc"), 14f, textC())
                lbl.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(lbl)
                val sw = Switch(this@MainActivity)
                sw.isChecked = prefs.getBoolean("auto_restart", false)
                sw.setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("auto_restart", on).apply() }
                addView(sw)
            })
            addView(tv(L.t("set_auto_restart_note"), 11f, dimC()).apply { setPadding(0, dp(8), 0, 0) })
        })
        page.addView(sectionDivider())

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(12))
            addView(tv(L.t("set_keeplive"), 11f, dimC(), true))
            val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
            val inWl = pm.isIgnoringBatteryOptimizations(packageName)
            addView(tv(L.t("set_keeplive_status") + " : " + (if (inWl) L.t("set_keeplive_yes") else L.t("set_keeplive_no")), 13f, if (inWl) greenC() else redC()).apply { setPadding(0, dp(6), 0, 0) })
            if (!inWl) {
                addView(button(L.t("set_keeplive_btn")) {
                    try {
                        startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            android.net.Uri.parse("package:" + packageName)))
                    } catch (e: Exception) {
                        try { AlertDialog.Builder(this@MainActivity).setMessage("" + e).setPositiveButton("OK", null).show() } catch (e2: Exception) {}
                    }
                }.apply { layoutParams = LinearLayout.LayoutParams(dp(220), dp(44)) })
            }
        })
        page.addView(sectionDivider())

        // ---------- FRP 内网穿透 ----------
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(8))
            addView(tv(L.t("frp_title"), 11f, dimC(), true))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
                val lp = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
                addView(button(L.t("frp_import")) { importFrpConfig() }.apply { layoutParams = lp })
                addView(button(L.t("frp_edit_config")) { showFrpEdit() }.apply { layoutParams = lp })
            })
            addView(tv(L.t("frp_config_hint"), 11f, dimC()).apply { setPadding(0, dp(8), 0, 0) })
        })
        page.addView(sectionDivider())

        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, dp(12), pad, dp(12))
            addView(tv("open.mp Server v2.0", 12f, dimC()))
            addView(tv(L.t("set_about"), 11f, dimC()))
        })
        return page
    }

    // ---------- 工具 ----------
    private fun setLanguage(l: String) {
        MainActivity.userLang = l
        L.lang = l
        recreate()
    }

    private fun send(action: String) {
        try {
            val i = Intent(this, OmpServerService::class.java).setAction(action)
            startService(i)
        } catch (t: Throwable) {
            try { File(filesDir, "app_log.txt").appendText("[send $action] $t\n") } catch (e: Exception) {}
        }
    }

    // ---------- FRP ----------
    private fun frpcConfigFile(): File = File(serverDir, "frpc/frpc.toml")

    // 实时进程检测（同 UID 子进程，pidof 可靠；不依赖持久化 state 文件）
    private fun isProcAlive(needle: String): Boolean = try {
        // 遍历 /proc，匹配进程 cmdline 里第一个参数（可执行名）的 basename 是否包含 needle。
        // 不依赖 pidof 精确进程名（frpc 以 libfrpc.so 启动、omp-server 以 libompserver.so / 完整路径启动，pidof 匹配不到）。
        // 匹配前把 needle 与 basename 都归一化（去掉非字母数字），使 "omp-server" 能同时匹配 "omp-server" 和 "libompserver.so"。
        val norm = needle.filter { it.isLetterOrDigit() }
        var found = false
        val proc = File("/proc")
        proc.listFiles()?.forEach { d ->
            if (found) return@forEach
            if (!d.isDirectory || !d.name.all { it.isDigit() }) return@forEach
            try {
                val cmd = File(d, "cmdline").readBytes().toString(Charsets.ISO_8859_1)
                    .substringBefore('\u0000').substringBefore(' ')
                val base = cmd.substringAfterLast('/').filter { it.isLetterOrDigit() }
                if (base.contains(norm)) found = true
            } catch (e: Exception) {}
        }
        found
    } catch (e: Exception) { false }

    private fun refreshFrpStatus() {
        try {
            // 用实时进程检测：pidof frpc > 0 才是真正运行（避免持久化标志残留导致的假状态）
            val running = isProcAlive("frpc")
            frpStatusText?.text = (if (running) "● " else "") + L.t("frp_title")
            frpStatusText?.setTextColor(if (running) greenC() else dimC())
        } catch (e: Exception) {}
    }

    private fun showFrpDialog() {
        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()
        items.add(L.t("frp_start")); actions.add { send(ACTION_FRPC_START); refreshFrpStatus() }
        items.add(L.t("frp_stop")); actions.add { send(ACTION_FRPC_STOP); refreshFrpStatus() }
        items.add(L.t("frp_edit_config")); actions.add { showFrpEdit() }
        items.add(L.t("frp_import")); actions.add { importFrpConfig() }
        items.add(L.t("frp_open_log")); actions.add { showFrpLog() }
        AlertDialog.Builder(this)
            .setTitle(L.t("frp_title"))
            .setItems(items.toTypedArray()) { _, i -> actions[i]() }
            .setNeutralButton(L.t("cancel"), null)
            .show()
    }

    private fun showFrpEdit() {
        val f = frpcConfigFile()
        if (!f.exists()) {
            AlertDialog.Builder(this).setMessage(L.t("frp_no_config")).setPositiveButton("OK", null).show()
            return
        }
        try {
            val raw = f.readBytes()
            // 优先按 UTF-8，失败按 GBK
            var text = try { String(raw, Charsets.UTF_8) } catch (e: Exception) { "" }
            if (text.isEmpty() || text.contains('\uFFFD')) {
                text = try { String(raw, Charset.forName("GBK")) } catch (e: Exception) { String(raw, Charsets.ISO_8859_1) }
            }
            val input = EditText(this).apply {
                setText(text)
                setTextColor(textC())
                setBackgroundColor(surfaceC())
                gravity = Gravity.TOP or Gravity.START
                isSingleLine = false
                minLines = 14
                setTextSize(11f)
            }
            val scroll = ScrollView(this); scroll.addView(input)
            AlertDialog.Builder(this)
                .setTitle(L.t("frp_title") + " - " + f.name)
                .setView(scroll)
                .setPositiveButton(L.t("save")) { _, _ ->
                    try { f.writeText(input.text.toString()) } catch (e: Exception) { AlertDialog.Builder(this@MainActivity).setMessage("$e").setPositiveButton("OK", null).show() }
                }
                .setNegativeButton(L.t("cancel"), null)
                .show()
        } catch (e: Exception) {
            AlertDialog.Builder(this).setMessage("$e").setPositiveButton("OK", null).show()
        }
    }

    private fun importFrpConfig() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream", "*/*"))
            }
            startActivityForResult(intent, REQ_IMPORT_FRP)
        } catch (e: Exception) {
            AlertDialog.Builder(this).setMessage("$e").setPositiveButton("OK", null).show()
        }
    }

    private fun showFrpLog() {
        try {
            val f = File(getExternalFilesDir(null), "frpc_log.txt")
            val text = if (f.exists()) f.readText().takeLast(8000) else "(no log)"
            AlertDialog.Builder(this)
                .setTitle(L.t("frp_open_log"))
                .setMessage(text)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) { }
    }

    private fun refreshState() {
        // 实时读取服务进程写入的 state.txt（跨进程真实状态）
        var running = false
        var phase = "stopped"
        var pid = 0L
        var port = 0
        var start = 0L
        var beat = 0L
        var gm = ""
        var err = ""
        try {
            val f = File(filesDir, "state.txt")
            if (f.exists()) {
                val m = HashMap<String, String>()
                f.readLines().forEach { l -> val i = l.indexOf('='); if (i > 0) m[l.substring(0, i)] = l.substring(i + 1) }
                running = m["running"] == "1"
                phase = m["phase"] ?: if (running) "running" else "stopped"
                pid = m["pid"]?.toLongOrNull() ?: 0L
                port = m["port"]?.toIntOrNull() ?: 0
                start = m["start"]?.toLongOrNull() ?: 0L
                beat = m["lastbeat"]?.toLongOrNull() ?: 0L
                gm = m["gamemode"] ?: ""
                err = m["error"] ?: ""
            }
        } catch (e: Exception) {}
        val fresh = System.currentTimeMillis() - beat < 10000
        // 最终运行判定：以实时进程检测为准（pidof omp-server），
        // 避免关闭 APP / 进程被杀后 state.txt 的持久 running 标志残留导致假"运行中"
        val actuallyRunning = running && isProcAlive("omp-server")
        val state = when {
            !actuallyRunning -> 0
            phase == "starting" -> 1
            !fresh -> 0
            else -> 2
        }
        stateText?.text = when (state) {
            0 -> L.t("state_stopped")
            1 -> L.t("state_starting")
            else -> L.t("state_running")
        }
        stateText?.setTextColor(when (state) {
            0 -> col("#C62828", "#EF9A9A")
            1 -> col("#F9A825", "#FFE082")
            else -> col("#2E7D32", "#81C784")
        })
        errText?.text = if (err.isNotEmpty()) L.t("state_error") + ": " + err else ""
        portText?.text = L.t("state_port") + " : " + (if (port > 0) port else "-")
        pidText?.text = L.t("state_pid") + " : " + (if (pid > 0) pid else "-")
        uptimeText?.text = L.t("state_uptime") + " : " + fmtUptime(if (state == 0) 0 else start)
        gmText?.text = L.t("state_gamemode") + " : " + (if (gm.isNotEmpty()) gm else "-")
        // 服务器从"运行/启动中"转为"已停止"：清空服务器日志（仅 server_log.txt，不动 app 日志）
        try {
            if (prevServerActive && state == 0) {
                try { File(getExternalFilesDir(null), "server_log.txt").delete() } catch (e: Exception) {}
                lastLog.remove(serverLogView)
            }
            prevServerActive = state != 0
        } catch (e: Exception) {}
        if (currentPage == 0) pullServerLog()
        if (currentPage == 2) pullLog()
        if (currentPage == 1) listFiles()
    }

    private fun fmtUptime(startedAt: Long): String {
        if (startedAt <= 0) return "-"
        val s = (System.currentTimeMillis() - startedAt) / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return String.format("%02d:%02d:%02d", h, m, sec)
    }

    private fun tick() {
        handler.postDelayed({
            refreshState()
            refreshFrpStatus()
            tick()
        }, 900)
    }

    private fun ftpAddr(): String {
        val port = prefs.getInt("ftp_port", 2122)
        return "ftp://" + localIp() + ":" + port
    }

    private fun localIp(): String {
        try {
            java.net.NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses?.toList()?.forEach { addr ->
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (e: Exception) {}
        return "127.0.0.1"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        try {
            if (requestCode == REQ_OPEN) {
                val uri = data.data!!
                contentResolver.openInputStream(uri)?.use { input ->
                    val target = File(pendingUploadDir, lastPath(uri) ?: "upload.bin")
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                listFiles()
            } else if (requestCode == REQ_SAVE) {
                val src = pendingDownload ?: return
                contentResolver.openOutputStream(data.data!!)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                }
            } else if (requestCode == REQ_IMPORT_FRP) {
                val uri = data.data!!
                val frpDir = File(serverDir, "frpc").apply { mkdirs() }
                val target = File(frpDir, "frpc.toml")
                contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                AlertDialog.Builder(this).setMessage(L.t("frp_imported")).setPositiveButton("OK", null).show()
            }
        } catch (t: Throwable) {
            AlertDialog.Builder(this).setMessage("transfer failed: $t").setPositiveButton("OK", null).show()
        }
    }

    private fun lastPath(uri: android.net.Uri): String? {
        val c = contentResolver.query(uri, null, null, null, null)
        c?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return it.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            setTextSize(size)
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(text: String, onClick: () -> Unit = {}): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.textSize = 13f
        b.setBackgroundColor(accentC())
        b.setTextColor(onAccent())
        b.setOnClickListener { onClick() }
        return b
    }

    private fun sectionDivider(): View = View(this).apply {
        setBackgroundColor(dividerC())
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
