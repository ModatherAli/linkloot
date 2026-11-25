package com.modatherali.linkloot.data.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.modatherali.linkloot.data.extractor.UrlNormalizer
import com.modatherali.linkloot.data.local.SettingsRepository
import com.modatherali.linkloot.domain.model.DownloadStatus
import com.modatherali.linkloot.domain.model.DownloadTask
import com.modatherali.linkloot.domain.model.HlsVariant
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
class DownloadCoordinator(
    private val context: Context,
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val directFileDownloader: DirectFileDownloader,
    private val hlsDownloadEngine: HlsDownloadEngine,
    private val mediaStoreSaver: MediaStoreSaver
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controlDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scheduleMutex = Mutex()
    private val runningJobs = ConcurrentHashMap<String, Job>()
    private val pausedIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledIds = ConcurrentHashMap.newKeySet<String>()

    private val recoveryJob: Job = scope.launch {
        downloadRepository.recoverInterruptedTasks()
    }

    suspend fun enqueueDownloads(
        items: List<MediaItem>,
        pageUrl: String,
        selectedVariants: Map<String, HlsVariant> = emptyMap()
    ): Int {
        val downloadable = items.filterNot { it.isEncrypted }
        if (downloadable.isEmpty()) return 0

        recoveryJob.join()
        val settings = settingsRepository.settings.first()
        val now = System.currentTimeMillis()
        val tasks = downloadable.mapIndexed { index, item ->
            val variant = selectedVariants[item.id]
            val targetUrl = variant?.url ?: item.resolvedUrl
            val domain = UrlNormalizer.extractDomain(pageUrl.ifBlank { targetUrl })
            val ext = resolveExtension(item, targetUrl)
            val mime = resolveMimeType(item, ext)
            val fileName = buildFileName(targetUrl, domain, ext, variant?.label, uniqueSalt = now + index)
            val subfolder = mediaStoreSaver.resolveSubfolder(
                strategy = settings.subfolderStrategy,
                domain = domain,
                mediaType = item.type
            )

            DownloadTask(
                id = UUID.randomUUID().toString(),
                mediaUrl = targetUrl,
                pageUrl = pageUrl.ifBlank { item.refererUrl },
                domain = domain,
                fileName = fileName,
                subfolder = subfolder,
                mediaType = item.type,
                mimeType = mime,
                isHls = item.isHls,
                status = DownloadStatus.QUEUED,
                totalBytes = item.fileSizeBytes ?: -1L,
                refererUrl = item.refererUrl.ifBlank { pageUrl },
                headers = item.headers,
                createdAt = now + index,
                updatedAt = now + index
            )
        }

        downloadRepository.upsertTasks(tasks)
        ensureForegroundServiceRunning()
        schedulePendingTasks()
        return tasks.size
    }

    fun pauseTask(taskId: String) {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            scheduleMutex.withLock {
                val job = runningJobs[taskId]
                if (job != null) {
                    pausedIds.add(taskId)
                    cancelledIds.remove(taskId)
                    job.cancel()
                } else {
                    pausedIds.remove(taskId)
                    cancelledIds.remove(taskId)
                    downloadRepository.updateStatusIfIn(
                        id = taskId,
                        status = DownloadStatus.PAUSED,
                        allowedCurrentStatuses = setOf(
                            DownloadStatus.QUEUED,
                            DownloadStatus.DOWNLOADING,
                            DownloadStatus.MUXING
                        )
                    )
                }
            }
        }
    }

    fun resumeTask(taskId: String) {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            while (true) {
                val cancelledJob = scheduleMutex.withLock {
                    runningJobs[taskId]?.takeIf { it.isCancelled }
                } ?: break
                cancelledJob.join()
            }
            val updated = scheduleMutex.withLock {
                pausedIds.remove(taskId)
                cancelledIds.remove(taskId)
                downloadRepository.updateStatusIfIn(
                    id = taskId,
                    status = DownloadStatus.QUEUED,
                    allowedCurrentStatuses = setOf(
                        DownloadStatus.PAUSED,
                        DownloadStatus.FAILED,
                        DownloadStatus.CANCELLED
                    )
                )
            }
            if (updated > 0) {
                ensureForegroundServiceRunning()
                schedulePendingTasks()
            }
        }
    }

    fun retryTask(taskId: String) {
        resumeTask(taskId)
    }

    fun cancelTask(taskId: String) {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            scheduleMutex.withLock {
                val job = runningJobs[taskId]
                if (job != null) {
                    cancelledIds.add(taskId)
                    pausedIds.remove(taskId)
                    job.cancel()
                } else {
                    cancelledIds.remove(taskId)
                    pausedIds.remove(taskId)
                    val updated = downloadRepository.updateStatusIfIn(
                        id = taskId,
                        status = DownloadStatus.CANCELLED,
                        allowedCurrentStatuses = setOf(
                            DownloadStatus.QUEUED,
                            DownloadStatus.DOWNLOADING,
                            DownloadStatus.MUXING,
                            DownloadStatus.PAUSED,
                            DownloadStatus.FAILED
                        )
                    )
                    if (updated > 0) {
                        cleanupTaskTemp(taskId)
                    }
                }
            }
        }
    }

    fun pauseAll() {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            scheduleMutex.withLock {
                val queued = downloadRepository.getQueuedTasks()
                queued.forEach { task ->
                    if (!runningJobs.containsKey(task.id)) {
                        downloadRepository.updateStatusIfIn(
                            id = task.id,
                            status = DownloadStatus.PAUSED,
                            allowedCurrentStatuses = setOf(DownloadStatus.QUEUED)
                        )
                    }
                }
                runningJobs.keys.toList().forEach { id ->
                    pausedIds.add(id)
                    cancelledIds.remove(id)
                    runningJobs[id]?.cancel()
                }
            }
        }
    }

    fun resumeAll() {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            while (true) {
                val cancelledJobs = scheduleMutex.withLock {
                    runningJobs.values.filter { it.isCancelled }
                }
                if (cancelledJobs.isEmpty()) break
                cancelledJobs.forEach { it.join() }
            }
            val resumedCount = scheduleMutex.withLock {
                val list = downloadRepository.getPausedTasks()
                var count = 0
                list.forEach { task ->
                    pausedIds.remove(task.id)
                    cancelledIds.remove(task.id)
                    count += downloadRepository.updateStatusIfIn(
                        id = task.id,
                        status = DownloadStatus.QUEUED,
                        allowedCurrentStatuses = setOf(DownloadStatus.PAUSED)
                    )
                }
                count
            }
            if (resumedCount == 0) return@launch
            ensureForegroundServiceRunning()
            schedulePendingTasks()
        }
    }

    fun cancelAllActive() {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            scheduleMutex.withLock {
                val queued = downloadRepository.getQueuedTasks()
                val paused = downloadRepository.getPausedTasks()
                (queued + paused).forEach { task ->
                    if (!runningJobs.containsKey(task.id)) {
                        val updated = downloadRepository.updateStatusIfIn(
                            id = task.id,
                            status = DownloadStatus.CANCELLED,
                            allowedCurrentStatuses = setOf(
                                DownloadStatus.QUEUED,
                                DownloadStatus.PAUSED
                            )
                        )
                        if (updated > 0) {
                            cleanupTaskTemp(task.id)
                        }
                    }
                }
                runningJobs.keys.toList().forEach { id ->
                    cancelledIds.add(id)
                    pausedIds.remove(id)
                    runningJobs[id]?.cancel()
                }
            }
        }
    }

    fun deleteTask(task: DownloadTask, deleteOutputFile: Boolean) {
        scope.launch(controlDispatcher) {
            recoveryJob.join()
            val job = scheduleMutex.withLock {
                runningJobs[task.id]?.also {
                    cancelledIds.add(task.id)
                    pausedIds.remove(task.id)
                    it.cancel()
                }
            }
            job?.join()
            scheduleMutex.withLock {
                cancelledIds.remove(task.id)
                pausedIds.remove(task.id)
            }
            val latestTask = downloadRepository.getTaskById(task.id) ?: task
            cleanupTaskTemp(task.id)
            downloadRepository.deleteTask(latestTask, deleteOutputFile)
        }
    }

    fun clearCompleted() {
        scope.launch(controlDispatcher) {
            downloadRepository.clearCompletedTasks()
        }
    }

    private suspend fun schedulePendingTasks() {
        recoveryJob.join()
        scheduleMutex.withLock {
            val maxConcurrent = settingsRepository.settings.first().maxConcurrentDownloads.coerceIn(1, 5)
            val availableSlots = maxConcurrent - runningJobs.size
            if (availableSlots <= 0) return

            val queued = downloadRepository.getQueuedTasks()
                .filter {
                    !runningJobs.containsKey(it.id) &&
                        !pausedIds.contains(it.id) &&
                        !cancelledIds.contains(it.id)
                }
                .take(availableSlots)

            for (task in queued) {
                val job = scope.launch(start = CoroutineStart.ATOMIC) {
                    executeTask(task)
                }
                runningJobs[task.id] = job
            }
        }
    }

    private suspend fun executeTask(task: DownloadTask) {
        var completedNormally = false
        var wasCancelled = false
        var failure: Throwable? = null
        try {
            coroutineContext.ensureActive()
            val transitioned = downloadRepository.updateStatusIfIn(
                id = task.id,
                status = DownloadStatus.DOWNLOADING,
                allowedCurrentStatuses = setOf(DownloadStatus.QUEUED)
            )
            if (transitioned == 0) return

            val headers = CookieHeaderHelper.buildEffectiveHeaders(
                targetUrl = task.mediaUrl,
                refererUrl = task.refererUrl,
                baseHeaders = task.headers
            )

            if (task.isHls) {
                downloadRepository.updateTempPath(task.id, hlsDownloadEngine.getTaskDir(task.id).absolutePath)
                val mp4File = hlsDownloadEngine.downloadAndMuxToMp4(
                    taskId = task.id,
                    playlistUrl = task.mediaUrl,
                    headers = headers,
                    onSegmentProgress = { completed, total, bytes ->
                        downloadRepository.updateHlsProgress(task.id, completed, total, bytes)
                    },
                    onMuxingStarted = {
                        downloadRepository.updateStatusIfIn(
                            id = task.id,
                            status = DownloadStatus.MUXING,
                            allowedCurrentStatuses = setOf(DownloadStatus.DOWNLOADING)
                        )
                    }
                )
                coroutineContext.ensureActive()
                val saved = mediaStoreSaver.saveFile(
                    sourceFile = mp4File,
                    desiredFileName = task.fileName,
                    mimeType = "video/mp4",
                    subfolder = task.subfolder
                )
                withContext(NonCancellable) {
                    hlsDownloadEngine.cleanupTaskDir(task.id)
                    downloadRepository.markCompleted(
                        id = task.id,
                        savedUri = saved.uriString,
                        savedDisplayPath = saved.displayPath,
                        finalBytes = saved.byteSize
                    )
                }
                completedNormally = true
            } else {
                downloadRepository.updateTempPath(task.id, directFileDownloader.getTempFile(task.id).absolutePath)
                val result = directFileDownloader.downloadToTemp(
                    taskId = task.id,
                    url = task.mediaUrl,
                    headers = headers,
                    onProgress = { downloaded, total ->
                        downloadRepository.updateByteProgress(task.id, downloaded, total)
                    }
                )
                coroutineContext.ensureActive()
                val finalMime = result.mimeType.ifBlank { task.mimeType }
                val saved = mediaStoreSaver.saveFile(
                    sourceFile = result.tempFile,
                    desiredFileName = task.fileName,
                    mimeType = finalMime,
                    subfolder = task.subfolder
                )
                withContext(NonCancellable) {
                    directFileDownloader.deleteTempFile(task.id)
                    downloadRepository.markCompleted(
                        id = task.id,
                        savedUri = saved.uriString,
                        savedDisplayPath = saved.displayPath,
                        finalBytes = saved.byteSize
                    )
                }
                completedNormally = true
            }
        } catch (ce: CancellationException) {
            wasCancelled = true
            throw ce
        } catch (t: Throwable) {
            failure = t
        } finally {
            withContext(NonCancellable) {
                scheduleMutex.withLock {
                    if (!completedNormally) {
                        when {
                            cancelledIds.remove(task.id) -> {
                                cleanupTaskTemp(task.id)
                                downloadRepository.updateStatusIfIn(
                                    id = task.id,
                                    status = DownloadStatus.CANCELLED,
                                    allowedCurrentStatuses = setOf(
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.DOWNLOADING,
                                        DownloadStatus.MUXING,
                                        DownloadStatus.PAUSED
                                    )
                                )
                            }
                            pausedIds.remove(task.id) -> {
                                downloadRepository.updateStatusIfIn(
                                    id = task.id,
                                    status = DownloadStatus.PAUSED,
                                    allowedCurrentStatuses = setOf(
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.DOWNLOADING,
                                        DownloadStatus.MUXING
                                    )
                                )
                            }
                            wasCancelled -> {
                                downloadRepository.updateStatusIfIn(
                                    id = task.id,
                                    status = DownloadStatus.PAUSED,
                                    allowedCurrentStatuses = setOf(
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.DOWNLOADING,
                                        DownloadStatus.MUXING
                                    )
                                )
                            }
                            failure != null -> {
                                downloadRepository.updateStatusIfIn(
                                    id = task.id,
                                    status = DownloadStatus.FAILED,
                                    allowedCurrentStatuses = setOf(
                                        DownloadStatus.QUEUED,
                                        DownloadStatus.DOWNLOADING,
                                        DownloadStatus.MUXING
                                    ),
                                    errorMessage = failure.message ?: "Download error"
                                )
                            }
                        }
                    }
                    runningJobs.remove(task.id)
                    pausedIds.remove(task.id)
                    cancelledIds.remove(task.id)
                }
                schedulePendingTasks()
            }
        }
    }

    private fun cleanupTaskTemp(taskId: String) {
        directFileDownloader.deleteTempFile(taskId)
        hlsDownloadEngine.cleanupTaskDir(taskId)
    }

    private fun ensureForegroundServiceRunning() {
        runCatching {
            val intent = Intent(context, DownloadForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private fun resolveExtension(item: MediaItem, targetUrl: String): String {
        if (item.isHls) return "mp4"
        val urlExt = targetUrl.substringBefore('?').substringBefore('#')
            .substringAfterLast('/', "")
            .substringAfterLast('.', "")
            .lowercase()
        if (urlExt in setOf("mp4", "webm", "mov", "m4v", "jpg", "jpeg", "png", "webp", "gif")) {
            return if (urlExt == "jpeg") "jpg" else urlExt
        }
        if (item.format.isNotBlank() && item.format != "m3u8") return item.format
        return if (item.type == MediaType.VIDEO) "mp4" else "jpg"
    }

    private fun resolveMimeType(item: MediaItem, ext: String): String {
        if (item.isHls) return "video/mp4"
        if (item.mimeType.isNotBlank() && !item.mimeType.contains("mpegurl")) return item.mimeType
        return when (ext.lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
    }

    private fun buildFileName(
        targetUrl: String,
        domain: String,
        ext: String,
        variantLabel: String?,
        uniqueSalt: Long
    ): String {
        val rawPathName = targetUrl.substringBefore('?').substringBefore('#')
            .substringAfterLast('/', "")
        val decoded = runCatching { URLDecoder.decode(rawPathName, "UTF-8") }.getOrDefault(rawPathName)
        val base = decoded.substringBeforeLast('.', decoded)
            .replace(Regex("""[^a-zA-Z0-9._-]+"""), "_")
            .trim('_', '.')
            .take(48)

        val qualitySuffix = variantLabel
            ?.replace(Regex("""[^a-zA-Z0-9]+"""), "")
            ?.takeIf { it.isNotBlank() }
            ?.let { "_$it" }
            .orEmpty()

        val finalBase = if (base.length >= 3 && !base.equals("master", true) && !base.equals("index", true)) {
            "$base$qualitySuffix"
        } else {
            val safeDomain = UrlNormalizer.sanitizePathSegment(domain).take(20)
            "linkloot_${safeDomain}_${uniqueSalt % 100000}$qualitySuffix"
        }

        return "$finalBase.$ext"
    }
}
