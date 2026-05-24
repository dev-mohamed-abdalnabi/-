package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FONT_SIZE = "font_size_scale"
        private const val KEY_THEME = "app_theme_mode"
        private const val KEY_CATEGORIES = "categories_list_json"
        private const val KEY_CUSTOM_API_KEY = "custom_gemini_api_key"
    }

    fun getFontSizeScale(): String {
        return prefs.getString(KEY_FONT_SIZE, "normal") ?: "normal"
    }

    fun setFontSizeScale(scale: String) {
        prefs.edit().putString(KEY_FONT_SIZE, scale).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString(KEY_THEME, "system") ?: "system"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME, mode).apply()
    }

    fun getCustomApiKey(): String {
        return prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
    }

    fun setCustomApiKey(key: String) {
        prefs.edit().putString(KEY_CUSTOM_API_KEY, key).apply()
    }

    fun getCategoriesList(): List<String> {
        val jsonStr = prefs.getString(KEY_CATEGORIES, null)
        if (jsonStr == null) {
            val defaultList = listOf("عام", "عمل", "دراسة", "شخصي", "أفكار")
            saveCategoriesList(defaultList)
            return defaultList
        }
        return try {
            Json.decodeFromString(jsonStr)
        } catch (e: Exception) {
            listOf("عام", "عمل", "دراسة", "شخصي", "أفكار")
        }
    }

    fun saveCategoriesList(list: List<String>) {
        val jsonStr = Json.encodeToString(list)
        prefs.edit().putString(KEY_CATEGORIES, jsonStr).apply()
    }
}
