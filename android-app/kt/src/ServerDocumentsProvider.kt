package com.openmp.app

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.system.Os
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** MT-Manager-style data files provider (免 root 访问私有目录).
 *  Referenced from L-JINBIN/MTDataFilesProvider. Empty onCreate + attachInfo init. */
class ServerDocumentsProvider : DocumentsProvider() {
    companion object {
        const val COLUMN_MT_PATH = "mt_path"
        const val COLUMN_MT_EXTRAS = "mt_extras"
        const val METHOD_SET_LAST_MODIFIED = "mt:setLastModified"
        const val METHOD_SET_PERMISSIONS = "mt:setPermissions"
        const val METHOD_CREATE_SYMLINK = "mt:createSymlink"
    }

    private var serverDir: File? = null

    override fun attachInfo(context: Context?, info: ProviderInfo?) {
        super.attachInfo(context, info)
        if (context == null) return
        serverDir = File(context.filesDir, "server")
    }

    override fun onCreate(): Boolean = true

    private fun fileFor(docId: String, checkExists: Boolean = true): File? {
        val base = serverDir ?: return null
        val rel = docId.removePrefix("server").trimStart('/').trim()
        val f = if (rel.isEmpty()) base else File(base, rel)
        try {
            val bp = base.canonicalPath
            val fp = f.canonicalPath
            if (fp != bp && !fp.startsWith(bp + File.separator)) return null
        } catch (e: Exception) { return null }
        if (checkExists) {
            try { Os.lstat(f.path) } catch (e: Exception) { return null }
        }
        return f
    }

    private fun mime(file: File): String {
        if (file.isDirectory) return DocumentsContract.Document.MIME_TYPE_DIR
        val n = file.name
        val dot = n.lastIndexOf('.')
        if (dot >= 0) {
            val m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(n.substring(dot + 1).lowercase())
            if (m != null) return m
        }
        return "application/octet-stream"
    }

    private fun includeFile(result: MatrixCursor, docId: String, file: File?) {
        var f = file
        if (f == null) f = fileFor(docId)
        if (f == null) {
            return
        }
        var flags = 0
        if (f.isDirectory) { if (f.canWrite()) flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE }
        else if (f.canWrite()) flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        if (f.parentFile?.canWrite() == true) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_RENAME
        }
        val path = f.path
        val displayName: String
        var addExtras = false
        if (serverDir != null && path == serverDir!!.path) displayName = "server"
        else { displayName = f.name; addExtras = true }
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_SIZE, f.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mime(f))
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, f.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(COLUMN_MT_PATH, path)
        if (addExtras) {
            try {
                val st = Os.lstat(path)
                val sb = StringBuilder()
                sb.append(st.st_mode).append("|").append(st.st_uid).append("|").append(st.st_gid)
                if ((st.st_mode and 0xF000) == 0xA000) sb.append("|").append(Os.readlink(path))
                row.add(COLUMN_MT_EXTRAS, sb.toString())
            } catch (e: Exception) {}
        }
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val ai = getContext()!!.applicationInfo
        val title = ai.loadLabel(getContext()!!.packageManager).toString()
        val cols = projection ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_SUMMARY, DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_TITLE, DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_ICON)
        val c = MatrixCursor(cols)
        val row = c.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, "server")
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, "server")
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, "server directory")
        row.add(DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD)
        row.add(DocumentsContract.Root.COLUMN_TITLE, title)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
        row.add(DocumentsContract.Root.COLUMN_ICON, ai.icon)
        return c
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cols = projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE,
            COLUMN_MT_PATH, COLUMN_MT_EXTRAS)
        val c = MatrixCursor(cols)
        includeFile(c, documentId, null)
        return c
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        var pid = parentDocumentId
        if (pid.endsWith("/")) pid = pid.substring(0, pid.length - 1)
        val cols = projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS, DocumentsContract.Document.COLUMN_SIZE,
            COLUMN_MT_PATH, COLUMN_MT_EXTRAS)
        val c = MatrixCursor(cols)
        val parent = fileFor(pid) ?: serverDir
        if (parent != null && parent.isDirectory) {
            parent.listFiles()?.sortedBy { it.name }?.forEach { includeFile(c, "$pid/${it.name}", it) }
        }
        return c
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        val f = fileFor(documentId, false) ?: throw FileNotFoundException(documentId)
        UiLog.log("openDocument id=$documentId mode=$mode file=${f.path}")
        val fm = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_APPEND
            "rw" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            else -> throw IllegalArgumentException("Invalid mode: $mode")
        }
        return ParcelFileDescriptor.open(f, fm)
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileFor(parentDocumentId)
        UiLog.log("createDocument parent=$parentDocumentId mime=$mimeType name=$displayName parent=${parent?.path}")
        if (parent != null && parent.isDirectory) {
            var safeName = displayName
            if (safeName.isBlank()) safeName = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) "NewFolder" else "NewFile.txt"
            safeName = safeName.replace(Regex("[\\\\/:\\u0000]"), "_")
            var newFile = File(parent, safeName)
            var noConflict = 2
            while (newFile.exists()) {
                val dot = safeName.lastIndexOf('.')
                newFile = if (dot > 0) File(parent, safeName.substring(0, dot) + " (" + noConflict + ")" + safeName.substring(dot))
                          else File(parent, "$safeName ($noConflict)")
                noConflict++
            }
            try {
                val ok = if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) newFile.mkdir() else newFile.createNewFile()
                if (ok) {
                    val id = if (parentDocumentId.endsWith("/")) parentDocumentId + newFile.name else "$parentDocumentId/${newFile.name}"
                    UiLog.log("createDocument OK id=$id")
                    return id
                }
                UiLog.log("createDocument FAILED mkdir/create returned false")
            } catch (e: IOException) {
                UiLog.log("createDocument EX " + e)
            }
        } else {
            UiLog.log("createDocument NO PARENT (parent=${parent?.path})")
        }
        throw FileNotFoundException("Failed to create in $parentDocumentId")
    }

    override fun deleteDocument(documentId: String) {
        val f = fileFor(documentId)
        if (f == null || !deleteRec(f)) throw FileNotFoundException("Failed to delete $documentId")
    }

    private fun deleteRec(f: File): Boolean {
        if (f.isDirectory) {
            f.listFiles()?.forEach { if (!deleteRec(it)) return false }
        }
        return f.delete()
    }

    override fun removeDocument(documentId: String, parentDocumentId: String) { deleteDocument(documentId) }

    override fun renameDocument(documentId: String, displayName: String): String? {
        val f = fileFor(documentId)
        if (f != null) {
            val target = File(f.parentFile, displayName)
            if (f.renameTo(target)) {
                val i = documentId.lastIndexOf('/', documentId.length - 2)
                return documentId.substring(0, i) + "/" + displayName
            }
        }
        throw FileNotFoundException("Failed to rename $documentId")
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String? {
        val src = fileFor(sourceDocumentId)
        val tgtDir = fileFor(targetParentDocumentId)
        if (src != null && tgtDir != null) {
            val target = File(tgtDir, src.name)
            if (!target.exists() && src.renameTo(target)) {
                return if (targetParentDocumentId.endsWith("/")) targetParentDocumentId + target.name else "$targetParentDocumentId/${target.name}"
            }
        }
        throw FileNotFoundException("Failed to move $sourceDocumentId")
    }

    override fun getDocumentType(documentId: String): String? {
        val f = fileFor(documentId, false)
        if (f == null) return null
        return if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else mime(f)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean = documentId.startsWith(parentDocumentId)

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val result = super.call(method, arg, extras)
        if (result != null) return result
        if (!method.startsWith("mt:") || extras == null) return null
        val out = Bundle()
        try {
            val uri = extras.getParcelable<Uri>("uri") ?: return out
            val seg = uri.pathSegments
            val documentId = if (seg.size >= 4) seg[3] else seg[1]
            when (method) {
                METHOD_SET_LAST_MODIFIED -> {
                    val f = fileFor(documentId)
                    out.putBoolean("result", f != null && f!!.setLastModified(extras.getLong("time")))
                }
                METHOD_SET_PERMISSIONS -> {
                    val f = fileFor(documentId)
                    if (f == null) out.putBoolean("result", false)
                    else {
                        try { Os.chmod(f.path, extras.getInt("permissions")); out.putBoolean("result", true) }
                        catch (e: Exception) { out.putBoolean("result", false); out.putString("message", e.message) }
                    }
                }
                METHOD_CREATE_SYMLINK -> {
                    val f = fileFor(documentId, false)
                    if (f == null) out.putBoolean("result", false)
                    else {
                        try { Os.symlink(extras.getString("path") ?: "", f.path); out.putBoolean("result", true) }
                        catch (e: Exception) { out.putBoolean("result", false); out.putString("message", e.message) }
                    }
                }
                else -> { out.putBoolean("result", false); out.putString("message", "Unsupported: $method") }
            }
        } catch (e: Exception) {
            out.putBoolean("result", false)
            out.putString("message", e.toString())
        }
        return out
    }
}
