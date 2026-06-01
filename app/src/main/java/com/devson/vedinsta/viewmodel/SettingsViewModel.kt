package com.devson.vedinsta.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import com.devson.vedinsta.ui.theme.AppThemePalette
import com.devson.vedinsta.ui.theme.AppThemePaletteHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)

    private val _isDarkTheme = MutableStateFlow<Boolean?>(
        if (prefs.contains("dark_theme")) prefs.getBoolean("dark_theme", false) else null
    )
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private val _isAmoledTheme = MutableStateFlow(
        prefs.getBoolean("is_amoled_theme", false)
    )
    val isAmoledTheme: StateFlow<Boolean> = _isAmoledTheme.asStateFlow()

    private val _dynamicColor = MutableStateFlow(
        prefs.getBoolean("dynamic_color", false)
    )
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _selectedPalette = MutableStateFlow(
        AppThemePaletteHelper.fromKey(prefs.getString("selected_palette", AppThemePalette.BLUE.name))
    )
    val selectedPalette: StateFlow<AppThemePalette> = _selectedPalette.asStateFlow()

    private val _isNavBarTransparent = MutableStateFlow(
        prefs.getBoolean("nav_bar_transparent", true)
    )
    val isNavBarTransparent: StateFlow<Boolean> = _isNavBarTransparent.asStateFlow()

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
        _isDarkTheme.value = isDark
    }

    fun resetDarkTheme() {
        prefs.edit().remove("dark_theme").apply()
        _isDarkTheme.value = null
    }

    fun setAmoledTheme(enabled: Boolean) {
        prefs.edit().putBoolean("is_amoled_theme", enabled).apply()
        _isAmoledTheme.value = enabled
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean("dynamic_color", enabled).apply()
        _dynamicColor.value = enabled
    }

    fun setSelectedPalette(palette: AppThemePalette) {
        prefs.edit().putString("selected_palette", palette.name).apply()
        _selectedPalette.value = palette
    }

    fun setNavBarTransparent(transparent: Boolean) {
        prefs.edit().putBoolean("nav_bar_transparent", transparent).apply()
        _isNavBarTransparent.value = transparent
    }
}
