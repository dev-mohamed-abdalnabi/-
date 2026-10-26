@file:OptIn(ExperimentalFoundationApi::class)

package com.next.files

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun Glow(tertiary: Boolean = false, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf((if (tertiary) cs.tertiaryContainer else cs.primaryContainer).copy(alpha = if (tertiary) 0.68f else 0.72f), Color.Transparent),
                    center = Offset(size.width * (if (rtl) 0.12f else 0.88f), size.height * 0.04f),
                    radius = size.width * 0.95f
                )
            )
        }
    ) { content() }
}

@Composable
fun SearchField(q: String, hint: String, focus: FocusRequester? = null, onChange: (String) -> Unit) {
    TextField(
        value = q, onValueChange = onChange, singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
        shape = RoundedCornerShape(26.dp), colors = fieldColors(),
        placeholder = { Text(hint, maxLines = 1) },
        leadingIcon = { Icon(Ic.Search, null) },
        trailingIcon = { if (q.isNotEmpty()) IconButton({ onChange("") }) { Icon(Ic.Close, "مسح") } }
    )
}

@Composable
private fun CircBtn(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Icon(icon, desc, Modifier.size(22.dp))
    }
}

// ===================== الرئيسية =====================
@Composable
fun HomeScreen(bottom: Dp) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var vols by remember { mutableStateOf(emptyList<Vol>()) }
    var counts by remember { mutableStateOf(emptyMap<CatKind, Int>()) }
    var trashN by remember { mutableStateOf(0) }
    LaunchedEffect(Ctl.rev, Prefs.showHidden) {
        withContext(Dispatchers.IO) {
            vols = FS.volumes(ctx)
            counts = CatKind.values().filter { it != CatKind.RECENT && it != CatKind.LARGE }
                .associateWith { FS.catCount(ctx, it, Prefs.showHidden) }
            trashN = TrashBin.list().size
        }
    }
    Glow {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("المزيد", style = MaterialTheme.typography.headlineMedium)
                        Text("Next · ${vols.size} وحدة تخزين", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    }
                    CookieIcon(Ic.Apps, 44.dp, cs.primary, cs.onPrimary)
                }
            }
            items(vols) { v ->
                val used = (v.total - v.free).coerceAtLeast(0)
                val frac = if (v.total > 0) used.toFloat() / v.total else 0f
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerHigh)
                        .clickable { Ctl.openDir(v.dir) }.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                            Icon(Ic.Storage, null, Modifier.size(22.dp), tint = cs.onPrimaryContainer)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${FS.fmtSize(used)} من ${FS.fmtSize(v.total)}",
                                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant
                            )
                        }
                        Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = cs.primary)
                    }
                    Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(cs.secondaryContainer)) {
                        Box(Modifier.fillMaxWidth(frac.coerceIn(0.02f, 1f)).fillMaxSize().clip(CircleShape).background(cs.primary))
                    }
                }
            }
            item {
                Text("التصنيفات", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.padding(top = 10.dp))
            }
            val cats = listOf(
                Triple(CatKind.IMAGES, Ic.Image, false), Triple(CatKind.VIDEOS, Ic.Video, true),
                Triple(CatKind.AUDIO, Ic.Music, false), Triple(CatKind.DOCS, Ic.Doc, true),
                Triple(CatKind.APKS, Ic.Apps, false), Triple(CatKind.ARCHIVES, Ic.Archive, true),
                Triple(CatKind.RECENT, Ic.Clock, false), Triple(CatKind.LARGE, Ic.Chart, true)
            )
            items(cats.chunked(2)) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { (k, ic, alt) ->
                        val n = counts[k]
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerHigh)
                                .clickable { Ctl.push(Overlay.Collection(k)) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CookieIcon(
                                ic, 46.dp, if (alt) cs.tertiaryContainer else cs.primaryContainer,
                                if (alt) cs.onTertiaryContainer else cs.onPrimaryContainer, spin = false
                            )
                            Column {
                                Text(k.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                if (n != null) Text("$n", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            item {
                Text("وصول سريع", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.padding(top = 10.dp))
            }
            item { QuickRow(Ic.Download, "التنزيلات", "مجلد التحميلات") { Ctl.openDir(FS.downloads()) } }
            item {
                QuickRow(Ic.Image, "الكاميرا", "DCIM") {
                    Ctl.openDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM))
                }
            }
            item { QuickRow(Ic.StarFill, "المفضلة", "${Prefs.favorites.size} عنصر") { Ctl.push(Overlay.Favorites) } }
            item { QuickRow(Ic.Trash, "سلة المحذوفات", "$trashN عنصر") { Ctl.push(Overlay.Trash) } }
            item {
                Text("أدوات", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.padding(top = 10.dp))
            }
            item { QuickRow(Ic.Chart, "المساحة والتنضيف", "أكبر الملفات · المكررات · الفاضي · المؤقت") { Ctl.push(Overlay.Storage) } }
            item { QuickRow(Ic.Lock, "Android/data", "بيانات التطبيقات") { openAndroidDir("data") } }
            item { QuickRow(Ic.Lock, "Android/obb", "ملفات OBB للألعاب") { openAndroidDir("obb") } }
            item { QuickRow(Ic.Share, "نقل لاسلكي", "اتصفح وانقل ملفاتك من الكمبيوتر عبر الواي فاي") { Ctl.push(Overlay.Wifi) } }
            item { QuickRow(Ic.Lock, "الخزنة المشفرة", "ملفاتك الخاصة بباسورد وتشفير AES-256") { Ctl.push(Overlay.Safe) } }
        }
    }
}

private fun openAndroidDir(sub: String) {
    // المتصفح نفسه بيتعامل مع Android/data و Android/obb (عن طريق SAF لو لازم)
    Ctl.openDir(File(Environment.getExternalStorageDirectory(), "Android/$sub"))
}

@Composable
private fun QuickRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Icon(Ic.ChevL, null, tint = cs.onSurfaceVariant)
    }
}

// ===================== الملفات =====================
@Composable
fun BrowserScreen(bottom: Dp, d: DlgState) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val primary = remember { Environment.getExternalStorageDirectory() }
    val dir = Ctl.browseDir ?: primary
    WatchDir(dir)
    var vols by remember { mutableStateOf(listOf<Vol>()) }
    LaunchedEffect(Unit) { vols = withContext(Dispatchers.IO) { FS.volumes(ctx) } }
    val atRoot = dir.path == primary.path || vols.any { it.dir.path == dir.path } || dir.parentFile == null

    var items by remember { mutableStateOf(emptyList<FileItem>()) }
    var loading by remember { mutableStateOf(true) }
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FileItem>?>(null) }
    val sel = remember { mutableStateListOf<String>() }
    var sortMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) { delay(60); try { fr.requestFocus() } catch (e: Exception) { } } }
    var addMenu by remember { mutableStateOf(false) }

    val sortSel = Prefs.sortFor(dir)
    LaunchedEffect(dir.path, Ctl.rev, Prefs.showHidden, sortSel) {
        loading = true
        items = withContext(Dispatchers.IO) { FS.sort(FS.list(dir, Prefs.showHidden), sortSel.first, sortSel.second) }
        loading = false
        sel.removeAll { p -> items.none { it.file.path == p } }
    }
    LaunchedEffect(dir.path) { sel.clear(); q = ""; results = null; searching = false }
    LaunchedEffect(q, dir.path, Ctl.rev) {
        if (q.trim().length < 2) {
            results = null
        } else {
            delay(350)
            results = withContext(Dispatchers.IO) { FS.search(dir, q.trim(), Prefs.showHidden) }
        }
    }

    val shown = results ?: items
    val needSaf = remember(dir.path, Ctl.rev) { SafFs.subOf(dir)?.takeIf { Saf.treeOf(ctx, it) == null } }
    val selecting = sel.isNotEmpty()
    BackHandler(enabled = selecting || searching || q.isNotEmpty() || !atRoot) {
        when {
            selecting -> sel.clear()
            searching || q.isNotEmpty() -> { q = ""; searching = false }
            else -> dir.parentFile?.let { Ctl.browseDir = it }
        }
    }

    fun selFiles(): List<File> = sel.map { File(it) }
    fun toggle(i: FileItem) {
        val p = i.file.path
        if (p in sel) sel.remove(p) else sel.add(p)
    }
    fun click(i: FileItem) {
        if (selecting) toggle(i)
        else if (i.isDir) Ctl.browseDir = i.file
        else Ctl.open(i.file, shown.filter { !it.isDir }.map { it.file })
    }

    val denseGrid = Prefs.grid && Prefs.viewSize == 1
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                GridCells.Fixed(if (Prefs.grid) listOf(4, 4, 2)[Prefs.viewSize.coerceIn(0, 2)] else 1), Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = if (denseGrid) 8.dp else 14.dp, end = if (denseGrid) 8.dp else 14.dp,
                    bottom = bottom + (if (selecting) 64.dp else 0.dp) + (if (Ctl.clip.isNotEmpty()) 64.dp else 0.dp)),
                horizontalArrangement = Arrangement.spacedBy(if (denseGrid) 2.dp else 6.dp), verticalArrangement = Arrangement.spacedBy(if (Prefs.grid) 2.dp else 4.dp)
            ) {
                val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
                item(span = full) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 8.dp, start = 6.dp, end = 6.dp)) {
                        TabStrip()
                        if (searching) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { SearchField(q, "ابحث في ${if (atRoot) "الملفات" else dir.name}", fr) { q = it } }
                                Spacer(Modifier.size(8.dp))
                                CircBtn(Ic.Close, "إغلاق البحث") { q = ""; searching = false }
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!atRoot) IconButton({ dir.parentFile?.let { Ctl.browseDir = it } }) { Icon(Ic.Back, "للمجلد الأعلى") }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (atRoot) "الملفات" else dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                    Crumbs(dir, primary, vols)
                                }
                                Box {
                                    CookieIcon(Ic.Menu, 44.dp, cs.primary, cs.onPrimary, onClick = { addMenu = true })
                                    DropdownMenu(sortMenu, { sortMenu = false }) {
                                        listOf("الاسم", "الحجم", "التاريخ", "النوع").forEachIndexed { i, n ->
                                            DropdownMenuItem(
                                                { Text("ترتيب: $n") },
                                                { Prefs.setSort(dir, i, Prefs.sortFor(dir).second); sortMenu = false },
                                                leadingIcon = {
                                                    if (Prefs.sortFor(dir).first == i) Icon(Ic.Check, null, Modifier.size(20.dp), tint = cs.primary)
                                                    else Spacer(Modifier.size(20.dp))
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            { Text(if (Prefs.sortFor(dir).second) "تصاعدي" else "تنازلي") },
                                            { val c = Prefs.sortFor(dir); Prefs.setSort(dir, c.first, !c.second); sortMenu = false },
                                            leadingIcon = { Icon(Ic.Up, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text("الترتيب ده لكل المجلدات") },
                                            { val c = Prefs.sortFor(dir); Prefs.sortAllLike(c.first, c.second); sortMenu = false }
                                        )
                                        if (Prefs.folderSort.containsKey(dir.path)) DropdownMenuItem(
                                            { Text("رجوع للترتيب العام") },
                                            { Prefs.clearSort(dir); sortMenu = false }
                                        )
                                    }
                                    DropdownMenu(addMenu, { addMenu = false }) {
                                        DropdownMenuItem(
                                            { Text("بحث") }, { addMenu = false; searching = true },
                                            leadingIcon = { Icon(Ic.Search, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text("مجلد جديد") }, { addMenu = false; d.mkdir = dir },
                                            leadingIcon = { Icon(Ic.NewFolder, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text("ملف جديد") }, { addMenu = false; d.newFile = dir },
                                            leadingIcon = { Icon(Ic.NewFile, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text("تاب جديد") }, { addMenu = false; Ctl.newTab(dir) },
                                            leadingIcon = { Icon(Ic.Plus, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text("المساحة والتنضيف") }, { addMenu = false; Ctl.push(Overlay.Storage) },
                                            leadingIcon = { Icon(Ic.Chart, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text("ترتيب") }, { addMenu = false; sortMenu = true },
                                            leadingIcon = { Icon(Ic.Sort, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text(if (Prefs.grid) "عرض قائمة" else "عرض شبكة") },
                                            { addMenu = false; Prefs.grid = !Prefs.grid; Prefs.save() },
                                            leadingIcon = { Icon(Ic.Grid, null, Modifier.size(20.dp)) }
                                        )
                                        DropdownMenuItem(
                                            { Text(if (Prefs.showHidden) "إخفاء الملفات المخفية" else "إظهار الملفات المخفية") },
                                            { addMenu = false; Prefs.showHidden = !Prefs.showHidden; Prefs.save() },
                                            leadingIcon = { Icon(Ic.Eye, null, Modifier.size(20.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (!loading && shown.isEmpty()) item(span = full) {
                    if (q.trim().length >= 2) EmptyState(Ic.Search, "مفيش نتائج", "جرّب كلمة تانية.")
                    else if (needSaf != null)
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState(
                                Ic.Lock, "محتاج صلاحية المجلد ده",
                                if (android.os.Build.VERSION.SDK_INT >= 33)
                                    "أندرويد 13 وأحدث بيمنع الوصول للمجلد ده بالطريقة العادية. جرّب المنح، ولو النظام رفض يبقى محتاج Shizuku أو root."
                                else "دوس منح الوصول، وبعدين \"استخدام هذا المجلد\" وإنت في Android/$needSaf."
                            )
                            androidx.compose.material3.Button({ Ctl.pickSaf(needSaf) }) { Text("منح الوصول") }
                        }
                    else EmptyState(Ic.Folder, "المجلد فاضي", "مفيش ملفات هنا لسه.")
                }
                if (Prefs.grid) {
                    items(shown, key = { it.file.path }) { i ->
                        FileCard(i, i.file.path in sel, { click(i) }, { if (!selecting) toggle(i) }, dense = denseGrid)
                    }
                } else {
                    items(shown, key = { it.file.path }, span = { GridItemSpan(maxLineSpan) }) { i ->
                        FileRow(
                            i, i.file.path in sel,
                            subtitle = if (results != null) (i.file.parent ?: "") .removePrefix(primary.path) .ifEmpty { "/" } + " · " + metaOf(i) else null,
                            onClick = { click(i) }, onLong = { if (!selecting) toggle(i) }
                        ) {
                            if (!selecting) MoreButton(i, d, showInFolder = results != null, siblings = shown.filter { !it.isDir }.map { it.file })
                        }
                    }
                }
            }
            if (selecting || Ctl.clip.isNotEmpty()) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = (bottom - 28.dp).coerceAtLeast(0.dp))) {
                    if (Ctl.clip.isNotEmpty()) Surface(
                        Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp), color = cs.tertiaryContainer, shadowElevation = 6.dp
                    ) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${Ctl.clip.size} عنصر ${if (Ctl.clipCut) "للنقل" else "للنسخ"}",
                                Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer
                            )
                            TextButton({ Ctl.paste(dir) }) { Icon(Ic.Paste, null, Modifier.size(20.dp)); Spacer(Modifier.size(6.dp)); Text("لصق هنا") }
                            IconButton({ Ctl.clearClip() }) { Icon(Ic.Close, "إلغاء", tint = cs.onTertiaryContainer) }
                        }
                    }
                    if (selecting) SelectionBar(
                    sel.size, onAll = { sel.clear(); sel.addAll(shown.map { it.file.path }) },
                    onCopy = { Ctl.setClip(selFiles(), false); sel.clear() },
                    onCut = { Ctl.setClip(selFiles(), true); sel.clear() },
                    onShare = { Ctl.shareFiles(selFiles()) },
                    onZip = { d.zip = selFiles(); sel.clear() },
                    onDelete = { Ctl.askDelete(selFiles(), d); sel.clear() },
                    onClose = { sel.clear() },
                    onRename = { d.batch = selFiles(); sel.clear() },
                    onInvert = {
                        val cur = sel.toSet()
                        val inv = shown.map { it.file.path }.filter { it !in cur }
                        sel.clear(); sel.addAll(inv)
                    },
                    onFav = {
                        selFiles().forEach { if (it.path !in Prefs.favorites) Prefs.favorites.add(it.path) }
                        Prefs.save(); sel.clear(); Ctl.toast("اتضافت للمفضلة")
                    },
                    onTab = { selFiles().firstOrNull { it.isDirectory }?.let { Ctl.newTab(it) }; sel.clear() },
                    modifier = Modifier
                )
                }
            }
        }
    }
}

// ===================== تصنيفات / أحدث =====================
@Composable
fun CollectionScreen(kind: CatKind, d: DlgState) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var files by remember { mutableStateOf(emptyList<FileItem>()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(kind, Ctl.rev, Prefs.showHidden) {
        loading = true
        files = withContext(Dispatchers.IO) { FS.catFiles(ctx, kind, Prefs.showHidden).map { FS.toItem(it) } }
        loading = false
    }
    val allFiles = remember(files) { files.map { it.file } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader(kind.title, "Next · ${files.size} عنصر", onBack = { Ctl.pop() })
            if (!loading && files.isEmpty()) {
                EmptyState(iconForCat(kind), "مفيش ${kind.title}", "مفيش ملفات من النوع ده.")
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 40.dp)
            ) {
                items(files, key = { it.file.path }) { i ->
                    FileRow(i, false, subtitle = FS.fmtSize(i.size) + " · " + FS.fmtDate(i.modified), onClick = {
                        Ctl.open(i.file, allFiles)
                    }, onLong = {}) {
                        MoreButton(i, d, showInFolder = true, siblings = allFiles)
                    }
                }
            }
        }
    }
}

fun iconForCat(k: CatKind): ImageVector = when (k) {
    CatKind.IMAGES -> Ic.Image
    CatKind.VIDEOS -> Ic.Video
    CatKind.AUDIO -> Ic.Music
    CatKind.DOCS -> Ic.Doc
    CatKind.APKS -> Ic.Apps
    CatKind.ARCHIVES -> Ic.Archive
    CatKind.RECENT -> Ic.Clock
    CatKind.LARGE -> Ic.Chart
}

// ===================== المفضلة =====================
@Composable
fun FavoritesScreen(d: DlgState) {
    val cs = MaterialTheme.colorScheme
    val list = Prefs.favorites.map { File(it) }.filter { it.exists() }.map { FS.toItem(it) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("المفضلة", "Next · ${list.size} عنصر", onBack = { Ctl.pop() })
            if (list.isEmpty()) EmptyState(Ic.Star, "مفيش مفضلة", "ضيف ملفات أو مجلدات من قائمة المزيد.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 40.dp)) {
                items(list, key = { it.file.path }) { i ->
                    FileRow(i, false, onClick = {
                        if (i.isDir) Ctl.openDir(i.file) else Ctl.open(i.file, list.filter { !it.isDir }.map { it.file })
                    }, onLong = {}) { MoreButton(i, d, showInFolder = !i.isDir) }
                }
            }
        }
    }
}

// ===================== السلة =====================
@Composable
fun TrashScreen() {
    val cs = MaterialTheme.colorScheme
    var list by remember { mutableStateOf(emptyList<TrashEntry>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(Ctl.rev) { list = withContext(Dispatchers.IO) { TrashBin.list() } }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            ScreenHeader("سلة المحذوفات", "Next · ${list.size} عنصر", onBack = { Ctl.pop() }) {
                if (list.isNotEmpty()) TextButton({ confirmEmpty = true }) { Text("تفريغ", color = cs.error) }
            }
            if (list.isEmpty()) EmptyState(Ic.Trash, "السلة فاضية", "الحاجات اللي بتحذفها بتظهر هنا.")
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 40.dp)) {
                items(list, key = { it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (e.isDir) Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { FolderArt(Modifier.size(48.dp)) } else Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (e.isDir) cs.primaryContainer else cs.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (e.isDir) Ic.Folder else iconFor(FS.kindOf(File(e.name).extension.lowercase())), null,
                                Modifier.size(26.dp), tint = if (e.isDir) cs.onPrimaryContainer else cs.onSecondaryContainer
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                            Text(
                                FS.fmtSize(e.size) + " · " + FS.fmtDate(e.date), maxLines = 1,
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
                            )
                        }
                        IconButton({ Ctl.restore(e) }) { Icon(Ic.Restore, "استرجاع", tint = cs.primary) }
                        IconButton({ Ctl.purge(e) }) { Icon(Ic.Trash, "حذف نهائي", tint = cs.error) }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("تفرّغ السلة؟", style = MaterialTheme.typography.titleLarge) },
            text = { Text("كل الملفات هتتحذف نهائيًا.") },
            confirmButton = { TextButton({ confirmEmpty = false; Ctl.emptyTrash() }) { Text("تفريغ", color = cs.error) } },
            dismissButton = { TextButton({ confirmEmpty = false }) { Text("إلغاء") } }
        )
    }
}
