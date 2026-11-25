package com.modatherali.linkloot.data.extractor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistParserTest {

    @Test
    fun `parses master playlist variants sorted descending by resolution and bandwidth`() {
        val master = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42e01e,mp4a.40.2"
            low/stream.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=4800000,RESOLUTION=1920x1080,CODECS="avc1.640028,mp4a.40.2"
            https://cdn.example.com/1080/stream.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2400000,RESOLUTION=1280x720
            /720/stream.m3u8
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse("https://example.com/video/master.m3u8", master)
        assertTrue(parsed.isMaster)
        assertEquals(3, parsed.variants.size)
        assertEquals("1080p", parsed.variants[0].label)
        assertEquals("https://cdn.example.com/1080/stream.m3u8", parsed.variants[0].url)
        assertEquals("720p", parsed.variants[1].label)
        assertEquals("https://example.com/720/stream.m3u8", parsed.variants[1].url)
        assertEquals("360p", parsed.variants[2].label)
        assertEquals("https://example.com/video/low/stream.m3u8", parsed.variants[2].url)
    }

    @Test
    fun `parses media playlist segments, init map, and sums duration`() {
        val media = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:4.500,
            seg0.m4s
            #EXTINF:5.500,
            seg1.m4s
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse("https://cdn.example.com/hls/720p.m3u8", media)
        assertFalse(parsed.isMaster)
        assertFalse(parsed.isEncrypted)
        assertEquals("https://cdn.example.com/hls/init.mp4", parsed.initMapUri)
        assertEquals(2, parsed.segments.size)
        assertEquals(10_000L, parsed.totalDurationMs)
    }

    @Test
    fun `detects encrypted HLS stream when EXT-X-KEY method is not NONE`() {
        val encrypted = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/key.bin"
            #EXTINF:5.0,
            seg0.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val parsed = HlsPlaylistParser.parse("https://cdn.example.com/private/index.m3u8", encrypted)
        assertTrue(parsed.isEncrypted)
    }
}
