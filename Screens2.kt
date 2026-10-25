package com.next.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ===================== محرر نصوص =====================
@Composable
fun EditorScreen(path: String) {
    val cs = MaterialTheme.colorScheme
    val file = remember(path) { File(path) }
    var text by remember { mutableStateOf<String?>(null) }
    var orig by remember { mutableStateOf("") }
    var askDiscard by remember { mutableStateOf(false) }
    LaunchedEffect(path) {
        val s = withContext(Dispatchers.IO) { try { file.readText() } catch (e: Exception) { null } }
        if (s == null) {
            Ctl.toast("تعذر قراءة الملف")
            Ctl.pop()
        } else {
            orig = s
            text = s
        }
    }
    val dirty = text != null && text != orig
    BackHandler { if (dirty) askDiscard = true else Ctl.pop() }

    fun save(thenClose: Boolean) {
        val t = text ?: return
        Ctl.task("جاري الحفظ…", { file.writeText(t) }) {
            orig = t
            Ctl.toast("تم الحفظ")
            if (thenClose) Ctl.pop()
        }
    }

    Surface0 {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding()) {
            ScreenHeader(file.name, if (dirty) "Next · فيه تعديلات لسه متحفظتش" else "Next · محرر نصوص", onBack = {
                if (dirty) askDiscard = true else Ctl.pop()
            }) {
                if (dirty) IconButton(
                    { save(false) },
                    Modifier.size(48.dp).clip(CircleShape).background(cs.primary)
                ) { Icon(Ic.Check, "حفظ", tint = cs.onPrimary) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                TextField(
                    value = text ?: "", onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp).padding(bottom = 8.dp),
                    shape = RoundedCornerShape(24.dp), colors = fieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                )
            }
        }
    }
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("فيه تعديلات متحفظتش", style = MaterialTheme.typography.titleLarge) },
            text = { Text("تحب تحفظها قبل ما تقفل؟") },
            confirmButton = { TextButton({ askDiscard = false; save(true) }) { Text("حفظ") } },
            dismissButton = {
                TextButton({ askDiscard = false; Ctl.pop() }) { Text("تجاهل", color = cs.error) }
            }
        )
    }
}

@Composable
private fun Surface0(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
}

// ===================== عارض الصور =====================
@Composable
fun ImageViewer(o: Overlay.Images) {
    val pager = rememberPagerState(o.index) { o.paths.size }
    var zoomed by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val rot = remember { androidx.compose.runtime.mutableStateMapOf<Int, Int>() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { p ->
            ZoomImage(File(o.paths[p]), pager.currentPage == p, rot[p] ?: 0) { zoomed = it }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                { Ctl.pop() },
                Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            ) { Icon(Ic.Back, "رجوع", tint = Color.White) }
            Text(
                File(o.paths[pager.currentPage]).name, Modifier.weight(1f).padding(horizontal = 12.dp),
                color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "${pager.currentPage + 1} / ${o.paths.size}", color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp)
            )
            IconButton(
                { val c = pager.currentPage; rot[c] = ((rot[c] ?: 0) + 90) % 360 },
                Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            ) { Text("⟳", color = Color.White, style = MaterialTheme.typography.titleLarge) }
            IconButton(
                { info = true },
                Modifier.padding(start = 6.dp).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
            ) { Icon(Ic.Info, "تفاصيل", tint = Color.White) }
        }
        if (info) {
            val f = File(o.paths[pager.currentPage])
            val dim = remember(f.path) {
                val op = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(f.path, op)
                "${op.outWidth}×${op.outHeight}"
            }
            AlertDialog(
                onDismissRequest = { info = false }, shape = RoundedCornerShape(28.dp),
                title = { Text("تفاصيل الصورة") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(f.name)
                        Text("الأبعاد: $dim")
                        Text("الحجم: ${FS.fmtSize(f.length())}")
                        Text("التاريخ: " + java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(f.lastModified())))
                        Text(f.parent ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = { TextButton({ info = false }) { Text("تمام") } }
            )
        }
    }
}

@Composable
private fun ZoomImage(f: File, active: Boolean, rotation: Int, onZoom: (Boolean) -> Unit) {
    val bmp by produceState<android.graphics.Bitmap?>(null, f.path, rotation) {
        value = withContext(Dispatchers.IO) {
            val b = Thumbs.decode(f, 2048)
            if (b != null && rotation != 0) {
                android.graphics.Bitmap.createBitmap(
                    b, 0, 0, b.width, b.height,
                    android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true
                )
            } else b
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            off = Offset.Zero
        }
    }
    fun clamp(o: Offset, s: Float): Offset {
        val mx = box.width * (s - 1f) / 2f
        val my = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }
    Box(
        Modifier.fillMaxSize().onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1.01f) {
                        scale = 1f; off = Offset.Zero
                    } else scale = 2.5f
                    onZoom(scale > 1.01f)
                })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val ev = awaitPointerEvent()
                        if (ev.changes.size > 1 || scale > 1.01f) {
                            val z = ev.calculateZoom()
                            val p = ev.calculatePan()
                            scale = (scale * z).coerceIn(1f, 6f)
                            off = if (scale <= 1.01f) Offset.Zero else clamp(off + p, scale)
                            onZoom(scale > 1.01f)
                            ev.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (ev.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                b.asImageBitmap(), null,
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y
                },
                contentScale = ContentScale.Fit
            )
        } else {
            androidx.compose.material3.CircularProgressIndicator(color = Color.White)
        }
    }
}

// ===================== الإعدادات =====================
@Composable
private fun SectionTitle(t: String, first: Boolean = false) {
    Text(
        t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (first) 0.dp else 28.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String?, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(22.dp), tint = cs.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun SettingsScreen(bottom: Dp) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = bottom)
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 20.dp)) {
                Text("الإعدادات", style = MaterialTheme.typography.displaySmall)
            }
        }
        item {
            SectionTitle("المظهر", first = true)
            Switcher(listOf("تلقائي", "فاتح", "داكن"), Prefs.theme) { Prefs.theme = it; Prefs.save() }
            Spacer(Modifier.height(8.dp))
            SettingRow(Ic.Theme, "الوضع الأسود (AMOLED)", "خلفية سودا خالصة في الوضع الداكن") {
                Switch(Prefs.amoled, { Prefs.amoled = it; Prefs.save() })
            }
            SettingRow(Ic.Palette, "لون التطبيق", if (Prefs.accent < 0) "ألوان النظام (لو متاحة)" else "لون مخصص")
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (listOf(-1) + AccentColors.indices.toList()).forEach { i ->
                    val sel = Prefs.accent == i
                    val c = if (i < 0) cs.primaryContainer else Color(AccentColors[i])
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(c)
                            .then(if (sel) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier)
                            .clickable { Prefs.accent = i; Prefs.save() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sel) Icon(Ic.Check, null, Modifier.size(20.dp), tint = Color.White)
                        else if (i < 0) Icon(Ic.Palette, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
                    }
                }
            }
        }
        item {
            SectionTitle("الملفات")
            SettingRow(Ic.Eye, "إظهار الملفات المخفية", "الملفات اللي اسمها بيبدأ بنقطة") {
                Switch(Prefs.showHidden, { Prefs.showHidden = it; Prefs.save() })
            }
            SettingRow(Ic.Grid, "العرض الشبكي", "اعرض الملفات كمربعات") {
                Switch(Prefs.grid, { Prefs.grid = it; Prefs.save() })
            }
            SettingRow(Ic.Edit, "حجم الخط", "يتطبق على التطبيق كله")
            Switcher(listOf("صغير", "عادي", "كبير", "أكبر"), Prefs.fontIdx) { Prefs.fontIdx = it; Prefs.save() }
            Spacer(Modifier.height(8.dp))
            SettingRow(Ic.Image, "حجم العناصر", "الأيقونات والفولدرات في القائمة والشبكة")
            Switcher(listOf("صغير", "متوسط", "كبير"), Prefs.viewSize) { Prefs.viewSize = it; Prefs.save() }
            Spacer(Modifier.height(6.dp))
        }
        item {
            SectionTitle("الحذف")
            SettingRow(Ic.Trash, "سلة المحذوفات", "انقل المحذوف للسلة بدل الحذف النهائي") {
                Switch(Prefs.useTrash, { Prefs.useTrash = it; Prefs.save() })
            }
            SettingRow(Ic.Info, "تأكيد قبل الحذف", "اسألني قبل ما أحذف") {
                Switch(Prefs.confirmDelete, { Prefs.confirmDelete = it; Prefs.save() })
            }
            SettingRow(Ic.Clock, "مسح السلة تلقائيًا", "يتمسح اللي فيها بعد المدة دي (بيتنفذ عند فتح التطبيق)")
            Switcher(listOf("أبدًا", "7 أيام", "30 يوم"), listOf(0, 7, 30).indexOf(Prefs.trashDays).coerceAtLeast(0)) { Prefs.trashDays = listOf(0, 7, 30)[it]; Prefs.save() }
            Spacer(Modifier.height(6.dp))
            SettingRow(Ic.Restore, "فتح السلة", "استرجع أو احذف نهائيًا", onClick = { Ctl.push(Overlay.Trash) })
        }
        item {
            SectionTitle("عن التطبيق")
            SettingRow(Ic.Folder, "مدير الملفات", "الإصدار 1.1 · تصميم Next")
            SettingRow(Ic.Lock, "صلاحية الوصول للملفات", if (Perm.granted) "مفعّلة" else "مش مفعّلة", onClick = { Ctl.requestAccess() })
        }
    }
}

// ===================== شاشة الصلاحية =====================
@Composable
fun PermissionScreen() {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        CookieIcon(Ic.Lock, 148.dp, cs.primaryContainer, cs.onPrimaryContainer)
        Spacer(Modifier.height(28.dp))
        Text("محتاجين نشوف ملفاتك", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(10.dp))
        Text(
            "علشان نقدر نعرض الملفات وننسخ وننقل ونحذف، لازم تسمح للتطبيق بالوصول لكل الملفات. التطبيق شغال بدون إنترنت وملفاتك مبتخرجش من جهازك.",
            style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        BigButton("السماح بالوصول", icon = Ic.Check) { Ctl.requestAccess() }
    }
}
