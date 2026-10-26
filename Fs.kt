package com.next.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.graphics.pdf.PdfRenderer
import android.os.StatFs
import android.provider.MediaStore
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FS {
    private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
    private val VID = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v", "ts")
    private val AUD = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "wma")
    private val DOCX = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "rtf", "epub")
    private val TXT = setOf(
        "txt", "md", "json", "xml", "html", "htm", "css", "js", "kt", "java", "py", "log", "csv",
        "ini", "conf", "cfg", "sh", "yml", "yaml", "properties", "gradle", "c", "cpp", "h"
    )
    private val APKX = setOf("apk", "apks", "xapk")
    private val ARC = setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")

    private const val COL_DATA = MediaStore.Files.FileColumns.DATA
    private const val COL_SIZE = MediaStore.Files.FileColumns.SIZE
    private const val COL_DATE = MediaStore.Files.FileColumns.DATE_MODIFIED
    private const val COL_FORMAT = "format"

    fun kindOf(ext: String): Kind = when {
        ext in IMG -> Kind.IMAGE
        ext in VID -> Kind.VIDEO
        ext in AUD -> Kind.AUDIO
        ext in DOCX -> Kind.DOC
        ext in TXT -> Kind.TEXT
        ext in APKX -> Kind.APK
        ext in ARC -> Kind.ARCHIVE
        else -> Kind.OTHER
    }

    fun toItem(f: File): FileItem {
        val d = f.isDirectory
        return FileItem(
            file = f,
            isDir = d,
            size = if (d) 0L else f.length(),
            modified = f.lastModified(),
            kind = if (d) Kind.FOLDER else kindOf(f.extension.lowercase()),
            count = -1
        )
    }

    fun list(dir: File, hidden: Boolean): List<FileItem> {
        if (SafFs.usesSaf(dir)) return SafFs.list(dir, hidden)
        val arr = dir.listFiles() ?: return emptyList()
        return arr.filter { hidden || !it.name.startsWith(".") }.map { toItem(it) }
    }

    fun sort(list: List<FileItem>, by: Int, asc: Boolean): List<FileItem> {
        val c = Comparator<FileItem> { a, b ->
            if (a.isDir != b.isDir) {
                if (a.isDir) -1 else 1
            } else {
                val r = when (by) {
                    1 -> a.size.compareTo(b.size)
                    2 -> a.modified.compareTo(b.modified)
                    3 -> {
                        val x = a.ext.compareTo(b.ext)
                        if (x == 0) a.name.compareTo(b.name, true) else x
                    }
                    else -> a.name.compareTo(b.name, true)
                }
                if (asc) r else -r
            }
        }
        return list.sortedWith(c)
    }

    suspend fun search(dir: File, q: String, hidden: Boolean, limit: Int = 300): List<FileItem> {
        val out = ArrayList<FileItem>()
        val queue = ArrayDeque<File>()
        queue.add(dir)
        val needle = q.lowercase()
        val safRoot = SafFs.usesSaf(dir)
        while (queue.isNotEmpty() && out.size < limit) {
            currentCoroutineContext().ensureActive()
            val d = queue.removeFirst()
            if (safRoot) {
                for (k in SafFs.list(d, hidden)) {
                    if (k.name.lowercase().contains(needle)) {
                        out.add(k)
                        if (out.size >= limit) break
                    }
                    if (k.isDir) queue.add(k.file)
                }
                continue
            }
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (!hidden && k.name.startsWith(".")) continue
                if (k.name.lowercase().contains(needle)) {
                    out.add(toItem(k))
                    if (out.size >= limit) break
                }
                if (k.isDirectory) queue.add(k)
            }
        }
        return out
    }

    fun sizeOf(f: File): Long {
        if (SafFs.usesSaf(f)) return SafFs.sizeOf(f)
        return if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    // ---------- storage volumes ----------
    fun volumes(ctx: Context): List<Vol> {
        val list = ArrayList<Vol>()
        val primary = Environment.getExternalStorageDirectory()
        list.add(makeVol("التخزين الداخلي", primary))
        try {
            for (d in ctx.getExternalFilesDirs(null)) {
                if (d == null) continue
                val p = d.absolutePath
                val i = p.indexOf("/Android/data")
                if (i > 0) {
                    val r = File(p.substring(0, i))
                    if (r.absolutePath != primary.absolutePath && r.exists() && list.none { it.dir.absolutePath == r.absolutePath }) {
                        list.add(makeVol("بطاقة الذاكرة", r))
                    }
                }
            }
        } catch (e: Exception) {
        }
        return list
    }

    private fun makeVol(name: String, f: File): Vol {
        return try {
            val s = StatFs(f.path)
            Vol(name, f, s.totalBytes, s.availableBytes)
        } catch (e: Exception) {
            Vol(name, f, 0L, 0L)
        }
    }

    fun downloads(): File = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    // ---------- file operations ----------
    fun unique(dir: File, name: String, isDir: Boolean): File {
        var t = File(dir, name)
        if (!t.exists()) return t
        val base = if (isDir) name else name.substringBeforeLast('.', name)
        val ext = if (isDir || !name.contains('.')) "" else "." + name.substringAfterLast('.')
        var i = 1
        while (t.exists()) {
            t = File(dir, "$base ($i)$ext")
            i++
        }
        return t
    }

    private fun checkNotInside(src: File, dst: File) {
        if (src.isDirectory) {
            val s = src.canonicalPath
            val d = dst.canonicalPath
            if (d == s || d.startsWith(s + File.separator)) throw IOException("مينفعش تحط المجلد جوه نفسه")
        }
    }

    fun copyTree(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyTree(it, File(dst, it.name)) }
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { i ->
                dst.outputStream().use { o ->
                    val b = ByteArray(128 * 1024)
                    while (true) {
                        val r = i.read(b)
                        if (r < 0) break
                        o.write(b, 0, r)
                        Prog.add(r.toLong())
                    }
                }
            }
            dst.setLastModified(src.lastModified())
        }
    }

    fun moveExact(src: File, target: File) {
        target.parentFile?.mkdirs()
        if (!src.renameTo(target)) {
            try {
                copyTree(src, target)
            } catch (e: Exception) {
                target.deleteRecursively()
                throw e
            }
            if (!src.deleteRecursively()) throw IOException("تعذر حذف الأصل بعد النقل")
        }
    }

    fun copy(src: File, dstDir: File): File {
        checkNotInside(src, dstDir)
        val t = unique(dstDir, src.name, src.isDirectory)
        try {
            copyTree(src, t)
        } catch (e: Exception) {
            t.deleteRecursively()
            throw e
        }
        return t
    }

    fun move(src: File, dstDir: File): File {
        if (src.parentFile?.canonicalPath == dstDir.canonicalPath) return src
        checkNotInside(src, dstDir)
        val t = unique(dstDir, src.name, src.isDirectory)
        moveExact(src, t)
        return t
    }

    fun zip(files: List<File>, out: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
            val used = HashSet<String>()
            fun add(f: File, base: String) {
                val name = if (base.isEmpty()) f.name else base + "/" + f.name
                if (f.isDirectory) {
                    val kids = f.listFiles()
                    if (kids == null || kids.isEmpty()) {
                        if (used.add("$name/")) {
                            z.putNextEntry(ZipEntry("$name/"))
                            z.closeEntry()
                        }
                    } else {
                        kids.forEach { add(it, name) }
                    }
                } else if (used.add(name)) {
                    z.putNextEntry(ZipEntry(name))
                    f.inputStream().use { i ->
                        val b = ByteArray(64 * 1024)
                        while (true) {
                            val r = i.read(b)
                            if (r < 0) break
                            z.write(b, 0, r)
                            Prog.add(r.toLong())
                        }
                    }
                    z.closeEntry()
                }
            }
            files.forEach { add(it, "") }
        }
    }

    // ---------- معاينة المجلد (بتتحمّل بس للعناصر الظاهرة وبتتخزن) ----------
    class FolderPeek(val count: Int, val names: List<String>, val kinds: List<Kind>, val files: List<File> = emptyList())

    private val peekCache = LruCache<String, FolderPeek>(800)

    fun peekCached(f: File): FolderPeek? =
        if (SafFs.usesSaf(f)) null else peekCache.get(f.path + "|" + f.lastModified())

    fun peek(dir: File, hidden: Boolean): FolderPeek {
        if (SafFs.usesSaf(dir)) return SafFs.peek(dir, hidden)
        val key = dir.path + "|" + dir.lastModified()
        peekCache.get(key)?.let { return it }
        val all = (dir.list() ?: emptyArray()).filter { hidden || !it.startsWith(".") }
        // بنفضّل نعرض الصور/الفيديو/PDF/APK (ليها معاينة حقيقية) وبعدها باقي الملفات وبعدها المجلدات
        fun rank(f: File): Int {
            if (f.isDirectory) return 2
            val k = kindOf(f.extension.lowercase())
            return if (k == Kind.IMAGE || k == Kind.VIDEO || k == Kind.APK || (k == Kind.DOC && f.extension.equals("pdf", true))) 0 else 1
        }
        val picked = all.sortedWith(String.CASE_INSENSITIVE_ORDER).take(60).map { File(dir, it) }.sortedBy { rank(it) }.take(3)
        val first = picked.map { it.name }
        val kinds = picked.map { if (it.isDirectory) Kind.FOLDER else kindOf(it.extension.lowercase()) }
        val r = FolderPeek(all.size, first, kinds, picked)
        peekCache.put(key, r)
        return r
    }

    // ---------- تصفح ملف zip من غير فك ----------
    class ZEntry(val name: String, val size: Long, val packed: Long, val time: Long, val isDir: Boolean)

    fun unzip(zip: File, dest: File) {
        dest.mkdirs()
        val root = dest.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            var e: ZipEntry? = zis.nextEntry
            while (e != null) {
                val entry: ZipEntry = e
                val out = File(dest, entry.name)
                if (!out.canonicalPath.startsWith(root)) throw IOException("الملف المضغوط فيه مسارات مش آمنة")
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
                zis.closeEntry()
                e = zis.nextEntry
            }
        }
    }

    fun scan(ctx: Context, paths: List<String>) {
        try {
            val all = ArrayList<String>()
            for (p in paths) {
                val f = File(p)
                if (f.isDirectory) {
                    f.walkTopDown().filter { it.isFile }.take(2000).forEach { all.add(it.path) }
                } else {
                    all.add(p)
                }
                if (all.size > 4000) break
            }
            if (all.isNotEmpty()) MediaScannerConnection.scanFile(ctx, all.toTypedArray(), null, null)
        } catch (e: Exception) {
        }
    }

    // ---------- open / share ----------
    fun mimeOf(f: File): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

    fun uriFor(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".provider", f)

    fun openFile(ctx: Context, f: File, chooser: Boolean = false) {
        try {
            val i = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(ctx, f), mimeOf(f))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            if (chooser) {
                ctx.startActivity(Intent.createChooser(i, "فتح باستخدام").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                ctx.startActivity(i)
            }
        } catch (e: ActivityNotFoundException) {
            Ctl.toast("مفيش تطبيق يفتح الملف ده")
        } catch (e: Exception) {
            Ctl.toast("تعذر فتح الملف")
        }
    }

    fun share(ctx: Context, files: List<File>) {
        val fs = files.filter { it.isFile }
        if (fs.isEmpty()) {
            Ctl.toast("اختار ملفات علشان تشاركها")
            return
        }
        try {
            val i = if (fs.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = mimeOf(fs[0])
                    putExtra(Intent.EXTRA_STREAM, uriFor(ctx, fs[0]))
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fs.map { uriFor(ctx, it) }))
                }
            }
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            ctx.startActivity(Intent.createChooser(i, "مشاركة").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Ctl.toast("تعذرت المشاركة")
        }
    }

    // ---------- MediaStore queries (categories) ----------
    private fun extsOf(k: CatKind): Set<String> = when (k) {
        CatKind.IMAGES -> IMG
        CatKind.VIDEOS -> VID
        CatKind.AUDIO -> AUD
        CatKind.DOCS -> DOCX + setOf("txt", "csv", "md")
        CatKind.APKS -> APKX
        CatKind.ARCHIVES -> ARC
        else -> emptySet()
    }

    private fun buildWhere(k: CatKind, hidden: Boolean, conds: MutableList<String>, args: MutableList<String>) {
        val ex = extsOf(k)
        if (ex.isNotEmpty()) {
            conds.add(ex.joinToString(" OR ") { "$COL_DATA LIKE ?" })
            ex.forEach { args.add("%.$it") }
        }
        if (k == CatKind.RECENT || k == CatKind.LARGE) {
            conds.add("$COL_FORMAT != 12289")
        }
        if (k == CatKind.LARGE) {
            conds.add("$COL_SIZE > ?")
            args.add("20971520")
        } else {
            conds.add("$COL_SIZE > 0")
        }
        conds.add("$COL_DATA NOT LIKE ?")
        args.add("%/Android/%")
        if (!hidden) {
            conds.add("$COL_DATA NOT LIKE ?")
            args.add("%/.%")
        }
    }

    fun catFiles(ctx: Context, k: CatKind, hidden: Boolean): List<File> {
        val conds = ArrayList<String>()
        val args = ArrayList<String>()
        buildWhere(k, hidden, conds, args)
        val order = if (k == CatKind.LARGE) "$COL_SIZE DESC" else "$COL_DATE DESC"
        val limit = when (k) {
            CatKind.RECENT -> 120
            CatKind.LARGE -> 150
            else -> 5000
        }
        val out = ArrayList<File>()
        try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(COL_DATA),
                conds.joinToString(" AND ") { "($it)" },
                args.toTypedArray(),
                order
            )?.use { cur ->
                val i = cur.getColumnIndexOrThrow(COL_DATA)
                while (cur.moveToNext() && out.size < limit) {
                    val p = cur.getString(i) ?: continue
                    val f = File(p)
                    if (f.isFile) out.add(f)
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun catCount(ctx: Context, k: CatKind, hidden: Boolean): Int {
        val conds = ArrayList<String>()
        val args = ArrayList<String>()
        buildWhere(k, hidden, conds, args)
        return try {
            ctx.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(COL_DATA),
                conds.joinToString(" AND ") { "($it)" },
                args.toTypedArray(),
                null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    // ---------- formatting ----------
    fun fmtSize(b: Long): String {
        if (b < 1024) return "$b B"
        val u = arrayOf("KB", "MB", "GB", "TB")
        var v = b.toDouble()
        var i = -1
        while (v >= 1024 && i < u.size - 1) {
            v /= 1024
            i++
        }
        return String.format(Locale.ENGLISH, if (v >= 100 || i == 0) "%.0f %s" else "%.1f %s", v, u[i])
    }

    fun fmtDate(ms: Long): String =
        SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.ENGLISH).format(Date(ms))
}

object TrashBin {
    val dir: File get() = File(Environment.getExternalStorageDirectory(), ".NextTrash")
    private val idx: File get() = File(dir, "index.json")

    @Synchronized
    private fun read(): JSONArray = try {
        JSONArray(idx.readText())
    } catch (e: Exception) {
        JSONArray()
    }

    @Synchronized
    private fun write(a: JSONArray) {
        dir.mkdirs()
        idx.writeText(a.toString())
    }

    @Synchronized
    fun list(): List<TrashEntry> {
        val a = read()
        val out = ArrayList<TrashEntry>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (!File(dir, id).exists()) continue
            out.add(
                TrashEntry(
                    id, o.optString("path"), o.optString("name"), o.optBoolean("dir"),
                    o.optLong("size"), o.optLong("date")
                )
            )
        }
        return out.sortedByDescending { it.date }
    }

    @Synchronized
    fun put(f: File): String {
        dir.mkdirs()
        val id = UUID.randomUUID().toString()
        val isDir = f.isDirectory
        val size = FS.sizeOf(f)
        FS.moveExact(f, File(dir, id))
        val a = read()
        a.put(
            JSONObject()
                .put("id", id).put("path", f.path).put("name", f.name)
                .put("dir", isDir).put("size", size).put("date", System.currentTimeMillis())
        )
        write(a)
        return id
    }

    private fun removeFromIndex(id: String) {
        val a = read()
        val n = JSONArray()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (o.optString("id") != id) n.put(o)
        }
        write(n)
    }

    @Synchronized
    fun restore(e: TrashEntry): File {
        val orig = File(e.path)
        val parent = orig.parentFile ?: Environment.getExternalStorageDirectory()
        parent.mkdirs()
        val target = FS.unique(parent, e.name, e.isDir)
        FS.moveExact(File(dir, e.id), target)
        removeFromIndex(e.id)
        return target
    }

    @Synchronized
    fun purge(e: TrashEntry) {
        File(dir, e.id).deleteRecursively()
        removeFromIndex(e.id)
    }

    @Synchronized
    fun purgeOld(days: Int) {
        if (days <= 0) return
        val limit = System.currentTimeMillis() - days * 86_400_000L
        list().filter { it.date < limit }.forEach { purge(it) }
    }

    @Synchronized
    fun empty() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        write(JSONArray())
    }
}

object Thumbs {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(ctx: Context, f: File, kind: Kind, px: Int): Bitmap? {
        val key = "${f.path}|${f.lastModified()}|$px"
        cache.get(key)?.let { return it }
        val bmp = try {
            when (kind) {
                Kind.IMAGE -> decode(f, px)
                Kind.VIDEO -> video(f, px)
                Kind.APK -> apk(ctx, f, px)
                Kind.AUDIO -> audio(f, px)
                Kind.DOC -> if (f.extension.equals("pdf", true)) pdf(f, px) else null
                else -> null
            }
        } catch (e: Throwable) {
            null
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun decode(f: File, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        return BitmapFactory.decodeFile(f.path, o2)
    }

    private fun scale(b: Bitmap, px: Int): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= px) return b
        val r = px.toFloat() / m
        return Bitmap.createScaledBitmap(
            b, (b.width * r).toInt().coerceAtLeast(1), (b.height * r).toInt().coerceAtLeast(1), true
        )
    }

    private fun video(f: File, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            val b = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            return scale(b, px)
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun audio(f: File, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(f.path)
            val b = r.embeddedPicture ?: return null
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(b, 0, b.size, o)
            var s = 1
            while (o.outWidth / (s * 2) >= px && o.outHeight / (s * 2) >= px) s *= 2
            return BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })
        } finally {
            try {
                r.release()
            } catch (e: Exception) {
            }
        }
    }

    private fun pdf(f: File, px: Int): Bitmap? {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val rd = PdfRenderer(pfd)
            try {
                if (rd.pageCount == 0) return null
                val pg = rd.openPage(0)
                try {
                    val w = px
                    val h = (px * pg.height.toFloat() / pg.width).toInt().coerceIn(1, px * 2)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bmp
                } finally {
                    pg.close()
                }
            } finally {
                rd.close()
            }
        } finally {
            pfd.close()
        }
    }

    private fun apk(ctx: Context, f: File, px: Int): Bitmap? {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(f.path, 0) ?: return null
        val ai = info.applicationInfo ?: return null
        ai.sourceDir = f.path
        ai.publicSourceDir = f.path
        return ai.loadIcon(pm).toBitmap(px, px)
    }
}
