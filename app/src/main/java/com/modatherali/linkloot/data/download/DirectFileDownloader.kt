package com.modatherali.linkloot.data.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext

data class DirectDownloadResult(
    val tempFile: File,
    val totalBytes: Long,
    val mimeType: String
)

class DirectFileDownloader(
    private val okHttpClient: OkHttpClient,
    private val tempDir: File
) {
    private val contentRangeRegex = Regex("""bytes\s+\d+-\d+/(\d+|\*)""", RegexOption.IGNORE_CASE)
    private val totalOnlyRangeRegex = Regex("""bytes\s+\*/(\d+)""", RegexOption.IGNORE_CASE)

    init {
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
    }

    fun getTempFile(taskId: String): File = File(tempDir, "$taskId.part")

    fun deleteTempFile(taskId: String) {
        val f = getTempFile(taskId)
        if (f.exists()) f.delete()
    }

    suspend fun downloadToTemp(
        taskId: String,
        url: String,
        headers: Map<String, String>,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): DirectDownloadResult = withContext(Dispatchers.IO) {
        if (!tempDir.exists()) tempDir.mkdirs()
        val tempFile = getTempFile(taskId)
        executeDownload(
            tempFile = tempFile,
            url = url,
            headers = headers,
            allowRetryFresh = true,
            onProgress = onProgress
        )
    }

    private suspend fun executeDownload(
        tempFile: File,
        url: String,
        headers: Map<String, String>,
        allowRetryFresh: Boolean,
        onProgress: suspend (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): DirectDownloadResult {
        val existingBytes = if (tempFile.exists()) tempFile.length() else 0L
        val reqBuilder = Request.Builder().url(url).get()
        for ((k, v) in headers) {
            reqBuilder.header(k, v)
        }
        if (existingBytes > 0L) {
            reqBuilder.header("Range", "bytes=$existingBytes-")
        }

        val call = okHttpClient.newCall(reqBuilder.build())
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { response ->
            val mimeType = response.header("Content-Type").orEmpty()
                .substringBefore(';')
                .trim()
                .lowercase()

            if (response.code == 416) {
                val contentRange = response.header("Content-Range").orEmpty()
                val total = totalOnlyRangeRegex.find(contentRange)?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (total != null && existingBytes == total && total > 0L) {
                    onProgress(existingBytes, total)
                    return DirectDownloadResult(tempFile, total, mimeType)
                }
                if (allowRetryFresh) {
                    if (tempFile.exists()) tempFile.delete()
                    return executeDownload(
                        tempFile = tempFile,
                        url = url,
                        headers = headers,
                        allowRetryFresh = false,
                        onProgress = onProgress
                    )
                }
                throw IOException("HTTP 416 Range Not Satisfiable")
            }

            if (!response.isSuccessful && response.code != 206) {
                throw IOException("HTTP ${response.code} ${response.message}")
            }

            val isAppend = response.code == 206 && existingBytes > 0L
            val startOffset = if (isAppend) existingBytes else 0L
            val contentLength = response.header("Content-Length")?.toLongOrNull() ?: -1L
            val contentRangeHeader = response.header("Content-Range")
            val totalFromRange = contentRangeHeader?.let { header ->
                contentRangeRegex.find(header)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }
            val rangeHasUnknownTotal = contentRangeHeader?.trim()?.endsWith("/*") == true

            val totalBytes = when {
                totalFromRange != null -> totalFromRange
                rangeHasUnknownTotal -> -1L
                isAppend && contentLength > 0L -> startOffset + contentLength
                !isAppend && contentLength > 0L -> contentLength
                else -> -1L
            }

            val bodyStream = response.body.byteStream()
            var downloaded = startOffset
            var lastEmitTime = 0L
            var lastEmitBytes = downloaded

            onProgress(downloaded, totalBytes)

            FileOutputStream(tempFile, isAppend).use { output ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = bodyStream.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read

                    val now = System.currentTimeMillis()
                    if (now - lastEmitTime >= 300L || downloaded - lastEmitBytes >= 65_536L) {
                        lastEmitTime = now
                        lastEmitBytes = downloaded
                        onProgress(downloaded, totalBytes)
                    }
                }
                output.flush()
            }

            val finalTotal = if (totalBytes > 0L) totalBytes else downloaded
            onProgress(downloaded, finalTotal)

            return DirectDownloadResult(
                tempFile = tempFile,
                totalBytes = finalTotal,
                mimeType = mimeType
            )
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            throw t
        } finally {
            cancellationHandle?.dispose()
        }
    }
}
