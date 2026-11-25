package com.modatherali.linkloot.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlNormalizerTest {

    @Test
    fun `extractFirstUrl pulls URL from shared social text`() {
        val shared = "Look at this awesome gallery! https://example.com/posts/123?ref=share#top check it out"
        val extracted = UrlNormalizer.extractFirstUrl(shared)
        assertEquals("https://example.com/posts/123?ref=share#top", extracted)
    }

    @Test
    fun `normalize prepends https to bare domain and strips fragment`() {
        val normalized = UrlNormalizer.normalize("www.example.com/gallery/photo#section")
        assertEquals("https://www.example.com/gallery/photo", normalized)
    }

    @Test
    fun `normalize preserves http scheme and port`() {
        val normalized = UrlNormalizer.normalize("http://blog.example.org:8080/media")
        assertEquals("http://blog.example.org:8080/media", normalized)
    }

    @Test
    fun `normalize returns null for invalid or empty input`() {
        assertNull(UrlNormalizer.normalize(""))
        assertNull(UrlNormalizer.normalize("   "))
        assertNull(UrlNormalizer.normalize("not a url at all"))
        assertNull(UrlNormalizer.normalize("javascript:alert(1)"))
    }

    @Test
    fun `extractDomain strips www prefix and lowercases`() {
        assertEquals("example.com", UrlNormalizer.extractDomain("https://WWW.Example.COM/path"))
    }

    @Test
    fun `sanitizePathSegment replaces illegal path characters`() {
        assertEquals("site_a_b_c", UrlNormalizer.sanitizePathSegment("site:a/b*c?"))
    }

    @Test
    fun `extractFirstUrl preserves balanced parentheses and trims trailing punctuation`() {
        val text = "Read (https://en.wikipedia.org/wiki/Kotlin_(programming_language))!"
        assertEquals(
            "https://en.wikipedia.org/wiki/Kotlin_(programming_language)",
            UrlNormalizer.extractFirstUrl(text)
        )
    }

    @Test
    fun `resolveUrl handles protocol-relative and relative paths while ignoring data URIs`() {
        val base = "https://cdn.example.com/articles/post.html"
        assertEquals("https://media.example.com/a.jpg", UrlNormalizer.resolveUrl(base, "//media.example.com/a.jpg"))
        assertEquals("https://cdn.example.com/img/hero.png", UrlNormalizer.resolveUrl(base, "/img/hero.png"))
        assertNull(UrlNormalizer.resolveUrl(base, "data:image/png;base64,AAA"))
        assertNull(UrlNormalizer.resolveUrl(base, "blob:https://example.com/123"))
    }
}
