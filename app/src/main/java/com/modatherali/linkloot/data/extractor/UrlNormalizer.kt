package com.modatherali.linkloot.data.extractor

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

object UrlNormalizer {

    private val urlInTextRegex = Regex(
        """(https?://[^\s"'<>]+)""",
        RegexOption.IGNORE_CASE
    )

    private val bareHostRegex = Regex(
        """^(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(?::\d{1,5})?(?:/[^\s]*)?$"""
    )

    private val ignoredPrefixes = listOf("data:", "blob:", "javascript:", "about:", "mailto:", "tel:")

    fun extractFirstUrl(rawText: String): String? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return null

        val matched = urlInTextRegex.find(trimmed)?.groupValues?.getOrNull(1)
        if (matched != null) {
            return trimTrailingPunctuation(matched)
        }

        val firstToken = trimmed.lineSequence()
            .flatMap { it.split(Regex("""\s+""")).asSequence() }
            .firstOrNull { token ->
                val cleaned = trimTrailingPunctuation(token)
                bareHostRegex.matches(cleaned)
            }

        return firstToken?.let { "https://${trimTrailingPunctuation(it)}" }
    }

    fun normalize(rawInput: String): String? {
        val candidate = extractFirstUrl(rawInput) ?: return null
        val withScheme = if (
            candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
        ) {
            candidate
        } else {
            "https://$candidate"
        }

        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (parsed.scheme != "http" && parsed.scheme != "https") return null
        if (parsed.host.isBlank()) return null

        return parsed.newBuilder()
            .fragment(null)
            .build()
            .toString()
    }

    fun extractDomain(url: String): String {
        val host = url.toHttpUrlOrNull()?.host
            ?: runCatching { URI(url).host }.getOrNull()
            ?: "unknown"
        return host.removePrefix("www.").lowercase().ifBlank { "unknown" }
    }

    fun sanitizePathSegment(segment: String): String {
        val cleaned = segment
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001F]+"""), "_")
            .trim(' ', '.', '_')
        return cleaned.ifBlank { "linkloot" }
    }

    fun resolveUrl(baseUrl: String, candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val trimmed = candidate.trim().trim('"', '\'')
        if (trimmed.isEmpty()) return null
        if (ignoredPrefixes.any { trimmed.startsWith(it, ignoreCase = true) }) return null

        val base = baseUrl.toHttpUrlOrNull() ?: return null
        val sanitizedCandidate = if (trimmed.contains(' ')) trimmed.replace(" ", "%20") else trimmed
        val resolved = when {
            sanitizedCandidate.startsWith("//") -> "${base.scheme}:$sanitizedCandidate".toHttpUrlOrNull()
            else -> base.resolve(sanitizedCandidate)
        } ?: return null

        if (resolved.scheme != "http" && resolved.scheme != "https") return null
        return resolved.newBuilder().fragment(null).build().toString()
    }

    private fun trimTrailingPunctuation(value: String): String {
        var result = value.trim()
        while (result.isNotEmpty() && result.last() in charArrayOf('.', ',', ';', '!', '?', ')', ']', '"', '\'')) {
            if (result.last() == ')' && result.count { it == '(' } >= result.count { it == ')' }) {
                break
            }
            result = result.dropLast(1)
        }
        return result
    }
}
