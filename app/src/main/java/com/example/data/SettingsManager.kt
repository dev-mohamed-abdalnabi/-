package com.example.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("next_notes_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_THEME_MODE = "theme_mode" // 0: Light, 1: Dark, 2: AMOLED, 3: System
        const val KEY_GEMINI_API_KEY = "gemini_api_key"
        const val KEY_FONT_SIZE = "font_size" // 1.0f (عادي), 1.2f (كبير), 1.4f (كبير جداً)
        const val KEY_AUTO_SAVE = "auto_save"
        const val KEY_SORT_ORDER = "sort_order" // "updated_at" or "created_at" or "title"
        const val KEY_CUSTOM_CATEGORIES = "custom_categories"
    }

    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 3)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GEMINI_API_KEY, value).apply()

    var fontSize: Float
        get() = prefs.getFloat(KEY_FONT_SIZE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SIZE, value).apply()

    var isAutoSaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SAVE, value).apply()

    var sortOrder: String
        get() = prefs.getString(KEY_SORT_ORDER, "updated_at") ?: "updated_at"
        set(value) = prefs.edit().putString(KEY_SORT_ORDER, value).apply()

    var customCategories: String
        get() = prefs.getString(KEY_CUSTOM_CATEGORIES, "عام") ?: "عام"
        set(value) = prefs.edit().putString(KEY_CUSTOM_CATEGORIES, value).apply()

    fun getCategoriesList(): List<String> {
        val cats = customCategories
        if (cats.isBlank()) return listOf("عام")
        return cats.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun addCategory(category: String) {
        val trimmed = category.trim()
        if (trimmed.isBlank() || trimmed.contains(",")) return
        val current = getCategoriesList().toMutableList()
        if (!current.contains(trimmed)) {
            current.add(trimmed)
            customCategories = current.joinToString(",")
        }
    }

    fun deleteCategory(category: String) {
        val current = getCategoriesList().toMutableList()
        if (current.size > 1) { // Always keep at least one category
            current.remove(category)
            customCategories = current.joinToString(",")
        }
    }
}
