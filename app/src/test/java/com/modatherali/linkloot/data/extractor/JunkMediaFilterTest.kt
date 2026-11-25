package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JunkMediaFilterTest {

    @Test
    fun `filters 1x1 tracking pixels, favicons, and small icons under 100px`() {
        val pixel = MediaItem(
            id = "1",
            mediaUrl = "https://example.com/pixel.gif",
            type = MediaType.IMAGE,
            format = "gif",
            width = 1,
            height = 1
        )
        val favicon = MediaItem(
            id = "2",
            mediaUrl = "https://example.com/favicon.ico",
            type = MediaType.IMAGE,
            format = "ico"
        )
        val smallIcon = MediaItem(
            id = "3",
            mediaUrl = "https://example.com/icon.png",
            type = MediaType.IMAGE,
            format = "png",
            width = 48,
            height = 48
        )

        assertTrue(JunkMediaFilter.isJunk(pixel))
        assertTrue(JunkMediaFilter.isJunk(favicon))
        assertTrue(JunkMediaFilter.isJunk(smallIcon))
    }

    @Test
    fun `keeps legitimate high-res images and all videos`() {
        val photo = MediaItem(
            id = "4",
            mediaUrl = "https://example.com/photos/sunset.jpg",
            type = MediaType.IMAGE,
            format = "jpg",
            width = 1920,
            height = 1080,
            fileSizeBytes = 250_000L
        )
        val unknownSizePhoto = MediaItem(
            id = "5",
            mediaUrl = "https://example.com/gallery/cover.webp",
            type = MediaType.IMAGE,
            format = "webp"
        )
        val video = MediaItem(
            id = "6",
            mediaUrl = "https://example.com/sprite-demo.mp4",
            type = MediaType.VIDEO,
            format = "mp4"
        )

        assertFalse(JunkMediaFilter.isJunk(photo))
        assertFalse(JunkMediaFilter.isJunk(unknownSizePhoto))
        assertFalse(JunkMediaFilter.isJunk(video))
    }
}
