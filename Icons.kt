package com.next.files

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private class Part(val fill: Boolean, val w: Float, val b: PathBuilder.() -> Unit)

private fun outl(w: Float = 2f, b: PathBuilder.() -> Unit) = Part(false, w, b)
private fun solid(w: Float = 1.6f, b: PathBuilder.() -> Unit) = Part(true, w, b)

private fun vec(name: String, fill: Boolean = false, w: Float = 2f, mirror: Boolean = false, b: PathBuilder.() -> Unit): ImageVector =
    vecM(name, mirror, Part(fill, w, b))

private fun vecM(name: String, mirror: Boolean = false, vararg parts: Part): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
        parts.forEach { p ->
            path(
                fill = if (p.fill) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = p.w,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = p.b
            )
        }
    }.build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
}

private fun PathBuilder.rr(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    horizontalLineTo(x + w - r)
    arcTo(r, r, 0f, false, true, x + w, y + r)
    verticalLineTo(y + h - r)
    arcTo(r, r, 0f, false, true, x + w - r, y + h)
    horizontalLineTo(x + r)
    arcTo(r, r, 0f, false, true, x, y + h - r)
    verticalLineTo(y + r)
    arcTo(r, r, 0f, false, true, x + r, y)
    close()
}

private fun PathBuilder.fileShape() {
    moveTo(8f, 3f)
    horizontalLineTo(13.5f)
    lineTo(19f, 8.5f)
    verticalLineTo(18f)
    arcTo(3f, 3f, 0f, false, true, 16f, 21f)
    horizontalLineTo(8f)
    arcTo(3f, 3f, 0f, false, true, 5f, 18f)
    verticalLineTo(6f)
    arcTo(3f, 3f, 0f, false, true, 8f, 3f)
    close()
    moveTo(13.5f, 3.5f)
    verticalLineTo(7f)
    arcTo(1.5f, 1.5f, 0f, false, false, 15f, 8.5f)
    horizontalLineTo(18.5f)
}

private fun PathBuilder.folderShape() {
    moveTo(3f, 7.5f)
    arcTo(2.5f, 2.5f, 0f, false, true, 5.5f, 5f)
    horizontalLineTo(9.2f)
    lineTo(11.2f, 7.5f)
    horizontalLineTo(18.5f)
    arcTo(2.5f, 2.5f, 0f, false, true, 21f, 10f)
    verticalLineTo(16.5f)
    arcTo(2.5f, 2.5f, 0f, false, true, 18.5f, 19f)
    horizontalLineTo(5.5f)
    arcTo(2.5f, 2.5f, 0f, false, true, 3f, 16.5f)
    close()
}

object Ic {
    val Home by lazy {
        vec("home") {
            moveTo(4f, 11f); lineTo(12f, 4.5f); lineTo(20f, 11f); verticalLineTo(18f)
            arcTo(2f, 2f, 0f, false, true, 18f, 20f); horizontalLineTo(6f)
            arcTo(2f, 2f, 0f, false, true, 4f, 18f); close()
        }
    }
    val Folder by lazy { vec("folder") { folderShape() } }
    val NewFolder by lazy {
        vec("newfolder") {
            folderShape()
            moveTo(12f, 10.8f); verticalLineTo(15.6f)
            moveTo(9.6f, 13.2f); horizontalLineTo(14.4f)
        }
    }
    val File by lazy { vec("file") { fileShape() } }
    val NewFile by lazy {
        vec("newfile") {
            fileShape()
            moveTo(12f, 12.2f); verticalLineTo(17f)
            moveTo(9.6f, 14.6f); horizontalLineTo(14.4f)
        }
    }
    val Doc by lazy {
        vec("doc") {
            fileShape()
            moveTo(9f, 13f); horizontalLineTo(15f)
            moveTo(9f, 16.6f); horizontalLineTo(13f)
        }
    }
    val Code by lazy {
        vec("code", w = 2.2f) {
            moveTo(9f, 7f); lineTo(4f, 12f); lineTo(9f, 17f)
            moveTo(15f, 7f); lineTo(20f, 12f); lineTo(15f, 17f)
        }
    }
    val Image by lazy {
        vecM(
            "image", false,
            outl { rr(3f, 4.5f, 18f, 15f, 3.8f) },
            outl { moveTo(4.5f, 17.5f); lineTo(9.5f, 12.8f); lineTo(13f, 15.8f); lineTo(15.6f, 13.6f); lineTo(19.5f, 17f) },
            solid(1.4f) { circle(8.6f, 9.2f, 1.5f) }
        )
    }
    val Video by lazy {
        vecM(
            "video", false,
            outl { rr(3f, 5.5f, 18f, 13f, 3.8f) },
            solid(1.6f) { moveTo(10.6f, 9.8f); lineTo(14.6f, 12f); lineTo(10.6f, 14.2f); close() }
        )
    }
    val Music by lazy {
        vecM(
            "music", false,
            outl { moveTo(9.5f, 17f); verticalLineTo(6.8f); lineTo(19f, 4.8f); verticalLineTo(15f) },
            solid(1.4f) { circle(7f, 17.3f, 2.4f); circle(16.5f, 15.3f, 2.4f) }
        )
    }
    val Apps by lazy {
        vec("apps") {
            rr(4f, 4f, 6.5f, 6.5f, 2f); rr(13.5f, 4f, 6.5f, 6.5f, 2f)
            rr(4f, 13.5f, 6.5f, 6.5f, 2f); rr(13.5f, 13.5f, 6.5f, 6.5f, 2f)
        }
    }
    val Archive by lazy {
        vec("archive") {
            fileShape()
            moveTo(10.5f, 5.2f); verticalLineTo(6.6f)
            moveTo(10.5f, 8.4f); verticalLineTo(9.8f)
            rr(9f, 11.8f, 3f, 4.6f, 1.2f)
        }
    }
    val Download by lazy {
        vec("download") {
            moveTo(12f, 4f); verticalLineTo(15f)
            moveTo(7.5f, 10.8f); lineTo(12f, 15.3f); lineTo(16.5f, 10.8f)
            moveTo(5f, 19.5f); horizontalLineTo(19f)
        }
    }
    val Storage by lazy {
        vec("storage") {
            rr(3.5f, 7f, 17f, 10f, 3f)
            moveTo(7f, 12f); horizontalLineTo(11f)
            moveTo(16.5f, 12f); lineTo(16.51f, 12f)
        }
    }
    val Chart by lazy {
        vec("chart", w = 3f) {
            moveTo(6f, 19f); verticalLineTo(12f)
            moveTo(12f, 19f); verticalLineTo(5.5f)
            moveTo(18f, 19f); verticalLineTo(14f)
        }
    }
    val Clock by lazy {
        vec("clock") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 7.5f); verticalLineTo(12f); lineTo(15f, 14f)
        }
    }
    val Settings by lazy {
        vec("settings") {
            moveTo(4f, 8f); horizontalLineTo(11.5f); circle(14f, 8f, 2.5f); moveTo(16.5f, 8f); horizontalLineTo(20f)
            moveTo(4f, 16f); horizontalLineTo(7f); circle(9.5f, 16f, 2.5f); moveTo(12f, 16f); horizontalLineTo(20f)
        }
    }
    val Search by lazy { vec("search", w = 2.2f) { circle(10.5f, 10.5f, 6.3f); moveTo(15.2f, 15.2f); lineTo(20f, 20f) } }
    val Close by lazy { vec("close", w = 2.2f) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    val SelectAll by lazy {
        vec("selectall", w = 2f) {
            rr(4f, 4f, 16f, 16f, 4.5f)
            moveTo(8.2f, 12.3f); lineTo(10.9f, 15f); lineTo(16f, 9.2f)
        }
    }
    val Check by lazy { vec("check", w = 2.4f) { moveTo(5f, 12.5f); lineTo(9.8f, 17.2f); lineTo(19f, 7.2f) } }
    val Menu by lazy {
        vec("menu", w = 2.2f) {
            moveTo(4f, 7f); horizontalLineTo(20f); moveTo(4f, 12f); horizontalLineTo(20f); moveTo(4f, 17f); horizontalLineTo(20f)
        }
    }
    val More by lazy {
        vec("more", w = 3.4f) {
            moveTo(12f, 5f); lineTo(12.01f, 5f); moveTo(12f, 12f); lineTo(12.01f, 12f); moveTo(12f, 19f); lineTo(12.01f, 19f)
        }
    }
    val Back by lazy { vec("back", w = 2.2f, mirror = true) { moveTo(14.5f, 5.5f); lineTo(8f, 12f); lineTo(14.5f, 18.5f) } }
    val ChevL by lazy { vec("chevl", w = 2.2f) { moveTo(14f, 7f); lineTo(9f, 12f); lineTo(14f, 17f) } }
    val Up by lazy { vec("up", w = 2.2f) { moveTo(5.5f, 14.5f); lineTo(12f, 8f); lineTo(18.5f, 14.5f) } }
    val Plus by lazy { vec("plus", w = 2.4f) { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }
    val Copy by lazy {
        vec("copy") {
            rr(9f, 9f, 11f, 11f, 3f)
            moveTo(15f, 9f); verticalLineTo(7f)
            arcTo(3f, 3f, 0f, false, false, 12f, 4f); horizontalLineTo(7f)
            arcTo(3f, 3f, 0f, false, false, 4f, 7f); verticalLineTo(12f)
            arcTo(3f, 3f, 0f, false, false, 7f, 15f); horizontalLineTo(9f)
        }
    }
    val Cut by lazy {
        vec("cut") {
            circle(7f, 17f, 2.6f); circle(17f, 17f, 2.6f)
            moveTo(8.8f, 15.1f); lineTo(16f, 4f)
            moveTo(15.2f, 15.1f); lineTo(8f, 4f)
        }
    }
    val Paste by lazy {
        vec("paste") {
            rr(5f, 5f, 14f, 16f, 3f)
            rr(9f, 3f, 6f, 4f, 2f)
        }
    }
    val Trash by lazy {
        vec("trash") {
            moveTo(4f, 7f); horizontalLineTo(20f)
            moveTo(9f, 7f); verticalLineTo(5.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 10.5f, 4f); horizontalLineTo(13.5f)
            arcTo(1.5f, 1.5f, 0f, false, true, 15f, 5.5f); verticalLineTo(7f)
            moveTo(6.2f, 7f); lineTo(7f, 18f)
            arcTo(2f, 2f, 0f, false, false, 9f, 20f); horizontalLineTo(15f)
            arcTo(2f, 2f, 0f, false, false, 17f, 18f); lineTo(17.8f, 7f)
            moveTo(10f, 11f); verticalLineTo(16f)
            moveTo(14f, 11f); verticalLineTo(16f)
        }
    }
    val Share by lazy {
        vec("share") {
            circle(6.5f, 12f, 2.4f); circle(17.5f, 6.5f, 2.4f); circle(17.5f, 17.5f, 2.4f)
            moveTo(8.6f, 10.8f); lineTo(15.4f, 7.7f)
            moveTo(8.6f, 13.2f); lineTo(15.4f, 16.3f)
        }
    }
    val Star by lazy {
        vec("star") {
            moveTo(12f, 3.8f); lineTo(14.5f, 9.2f); lineTo(20.4f, 9.9f); lineTo(16f, 13.9f); lineTo(17.3f, 19.7f)
            lineTo(12f, 16.8f); lineTo(6.7f, 19.7f); lineTo(8f, 13.9f); lineTo(3.6f, 9.9f); lineTo(9.5f, 9.2f); close()
        }
    }
    val StarFill by lazy {
        vec("starfill", fill = true, w = 1.6f) {
            moveTo(12f, 3.8f); lineTo(14.5f, 9.2f); lineTo(20.4f, 9.9f); lineTo(16f, 13.9f); lineTo(17.3f, 19.7f)
            lineTo(12f, 16.8f); lineTo(6.7f, 19.7f); lineTo(8f, 13.9f); lineTo(3.6f, 9.9f); lineTo(9.5f, 9.2f); close()
        }
    }
    val Info by lazy {
        vec("info") {
            circle(12f, 12f, 8.5f)
            moveTo(12f, 11f); verticalLineTo(16f)
            moveTo(12f, 8f); lineTo(12.01f, 8f)
        }
    }
    val Edit by lazy {
        vec("edit") {
            moveTo(4.5f, 19.5f); lineTo(5.5f, 15.5f); lineTo(15f, 6f); lineTo(18f, 9f); lineTo(8.5f, 18.5f); close()
            moveTo(13f, 8f); lineTo(16f, 11f)
        }
    }
    val Sort by lazy {
        vec("sort", w = 2.2f) {
            moveTo(4f, 7f); horizontalLineTo(20f); moveTo(4f, 12f); horizontalLineTo(15f); moveTo(4f, 17f); horizontalLineTo(10f)
        }
    }
    val Grid by lazy {
        vec("grid") {
            rr(4f, 4f, 6.5f, 6.5f, 2f); rr(13.5f, 4f, 6.5f, 6.5f, 2f)
            rr(4f, 13.5f, 6.5f, 6.5f, 2f); rr(13.5f, 13.5f, 6.5f, 6.5f, 2f)
        }
    }
    val Restore by lazy {
        vec("restore", w = 2.2f) {
            moveTo(4f, 4.5f); verticalLineTo(9.5f); horizontalLineTo(9f)
            moveTo(5.4f, 9.5f); arcTo(7.5f, 7.5f, 0f, true, true, 5f, 15.5f)
        }
    }
    val Eye by lazy {
        vec("eye") {
            moveTo(3f, 12f); curveTo(6f, 6.5f, 18f, 6.5f, 21f, 12f); curveTo(18f, 17.5f, 6f, 17.5f, 3f, 12f); close()
            circle(12f, 12f, 2.6f)
        }
    }
    val Theme by lazy {
        vecM(
            "theme", false,
            outl { circle(12f, 12f, 8.5f) },
            solid(1.2f) { moveTo(12f, 3.5f); arcTo(8.5f, 8.5f, 0f, false, false, 12f, 20.5f); close() }
        )
    }
    val Palette by lazy {
        vecM(
            "palette", false,
            outl { circle(12f, 12f, 8.5f) },
            solid(1.2f) { circle(8.6f, 11f, 1.1f); circle(12f, 7.8f, 1.1f); circle(15.6f, 11f, 1.1f); circle(12f, 15.4f, 1.1f) }
        )
    }
    val Lock by lazy {
        vec("lock") {
            rr(5f, 10.5f, 14f, 9.5f, 3f)
            moveTo(8f, 10.5f); verticalLineTo(8f)
            arcTo(4f, 4f, 0f, false, true, 16f, 8f); verticalLineTo(10.5f)
        }
    }
}
