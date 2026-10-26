package com.next.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** وصول لـ Android/data و Android/obb عن طريق SAF (أندرويد 11 و12). أقل من 11 بيتفتح كمجلد عادي. */
object Saf {
    private const val AUTH = "com.android.externalstorage.documents"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("next_saf", Context.MODE_PRIVATE)

    fun pickIntent(sub: String): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(AUTH, "primary:Android/$sub"))
            .addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )

    fun treeOf(ctx: Context, sub: String): Uri? {
        val u = sp(ctx).getString(sub, null)?.let { Uri.parse(it) } ?: return null
        return u.takeIf { x -> ctx.contentResolver.persistedUriPermissions.any { it.uri == x && it.isWritePermission } }
    }

    /** بيرجع false لو المجلد المختار مش Android/<sub> */
    fun save(ctx: Context, sub: String, uri: Uri): Boolean {
        val id = DocumentsContract.getTreeDocumentId(uri)
        if (!id.equals("primary:Android/$sub", true)) return false
        ctx.contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        sp(ctx).edit().putString(sub, uri.toString()).apply()
        return true
    }

    fun forget(ctx: Context, sub: String) {
        sp(ctx).getString(sub, null)?.let {
            try { ctx.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } catch (e: Exception) { }
        }
        sp(ctx).edit().remove(sub).apply()
    }

    class Doc(val id: String, val name: String, val mime: String, val size: Long, val time: Long) {
        val isDir: Boolean get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }

    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)
    fun uri(tree: Uri, id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    fun stat(ctx: Context, tree: Uri, id: String): Doc? {
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        return try {
            ctx.contentResolver.query(uri(tree, id), cols, null, null, null)?.use { c ->
                if (c.moveToFirst()) Doc(
                    c.getString(0), c.getString(1) ?: "?", c.getString(2) ?: "",
                    if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4)
                ) else null
            }
        } catch (e: Exception) { null }
    }

    fun list(ctx: Context, tree: Uri, parentId: String): List<Doc> {
        val out = ArrayList<Doc>()
        val kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        ctx.contentResolver.query(kids, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    Doc(
                        c.getString(0), c.getString(1) ?: "?", c.getString(2) ?: "",
                        if (c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4)
                    )
                )
            }
        }
        return out.sortedWith(compareBy<Doc>({ !it.isDir }, { it.name.lowercase() }))
    }

    fun copyOut(ctx: Context, tree: Uri, d: Doc, dstDir: File): File {
        val t = FS.unique(dstDir, d.name, d.isDir)
        if (d.isDir) {
            t.mkdirs()
            for (k in list(ctx, tree, d.id)) copyOut(ctx, tree, k, t)
        } else {
            t.parentFile?.mkdirs()
            val ins = ctx.contentResolver.openInputStream(uri(tree, d.id)) ?: throw IOException("تعذر قراءة ${d.name}")
            ins.use { i ->
                t.outputStream().use { o ->
                    val b = ByteArray(128 * 1024)
                    while (true) {
                        val r = i.read(b)
                        if (r < 0) break
                        o.write(b, 0, r)
                        Prog.add(r.toLong())
                    }
                }
            }
        }
        return t
    }

    fun copyIn(ctx: Context, tree: Uri, parentId: String, src: File) {
        val parent = uri(tree, parentId)
        if (src.isDirectory) {
            val nu = DocumentsContract.createDocument(ctx.contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, src.name)
                ?: throw IOException("تعذر إنشاء ${src.name}")
            val nid = DocumentsContract.getDocumentId(nu)
            src.listFiles()?.forEach { copyIn(ctx, tree, nid, it) }
        } else {
            val mime = FS.mimeOf(src).let { if (it == "*/*") "application/octet-stream" else it }
            val nu = DocumentsContract.createDocument(ctx.contentResolver, parent, mime, src.name)
                ?: throw IOException("تعذر إنشاء ${src.name}")
            val out = ctx.contentResolver.openOutputStream(nu) ?: throw IOException("تعذر الكتابة في ${src.name}")
            out.use { o ->
                src.inputStream().use { i ->
                    val b = ByteArray(128 * 1024)
                    while (true) {
                        val r = i.read(b)
                        if (r < 0) break
                        o.write(b, 0, r)
                        Prog.add(r.toLong())
                    }
                }
            }
        }
    }

    fun delete(ctx: Context, tree: Uri, id: String): Boolean = DocumentsContract.deleteDocument(ctx.contentResolver, uri(tree, id))
    fun rename(ctx: Context, tree: Uri, id: String, name: String): Boolean =
        DocumentsContract.renameDocument(ctx.contentResolver, uri(tree, id), name) != null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AndroidDirScreen(sub: String) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val tree = remember(Ctl.rev, sub) { Saf.treeOf(ctx, sub) }
    val stack = remember { mutableStateListOf<Pair<String, String>>() }
    var docs by remember { mutableStateOf<List<Saf.Doc>?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Saf.Doc?>(null) }
    var confirmDel by remember { mutableStateOf<Saf.Doc?>(null) }

    val parentId = if (tree == null) "" else stack.lastOrNull()?.first ?: Saf.rootId(tree)

    LaunchedEffect(tree, parentId, Ctl.rev) {
        if (tree != null) {
            docs = withContext(Dispatchers.IO) { try { Saf.list(ctx, tree, parentId) } catch (e: Exception) { emptyList() } }
        }
    }
    BackHandler { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else Ctl.pop() }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(
                if (stack.isEmpty()) "Android/$sub" else stack.last().second,
                "Next · " + (if (stack.isEmpty()) "وصول عبر صلاحية النظام (SAF)" else "Android/$sub/" + stack.joinToString("/") { it.second }),
                onBack = { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) else Ctl.pop() }
            )
            if (tree == null) {
                Column(Modifier.weight(1f).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyState(
                        Ic.Folder, "محتاج صلاحية الوصول",
                        if (android.os.Build.VERSION.SDK_INT >= 33)
                            "أندرويد 13 وأحدث بيمنع أي تطبيق ملفات من الوصول للمجلد ده بالطريقة العادية. جرّب المنح، ولو النظام رفض يبقى محتاج Shizuku أو root."
                        else "دوس منح الوصول، وبعدين \"استخدام هذا المجلد\" وإنت في Android/$sub."
                    )
                    Button({ Ctl.pickSaf(sub) }) { Text("منح الوصول") }
                }
            } else {
                val list = docs
                if (list != null && list.isEmpty()) EmptyState(Ic.Folder, "المجلد فاضي", "مفيش حاجة هنا.")
                LazyColumn(
                    Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(list ?: emptyList(), key = { it.id }) { dd ->
                        val kind = if (dd.isDir) Kind.FOLDER else FS.kindOf(dd.name.substringAfterLast('.', "").lowercase())
                        val c = kindColor(kind)
                        Box {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                    .combinedClickable(
                                        onClick = {
                                            if (dd.isDir) stack.add(dd.id to dd.name)
                                            else Ctl.task("جاري التحضير…", {
                                                Prog.begin(0)
                                                Saf.copyOut(ctx, tree, dd, File(ctx.cacheDir, "safview").also { it.deleteRecursively(); it.mkdirs() })
                                            }) { f -> Ctl.open(f, listOf(f)) }
                                        },
                                        onLongClick = { menuFor = dd.id }
                                    ).padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                    Icon(if (dd.isDir) Ic.Folder else iconFor(kind), null, Modifier.size(22.dp), tint = c)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(dd.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (dd.isDir) "مجلد" else FS.fmtSize(dd.size) + (if (dd.time > 0) " · " + FS.fmtDate(dd.time) else ""),
                                        maxLines = 1, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                    )
                                }
                            }
                            DropdownMenu(menuFor == dd.id, { menuFor = null }) {
                                DropdownMenuItem({ Text("تصدير للتنزيلات") }, {
                                    menuFor = null
                                    Ctl.task("جاري التصدير…", {
                                        Prog.begin(0)
                                        Saf.copyOut(ctx, tree, dd, File(FS.downloads(), "Next-Export").also { it.mkdirs() }).path
                                    }) { p -> FS.scan(ctx, listOf(p)); Ctl.toast("اتصدّر في Download/Next-Export") }
                                }, leadingIcon = { Icon(Ic.Download, null, Modifier.size(20.dp)) })
                                DropdownMenuItem({ Text("إعادة تسمية") }, { menuFor = null; renaming = dd }, leadingIcon = { Icon(Ic.Edit, null, Modifier.size(20.dp)) })
                                DropdownMenuItem({ Text("حذف", color = cs.error) }, { menuFor = null; confirmDel = dd }, leadingIcon = { Icon(Ic.Trash, null, Modifier.size(20.dp), tint = cs.error) })
                            }
                        }
                    }
                }
                if (Ctl.clip.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button({
                            val items = Ctl.clip.toList()
                            val cut = Ctl.clipCut
                            Ctl.task("جاري اللصق…", {
                                Prog.begin(items.sumOf { FS.sizeOf(it) })
                                for (f in items) {
                                    Saf.copyIn(ctx, tree, parentId, f)
                                    if (cut) f.deleteRecursively()
                                }
                                items.size
                            }) { n -> if (cut) Ctl.clearClip(); Ctl.toast("اتلصق $n عنصر") }
                        }, Modifier.weight(1f)) { Text("لصق هنا (${Ctl.clip.size})") }
                        TextButton({ Ctl.clearClip() }) { Text("إلغاء") }
                    }
                }
            }
        }
    }

    renaming?.let { dd ->
        var t by remember { mutableStateOf(dd.name) }
        AlertDialog(
            onDismissRequest = { renaming = null }, shape = RoundedCornerShape(28.dp),
            title = { Text("إعادة تسمية") },
            text = { TextField(t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton({
                    renaming = null
                    Ctl.task("جاري التغيير…", { tree != null && Saf.rename(ctx, tree, dd.id, t.trim()) }) { ok ->
                        Ctl.toast(if (ok) "تم تغيير الاسم" else "تعذر تغيير الاسم")
                    }
                }) { Text("تغيير") }
            },
            dismissButton = { TextButton({ renaming = null }) { Text("إلغاء") } }
        )
    }
    confirmDel?.let { dd ->
        AlertDialog(
            onDismissRequest = { confirmDel = null }, shape = RoundedCornerShape(28.dp),
            title = { Text("تحذف ${dd.name}؟") },
            text = { Text("الحذف هنا نهائي ومفيش سلة محذوفات. ممكن يأثر على بيانات التطبيق.") },
            confirmButton = {
                TextButton({
                    confirmDel = null
                    Ctl.task("جاري الحذف…", { tree != null && Saf.delete(ctx, tree, dd.id) }) { ok ->
                        Ctl.toast(if (ok) "تم الحذف" else "تعذر الحذف")
                    }
                }) { Text("حذف", color = cs.error) }
            },
            dismissButton = { TextButton({ confirmDel = null }) { Text("إلغاء") } }
        )
    }
}
