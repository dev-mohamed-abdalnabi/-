package com.next.files

import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * طبقة بتخلي متصفح الملفات العادي يشتغل جوه Android/data و Android/obb على أندرويد 11+ عن طريق SAF،
 * من غير ما باقي التطبيق يتغير: المسارات بتفضل File عادي ("افتراضية") والعمليات بتتحول هنا لـ SAF.
 * بتشتغل بس لو المستخدم منح صلاحية المجلد (Saf.treeOf).
 */
object SafFs {
    private val primaryPath: String get() = Environment.getExternalStorageDirectory().path

    /** "data" أو "obb" لو المسار جوه Android/data أو Android/obb على التخزين الداخلي */
    fun subOf(f: File): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val root = primaryPath
        val p = f.path
        if (!p.startsWith("$root/")) return null
        val rel = p.substring(root.length)
        for (s in arrayOf("data", "obb")) {
            val b = "/Android/$s"
            if (rel == b || rel.startsWith("$b/")) return s
        }
        return null
    }

    fun tree(f: File): Uri? = subOf(f)?.let { Saf.treeOf(Ctl.app, it) }

    /** true لو المسار ده لازم يتعامل معاه عن طريق SAF (الصلاحية ممنوحة) */
    fun usesSaf(f: File): Boolean = tree(f) != null

    private fun id(f: File): String = "primary:" + f.path.substring(primaryPath.length).trimStart('/')

    private fun itemOf(dir: File, d: Saf.Doc): FileItem = FileItem(
        file = File(dir, d.name),
        isDir = d.isDir,
        size = if (d.isDir) 0L else d.size,
        modified = d.time,
        kind = if (d.isDir) Kind.FOLDER else FS.kindOf(d.name.substringAfterLast('.', "").lowercase()),
        count = -1
    )

    fun list(dir: File, hidden: Boolean): List<FileItem> {
        val t = tree(dir) ?: return emptyList()
        val docs = try { Saf.list(Ctl.app, t, id(dir)) } catch (e: Exception) { emptyList() }
        return docs.filter { hidden || !it.name.startsWith(".") }.map { itemOf(dir, it) }
    }

    fun peek(dir: File, hidden: Boolean): FS.FolderPeek {
        val items = list(dir, hidden)
        val first = items.map { it.file.name }.sortedWith(String.CASE_INSENSITIVE_ORDER).take(3)
        val kinds = first.map { n -> items.first { it.file.name == n }.kind }
        return FS.FolderPeek(items.size, first, kinds)
    }

    fun stat(f: File): Saf.Doc? {
        val t = tree(f) ?: return null
        return Saf.stat(Ctl.app, t, id(f))
    }

    fun exists(f: File): Boolean = stat(f) != null
    fun isDir(f: File): Boolean = stat(f)?.isDir == true

    fun sizeOf(f: File): Long {
        val t = tree(f) ?: return 0L
        val root = Saf.stat(Ctl.app, t, id(f)) ?: return 0L
        if (!root.isDir) return root.size
        var total = 0L
        val q = ArrayDeque<String>()
        q.add(root.id)
        while (q.isNotEmpty()) {
            for (k in try { Saf.list(Ctl.app, t, q.removeFirst()) } catch (e: Exception) { emptyList() }) {
                if (k.isDir) q.add(k.id) else total += k.size
            }
        }
        return total
    }

    /** نسخ من Android/data لمجلد عادي */
    fun copyOut(src: File, dstDir: File): File {
        val t = tree(src) ?: throw IOException("مفيش صلاحية للمجلد ده")
        val d = Saf.stat(Ctl.app, t, id(src)) ?: throw IOException("${src.name} مش موجود")
        return Saf.copyOut(Ctl.app, t, d, dstDir)
    }

    /** نسخ من مجلد عادي جوه Android/data */
    fun copyIn(src: File, destDir: File): File {
        val t = tree(destDir) ?: throw IOException("مفيش صلاحية للمجلد ده")
        Saf.copyIn(Ctl.app, t, id(destDir), src)
        return File(destDir, src.name)
    }

    /** نسخة مؤقتة في الكاش علشان الفتح/المشاركة */
    fun toCache(f: File): File {
        val base = File(Ctl.app.cacheDir, "safopen")
        val now = System.currentTimeMillis()
        base.listFiles()?.filter { now - it.lastModified() > 3_600_000L }?.forEach { it.deleteRecursively() }
        val dir = File(base, System.nanoTime().toString()).also { it.mkdirs() }
        return copyOut(f, dir)
    }

    fun delete(f: File): Boolean {
        val t = tree(f) ?: return false
        return try { Saf.delete(Ctl.app, t, id(f)) } catch (e: Exception) { false }
    }

    fun rename(f: File, name: String): Boolean {
        val t = tree(f) ?: return false
        return try { Saf.rename(Ctl.app, t, id(f), name) } catch (e: Exception) { false }
    }

    private fun create(dir: File, mime: String, name: String): Boolean {
        val t = tree(dir) ?: return false
        return try {
            DocumentsContract.createDocument(
                Ctl.app.contentResolver, DocumentsContract.buildDocumentUriUsingTree(t, id(dir)), mime, name
            ) != null
        } catch (e: Exception) { false }
    }

    fun mkdir(dir: File, name: String): Boolean = create(dir, DocumentsContract.Document.MIME_TYPE_DIR, name)

    fun newFile(dir: File, name: String): Boolean =
        create(dir, FS.mimeOf(File(name)).let { if (it == "*/*") "application/octet-stream" else it }, name)
}
