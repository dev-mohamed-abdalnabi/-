package com.next.files

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * اختيارات الأرشيف لما تدوس على ملف مضغوط: فتح / فك هنا / فك في مكان تاني / حماية بباسورد.
 * كلها أشرطة سفلية بنفس عرض ومكان شريط اللصق (بلون مختلف).
 */
@Composable
fun ArchiveDialogs() {
    val cs = MaterialTheme.colorScheme
    val ask = Ctl.arcAsk
    var picking by remember { mutableStateOf(false) }
    var protectFor by remember { mutableStateOf<File?>(null) }
    var pwdJob by remember { mutableStateOf<Triple<File, File, Boolean>?>(null) } // الأرشيف، مكان الفك، الباسورد غلط؟

    fun run(a: File, parent: File, pwd: String?) {
        Ctl.extractTo(a, parent, pwd) { wrong -> pwdJob = Triple(a, parent, wrong) }
    }

    if (ask != null && !picking) {
        val dismiss = { Ctl.arcAsk = null }
        BottomBar(dismiss) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                @Composable
                fun opt(ic: ImageVector, t: String, a: () -> Unit) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(onClick = a).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(ic, null, Modifier.size(22.dp), tint = cs.onPrimaryContainer)
                        Text(t, style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer, maxLines = 1)
                    }
                }
                opt(Ic.Eye, "فتح") { dismiss(); Ctl.push(Overlay.Zip(ask.path)) }
                opt(Ic.Download, "فك هنا") {
                    dismiss()
                    val parent = Incoming.destParent(Ctl.app, ask) ?: ask.parentFile
                    if (parent != null) run(ask, parent, null)
                }
                opt(Ic.Folder, "فك في…") { picking = true }
                opt(Ic.Lock, "باسورد") { dismiss(); protectFor = ask }
                IconButton(dismiss, Modifier.size(40.dp)) {
                    Icon(Ic.Close, "إلغاء", Modifier.size(22.dp), tint = cs.onPrimaryContainer)
                }
            }
        }
    }

    if (ask != null && picking) {
        val primary = remember { Environment.getExternalStorageDirectory() }
        var cur by remember(ask) { mutableStateOf(Incoming.destParent(Ctl.app, ask) ?: primary) }
        val folders = remember(cur) {
            FS.list(cur, false).filter { it.isDir }.sortedBy { it.file.name.lowercase() }.map { it.file }
        }
        val close = { picking = false; Ctl.arcAsk = null }
        BottomBar({ picking = false }) {
            Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 8.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "فك في: التخزين" + cur.path.removePrefix(primary.path),
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer
                    )
                    TextButton({ picking = false }) { Text("رجوع") }
                    TextButton({ val t = cur; close(); run(ask, t, null) }) { Text("فك هنا") }
                }
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                    if (cur.path != primary.path && cur.parentFile != null) item {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { cur = cur.parentFile ?: cur }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Ic.Up, null, Modifier.size(22.dp), tint = cs.onPrimaryContainer)
                            Spacer(Modifier.size(12.dp))
                            Text("..  المجلد الأعلى", color = cs.onPrimaryContainer)
                        }
                    }
                    items(folders, key = { it.path }) { fo ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { cur = fo }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Ic.Folder, null, Modifier.size(22.dp), tint = cs.primary)
                            Spacer(Modifier.size(12.dp))
                            Text(fo.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = cs.onPrimaryContainer)
                        }
                    }
                    if (folders.isEmpty()) item {
                        Text("مفيش مجلدات هنا", Modifier.padding(12.dp), color = cs.onPrimaryContainer)
                    }
                }
            }
        }
    }

    pwdJob?.let { (a, parent, wrong) ->
        var t by remember(a, wrong) { mutableStateOf("") }
        val fr = rememberAutoFocus()
        BottomBar({ pwdJob = null }) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextField(
                    t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    isError = wrong,
                    label = { Text(if (wrong) "الباسورد غلط" else "الأرشيف محمي بباسورد") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f).focusRequester(fr)
                )
                TextButton({ pwdJob = null; run(a, parent, t) }) { Text("فك") }
                IconButton({ pwdJob = null }, Modifier.size(40.dp)) {
                    Icon(Ic.Close, "إلغاء", Modifier.size(22.dp), tint = cs.onPrimaryContainer)
                }
            }
        }
    }

    protectFor?.let { a ->
        var t by remember(a) { mutableStateOf("") }
        val fr = rememberAutoFocus()
        BottomBar({ protectFor = null }) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = BarMinHeight).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextField(
                    t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    label = { Text("باسورد النسخة المشفّرة (ZIP)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f).focusRequester(fr)
                )
                TextButton({ if (t.isNotEmpty()) { protectFor = null; Ctl.protectArchive(a, t) } }) { Text("إنشاء") }
                IconButton({ protectFor = null }, Modifier.size(40.dp)) {
                    Icon(Ic.Close, "إلغاء", Modifier.size(22.dp), tint = cs.onPrimaryContainer)
                }
            }
        }
    }
}
