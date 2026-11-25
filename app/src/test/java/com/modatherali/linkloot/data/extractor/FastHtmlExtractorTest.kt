package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.domain.model.MediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FastHtmlExtractorTest {

    private val extractor = FastHtmlExtractor(OkHttpClient())

    @Test
    fun `extracts OpenGraph, JSON-LD, srcset highest width, video sources, and escaped script URLs`() {
        val html = """
            <!doctype html>
            <html>
            <head>
                <title>Test Gallery</title>
                <meta property="og:title" content="Featured Media Page" />
                <meta property="og:image" content="https://cdn.example.com/og-cover.jpg" />
                <meta property="og:video:secure_url" content="https://cdn.example.com/og-clip.mp4" />
                <script type="application/ld+json">
                {
                    "@context": "https://schema.org",
                    "@type": "VideoObject",
                    "name": "Demo",
                    "thumbnailUrl": "https://cdn.example.com/jsonld-thumb.webp",
                    "contentUrl": "https://cdn.example.com/jsonld-video.mp4"
                }
                </script>
            </head>
            <body>
                <img src="/fallback-small.jpg"
                     srcset="/img-400.jpg 400w, /img-1600.jpg 1600w, /img-800.jpg 800w"
                     width="1600" height="900" />
                <video poster="/poster.png" width="1280" height="720">
                    <source src="/clip.webm" type="video/webm" />
                </video>
                <div style="background-image: url('/bg-banner.png')"></div>
                <script>
                    window.__MEDIA__ = {
                        "hls": "https:\/\/stream.example.com\/live\/master.m3u8?token=abc",
                        "extra": "https:\u002F\u002Fcdn.example.com\u002Fextra-photo.png"
                    };
                </script>
            </body>
            </html>
        """.trimIndent()

        val output = extractor.extractFromHtml(html, "https://example.com/post")
        val urls = output.items.map { it.resolvedUrl }.toSet()

        assertEquals("Featured Media Page", output.pageTitle)
        assertFalse(output.looksLikeSpaShell)
        assertTrue(urls.contains("https://cdn.example.com/og-cover.jpg"))
        assertTrue(urls.contains("https://cdn.example.com/og-clip.mp4"))
        assertTrue(urls.contains("https://cdn.example.com/jsonld-thumb.webp"))
        assertTrue(urls.contains("https://cdn.example.com/jsonld-video.mp4"))
        assertTrue(urls.contains("https://example.com/img-1600.jpg"))
        assertFalse(urls.contains("https://example.com/img-400.jpg"))
        assertTrue(urls.contains("https://example.com/poster.png"))
        assertTrue(urls.contains("https://example.com/clip.webm"))
        assertTrue(urls.contains("https://example.com/bg-banner.png"))
        assertTrue(urls.contains("https://stream.example.com/live/master.m3u8?token=abc"))
        assertTrue(urls.contains("https://cdn.example.com/extra-photo.png"))

        val hlsItem = output.items.first { it.resolvedUrl.contains("master.m3u8") }
        assertTrue(hlsItem.isHls)
        assertEquals(MediaType.VIDEO, hlsItem.type)
    }

    @Test
    fun `detects SPA shell when body is empty root div`() {
        val spaHtml = """
            <html>
            <head><title>SPA App</title></head>
            <body>
                <div id="root"></div>
                <script src="/bundle.js"></script>
            </body>
            </html>
        """.trimIndent()

        val output = extractor.extractFromHtml(spaHtml, "https://spa.example.com/")
        assertTrue(output.looksLikeSpaShell)
    }
}
