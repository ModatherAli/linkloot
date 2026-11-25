package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.security.MessageDigest

data class FastExtractionOutput(
    val pageTitle: String,
    val resolvedPageUrl: String,
    val items: List<MediaItem>,
    val sessionHeaders: Map<String, String>,
    val looksLikeSpaShell: Boolean
)

class FastHtmlExtractor(private val okHttpClient: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    private val inlineMediaRegex = Regex(
        """https?://[^\s"'<>\\`]+?\.(?:mp4|m3u8|webm|mov|m4v|jpg|jpeg|png|webp|gif)(?:\?[^\s"'<>\\`]*)?""",
        RegexOption.IGNORE_CASE
    )

    private val cssBackgroundUrlRegex = Regex(
        """background(?:-image)?\s*:[^;]*url\(\s*['"]?([^'")\s]+)['"]?\s*\)""",
        RegexOption.IGNORE_CASE
    )

    suspend fun extractFromUrl(targetUrl: String): FastExtractionOutput = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(targetUrl)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val contentType = response.header("Content-Type").orEmpty().lowercase()

            // Direct media URL passed straight to analyzer
            if (isDirectMediaContentType(contentType) || hasDirectMediaExtension(finalUrl)) {
                val type = if (contentType.startsWith("video/") || isVideoUrl(finalUrl)) {
                    MediaType.VIDEO
                } else {
                    MediaType.IMAGE
                }
                val item = buildCandidate(
                    rawUrl = finalUrl,
                    baseUrl = finalUrl,
                    type = type,
                    mimeHint = contentType.substringBefore(';').trim(),
                    referer = finalUrl,
                    headers = mapOf("Referer" to finalUrl, "User-Agent" to DEFAULT_USER_AGENT)
                )
                return@use FastExtractionOutput(
                    pageTitle = UrlNormalizer.extractDomain(finalUrl),
                    resolvedPageUrl = finalUrl,
                    items = listOfNotNull(item),
                    sessionHeaders = mapOf("Referer" to finalUrl, "User-Agent" to DEFAULT_USER_AGENT),
                    looksLikeSpaShell = false
                )
            }

            val mime = contentType.substringBefore(';').trim()
            if (mime.isNotBlank() &&
                !mime.contains("html") &&
                !mime.contains("xml") &&
                !mime.startsWith("text/")
            ) {
                throw IllegalArgumentException("Unsupported content type: $mime")
            }

            // Cap body read at 4 MB to avoid OOM on unexpected oversized payloads
            val html = response.peekBody(4L * 1024L * 1024L).string()
            val setCookies = response.headers("Set-Cookie")
            val cookieHeader = setCookies
                .mapNotNull { it.substringBefore(';').trim().takeIf { kv -> kv.contains('=') } }
                .joinToString("; ")
            val headers = buildMap {
                put("Referer", finalUrl)
                put("User-Agent", DEFAULT_USER_AGENT)
                if (cookieHeader.isNotBlank()) {
                    put("Cookie", cookieHeader)
                }
            }
            extractFromHtml(html = html, baseUrl = finalUrl, sessionHeaders = headers)
        }
    }

    fun extractFromHtml(
        html: String,
        baseUrl: String,
        sessionHeaders: Map<String, String> = mapOf("Referer" to baseUrl, "User-Agent" to DEFAULT_USER_AGENT)
    ): FastExtractionOutput {
        val doc = Jsoup.parse(html, baseUrl)
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() }
            ?: UrlNormalizer.extractDomain(baseUrl)

        val candidates = linkedMapOf<String, MediaItem>()

        fun addCandidate(
            rawUrl: String?,
            typeHint: MediaType? = null,
            width: Int? = null,
            height: Int? = null,
            mimeHint: String = "",
            thumbnailUrl: String? = null
        ) {
            val resolved = UrlNormalizer.resolveUrl(baseUrl, rawUrl) ?: return
            val inferredType = typeHint ?: inferMediaType(resolved, mimeHint)
            val format = inferFormat(resolved, mimeHint)
            val isHls = format == "m3u8" || mimeHint.contains("mpegurl", ignoreCase = true)
            val existing = candidates[resolved]
            val newItem = MediaItem(
                id = stableId(resolved),
                mediaUrl = resolved,
                resolvedUrl = resolved,
                type = if (existing?.type == MediaType.VIDEO || inferredType == MediaType.VIDEO) {
                    MediaType.VIDEO
                } else {
                    inferredType
                },
                format = existing?.format?.ifBlank { format } ?: format,
                mimeType = existing?.mimeType?.ifBlank { mimeHint } ?: mimeHint,
                width = existing?.width ?: width,
                height = existing?.height ?: height,
                isHls = existing?.isHls == true || isHls,
                refererUrl = baseUrl,
                headers = sessionHeaders,
                thumbnailUrl = existing?.thumbnailUrl ?: thumbnailUrl
            )
            candidates[resolved] = newItem
        }

        // 1. OpenGraph & Twitter Cards
        val metaSelectors = listOf(
            "meta[property=og:image]" to MediaType.IMAGE,
            "meta[property=og:image:secure_url]" to MediaType.IMAGE,
            "meta[property=og:image:url]" to MediaType.IMAGE,
            "meta[name=twitter:image]" to MediaType.IMAGE,
            "meta[name=twitter:image:src]" to MediaType.IMAGE,
            "meta[property=twitter:image]" to MediaType.IMAGE,
            "meta[property=og:video]" to MediaType.VIDEO,
            "meta[property=og:video:secure_url]" to MediaType.VIDEO,
            "meta[property=og:video:url]" to MediaType.VIDEO,
            "meta[name=twitter:player:stream]" to MediaType.VIDEO,
            "meta[property=twitter:player:stream]" to MediaType.VIDEO
        )
        for ((selector, type) in metaSelectors) {
            doc.select(selector).forEach { el ->
                val content = el.attr("content")
                if (type == MediaType.VIDEO && content.contains(".html", ignoreCase = true)) {
                    return@forEach
                }
                addCandidate(rawUrl = content, typeHint = type)
            }
        }

        // 2. JSON-LD blocks
        doc.select("script[type=application/ld+json]").forEach { script ->
            val rawJson = script.data().trim()
            if (rawJson.isNotEmpty()) {
                runCatching {
                    val element = json.parseToJsonElement(rawJson)
                    extractFromJsonLd(element) { url, typeHint ->
                        addCandidate(rawUrl = url, typeHint = typeHint)
                    }
                }
            }
        }

        // 3. <picture><source>
        doc.select("picture source").forEach { source ->
            val bestFromSrcset = pickBestFromSrcset(source.attr("srcset"))
                ?: pickBestFromSrcset(source.attr("data-srcset"))
            val url = bestFromSrcset ?: source.attr("src").ifBlank { source.attr("data-src") }
            addCandidate(
                rawUrl = url,
                typeHint = MediaType.IMAGE,
                mimeHint = source.attr("type")
            )
        }

        // 4. <img> tags
        doc.select("img").forEach { img ->
            val srcsetBest = pickBestFromSrcset(img.attr("srcset"))
                ?: pickBestFromSrcset(img.attr("data-srcset"))
            val directSrc = listOf(
                img.attr("data-original"),
                img.attr("data-lazy-src"),
                img.attr("data-src"),
                img.attr("src")
            ).firstOrNull { it.isNotBlank() }

            val chosen = srcsetBest ?: directSrc
            val w = img.attr("width").toIntOrNull()
            val h = img.attr("height").toIntOrNull()
            addCandidate(
                rawUrl = chosen,
                typeHint = MediaType.IMAGE,
                width = w,
                height = h
            )
        }

        // 5. <video> and nested <source>
        doc.select("video").forEach { video ->
            val posterUrl = UrlNormalizer.resolveUrl(baseUrl, video.attr("poster"))
            if (posterUrl != null) {
                addCandidate(rawUrl = posterUrl, typeHint = MediaType.IMAGE)
            }
            val videoW = video.attr("width").toIntOrNull()
            val videoH = video.attr("height").toIntOrNull()
            val videoSrc = video.attr("src").ifBlank { video.attr("data-src") }
            if (videoSrc.isNotBlank()) {
                addCandidate(
                    rawUrl = videoSrc,
                    typeHint = MediaType.VIDEO,
                    width = videoW,
                    height = videoH,
                    thumbnailUrl = posterUrl
                )
            }
            video.select("source").forEach { source ->
                val srcUrl = source.attr("src").ifBlank { source.attr("data-src") }
                addCandidate(
                    rawUrl = srcUrl,
                    typeHint = MediaType.VIDEO,
                    width = videoW,
                    height = videoH,
                    mimeHint = source.attr("type"),
                    thumbnailUrl = posterUrl
                )
            }
        }

        // 6. CSS inline styles & <style> tags
        doc.select("[style]").forEach { el ->
            val style = el.attr("style")
            for (match in cssBackgroundUrlRegex.findAll(style)) {
                addCandidate(rawUrl = match.groupValues[1], typeHint = MediaType.IMAGE)
            }
        }
        doc.select("style").forEach { styleEl ->
            for (match in cssBackgroundUrlRegex.findAll(styleEl.data())) {
                addCandidate(rawUrl = match.groupValues[1], typeHint = MediaType.IMAGE)
            }
        }

        // 7. Inline <script> URL scan (handles JSON-escaped slashes \/ and \u002F)
        doc.select("script:not([type=application/ld+json])").forEach { scriptEl ->
            val scriptData = scriptEl.data()
            if (scriptData.isBlank()) return@forEach
            val unescaped = scriptData
                .replace("\\/", "/")
                .replace("\\u002F", "/", ignoreCase = true)
                .replace("\\u0026", "&", ignoreCase = true)
                .replace("\\u003D", "=", ignoreCase = true)
                .replace("\\u003F", "?", ignoreCase = true)
            for (match in inlineMediaRegex.findAll(unescaped)) {
                addCandidate(rawUrl = match.value)
            }
        }

        val items = candidates.values.toList()
        val nonJunkImages = items.count { it.type == MediaType.IMAGE && !JunkMediaFilter.isJunk(it) }
        val videosCount = items.count { it.type == MediaType.VIDEO }
        val hasSpaMarkers = html.contains("id=\"root\"></div>", ignoreCase = true) ||
            html.contains("id=\"app\"></div>", ignoreCase = true) ||
            html.contains("__NEXT_DATA__") ||
            html.contains("data-reactroot") ||
            doc.select("noscript").text().contains("javascript", ignoreCase = true)

        val looksLikeSpaShell = (videosCount == 0 && nonJunkImages <= 1) ||
            (hasSpaMarkers && videosCount == 0 && nonJunkImages <= 2)

        return FastExtractionOutput(
            pageTitle = title,
            resolvedPageUrl = baseUrl,
            items = items,
            sessionHeaders = sessionHeaders,
            looksLikeSpaShell = looksLikeSpaShell
        )
    }

    private fun extractFromJsonLd(
        element: JsonElement,
        onFound: (url: String, typeHint: MediaType?) -> Unit
    ) {
        when (element) {
            is JsonArray -> element.forEach { extractFromJsonLd(it, onFound) }
            is JsonObject -> {
                val typeStr = (element["@type"] as? JsonPrimitive)?.content?.lowercase().orEmpty()
                for ((key, value) in element) {
                    val lowerKey = key.lowercase()
                    when {
                        lowerKey in listOf("contenturl", "embedurl") -> {
                            val hint = if (typeStr.contains("video")) MediaType.VIDEO else null
                            if (value is JsonPrimitive && value.isString) {
                                onFound(value.content, hint)
                            } else {
                                extractFromJsonLd(value, onFound)
                            }
                        }
                        lowerKey in listOf("image", "thumbnailurl", "thumbnail") -> {
                            when (value) {
                                is JsonPrimitive -> if (value.isString) onFound(value.content, MediaType.IMAGE)
                                is JsonArray -> value.forEach { child ->
                                    if (child is JsonPrimitive && child.isString) {
                                        onFound(child.content, MediaType.IMAGE)
                                    } else {
                                        extractFromJsonLd(child, onFound)
                                    }
                                }
                                is JsonObject -> {
                                    (value["url"] as? JsonPrimitive)?.takeIf { it.isString }?.let {
                                        onFound(it.content, MediaType.IMAGE)
                                    }
                                    (value["contentUrl"] as? JsonPrimitive)?.takeIf { it.isString }?.let {
                                        onFound(it.content, MediaType.IMAGE)
                                    }
                                }
                            }
                        }
                        lowerKey == "url" && (typeStr.contains("image") || typeStr.contains("video")) -> {
                            if (value is JsonPrimitive && value.isString) {
                                val hint = if (typeStr.contains("video")) MediaType.VIDEO else MediaType.IMAGE
                                onFound(value.content, hint)
                            }
                        }
                        else -> extractFromJsonLd(value, onFound)
                    }
                }
            }
            is JsonPrimitive -> Unit
        }
    }

    fun pickBestFromSrcset(srcset: String): String? {
        if (srcset.isBlank()) return null
        var bestUrl: String? = null
        var bestScore = -1.0

        val entries = srcset.split(',')
        for (entry in entries) {
            val parts = entry.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (parts.isEmpty()) continue
            val url = parts[0]
            val descriptor = parts.getOrNull(1)?.lowercase().orEmpty()
            val score = when {
                descriptor.endsWith("w") -> descriptor.dropLast(1).toDoubleOrNull() ?: 0.0
                descriptor.endsWith("x") -> (descriptor.dropLast(1).toDoubleOrNull() ?: 1.0) * 1000.0
                else -> 1.0
            }
            if (score > bestScore) {
                bestScore = score
                bestUrl = url
            }
        }
        return bestUrl
    }

    private fun buildCandidate(
        rawUrl: String,
        baseUrl: String,
        type: MediaType,
        mimeHint: String,
        referer: String,
        headers: Map<String, String>
    ): MediaItem? {
        val resolved = UrlNormalizer.resolveUrl(baseUrl, rawUrl) ?: return null
        val format = inferFormat(resolved, mimeHint)
        val isHls = format == "m3u8" || mimeHint.contains("mpegurl", ignoreCase = true)
        return MediaItem(
            id = stableId(resolved),
            mediaUrl = resolved,
            resolvedUrl = resolved,
            type = type,
            format = format,
            mimeType = mimeHint,
            isHls = isHls,
            refererUrl = referer,
            headers = headers
        )
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

        fun stableId(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }

        fun isVideoUrl(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return path.endsWith(".mp4") ||
                path.endsWith(".m3u8") ||
                path.endsWith(".webm") ||
                path.endsWith(".mov") ||
                path.endsWith(".m4v")
        }

        fun hasDirectMediaExtension(url: String): Boolean {
            val path = url.substringBefore('?').substringBefore('#').lowercase()
            return isVideoUrl(url) ||
                path.endsWith(".jpg") ||
                path.endsWith(".jpeg") ||
                path.endsWith(".png") ||
                path.endsWith(".webp") ||
                path.endsWith(".gif")
        }

        fun isDirectMediaContentType(contentType: String): Boolean {
            return contentType.startsWith("image/") ||
                contentType.startsWith("video/") ||
                contentType.contains("mpegurl")
        }

        fun inferMediaType(url: String, mimeHint: String = ""): MediaType {
            val lowerMime = mimeHint.lowercase()
            if (lowerMime.startsWith("video/") || lowerMime.contains("mpegurl")) {
                return MediaType.VIDEO
            }
            if (lowerMime.startsWith("image/")) {
                return MediaType.IMAGE
            }
            return if (isVideoUrl(url)) MediaType.VIDEO else MediaType.IMAGE
        }

        fun inferFormat(url: String, mimeHint: String = ""): String {
            val ext = url.substringBefore('?').substringBefore('#')
                .substringAfterLast('/', "")
                .substringAfterLast('.', "")
                .lowercase()
            if (ext in setOf("mp4", "m3u8", "webm", "mov", "m4v", "jpg", "jpeg", "png", "webp", "gif", "ico", "svg")) {
                return if (ext == "jpeg") "jpg" else ext
            }
            return when {
                mimeHint.contains("mpegurl", true) -> "m3u8"
                mimeHint.contains("mp4", true) -> "mp4"
                mimeHint.contains("webm", true) -> "webm"
                mimeHint.contains("png", true) -> "png"
                mimeHint.contains("webp", true) -> "webp"
                mimeHint.contains("gif", true) -> "gif"
                mimeHint.contains("jpeg", true) || mimeHint.contains("jpg", true) -> "jpg"
                else -> ""
            }
        }
    }
}
