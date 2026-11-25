package com.modatherali.linkloot.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history_entries",
    indices = [Index(value = ["normalizedUrl"]), Index(value = ["lastAnalyzedAt"])]
)
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val pageUrl: String,
    val normalizedUrl: String,
    val domain: String,
    val pageTitle: String,
    val thumbnailUrl: String?,
    val imageCount: Int,
    val videoCount: Int,
    val lastAnalyzedAt: Long
)

@Entity(
    tableName = "discovered_media",
    foreignKeys = [
        ForeignKey(
            entity = HistoryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["historyId"])]
)
data class DiscoveredMediaEntity(
    @PrimaryKey
    val id: String,
    val historyId: Long,
    val mediaUrl: String,
    val resolvedUrl: String,
    val type: String,
    val format: String,
    val mimeType: String,
    val width: Int?,
    val height: Int?,
    val fileSizeBytes: Long?,
    val durationMs: Long?,
    val qualityLabel: String?,
    val isHls: Boolean,
    val isEncrypted: Boolean,
    val hlsVariantsJson: String,
    val isJunkFiltered: Boolean,
    val refererUrl: String,
    val headersJson: String,
    val thumbnailUrl: String?,
    val supportsRange: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "download_tasks",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])]
)
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val mediaUrl: String,
    val pageUrl: String,
    val domain: String,
    val fileName: String,
    val subfolder: String,
    val mediaType: String,
    val mimeType: String,
    val isHls: Boolean,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val segmentsCompleted: Int,
    val segmentsTotal: Int,
    val tempPath: String?,
    val savedUri: String?,
    val savedDisplayPath: String?,
    val refererUrl: String,
    val headersJson: String,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)
