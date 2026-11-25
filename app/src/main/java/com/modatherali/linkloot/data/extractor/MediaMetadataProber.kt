package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.data.download.CookieHeaderHelper
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class MediaMetadataProber(private val okHttpClient: OkHttpClient) {

    private val contentRangeTotalRegex = Regex("""bytes\s+\d+-\d+/(\d+)""", RegexOption.IGNORE_CASE)

    suspend fun probeAll(items: List<MediaItem>): List<MediaItem> = coroutineScope {
        val semaphore = Semaphore(6)
        items.map { item ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching { probeItem(item) }.getOrDefault(item)
                }
            }
        }.awaitAll()
    }

    private suspend fun probeItem(item: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val headers = CookieHeaderHelper.buildEffectiveHeaders(
            targetUrl = item.resolvedUrl,
            refererUrl = item.refererUrl,
            baseHeaders = item.headers
        )

        if (item.isHls || item.format == "m3u8") {
            return@withContext probeHlsItem(item, headers)
        }

        // First try HEAD
        val headRequest = buildRequest(item.resolvedUrl, headers).head().build()
        var probed = okHttpClient.newCall(headRequest).execute().use { response ->
            if (response.isSuccessful) {
                extractMetadataFromResponse(item, response, fromRangeGet = false)
            } else {
                null
            }
        }

        // If HEAD failed (e.g. 405 Method Not Allowed) or gave no content-length, try Range 0-0 GET
        if (probed == null || probed.fileSizeBytes == null) {
            val rangeRequest = buildRequest(item.resolvedUrl, headers)
                .header("Range", "bytes=0-0")
                .get()
                .build()
            okHttpClient.newCall(rangeRequest).execute().use { response ->
                if (response.isSuccessful || response.code == 206) {
                    val rangeProbed = extractMetadataFromResponse(
                        probed ?: item,
                        response,
                        fromRangeGet = true
                    )
                    probed = rangeProbed
                }
            }
        }

        probed ?: item
    }

    private fun probeHlsItem(item: MediaItem, headers: Map<String, String>): MediaItem {
        val request = buildRequest(item.resolvedUrl, headers).get().build()
        val (finalPlaylistUrl, bodyText) = okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return item
            response.request.url.toString() to response.body.string()
        }

        val parsed = HlsPlaylistParser.parse(finalPlaylistUrl, bodyText)
        if (parsed.isMaster && parsed.variants.isNotEmpty()) {
            val best = parsed.variants.first()
            // Probe the best variant media playlist to obtain duration and encryption flag
            val bestPlaylist = runCatching {
                val variantReq = buildRequest(best.url, headers).get().build()
                okHttpClient.newCall(variantReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        HlsPlaylistParser.parse(resp.request.url.toString(), resp.body.string())
                    } else null
                }
            }.getOrNull()

            return item.copy(
                resolvedUrl = finalPlaylistUrl,
                type = MediaType.VIDEO,
                format = "m3u8",
                mimeType = item.mimeType.ifBlank { "application/vnd.apple.mpegurl" },
                isHls = true,
                hlsVariants = parsed.variants,
                width = best.width ?: item.width,
                height = best.height ?: item.height,
                qualityLabel = best.label,
                durationMs = bestPlaylist?.totalDurationMs?.takeIf { it > 0 } ?: item.durationMs,
                isEncrypted = bestPlaylist?.isEncrypted == true
            )
        } else {
            return item.copy(
                resolvedUrl = finalPlaylistUrl,
                type = MediaType.VIDEO,
                format = "m3u8",
                mimeType = item.mimeType.ifBlank { "application/vnd.apple.mpegurl" },
                isHls = true,
                durationMs = parsed.totalDurationMs.takeIf { it > 0 } ?: item.durationMs,
                isEncrypted = parsed.isEncrypted
            )
        }
    }

    private fun extractMetadataFromResponse(
        item: MediaItem,
        response: Response,
        fromRangeGet: Boolean
    ): MediaItem {
        val resolvedUrl = response.request.url.toString()
        val rawContentType = response.header("Content-Type").orEmpty()
            .substringBefore(';')
            .trim()
            .lowercase()

        val contentRange = response.header("Content-Range")
        val rangeTotal = contentRange?.let { header ->
            contentRangeTotalRegex.find(header)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }

        val contentLength = response.header("Content-Length")?.toLongOrNull()
            ?.takeIf { !fromRangeGet || response.code == 200 }

        val finalSize = rangeTotal ?: contentLength ?: item.fileSizeBytes
        val acceptRanges = response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
        val supportsRange = item.supportsRange || response.code == 206 || acceptRanges

        val mime = rawContentType.ifBlank { item.mimeType }
        val inferredType = when {
            mime.startsWith("video/") || mime.contains("mpegurl") -> MediaType.VIDEO
            mime.startsWith("image/") -> MediaType.IMAGE
            else -> item.type
        }
        val format = FastHtmlExtractor.inferFormat(resolvedUrl, mime).ifBlank { item.format }
        val isHls = item.isHls || format == "m3u8" || mime.contains("mpegurl")

        return item.copy(
            resolvedUrl = resolvedUrl,
            type = inferredType,
            format = format,
            mimeType = mime,
            fileSizeBytes = finalSize,
            isHls = isHls,
            supportsRange = supportsRange
        )
    }

    private fun buildRequest(url: String, headers: Map<String, String>): Request.Builder {
        val builder = Request.Builder().url(url)
        for ((k, v) in headers) {
            builder.header(k, v)
        }
        return builder
    }
}
