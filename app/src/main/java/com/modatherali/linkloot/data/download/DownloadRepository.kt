package com.modatherali.linkloot.data.download

import com.modatherali.linkloot.data.local.DownloadDao
import com.modatherali.linkloot.data.local.EntityMappers.toDomain
import com.modatherali.linkloot.data.local.EntityMappers.toEntity
import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val mediaStoreSaver: MediaStoreSaver
) {
    fun observeAllTasks(): Flow<List<DownloadTask>> =
        downloadDao.observeAllTasks().map { list -> list.map { it.toDomain() } }

    fun observeActiveCount(): Flow<Int> = downloadDao.observeActiveCount()

    suspend fun getTaskById(id: String): DownloadTask? =
        downloadDao.getTaskById(id)?.toDomain()

    suspend fun getQueuedTasks(): List<DownloadTask> =
        downloadDao.getTasksByStatuses(listOf(DownloadStatus.QUEUED.name)).map { it.toDomain() }

    suspend fun getPausedTasks(): List<DownloadTask> =
        downloadDao.getTasksByStatuses(listOf(DownloadStatus.PAUSED.name)).map { it.toDomain() }

    suspend fun recoverInterruptedTasks() {
        val inFlight = downloadDao.getInFlightTasks()
        for (task in inFlight) {
            downloadDao.updateStatusIfIn(
                id = task.id,
                status = DownloadStatus.PAUSED.name,
                allowedCurrentStatuses = listOf(
                    DownloadStatus.DOWNLOADING.name,
                    DownloadStatus.MUXING.name
                ),
                errorMessage = null
            )
        }
    }

    suspend fun upsertTask(task: DownloadTask) {
        downloadDao.upsertTask(task.toEntity())
    }

    suspend fun upsertTasks(tasks: List<DownloadTask>) {
        downloadDao.upsertTasks(tasks.map { it.toEntity() })
    }

    suspend fun updateByteProgress(id: String, bytesDownloaded: Long, totalBytes: Long) {
        downloadDao.updateByteProgress(id, bytesDownloaded, totalBytes)
    }

    suspend fun updateHlsProgress(
        id: String,
        segmentsCompleted: Int,
        segmentsTotal: Int,
        bytesDownloaded: Long
    ) {
        downloadDao.updateHlsProgress(id, segmentsCompleted, segmentsTotal, bytesDownloaded)
    }

    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null) {
        downloadDao.updateStatus(id, status.name, errorMessage)
    }

    suspend fun updateStatusIfIn(
        id: String,
        status: DownloadStatus,
        allowedCurrentStatuses: Set<DownloadStatus>,
        errorMessage: String? = null
    ): Int {
        if (allowedCurrentStatuses.isEmpty()) return 0
        return downloadDao.updateStatusIfIn(
            id = id,
            status = status.name,
            allowedCurrentStatuses = allowedCurrentStatuses.map { it.name },
            errorMessage = errorMessage
        )
    }

    suspend fun updateTempPath(id: String, tempPath: String?) {
        downloadDao.updateTempPath(id, tempPath)
    }

    suspend fun markCompleted(
        id: String,
        savedUri: String,
        savedDisplayPath: String,
        finalBytes: Long
    ) {
        downloadDao.markCompleted(id, savedUri, savedDisplayPath, finalBytes)
    }

    suspend fun deleteTask(task: DownloadTask, deleteOutputFile: Boolean) {
        if (deleteOutputFile && !task.savedUri.isNullOrBlank()) {
            mediaStoreSaver.deleteSavedMedia(task.savedUri)
        }
        downloadDao.deleteTaskById(task.id)
    }

    suspend fun clearCompletedTasks() {
        downloadDao.clearCompletedTasks()
    }
}
