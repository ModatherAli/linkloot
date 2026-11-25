package com.modatherali.linkloot.data.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class DirectFileDownloaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: DirectFileDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = DirectFileDownloader(
            okHttpClient = OkHttpClient(),
            tempDir = tempFolder.newFolder("part_downloads")
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `downloads full file cleanly`() = runTest {
        val payload = ByteArray(4096) { (it % 251).toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/jpeg")
                .setBody(Buffer().write(payload))
        )

        val result = downloader.downloadToTemp(
            taskId = "task-full",
            url = server.url("/photo.jpg").toString(),
            headers = mapOf("Referer" to "https://example.com"),
            onProgress = { _, _ -> }
        )

        assertEquals(payload.size.toLong(), result.totalBytes)
        assertEquals("image/jpeg", result.mimeType)
        assertArrayEquals(payload, result.tempFile.readBytes())
    }

    @Test
    fun `resumes partial file using HTTP Range 206`() = runTest {
        val fullPayload = ByteArray(2048) { (it % 199).toByte() }
        val splitPoint = 768
        val firstChunk = fullPayload.copyOfRange(0, splitPoint)
        val secondChunk = fullPayload.copyOfRange(splitPoint, fullPayload.size)

        // Simulate existing interrupted .part file on disk
        val partFile = downloader.getTempFile("task-resume")
        partFile.writeBytes(firstChunk)

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "video/mp4")
                .setHeader("Content-Range", "bytes $splitPoint-${fullPayload.size - 1}/${fullPayload.size}")
                .setBody(Buffer().write(secondChunk))
        )

        val result = downloader.downloadToTemp(
            taskId = "task-resume",
            url = server.url("/video.mp4").toString(),
            headers = emptyMap(),
            onProgress = { _, _ -> }
        )

        val recorded = server.takeRequest()
        assertEquals("bytes=$splitPoint-", recorded.getHeader("Range"))
        assertEquals(fullPayload.size.toLong(), result.totalBytes)
        assertArrayEquals(fullPayload, result.tempFile.readBytes())
    }

    @Test
    fun `restarts from scratch when server ignores Range and returns 200`() = runTest {
        val fullPayload = "complete-file-content".encodeToByteArray()
        downloader.getTempFile("task-restart").writeBytes("stale-prefix".encodeToByteArray())

        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "image/png")
                .setBody(Buffer().write(fullPayload))
        )

        val result = downloader.downloadToTemp(
            taskId = "task-restart",
            url = server.url("/image.png").toString(),
            headers = emptyMap(),
            onProgress = { _, _ -> }
        )

        assertArrayEquals(fullPayload, result.tempFile.readBytes())
    }

    @Test
    fun `handles HTTP 416 when partial file is already complete`() = runTest {
        val fullPayload = "already-complete-bytes".encodeToByteArray()
        downloader.getTempFile("task-416-done").writeBytes(fullPayload)

        server.enqueue(
            MockResponse()
                .setResponseCode(416)
                .setHeader("Content-Range", "bytes */${fullPayload.size}")
        )

        val result = downloader.downloadToTemp(
            taskId = "task-416-done",
            url = server.url("/done.bin").toString(),
            headers = emptyMap(),
            onProgress = { _, _ -> }
        )

        assertEquals(fullPayload.size.toLong(), result.totalBytes)
        assertArrayEquals(fullPayload, result.tempFile.readBytes())
    }

    @Test
    fun `cancelling active download propagates CancellationException rather than IOException`() = runTest {
        val chunk = ByteArray(4096) { 1 }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", "1048576")
                .setBody(Buffer().write(chunk))
                .throttleBody(16, 200, TimeUnit.MILLISECONDS)
        )

        val started = CompletableDeferred<Unit>()
        var caught: Throwable? = null
        val job = launch {
            try {
                downloader.downloadToTemp(
                    taskId = "task-cancel",
                    url = server.url("/slow.mp4").toString(),
                    headers = emptyMap(),
                    onProgress = { bytes, _ ->
                        if (bytes > 0L) started.complete(Unit)
                    }
                )
            } catch (t: Throwable) {
                caught = t
            }
        }

        started.await()
        job.cancel()
        job.join()

        assertTrue("Expected CancellationException, got $caught", caught is CancellationException)
    }
}
