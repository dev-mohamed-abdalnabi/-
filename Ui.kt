@file:OptIn(ExperimentalFoundationApi::class)

package com.next.files

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DlgState {
    var del by mutableStateOf<List<File>?>(null)
    var rename by mutableStateOf<File?>(null)
    var mkdir by mutableStateOf<File?>(null)
    var newFile by mutableStateOf<File?>(null)
    var zip by mutableStateOf<List<File>?>(null)
    var info by mutableStateOf<File?>(null)
}

fun iconFor(k: Kind): ImageVector = when (k) {
    Kind.FOLDER -> Ic.Folder
    Kind.IMAGE -> Ic.Image
    Kind.VIDEO -> Ic.Video
    Kind.AUDIO -> Ic.Music
    Kind.DOC -> Ic.Doc
    Kind.TEXT -> Ic.Code
    Kind.APK -> Ic.Apps
    Kind.ARCHIVE -> Ic.Archive
    Kind.OTHER -> Ic.File
}

private class NavTab(val label: String, val icon: ImageVector)

@Composable
fun FloatingNav(sel: Int, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hap = LocalHapticFeedback.current
    val items = remember {
        listOf(NavTab("الرئيسية", Ic.Home), NavTab("الملفات", Ic.Folder), NavTab("الإعدادات", Ic.Settings))
    }
    Surface(shape = CircleShape, color = cs.surfaceContainerHigh, shadowElevation = 10.dp) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            items.forEachIndexed { i, t ->
                val a = i == sel
                val bg by animateColorAsState(if (a) cs.primaryContainer else Color.Transparent, label = "nb")
                Row(
                    Modifier.height(54.dp).clip(CircleShape).background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            hap.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            on(i)
                        }
                        .padding(horizontal = 20.dp).animateContentSize(spring(0.8f, 400f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(t.icon, t.label, tint = if (a) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    AnimatedVisibility(a, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                        Text(
                            t.label, Modifier.padding(start = 10.dp), color = cs.onPrimaryContainer,
                            style = MaterialTheme.typography.titleSmall, maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileThumb(item: FileItem, size: Dp, radius: Dp, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val wantsBmp = item.kind == Kind.IMAGE || item.kind == Kind.VIDEO || item.kind == Kind.APK
    val bmp by produceState<android.graphics.Bitmap?>(null, item.file.path, item.modified) {
        value = if (wantsBmp) withContext(Dispatchers.IO) { Thumbs.load(ctx, item.file, item.kind, 256) } else null
    }
    val (bg, fg) = if (item.isDir) cs.primaryContainer to cs.onPrimaryContainer
    else cs.secondaryContainer to cs.onSecondaryContainer
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(bg),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = if (item.kind == Kind.APK) ContentScale.Fit else ContentScale.Crop
            )
        } else {
            Icon(iconFor(item.kind), null, Modifier.size(size * 0.5f), tint = fg)
        }
    }
}

@Composable
fun SelCheck(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.size(24.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
        Icon(Ic.Check, null, Modifier.size(16.dp), tint = cs.onPrimary)
    }
}

fun metaOf(i: FileItem): String =
    if (i.isDir) "${i.count} عنصر · ${FS.fmtDate(i.modified)}" else "${FS.fmtSize(i.size)} · ${FS.fmtDate(i.modified)}"

@Composable
fun FileRow(
    item: FileItem, selected: Boolean, subtitle: String? = null,
    onClick: () -> Unit, onLong: () -> Unit, trailing: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fav = item.file.path in Prefs.favorites
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (selected) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box {
            FileThumb(item, 56.dp, 16.dp)
            if (selected) SelCheck(Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) cs.primary else cs.onSurface
            )
            Text(
                subtitle ?: metaOf(item), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        if (fav) Icon(Ic.StarFill, null, Modifier.size(16.dp), tint = cs.primary)
        trailing()
    }
}

@Composable
fun FileCard(item: FileItem, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (selected) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))) {
                BoxFill(item)
            }
            if (selected) SelCheck(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.primary else cs.onSurface
        )
    }
}

@Composable
private fun BoxFill(item: FileItem) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val wants = item.kind == Kind.IMAGE || item.kind == Kind.VIDEO || item.kind == Kind.APK
    val bmp by produceState<android.graphics.Bitmap?>(null, item.file.path, item.modified) {
        value = if (wants) withContext(Dispatchers.IO) { Thumbs.load(ctx, item.file, item.kind, 384) } else null
    }
    val bg = if (item.isDir) cs.primaryContainer else cs.secondaryContainer
    val fg = if (item.isDir) cs.onPrimaryContainer else cs.onSecondaryContainer
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(), null, Modifier.fillMaxSize(),
                contentScale = if (item.kind == Kind.APK) ContentScale.Fit else ContentScale.Crop
            )
        } else {
            Icon(iconFor(item.kind), null, Modifier.size(44.dp), tint = fg)
        }
    }
}

@Composable
fun MoreButton(item: FileItem, d: DlgState, showInFolder: Boolean = false, siblings: List<File> = emptyList()) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true }, Modifier.size(36.dp)) {
            Icon(Ic.More, "المزيد", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ItemMenu(open, { open = false }, item, d, showInFolder, siblings)
    }
}

@Composable
fun ItemMenu(open: Boolean, dismiss: () -> Unit, item: FileItem, d: DlgState, showInFolder: Boolean, siblings: List<File>) {
    val ctx = LocalContext.current
    val f = item.file
    val fav = f.path in Prefs.favorites
    DropdownMenu(open, dismiss) {
        @Composable
        fun mi(t: String, ic: ImageVector, err: Boolean = false, a: () -> Unit) {
            val c = if (err) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Text(t, color = c) },
                leadingIcon = { Icon(ic, null, Modifier.size(20.dp), tint = c) },
                onClick = { dismiss(); a() }
            )
        }
        if (!item.isDir) {
            mi("فتح", Ic.Eye) { Ctl.open(f, siblings) }
            mi("فتح بواسطة", Ic.File) { FS.openFile(ctx, f, true) }
            mi("مشاركة", Ic.Share) { FS.share(ctx, listOf(f)) }
        }
        if (showInFolder) mi("عرض في المجلد", Ic.Folder) { f.parentFile?.let { Ctl.openDir(it) } }
        mi("نسخ", Ic.Copy) { Ctl.setClip(listOf(f), false) }
        mi("قص", Ic.Cut) { Ctl.setClip(listOf(f), true) }
        mi("إعادة تسمية", Ic.Edit) { d.rename = f }
        mi("ضغط", Ic.Archive) { d.zip = listOf(f) }
        if (item.ext == "zip") mi("فك الضغط", Ic.Download) { Ctl.unzip(f) }
        mi(if (fav) "إزالة من المفضلة" else "إضافة للمفضلة", if (fav) Ic.Star else Ic.StarFill) { Prefs.toggleFav(f.path) }
        mi("التفاصيل", Ic.Info) { d.info = f }
        mi("حذف", Ic.Trash, true) { Ctl.askDelete(listOf(f), d) }
    }
}

@Composable
fun SelectionBar(count: Int, onAll: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onShare: () -> Unit,
                 onZip: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier.padding(horizontal = 12.dp).padding(top = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, shadowElevation = 6.dp
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count محدد", Modifier.weight(1f).padding(start = 10.dp),
                style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer
            )
            TextButton(onAll) { Text("الكل") }
            @Composable
            fun ib(ic: ImageVector, t: String, c: Color = cs.onSecondaryContainer, a: () -> Unit) =
                IconButton(a, Modifier.size(40.dp)) { Icon(ic, t, Modifier.size(22.dp), tint = c) }
            ib(Ic.Copy, "نسخ", a = onCopy)
            ib(Ic.Cut, "قص", a = onCut)
            ib(Ic.Share, "مشاركة", a = onShare)
            ib(Ic.Archive, "ضغط", a = onZip)
            ib(Ic.Trash, "حذف", cs.error, onDelete)
            ib(Ic.Close, "إغلاق", a = onClose)
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            TextField(
                t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp),
                colors = fieldColors(), modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton({ onOk(t); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun Dialogs(d: DlgState) {
    d.rename?.let { f ->
        NameDialog("إعادة تسمية", f.name, "تغيير", { d.rename = null }) { Ctl.rename(f, it) }
    }
    d.mkdir?.let { dir ->
        NameDialog("مجلد جديد", "", "إنشاء", { d.mkdir = null }) { Ctl.mkdir(dir, it) }
    }
    d.newFile?.let { dir ->
        NameDialog("ملف جديد", "ملف.txt", "إنشاء", { d.newFile = null }) { Ctl.newFile(dir, it) }
    }
    d.zip?.let { files ->
        val base = if (files.size == 1) files[0].nameWithoutExtension else "أرشيف"
        NameDialog("ضغط إلى ZIP", base, "ضغط", { d.zip = null }) { Ctl.zip(files, it) }
    }
    d.del?.let { files ->
        AlertDialog(
            onDismissRequest = { d.del = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text("تحذف ${files.size} عنصر؟", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    if (Prefs.useTrash) "هتتنقل لسلة المحذوفات وتقدر ترجعها." else "هتتحذف نهائيًا ومش هتقدر ترجعها.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton({ Ctl.delete(files); d.del = null }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ d.del = null }) { Text("إلغاء") } }
        )
    }
    d.info?.let { f ->
        val size by produceState(-1L, f.path) { value = withContext(Dispatchers.IO) { FS.sizeOf(f) } }
        AlertDialog(
            onDismissRequest = { d.info = null },
            shape = RoundedCornerShape(28.dp),
            title = { Text(f.name, style = MaterialTheme.typography.titleLarge, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val cs = MaterialTheme.colorScheme
                    @Composable
                    fun line(k: String, v: String) {
                        Column {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    line("المسار", f.parent ?: "/")
                    line("الحجم", if (size < 0) "جاري الحساب…" else FS.fmtSize(size))
                    line("آخر تعديل", FS.fmtDate(f.lastModified()))
                    line("النوع", if (f.isDirectory) "مجلد" else FS.mimeOf(f))
                }
            },
            confirmButton = { TextButton({ d.info = null }) { Text("تمام") } }
        )
    }
}

@Composable
fun BusyOverlay() {
    val m = Ctl.busy ?: return
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center
    ) {
        Surface(shape = RoundedCornerShape(28.dp), color = cs.surfaceContainerHigh, shadowElevation = 8.dp) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(m, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String?, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onBack, Modifier.padding(end = 6.dp)) { Icon(Ic.Back, "رجوع") }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}
