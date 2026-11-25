package com.modatherali.linkloot.data.local

import com.modatherali.linkloot.data.local.EntityMappers.toDomain
import com.modatherali.linkloot.data.local.EntityMappers.toEntity
import com.modatherali.linkloot.domain.model.ExtractionResult
import com.modatherali.linkloot.domain.model.HistorySession
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(private val historyDao: HistoryDao) {

    fun observeHistory(query: String = ""): Flow<List<HistorySession>> {
        val source = if (query.isBlank()) {
            historyDao.observeAllHistory()
        } else {
            historyDao.searchHistory(query.trim())
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    suspend fun saveExtractionSession(
        pageUrl: String,
        normalizedUrl: String,
        domain: String,
        pageTitle: String,
        mediaItems: List<MediaItem>
    ): Long {
        val visibleItems = mediaItems.filterNot { it.isJunkFiltered }
        val imageCount = mediaItems.count { it.type == MediaType.IMAGE && !it.isJunkFiltered }
        val videoCount = mediaItems.count { it.type == MediaType.VIDEO }
        val thumb = visibleItems.firstOrNull { it.type == MediaType.IMAGE }?.resolvedUrl
            ?: visibleItems.firstOrNull()?.thumbnailUrl
            ?: mediaItems.firstOrNull { it.type == MediaType.IMAGE }?.resolvedUrl

        val entry = HistoryEntryEntity(
            pageUrl = pageUrl,
            normalizedUrl = normalizedUrl,
            domain = domain,
            pageTitle = pageTitle.ifBlank { domain },
            thumbnailUrl = thumb,
            imageCount = imageCount,
            videoCount = videoCount,
            lastAnalyzedAt = System.currentTimeMillis()
        )
        val entities = mediaItems.mapIndexed { index, item ->
            item.toEntity(historyId = 0L, sortOrder = index)
        }
        return historyDao.saveExtractionSession(entry, entities)
    }

    suspend fun loadCachedSession(historyId: Long): ExtractionResult? {
        val entry = historyDao.getById(historyId) ?: return null
        val media = historyDao.getMediaForHistory(historyId).map { it.toDomain() }
        return ExtractionResult(
            historyId = entry.id,
            pageUrl = entry.pageUrl,
            normalizedUrl = entry.normalizedUrl,
            domain = entry.domain,
            pageTitle = entry.pageTitle,
            mediaItems = media,
            usedWebView = false
        )
    }

    suspend fun deleteHistory(historyId: Long) {
        historyDao.deleteHistoryById(historyId)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAllHistory()
    }
}
