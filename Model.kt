package com.next.files

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File

enum class Kind { FOLDER, IMAGE, VIDEO, AUDIO, DOC, TEXT, APK, ARCHIVE, OTHER }

enum class CatKind(val title: String) {
    IMAGES("الصور"), VIDEOS("الفيديو"), AUDIO("الصوتيات"), DOCS("المستندات"),
    APKS("التطبيقات"), ARCHIVES("المضغوطات"), RECENT("الأحدث"), LARGE("الملفات الكبيرة")
}

class FileItem(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val kind: Kind,
    val count: Int
) {
    val name: String get() = file.name
    val ext: String get() = file.extension.lowercase()
}

class Vol(val name: String, val dir: File, val total: Long, val free: Long)

class TrashEntry(
    val id: String,
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val date: Long
)

sealed interface Overlay {
    data class Collection(val kind: CatKind) : Overlay
    data object Favorites : Overlay
    data object Trash : Overlay
    data class Editor(val path: String) : Overlay
    data class Images(val paths: List<String>, val index: Int) : Overlay
    data class Zip(val path: String) : Overlay
    data class AndroidDir(val sub: String) : Overlay
    data object Storage : Overlay
    data class Clean(val key: String) : Overlay
    data class Pdf(val path: String) : Overlay
    data class Media(val path: String) : Overlay
    data object Wifi : Overlay
    data object Safe : Overlay
}

object Prefs {
    private var sp: SharedPreferences? = null

    var theme by mutableIntStateOf(0)          // 0 تلقائي، 1 فاتح، 2 داكن
    var amoled by mutableStateOf(false)
    var accent by mutableIntStateOf(-1)        // -1 ديناميكي/افتراضي
    var showHidden by mutableStateOf(false)
    var grid by mutableStateOf(false)
    var viewSize by mutableIntStateOf(1)       // 0 صغير، 1 متوسط، 2 كبير
    var sortBy by mutableIntStateOf(0)         // 0 اسم، 1 حجم، 2 تاريخ، 3 نوع
    var sortAsc by mutableStateOf(true)
    var useTrash by mutableStateOf(true)
    var confirmDelete by mutableStateOf(true)
    val favorites = mutableStateListOf<String>()
    var trashDays by mutableIntStateOf(0)      // 0 أبدًا، أو عدد الأيام
    var fontIdx by mutableIntStateOf(1)        // 0 صغير، 1 عادي، 2 كبير، 3 أكبر
    val folderSort = mutableStateMapOf<String, Int>()   // مسار المجلد -> ترتيب*2 + (تصاعدي؟1:0)

    fun fontScale(): Float = listOf(0.85f, 1f, 1.15f, 1.3f)[fontIdx.coerceIn(0, 3)]

    fun sortFor(dir: File): Pair<Int, Boolean> {
        val v = folderSort[dir.path] ?: return sortBy to sortAsc
        return (v shr 1) to ((v and 1) == 1)
    }

    fun setSort(dir: File, by: Int, asc: Boolean) {
        folderSort[dir.path] = by * 2 + (if (asc) 1 else 0)
        save()
    }

    fun clearSort(dir: File) {
        folderSort.remove(dir.path)
        save()
    }

    fun sortAllLike(by: Int, asc: Boolean) {
        sortBy = by
        sortAsc = asc
        folderSort.clear()
        save()
    }

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("next_files", Context.MODE_PRIVATE)
        sp = p
        theme = p.getInt("theme", 0)
        amoled = p.getBoolean("amoled", false)
        accent = p.getInt("accent", -1)
        showHidden = p.getBoolean("hidden", false)
        grid = p.getBoolean("grid", false)
        viewSize = p.getInt("viewSize", 1)
        sortBy = p.getInt("sortBy", 0)
        sortAsc = p.getBoolean("sortAsc", true)
        useTrash = p.getBoolean("trash", true)
        confirmDelete = p.getBoolean("confirm", true)
        trashDays = p.getInt("trashDays", 0)
        fontIdx = p.getInt("fontIdx", 1)
        folderSort.clear()
        try {
            val j = JSONObject(p.getString("fsort", "{}") ?: "{}")
            j.keys().forEach { k -> folderSort[k] = j.getInt(k) }
        } catch (e: Exception) {
        }
        favorites.clear()
        favorites.addAll((p.getStringSet("fav", emptySet()) ?: emptySet()).sorted())
    }

    fun save() {
        sp?.edit()
            ?.putInt("theme", theme)
            ?.putBoolean("amoled", amoled)
            ?.putInt("accent", accent)
            ?.putBoolean("hidden", showHidden)
            ?.putBoolean("grid", grid)
            ?.putInt("viewSize", viewSize)
            ?.putInt("sortBy", sortBy)
            ?.putBoolean("sortAsc", sortAsc)
            ?.putBoolean("trash", useTrash)
            ?.putBoolean("confirm", confirmDelete)
            ?.putInt("trashDays", trashDays)
            ?.putInt("fontIdx", fontIdx)
            ?.putString("fsort", JSONObject(folderSort.toMap()).toString())
            ?.putStringSet("fav", favorites.toSet())
            ?.apply()
    }

    fun toggleFav(path: String) {
        if (path in favorites) favorites.remove(path) else favorites.add(path)
        save()
    }
}

object Perm {
    var granted by mutableStateOf(false)

    fun check(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ctx.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    fun refresh(ctx: Context) {
        granted = check(ctx)
    }
}
