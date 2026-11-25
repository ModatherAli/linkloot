package com.modatherali.linkloot.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.modatherali.linkloot.data.local.HistoryRepository
import com.modatherali.linkloot.data.local.SettingsRepository
import com.modatherali.linkloot.di.AppContainer
import com.modatherali.linkloot.domain.model.AppLanguage
import com.modatherali.linkloot.domain.model.AppSettings
import com.modatherali.linkloot.domain.model.SubfolderStrategy
import com.modatherali.linkloot.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsEvent {
    data object HistoryCleared : SettingsEvent
    data object CookiesCleared : SettingsEvent
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val clearCookiesAction: () -> Unit
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUseDynamicColor(enabled) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            settingsRepository.setAppLanguage(language)
            val locales = when (language) {
                AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
                AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
                AppLanguage.AR -> LocaleListCompat.forLanguageTags("ar")
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    fun setHideSmallMedia(hide: Boolean) {
        viewModelScope.launch { settingsRepository.setHideSmallMedia(hide) }
    }

    fun setAutoFallbackToWebView(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoFallbackToWebView(enabled) }
    }

    fun setSubfolderStrategy(strategy: SubfolderStrategy) {
        viewModelScope.launch { settingsRepository.setSubfolderStrategy(strategy) }
    }

    fun setMaxConcurrentDownloads(count: Int) {
        viewModelScope.launch { settingsRepository.setMaxConcurrentDownloads(count) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
            _events.emit(SettingsEvent.HistoryCleared)
        }
    }

    fun clearCookiesAndCache() {
        viewModelScope.launch {
            clearCookiesAction()
            _events.emit(SettingsEvent.CookiesCleared)
        }
    }

    class Factory(private val appContainer: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                settingsRepository = appContainer.settingsRepository,
                historyRepository = appContainer.historyRepository,
                clearCookiesAction = { appContainer.clearCookiesAndCache() }
            ) as T
        }
    }
}
