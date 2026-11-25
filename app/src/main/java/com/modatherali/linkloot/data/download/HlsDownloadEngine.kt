package com.modatherali.linkloot.data.download

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.modatherali.linkloot.data.extractor.HlsPlaylistParser
import com.modatherali.linkloot.domain.model.HlsPlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EncryptedStreamException(message: String) : IOException(message)

class HlsDownloadEngine(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val tempHlsRoot: File
) {
    init {
        if (!tempHlsRoot.exists()) tempHlsRoot.mkdirs()
    }

    fun getTaskDir(taskId: String): File = File(tempHlsRoot, taskId)

    fun cleanupTaskDir(taskId: String) {
        val dir = getTaskDir(taskId)
        if (dir.exists()) dir.deleteRecursively()
    }

    suspend fun downloadAndMuxToMp4(
        taskId: String,
        playlistUrl: String,
        headers: Map<String, String>,
        onSegmentProgress: suspend (completed: Int, total: Int, bytesDownloaded: Long) -> Unit,
        onMuxingStarted: suspend () -> Unit
    ): File = withContext(Dispatchers.IO) {
        val taskDir = getTaskDir(taskId)
        if (!taskDir.exists()) taskDir.mkdirs()

        val mediaPlaylist = resolveMediaPlaylist(playlistUrl, headers)
        if (mediaPlaylist.isEncrypted) {
            throw EncryptedStreamException("HLS stream is encrypted (#EXT-X-KEY)")
        }
        if (mediaPlaylist.segments.isEmpty()) {
            throw IOException("HLS playlist contains no media segments")
        }

        val isFmp4 = mediaPlaylist.initMapUri != null
        val segmentExt = if (isFmp4) "m4s" else "ts"
        var totalBytesDownloaded = 0L

        // Download init map segment if present (fMP4 HLS)
        var localInitFileName: String? = null
        mediaPlaylist.initMapUri?.let { initUri ->
            val initFile = File(taskDir, "init.mp4")
            if (!initFile.exists() || initFile.length() == 0L) {
                downloadSegmentToFile(initUri, headers, initFile)
            }
            totalBytesDownloaded += initFile.length()
            localInitFileName = initFile.name
        }

        val totalSegments = mediaPlaylist.segments.size
        val localSegmentNames = mutableListOf<String>()
        var lastEmitTime = 0L

        mediaPlaylist.segments.forEachIndexed { index, segment ->
            coroutineContext.ensureActive()
            val segName = "seg_%05d.%s".format(index, segmentExt)
            val segFile = File(taskDir, segName)
            if (!segFile.exists() || segFile.length() == 0L) {
                downloadSegmentToFile(segment.uri, headers, segFile)
            }
            totalBytesDownloaded += segFile.length()
            localSegmentNames += segName
            val now = System.currentTimeMillis()
            if (index == totalSegments - 1 || now - lastEmitTime >= 250L) {
                lastEmitTime = now
                onSegmentProgress(index + 1, totalSegments, totalBytesDownloaded)
            }
        }

        // Write local m3u8 manifest pointing to local files
        val localPlaylistFile = File(taskDir, "local.m3u8")
        buildLocalPlaylist(
            file = localPlaylistFile,
            initMapName = localInitFileName,
            segments = mediaPlaylist.segments.zip(localSegmentNames)
        )

        onMuxingStarted()
        val outputMp4 = File(taskDir, "output.mp4")
        if (outputMp4.exists()) outputMp4.delete()

        muxLocalHlsToMp4(localPlaylistFile, outputMp4)
        outputMp4
    }

    private suspend fun resolveMediaPlaylist(
        url: String,
        headers: Map<String, String>
    ): HlsPlaylist {
        val (resolvedUrl, text) = fetchText(url, headers)
        val parsed = HlsPlaylistParser.parse(resolvedUrl, text)
        if (!parsed.isMaster) return parsed

        val bestVariant = parsed.variants.firstOrNull()
            ?: throw IOException("Master HLS playlist has no variants")
        val (resolvedVariantUrl, mediaText) = fetchText(bestVariant.url, headers)
        return HlsPlaylistParser.parse(resolvedVariantUrl, mediaText)
    }

    private suspend fun fetchText(url: String, headers: Map<String, String>): Pair<String, String> {
        val builder = Request.Builder().url(url).get()
        for ((k, v) in headers) builder.header(k, v)
        val call = okHttpClient.newCall(builder.build())
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            return call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} fetching $url")
                resp.request.url.toString() to resp.body.string()
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            throw t
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private suspend fun downloadSegmentToFile(
        url: String,
        headers: Map<String, String>,
        targetFile: File
    ) {
        val tmpFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        val builder = Request.Builder().url(url).get()
        for ((k, v) in headers) builder.header(k, v)
        val call = okHttpClient.newCall(builder.build())
        val cancellationHandle = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} downloading segment")
                FileOutputStream(tmpFile, false).use { out ->
                    val buffer = ByteArray(16 * 1024)
                    val stream = resp.body.byteStream()
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = stream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                    }
                    out.flush()
                }
            }
        } catch (t: Throwable) {
            coroutineContext.ensureActive()
            throw t
        } finally {
            cancellationHandle?.dispose()
        }
        if (targetFile.exists()) targetFile.delete()
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
    }

    private fun buildLocalPlaylist(
        file: File,
        initMapName: String?,
        segments: List<Pair<com.modatherali.linkloot.domain.model.HlsSegment, String>>
    ) {
        val maxDurationSec = segments.maxOfOrNull { (seg, _) ->
            ((seg.durationMs + 999L) / 1000L).coerceAtLeast(1L)
        } ?: 10L

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-TARGETDURATION:$maxDurationSec")
        sb.appendLine("#EXT-X-MEDIA-SEQUENCE:0")
        sb.appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        if (initMapName != null) {
            sb.appendLine("#EXT-X-MAP:URI=\"$initMapName\"")
        }
        for ((seg, localName) in segments) {
            val sec = (seg.durationMs / 1000.0).coerceAtLeast(0.1)
            sb.appendLine(String.format(java.util.Locale.US, "#EXTINF:%.3f,", sec))
            sb.appendLine(localName)
        }
        sb.appendLine("#EXT-X-ENDLIST")
        file.writeText(sb.toString())
    }

    @OptIn(UnstableApi::class)
    private suspend fun muxLocalHlsToMp4(
        localPlaylistFile: File,
        outputMp4: File
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                IOException("HLS muxing failed: ${exportException.message}", exportException)
                            )
                        }
                    }
                })
                .build()

            cont.invokeOnCancellation {
                mainHandler.post {
                    runCatching { transformer.cancel() }
                }
            }

            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(localPlaylistFile))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()

            transformer.start(mediaItem, outputMp4.absolutePath)
        }
    }
}
