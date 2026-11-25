package com.modatherali.linkloot.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_entries ORDER BY lastAnalyzedAt DESC")
    fun observeAllHistory(): Flow<List<HistoryEntryEntity>>

    @Query(
        """
        SELECT * FROM history_entries
        WHERE pageTitle LIKE '%' || :query || '%'
           OR domain LIKE '%' || :query || '%'
           OR pageUrl LIKE '%' || :query || '%'
        ORDER BY lastAnalyzedAt DESC
        """
    )
    fun searchHistory(query: String): Flow<List<HistoryEntryEntity>>

    @Query("SELECT * FROM history_entries WHERE normalizedUrl = :normalizedUrl LIMIT 1")
    suspend fun getByNormalizedUrl(normalizedUrl: String): HistoryEntryEntity?

    @Query("SELECT * FROM history_entries WHERE id = :historyId LIMIT 1")
    suspend fun getById(historyId: Long): HistoryEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: HistoryEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(items: List<DiscoveredMediaEntity>)

    @Query("DELETE FROM discovered_media WHERE historyId = :historyId")
    suspend fun deleteMediaForHistory(historyId: Long)

    @Query("SELECT * FROM discovered_media WHERE historyId = :historyId ORDER BY sortOrder ASC")
    suspend fun getMediaForHistory(historyId: Long): List<DiscoveredMediaEntity>

    @Query("DELETE FROM history_entries WHERE id = :historyId")
    suspend fun deleteHistoryById(historyId: Long)

    @Query("DELETE FROM history_entries")
    suspend fun clearAllHistory()

    @Transaction
    suspend fun saveExtractionSession(
        entry: HistoryEntryEntity,
        mediaItems: List<DiscoveredMediaEntity>
    ): Long {
        val existing = getByNormalizedUrl(entry.normalizedUrl)
        val historyId = if (existing != null) {
            val updated = entry.copy(id = existing.id)
            insertHistory(updated)
            deleteMediaForHistory(existing.id)
            existing.id
        } else {
            insertHistory(entry)
        }
        val mediaWithFk = mediaItems.map { item ->
            item.copy(
                id = "${historyId}_${item.id}",
                historyId = historyId
            )
        }
        insertMedia(mediaWithFk)
        return historyId
    }
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM download_tasks
        WHERE status IN ('QUEUED', 'DOWNLOADING', 'MUXING', 'PAUSED')
        """
    )
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE status IN (:statuses) ORDER BY createdAt ASC")
    suspend fun getTasksByStatuses(statuses: List<String>): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE status = 'DOWNLOADING' OR status = 'MUXING'")
    suspend fun getInFlightTasks(): List<DownloadTaskEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTasks(tasks: List<DownloadTaskEntity>)

    @Query(
        """
        UPDATE download_tasks
        SET bytesDownloaded = :bytesDownloaded,
            totalBytes = :totalBytes,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status != 'COMPLETED'
        """
    )
    suspend fun updateByteProgress(
        id: String,
        bytesDownloaded: Long,
        totalBytes: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET segmentsCompleted = :segmentsCompleted,
            segmentsTotal = :segmentsTotal,
            bytesDownloaded = :bytesDownloaded,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status != 'COMPLETED'
        """
    )
    suspend fun updateHlsProgress(
        id: String,
        segmentsCompleted: Int,
        segmentsTotal: Int,
        bytesDownloaded: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status != 'COMPLETED'
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            errorMessage = :errorMessage,
            updatedAt = :updatedAt
        WHERE id = :id
          AND status IN (:allowedCurrentStatuses)
        """
    )
    suspend fun updateStatusIfIn(
        id: String,
        status: String,
        allowedCurrentStatuses: List<String>,
        errorMessage: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query(
        """
        UPDATE download_tasks
        SET tempPath = :tempPath,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateTempPath(
        id: String,
        tempPath: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'COMPLETED',
            savedUri = :savedUri,
            savedDisplayPath = :savedDisplayPath,
            bytesDownloaded = :finalBytes,
            totalBytes = :finalBytes,
            tempPath = NULL,
            errorMessage = NULL,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun markCompleted(
        id: String,
        savedUri: String,
        savedDisplayPath: String,
        finalBytes: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE status IN ('COMPLETED', 'CANCELLED')")
    suspend fun clearCompletedTasks()
}
