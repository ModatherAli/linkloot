package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.domain.model.HlsPlaylist
import com.modatherali.linkloot.domain.model.HlsSegment
import com.modatherali.linkloot.domain.model.HlsVariant
import kotlin.math.roundToLong

object HlsPlaylistParser {

    private val attributeRegex = Regex("""([A-Z0-9-]+)=("([^"]*)"|([^,]*))""")

    fun parse(playlistUrl: String, content: String): HlsPlaylist {
        val lines = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val isMaster = lines.any { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        return if (isMaster) {
            parseMaster(playlistUrl, lines)
        } else {
            parseMedia(playlistUrl, lines)
        }
    }

    private fun parseMaster(playlistUrl: String, lines: List<String>): HlsPlaylist {
        val variants = mutableListOf<HlsVariant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val attrs = parseAttributes(line.substringAfter(':', ""))
                var uriLine: String? = null
                var j = i + 1
                while (j < lines.size) {
                    if (!lines[j].startsWith("#")) {
                        uriLine = lines[j]
                        break
                    }
                    j++
                }
                val resolvedVariantUrl = uriLine?.let { UrlNormalizer.resolveUrl(playlistUrl, it) }
                if (resolvedVariantUrl != null) {
                    val resolution = attrs["RESOLUTION"]?.split('x', 'X')
                    val width = resolution?.getOrNull(0)?.toIntOrNull()
                    val height = resolution?.getOrNull(1)?.toIntOrNull()
                    val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull()
                        ?: attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull()
                    val codecs = attrs["CODECS"]
                    val nameAttr = attrs["NAME"]
                    val label = buildVariantLabel(nameAttr, height, bandwidth)
                    variants += HlsVariant(
                        url = resolvedVariantUrl,
                        width = width,
                        height = height,
                        bandwidthBps = bandwidth,
                        codecs = codecs,
                        label = label
                    )
                }
                i = j + 1
            } else {
                i++
            }
        }

        val sorted = variants
            .distinctBy { it.url }
            .sortedWith(
                compareByDescending<HlsVariant> { it.height ?: 0 }
                    .thenByDescending { it.bandwidthBps ?: 0L }
            )

        return HlsPlaylist(
            isMaster = true,
            variants = sorted
        )
    }

    private fun parseMedia(playlistUrl: String, lines: List<String>): HlsPlaylist {
        val segments = mutableListOf<HlsSegment>()
        var initMapUri: String? = null
        var isEncrypted = false
        var pendingDurationMs = 0L
        var totalDurationMs = 0L

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-KEY", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    val method = attrs["METHOD"]?.uppercase() ?: "NONE"
                    if (method != "NONE") {
                        isEncrypted = true
                    }
                }
                line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substringAfter(':', ""))
                    val mapUri = attrs["URI"]
                    if (mapUri != null) {
                        initMapUri = UrlNormalizer.resolveUrl(playlistUrl, mapUri)
                    }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val secondsStr = line.substringAfter(':', "").substringBefore(',').trim()
                    val seconds = secondsStr.toDoubleOrNull() ?: 0.0
                    pendingDurationMs = (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
                }
                !line.startsWith("#") -> {
                    val segmentUri = UrlNormalizer.resolveUrl(playlistUrl, line)
                    if (segmentUri != null) {
                        segments += HlsSegment(
                            uri = segmentUri,
                            durationMs = pendingDurationMs
                        )
                        totalDurationMs += pendingDurationMs
                    }
                    pendingDurationMs = 0L
                }
            }
        }

        return HlsPlaylist(
            isMaster = false,
            segments = segments,
            initMapUri = initMapUri,
            totalDurationMs = totalDurationMs,
            isEncrypted = isEncrypted
        )
    }

    private fun parseAttributes(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (match in attributeRegex.findAll(raw)) {
            val key = match.groupValues[1].uppercase()
            val quoted = match.groupValues[3]
            val unquoted = match.groupValues[4]
            val value = if (quoted.isNotEmpty()) quoted else unquoted
            result[key] = value.trim()
        }
        return result
    }

    private fun buildVariantLabel(name: String?, height: Int?, bandwidth: Long?): String {
        if (!name.isNullOrBlank()) return name
        if (height != null && height > 0) return "${height}p"
        if (bandwidth != null && bandwidth > 0) {
            val mbps = bandwidth / 1_000_000.0
            return if (mbps >= 1.0) {
                String.format(java.util.Locale.US, "%.1f Mbps", mbps)
            } else {
                "${bandwidth / 1000} kbps"
            }
        }
        return "Auto"
    }
}
