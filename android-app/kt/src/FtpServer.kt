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

/** Minimal anonymous FTP server serving the internal server dir (files/server).
 *  PASV mode only; used to edit server files remotely (LAN). */
class FtpServer(private val rootDir: File, private val port: Int) {
    @Volatile var running = false
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
        try {
            control.soTimeout = 30000
            val out = PrintWriter(control.getOutputStream(), true)
            val ins = BufferedReader(InputStreamReader(control.getInputStream()))
            out.println("220 open.mp Server FTP ready")
            var cwd = rootDir
            var pasv: ServerSocket? = null
            var dataIp: String = "127.0.0.1"
            var portHost: String? = null
            var portPort = 0
            var renameFrom: File? = null
            while (true) {
                val line = ins.readLine() ?: break
                if (line.isEmpty()) continue
                val sp = line.split(" ", limit = 2)
                val cmd = sp[0].uppercase(Locale.US)
                val arg = sp.getOrElse(1) { "" }.trim()
                try {
                    when (cmd) {
                        "USER", "PASS" -> out.println("230 Anonymous login ok")
                        "SYST" -> out.println("215 UNIX Type: L8")
                        "TYPE" -> out.println(if (arg == "I") "200 Type set to I" else "200 Type set to A")
                        "NOOP" -> out.println("200 OK")
                        "PWD", "XPWD" -> out.println("257 \"" + rel(cwd) + "\"")
                        "CWD", "XCWD" -> {
                            val f = resolve(cwd, arg)
                            if (f.isDirectory) { cwd = f; out.println("250 OK") } else out.println("550 no such dir")
                        }
                        "CDUP" -> { cwd = cwd.parentFile ?: rootDir; out.println("250 OK") }
                        "PASV" -> {
                            try { pasv?.close() } catch (e: Exception) {}
                            pasv = ServerSocket(0)
                            pasv!!.soTimeout = 15000
                            dataIp = localIp()
                            val p = pasv!!.localPort
                            val nums = dataIp.split(".").map { it.toInt() }.toMutableList()
                            if (nums.size != 4) { out.println("425 can't open passive connection"); continue }
                            out.println("227 Entering Passive Mode (${nums[0]},${nums[1]},${nums[2]},${nums[3]},${p / 256},${p % 256})")
                        }
                        "EPSV" -> {
                            try { pasv?.close() } catch (e: Exception) {}
                            pasv = ServerSocket(0)
                            pasv!!.soTimeout = 15000
                            out.println("229 Entering Extended Passive Mode (|||${pasv!!.localPort}|)")
                        }
                        "PORT" -> {
                            val parts = arg.split(",")
                            if (parts.size == 6) {
                                portHost = parts.take(4).joinToString(".")
                                portPort = try { parts[4].trim().toInt() * 256 + parts[5].trim().toInt() } catch (e: Exception) { -1 }
                                if (portPort in 1..65535) out.println("200 PORT ok") else { portHost = null; out.println("501 bad PORT arg") }
                            } else out.println("501 bad PORT arg")
                        }
                        "LIST", "NLST" -> {
                            val target = if (arg.isNotEmpty()) resolve(cwd, arg) else cwd
                            val data = openData(pasv, portHost, portPort) ?: { out.println("425 no passive conn"); null }()
                            if (data == null) continue
                            out.println("150 opening data connection")
                            val w = PrintWriter(data.getOutputStream(), true)
                            val files = if (target.isFile) listOf(target) else target.listFiles()?.toList() ?: emptyList()
                            for (f in files.sortedBy { it.name }) {
                                if (cmd == "NLST") { w.println(f.name); continue }
                                val type = if (f.isDirectory) "d" else "-"
                                val size = if (f.isDirectory) 4096 else f.length()
                                val date = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(f.lastModified()))
                                w.println("$type--------- 1 owner group $size $date ${f.name}")
                            }
                            w.flush(); data.close()
                            out.println("226 transfer complete")
                        }
                        "RETR" -> {
                            val f = resolve(cwd, arg)
                            if (!f.isFile) { out.println("550 no such file"); continue }
                            val data = openData(pasv, portHost, portPort) ?: { out.println("425 no passive conn"); null }()
                            if (data == null) continue
                            out.println("150 opening data connection")
                            data.getOutputStream().use { os ->
                                f.inputStream().use { input ->
                                    val buf = ByteArray(65536)
                                    while (true) { val n = input.read(buf); if (n <= 0) break; os.write(buf, 0, n) }
                                }
                            }
                            data.close()
                            out.println("226 transfer complete")
                        }
                        "STOR" -> {
                            val f = resolve(cwd, arg)
                            val data = openData(pasv, portHost, portPort) ?: { out.println("425 no passive conn"); null }()
                            if (data == null) continue
                            out.println("150 opening data connection")
                            data.getInputStream().use { input ->
                                f.outputStream().use { os ->
                                    val buf = ByteArray(65536)
                                    while (true) { val n = input.read(buf); if (n <= 0) break; os.write(buf, 0, n) }
                                }
                            }
                            data.close()
                            out.println("226 transfer complete")
                        }
                        "SIZE" -> { val f = resolve(cwd, arg); out.println(if (f.isFile) "213 ${f.length()}" else "550 no such file") }
                        "MDTM" -> { val f = resolve(cwd, arg); out.println(if (f.isFile) "213 " + SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(Date(f.lastModified())) else "550 no such file") }
                        "DELE" -> { val f = resolve(cwd, arg); out.println(if (f.delete()) "250 deleted" else "550 delete failed") }
                        "MKD" -> { val f = resolve(cwd, arg); out.println(if (f.mkdir()) "257 created" else "550 mkdir failed") }
                        "RMD" -> { val f = resolve(cwd, arg); out.println(if (f.isDirectory && f.delete()) "250 removed" else "550 rmd failed") }
                        "RNFR" -> { val f = resolve(cwd, arg); renameFrom = if (f.exists()) f else null; out.println(if (renameFrom != null) "350 ok" else "550 no such file") }
                        "RNTO" -> {
                            val from = renameFrom
                            if (from == null) { out.println("503 RNFR first"); continue }
                            val to = resolve(cwd, arg)
                            out.println(if (from.renameTo(to)) "250 renamed" else "550 rename failed")
                            renameFrom = null
                        }
                        "FEAT" -> out.println("211-Extensions\r\n SIZE\r\n MDTM\r\n211 END")
                        "QUIT" -> { out.println("221 bye"); break }
                        else -> out.println("502 not implemented")
                    }
                } catch (e: SocketTimeoutException) { out.println("425 data timeout") }
                catch (e: Exception) { out.println("550 error: ${e.message}") }
            }
        } catch (e: Exception) {
        } finally {
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
