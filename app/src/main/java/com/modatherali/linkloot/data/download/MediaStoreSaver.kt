package com.modatherali.linkloot.data.download

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.modatherali.linkloot.data.extractor.UrlNormalizer
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.domain.model.SubfolderStrategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class SavedMediaLocation(
    val uriString: String,
    val displayPath: String,
    val byteSize: Long
)

class MediaStoreSaver(private val context: Context) {

    private val saveMutex = Mutex()

    fun resolveSubfolder(
        strategy: SubfolderStrategy,
        domain: String,
        mediaType: MediaType
    ): String {
        val typeFolder = if (mediaType == MediaType.VIDEO) "Videos" else "Images"
        return when (strategy) {
            SubfolderStrategy.BY_MEDIA_TYPE -> "LinkLoot/$typeFolder"
            SubfolderStrategy.BY_DOMAIN_AND_TYPE -> {
                val safeDomain = UrlNormalizer.sanitizePathSegment(domain)
                "LinkLoot/$safeDomain/$typeFolder"
            }
        }
    }

    suspend fun saveFile(
        sourceFile: File,
        desiredFileName: String,
        mimeType: String,
        subfolder: String
    ): SavedMediaLocation = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || sourceFile.length() == 0L) {
            throw IOException("Source file missing or empty")
        }

        val safeName = UrlNormalizer.sanitizePathSegment(desiredFileName)
        val effectiveMime = mimeType.ifBlank { "application/octet-stream" }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveMutex.withLock {
                saveViaMediaStoreDownloads(sourceFile, safeName, effectiveMime, subfolder)
            }
        } else {
            saveMutex.withLock {
                saveLegacyPublicDownloads(sourceFile, safeName, effectiveMime, subfolder)
            }
        }
    }

    suspend fun deleteSavedMedia(uriString: String?): Boolean = withContext(Dispatchers.IO) {
        if (uriString.isNullOrBlank()) return@withContext false
        runCatching {
            val uri = Uri.parse(uriString)
            when (uri.scheme) {
                ContentResolver.SCHEME_CONTENT -> {
                    context.contentResolver.delete(uri, null, null) > 0
                }
                ContentResolver.SCHEME_FILE -> {
                    uri.path?.let { File(it).delete() } == true
                }
                else -> false
            }
        }.getOrDefault(false)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveViaMediaStoreDownloads(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        subfolder: String
    ): SavedMediaLocation {
        val resolver = context.contentResolver
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$subfolder"
        val uniqueName = resolveUniqueMediaStoreName(resolver, relativePath, fileName)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, uniqueName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: throw IOException("Failed to create MediaStore entry")

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out, bufferSize = 16 * 1024)
                }
            } ?: throw IOException("Failed to open output stream for $itemUri")

            val publishValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(itemUri, publishValues, null, null)

            return SavedMediaLocation(
                uriString = itemUri.toString(),
                displayPath = "Downloads/$subfolder/$uniqueName",
                byteSize = sourceFile.length()
            )
        } catch (t: Throwable) {
            runCatching { resolver.delete(itemUri, null, null) }
            throw t
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resolveUniqueMediaStoreName(
        resolver: ContentResolver,
        relativePath: String,
        desiredName: String
    ): String {
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var candidate = desiredName
        var index = 1

        val normalizedRel = if (relativePath.endsWith('/')) relativePath else "$relativePath/"
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?"

        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        while (true) {
            val exists = resolver.query(
                collection,
                projection,
                selection,
                arrayOf(normalizedRel, candidate),
                null
            )?.use { cursor -> cursor.count > 0 } ?: false

            if (!exists) return candidate
            candidate = if (ext.isNotEmpty()) "$base ($index).$ext" else "$base ($index)"
            index++
        }
    }

    @Suppress("DEPRECATION")
    private fun saveLegacyPublicDownloads(
        sourceFile: File,
        fileName: String,
        mimeType: String,
        subfolder: String
    ): SavedMediaLocation {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, subfolder)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw IOException("Unable to create folder: ${targetDir.absolutePath}")
        }

        val destFile = resolveUniqueFile(targetDir, fileName)
        sourceFile.inputStream().use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 16 * 1024)
            }
        }

        MediaScannerConnection.scanFile(
            context,
            arrayOf(destFile.absolutePath),
            arrayOf(mimeType),
            null
        )

        val contentUri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destFile)
        }.getOrElse { Uri.fromFile(destFile) }

        return SavedMediaLocation(
            uriString = contentUri.toString(),
            displayPath = "Downloads/$subfolder/${destFile.name}",
            byteSize = destFile.length()
        )
    }

    private fun resolveUniqueFile(dir: File, desiredName: String): File {
        val base = desiredName.substringBeforeLast('.', desiredName)
        val ext = desiredName.substringAfterLast('.', "")
        var candidate = File(dir, desiredName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "$base ($index).$ext" else "$base ($index)"
            candidate = File(dir, nextName)
            index++
        }
        return candidate
    }
}
