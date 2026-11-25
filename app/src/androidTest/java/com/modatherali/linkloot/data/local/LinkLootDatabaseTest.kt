package com.modatherali.linkloot.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LinkLootDatabaseTest {

    private lateinit var db: LinkLootDatabase
    private lateinit var historyDao: HistoryDao
    private lateinit var downloadDao: DownloadDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LinkLootDatabase::class.java
        ).allowMainThreadQueries().build()
        historyDao = db.historyDao()
        downloadDao = db.downloadDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun historySaveAndCascadeDeleteWorks() = runTest {
        val entry = HistoryEntryEntity(
            pageUrl = "https://example.com/gallery",
            normalizedUrl = "https://example.com/gallery",
            domain = "example.com",
            pageTitle = "Gallery",
            thumbnailUrl = "https://example.com/1.jpg",
            imageCount = 2,
            videoCount = 1,
            lastAnalyzedAt = 1000L
        )
        val media = listOf(
            DiscoveredMediaEntity(
                id = "m1",
                historyId = 0L,
                mediaUrl = "https://example.com/1.jpg",
                resolvedUrl = "https://example.com/1.jpg",
                type = "IMAGE",
                format = "jpg",
                mimeType = "image/jpeg",
                width = 800,
                height = 600,
                fileSizeBytes = 12_000L,
                durationMs = null,
                qualityLabel = null,
                isHls = false,
                isEncrypted = false,
                hlsVariantsJson = "[]",
                isJunkFiltered = false,
                refererUrl = "https://example.com/gallery",
                headersJson = "{}",
                thumbnailUrl = null,
                supportsRange = true,
                sortOrder = 0
            )
        )

        val historyId = historyDao.saveExtractionSession(entry, media)
        val cached = historyDao.getMediaForHistory(historyId)
        assertEquals(1, cached.size)
        assertEquals("https://example.com/1.jpg", cached.first().mediaUrl)

        historyDao.deleteHistoryById(historyId)
        assertNull(historyDao.getById(historyId))
        assertTrue(historyDao.getMediaForHistory(historyId).isEmpty())
    }

    @Test
    fun downloadDaoUpdatesProgressAndStatus() = runTest {
        val task = DownloadTaskEntity(
            id = "dl-1",
            mediaUrl = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            domain = "example.com",
            fileName = "video.mp4",
            subfolder = "LinkLoot/Videos",
            mediaType = "VIDEO",
            mimeType = "video/mp4",
            isHls = false,
            status = "QUEUED",
            bytesDownloaded = 0L,
            totalBytes = 1000L,
            segmentsCompleted = 0,
            segmentsTotal = 0,
            tempPath = null,
            savedUri = null,
            savedDisplayPath = null,
            refererUrl = "https://example.com",
            headersJson = "{}",
            errorMessage = null,
            createdAt = 100L,
            updatedAt = 100L
        )

        downloadDao.upsertTask(task)
        assertEquals(1, downloadDao.observeActiveCount().first())

        downloadDao.updateStatus("dl-1", "DOWNLOADING", null, 200L)
        downloadDao.updateByteProgress("dl-1", 500L, 1000L, 250L)
        val inProgress = downloadDao.getTaskById("dl-1")
        assertEquals("DOWNLOADING", inProgress?.status)
        assertEquals(500L, inProgress?.bytesDownloaded)

        downloadDao.markCompleted(
            id = "dl-1",
            savedUri = "content://media/external/downloads/1",
            savedDisplayPath = "Downloads/LinkLoot/Videos/video.mp4",
            finalBytes = 1000L,
            updatedAt = 300L
        )
        assertEquals(0, downloadDao.observeActiveCount().first())
        assertEquals("COMPLETED", downloadDao.getTaskById("dl-1")?.status)

        // Ensure late pause/cancel updates never overwrite a COMPLETED download
        downloadDao.updateStatus("dl-1", "PAUSED", null, 400L)
        val rowsUpdated = downloadDao.updateStatusIfIn(
            id = "dl-1",
            status = "CANCELLED",
            allowedCurrentStatuses = listOf("QUEUED", "DOWNLOADING", "MUXING", "PAUSED", "FAILED"),
            errorMessage = null,
            updatedAt = 450L
        )
        assertEquals(0, rowsUpdated)
        assertEquals("COMPLETED", downloadDao.getTaskById("dl-1")?.status)
    }
}
