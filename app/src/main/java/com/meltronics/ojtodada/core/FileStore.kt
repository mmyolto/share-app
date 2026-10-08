package com.meltronics.ojtodada.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Where received files go: Downloads/OJ-to-Dada */
class Sink(
    val uri: Uri,
    val out: OutputStream,
    private val onCommit: () -> Unit,
    private val onAbort: () -> Unit,
) {
    fun commit() = onCommit()
    fun abort() = runCatching { onAbort() }
}

object FileStore {
    const val FOLDER = "OJ-to-Dada"
    const val APK_MIME = "application/vnd.android.package-archive"

    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext == "apk") return APK_MIME
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    /** Keeps names safe for every file system (Windows included). */
    fun cleanName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = base.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim().trimStart('.')
        return cleaned.ifBlank { "file_${System.currentTimeMillis()}" }.take(200)
    }

    fun create(ctx: Context, rawName: String): Sink {
        val name = cleanName(rawName)
        val mime = mimeFor(name)
        return if (Build.VERSION.SDK_INT >= 29) {
            val cr = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw java.io.IOException("Cannot create $name")
            val out = cr.openOutputStream(uri) ?: throw java.io.IOException("Cannot write $name")
            Sink(
                uri, out,
                onCommit = {
                    out.close()
                    cr.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                },
                onAbort = {
                    runCatching { out.close() }
                    cr.delete(uri, null, null)
                },
            )
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
            dir.mkdirs()
            var file = File(dir, name)
            var i = 1
            val stem = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
            while (file.exists()) file = File(dir, "$stem (${i++})$ext")
            val out = FileOutputStream(file)
            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
            Sink(uri, out, onCommit = { out.close() }, onAbort = { runCatching { out.close() }; file.delete() })
        }
    }

    /** Reads name + size of anything the user picked. Copies to cache only when the size is unknown. */
    fun describe(ctx: Context, uri: Uri): ShareItem? = runCatching {
        var name: String? = null
        var size = -1L
        if (uri.scheme == "file") {
            val f = File(uri.path!!)
            name = f.name; size = f.length()
        } else {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        val finalName = cleanName(name ?: uri.lastPathSegment ?: "file")
        if (size < 0) {
            // Unknown size: measure by copying to cache (rare; some cloud providers)
            val tmp = File(ctx.cacheDir, "out_${System.nanoTime()}_$finalName")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { o -> i.copyTo(o, Wire.BUF) } }
            return@runCatching ShareItem(Uri.fromFile(tmp), finalName, tmp.length(), mimeFor(finalName))
        }
        ShareItem(uri, finalName, size, ctx.contentResolver.getType(uri) ?: mimeFor(finalName))
    }.getOrNull()

    /** Open a received file; APKs go to the installer. */
    fun open(ctx: Context, f: ReceivedFile) {
        val uri = Uri.parse(f.uri)
        if (f.mime == APK_MIME && Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, f.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(Intent.createChooser(intent, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
