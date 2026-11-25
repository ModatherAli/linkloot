package com.modatherali.linkloot.ui.common

import android.text.format.DateUtils
import java.util.Locale
import kotlin.math.roundToInt

object Formatters {
    fun formatBytes(bytes: Long?): String {
        if (bytes == null || bytes < 0L) return ""
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    fun formatDuration(durationMs: Long?): String {
        if (durationMs == null || durationMs <= 0L) return ""
        val totalSec = (durationMs / 1000.0).roundToInt()
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatDimensions(width: Int?, height: Int?): String {
        if (width == null || height == null || width <= 0 || height <= 0) return ""
        return "${width}×${height}"
    }

    fun formatRelativeTime(timestampMs: Long): String {
        return DateUtils.getRelativeTimeSpanString(
            timestampMs,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
}
