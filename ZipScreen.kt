package com.next.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private class ZNode(val name: String, val full: String, val isDir: Boolean, val size: Long, val time: Long, val count: Int)

// ===================== استعراض الأرشيفات من غير فك (zip/rar/7z/tar...) =====================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZipScreen(path: String) {
    val cs = MaterialTheme.colorScheme
    val zip = remember(path) { File(path) }
    var entries by remember { mutableStateOf<List<FS.ZEntry>?>(null) }
    var encrypted by remember { mutableStateOf(false) }
    var pwd by remember { mutableStateOf<String?>(null) }
    var askPwd by remember { mutableStateOf<Boolean?>(null) } // null = مفيش سؤال، false = أول مرة، true = غلط
    var pendingAfterPwd by remember { mutableStateOf<(() -> Unit)?>(null) }
    var cur by remember { mutableStateOf("") }
    val sel = remember { mutableStateListOf<String>() }

    LaunchedEffect(path, pwd) {
        val r = withContext(Dispatchers.IO) {
            try { Result.success(Arc.list(zip, pwd)) } catch (e: Exception) { Result.failure<Arc.Listing>(e) }
        }
        r.onSuccess { entries = it.entries; encrypted = it.encrypted }
        r.onFailure { e ->
            if (e is Arc.NeedPassword) askPwd = e.wrong
            else { Ctl.toast(e.message ?: "الملف ده مش سليم أو تالف"); Ctl.pop() }
        }
    }
    DisposableEffect(path) { onDispose { Arc.clearCache(Ctl.app) } }

    fun up() {
        cur = if (cur.isEmpty()) "" else cur.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
    }
    BackHandler {
        when {
            sel.isNotEmpty() -> sel.clear()
            cur.isNotEmpty() -> up()
            else -> Ctl.pop()
        }
    }

    val nodes = remember(entries, cur) {
        val all = entries ?: emptyList()
        val folders = LinkedHashMap<String, LongArray>() // size, time, count
        val files = ArrayList<ZNode>()
        for (e in all) {
            if (!e.name.startsWith(cur) || e.name == cur) continue
            val rest = e.name.removePrefix(cur)
            if (rest.contains('/')) {
                val seg = rest.substringBefore('/')
                val a = folders.getOrPut(seg) { longArrayOf(0, 0, 0) }
                if (!e.isDir) { a[0] += maxOf(e.size, 0); a[2]++ }
                a[1] = maxOf(a[1], e.time)
            } else if (e.isDir) {
                folders.getOrPut(rest.trimEnd('/')) { longArrayOf(0, e.time, 0) }
            } else files.add(ZNode(rest, e.name, false, e.size, e.time, 0))
        }
        folders.map { (k, v) -> ZNode(k, "$cur$k/", true, v[0], v[1], v[2].toInt()) }
            .sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
    }
    val fileCount = entries?.count { !it.isDir } ?: 0
    val total = entries?.sumOf { maxOf(it.size, 0L) } ?: 0L

    fun needPwd(wrong: Boolean, retry: () -> Unit) {
        pendingAfterPwd = retry
        askPwd = wrong
    }

    fun extractTo(names: List<String>?) {
        val dest = FS.unique(Incoming.destParent(Ctl.app, zip) ?: return, zip.name.substringBefore('.'), true)
        if (encrypted && pwd == null) { needPwd(false) { extractTo(names) }; return }
        Ctl.unzipSelected(zip, names, dest, pwd) { wrong -> needPwd(wrong) { extractTo(names) } }
        sel.clear()
    }

    fun openNode(n: ZNode) {
        if (sel.isNotEmpty()) { if (n.full in sel) sel.remove(n.full) else sel.add(n.full); return }
        if (n.isDir) { cur = n.full; return }
        if (encrypted && pwd == null) { needPwd(false) { openNode(n) }; return }
        Ctl.task("جاري التحضير…", { Arc.extractToCache(Ctl.app, zip, n.full, pwd) }, fail = { e ->
            if (e is Arc.NeedPassword) { needPwd(e.wrong) { openNode(n) }; true } else false
        }) { f -> Ctl.open(f, listOf(f)) }
    }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(
                zip.name,
                if (entries == null) "Next · جاري القراءة…" else "Next · ${Arc.fmt(zip)?.uppercase()} · $fileCount ملف · ${FS.fmtSize(total)}" + (if (encrypted) " · 🔒" else "") + (if (cur.isNotEmpty()) " · /$cur" else ""),
                onBack = { if (cur.isNotEmpty() && sel.isEmpty()) up() else if (sel.isNotEmpty()) sel.clear() else Ctl.pop() }
            )
            if (entries != null && nodes.isEmpty()) {
                EmptyState(Ic.Archive, "فاضي", "مفيش حاجة هنا.")
            }
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(nodes, key = { it.full }) { n ->
                    val picked = n.full in sel
                    val kind = if (n.isDir) Kind.FOLDER else FS.kindOf(n.name.substringAfterLast('.', "").lowercase())
                    val c = kindColor(kind)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                            .background(if (picked) cs.primaryContainer.copy(alpha = 0.6f) else androidx.compose.ui.graphics.Color.Transparent)
                            .combinedClickable(onClick = { openNode(n) }, onLongClick = { if (picked) sel.remove(n.full) else sel.add(n.full) })
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                            if (picked) SelCheck() else Icon(if (n.isDir) Ic.Folder else iconFor(kind), null, Modifier.size(22.dp), tint = c)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(n.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (n.isDir) "${n.count} ملف · ${FS.fmtSize(n.size)}" else "${FS.fmtSize(maxOf(n.size, 0))}" + (if (n.time > 0) " · ${FS.fmtDate(n.time)}" else ""),
                                maxLines = 1, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            if (entries != null) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (sel.isNotEmpty()) {
                        Button({ extractTo(sel.toList()) }, Modifier.weight(1f)) { Text("فك المحدد (${sel.size})") }
                        OutlinedButton({ sel.clear() }) { Text("إلغاء") }
                    } else {
                        Button({ extractTo(null) }, Modifier.weight(1f)) { Text("فك الكل هنا") }
                        if (cur.isNotEmpty()) OutlinedButton({ extractTo(listOf(cur)) }) { Text("فك المجلد ده") }
                    }
                }
            }
        }
    }

    askPwd?.let { wrong ->
        var t by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askPwd = null; pendingAfterPwd = null; if (entries == null) Ctl.pop() },
            shape = RoundedCornerShape(28.dp),
            title = { Text(if (wrong) "الباسورد غلط" else "الأرشيف محمي بباسورد", style = MaterialTheme.typography.titleLarge) },
            text = {
                TextField(
                    t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    placeholder = { Text("اكتب الباسورد") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton({
                    val r = pendingAfterPwd
                    pendingAfterPwd = null
                    askPwd = null
                    pwd = t
                    r?.invoke()
                }) { Text("تأكيد") }
            },
            dismissButton = { TextButton({ askPwd = null; pendingAfterPwd = null; if (entries == null) Ctl.pop() }) { Text("إلغاء") } }
        )
    }
}
