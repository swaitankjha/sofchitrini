package com.jhacode.chitrini.ui.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(context: Context) : ViewModel() {
    private val prefs = context.getSharedPreferences("chitrini_prefs", Context.MODE_PRIVATE)

    private val _appWallpaperUri = MutableStateFlow(prefs.getString("app_wallpaper_uri", ""))
    val appWallpaperUri = _appWallpaperUri.asStateFlow()

    private val _appStockColor = MutableStateFlow<Int?>(if (prefs.contains("app_stock_color")) prefs.getInt("app_stock_color", 0) else null)
    val appStockColor = _appStockColor.asStateFlow()

    private val _appBlur = MutableStateFlow(prefs.getBoolean("app_wallpaper_blur", false))
    val appBlur = _appBlur.asStateFlow()

    private val _chatWallpaperUri = MutableStateFlow(prefs.getString("chat_wallpaper_uri", ""))
    val chatWallpaperUri = _chatWallpaperUri.asStateFlow()

    private val _chatStockColor = MutableStateFlow<Int?>(if (prefs.contains("chat_stock_color")) prefs.getInt("chat_stock_color", 0) else null)
    val chatStockColor = _chatStockColor.asStateFlow()

    private val _chatBlur = MutableStateFlow(prefs.getBoolean("chat_wallpaper_blur", false))
    val chatBlur = _chatBlur.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "System") ?: "System")
    val themeMode = _themeMode.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "app_wallpaper_uri" -> _appWallpaperUri.value = p.getString(key, "")
            "app_stock_color" -> _appStockColor.value = if (p.contains(key)) p.getInt(key, 0) else null
            "app_wallpaper_blur" -> _appBlur.value = p.getBoolean(key, false)
            "chat_wallpaper_uri" -> _chatWallpaperUri.value = p.getString(key, "")
            "chat_stock_color" -> _chatStockColor.value = if (p.contains(key)) p.getInt(key, 0) else null
            "chat_wallpaper_blur" -> _chatBlur.value = p.getBoolean(key, false)
            "theme_mode" -> _themeMode.value = p.getString(key, "System") ?: "System"
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}
