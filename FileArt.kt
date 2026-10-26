package com.next.files

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ======================================================================
//  أشكال الملفات: كل نوع له شكل ورمز مرسوم خاص بيه (مش بس لون مختلف)
// ======================================================================

private enum class G {
    DOC, TXT, TABLE, SLIDE, PDF, MD, HTML, XML, JSON, HASH, TERM, SLIDERS,
    BOOK, BOX, ZIP, NOTE, PLAY, MOUNTAIN, LABEL, PLAIN
}

private class Spec(val c: Color, val g: G, val label: String = "")

private fun specOf(ext: String, kind: Kind): Spec = when (ext) {
    "pdf" -> Spec(Color(0xFFE5484D), G.PDF)
    "doc", "docx", "odt", "rtf" -> Spec(Color(0xFF2F6BFF), G.DOC)
    "xls", "xlsx", "ods" -> Spec(Color(0xFF1F9D55), G.TABLE)
    "csv" -> Spec(Color(0xFF0E9F9F), G.TABLE)
    "ppt", "pptx", "odp" -> Spec(Color(0xFFF08A24), G.SLIDE)
    "epub" -> Spec(Color(0xFFB45309), G.BOOK)
    "txt", "log" -> Spec(Color(0xFF64748B), G.TXT)
    "md" -> Spec(Color(0xFF6366F1), G.MD)
    "html", "htm" -> Spec(Color(0xFFF0643C), G.HTML)
    "xml" -> Spec(Color(0xFF0E9AA7), G.XML)
    "json" -> Spec(Color(0xFFD97706), G.JSON)
    "css" -> Spec(Color(0xFF0EA5E9), G.HASH)
    "js" -> Spec(Color(0xFFD9A400), G.LABEL, "JS")
    "kt" -> Spec(Color(0xFF7F52FF), G.LABEL, "Kt")
    "java" -> Spec(Color(0xFFE76F00), G.LABEL, "Jv")
    "py" -> Spec(Color(0xFF3776AB), G.LABEL, "Py")
    "c" -> Spec(Color(0xFF5C6BC0), G.LABEL, "C")
    "cpp" -> Spec(Color(0xFF00599C), G.LABEL, "C++")
    "h" -> Spec(Color(0xFF8E6BBF), G.LABEL, "H")
    "sh" -> Spec(Color(0xFF334155), G.TERM)
    "yml", "yaml", "ini", "conf", "cfg", "properties", "gradle" -> Spec(Color(0xFF0F766E), G.SLIDERS)
    else -> when (kind) {
        Kind.APK -> Spec(Color(0xFF2BB673), G.BOX)
        Kind.ARCHIVE -> Spec(Color(0xFFE0A100), G.ZIP)
        Kind.AUDIO -> Spec(Color(0xFF8E5CF7), G.NOTE)
        Kind.VIDEO -> Spec(Color(0xFFE5484D), G.PLAY)
        Kind.IMAGE -> Spec(Color(0xFF12A594), G.MOUNTAIN)
        Kind.DOC -> Spec(Color(0xFF2F6BFF), G.DOC)
        Kind.TEXT -> Spec(Color(0xFF64748B), G.TXT)
        else -> Spec(Color(0xFF8A8F98), G.PLAIN)
    }
}

// كل الرسم بيتم على مساحة 100×100 وبعدين بيتكبّر/يتصغّر حسب الحجم
private fun DrawScope.unit(block: DrawScope.() -> Unit) {
    val k = size.minDimension / 100f
    translate((size.width - 100f * k) / 2f, (size.height - 100f * k) / 2f) {
        scale(k, Offset.Zero) { block() }
    }
}

private fun DrawScope.ln(c: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 4f) =
    drawLine(c, Offset(x1, y1), Offset(x2, y2), strokeWidth = w, cap = StrokeCap.Round)

private fun DrawScope.pth(c: Color, w: Float = 4f, b: Path.() -> Unit) =
    drawPath(Path().apply(b), c, style = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.glyph(s: Spec, paint: Paint) {
    val c = s.c
    when (s.g) {
        G.DOC -> {
            drawRoundRect(c, Offset(30f, 30f), Size(24f, 7f), CornerRadius(3f, 3f))
            ln(c, 31f, 47f, 69f, 47f, 3.6f)
            ln(c, 31f, 56f, 69f, 56f, 3.6f)
            ln(c, 31f, 65f, 54f, 65f, 3.6f)
        }
        G.TXT -> {
            ln(c, 31f, 33f, 69f, 33f, 3.4f)
            ln(c, 31f, 42f, 69f, 42f, 3.4f)
            ln(c, 31f, 51f, 69f, 51f, 3.4f)
            ln(c, 31f, 60f, 69f, 60f, 3.4f)
            ln(c, 31f, 69f, 52f, 69f, 3.4f)
        }
        G.TABLE -> {
            drawRoundRect(c, Offset(30f, 31f), Size(40f, 38f), CornerRadius(4f, 4f), style = Stroke(width = 3.4f))
            drawRoundRect(c.copy(alpha = 0.35f), Offset(31.7f, 32.7f), Size(36.6f, 9.6f), CornerRadius(2.5f, 2.5f))
            ln(c, 30f, 43f, 70f, 43f, 3.2f)
            ln(c, 30f, 56f, 70f, 56f, 3.2f)
            ln(c, 50f, 31f, 50f, 69f, 3.2f)
        }
        G.SLIDE -> {
            drawCircle(c, 18f, Offset(50f, 50f), style = Stroke(width = 3.6f))
            drawArc(c, -90f, 105f, true, Offset(32f, 32f), Size(36f, 36f))
        }
        G.PDF -> pth(c, 3.8f) {
            moveTo(34f, 70f)
            cubicTo(46f, 60f, 54f, 44f, 51f, 33f)
            cubicTo(49f, 27f, 43f, 31f, 46f, 41f)
            cubicTo(49f, 51f, 60f, 60f, 69f, 59f)
            cubicTo(73f, 58f, 71f, 53f, 64f, 55f)
            cubicTo(54f, 58f, 42f, 66f, 34f, 70f)
        }
        G.MD -> {
            pth(c, 4.6f) {
                moveTo(29f, 66f); lineTo(29f, 36f); lineTo(40f, 52f); lineTo(51f, 36f); lineTo(51f, 66f)
            }
            pth(c, 4.6f) {
                moveTo(64f, 36f); lineTo(64f, 64f)
                moveTo(56f, 56f); lineTo(64f, 65f); lineTo(72f, 56f)
            }
        }
        G.HTML -> {
            pth(c, 4.6f) { moveTo(40f, 36f); lineTo(28f, 50f); lineTo(40f, 64f) }
            pth(c, 4.6f) { moveTo(60f, 36f); lineTo(72f, 50f); lineTo(60f, 64f) }
            ln(c, 54f, 34f, 46f, 66f, 4.4f)
        }
        G.XML -> {
            pth(c, 4.6f) { moveTo(38f, 34f); lineTo(25f, 50f); lineTo(38f, 66f) }
            pth(c, 4.6f) { moveTo(62f, 34f); lineTo(75f, 50f); lineTo(62f, 66f) }
            drawCircle(c, 4f, Offset(50f, 50f))
        }
        G.JSON -> {
            pth(c, 4.2f) {
                moveTo(42f, 31f); quadraticBezierTo(35f, 31f, 35f, 38f); lineTo(35f, 44f)
                quadraticBezierTo(35f, 50f, 29f, 50f); quadraticBezierTo(35f, 50f, 35f, 56f)
                lineTo(35f, 62f); quadraticBezierTo(35f, 69f, 42f, 69f)
            }
            pth(c, 4.2f) {
                moveTo(58f, 31f); quadraticBezierTo(65f, 31f, 65f, 38f); lineTo(65f, 44f)
                quadraticBezierTo(65f, 50f, 71f, 50f); quadraticBezierTo(65f, 50f, 65f, 56f)
                lineTo(65f, 62f); quadraticBezierTo(65f, 69f, 58f, 69f)
            }
            drawCircle(c, 2.4f, Offset(45f, 50f)); drawCircle(c, 2.4f, Offset(50f, 50f)); drawCircle(c, 2.4f, Offset(55f, 50f))
        }
        G.HASH -> {
            ln(c, 42f, 31f, 38f, 69f, 4.6f)
            ln(c, 60f, 31f, 56f, 69f, 4.6f)
            ln(c, 31f, 43f, 70f, 43f, 4.6f)
            ln(c, 29f, 58f, 68f, 58f, 4.6f)
        }
        G.TERM -> {
            pth(c, 4.8f) { moveTo(31f, 37f); lineTo(46f, 50f); lineTo(31f, 63f) }
            ln(c, 51f, 66f, 70f, 66f, 4.8f)
        }
        G.SLIDERS -> {
            ln(c, 30f, 36f, 70f, 36f, 3.2f)
            ln(c, 30f, 50f, 70f, 50f, 3.2f)
            ln(c, 30f, 64f, 70f, 64f, 3.2f)
            drawCircle(c, 6.2f, Offset(42f, 36f))
            drawCircle(c, 6.2f, Offset(60f, 50f))
            drawCircle(c, 6.2f, Offset(46f, 64f))
        }
        G.BOOK -> pth(c, 3.8f) {
            moveTo(50f, 38f); quadraticBezierTo(40f, 31f, 29f, 34f); lineTo(29f, 65f)
            quadraticBezierTo(40f, 62f, 50f, 69f); quadraticBezierTo(60f, 62f, 71f, 65f)
            lineTo(71f, 34f); quadraticBezierTo(60f, 31f, 50f, 38f); lineTo(50f, 69f)
        }
        G.BOX -> {
            pth(c, 3.8f) {
                moveTo(50f, 29f); lineTo(69f, 39f); lineTo(69f, 61f); lineTo(50f, 71f)
                lineTo(31f, 61f); lineTo(31f, 39f); close()
            }
            pth(c, 3.8f) {
                moveTo(31f, 39f); lineTo(50f, 50f); lineTo(69f, 39f)
                moveTo(50f, 50f); lineTo(50f, 71f)
            }
        }
        G.ZIP -> {
            for (i in 0..3) {
                drawRoundRect(c, Offset(if (i % 2 == 0) 43f else 51f, 29f + i * 8f), Size(6f, 6f), CornerRadius(1.6f, 1.6f))
            }
            drawRoundRect(c, Offset(43f, 61f), Size(14f, 11f), CornerRadius(3.5f, 3.5f))
        }
        G.NOTE -> {
            drawCircle(c, 6.6f, Offset(41f, 63f))
            drawCircle(c, 6.6f, Offset(61f, 58f))
            ln(c, 47.5f, 63f, 47.5f, 35f, 4f)
            ln(c, 67.5f, 58f, 67.5f, 30f, 4f)
            ln(c, 47.5f, 35f, 67.5f, 30f, 5.4f)
        }
        G.PLAY -> {
            val tri = Path().apply { moveTo(41f, 34f); lineTo(41f, 66f); lineTo(67f, 50f); close() }
            drawPath(tri, c)
            drawPath(tri, c, style = Stroke(width = 5f, join = StrokeJoin.Round))
        }
        G.MOUNTAIN -> {
            val m = Path().apply {
                moveTo(28f, 67f); lineTo(43f, 44f); lineTo(53f, 57f); lineTo(60f, 48f); lineTo(72f, 67f); close()
            }
            drawPath(m, c)
            drawPath(m, c, style = Stroke(width = 3f, join = StrokeJoin.Round))
            drawCircle(c, 5.4f, Offset(62f, 35f))
        }
        G.LABEL -> {
            paint.color = c.toArgb()
            paint.textSize = if (s.label.length > 2) 22f else 29f
            drawIntoCanvas { it.nativeCanvas.drawText(s.label, 50f, if (s.label.length > 2) 58f else 60f, paint) }
        }
        G.PLAIN -> {
            drawCircle(c, 4.2f, Offset(36f, 50f))
            drawCircle(c, 4.2f, Offset(50f, 50f))
            drawCircle(c, 4.2f, Offset(64f, 50f))
        }
    }
}

@Composable
fun FileTypeArt(ext: String, kind: Kind, modifier: Modifier = Modifier, tag: Boolean = true) {
    val sp = remember(ext, kind) { specOf(ext, kind) }
    val label = remember(ext) { ext.uppercase().take(4) }
    val glyphPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER } }
    val tagPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER; color = android.graphics.Color.WHITE } }
    Canvas(modifier) {
        val showTag = tag && label.isNotEmpty() && size.minDimension > 50.dp.toPx()
        unit {
            val page = Path().apply {
                moveTo(26f, 6f); lineTo(60f, 6f); lineTo(80f, 26f); lineTo(80f, 88f)
                quadraticBezierTo(80f, 94f, 74f, 94f); lineTo(26f, 94f)
                quadraticBezierTo(20f, 94f, 20f, 88f); lineTo(20f, 12f)
                quadraticBezierTo(20f, 6f, 26f, 6f); close()
            }
            val fold = Path().apply {
                moveTo(60f, 6f); lineTo(60f, 20f); quadraticBezierTo(60f, 26f, 66f, 26f); lineTo(80f, 26f); close()
            }
            drawPath(page, lerp(sp.c, Color.White, 0.86f))
            drawPath(page, sp.c.copy(alpha = 0.35f), style = Stroke(width = 2f, join = StrokeJoin.Round))
            drawPath(fold, lerp(sp.c, Color.White, 0.58f))
            translate(0f, if (showTag) 0f else 7f) { glyph(sp, glyphPaint) }
            if (showTag) {
                drawRoundRect(sp.c, Offset(24f, 75f), Size(52f, 17f), CornerRadius(8.5f, 8.5f))
                tagPaint.textSize = if (label.length > 3) 10.5f else 12.5f
                drawIntoCanvas { it.nativeCanvas.drawText(label, 50f, 87.4f, tagPaint) }
            }
        }
    }
}

// ======================================================================
//  الفولدر: بيعرض اللي جواه بجد (صور/فيديو بالصورة الحقيقية، والباقي بشكل نوعه)
// ======================================================================

private fun folderBack() = Path().apply {
    moveTo(8f, 22f); quadraticBezierTo(8f, 14f, 16f, 14f)
    lineTo(38f, 14f); quadraticBezierTo(42f, 14f, 45f, 18f)
    lineTo(48f, 22f); lineTo(84f, 22f); quadraticBezierTo(92f, 22f, 92f, 30f)
    lineTo(92f, 78f); quadraticBezierTo(92f, 86f, 84f, 86f)
    lineTo(16f, 86f); quadraticBezierTo(8f, 86f, 8f, 78f); close()
}

private fun folderFront() = Path().apply {
    moveTo(12f, 60f); quadraticBezierTo(12f, 54f, 18f, 53.5f)
    lineTo(88f, 49f); quadraticBezierTo(95f, 48.5f, 94.5f, 55f)
    lineTo(89f, 80f); quadraticBezierTo(88f, 87f, 81f, 87f)
    lineTo(18f, 87f); quadraticBezierTo(11f, 87f, 12f, 80f); close()
}

@Composable
fun FolderArt(modifier: Modifier = Modifier, badge: ImageVector? = null, peek: FS.FolderPeek? = null) {
    val cs = MaterialTheme.colorScheme
    val back = cs.primary
    val front = lerp(cs.primary, Color.White, 0.55f).copy(alpha = 0.94f)
    val n = peek?.names?.size?.coerceAtMost(3) ?: 0
    BoxWithConstraints(modifier) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) { unit { drawPath(folderBack(), back) } }
        if (peek != null) {
            for (i in 0 until n) {
                val cw = w * (when (n) { 1 -> 0.34f; 2 -> 0.30f; else -> 0.25f })
                val ch = cw * 1.4f
                val cx = when (n) { 1 -> 0.50f; 2 -> if (i == 0) 0.37f else 0.63f; else -> 0.28f + 0.22f * i }
                val top = if (n == 3 && i == 1) 0.15f else 0.20f
                val rot = when (n) { 1 -> 0f; 2 -> if (i == 0) -5f else 5f; else -> (i - 1) * 7f }
                PeekCard(
                    peek.files.getOrNull(i), peek.names[i], peek.kinds.getOrElse(i) { Kind.OTHER }, cw, ch,
                    Modifier.align(AbsoluteAlignment.TopLeft)
                        .absoluteOffset(x = w * cx - cw / 2, y = w * top)
                        .graphicsLayer { rotationZ = rot; transformOrigin = TransformOrigin(0.5f, 1f) }
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) { unit { drawPath(folderFront(), front) } }
        if (badge != null && n == 0) {
            Icon(badge, null, Modifier.size(maxWidth * 0.3f).align(Alignment.Center).absoluteOffset(x = 0.dp, y = maxHeight * 0.2f), tint = cs.primary)
        }
    }
}

@Composable
private fun PeekCard(file: File?, name: String, kind: Kind, cw: Dp, ch: Dp, modifier: Modifier) {
    val ctx = LocalContext.current
    val ext = remember(name) { name.substringAfterLast('.', "").lowercase() }
    val real = file != null && (kind == Kind.IMAGE || kind == Kind.VIDEO || kind == Kind.APK || (kind == Kind.DOC && ext == "pdf"))
    val bmp by produceState<Bitmap?>(null, file?.path) {
        value = if (file != null && real) withContext(Dispatchers.IO) { Thumbs.load(ctx, file, kind, 160) } else null
    }
    val shape = RoundedCornerShape(5.dp)
    val b = bmp
    when {
        b != null -> Image(
            b.asImageBitmap(), null,
            modifier.size(cw, ch).shadow(2.dp, shape).clip(shape),
            contentScale = ContentScale.Crop
        )
        kind == Kind.FOLDER -> Box(modifier.size(cw, ch), contentAlignment = Alignment.Center) {
            FolderArt(Modifier.requiredSize(ch * 0.95f))
        }
        else -> Box(modifier.size(cw, ch), contentAlignment = Alignment.Center) {
            FileTypeArt(ext, kind, Modifier.requiredSize(ch * 1.1f), tag = false)
        }
    }
}
