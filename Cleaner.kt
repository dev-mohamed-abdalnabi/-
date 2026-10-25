package com.next.files

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** تحليل التخزين والتنضيف: أكبر المجلدات والملفات، المكررات، الفاضي، المؤقت، APK */
object Cleaner {
    class Report(
        val root: File, val total: Long, val files: Int,
        val byKind: Map<Kind, Long>,
        val topFiles: List<File>,
        val folders: List<Pair<String, Long>>,
        val sizeGroups: Map<Long, List<File>>,
        val emptyDirs: List<File>, val emptyFiles: List<File>,
        val junk: List<File>, val apks: List<File>
    )

    var report by mutableStateOf<Report?>(null)
    var scanning by mutableStateOf(false)
    var scanned by mutableIntStateOf(0)
    var dups by mutableStateOf<List<List<File>>?>(null)
    var dupBusy by mutableStateOf(false)

    fun scan(root: File) {
        if (scanning) return
        scanning = true
        scanned = 0
        dups = null
        Ctl.scope.launch {
            try {
                report = withContext(Dispatchers.IO) { doScan(root) }
            } catch (e: Exception) {
                Ctl.toast("تعذر تحليل المساحة")
            } finally {
                scanning = false
            }
        }
    }

    private fun doScan(root: File): Report {
        val byKind = HashMap<Kind, Long>()
        val folderSize = HashMap<String, Long>()
        val bySize = HashMap<Long, MutableList<File>>()
        val heap = java.util.PriorityQueue<Pair<Long, File>>(compareBy<Pair<Long, File>> { it.first })
        val emptyDirs = ArrayList<File>()
        val emptyFiles = ArrayList<File>()
        val junk = ArrayList<File>()
        val apks = ArrayList<File>()
        val junkExt = setOf("tmp", "temp", "log", "bak", "old", "crdownload", "part", "dmp")
        val androidPath = root.path + "/Android"
        val rootLen = root.path.length + 1
        var total = 0L
        var n = 0
        val stack = ArrayDeque<File>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val d = stack.removeLast()
            val kids = d.listFiles() ?: continue
            val inAndroid = d.path == androidPath || d.path.startsWith("$androidPath/")
            if (kids.isEmpty() && d != root && !inAndroid) emptyDirs.add(d)
            for (k in kids) {
                if (k.isDirectory) {
                    if (java.nio.file.Files.isSymbolicLink(k.toPath())) continue
                    if (!inAndroid && (k.name == ".thumbnails" || k.name == "LOST.DIR")) { junk.add(k); continue }
                    stack.addLast(k)
                } else {
                    val len = k.length()
                    total += len
                    n++
                    if (n % 400 == 0) scanned = n
                    val ext = k.extension.lowercase()
                    val kind = FS.kindOf(ext)
                    byKind[kind] = (byKind[kind] ?: 0L) + len
                    val rel = k.path.substring(rootLen)
                    val seg = if (rel.contains('/')) rel.substringBefore('/') else ""
                    folderSize[seg] = (folderSize[seg] ?: 0L) + len
                    if (heap.size < 60) heap.add(len to k) else if (len > heap.peek().first) { heap.poll(); heap.add(len to k) }
                    if (!inAndroid) {
                        if (len == 0L) { if (k.name != ".nomedia") emptyFiles.add(k) }
                        else {
                            if (ext in junkExt) junk.add(k)
                            if (ext == "apk") apks.add(k)
                            if (len >= 100 * 1024) bySize.getOrPut(len) { ArrayList() }.add(k)
                        }
                    }
                }
            }
        }
        scanned = n
        return Report(
            root, total, n, byKind,
            heap.sortedByDescending { it.first }.map { it.second },
            folderSize.filter { it.key.isNotEmpty() }.toList().sortedByDescending { it.second }.take(12),
            bySize.filter { it.value.size >= 2 },
            emptyDirs, emptyFiles, junk, apks
        )
    }

    private fun md5(f: File, limit: Long = Long.MAX_VALUE): String {
        val md = MessageDigest.getInstance("MD5")
        val b = ByteArray(64 * 1024)
        var left = limit
        f.inputStream().use { i ->
            while (left > 0) {
                val r = i.read(b, 0, minOf(b.size.toLong(), left).toInt())
                if (r < 0) break
                md.update(b, 0, r)
                left -= r
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun findDups() {
        val r = report ?: return
        if (dupBusy) return
        dupBusy = true
        scanned = 0
        Ctl.scope.launch {
            try {
                dups = withContext(Dispatchers.IO) {
                    val out = ArrayList<List<File>>()
                    var n = 0
                    for ((_, files) in r.sizeGroups.entries.sortedByDescending { it.key }) {
                        val live = files.filter { it.exists() }
                        val partial = live.groupBy { try { md5(it, 65536) } catch (e: Exception) { it.path } }
                        for ((_, g) in partial) {
                            if (g.size < 2) continue
                            val full = g.groupBy { try { md5(it) } catch (e: Exception) { it.path } }
                            for ((_, h) in full) if (h.size >= 2) out.add(h.sortedBy { it.lastModified() })
                        }
                        n += files.size
                        scanned = n
                    }
                    out.sortedByDescending { it[0].length() * (it.size - 1) }
                }
            } catch (e: Exception) {
                Ctl.toast("تعذر فحص المكررات")
            } finally {
                dupBusy = false
            }
        }
    }
}

private fun kindTitle(k: Kind) = when (k) {
    Kind.IMAGE -> "صور"; Kind.VIDEO -> "فيديو"; Kind.AUDIO -> "صوت"; Kind.DOC -> "مستندات"
    Kind.TEXT -> "نصوص وأكواد"; Kind.APK -> "تطبيقات"; Kind.ARCHIVE -> "مضغوطات"; else -> "أخرى"
}

private fun sumSize(l: List<File>) = l.sumOf { if (it.isFile) it.length() else 0L }

// ===================== شاشة المساحة =====================
@Composable
fun StorageScreen(d: DlgState) {
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val primary = remember { Environment.getExternalStorageDirectory() }
    var vol by remember { mutableStateOf<Vol?>(null) }
    LaunchedEffect(Unit) {
        vol = withContext(Dispatchers.IO) { FS.volumes(ctx).firstOrNull() }
        if (Cleaner.report == null) Cleaner.scan(primary)
    }
    val r = Cleaner.report

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader("المساحة والتنضيف", "Next · تحليل التخزين", onBack = { Ctl.pop() }) {
                IconButton({ Cleaner.scan(primary) }) { Icon(Ic.Restore, "تحليل من جديد") }
            }
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                vol?.let { v ->
                    item {
                        val used = (v.total - v.free).coerceAtLeast(0)
                        val frac = if (v.total > 0) used.toFloat() / v.total else 0f
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("${FS.fmtSize(used)} مستخدم من ${FS.fmtSize(v.total)}", style = MaterialTheme.typography.titleMedium)
                            Text("متاح ${FS.fmtSize(v.free)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(cs.secondaryContainer)) {
                                Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxSize().clip(CircleShape).background(cs.primary))
                            }
                        }
                    }
                }
                if (Cleaner.scanning) item {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        Text("جاري التحليل… ${Cleaner.scanned} ملف", style = MaterialTheme.typography.titleSmall)
                    }
                }
                if (r != null) {
                    item {
                        Text("إيه واخد المساحة", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                    }
                    item {
                        val parts = r.byKind.filter { it.key != Kind.FOLDER && it.value > 0 }.toList().sortedByDescending { it.second }
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape)) {
                                parts.forEach { (k, s) ->
                                    Box(Modifier.weight((s.toFloat() / r.total.coerceAtLeast(1)).coerceAtLeast(0.01f)).fillMaxSize().background(kindColor(k)))
                                }
                            }
                            parts.forEach { (k, s) ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Box(Modifier.size(10.dp).clip(CircleShape).background(kindColor(k)))
                                    Text(kindTitle(k), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Text(FS.fmtSize(s), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                                }
                            }
                            Text("${r.files} ملف · ${FS.fmtSize(r.total)}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    item { Text("أكبر المجلدات", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.padding(top = 4.dp)) }
                    items(r.folders, key = { "f" + it.first }) { (name, size) ->
                        val frac = (size.toFloat() / (r.folders.firstOrNull()?.second ?: 1L).coerceAtLeast(1)).coerceIn(0.03f, 1f)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { Ctl.openDir(File(r.root, name)) }.padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(FS.fmtSize(size), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(cs.secondaryContainer)) {
                                Box(Modifier.fillMaxWidth(frac).fillMaxSize().clip(CircleShape).background(cs.primary))
                            }
                        }
                    }
                    item { Text("تنضيف", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.padding(top = 8.dp)) }
                    item {
                        val dd = Cleaner.dups
                        CleanRow(
                            Ic.Copy, "الملفات المكررة",
                            when {
                                Cleaner.dupBusy -> "جاري الفحص… ${Cleaner.scanned}/${r.sizeGroups.values.sumOf { it.size }}"
                                dd == null -> "دوس علشان تفحص (الملفات أكبر من 100 ك.ب)"
                                else -> "${dd.size} مجموعة · يوفر ${FS.fmtSize(dd.sumOf { g -> g[0].length() * (g.size - 1) })}"
                            }
                        ) {
                            if (dd == null) Cleaner.findDups() else Ctl.push(Overlay.Clean("dups"))
                        }
                    }
                    item { CleanRow(Ic.Chart, "أكبر الملفات", "${r.topFiles.size} ملف · ${FS.fmtSize(sumSize(r.topFiles))}") { Ctl.push(Overlay.Clean("large")) } }
                    item { CleanRow(Ic.Clock, "ملفات مؤقتة وسجلات", "${r.junk.size} عنصر · ${FS.fmtSize(sumSize(r.junk))}") { Ctl.push(Overlay.Clean("junk")) } }
                    item { CleanRow(Ic.Apps, "ملفات APK", "${r.apks.size} ملف · ${FS.fmtSize(sumSize(r.apks))}") { Ctl.push(Overlay.Clean("apks")) } }
                    item { CleanRow(Ic.File, "ملفات فاضية", "${r.emptyFiles.size} ملف") { Ctl.push(Overlay.Clean("emptyf")) } }
                    item { CleanRow(Ic.Folder, "مجلدات فاضية", "${r.emptyDirs.size} مجلد") { Ctl.push(Overlay.Clean("emptyd")) } }
                    item { CleanRow(Ic.Trash, "سلة المحذوفات", "فضّيها وارجع مساحتها") { Ctl.push(Overlay.Trash) } }
                }
            }
        }
    }
}

@Composable
private fun CleanRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = cs.onTertiaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
        }
        Icon(Ic.ChevL, null, tint = cs.onSurfaceVariant)
    }
}

// ===================== قايمة التنضيف (تحديد وحذف) =====================
private class CRow(val file: File?, val header: String?)

@Composable
fun CleanScreen(key: String, d: DlgState) {
    val cs = MaterialTheme.colorScheme
    val title = when (key) {
        "dups" -> "الملفات المكررة"; "large" -> "أكبر الملفات"; "junk" -> "ملفات مؤقتة وسجلات"
        "apks" -> "ملفات APK"; "emptyf" -> "ملفات فاضية"; else -> "مجلدات فاضية"
    }
    val rows by produceState(emptyList<CRow>(), key, Ctl.rev, Cleaner.report, Cleaner.dups) {
        value = withContext(Dispatchers.IO) {
            val r = Cleaner.report
            when (key) {
                "dups" -> (Cleaner.dups ?: emptyList()).flatMap { g ->
                    val live = g.filter { it.exists() }
                    if (live.size < 2) emptyList<CRow>() else
                        listOf(CRow(null, "${live.size} نسخ · ${FS.fmtSize(live[0].length())} للنسخة")) + live.map { CRow(it, null) }
                }
                else -> (when (key) {
                    "large" -> r?.topFiles; "junk" -> r?.junk; "apks" -> r?.apks
                    "emptyf" -> r?.emptyFiles; else -> r?.emptyDirs
                } ?: emptyList()).filter { it.exists() }.map { CRow(it, null) }
            }
        }
    }
    val sel = remember { mutableStateListOf<String>() }
    val root = remember { Environment.getExternalStorageDirectory() }
    BackHandler { Ctl.pop() }

    val files = rows.mapNotNull { it.file }
    val selFiles = files.filter { it.path in sel }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ScreenHeader(title, "Next · ${files.size} عنصر", onBack = { Ctl.pop() })
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (key == "dups") OutlinedButton({
                    sel.clear()
                    var i = 0
                    for (row in rows) { if (row.header != null) i = 0 else { if (i > 0) sel.add(row.file!!.path); i++ } }
                }) { Text("حدد الزيادة (سيب الأقدم)") }
                else OutlinedButton({ sel.clear(); sel.addAll(files.map { it.path }) }) { Text("تحديد الكل") }
                if (sel.isNotEmpty()) OutlinedButton({ sel.clear() }) { Text("إلغاء التحديد") }
            }
            if (rows.isEmpty()) EmptyState(Ic.Check, "مفيش حاجة هنا", "التخزين نضيف في الجزء ده.")
            LazyColumn(
                Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(rows, key = { (it.file?.path ?: it.header) + "#" + System.identityHashCode(it) }) { row ->
                    if (row.header != null) {
                        Text(row.header, Modifier.padding(start = 8.dp, top = 12.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge, color = cs.primary)
                    } else {
                        val f = row.file!!
                        val picked = f.path in sel
                        val item = remember(f.path) { FS.toItem(f) }
                        val dirSize by produceState(0L, f.path) { value = if (f.isDirectory) withContext(Dispatchers.IO) { FS.sizeOf(f) } else f.length() }
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                                .background(if (picked) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                                .clickable { if (picked) sel.remove(f.path) else sel.add(f.path) }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                FileThumb(item, 48.dp, 14.dp)
                                if (picked) SelCheck(Modifier.align(Alignment.BottomEnd))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    (f.parent ?: "").removePrefix(root.path).ifEmpty { "/" } + " · " + FS.fmtSize(dirSize),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            if (sel.isNotEmpty()) {
                Button(
                    { Ctl.askDelete(selFiles, d); sel.clear() },
                    Modifier.fillMaxWidth().padding(14.dp)
                ) { Text("حذف ${selFiles.size} عنصر · ${FS.fmtSize(sumSize(selFiles))}") }
            }
        }
    }
}
