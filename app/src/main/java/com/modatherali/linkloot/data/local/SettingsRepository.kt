package com.modatherali.linkloot.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.modatherali.linkloot.domain.model.AppLanguage
import com.modatherali.linkloot.domain.model.AppSettings
import com.modatherali.linkloot.domain.model.SubfolderStrategy
import com.modatherali.linkloot.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "linkloot_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val HIDE_SMALL_MEDIA = booleanPreferencesKey("hide_small_media")
        val MIN_IMAGE_SIZE_PX = intPreferencesKey("min_image_size_px")
        val SUBFOLDER_STRATEGY = stringPreferencesKey("subfolder_strategy")
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val AUTO_FALLBACK_WEBVIEW = booleanPreferencesKey("auto_fallback_webview")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.SYSTEM,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: false,
            appLanguage = prefs[Keys.APP_LANGUAGE]?.let {
                runCatching { AppLanguage.valueOf(it) }.getOrNull()
            } ?: AppLanguage.SYSTEM,
            hideSmallMedia = prefs[Keys.HIDE_SMALL_MEDIA] ?: true,
            minImageSizePx = prefs[Keys.MIN_IMAGE_SIZE_PX] ?: 100,
            subfolderStrategy = prefs[Keys.SUBFOLDER_STRATEGY]?.let {
                runCatching { SubfolderStrategy.valueOf(it) }.getOrNull()
            } ?: SubfolderStrategy.BY_MEDIA_TYPE,
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 3).coerceIn(1, 5),
            autoFallbackToWebView = prefs[Keys.AUTO_FALLBACK_WEBVIEW] ?: true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setHideSmallMedia(hide: Boolean) {
        context.settingsDataStore.edit { it[Keys.HIDE_SMALL_MEDIA] = hide }
    }

    suspend fun setSubfolderStrategy(strategy: SubfolderStrategy) {
        context.settingsDataStore.edit { it[Keys.SUBFOLDER_STRATEGY] = strategy.name }
    }

    suspend fun setMaxConcurrentDownloads(count: Int) {
        context.settingsDataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = count.coerceIn(1, 5) }
    }

    suspend fun setAutoFallbackToWebView(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.AUTO_FALLBACK_WEBVIEW] = enabled }
    }
}
