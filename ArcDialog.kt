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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/** اختيارات الأرشيف لما تدوس على ملف مضغوط: فتح / فك هنا / فك في مكان تاني / حماية بباسورد */
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
        AlertDialog(
            onDismissRequest = dismiss, shape = RoundedCornerShape(28.dp),
            title = { Text(ask.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    @Composable
                    fun opt(ic: ImageVector, t: String, a: () -> Unit) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = a).padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(ic, null, Modifier.size(22.dp), tint = cs.primary)
                            Spacer(Modifier.size(14.dp))
                            Text(t, style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    opt(Ic.Eye, "فتح (استعراض المحتوى)") { dismiss(); Ctl.push(Overlay.Zip(ask.path)) }
                    opt(Ic.Download, "فك هنا") {
                        dismiss()
                        val parent = Incoming.destParent(Ctl.app, ask) ?: ask.parentFile
                        if (parent != null) run(ask, parent, null)
                    }
                    opt(Ic.Folder, "فك في مكان تاني…") { picking = true }
                    opt(Ic.Lock, "عمل نسخة بباسورد") { dismiss(); protectFor = ask }
                }
            },
            confirmButton = { TextButton(dismiss) { Text("إلغاء") } }
        )
    }

    if (ask != null && picking) {
        val primary = remember { Environment.getExternalStorageDirectory() }
        var cur by remember(ask) { mutableStateOf(Incoming.destParent(Ctl.app, ask) ?: primary) }
        val folders = remember(cur) {
            FS.list(cur, false).filter { it.isDir }.sortedBy { it.file.name.lowercase() }.map { it.file }
        }
        val close = { picking = false; Ctl.arcAsk = null }
        AlertDialog(
            onDismissRequest = { picking = false }, shape = RoundedCornerShape(28.dp),
            title = { Text("فك في…", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    Text(
                        "التخزين" + cur.path.removePrefix(primary.path),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge, color = cs.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        if (cur.path != primary.path && cur.parentFile != null) item {
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { cur = cur.parentFile ?: cur }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Ic.Up, null, Modifier.size(22.dp)); Spacer(Modifier.size(12.dp)); Text("..  المجلد الأعلى")
                            }
                        }
                        items(folders, key = { it.path }) { fo ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { cur = fo }.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Ic.Folder, null, Modifier.size(22.dp), tint = cs.primary)
                                Spacer(Modifier.size(12.dp))
                                Text(fo.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (folders.isEmpty()) item {
                            Text("مفيش مجلدات هنا", Modifier.padding(12.dp), color = cs.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ val t = cur; close(); run(ask, t, null) }) { Text("فك هنا") } },
            dismissButton = { TextButton({ picking = false }) { Text("رجوع") } }
        )
    }

    pwdJob?.let { (a, parent, wrong) ->
        var t by remember(a, wrong) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pwdJob = null }, shape = RoundedCornerShape(28.dp),
            title = { Text(if (wrong) "الباسورد غلط" else "الأرشيف محمي بباسورد", style = MaterialTheme.typography.titleLarge) },
            text = {
                TextField(
                    t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                    placeholder = { Text("اكتب الباسورد") },
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = { TextButton({ pwdJob = null; run(a, parent, t) }) { Text("فك") } },
            dismissButton = { TextButton({ pwdJob = null }) { Text("إلغاء") } }
        )
    }

    protectFor?.let { a ->
        var t by remember(a) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { protectFor = null }, shape = RoundedCornerShape(28.dp),
            title = { Text("نسخة بباسورد", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("هتتعمل نسخة ZIP جديدة مشفّرة AES-256 والأصلي هيفضل زي ما هو.", style = MaterialTheme.typography.bodyMedium)
                    TextField(
                        t, { t = it }, singleLine = true, shape = RoundedCornerShape(20.dp), colors = fieldColors(),
                        placeholder = { Text("الباسورد") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton({ if (t.isNotEmpty()) { protectFor = null; Ctl.protectArchive(a, t) } }) { Text("إنشاء") }
            },
            dismissButton = { TextButton({ protectFor = null }) { Text("إلغاء") } }
        )
    }
}
