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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
}

object Prefs {
    private var sp: SharedPreferences? = null

    var theme by mutableIntStateOf(0)          // 0 تلقائي، 1 فاتح، 2 داكن
    var amoled by mutableStateOf(false)
    var accent by mutableIntStateOf(-1)        // -1 ديناميكي/افتراضي
    var showHidden by mutableStateOf(false)
    var grid by mutableStateOf(false)
    var sortBy by mutableIntStateOf(0)         // 0 اسم، 1 حجم، 2 تاريخ، 3 نوع
    var sortAsc by mutableStateOf(true)
    var useTrash by mutableStateOf(true)
    var confirmDelete by mutableStateOf(true)
    val favorites = mutableStateListOf<String>()

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("next_files", Context.MODE_PRIVATE)
        sp = p
        theme = p.getInt("theme", 0)
        amoled = p.getBoolean("amoled", false)
        accent = p.getInt("accent", -1)
        showHidden = p.getBoolean("hidden", false)
        grid = p.getBoolean("grid", false)
        sortBy = p.getInt("sortBy", 0)
        sortAsc = p.getBoolean("sortAsc", true)
        useTrash = p.getBoolean("trash", true)
        confirmDelete = p.getBoolean("confirm", true)
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
            ?.putInt("sortBy", sortBy)
            ?.putBoolean("sortAsc", sortAsc)
            ?.putBoolean("trash", useTrash)
            ?.putBoolean("confirm", confirmDelete)
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
