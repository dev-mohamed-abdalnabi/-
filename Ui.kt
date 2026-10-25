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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var batch by mutableStateOf<List<File>?>(null)
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
        listOf(NavTab("الملفات", Ic.Folder), NavTab("المزيد", Ic.Apps), NavTab("الإعدادات", Ic.Settings))
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

private fun Path.q(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) =
    cubicTo(x0 + 2f / 3f * (cx - x0), y0 + 2f / 3f * (cy - y0), x1 + 2f / 3f * (cx - x1), y1 + 2f / 3f * (cy - y1), x1, y1)

@Composable
fun FolderArt(modifier: Modifier = Modifier, badge: ImageVector? = null, sheets: List<Color> = emptyList()) {
    val cs = MaterialTheme.colorScheme
    val back = cs.primary
    val front = lerp(cs.primary, Color.White, 0.55f).copy(alpha = 0.92f)
    BoxWithConstraints(modifier) {
    Canvas(Modifier.fillMaxSize()) {
        val k = size.minDimension / 100f
        val b = Path().apply {
            moveTo(8 * k, 22 * k); q(8 * k, 22 * k, 8 * k, 14 * k, 16 * k, 14 * k)
            lineTo(38 * k, 14 * k); q(38 * k, 14 * k, 42 * k, 14 * k, 45 * k, 18 * k)
            lineTo(48 * k, 22 * k); lineTo(84 * k, 22 * k); q(84 * k, 22 * k, 92 * k, 22 * k, 92 * k, 30 * k)
            lineTo(92 * k, 78 * k); q(92 * k, 78 * k, 92 * k, 86 * k, 84 * k, 86 * k)
            lineTo(16 * k, 86 * k); q(16 * k, 86 * k, 8 * k, 86 * k, 8 * k, 78 * k); close()
        }
        val f = Path().apply {
            moveTo(12 * k, 54 * k); q(12 * k, 54 * k, 12 * k, 48 * k, 18 * k, 47.5f * k)
            lineTo(88 * k, 42 * k); q(88 * k, 42 * k, 95 * k, 41.5f * k, 94.5f * k, 48 * k)
            lineTo(89 * k, 80 * k); q(89 * k, 80 * k, 88 * k, 87 * k, 81 * k, 87 * k)
            lineTo(18 * k, 87 * k); q(18 * k, 87 * k, 11 * k, 87 * k, 12 * k, 80 * k); close()
        }
        drawPath(b, back)
        sheets.take(3).forEachIndexed { i, c ->
            drawRoundRect(
                c.copy(alpha = 0.95f),
                topLeft = androidx.compose.ui.geometry.Offset((18 + i * 24) * k, (26 - (if (i == 1) 4 else 0)) * k),
                size = androidx.compose.ui.geometry.Size(20 * k, 30 * k),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4 * k, 4 * k)
            )
        }
        drawPath(f, front)
    }
    if (badge != null) Icon(badge, null, Modifier.size(maxWidth * 0.3f).align(Alignment.Center).offset(y = maxHeight * 0.2f), tint = cs.primary)
    }
}

@Composable
fun FileThumb(item: FileItem, size: Dp, radius: Dp, modifier: Modifier = Modifier, plain: Boolean = false) {
    if (item.isDir) Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val pk = rememberPeek(item)
        FolderArt(Modifier.size(size * 0.84f), if (pk == null || pk.kinds.isEmpty()) folderBadge(item.name) else null,
            pk?.kinds?.map { kindColor(it) } ?: emptyList())
    } else FileThumbTile(item, size, radius, modifier, plain)
}

@Composable
fun rememberPeek(item: FileItem): FS.FolderPeek? {
    val p by produceState<FS.FolderPeek?>(FS.peekCached(item.file), item.file.path, item.modified, Prefs.showHidden) {
        value = FS.peekCached(item.file) ?: withContext(Dispatchers.IO) { FS.peek(item.file, Prefs.showHidden) }
    }
    return p
}

@Composable
fun folderSubtitle(item: FileItem, short: Boolean = false): String {
    val p = rememberPeek(item) ?: return if (short) "" else FS.fmtDate(item.modified)
    if (p.count == 0) return if (short) "فاضي" else "فاضي · " + FS.fmtDate(item.modified)
    val names = p.names.joinToString("، ")
    val more = if (p.count > p.names.size) " +" + (p.count - p.names.size) else ""
    return if (short) "${p.count} عنصر" else "${p.count} عنصر · $names$more"
}

fun kindColor(k: Kind): Color = when (k) {
    Kind.FOLDER -> Color(0xFF5B8DEF)
    Kind.AUDIO -> Color(0xFF8E5CF7)
    Kind.VIDEO -> Color(0xFFE5484D)
    Kind.IMAGE -> Color(0xFF12A594)
    Kind.APK -> Color(0xFF2BB673)
    Kind.ARCHIVE -> Color(0xFFE0A100)
    Kind.TEXT -> Color(0xFF14A3B8)
    Kind.DOC -> Color(0xFF2F6BFF)
    else -> Color(0xFF8A8F98)
}

fun folderBadge(name: String): ImageVector? = when (name.lowercase()) {
    "download", "downloads" -> Ic.Download
    "dcim", "pictures", "camera", "screenshots" -> Ic.Image
    "music", "audio", "recordings" -> Ic.Music
    "movies", "video", "videos" -> Ic.Video
    "documents", "books" -> Ic.Doc
    "android" -> Ic.Apps
    else -> null
}

fun typeColor(item: FileItem): Color = when (item.kind) {
    Kind.AUDIO -> Color(0xFF8E5CF7)
    Kind.VIDEO -> Color(0xFFE5484D)
    Kind.IMAGE -> Color(0xFF12A594)
    Kind.APK -> Color(0xFF2BB673)
    Kind.ARCHIVE -> Color(0xFFE0A100)
    Kind.TEXT -> Color(0xFF14A3B8)
    Kind.DOC -> when (item.ext) {
        "pdf" -> Color(0xFFE5484D)
        "xls", "xlsx", "ods", "csv" -> Color(0xFF1F9D55)
        "ppt", "pptx", "odp" -> Color(0xFFF08A24)
        else -> Color(0xFF2F6BFF)
    }
    else -> Color(0xFF8A8F98)
}

fun hasReal(i: FileItem): Boolean =
    i.kind == Kind.IMAGE || i.kind == Kind.VIDEO || i.kind == Kind.APK || i.kind == Kind.AUDIO ||
        (i.kind == Kind.DOC && i.ext == "pdf")

@Composable
fun TypeTile(item: FileItem, size: Dp, radius: Dp, modifier: Modifier = Modifier, plain: Boolean = false) {
    val c = typeColor(item)
    val label = size >= 48.dp && item.ext.isNotEmpty()
    Box(
        if (plain) modifier.size(size) else modifier.size(size).clip(RoundedCornerShape(radius)).background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            iconFor(item.kind), null,
            Modifier.size(size * (if (plain) 0.58f else 0.44f)).offset(y = if (label) -(size * (if (plain) 0.137f else 0.07f)) else 0.dp), tint = c
        )
        if (label) {
            Text(
                item.ext.uppercase().take(4), Modifier.align(Alignment.BottomCenter).padding(bottom = size * (if (plain) 0.07f else 0.07f)),
                color = c, maxLines = 1,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = (size.value * (if (plain) 0.18f else 0.15f)).coerceAtLeast(8f).sp)
            )
        }
    }
}

@Composable
private fun FileThumbTile(item: FileItem, size: Dp, radius: Dp, modifier: Modifier = Modifier, plain: Boolean = false) {
    val ctx = LocalContext.current
    val real = hasReal(item)
    val px = if (size > 90.dp) 384 else 256
    val bmp by produceState<android.graphics.Bitmap?>(null, item.file.path, item.modified, px) {
        value = if (real) withContext(Dispatchers.IO) { Thumbs.load(ctx, item.file, item.kind, px) } else null
    }
    val b = bmp
    if (b != null) {
        val apk = item.kind == Kind.APK
        Box(
            modifier.size(size).clip(RoundedCornerShape(radius))
                .background(if (plain) Color.Transparent else if (apk) typeColor(item).copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.06f))
        ) {
            Image(
                b.asImageBitmap(), null,
                Modifier.fillMaxSize().then(if (apk && !plain) Modifier.padding(size * 0.14f) else Modifier),
                contentScale = if (apk) ContentScale.Fit else ContentScale.Crop,
                alignment = if (item.kind == Kind.DOC) Alignment.TopCenter else Alignment.Center
            )
        }
    } else {
        TypeTile(item, size, radius, modifier, plain)
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
    if (i.isDir) FS.fmtDate(i.modified) else "${FS.fmtSize(i.size)} · ${FS.fmtDate(i.modified)}"

@Composable
fun FileRow(
    item: FileItem, selected: Boolean, subtitle: String? = null,
    onClick: () -> Unit, onLong: () -> Unit, trailing: @Composable () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val fav = item.file.path in Prefs.favorites
    val ts = listOf(44, 56, 68)[Prefs.viewSize.coerceIn(0, 2)].dp
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (selected) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box {
            FileThumb(item, ts, ts * 0.28f)
            if (selected) SelCheck(Modifier.align(Alignment.BottomEnd))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) cs.primary else cs.onSurface
            )
            Text(
                subtitle ?: (if (item.isDir) folderSubtitle(item) else metaOf(item)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant
            )
        }
        if (fav) Icon(Ic.StarFill, null, Modifier.size(16.dp), tint = cs.primary)
        trailing()
    }
}

@Composable
fun FileCard(item: FileItem, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit, dense: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(if (selected) cs.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(if (dense) 2.dp else 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val s = maxWidth * (if (dense) 0.85f else 0.7f)
                val h = s * 0.8f
                Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
                    if (item.isDir) {
                        val pk = rememberPeek(item)
                        FolderArt(Modifier.align(Alignment.TopCenter).requiredSize(s).offset(y = -(s * 0.10f)),
                            if (pk == null || pk.kinds.isEmpty()) folderBadge(item.name) else null,
                            pk?.kinds?.map { kindColor(it) } ?: emptyList())
                    } else FileThumb(item, h, h * 0.2f, plain = true)
                }
            }
            if (selected) SelCheck(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            item.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) cs.primary else cs.onSurface
        )
        if (item.isDir) Text(
            folderSubtitle(item, short = true), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant
        )
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
        if (item.isDir) mi("فتح في تاب جديد", Ic.Plus) { Ctl.newTab(f) }
        if (!item.isDir && Arc.supported(f)) {
            mi("استعراض المحتوى", Ic.Eye) { Ctl.push(Overlay.Zip(f.path)) }
            mi("فك الضغط هنا", Ic.Download) { Ctl.unzip(f) }
        }
        mi(if (fav) "إزالة من المفضلة" else "إضافة للمفضلة", if (fav) Ic.Star else Ic.StarFill) { Prefs.toggleFav(f.path) }
        mi("التفاصيل", Ic.Info) { d.info = f }
        mi("حذف", Ic.Trash, true) { Ctl.askDelete(listOf(f), d) }
    }
}

@Composable
fun SelectionBar(count: Int, onAll: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onShare: () -> Unit,
                 onZip: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
                 onRename: () -> Unit, onInvert: () -> Unit, onFav: () -> Unit, onTab: () -> Unit,
                 modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var more by remember { mutableStateOf(false) }
    Surface(
        modifier.padding(horizontal = 12.dp).padding(top = 8.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, shadowElevation = 6.dp
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count محدد", Modifier.weight(1f).clickable { onClose() }.padding(start = 10.dp),
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
            Box {
                ib(Ic.More, "المزيد") { more = true }
                DropdownMenu(more, { more = false }) {
                    @Composable
                    fun mi(t: String, ic: ImageVector, a: () -> Unit) = DropdownMenuItem(
                        { Text(t) }, { more = false; a() }, leadingIcon = { Icon(ic, null, Modifier.size(20.dp)) }
                    )
                    mi("تحديد عكسي", Ic.Sort, onInvert)
                    mi("إعادة تسمية جماعية", Ic.Edit, onRename)
                    mi("إضافة للمفضلة", Ic.StarFill, onFav)
                    mi("فتح المجلد في تاب جديد", Ic.Folder, onTab)
                    mi("إلغاء التحديد", Ic.Close, onClose)
                }
            }
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
private fun ZipDialog(initial: String, onDismiss: () -> Unit, onOk: (String, String, Int) -> Unit) {
    var fmt by remember { mutableStateOf(0) }
    var n by remember { mutableStateOf(initial) }
    var p by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("ضغط", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Switcher(listOf("ZIP", "7z", "TAR.GZ"), fmt) { fmt = it }
                TextField(n, { n = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(), modifier = Modifier.fillMaxWidth())
                if (fmt == 0) TextField(
                    p, { p = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    placeholder = { Text("باسورد (اختياري) - تشفير AES-256") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton({ onOk(n, p, fmt); onDismiss() }) { Text("ضغط") } },
        dismissButton = { TextButton(onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun BatchDialog(count: Int, onDismiss: () -> Unit, onOk: (String, Int, Boolean) -> Unit) {
    var pat by remember { mutableStateOf("ملف_###") }
    var start by remember { mutableStateOf("1") }
    var keep by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss, shape = RoundedCornerShape(28.dp),
        title = { Text("إعادة تسمية $count عنصر", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(pat, { pat = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    label = { Text("النمط (كل # رقم)") }, modifier = Modifier.fillMaxWidth())
                TextField(start, { start = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    label = { Text("يبدأ من") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().clickable { keep = !keep }, verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(keep, { keep = it })
                    Text("سيب الامتداد زي ما هو")
                }
            }
        },
        confirmButton = { TextButton({ onOk(pat, start.toIntOrNull() ?: 1, keep); onDismiss() }) { Text("تغيير") } },
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
        ZipDialog(base, { d.zip = null }) { n, p, f -> Ctl.zip(files, n, p, f) }
    }
    d.batch?.let { files -> BatchDialog(files.size, { d.batch = null }) { pat, start, keep -> Ctl.batchRename(files, pat, start, keep) } }
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
                    if (f.isFile) {
                        var calc by remember(f.path) { mutableStateOf(false) }
                        val sums by produceState<Pair<String, String>?>(null, f.path, calc) {
                            if (calc) value = withContext(Dispatchers.IO) {
                                val m5 = java.security.MessageDigest.getInstance("MD5")
                                val s2 = java.security.MessageDigest.getInstance("SHA-256")
                                f.inputStream().use { i ->
                                    val b = ByteArray(64 * 1024)
                                    while (true) { val r = i.read(b); if (r < 0) break; m5.update(b, 0, r); s2.update(b, 0, r) }
                                }
                                fun hex(x: ByteArray) = x.joinToString("") { "%02x".format(it) }
                                hex(m5.digest()) to hex(s2.digest())
                            }
                        }
                        val sm = sums
                        if (sm != null) { line("MD5", sm.first); line("SHA-256", sm.second) }
                        else TextButton({ calc = true }) { Text(if (calc) "جاري الحساب…" else "احسب MD5 / SHA-256") }
                    }
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
            Column(Modifier.width(280.dp).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (Prog.frac < 0f) androidx.compose.material3.CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        if (Prog.frac >= 0f) "$m ${(Prog.frac * 100).toInt()}%" else m,
                        Modifier.weight(1f), style = MaterialTheme.typography.titleSmall
                    )
                }
                if (Prog.frac >= 0f) androidx.compose.material3.LinearProgressIndicator(progress = { Prog.frac }, modifier = Modifier.fillMaxWidth())
                if (Prog.cancellable) TextButton({ Prog.cancel = true }, Modifier.align(Alignment.End)) { Text("إلغاء", color = cs.error) }
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
