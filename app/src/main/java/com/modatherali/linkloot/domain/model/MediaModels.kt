package com.modatherali.linkloot.domain.model

import kotlinx.serialization.Serializable

enum class MediaType {
    IMAGE,
    VIDEO
}

@Serializable
data class HlsVariant(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val bandwidthBps: Long? = null,
    val codecs: String? = null,
    val label: String
)

data class HlsSegment(
    val uri: String,
    val durationMs: Long
)

data class HlsPlaylist(
    val isMaster: Boolean,
    val variants: List<HlsVariant> = emptyList(),
    val segments: List<HlsSegment> = emptyList(),
    val initMapUri: String? = null,
    val totalDurationMs: Long = 0L,
    val isEncrypted: Boolean = false
)

data class MediaItem(
    val id: String,
    val mediaUrl: String,
    val resolvedUrl: String = mediaUrl,
    val type: MediaType,
    val format: String = "",
    val mimeType: String = "",
    val width: Int? = null,
    val height: Int? = null,
    val fileSizeBytes: Long? = null,
    val durationMs: Long? = null,
    val qualityLabel: String? = null,
    val isHls: Boolean = false,
    val isEncrypted: Boolean = false,
    val hlsVariants: List<HlsVariant> = emptyList(),
    val isJunkFiltered: Boolean = false,
    val refererUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val thumbnailUrl: String? = null,
    val supportsRange: Boolean = false
)

enum class ExtractionStage {
    IDLE,
    NORMALIZING,
    FAST_HTML,
    WEBVIEW_SNIFF,
    PROBING,
    SAVING,
    COMPLETED
}

data class ExtractionProgress(
    val stage: ExtractionStage = ExtractionStage.IDLE,
    val imagesFound: Int = 0,
    val videosFound: Int = 0,
    val isUsingWebView: Boolean = false
)

data class ExtractionResult(
    val historyId: Long,
    val pageUrl: String,
    val normalizedUrl: String,
    val domain: String,
    val pageTitle: String,
    val mediaItems: List<MediaItem>,
    val usedWebView: Boolean = false
)

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    MUXING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val mediaUrl: String,
    val pageUrl: String,
    val domain: String,
    val fileName: String,
    val subfolder: String,
    val mediaType: MediaType,
    val mimeType: String,
    val isHls: Boolean,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val segmentsCompleted: Int = 0,
    val segmentsTotal: Int = 0,
    val tempPath: String? = null,
    val savedUri: String? = null,
    val savedDisplayPath: String? = null,
    val refererUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val progressFraction: Float?
        get() = when {
            isHls && segmentsTotal > 0 -> (segmentsCompleted.toFloat() / segmentsTotal.toFloat()).coerceIn(0f, 1f)
            totalBytes > 0L -> (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            else -> null
        }

    val isActiveOrPending: Boolean
        get() = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.DOWNLOADING ||
            status == DownloadStatus.MUXING ||
            status == DownloadStatus.PAUSED
}

data class HistorySession(
    val id: Long,
    val pageUrl: String,
    val normalizedUrl: String,
    val domain: String,
    val pageTitle: String,
    val thumbnailUrl: String?,
    val imageCount: Int,
    val videoCount: Int,
    val lastAnalyzedAt: Long
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage {
    SYSTEM,
    AR,
    EN
}

enum class SubfolderStrategy {
    BY_MEDIA_TYPE,
    BY_DOMAIN_AND_TYPE
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val hideSmallMedia: Boolean = true,
    val minImageSizePx: Int = 100,
    val subfolderStrategy: SubfolderStrategy = SubfolderStrategy.BY_MEDIA_TYPE,
    val maxConcurrentDownloads: Int = 3,
    val autoFallbackToWebView: Boolean = true
)

enum class MediaFilterTab {
    ALL,
    IMAGES,
    VIDEOS
}

enum class SortOption {
    DEFAULT,
    SIZE_DESC,
    TYPE
}
