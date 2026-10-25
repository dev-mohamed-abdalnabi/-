package com.next.files

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException

/** استقبال ملفات من برّه التطبيق (فتح بواسطة Next) */
object Incoming {
    class Result(val file: File, val temp: Boolean)

    private fun root(ctx: Context) = File(ctx.cacheDir, "incoming")

    fun isTemp(ctx: Context, f: File) = f.path.startsWith(root(ctx).path + "/")

    fun saveDir(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /** المجلد اللي بنفك/نحفظ فيه: جنب الملف، أو Download لو الملف نسخة مؤقتة */
    fun destParent(ctx: Context, f: File): File? =
        if (isTemp(ctx, f)) saveDir().also { it.mkdirs() } else f.parentFile

    /** مسح النسخ المؤقتة القديمة (أقدم من يوم) */
    fun clearOld(ctx: Context) {
        try {
            val limit = System.currentTimeMillis() - 24L * 3600 * 1000
            root(ctx).listFiles()?.forEach { if (it.lastModified() < limit) it.deleteRecursively() }
        } catch (e: Exception) {
        }
    }

    /** بيرجّع الملف الحقيقي لو أمكن، وإلا بينسخه في الكاش */
    fun resolve(ctx: Context, uri: Uri): Result {
        direct(ctx, uri)?.let { return Result(it, false) }
        return Result(copy(ctx, uri), true)
    }

    private fun ok(f: File?): File? = f?.takeIf { it.exists() && it.canRead() }

    private fun direct(ctx: Context, uri: Uri): File? {
        try {
            if (uri.scheme == ContentResolver.SCHEME_FILE) return ok(uri.path?.let { File(it) })
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri)
                    val parts = id.split(":", limit = 2)
                    val base = if (parts[0].equals("primary", true)) Environment.getExternalStorageDirectory()
                    else File("/storage/" + parts[0])
                    return ok(File(base, parts.getOrElse(1) { "" }))
                }
                "com.android.providers.downloads.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri)
                    if (id.startsWith("raw:")) return ok(File(id.removePrefix("raw:")))
                    return dataColumn(ctx, uri)
                }
                "com.android.providers.media.documents" -> {
                    val id = DocumentsContract.getDocumentId(uri).substringAfter(':').toLongOrNull() ?: return null
                    return dataColumn(ctx, ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id))
                }
                "media" -> return dataColumn(ctx, uri)
            }
        } catch (e: Exception) {
        }
        return null
    }

    private fun dataColumn(ctx: Context, uri: Uri): File? = try {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) ok(c.getString(0)?.let { File(it) }) else null
        }
    } catch (e: Exception) {
        null
    }

    fun copy(ctx: Context, uri: Uri): File {
        val cr = ctx.contentResolver
        var name: String? = null
        try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
        } catch (e: Exception) {
        }
        var n = (name ?: uri.lastPathSegment ?: "file").substringAfterLast('/')
            .replace(Regex("[\\\\:*?\"<>|]"), "_").trim().trimStart('.').ifEmpty { "file" }
        if (File(n).extension.isEmpty()) {
            val ext = cr.getType(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            if (ext != null) n += ".$ext"
        }
        val dir = File(root(ctx), java.lang.Long.toHexString(System.nanoTime()))
        if (!dir.mkdirs()) throw IOException("تعذر تجهيز الملف")
        val out = File(dir, n)
        try {
            val input = cr.openInputStream(uri) ?: throw IOException("تعذر قراءة الملف")
            input.use { i -> out.outputStream().use { o -> i.copyTo(o, 64 * 1024) } }
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw if (e is IOException) e else IOException("تعذر قراءة الملف")
        }
        return out
    }
}
