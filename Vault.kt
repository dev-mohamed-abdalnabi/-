package com.next.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** خزنة مشفرة: AES-256-GCM على قطع 1MB، والمفتاح من الباسورد بـ PBKDF2 */
object Vault {
    class VItem(val id: String, val name: String, val size: Long, val date: Long)

    private const val CH = 1 shl 20
    private const val ITER = 150_000

    var key by mutableStateOf<SecretKeySpec?>(null)
    var items by mutableStateOf<List<VItem>>(emptyList())

    private val dir: File get() = File(Ctl.app.filesDir, "vault")
    private val idxFile: File get() = File(dir, "index.enc")
    private val saltFile: File get() = File(dir, "salt")
    private val viewDir: File get() = File(Ctl.app.cacheDir, "vaultview")

    fun exists(): Boolean = idxFile.exists() && saltFile.exists()

    private fun derive(pwd: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pwd.toCharArray(), salt, ITER, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    fun setup(pwd: String) {
        dir.mkdirs()
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        saltFile.writeBytes(salt)
        key = derive(pwd, salt)
        items = emptyList()
        saveIndex()
    }

    /** بيرجّع true لو الباسورد صح */
    fun unlock(pwd: String): Boolean {
        return try {
            val k = derive(pwd, saltFile.readBytes())
            val raw = idxFile.readBytes()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, raw.copyOfRange(0, 12)))
            val json = String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
            val a = JSONArray(json)
            val list = ArrayList<VItem>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list.add(VItem(o.getString("id"), o.getString("name"), o.optLong("size"), o.optLong("date")))
            }
            items = list
            key = k
            true
        } catch (e: Exception) {
            false
        }
    }

    fun lock() {
        key = null
        items = emptyList()
        clearViews()
    }

    fun clearViews() {
        try { viewDir.deleteRecursively() } catch (e: Exception) { }
    }

    private fun saveIndex() {
        val k = key ?: throw IOException("الخزنة مقفولة")
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("size", it.size).put("date", it.date)) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        val tmp = File(dir, "index.tmp")
        tmp.writeBytes(iv + c.doFinal(a.toString().toByteArray(Charsets.UTF_8)))
        if (!tmp.renameTo(idxFile)) {
            idxFile.delete()
            if (!tmp.renameTo(idxFile)) throw IOException("تعذر حفظ فهرس الخزنة")
        }
    }

    private fun nonce(prefix: ByteArray, i: Int): ByteArray = prefix + ByteBuffer.allocate(4).putInt(i).array()

    private fun readFully(i: InputStream, b: ByteArray): Int {
        var n = 0
        while (n < b.size) {
            val r = i.read(b, n, b.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }

    private fun encryptFile(src: File, dst: File) {
        val k = key ?: throw IOException("الخزنة مقفولة")
        val prefix = ByteArray(8).also { SecureRandom().nextBytes(it) }
        val total = src.length()
        DataOutputStream(BufferedOutputStream(FileOutputStream(dst))).use { o ->
            o.write(prefix)
            FileInputStream(src).use { i ->
                val buf = ByteArray(CH)
                var done = 0L
                var idx = 0
                do {
                    val n = readFully(i, buf)
                    done += n
                    val last = done >= total || n < CH
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, nonce(prefix, idx)))
                    c.updateAAD(byteArrayOf(if (last) 1 else 0))
                    val ct = c.doFinal(buf, 0, n)
                    o.writeByte(if (last) 1 else 0)
                    o.writeInt(ct.size)
                    o.write(ct)
                    Prog.add(n.toLong())
                    idx++
                } while (!last)
            }
        }
    }

    private fun decryptFile(src: File, dst: File) {
        val k = key ?: throw IOException("الخزنة مقفولة")
        DataInputStream(BufferedInputStream(FileInputStream(src))).use { i ->
            val prefix = ByteArray(8)
            i.readFully(prefix)
            FileOutputStream(dst).use { o ->
                var idx = 0
                while (true) {
                    val last = i.readByte().toInt() == 1
                    val len = i.readInt()
                    if (len < 16 || len > CH + 16) throw IOException("الملف تالف")
                    val ct = ByteArray(len)
                    i.readFully(ct)
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, nonce(prefix, idx)))
                    c.updateAAD(byteArrayOf(if (last) 1 else 0))
                    o.write(c.doFinal(ct))
                    idx++
                    if (last) break
                }
            }
        }
    }

    /** إضافة ملفات/مجلدات (المجلد بيتضغط zip الأول) */
    fun addAll(files: List<File>, deleteOriginals: Boolean): Int {
        dir.mkdirs()
        var n = 0
        for (f in files) {
            val tmp = if (f.isDirectory) File(Ctl.app.cacheDir, "vtmp_${System.nanoTime()}.zip").also { FS.zip(listOf(f), it) } else null
            val src = tmp ?: f
            val id = UUID.randomUUID().toString()
            try {
                encryptFile(src, File(dir, id))
            } catch (e: Exception) {
                File(dir, id).delete()
                throw e
            } finally {
                tmp?.delete()
            }
            val name = if (f.isDirectory) f.name + ".zip" else f.name
            items = items + VItem(id, name, src.length(), System.currentTimeMillis())
            saveIndex()
            if (deleteOriginals) f.deleteRecursively()
            n++
        }
        return n
    }

    /** فك الملف في الكاش المؤقت */
    fun decryptToCache(it: VItem): File {
        val d = File(viewDir, it.id).also { x -> x.mkdirs() }
        val out = File(d, it.name)
        decryptFile(File(dir, it.id), out)
        return out
    }

    fun export(it: VItem): File {
        val dest = Incoming.saveDir().also { x -> x.mkdirs() }
        val out = FS.unique(dest, it.name, false)
        try {
            decryptFile(File(dir, it.id), out)
        } catch (e: Exception) {
            out.delete()
            throw e
        }
        return out
    }

    fun remove(it: VItem) {
        File(dir, it.id).delete()
        items = items.filter { x -> x.id != it.id }
        saveIndex()
    }
}

@Composable
fun VaultScreen() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ Ctl.pop() }) { Icon(Ic.Back, "رجوع") }
            Text("الخزنة المشفرة", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            if (Vault.key != null) TextButton({ Vault.lock() }) { Icon(Ic.Lock, null, Modifier.size(18.dp)); Text("  قفل") }
        }
        if (Vault.key == null) VaultLogin() else VaultList()
    }
}

@Composable
private fun VaultLogin() {
    val cs = MaterialTheme.colorScheme
    val fresh = remember { !Vault.exists() }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (fresh) "اعمل باسورد للخزنة. الملفات بتتشفر بيه، ولو نسيته مفيش أي طريقة لاسترجاعها."
            else "اكتب باسورد الخزنة علشان تفتحها.",
            color = cs.onSurfaceVariant
        )
        TextField(
            p1, { p1 = it; err = null }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
            placeholder = { Text("الباسورد") }, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (fresh) {
            TextField(
                p2, { p2 = it; err = null }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                placeholder = { Text("اكتبه تاني") }, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        err?.let { Text(it, color = cs.error) }
        Button(
            {
                when {
                    working -> {}
                    fresh && p1.length < 4 -> err = "الباسورد لازم يكون 4 حروف على الأقل"
                    fresh && p1 != p2 -> err = "الباسورد مش متطابق"
                    else -> {
                        working = true
                        Ctl.task("جاري التجهيز…", {
                            if (fresh) { Vault.setup(p1); true } else Vault.unlock(p1)
                        }) { ok ->
                            working = false
                            if (!ok) err = "الباسورد غلط"
                        }
                    }
                }
            },
            Modifier.fillMaxWidth()
        ) { Text(if (fresh) "إنشاء الخزنة" else "فتح") }
    }
}

@Composable
private fun VaultList() {
    val cs = MaterialTheme.colorScheme
    var sel by remember { mutableStateOf<Vault.VItem?>(null) }
    Column(Modifier.fillMaxSize()) {
        Button(
            {
                val files = Ctl.clip.toList()
                val cut = Ctl.clipCut
                if (files.isEmpty()) {
                    Ctl.toast("انسخ أو قص ملفات من التصفح الأول، وبعدين ارجع هنا واضغط إضافة")
                } else {
                    Ctl.task("جاري التشفير…", {
                        Prog.begin(files.sumOf { FS.sizeOf(it) })
                        Vault.addAll(files, cut)
                    }) { n ->
                        if (cut) Ctl.clip.clear()
                        FS.scan(Ctl.app, files.map { it.path })
                        Ctl.toast("اتضاف $n عنصر للخزنة")
                    }
                }
            },
            Modifier.fillMaxWidth()
        ) { Text("إضافة اللي في الحافظة (${Ctl.clip.size})") }
        if (Vault.items.isEmpty()) {
            Text("الخزنة فاضية", Modifier.padding(top = 24.dp), color = cs.onSurfaceVariant)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(Vault.items, key = { it.id }) { it ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { sel = it }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Ic.File, null, tint = cs.primary)
                    Column(Modifier.weight(1f)) {
                        Text(it.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                        Text(FS.fmtSize(it.size), color = cs.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    sel?.let { it ->
        AlertDialog(
            onDismissRequest = { sel = null }, shape = RoundedCornerShape(28.dp),
            title = { Text(it.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton({
                        sel = null
                        Ctl.task("جاري فك التشفير…", { Vault.decryptToCache(it) }) { f -> Ctl.open(f, listOf(f)) }
                    }) { Text("فتح") }
                    TextButton({
                        sel = null
                        Ctl.task("جاري الاستخراج…", { Vault.export(it) }) { f ->
                            FS.scan(Ctl.app, listOf(f.path))
                            Ctl.toast("اتستخرج في Download")
                        }
                    }) { Text("استخراج إلى Download") }
                    TextButton({
                        sel = null
                        Ctl.task("جاري الحذف…", { Vault.remove(it) }) { Ctl.toast("اتحذف من الخزنة") }
                    }) { Text("حذف من الخزنة", color = cs.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ sel = null }) { Text("إلغاء") } }
        )
    }
}
