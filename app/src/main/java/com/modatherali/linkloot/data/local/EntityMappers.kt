package com.modatherali.linkloot.data.local

import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.domain.model.DownloadTask
import com.modatherali.linkloot.domain.model.HistorySession
import com.modatherali.linkloot.domain.model.HlsVariant
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object EntityMappers {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun HistoryEntryEntity.toDomain(): HistorySession = HistorySession(
        id = id,
        pageUrl = pageUrl,
        normalizedUrl = normalizedUrl,
        domain = domain,
        pageTitle = pageTitle,
        thumbnailUrl = thumbnailUrl,
        imageCount = imageCount,
        videoCount = videoCount,
        lastAnalyzedAt = lastAnalyzedAt
    )

    fun MediaItem.toEntity(historyId: Long, sortOrder: Int): DiscoveredMediaEntity =
        DiscoveredMediaEntity(
            id = id,
            historyId = historyId,
            mediaUrl = mediaUrl,
            resolvedUrl = resolvedUrl,
            type = type.name,
            format = format,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            durationMs = durationMs,
            qualityLabel = qualityLabel,
            isHls = isHls,
            isEncrypted = isEncrypted,
            hlsVariantsJson = json.encodeToString(hlsVariants),
            isJunkFiltered = isJunkFiltered,
            refererUrl = refererUrl,
            headersJson = json.encodeToString(headers),
            thumbnailUrl = thumbnailUrl,
            supportsRange = supportsRange,
            sortOrder = sortOrder
        )

    fun DiscoveredMediaEntity.toDomain(): MediaItem {
        val variants = runCatching {
            json.decodeFromString<List<HlsVariant>>(hlsVariantsJson)
        }.getOrDefault(emptyList())
        val parsedHeaders = runCatching {
            json.decodeFromString<Map<String, String>>(headersJson)
        }.getOrDefault(emptyMap())
        val mediaType = runCatching { MediaType.valueOf(type) }.getOrDefault(MediaType.IMAGE)
        // Strip "<historyId>_" prefix when returning to UI so item IDs remain clean
        val cleanId = id.substringAfter('_', id)
        return MediaItem(
            id = cleanId,
            mediaUrl = mediaUrl,
            resolvedUrl = resolvedUrl,
            type = mediaType,
            format = format,
            mimeType = mimeType,
            width = width,
            height = height,
            fileSizeBytes = fileSizeBytes,
            durationMs = durationMs,
            qualityLabel = qualityLabel,
            isHls = isHls,
            isEncrypted = isEncrypted,
            hlsVariants = variants,
            isJunkFiltered = isJunkFiltered,
            refererUrl = refererUrl,
            headers = parsedHeaders,
            thumbnailUrl = thumbnailUrl,
            supportsRange = supportsRange
        )
    }

    fun DownloadTask.toEntity(): DownloadTaskEntity = DownloadTaskEntity(
        id = id,
        mediaUrl = mediaUrl,
        pageUrl = pageUrl,
        domain = domain,
        fileName = fileName,
        subfolder = subfolder,
        mediaType = mediaType.name,
        mimeType = mimeType,
        isHls = isHls,
        status = status.name,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        segmentsCompleted = segmentsCompleted,
        segmentsTotal = segmentsTotal,
        tempPath = tempPath,
        savedUri = savedUri,
        savedDisplayPath = savedDisplayPath,
        refererUrl = refererUrl,
        headersJson = json.encodeToString(headers),
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun DownloadTaskEntity.toDomain(): DownloadTask {
        val parsedHeaders = runCatching {
            json.decodeFromString<Map<String, String>>(headersJson)
        }.getOrDefault(emptyMap())
        return DownloadTask(
            id = id,
            mediaUrl = mediaUrl,
            pageUrl = pageUrl,
            domain = domain,
            fileName = fileName,
            subfolder = subfolder,
            mediaType = runCatching { MediaType.valueOf(mediaType) }.getOrDefault(MediaType.IMAGE),
            mimeType = mimeType,
            isHls = isHls,
            status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            segmentsCompleted = segmentsCompleted,
            segmentsTotal = segmentsTotal,
            tempPath = tempPath,
            savedUri = savedUri,
            savedDisplayPath = savedDisplayPath,
            refererUrl = refererUrl,
            headers = parsedHeaders,
            errorMessage = errorMessage,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
