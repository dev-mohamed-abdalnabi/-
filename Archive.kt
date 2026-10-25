package com.next.files

import android.content.Context
import com.github.junrar.Archive
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import java.io.BufferedOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import net.lingala.zip4j.ZipFile as Z4

/** محرك الأرشيفات: zip (مع باسورد AES) · rar (RAR4) · 7z · tar · tgz · tbz · txz · gz · bz2 · xz */
object Arc {
    class NeedPassword(val wrong: Boolean) :
        IOException(if (wrong) "الباسورد غلط أو الملف تالف" else "الأرشيف محمي بباسورد")

    class Listing(val entries: List<FS.ZEntry>, val encrypted: Boolean)

    fun fmt(f: File): String? {
        val n = f.name.lowercase()
        return when {
            n.endsWith(".zip") -> "zip"
            n.endsWith(".rar") -> "rar"
            n.endsWith(".7z") -> "7z"
            n.endsWith(".tar") -> "tar"
            n.endsWith(".tar.gz") || n.endsWith(".tgz") -> "tgz"
            n.endsWith(".tar.bz2") || n.endsWith(".tbz2") || n.endsWith(".tbz") -> "tbz"
            n.endsWith(".tar.xz") || n.endsWith(".txz") -> "txz"
            n.endsWith(".gz") -> "gz"
            n.endsWith(".bz2") -> "bz2"
            n.endsWith(".xz") -> "xz"
            else -> null
        }
    }

    fun supported(f: File) = fmt(f) != null

    private fun norm(n: String) = n.replace('\\', '/').trimStart('/')
    private fun dirName(n: String, isDir: Boolean): String {
        val x = norm(n)
        return if (isDir && !x.endsWith("/")) "$x/" else x
    }

    private fun sevenOpen(f: File, pwd: String?): SevenZFile =
        if (pwd.isNullOrEmpty()) SevenZFile(f) else SevenZFile(f, pwd.toCharArray())

    private fun rarOpen(f: File, pwd: String?): Archive =
        try {
            if (pwd.isNullOrEmpty()) Archive(f) else Archive(f, pwd)
        } catch (e: Exception) {
            if (e.javaClass.simpleName.contains("V5")) throw IOException("RAR5 لسه مش مدعوم - المدعوم RAR4 و7z و zip")
            throw IOException("تعذر قراءة ملف rar")
        }

    private fun tarOpen(f: File): TarArchiveInputStream {
        val raw = BufferedInputStream(FileInputStream(f))
        val s: InputStream = when (fmt(f)) {
            "tgz" -> GzipCompressorInputStream(raw)
            "tbz" -> BZip2CompressorInputStream(raw)
            "txz" -> XZCompressorInputStream(raw)
            else -> raw
        }
        return TarArchiveInputStream(s)
    }

    fun list(f: File, pwd: String?): Listing {
        try {
            return when (fmt(f)) {
                "zip" -> {
                    val z = Z4(f)
                    val hs = z.fileHeaders
                    Listing(
                        hs.map { FS.ZEntry(norm(it.fileName), it.uncompressedSize, it.compressedSize, it.lastModifiedTimeEpoch, it.isDirectory) },
                        z.isEncrypted
                    )
                }
                "7z" -> sevenOpen(f, pwd).use { sz ->
                    val l = ArrayList<FS.ZEntry>()
                    for (e in sz.entries) {
                        val t = if (e.hasLastModifiedDate()) e.lastModifiedDate.time else 0L
                        l.add(FS.ZEntry(dirName(e.name, e.isDirectory), e.size, 0, t, e.isDirectory))
                    }
                    Listing(l, false)
                }
                "rar" -> rarOpen(f, pwd).use { a ->
                    val hs = a.fileHeaders
                    Listing(
                        hs.map { FS.ZEntry(dirName(it.fileName, it.isDirectory), it.fullUnpackSize, 0, it.mTime?.time ?: 0L, it.isDirectory) },
                        hs.any { it.isEncrypted }
                    )
                }
                "tar", "tgz", "tbz", "txz" -> tarOpen(f).use { t ->
                    val l = ArrayList<FS.ZEntry>()
                    var e = t.nextTarEntry
                    while (e != null) {
                        l.add(FS.ZEntry(dirName(e.name, e.isDirectory), e.size, 0, e.modTime?.time ?: 0L, e.isDirectory))
                        e = t.nextTarEntry
                    }
                    Listing(l, false)
                }
                "gz", "bz2", "xz" -> Listing(listOf(FS.ZEntry(f.nameWithoutExtension, -1, f.length(), f.lastModified(), false)), false)
                else -> throw IOException("نوع الأرشيف مش مدعوم")
            }
        } catch (e: org.apache.commons.compress.PasswordRequiredException) {
            throw NeedPassword(false)
        } catch (e: NeedPassword) {
            throw e
        } catch (e: IOException) {
            if (fmt(f) == "7z" && !pwd.isNullOrEmpty()) throw NeedPassword(true)
            throw e
        }
    }

    private fun pump(i: InputStream, o: OutputStream) {
        val b = ByteArray(128 * 1024)
        while (true) {
            val r = i.read(b)
            if (r < 0) break
            o.write(b, 0, r)
            Prog.add(r.toLong())
        }
    }

    /** names = null يعني الكل. اسم ينتهي بـ / يعني مجلد كامل. بيرجع عدد الملفات اللي اتفكت */
    fun extract(f: File, names: Collection<String>?, dest: File, pwd: String?): Int {
        dest.mkdirs()
        val root = dest.canonicalPath + File.separator
        fun want(n: String) = names == null || names.any { n == it || (it.endsWith("/") && n.startsWith(it)) }
        fun outFor(n: String): File {
            val o = File(dest, n)
            if (!o.canonicalPath.startsWith(root)) throw IOException("الأرشيف فيه مسارات مش آمنة")
            return o
        }
        fun write(o: File, ins: InputStream) {
            o.parentFile?.mkdirs()
            FileOutputStream(o).use { out -> pump(ins, out) }
        }
        var count = 0
        try {
            when (fmt(f)) {
                "zip" -> {
                    val z = Z4(f)
                    if (z.isEncrypted) {
                        if (pwd.isNullOrEmpty()) throw NeedPassword(false)
                        z.setPassword(pwd.toCharArray())
                    }
                    val hs = z.fileHeaders.filter { want(norm(it.fileName)) }
                    Prog.begin(hs.sumOf { maxOf(it.uncompressedSize, 0L) })
                    for (h in hs) {
                        try {
                            z.extractFile(h, dest.path)
                        } catch (e: net.lingala.zip4j.exception.ZipException) {
                            if (e.type == net.lingala.zip4j.exception.ZipException.Type.WRONG_PASSWORD) throw NeedPassword(true)
                            throw e
                        }
                        if (!h.isDirectory) count++
                        Prog.add(maxOf(h.uncompressedSize, 0L))
                    }
                }
                "7z" -> {
                    val total = list(f, pwd).entries.filter { want(it.name) }.sumOf { maxOf(it.size, 0L) }
                    Prog.begin(total)
                    sevenOpen(f, pwd).use { sz ->
                        val buf = ByteArray(64 * 1024)
                        var e = sz.nextEntry
                        while (e != null) {
                            val n = dirName(e.name, e.isDirectory)
                            if (want(n)) {
                                val o = outFor(norm(e.name))
                                if (e.isDirectory) o.mkdirs() else {
                                    o.parentFile?.mkdirs()
                                    FileOutputStream(o).use { out ->
                                        while (true) {
                                            val r = sz.read(buf)
                                            if (r < 0) break
                                            out.write(buf, 0, r)
                                            Prog.add(r.toLong())
                                        }
                                    }
                                    count++
                                }
                            }
                            e = sz.nextEntry
                        }
                    }
                }
                "rar" -> rarOpen(f, pwd).use { a ->
                    val hs = a.fileHeaders.filter { want(dirName(it.fileName, it.isDirectory)) }
                    Prog.begin(hs.sumOf { maxOf(it.fullUnpackSize, 0L) })
                    for (h in hs) {
                        if (h.isEncrypted && pwd.isNullOrEmpty()) throw NeedPassword(false)
                        val o = outFor(norm(h.fileName))
                        if (h.isDirectory) o.mkdirs() else {
                            o.parentFile?.mkdirs()
                            FileOutputStream(o).use { out ->
                                a.extractFile(h, object : OutputStream() {
                                    override fun write(b: Int) { out.write(b) }
                                    override fun write(b: ByteArray, off: Int, len: Int) {
                                        out.write(b, off, len)
                                        Prog.add(len.toLong())
                                    }
                                })
                            }
                            count++
                        }
                    }
                }
                "tar", "tgz", "tbz", "txz" -> {
                    Prog.begin(0)
                    tarOpen(f).use { t ->
                        var e = t.nextTarEntry
                        while (e != null) {
                            val n = dirName(e.name, e.isDirectory)
                            if (want(n)) {
                                val o = outFor(norm(e.name))
                                if (e.isDirectory) o.mkdirs() else { write(o, t); count++ }
                            }
                            e = t.nextTarEntry
                        }
                    }
                }
                "gz", "bz2", "xz" -> {
                    Prog.begin(0)
                    val raw = BufferedInputStream(FileInputStream(f))
                    val s: InputStream = when (fmt(f)) {
                        "gz" -> GzipCompressorInputStream(raw)
                        "bz2" -> BZip2CompressorInputStream(raw)
                        else -> XZCompressorInputStream(raw)
                    }
                    s.use { write(outFor(f.nameWithoutExtension), it) }
                    count = 1
                }
                else -> throw IOException("نوع الأرشيف مش مدعوم")
            }
        } catch (e: org.apache.commons.compress.PasswordRequiredException) {
            throw NeedPassword(false)
        }
        return count
    }

    fun extractToCache(ctx: Context, f: File, entry: String, pwd: String?): File {
        val dir = File(ctx.cacheDir, "arcview/" + Integer.toHexString(f.path.hashCode()))
        extract(f, listOf(entry), dir, pwd)
        return File(dir, norm(entry))
    }

    fun clearCache(ctx: Context) {
        try { File(ctx.cacheDir, "arcview").deleteRecursively() } catch (e: Exception) { }
    }

    /** ضغط zip بباسورد AES-256 */
    fun createZip(files: List<File>, out: File, pwd: String) {
        val z = Z4(out, pwd.toCharArray())
        val p = ZipParameters()
        p.isEncryptFiles = true
        p.encryptionMethod = EncryptionMethod.AES
        p.aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        for (f in files) {
            if (f.isDirectory) z.addFolder(f, p) else z.addFile(f, p)
            Prog.add(FS.sizeOf(f))
        }
    }

    /** إنشاء 7z أو tar.gz (fmt = "7z" أو "tgz") */
    fun createArchive(files: List<File>, out: File, fmt: String) {
        val buf = ByteArray(64 * 1024)
        if (fmt == "7z") {
            SevenZOutputFile(out).use { z ->
                fun add(f: File, base: String) {
                    val name = if (base.isEmpty()) f.name else base + "/" + f.name
                    z.putArchiveEntry(z.createArchiveEntry(f, name))
                    if (f.isFile) {
                        FileInputStream(f).use { i ->
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                z.write(buf, 0, n)
                                Prog.add(n.toLong())
                            }
                        }
                    }
                    z.closeArchiveEntry()
                    if (f.isDirectory) f.listFiles()?.forEach { add(it, name) }
                }
                files.forEach { add(it, "") }
            }
        } else {
            TarArchiveOutputStream(GzipCompressorOutputStream(BufferedOutputStream(FileOutputStream(out)))).use { t ->
                t.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                t.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
                fun add(f: File, base: String) {
                    val name = if (base.isEmpty()) f.name else base + "/" + f.name
                    t.putArchiveEntry(t.createArchiveEntry(f, name))
                    if (f.isFile) {
                        FileInputStream(f).use { i ->
                            while (true) {
                                val n = i.read(buf)
                                if (n < 0) break
                                t.write(buf, 0, n)
                                Prog.add(n.toLong())
                            }
                        }
                    }
                    t.closeArchiveEntry()
                    if (f.isDirectory) f.listFiles()?.forEach { add(it, name) }
                }
                files.forEach { add(it, "") }
            }
        }
    }
}
