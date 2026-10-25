package com.next.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File

/** شريط مسار: كل جزء بتدوس عليه تروحله */
@Composable
fun Crumbs(dir: File, primary: File, vols: List<Vol>) {
    val cs = MaterialTheme.colorScheme
    val rootVol = vols.firstOrNull { dir.path == it.dir.path || dir.path.startsWith(it.dir.path + "/") }
    val root = rootVol?.dir ?: primary
    val rootName = if (root.path == primary.path) "التخزين" else (rootVol?.name ?: root.name)
    val rel = dir.path.removePrefix(root.path).trim('/')
    val parts = if (rel.isEmpty()) emptyList() else rel.split('/')
    val scroll = rememberScrollState()
    LaunchedEffect(dir.path, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll), verticalAlignment = Alignment.CenterVertically) {
        Text(
            rootName, Modifier.clip(RoundedCornerShape(10.dp)).clickable { Ctl.browseDir = root }.padding(horizontal = 6.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (parts.isEmpty()) cs.primary else cs.onSurfaceVariant,
            fontWeight = if (parts.isEmpty()) FontWeight.Bold else null
        )
        var acc = root
        parts.forEachIndexed { i, p ->
            acc = File(acc, p)
            val target = acc
            val last = i == parts.lastIndex
            Icon(Ic.ChevL, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
            Text(
                p, Modifier.clip(RoundedCornerShape(10.dp)).clickable { Ctl.browseDir = target }.padding(horizontal = 6.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (last) cs.primary else cs.onSurfaceVariant,
                fontWeight = if (last) FontWeight.Bold else null
            )
        }
    }
}

/** تابات التصفح - بتظهر لما يبقى فيه أكتر من تاب */
@Composable
fun TabStrip() {
    if (Ctl.tabs.size <= 1) return
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Ctl.tabs.forEachIndexed { i, f ->
            val on = i == Ctl.tabIdx
            Row(
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(if (on) cs.primaryContainer else cs.surfaceContainerHigh)
                    .clickable { Ctl.tabIdx = i }
                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    f?.name?.ifEmpty { "التخزين" } ?: "التخزين", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) cs.onPrimaryContainer else cs.onSurface
                )
                Icon(Ic.Close, "قفل التاب", Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).clickable { Ctl.closeTab(i) },
                    tint = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant)
            }
        }
        Icon(Ic.Plus, "تاب جديد", Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable { Ctl.newTab() }.padding(6.dp))
    }
}
