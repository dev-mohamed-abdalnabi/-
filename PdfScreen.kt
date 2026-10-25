package com.next.files

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** قارئ PDF داخلي (PdfRenderer بتاع النظام) */
private class PdfDoc(f: File) {
    private val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    private val r = PdfRenderer(fd)
    val count: Int = r.pageCount

    @Synchronized
    fun render(i: Int, w: Int): Bitmap {
        val p = r.openPage(i)
        try {
            val h = (w.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            b.eraseColor(android.graphics.Color.WHITE)
            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return b
        } finally {
            p.close()
        }
    }

    @Synchronized
    fun close() {
        try { r.close() } catch (e: Exception) { }
        try { fd.close() } catch (e: Exception) { }
    }
}

private val Zooms = listOf(1f, 1.5f, 2f, 3f)

@Composable
fun PdfScreen(path: String) {
    var doc by remember(path) { mutableStateOf<PdfDoc?>(null) }
    var zi by remember(path) { mutableStateOf(0) }
    val zoom = Zooms[zi]
    val list = rememberLazyListState()
    val hs = rememberScrollState()

    LaunchedEffect(path) {
        val d = withContext(Dispatchers.IO) { try { PdfDoc(File(path)) } catch (e: Exception) { null } }
        if (d == null) {
            Ctl.toast("تعذر فتح الـ PDF (ممكن يكون محمي بباسورد)")
            Ctl.pop()
        } else doc = d
    }
    DisposableEffect(path) { onDispose { doc?.close() } }

    Box(Modifier.fillMaxSize().background(Color(0xFF2B2B2B))) {
        val d = doc
        if (d == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wDp = maxWidth * zoom
                val wPx = with(LocalDensity.current) { wDp.roundToPx() }
                Box(Modifier.fillMaxSize().horizontalScroll(hs)) {
                    LazyColumn(
                        Modifier.width(wDp).fillMaxHeight(), state = list,
                        contentPadding = PaddingValues(top = 76.dp, bottom = 24.dp)
                    ) {
                        items(d.count) { i -> PdfPage(d, i, wPx) }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton({ Ctl.pop() }, Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))) {
                Icon(Ic.Back, "رجوع", tint = Color.White)
            }
            Text(
                File(path).name, Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall
            )
            val cur = (list.firstVisibleItemIndex + 1).coerceAtMost((doc?.count ?: 1).coerceAtLeast(1))
            Text(
                "$cur / ${doc?.count ?: 0}", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 12.dp, vertical = 6.dp)
            )
            Text(
                "${(zoom * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f))
                    .clickable { zi = (zi + 1) % Zooms.size }.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PdfPage(d: PdfDoc, i: Int, wPx: Int) {
    val bmp by produceState<Bitmap?>(null, d, i, wPx) {
        value = withContext(Dispatchers.IO) { try { d.render(i, wPx) } catch (e: Exception) { null } }
    }
    Box(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color.White).heightIn(min = 240.dp),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        else CircularProgressIndicator(color = Color.Gray)
    }
}
