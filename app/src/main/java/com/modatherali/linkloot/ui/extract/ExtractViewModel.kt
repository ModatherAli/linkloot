package com.modatherali.linkloot.ui.extract

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.modatherali.linkloot.data.download.DownloadCoordinator
import com.modatherali.linkloot.data.extractor.ExtractionRepository
import com.modatherali.linkloot.data.extractor.UrlNormalizer
import com.modatherali.linkloot.data.local.HistoryRepository
import com.modatherali.linkloot.data.local.SettingsRepository
import com.modatherali.linkloot.domain.model.ExtractionProgress
import com.modatherali.linkloot.domain.model.ExtractionResult
import com.modatherali.linkloot.domain.model.HlsVariant
import com.modatherali.linkloot.domain.model.MediaFilterTab
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.domain.model.SortOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExtractUiState(
    val urlInput: String = "",
    val clipboardCandidate: String? = null,
    val isAnalyzing: Boolean = false,
    val progress: ExtractionProgress = ExtractionProgress(),
    val errorMessage: String? = null,
    val hasAttemptedAnalysis: Boolean = false,
    val pageTitle: String = "",
    val currentPageUrl: String = "",
    val usedWebView: Boolean = false,
    val allMedia: List<MediaItem> = emptyList(),
    val activeTab: MediaFilterTab = MediaFilterTab.ALL,
    val hideSmallMedia: Boolean = true,
    val sortOption: SortOption = SortOption.DEFAULT,
    val selectedIds: Set<String> = emptySet(),
    val hlsPickerItem: MediaItem? = null,
    val previewItemId: String? = null
) {
    val isSelectionMode: Boolean
        get() = selectedIds.isNotEmpty()

    val hiddenSmallCount: Int
        get() = allMedia.count { it.isJunkFiltered }

    val visibleAfterJunkFilter: List<MediaItem>
        get() = if (hideSmallMedia) allMedia.filterNot { it.isJunkFiltered } else allMedia

    val totalVisibleCount: Int
        get() = visibleAfterJunkFilter.size

    val imageCount: Int
        get() = visibleAfterJunkFilter.count { it.type == MediaType.IMAGE }

    val videoCount: Int
        get() = visibleAfterJunkFilter.count { it.type == MediaType.VIDEO }

    val displayedMedia: List<MediaItem>
        get() {
            val filtered = when (activeTab) {
                MediaFilterTab.ALL -> visibleAfterJunkFilter
                MediaFilterTab.IMAGES -> visibleAfterJunkFilter.filter { it.type == MediaType.IMAGE }
                MediaFilterTab.VIDEOS -> visibleAfterJunkFilter.filter { it.type == MediaType.VIDEO }
            }
            return when (sortOption) {
                SortOption.DEFAULT -> filtered
                SortOption.SIZE_DESC -> filtered.sortedByDescending { it.fileSizeBytes ?: -1L }
                SortOption.TYPE -> filtered.sortedWith(
                    compareBy<MediaItem> { it.type.ordinal }
                        .thenBy { it.format }
                )
            }
        }
}

sealed interface ExtractEvent {
    data class DownloadsQueued(val count: Int) : ExtractEvent
    data object EncryptedStreamWarning : ExtractEvent
    data object InvalidUrlWarning : ExtractEvent
}

class ExtractViewModel(
    private val extractionRepository: ExtractionRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadCoordinator: DownloadCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExtractUiState())
    val uiState: StateFlow<ExtractUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExtractEvent>()
    val events: SharedFlow<ExtractEvent> = _events.asSharedFlow()

    private var analyzeJob: Job? = null

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.hideSmallMedia }
                .distinctUntilChanged()
                .collect { hideSmall ->
                    _uiState.update { it.copy(hideSmallMedia = hideSmall) }
                }
        }
        viewModelScope.launch {
            extractionRepository.progress.collect { progress ->
                _uiState.update { it.copy(progress = progress) }
            }
        }
    }

    fun onUrlInputChange(newValue: String) {
        _uiState.update { it.copy(urlInput = newValue, errorMessage = null) }
    }

    fun updateClipboardCandidate(rawClipboard: String?) {
        val normalized = rawClipboard?.let { UrlNormalizer.normalize(it) }
        _uiState.update { state ->
            val candidate = if (normalized != null && normalized != state.urlInput.trim()) normalized else null
            state.copy(clipboardCandidate = candidate)
        }
    }

    fun applyClipboardCandidate() {
        val candidate = _uiState.value.clipboardCandidate ?: return
        _uiState.update { it.copy(urlInput = candidate, clipboardCandidate = null, errorMessage = null) }
    }

    fun clearUrlInput() {
        _uiState.update { it.copy(urlInput = "", errorMessage = null) }
    }

    fun analyzeUrl(rawUrl: String? = null, forceDeepWebView: Boolean = false) {
        val input = rawUrl ?: _uiState.value.urlInput
        if (rawUrl != null) {
            _uiState.update { it.copy(urlInput = rawUrl) }
        }
        val normalized = UrlNormalizer.normalize(input)
        if (normalized == null) {
            viewModelScope.launch { _events.emit(ExtractEvent.InvalidUrlWarning) }
            return
        }

        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    errorMessage = null,
                    selectedIds = emptySet(),
                    hasAttemptedAnalysis = true,
                    hideSmallMedia = settings.hideSmallMedia
                )
            }
            try {
                val result = extractionRepository.extract(
                    rawInput = normalized,
                    forceDeepWebView = forceDeepWebView,
                    autoFallbackToWebView = settings.autoFallbackToWebView,
                    minImageSizePx = settings.minImageSizePx
                )
                applyExtractionResult(result)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        errorMessage = t.message ?: "Extraction failed"
                    )
                }
            }
        }
    }

    fun cancelAnalysis() {
        analyzeJob?.cancel()
        analyzeJob = null
        extractionRepository.resetProgress()
        _uiState.update { it.copy(isAnalyzing = false) }
    }

    fun loadFromHistory(historyId: Long) {
        analyzeJob?.cancel()
        viewModelScope.launch {
            val cached = historyRepository.loadCachedSession(historyId) ?: return@launch
            applyExtractionResult(cached)
        }
    }

    fun importCapturedMedia(pageUrl: String, pageTitle: String, items: List<MediaItem>) {
        analyzeJob?.cancel()
        analyzeJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            _uiState.update {
                it.copy(
                    urlInput = pageUrl,
                    isAnalyzing = true,
                    errorMessage = null,
                    selectedIds = emptySet(),
                    hasAttemptedAnalysis = true,
                    hideSmallMedia = settings.hideSmallMedia
                )
            }
            runCatching {
                extractionRepository.importCapturedMedia(
                    pageUrl = pageUrl,
                    pageTitle = pageTitle,
                    capturedItems = items,
                    minImageSizePx = settings.minImageSizePx
                )
            }.onSuccess { result ->
                applyExtractionResult(result)
            }.onFailure { t ->
                _uiState.update {
                    it.copy(isAnalyzing = false, errorMessage = t.message ?: "Import failed")
                }
            }
        }
    }

    private fun applyExtractionResult(result: ExtractionResult) {
        _uiState.update {
            it.copy(
                urlInput = result.pageUrl,
                isAnalyzing = false,
                errorMessage = null,
                hasAttemptedAnalysis = true,
                pageTitle = result.pageTitle,
                currentPageUrl = result.pageUrl,
                usedWebView = result.usedWebView,
                allMedia = result.mediaItems,
                selectedIds = emptySet(),
                activeTab = MediaFilterTab.ALL
            )
        }
    }

    fun setTab(tab: MediaFilterTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleHideSmallMedia() {
        _uiState.update { it.copy(hideSmallMedia = !it.hideSmallMedia) }
    }

    fun setSortOption(sort: SortOption) {
        _uiState.update { it.copy(sortOption = sort) }
    }

    fun toggleSelection(itemId: String) {
        _uiState.update { state ->
            val next = if (itemId in state.selectedIds) {
                state.selectedIds - itemId
            } else {
                state.selectedIds + itemId
            }
            state.copy(selectedIds = next)
        }
    }

    fun selectAllDisplayed() {
        _uiState.update { state ->
            state.copy(selectedIds = state.displayedMedia.map { it.id }.toSet())
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun openPreview(item: MediaItem) {
        _uiState.update { it.copy(previewItemId = item.id) }
    }

    fun closePreview() {
        _uiState.update { it.copy(previewItemId = null) }
    }

    fun dismissHlsPicker() {
        _uiState.update { it.copy(hlsPickerItem = null) }
    }

    fun requestDownloadSingle(item: MediaItem) {
        if (item.isEncrypted) {
            viewModelScope.launch { _events.emit(ExtractEvent.EncryptedStreamWarning) }
            return
        }
        if (item.isHls && item.hlsVariants.size > 1) {
            _uiState.update { it.copy(hlsPickerItem = item) }
            return
        }
        val defaultVariants = if (item.isHls && item.hlsVariants.size == 1) {
            mapOf(item.id to item.hlsVariants.first())
        } else {
            emptyMap()
        }
        enqueueItems(listOf(item), defaultVariants)
    }

    fun downloadHlsVariant(item: MediaItem, variant: HlsVariant) {
        _uiState.update { it.copy(hlsPickerItem = null) }
        enqueueItems(listOf(item), mapOf(item.id to variant))
    }

    fun downloadSelected() {
        val state = _uiState.value
        val selected = state.allMedia.filter { it.id in state.selectedIds }
        if (selected.isEmpty()) return

        val hasEncrypted = selected.any { it.isEncrypted }
        val downloadable = selected.filterNot { it.isEncrypted }
        if (downloadable.isEmpty() && hasEncrypted) {
            viewModelScope.launch { _events.emit(ExtractEvent.EncryptedStreamWarning) }
            return
        }

        val variants = downloadable.mapNotNull { item ->
            item.hlsVariants.firstOrNull()?.let { item.id to it }
        }.toMap()

        _uiState.update { it.copy(selectedIds = emptySet()) }
        enqueueItems(downloadable, variants)
    }

    private fun enqueueItems(
        items: List<MediaItem>,
        variants: Map<String, HlsVariant>
    ) {
        val pageUrl = _uiState.value.currentPageUrl
        viewModelScope.launch {
            val count = downloadCoordinator.enqueueDownloads(
                items = items,
                pageUrl = pageUrl,
                selectedVariants = variants
            )
            if (count > 0) {
                _events.emit(ExtractEvent.DownloadsQueued(count))
            }
        }
    }

    class Factory(
        private val extractionRepository: ExtractionRepository,
        private val historyRepository: HistoryRepository,
        private val settingsRepository: SettingsRepository,
        private val downloadCoordinator: DownloadCoordinator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ExtractViewModel(
                extractionRepository = extractionRepository,
                historyRepository = historyRepository,
                settingsRepository = settingsRepository,
                downloadCoordinator = downloadCoordinator
            ) as T
        }
    }
}
