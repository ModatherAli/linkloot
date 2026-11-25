package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.data.local.HistoryRepository
import com.modatherali.linkloot.domain.model.ExtractionProgress
import com.modatherali.linkloot.domain.model.ExtractionResult
import com.modatherali.linkloot.domain.model.ExtractionStage
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExtractionRepository(
    private val fastHtmlExtractor: FastHtmlExtractor,
    private val webViewMediaSniffer: WebViewMediaSniffer,
    private val mediaMetadataProber: MediaMetadataProber,
    private val historyRepository: HistoryRepository
) {
    private val _progress = MutableStateFlow(ExtractionProgress())
    val progress: StateFlow<ExtractionProgress> = _progress.asStateFlow()

    suspend fun extract(
        rawInput: String,
        forceDeepWebView: Boolean,
        autoFallbackToWebView: Boolean,
        minImageSizePx: Int
    ): ExtractionResult {
        _progress.value = ExtractionProgress(stage = ExtractionStage.NORMALIZING)
        val normalizedUrl = UrlNormalizer.normalize(rawInput)
            ?: throw IllegalArgumentException("Invalid URL")

        _progress.value = ExtractionProgress(stage = ExtractionStage.FAST_HTML)
        val fastOutput = runCatching {
            fastHtmlExtractor.extractFromUrl(normalizedUrl)
        }.getOrElse { error ->
            if (!forceDeepWebView && !autoFallbackToWebView) throw error
            FastExtractionOutput(
                pageTitle = UrlNormalizer.extractDomain(normalizedUrl),
                resolvedPageUrl = normalizedUrl,
                items = emptyList(),
                sessionHeaders = mapOf("Referer" to normalizedUrl),
                looksLikeSpaShell = true
            )
        }

        val merged = linkedMapOf<String, MediaItem>()
        fastOutput.items.forEach { merged[it.resolvedUrl] = it }

        var pageTitle = fastOutput.pageTitle
        var resolvedPageUrl = fastOutput.resolvedPageUrl
        var usedWebView = false

        emitCounts(ExtractionStage.FAST_HTML, merged.values, isUsingWebView = false)

        if (forceDeepWebView || (autoFallbackToWebView && fastOutput.looksLikeSpaShell)) {
            usedWebView = true
            emitCounts(ExtractionStage.WEBVIEW_SNIFF, merged.values, isUsingWebView = true)
            val webViewOutput = runCatching {
                webViewMediaSniffer.sniff(resolvedPageUrl)
            }.getOrNull()

            if (webViewOutput != null) {
                if (webViewOutput.pageTitle.isNotBlank()) pageTitle = webViewOutput.pageTitle
                if (webViewOutput.resolvedUrl.isNotBlank()) resolvedPageUrl = webViewOutput.resolvedUrl
                webViewOutput.items.forEach { item ->
                    val existing = merged[item.resolvedUrl]
                    merged[item.resolvedUrl] = mergeMediaItems(existing, item)
                }
            }
        }

        emitCounts(ExtractionStage.PROBING, merged.values, isUsingWebView = usedWebView)
        val probed = mediaMetadataProber.probeAll(merged.values.toList())
        val filtered = JunkMediaFilter.applyFilter(probed, minImageSizePx = minImageSizePx)

        emitCounts(ExtractionStage.SAVING, filtered, isUsingWebView = usedWebView)
        val domain = UrlNormalizer.extractDomain(resolvedPageUrl)
        val historyId = historyRepository.saveExtractionSession(
            pageUrl = resolvedPageUrl,
            normalizedUrl = normalizedUrl,
            domain = domain,
            pageTitle = pageTitle,
            mediaItems = filtered
        )

        emitCounts(ExtractionStage.COMPLETED, filtered, isUsingWebView = usedWebView)
        return ExtractionResult(
            historyId = historyId,
            pageUrl = resolvedPageUrl,
            normalizedUrl = normalizedUrl,
            domain = domain,
            pageTitle = pageTitle,
            mediaItems = filtered,
            usedWebView = usedWebView
        )
    }

    suspend fun importCapturedMedia(
        pageUrl: String,
        pageTitle: String,
        capturedItems: List<MediaItem>,
        minImageSizePx: Int
    ): ExtractionResult {
        val normalizedUrl = UrlNormalizer.normalize(pageUrl) ?: pageUrl
        val domain = UrlNormalizer.extractDomain(normalizedUrl)
        _progress.value = ExtractionProgress(
            stage = ExtractionStage.PROBING,
            imagesFound = capturedItems.count { it.type == MediaType.IMAGE },
            videosFound = capturedItems.count { it.type == MediaType.VIDEO },
            isUsingWebView = true
        )
        val probed = mediaMetadataProber.probeAll(capturedItems.distinctBy { it.resolvedUrl })
        val filtered = JunkMediaFilter.applyFilter(probed, minImageSizePx = minImageSizePx)

        val historyId = historyRepository.saveExtractionSession(
            pageUrl = pageUrl,
            normalizedUrl = normalizedUrl,
            domain = domain,
            pageTitle = pageTitle.ifBlank { domain },
            mediaItems = filtered
        )
        _progress.value = ExtractionProgress(
            stage = ExtractionStage.COMPLETED,
            imagesFound = filtered.count { it.type == MediaType.IMAGE && !it.isJunkFiltered },
            videosFound = filtered.count { it.type == MediaType.VIDEO },
            isUsingWebView = true
        )
        return ExtractionResult(
            historyId = historyId,
            pageUrl = pageUrl,
            normalizedUrl = normalizedUrl,
            domain = domain,
            pageTitle = pageTitle.ifBlank { domain },
            mediaItems = filtered,
            usedWebView = true
        )
    }

    fun resetProgress() {
        _progress.value = ExtractionProgress()
    }

    private fun emitCounts(
        stage: ExtractionStage,
        items: Collection<MediaItem>,
        isUsingWebView: Boolean
    ) {
        _progress.value = ExtractionProgress(
            stage = stage,
            imagesFound = items.count { it.type == MediaType.IMAGE },
            videosFound = items.count { it.type == MediaType.VIDEO },
            isUsingWebView = isUsingWebView
        )
    }

    private fun mergeMediaItems(first: MediaItem?, second: MediaItem): MediaItem {
        if (first == null) return second
        return first.copy(
            type = if (first.type == MediaType.VIDEO || second.type == MediaType.VIDEO) {
                MediaType.VIDEO
            } else {
                first.type
            },
            format = first.format.ifBlank { second.format },
            mimeType = first.mimeType.ifBlank { second.mimeType },
            width = first.width ?: second.width,
            height = first.height ?: second.height,
            durationMs = first.durationMs ?: second.durationMs,
            isHls = first.isHls || second.isHls,
            thumbnailUrl = first.thumbnailUrl ?: second.thumbnailUrl,
            headers = if (second.headers.isNotEmpty()) second.headers else first.headers
        )
    }
}
