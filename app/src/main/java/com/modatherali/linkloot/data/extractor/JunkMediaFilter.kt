package com.modatherali.linkloot.data.extractor

import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType

object JunkMediaFilter {

    private val junkKeywords = listOf(
        "favicon",
        "apple-touch-icon",
        "mask-icon",
        "/sprite",
        "sprite.",
        "spritesheet",
        "spacer",
        "1x1",
        "pixel.gif",
        "blank.gif",
        "clear.gif",
        "tracking",
        "beacon",
        "analytics",
        "/ads/",
        "doubleclick"
    )

    fun isJunk(item: MediaItem, minImageSizePx: Int = 100): Boolean {
        if (item.type == MediaType.VIDEO) return false

        val lowerUrl = item.resolvedUrl.lowercase()
        val lowerFormat = item.format.lowercase()
        if (lowerFormat == "ico" || lowerUrl.endsWith(".ico") || lowerUrl.contains(".ico?")) {
            return true
        }

        if (junkKeywords.any { lowerUrl.contains(it) }) {
            // Keep if known large image dimensions prove it's actually a real photo
            val w = item.width
            val h = item.height
            if (w == null || h == null || w < minImageSizePx || h < minImageSizePx) {
                return true
            }
        }

        val width = item.width
        val height = item.height
        if (width != null && height != null) {
            if (width <= 1 && height <= 1) return true
            if (width < minImageSizePx && height < minImageSizePx) return true
        } else if ((width != null && width < 32) || (height != null && height < 32)) {
            return true
        }

        val size = item.fileSizeBytes
        if (size != null && size in 1..256 && (lowerFormat == "gif" || lowerFormat == "png")) {
            // 1x1 transparent GIF/PNG trackers are typically < 100 bytes
            return true
        }

        return false
    }

    fun applyFilter(items: List<MediaItem>, minImageSizePx: Int = 100): List<MediaItem> {
        return items.map { item ->
            item.copy(isJunkFiltered = isJunk(item, minImageSizePx))
        }
    }
}
