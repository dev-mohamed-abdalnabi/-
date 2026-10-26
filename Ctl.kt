package com.next.files

import android.content.Context
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var snackbarJob: Job? = null
    private var snackbarTimer: Job? = null

    var tab by mutableIntStateOf(0)
    val stack = mutableStateListOf<Overlay>()
    val clip = mutableStateListOf<File>()
    var clipCut by mutableStateOf(false)
    var busy by mutableStateOf<String?>(null)
    var rev by mutableIntStateOf(0)
    // ---- تابات التصفح ----
    val tabs = mutableStateListOf<File?>(null)
    var tabIdx by mutableIntStateOf(0)
    var browseDir: File?
        get() = tabs.getOrNull(tabIdx)
        set(v) {
            if (tabs.isEmpty()) tabs.add(v) else tabs[tabIdx.coerceIn(0, tabs.lastIndex)] = v
        }

    fun newTab(f: File? = null) {
        tabs.add(f ?: browseDir)
        tabIdx = tabs.lastIndex
        tab = 0
        stack.clear()
    }

    fun closeTab(i: Int) {
        if (tabs.size <= 1 || i !in tabs.indices) return
        tabs.removeAt(i)
        if (i < tabIdx) tabIdx-- else if (tabIdx > tabs.lastIndex) tabIdx = tabs.lastIndex
    }
    var pendingUri: Uri? = null

    /** فتح ملف جاي من تطبيق تاني (فتح بواسطة Next) */
    fun openExternal(uri: Uri) {
        task("جاري فتح الملف…", { Incoming.resolve(app, uri) }, fail = { e ->
            toast(e.message ?: "تعذر فتح الملف"); true
        }) { r -> showExternal(r) }
    }

    private fun showExternal(r: Incoming.Result) {
        val f = r.file
        val k = FS.kindOf(f.extension.lowercase())
        val internal = when (k) {
            Kind.IMAGE -> true
            Kind.ARCHIVE -> Arc.supported(f)
            Kind.TEXT -> f.length() <= 1_000_000L
            Kind.VIDEO, Kind.AUDIO -> true
            Kind.DOC -> f.extension.equals("pdf", true)
            else -> false
        }
        stack.clear()
        tab = 0
        if (f.isDirectory) {
            openDir(f)
        } else if (internal) {
            if (r.temp && k == Kind.TEXT) toast("ده نسخة مؤقتة - التعديل مش هيتحفظ في الملف الأصلي")
            open(f, listOf(f))
        } else if (r.temp) {
            task("جاري الحفظ…", {
                val dest = Incoming.saveDir().also { it.mkdirs() }
                val out = FS.unique(dest, f.name, false)
                f.copyTo(out)
                f.parentFile?.deleteRecursively()
                out
            }) { out ->
                FS.scan(app, listOf(out.path))
                openDir(out.parentFile ?: out)
                toast("اتحفظ ${out.name} في Download")
            }
        } else {
            openDir(f.parentFile ?: f)
            toast(f.name)
        }
    }

    /** استلام ملفات من "مشاركة إلى Next": بتتحط في الحافظة (قص) وتلصقها في أي مجلد */
    fun receive(uris: List<Uri>) {
        if (uris.isEmpty()) { toast("مفيش ملفات في المشاركة"); return }
        task("جاري استلام الملفات…", { uris.map { Incoming.copy(app, it) } }, fail = { e ->
            toast(e.message ?: "تعذر استلام الملفات"); true
        }) { files ->
            stack.clear()
            tab = 0
            setClip(files, true)
        }
    }

    var requestAccess: () -> Unit = {}
    var pickSaf: (String) -> Unit = {}

    /** Feedback breve, con duración ajustada حسب أهمية العملية. */
    private fun feedbackDuration(m: String): Long = when {
        m.contains("تعذر") || m.contains("فشل") || m.contains("مشكلة") || m.contains("مش مظبوط") || m.contains("باسورد") -> 3200L
        m.contains("حذف") || m.contains("اتحذف") || m.contains("اتنقل") -> 2600L
        m.contains("اتنسخ") || m.contains("اتقص") || m.contains("لصق") || m.contains("نسخ") || m.contains("نقل") -> 1900L
        m.contains("تم") || m.contains("اتحفظ") || m.contains("اتضاف") || m.contains("اتسترجع") -> 2100L
        else -> 2400L
    }

    private fun replaceSnackbar() {
        snackbarTimer?.cancel()
        snackbarJob?.cancel()
        snack.currentSnackbarData?.dismiss()
    }

    fun toast(m: String) {
        replaceSnackbar()
        snackbarJob = scope.launch {
            snack.showSnackbar(m, duration = SnackbarDuration.Indefinite)
        }
        snackbarTimer = scope.launch {
            delay(feedbackDuration(m))
            snack.currentSnackbarData?.dismiss()
        }
    }

    fun toastUndo(m: String, label: String, onClick: () -> Unit) {
        replaceSnackbar()
        snackbarJob = scope.launch {
            val r = snack.showSnackbar(m, actionLabel = label, duration = SnackbarDuration.Indefinite)
            if (r == SnackbarResult.ActionPerformed) onClick()
        }
        snackbarTimer = scope.launch {
            delay(6500L)
            snack.currentSnackbarData?.dismiss()
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
        tab = 0
        stack.clear()
    }

    fun open(f: File, siblings: List<File> = emptyList()) {
        if (SafFs.usesSaf(f)) {
            task("جاري التحضير…", { Prog.begin(0L); SafFs.toCache(f) }) { c -> open(c, listOf(c)) }
            return
        }
        when (FS.kindOf(f.extension.lowercase())) {
            Kind.IMAGE -> {
                val imgs = siblings.filter { FS.kindOf(it.extension.lowercase()) == Kind.IMAGE }.map { it.path }
                val list = if (imgs.contains(f.path)) imgs else listOf(f.path)
                push(Overlay.Images(list, list.indexOf(f.path).coerceAtLeast(0)))
            }
            Kind.ARCHIVE -> if (Arc.supported(f)) arcAsk = f else FS.openFile(app, f)
            Kind.TEXT -> if (f.length() <= 1_000_000L) push(Overlay.Editor(f.path)) else FS.openFile(app, f)
            Kind.VIDEO, Kind.AUDIO -> push(Overlay.Media(f.path))
            Kind.DOC -> if (f.extension.equals("pdf", true)) push(Overlay.Pdf(f.path)) else FS.openFile(app, f)
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

    fun <T> task(msg: String, work: () -> T, fail: (Exception) -> Boolean = { false }, done: (T) -> Unit = {}) {
        scope.launch {
            Prog.reset()
            busy = msg
            try {
                val r = withContext(Dispatchers.IO) { work() }
                done(r)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!fail(e)) toast(e.message ?: "حصلت مشكلة")
            } finally {
                busy = null
                Prog.reset()
                bump()
            }
        }
    }

    fun paste(dest: File) {
        val items = clip.toList()
        val cut = clipCut
        if (items.isEmpty()) return
        task(if (cut) "جاري النقل…" else "جاري النسخ…", {
            Prog.begin(if (cut) 0L else items.sumOf { FS.sizeOf(it) })
            val touched = ArrayList<String>()
            val destSaf = SafFs.usesSaf(dest)
            for (f in items) {
                val srcSaf = SafFs.usesSaf(f)
                if (!srcSaf && !destSaf) {
                    val r = if (cut) FS.move(f, dest) else FS.copy(f, dest)
                    touched.add(r.path)
                    if (cut) touched.add(f.path)
                } else if (srcSaf && !destSaf) {
                    touched.add(SafFs.copyOut(f, dest).path)
                    if (cut && !SafFs.delete(f)) throw IOException("تعذر حذف الأصل ${f.name}")
                } else if (!srcSaf) {
                    SafFs.copyIn(f, dest)
                    if (cut) { f.deleteRecursively(); touched.add(f.path) }
                } else {
                    val tmp = File(app.cacheDir, "safpaste/" + System.nanoTime()).also { it.mkdirs() }
                    try {
                        SafFs.copyIn(SafFs.copyOut(f, tmp), dest)
                    } finally {
                        tmp.deleteRecursively()
                    }
                    if (cut && !SafFs.delete(f)) throw IOException("تعذر حذف الأصل ${f.name}")
                }
            }
            touched
        }) { touched ->
            clip.clear()
            FS.scan(app, touched)
            toast(if (cut) "تم نقل ${items.size} عنصر" else "تم نسخ ${items.size} عنصر")
        }
    }

    /** مشاركة: ملفات Android/data بتتنسخ مؤقتًا للكاش الأول */
    fun shareFiles(files: List<File>) {
        if (files.none { SafFs.usesSaf(it) }) { FS.share(app, files); return }
        task("جاري التحضير…", {
            Prog.begin(0L)
            files.mapNotNull { f -> if (SafFs.usesSaf(f)) (if (SafFs.isDir(f)) null else SafFs.toCache(f)) else f }
        }) { fs -> FS.share(app, fs) }
    }

    fun openWith(f: File) {
        if (!SafFs.usesSaf(f)) { FS.openFile(app, f, true); return }
        task("جاري التحضير…", { Prog.begin(0L); SafFs.toCache(f) }) { c -> FS.openFile(app, c, true) }
    }

    fun askDelete(files: List<File>, dlg: DlgState) {
        if (Prefs.confirmDelete) dlg.del = files else delete(files)
    }

    fun delete(files: List<File>) {
        val saf = files.filter { SafFs.usesSaf(it) }
        val norm = files.filter { f -> saf.none { it.path == f.path } }
        val permanent = !Prefs.useTrash || norm.isEmpty()
        task(if (permanent) "جاري الحذف…" else "جاري النقل للسلة…", {
            val paths = files.map { it.path }
            val ids = ArrayList<String>()
            for (f in saf) if (!SafFs.delete(f)) throw IOException("تعذر حذف ${f.name}")
            for (f in norm) {
                if (!Prefs.useTrash) {
                    if (!f.deleteRecursively()) throw IOException("تعذر حذف ${f.name}")
                } else {
                    ids.add(TrashBin.put(f))
                }
            }
            paths to ids
        }) { (paths, ids) ->
            FS.scan(app, norm.map { it.path })
            paths.forEach { Prefs.favorites.remove(it) }
            Prefs.save()
            if (permanent) toast("تم الحذف")
            else toastUndo("اتنقل ${paths.size} عنصر لسلة المحذوفات", "تراجع") { undoDelete(ids) }
        }
    }

    fun undoDelete(ids: List<String>) {
        task("جاري الاسترجاع…", {
            TrashBin.list().filter { it.id in ids }.map { TrashBin.restore(it).path }
        }) { paths ->
            FS.scan(app, paths)
            toast("اتسترجع ${paths.size} عنصر")
        }
    }

    fun rename(f: File, name: String) {
        val n = name.trim()
        if (n.isEmpty() || n.contains('/')) {
            toast("الاسم مش مظبوط")
            return
        }
        if (SafFs.usesSaf(f)) {
            if (n != f.name && SafFs.exists(File(f.parentFile, n))) { toast("فيه ملف بنفس الاسم"); return }
            task("جاري التغيير…", { SafFs.rename(f, n) }) { ok -> toast(if (ok) "تم تغيير الاسم" else "تعذر تغيير الاسم") }
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
        if (SafFs.usesSaf(dir)) {
            if (SafFs.exists(File(dir, n))) { toast("فيه عنصر بنفس الاسم"); return }
            task("جاري الإنشاء…", { SafFs.mkdir(dir, n) }) { ok -> toast(if (ok) "تم إنشاء المجلد" else "تعذر إنشاء المجلد") }
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
        if (SafFs.usesSaf(dir)) {
            if (SafFs.exists(File(dir, n))) { toast("فيه عنصر بنفس الاسم"); return }
            task("جاري الإنشاء…", { SafFs.newFile(dir, n) }) { ok -> toast(if (ok) "تم إنشاء الملف" else "تعذر إنشاء الملف") }
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

    fun zip(files: List<File>, name: String, pwd: String = "", fmt: Int = 0) {
        if (files.any { SafFs.usesSaf(it) }) { toast("الضغط مش متاح جوه Android/data - انسخ الملفات لمكان تاني الأول"); return }
        val parent = files.firstOrNull()?.parentFile ?: return
        var n = name.trim().ifEmpty { "archive" }
        val ext = listOf(".zip", ".7z", ".tar.gz")[fmt.coerceIn(0, 2)]
        if (!n.lowercase().endsWith(ext)) n += ext
        task("جاري الضغط…", {
            Prog.begin(files.sumOf { FS.sizeOf(it) })
            val out = FS.unique(parent, n, false)
            try {
                when {
                    fmt != 0 -> Arc.createArchive(files, out, if (fmt == 1) "7z" else "tgz")
                    pwd.isEmpty() -> FS.zip(files, out)
                    else -> Arc.createZip(files, out, pwd)
                }
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

    /** فك أرشيف كامل جنب الملف. لو محمي بباسورد بيفتح شاشة الاستعراض */
    fun unzip(f: File) {
        val parent = Incoming.destParent(app, f) ?: return
        task("جاري فك الضغط…", {
            val dest = FS.unique(parent, f.name.substringBefore('.').ifEmpty { f.nameWithoutExtension }, true)
            try {
                Arc.extract(f, null, dest, null)
            } catch (e: Exception) {
                dest.deleteRecursively()
                throw e
            }
            dest.path
        }, fail = { e ->
            if (e is Arc.NeedPassword) { push(Overlay.Zip(f.path)); toast("الأرشيف محمي بباسورد - اكتبه علشان يتفك"); true } else false
        }) { p ->
            FS.scan(app, listOf(p))
            toast("تم فك الضغط في ${File(p).name}")
        }
    }

    /** الأرشيف اللي المستخدم داس عليه - بيظهر له اختيارات (فتح / فك هنا / فك في مكان تاني / باسورد) */
    var arcAsk by mutableStateOf<File?>(null)

    /** فك أرشيف كامل جوه فولدر [parent] (جديد باسم الأرشيف). لو محمي بباسورد بينادي onNeedPwd */
    fun extractTo(f: File, parent: File, pwd: String?, onNeedPwd: (Boolean) -> Unit) {
        task("جاري فك الضغط…", {
            val dest = FS.unique(parent, f.name.substringBefore('.').ifEmpty { f.nameWithoutExtension }, true)
            try {
                Arc.extract(f, null, dest, pwd)
            } catch (e: Exception) {
                dest.deleteRecursively()
                throw e
            }
            dest.path
        }, fail = { e ->
            if (e is Arc.NeedPassword) { onNeedPwd(e.wrong); true } else false
        }) { p ->
            FS.scan(app, listOf(p))
            toast("تم فك الضغط في ${File(p).name}")
        }
    }

    /** عمل نسخة ZIP جديدة من الأرشيف بباسورد AES-256 (الأصلي بيفضل زي ما هو) */
    fun protectArchive(f: File, pwd: String) {
        task("جاري حماية الأرشيف…", {
            val tmp = File(app.cacheDir, "repack/" + System.nanoTime())
            try {
                tmp.mkdirs()
                Arc.extract(f, null, tmp, null)
                val items = tmp.listFiles()?.toList().orEmpty()
                val parent = Incoming.destParent(app, f) ?: f.parentFile ?: tmp
                val out = FS.unique(parent, f.nameWithoutExtension.removeSuffix(".tar") + "_protected.zip", false)
                Prog.begin(items.sumOf { FS.sizeOf(it) })
                try {
                    Arc.createZip(items, out, pwd)
                } catch (e: Exception) {
                    out.delete()
                    throw e
                }
                out.path
            } finally {
                tmp.deleteRecursively()
            }
        }, fail = { e ->
            if (e is Arc.NeedPassword) { toast("الأرشيف ده محمي بباسورد بالفعل"); true } else false
        }) { p ->
            FS.scan(app, listOf(p))
            toast("تم إنشاء ${File(p).name} بباسورد")
        }
    }

    fun unzipSelected(zip: File, names: List<String>?, dest: File, pwd: String?, onNeedPwd: (Boolean) -> Unit) {
        task("جاري فك الضغط…", {
            val n = Arc.extract(zip, names, dest, pwd)
            n to dest.path
        }, fail = { e ->
            if (e is Arc.NeedPassword) { dest.deleteRecursively(); onNeedPwd(e.wrong); true } else false
        }) { (n, p) ->
            FS.scan(app, listOf(p))
            toast("اتفك $n ملف في ${File(p).name}")
        }
    }

    fun batchRename(files: List<File>, pattern: String, start: Int, newExt: String?) {
        if (files.any { SafFs.usesSaf(it) }) { toast("إعادة التسمية الجماعية مش متاحة جوه Android/data"); return }
        if (files.isEmpty() || pattern.isBlank()) { toast("اكتب نمط الاسم"); return }
        val width = pattern.count { it == '#' }.coerceAtLeast(1)
        val plan = files.mapIndexed { i, f ->
            val num = (start + i).toString().padStart(width, '0')
            val base = if (pattern.contains('#')) pattern.replace(Regex("#+"), num) else pattern + num
            val ext = if (!f.isFile) ""
                else if (newExt != null) "." + newExt
                else if (f.extension.isNotEmpty()) "." + f.extension else ""
            f to File(f.parentFile, base.trim() + ext)
        }
        val srcPaths = files.map { it.path }.toSet()
        if (plan.map { it.second.path }.toSet().size != plan.size) { toast("الأسماء الجديدة فيها تكرار"); return }
        if (plan.any { it.second.exists() && it.second.path !in srcPaths }) { toast("فيه ملف بنفس الاسم الجديد"); return }
        task("جاري إعادة التسمية…", {
            val tmp = plan.mapIndexed { i, (f, _) ->
                val t = File(f.parentFile, ".nf_tmp_$i")
                if (!f.renameTo(t)) throw IOException("تعذر تغيير اسم ${f.name}")
                t
            }
            plan.forEachIndexed { i, (_, to) -> tmp[i].renameTo(to) }
            plan.flatMap { listOf(it.first.path, it.second.path) }
        }) { paths ->
            FS.scan(app, paths)
            toast("تمت إعادة تسمية ${plan.size} عنصر")
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

/** تقدم العمليات الطويلة + إلغاء */
object Prog {
    @Volatile var cancel = false
    @Volatile var done = 0L
    @Volatile var total = 0L
    private var lastPct = -1
    var frac by mutableFloatStateOf(-1f)
    var cancellable by mutableStateOf(false)

    fun reset() {
        cancel = false; done = 0; total = 0; lastPct = -1
        frac = -1f
        cancellable = false
    }

    fun begin(t: Long) {
        cancel = false; done = 0; total = t; lastPct = -1
        frac = if (t > 0) 0f else -1f
        cancellable = true
    }

    fun add(n: Long) {
        if (cancel) throw IOException("اتلغت العملية")
        done += n
        if (total > 0) {
            val p = (done * 100 / total).toInt()
            if (p != lastPct) { lastPct = p; frac = (done.toFloat() / total).coerceIn(0f, 1f) }
        }
    }
}
