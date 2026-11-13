package com.openmp.app

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/** Minimal FTP server serving the internal server dir (files/server).
 *  PASV mode only; used to edit server files remotely (LAN).
 *  Login required: username/password verified against the given credentials. */
class FtpServer(private val rootDir: File, private val port: Int,
                private val user: String = "", private val pass: String = "") {
    @Volatile var running = false
    @Volatile var logSink: (String) -> Unit = {}
    private var listener: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Socket>()

    fun start() {
        if (running) return
        try {
            listener = ServerSocket(port)
            running = true
            Thread { acceptLoop() }.start()
        } catch (e: Exception) {
            running = false
            throw e
        }
    }

    fun stop() {
        running = false
        try { listener?.close() } catch (e: Exception) {}
        clients.forEach { try { it.close() } catch (e: Exception) {} }
        clients.clear()
    }

    private fun acceptLoop() {
        while (running) {
            try {
                val sock = listener!!.accept()
                clients.add(sock)
                Thread { handleClient(sock) }.start()
            } catch (e: Exception) { if (running) break }
        }
    }

    private fun handleClient(control: Socket) {
        val log = { s: String -> try { logSink(s) } catch (e: Exception) {} }
        try {
            control.soTimeout = 30000
            control.tcpNoDelay = true
            val out = PrintWriter(control.getOutputStream(), true)
            val ins = BufferedReader(InputStreamReader(control.getInputStream()))
            // 立即发送欢迎横幅并强制 flush（MT 管理器对初始 220 响应有严格超时）
            out.write("220 open.mp Server FTP ready\r\n")
            out.flush()
            // FTP 协议要求每行以 \r\n 结尾；PrintWriter.println 只写 \n，某些客户端（MT 管理器）不识别导致握手卡死。
            fun reply(s: String) { out.write("$s\r\n"); out.flush() }
            log("C: <connect> from " + control.inetAddress?.hostAddress + ":" + control.port + " -> 220")
            var cwd = rootDir
            var pasv: ServerSocket? = null
            var dataIp: String = "127.0.0.1"
            var portHost: String? = null
            var portPort = 0
            var renameFrom: File? = null
            var loginUser = ""
            var loggedIn = false
            while (true) {
                val line = ins.readLine() ?: break
                if (line.isEmpty()) continue
                val sp = line.split(" ", limit = 2)
                val cmd = sp[0].uppercase(Locale.US)
                val arg = sp.getOrElse(1) { "" }.trim()
                log("C: $cmd ${if (arg.length > 24) arg.take(24) + "…" else arg}")
                try {
                    when (cmd) {
                        "USER" -> {
                            loginUser = arg
                            // 配置了账号密码 → 需要密码（331）；未配置 → 匿名直接登录（230）
                            if (user.isEmpty()) loggedIn = true
                            reply(if (user.isEmpty()) "230 Anonymous login ok" else "331 Password required")
                        }
                        "PASS" -> {
                            if (user.isEmpty()) { loggedIn = true; reply("230 Anonymous login ok") }
                            else if (loginUser == user && arg == pass) { loggedIn = true; reply("230 Login successful") }
                            else { loggedIn = false; reply("530 Login incorrect") }
                        }
                        "ACCT" -> reply(if (loggedIn) "230 Account ok" else "530 Not logged in")
                        "SYST" -> reply("215 UNIX Type: L8")
                        "TYPE" -> reply(if (arg == "I") "200 Type set to I" else "200 Type set to A")
                        "NOOP" -> reply("200 OK")
                        "OPTS" -> reply("200 OPTS OK")
                        "CLNT" -> reply("200 CLNT OK")
                        "PWD", "XPWD" -> reply(if (loggedIn) "257 \"" + rel(cwd) + "\"" else "530 Not logged in")
                        "CWD", "XCWD" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val f = resolve(cwd, arg)
                            if (f.isDirectory) { cwd = f; reply("250 OK") } else reply("550 no such dir")
                        }
                        "CDUP" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; cwd = cwd.parentFile ?: rootDir; reply("250 OK") }
                        "PASV" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            try { pasv?.close() } catch (e: Exception) {}
                            pasv = ServerSocket(0)
                            pasv!!.soTimeout = 15000
                            dataIp = localIp()
                            val p = pasv!!.localPort
                            val nums = dataIp.split(".").map { it.toInt() }.toMutableList()
                            if (nums.size != 4) { reply("425 can't open passive connection"); continue }
                            reply("227 Entering Passive Mode (${nums[0]},${nums[1]},${nums[2]},${nums[3]},${p / 256},${p % 256})")
                        }
                        "EPSV" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            try { pasv?.close() } catch (e: Exception) {}
                            pasv = ServerSocket(0)
                            pasv!!.soTimeout = 15000
                            reply("229 Entering Extended Passive Mode (|||${pasv!!.localPort}|)")
                        }
                        "CPSV" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            try { pasv?.close() } catch (e: Exception) {}
                            pasv = ServerSocket(0)
                            pasv!!.soTimeout = 15000
                            reply("227 Entering Passive Mode (|||${pasv!!.localPort}|)")
                        }
                        "PORT" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val parts = arg.split(",")
                            if (parts.size == 6) {
                                portHost = parts.take(4).joinToString(".")
                                portPort = try { parts[4].trim().toInt() * 256 + parts[5].trim().toInt() } catch (e: Exception) { -1 }
                                if (portPort in 1..65535) reply("200 PORT ok") else { portHost = null; reply("501 bad PORT arg") }
                            } else reply("501 bad PORT arg")
                        }
                        "LIST", "NLST", "MLSD" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val target = if (arg.isNotEmpty()) resolve(cwd, arg) else cwd
                            val data = openData(pasv, portHost, portPort) ?: { reply("425 no passive conn"); null }()
                            if (data == null) continue
                            reply("150 opening data connection")
                            val w = PrintWriter(data.getOutputStream(), true)
                            val files = if (target.isFile) listOf(target) else target.listFiles()?.toList() ?: emptyList()
                            for (f in files.sortedBy { it.name }) {
                                val type = if (f.isDirectory) "d" else "-"
                                val size = if (f.isDirectory) 4096 else f.length()
                                val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(f.lastModified()))
                                when (cmd) {
                                    "NLST" -> w.println(f.name)
                                    "MLSD" -> w.println("Type=${if (f.isDirectory) "dir" else "file"};Size=$size;Modify=${SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(f.lastModified()))}; ${f.name}")
                                    else -> w.println("$type--------- 1 owner group $size $date ${f.name}")
                                }
                            }
                            w.flush(); data.close()
                            reply("226 transfer complete")
                        }
                        "MLST" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val f = resolve(cwd, arg)
                            if (!f.exists()) { reply("550 no such file"); continue }
                            val size = if (f.isDirectory) 4096 else f.length()
                            reply("250-Listing ${rel(f)}")
                            reply(" Type=${if (f.isDirectory) "dir" else "file"};Size=$size;Modify=${SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(f.lastModified()))}; ${f.name}")
                            reply("250 End")
                        }
                        "RETR" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val f = resolve(cwd, arg)
                            if (!f.isFile) { reply("550 no such file"); continue }
                            val data = openData(pasv, portHost, portPort) ?: { reply("425 no passive conn"); null }()
                            if (data == null) continue
                            reply("150 opening data connection")
                            data.getOutputStream().use { os ->
                                f.inputStream().use { input ->
                                    val buf = ByteArray(65536)
                                    while (true) { val n = input.read(buf); if (n <= 0) break; os.write(buf, 0, n) }
                                }
                            }
                            data.close()
                            reply("226 transfer complete")
                        }
                        "STOR" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val f = resolve(cwd, arg)
                            val data = openData(pasv, portHost, portPort) ?: { reply("425 no passive conn"); null }()
                            if (data == null) continue
                            reply("150 opening data connection")
                            data.getInputStream().use { input ->
                                f.outputStream().use { os ->
                                    val buf = ByteArray(65536)
                                    while (true) { val n = input.read(buf); if (n <= 0) break; os.write(buf, 0, n) }
                                }
                            }
                            data.close()
                            reply("226 transfer complete")
                        }
                        "SIZE" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; val f = resolve(cwd, arg); reply(if (f.isFile) "213 ${f.length()}" else "550 no such file") }
                        "MDTM" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; val f = resolve(cwd, arg); reply(if (f.isFile) "213 " + SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(f.lastModified())) else "550 no such file") }
                        "DELE" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; val f = resolve(cwd, arg); reply(if (f.delete()) "250 deleted" else "550 delete failed") }
                        "MKD" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; val f = resolve(cwd, arg); reply(if (f.mkdir()) "257 created" else "550 mkdir failed") }
                        "RMD" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; val f = resolve(cwd, arg); reply(if (f.isDirectory && f.delete()) "250 removed" else "550 rmd failed") }
                        "RNFR" -> { if (!loggedIn) { reply("530 Not logged in"); continue }; val f = resolve(cwd, arg); renameFrom = if (f.exists()) f else null; reply(if (renameFrom != null) "350 ok" else "550 no such file") }
                        "RNTO" -> {
                            if (!loggedIn) { reply("530 Not logged in"); continue }
                            val from = renameFrom
                            if (from == null) { reply("503 RNFR first"); continue }
                            val to = resolve(cwd, arg)
                            reply(if (from.renameTo(to)) "250 renamed" else "550 rename failed")
                            renameFrom = null
                        }
                        "FEAT" -> reply("211-Features:\r\n SIZE\r\n MDTM\r\n MLST type*;size*;modify*\r\n MLSD\r\n EPSV\r\n UTF8\r\n211 End")
                        "QUIT" -> { reply("221 bye"); break }
                        else -> reply("502 not implemented")
                    }
                } catch (e: SocketTimeoutException) { reply("425 data timeout"); log("E: SocketTimeoutException") }
                catch (e: Exception) { log("E: ${e.javaClass.simpleName}: ${e.message}"); reply("550 error: ${e.message}") }
            }
        } catch (e: Exception) {
            log("FATAL: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            log("C: <closed>")
            try { control.close() } catch (e: Exception) {}
            clients.remove(control)
        }
    }

    private fun openData(pasv: ServerSocket?, portHost: String?, portPort: Int): Socket? = try {
        if (portHost != null) Socket(portHost, portPort) else pasv?.accept()
    } catch (e: Exception) { null }

    private fun resolve(base: File, path: String): File {
        val f = if (path.startsWith("/")) File(rootDir, path.trimStart('/')) else File(base, path)
        val rp = try { rootDir.canonicalPath } catch (e: Exception) { rootDir.absolutePath }
        val fp = try { f.canonicalPath } catch (e: Exception) { f.absolutePath }
        if (fp != rp && !fp.startsWith(rp + File.separator)) return File(rootDir, "..").let { File(rootDir, "__denied__") }
        return f
    }

    private fun rel(f: File): String {
        val rp = try { rootDir.canonicalPath } catch (e: Exception) { rootDir.absolutePath }
        val fp = try { f.canonicalPath } catch (e: Exception) { f.absolutePath }
        return if (fp == rp) "/" else "/" + f.relativeTo(rootDir).path.replace(File.separator, "/")
    }

    private fun localIp(): String {
        try {
            NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
                if (!ni.isUp || ni.isLoopback) return@forEach
                ni.inetAddresses?.toList()?.forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress
                }
            }
        } catch (e: Exception) {}
        return "127.0.0.1"
    }
}
