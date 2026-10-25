package com.next.files

import android.content.Context
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object Ctl {
    lateinit var app: Context
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val snack = SnackbarHostState()

    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Overlay>()
    val clip = mutableStateListOf<File>()
    var clipCut by mutableStateOf(false)
    var busy by mutableStateOf<String?>(null)
    var rev by mutableIntStateOf(0)
    var browseDir by mutableStateOf<File?>(null)
    var requestAccess: () -> Unit = {}

    fun toast(m: String) {
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            snack.showSnackbar(m)
        }
    }

    fun bump() {
        rev++
    }

    fun push(o: Overlay) {
        stack.add(o)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    fun openDir(f: File) {
        browseDir = f
        tab = 1
        stack.clear()
    }

    fun open(f: File, siblings: List<File> = emptyList()) {
        when (FS.kindOf(f.extension.lowercase())) {
            Kind.IMAGE -> {
                val imgs = siblings.filter { FS.kindOf(it.extension.lowercase()) == Kind.IMAGE }.map { it.path }
                val list = if (imgs.contains(f.path)) imgs else listOf(f.path)
                push(Overlay.Images(list, list.indexOf(f.path).coerceAtLeast(0)))
            }
            Kind.TEXT -> if (f.length() <= 1_000_000L) push(Overlay.Editor(f.path)) else FS.openFile(app, f)
            else -> FS.openFile(app, f)
        }
    }

    fun setClip(files: List<File>, cut: Boolean) {
        clip.clear()
        clip.addAll(files)
        clipCut = cut
        toast(if (cut) "اتقص ${files.size} عنصر - روح للمكان واضغط لصق" else "اتنسخ ${files.size} عنصر - روح للمكان واضغط لصق")
    }

    fun clearClip() {
        clip.clear()
    }

    fun <T> task(msg: String, work: () -> T, done: (T) -> Unit = {}) {
        scope.launch {
            busy = msg
            try {
                val r = withContext(Dispatchers.IO) { work() }
                done(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "حصلت مشكلة")
            } finally {
                busy = null
                bump()
            }
        }
    }

    fun paste(dest: File) {
        val items = clip.toList()
        val cut = clipCut
        if (items.isEmpty()) return
        task(if (cut) "جاري النقل…" else "جاري النسخ…", {
            val touched = ArrayList<String>()
            for (f in items) {
                val r = if (cut) FS.move(f, dest) else FS.copy(f, dest)
                touched.add(r.path)
                if (cut) touched.add(f.path)
            }
            touched
        }) { touched ->
            if (cut) clip.clear()
            FS.scan(app, touched)
            toast(if (cut) "تم نقل ${items.size} عنصر" else "تم نسخ ${items.size} عنصر")
        }
    }

    fun askDelete(files: List<File>, dlg: DlgState) {
        if (Prefs.confirmDelete) dlg.del = files else delete(files)
    }

    fun delete(files: List<File>) {
        val permanent = !Prefs.useTrash
        task(if (permanent) "جاري الحذف…" else "جاري النقل للسلة…", {
            val paths = files.map { it.path }
            for (f in files) {
                if (permanent) {
                    if (!f.deleteRecursively()) throw IOException("تعذر حذف ${f.name}")
                } else {
                    TrashBin.put(f)
                }
            }
            paths
        }) { paths ->
            FS.scan(app, paths)
            paths.forEach { Prefs.favorites.remove(it) }
            Prefs.save()
            toast(if (permanent) "تم الحذف" else "اتنقل لسلة المحذوفات")
        }
    }

    fun rename(f: File, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) {
            toast("الاسم مش مظبوط")
            return
        }
        val t = File(f.parentFile, n)
        if (t.exists() && t.path != f.path) {
            toast("فيه ملف بنفس الاسم")
            return
        }
        if (f.renameTo(t)) {
            FS.scan(app, listOf(f.path, t.path))
            val i = Prefs.favorites.indexOf(f.path)
            if (i >= 0) {
                Prefs.favorites[i] = t.path
                Prefs.save()
            }
            toast("تم تغيير الاسم")
        } else {
            toast("تعذر تغيير الاسم")
        }
        bump()
    }

    fun mkdir(dir: File, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) {
            toast("الاسم مش مظبوط")
            return
        }
        val t = File(dir, n)
        if (t.exists()) toast("فيه عنصر بنفس الاسم") else if (t.mkdirs()) toast("تم إنشاء المجلد") else toast("تعذر إنشاء المجلد")
        bump()
    }

    fun newFile(dir: File, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) {
            toast("الاسم مش مظبوط")
            return
        }
        val t = File(dir, n)
        try {
            if (t.exists()) toast("فيه عنصر بنفس الاسم") else if (t.createNewFile()) {
                FS.scan(app, listOf(t.path))
                toast("تم إنشاء الملف")
            } else toast("تعذر إنشاء الملف")
        } catch (e: Exception) {
            toast("تعذر إنشاء الملف")
        }
        bump()
    }

    fun zip(files: List<File>, name: String) {
        val parent = files.firstOrNull()?.parentFile ?: return
        var n = name.trim().ifEmpty { "archive" }
        if (!n.lowercase().endsWith(".zip")) n += ".zip"
        task("جاري الضغط…", {
            val out = FS.unique(parent, n, false)
            try {
                FS.zip(files, out)
            } catch (e: Exception) {
                out.delete()
                throw e
            }
            out.path
        }) { p ->
            FS.scan(app, listOf(p))
            toast("تم إنشاء ${File(p).name}")
        }
    }

    fun unzip(f: File) {
        val parent = f.parentFile ?: return
        task("جاري فك الضغط…", {
            val dest = FS.unique(parent, f.nameWithoutExtension, true)
            try {
                FS.unzip(f, dest)
            } catch (e: Exception) {
                dest.deleteRecursively()
                throw e
            }
            dest.path
        }) { p ->
            FS.scan(app, listOf(p))
            toast("تم فك الضغط في ${File(p).name}")
        }
    }

    fun restore(e: TrashEntry) {
        task("جاري الاسترجاع…", {
            TrashBin.restore(e).path
        }) { p ->
            FS.scan(app, listOf(p))
            toast("اتسترجع الملف")
        }
    }

    fun purge(e: TrashEntry) {
        task("جاري الحذف…", { TrashBin.purge(e) }) { toast("اتحذف نهائيًا") }
    }

    fun emptyTrash() {
        task("جاري تفريغ السلة…", { TrashBin.empty() }) { toast("السلة فاضية دلوقتي") }
    }
}
