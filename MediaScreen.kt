package com.next.files

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

/** مشغّل فيديو وصوت داخلي */
@Composable
fun MediaScreen(path: String) {
    val f = remember(path) { File(path) }
    val isVideo = remember(path) { FS.kindOf(f.extension.lowercase()) == Kind.VIDEO }
    var vv by remember(path) { mutableStateOf<VideoView?>(null) }
    DisposableEffect(path) {
        onDispose { try { vv?.stopPlayback() } catch (e: Exception) { } }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { c ->
                VideoView(c).also { v ->
                    val mc = MediaController(c)
                    mc.setAnchorView(v)
                    v.setMediaController(mc)
                    v.keepScreenOn = true
                    v.setOnPreparedListener { v.start() }
                    v.setOnErrorListener { _, _, _ ->
                        Ctl.toast("الملف ده مش مدعوم في المشغّل الداخلي")
                        true
                    }
                    v.setVideoPath(path)
                    vv = v
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        if (!isVideo) {
            Icon(Ic.Music, null, Modifier.align(Alignment.Center).size(96.dp), tint = Color.White.copy(alpha = 0.7f))
        }
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton({ Ctl.pop() }, Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))) {
                Icon(Ic.Back, "رجوع", tint = Color.White)
            }
            Text(
                f.name, Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall
            )
            Text(
                "فتح بتطبيق تاني", color = Color.White, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                    .clickable { FS.openFile(Ctl.app, f, true) }.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
