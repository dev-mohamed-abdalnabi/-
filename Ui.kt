@file:OptIn(ExperimentalFoundationApi::class)

package com.next.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
fun FloatingNav(sel: Int, onFilesDouble: () -> Unit = {}, on: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val hap = LocalHapticFeedback.current
    val lastFilesTap = remember { longArrayOf(0L) }
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
                            val now = android.os.SystemClock.uptimeMillis()
                            if (i == 0 && now - lastFilesTap[0] < 400L) {
                                // ضغطتين سريعتين على "الملفات" = ارجع للرئيسية
                                lastFilesTap[0] = 0L
                                on(0)
                                onFilesDouble()
                            } else {
                                lastFilesTap[0] = if (i == 0) now else 0L
                                on(i)
                            }
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
fun FileThumb(item: FileItem, size: Dp, radius: Dp, modifier: Modifier = Modifier, plain: Boolean = false) {
    if (item.isDir) Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val pk = rememberPeek(item)
        FolderArt(Modifier.size(size * 0.84f), folderBadge(item.name), pk)
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
    FileTypeArt(item.ext, item.kind, modifier.size(size), tag = true)
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
                            folderBadge(item.name), pk)
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
            mi("فتح بواسطة", Ic.File) { Ctl.openWith(f) }
            mi("مشاركة", Ic.Share) { Ctl.shareFiles(listOf(f)) }
        }
        if (showInFolder) mi("عرض في المجلد", Ic.Folder) { f.parentFile?.let { Ctl.openDir(it) } }
        mi("نسخ", Ic.Copy) { Ctl.setClip(listOf(f), false) }
        mi("قص", Ic.Cut) { Ctl.setClip(listOf(f), true) }
        mi("إعادة تسمية", Ic.Edit) { d.rename = f }
        mi("ضغط", Ic.Archive) { d.zip = listOf(f) }
        if (item.isDir) mi("فتح في تاب جديد", Ic.Plus) { Ctl.newTab(f) }
        if (!item.isDir && Arc.supported(f) && !SafFs.usesSaf(f)) {
            mi("استعراض المحتوى", Ic.Eye) { Ctl.push(Overlay.Zip(f.path)) }
            mi("فك الضغط هنا", Ic.Download) { Ctl.unzip(f) }
        }
        mi(if (fav) "إزالة من المفضلة" else "إضافة للمفضلة", if (fav) Ic.Star else Ic.StarFill) { Prefs.toggleFav(f.path) }
        mi("التفاصيل", Ic.Info) { d.info = f }
        mi("حذف", Ic.Trash, true) { Ctl.askDelete(listOf(f), d) }
    }
}

/** ارتفاع موحّد لكل الأشرطة السفلية (لصق / إجراءات / ضغط / أرشيف / حذف...). */
val BarMinHeight = 56.dp

/**
 * شريط سفلي موحّد: نفس العرض (12dp من الجنبين) ونفس المكان والشكل بتاع شريط اللصق.
 * بيتحط فوق الشاشة مع خلفية شفافة؛ الضغط برّه الشريط أو زر الرجوع بيقفله.
 */
@Composable
fun BottomBar(
    onDismiss: () -> Unit,
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    content: @Composable ColumnScope.() -> Unit
) {
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss
        )
    ) {
        Surface(
            Modifier.align(Alignment.BottomCenter)
                .then(if (imeOpen) Modifier.imePadding().padding(bottom = 12.dp) else Modifier.padding(bottom = nav + 92.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            shape = RoundedCornerShape(28.dp), color = color, shadowElevation = 6.dp
        ) { Column(content = content) }
    }
}

@Composable
fun rememberAutoFocus(): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); try { fr.requestFocus() } catch (e: Exception) { } }
    return fr
}

@Composable
fun SelectionBar(count: Int, onAll: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onShare: () -> Unit,
                 onZip: () -> Unit, onDelete: () -> Unit, onClose: () -> Unit,
                 onRename: () -> Unit, onInvert: () -> Unit, onFav: () -> Unit, onTab: () -> Unit,
                 canOpenWith: Boolean = false, onOpenWith: () -> Unit = {},
                 canRenameOne: Boolean = false, onRenameOne: () -> Unit = {},
                 modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    var more by remember { mutableStateOf(false) }
    Surface(
        modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), color = cs.secondaryContainer, shadowElevation = 6.dp
    ) {
        // الأيقونات بتتوزع بالتساوي على كامل عرض الشريط (زي شريط اللصق) بدل ما تتلم في طرف.
        Row(
            Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            @Composable
            fun ib(ic: ImageVector, t: String, c: Color = cs.onSecondaryContainer, a: () -> Unit) =
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(a, Modifier.size(40.dp)) { Icon(ic, t, Modifier.size(22.dp), tint = c) }
                }
            ib(Ic.Close, "إلغاء التحديد", a = onClose)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "$count", style = MaterialTheme.typography.titleMedium, color = cs.onSecondaryContainer, maxLines = 1
                )
            }
            ib(Ic.SelectAll, "تحديد الكل", a = onAll)
            ib(Ic.Copy, "نسخ", a = onCopy)
            ib(Ic.Cut, "قص", a = onCut)
            ib(Ic.Share, "مشاركة", a = onShare)
            ib(Ic.Archive, "ضغط", a = onZip)
            ib(Ic.Trash, "حذف", cs.error, onDelete)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton({ more = true }, Modifier.size(40.dp)) {
                    Icon(Ic.More, "المزيد", Modifier.size(22.dp), tint = cs.onSecondaryContainer)
                }
                DropdownMenu(more, { more = false }) {
                    @Composable
                    fun mi(t: String, ic: ImageVector, enabled: Boolean = true, a: () -> Unit) = DropdownMenuItem(
                        { Text(t) }, { more = false; a() }, enabled = enabled,
                        leadingIcon = { Icon(ic, null, Modifier.size(20.dp)) }
                    )
                    // "فتح بتطبيق تاني" بيشتغل مع ملف واحد بس (ACTION_VIEW مبيدعمش أكتر من ملف)
                    mi(if (canOpenWith) "فتح بتطبيق تاني" else "فتح بتطبيق تاني (اختار ملف واحد)", Ic.File, canOpenWith, onOpenWith)
                    mi("تحديد عكسي", Ic.Sort, true, onInvert)
                    mi("إعادة تسمية", Ic.Edit, canRenameOne, onRenameOne)
                    mi(if (count > 1) "إعادة تسمية جماعية" else "إعادة تسمية جماعية (اختار أكتر من ملف)", Ic.Edit, count > 1, onRename)
                    mi("إضافة للمفضلة", Ic.StarFill, true, onFav)
                    mi("فتح المجلد في تاب جديد", Ic.Folder, true, onTab)
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var t by remember { mutableStateOf(initial) }
    val cs = MaterialTheme.colorScheme
    val fr = rememberAutoFocus()
    BottomBar(onDismiss) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextField(
                t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                label = { Text(title) }, modifier = Modifier.weight(1f).focusRequester(fr)
            )
            TextButton({ onOk(t); onDismiss() }) { Text(confirm) }
            IconButton(onDismiss, Modifier.size(40.dp)) { Icon(Ic.Close, "إلغاء", Modifier.size(22.dp), tint = cs.onPrimaryContainer) }
        }
    }
}

@Composable
private fun ZipDialog(initial: String, onDismiss: () -> Unit, onOk: (String, String, Int) -> Unit) {
    var fmt by remember { mutableStateOf(0) }
    var n by remember { mutableStateOf(initial) }
    var p by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme

    // شريط ضغط سفلي بنفس عرض ومكان وشكل شريط اللصق (بلون مختلف).
    BottomBar(onDismiss) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = BarMinHeight)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("ضغط", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
            Switcher(listOf("ZIP", "7z", "TAR.GZ"), fmt) { fmt = it }
            TextField(
                value = n,
                onValueChange = { n = it },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors(),
                modifier = Modifier.width(150.dp),
                placeholder = { Text("اسم الملف") }
            )
            if (fmt == 0) TextField(
                value = p,
                onValueChange = { p = it },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = fieldColors(),
                modifier = Modifier.width(170.dp),
                placeholder = { Text("باسورد اختياري") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            TextButton(onClick = { onOk(n, p, fmt); onDismiss() }) { Text("إنشاء") }
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    }
}

/** الامتدادات الموجودة فعلًا في نفس المجلد (علشان تختار منها) + امتدادات الملفات نفسها. */
private fun existingExts(files: List<File>): List<String> {
    val out = LinkedHashSet<String>()
    files.forEach { if (it.isFile && it.extension.isNotEmpty()) out.add(it.extension) }
    val dirs = files.mapNotNull { it.parentFile }.distinct().filter { !SafFs.usesSaf(it) }
    for (d in dirs) {
        try {
            d.listFiles()?.asSequence()?.filter { it.isFile && it.extension.isNotEmpty() }
                ?.map { it.extension.lowercase() }?.distinct()?.sorted()?.take(40)?.forEach { out.add(it) }
        } catch (e: Exception) { }
    }
    return out.take(30).toList()
}

private fun cleanExt(s: String): String = s.trim().removePrefix(".").filter { it != '/' && it != '\\' }.trim()

/** امتدادات جاهزة مقسّمة تصنيفات علشان الزحمة. */
private val ExtGroups: List<Pair<String, List<String>>> = listOf(
    "صور" to listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg", "ico", "tiff", "raw", "dng", "avif"),
    "فيديو" to listOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "m4v", "ts", "mpg", "mpeg", "vob"),
    "صوت" to listOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "opus", "wma", "amr", "mid", "m4b", "aiff"),
    "مستندات" to listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "csv", "epub", "md"),
    "أرشيف" to listOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "cab"),
    "كود" to listOf("html", "css", "js", "json", "xml", "kt", "java", "py", "c", "cpp", "h", "php", "sql", "sh", "yml", "ini", "log", "bat"),
    "تطبيقات" to listOf("apk", "apks", "xapk", "aab", "exe", "msi", "dmg", "deb", "jar", "bak", "tmp")
)

/** خانة الامتداد: اكتب بإيدك أو اختار من الامتدادات (الموجودة أولًا، وبعدها تصنيفات). */
@Composable
private fun ExtPicker(ext: String, onExt: (String) -> Unit, options: List<String>, hint: String) {
    val cs = MaterialTheme.colorScheme
    val groups = remember(options) {
        (if (options.isNotEmpty()) listOf("الموجودة" to options) else emptyList()) + ExtGroups
    }
    var gi by remember(options) { mutableStateOf(0) }
    val cur = groups[gi.coerceIn(0, groups.lastIndex)]
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextField(
            ext, { onExt(it.filter { c -> c != '/' && c != '\\' }.take(12)) }, singleLine = true,
            shape = RoundedCornerShape(20.dp), colors = fieldColors(),
            label = { Text("الامتداد (الصيغة)") }, placeholder = { Text(hint) },
            modifier = Modifier.fillMaxWidth()
        )
        // صف التصنيفات
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groups.forEachIndexed { i, g ->
                val on = i == gi
                Text(
                    g.first, style = MaterialTheme.typography.labelMedium,
                    color = if (on) cs.onSecondaryContainer else cs.onPrimaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (on) cs.secondaryContainer else Color.Transparent)
                        .clickable { gi = i }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        // صف الامتدادات بتاع التصنيف المختار (سطرين بيتحركوا أفقيًا مع بعض)
        val rows = cur.second.chunked((cur.second.size + 1) / 2)
        Column(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.forEach { o ->
                        val sel = cleanExt(ext).equals(o, true)
                        Text(
                            ".$o", style = MaterialTheme.typography.labelLarge,
                            color = if (sel) cs.onPrimary else cs.onPrimaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (sel) cs.primary else cs.surface.copy(alpha = 0.5f))
                                .clickable { onExt(if (sel) "" else o) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchDialog(files: List<File>, onDismiss: () -> Unit, onOk: (String, Int, String?) -> Unit) {
    var pat by remember { mutableStateOf("ملف_###") }
    var start by remember { mutableStateOf("1") }
    var ext by remember { mutableStateOf("") }
    val opts = remember(files) { existingExts(files) }
    val cs = MaterialTheme.colorScheme
    BottomBar(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("إعادة تسمية ${files.size} عنصر", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(pat, { pat = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    label = { Text("النمط (كل # رقم)") }, modifier = Modifier.weight(1f))
                TextField(start, { start = it.filter { c -> c.isDigit() }.take(6) }, singleLine = true,
                    shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    label = { Text("يبدأ من") }, modifier = Modifier.width(100.dp))
            }
            ExtPicker(ext, { ext = it }, opts, "سيبه فاضي علشان كل ملف يفضل بامتداده")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    { onOk(pat, start.toIntOrNull() ?: 1, cleanExt(ext).ifEmpty { null }); onDismiss() },
                    Modifier.weight(1f)
                ) { Text("تطبيق") }
                TextButton(onDismiss) { Text("إلغاء") }
            }
        }
    }
}

/** إعادة تسمية ملف واحد: الاسم لوحده + الامتداد لوحده (كتابة أو اختيار). */
@Composable
private fun RenameDialog(f: File, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    val isFile = !f.isDirectory
    var name by remember { mutableStateOf(if (isFile) f.nameWithoutExtension else f.name) }
    var ext by remember { mutableStateOf(if (isFile) f.extension else "") }
    val opts = remember(f) { if (isFile) existingExts(listOf(f)) else emptyList() }
    val cs = MaterialTheme.colorScheme
    val fr = rememberAutoFocus()
    BottomBar(onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("إعادة تسمية", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
            TextField(
                name, { name = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                label = { Text("الاسم") }, modifier = Modifier.fillMaxWidth().focusRequester(fr)
            )
            if (isFile) ExtPicker(ext, { ext = it }, opts, "سيبه فاضي علشان الملف يبقى من غير امتداد")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button({
                    val e = cleanExt(ext)
                    onOk(if (isFile && e.isNotEmpty()) "${name.trim()}.$e" else name.trim()); onDismiss()
                }, Modifier.weight(1f)) { Text("تطبيق") }
                TextButton(onDismiss) { Text("إلغاء") }
            }
        }
    }
}

@Composable
fun Dialogs(d: DlgState) {
    ArchiveDialogs()
    d.rename?.let { f ->
        RenameDialog(f, { d.rename = null }) { Ctl.rename(f, it) }
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
    d.batch?.let { files -> BatchDialog(files, { d.batch = null }) { pat, start, ext -> Ctl.batchRename(files, pat, start, ext) } }
    d.del?.let { files ->
        val cs = MaterialTheme.colorScheme
        BottomBar({ d.del = null }, color = cs.errorContainer) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("تحذف ${files.size} عنصر؟", style = MaterialTheme.typography.titleSmall, color = cs.onErrorContainer, maxLines = 1)
                    Text(
                        if (Prefs.useTrash && files.none { SafFs.usesSaf(it) }) "هتتنقل لسلة المحذوفات وتقدر ترجعها." else "هتتحذف نهائيًا ومش هتقدر ترجعها.",
                        style = MaterialTheme.typography.labelSmall, color = cs.onErrorContainer, maxLines = 2
                    )
                }
                TextButton({ Ctl.delete(files); d.del = null }) { Text("حذف", color = cs.error) }
                TextButton({ d.del = null }) { Text("إلغاء", color = cs.onErrorContainer) }
            }
        }
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
