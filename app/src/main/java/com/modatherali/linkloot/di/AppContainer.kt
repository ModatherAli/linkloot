package com.modatherali.linkloot.di

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.room.Room
import com.modatherali.linkloot.data.download.DirectFileDownloader
import com.modatherali.linkloot.data.download.DownloadCoordinator
import com.modatherali.linkloot.data.download.DownloadRepository
import com.modatherali.linkloot.data.download.HlsDownloadEngine
import com.modatherali.linkloot.data.download.MediaStoreSaver
import com.modatherali.linkloot.data.download.SharedCookieJar
import com.modatherali.linkloot.data.extractor.ExtractionRepository
import com.modatherali.linkloot.data.extractor.FastHtmlExtractor
import com.modatherali.linkloot.data.extractor.MediaMetadataProber
import com.modatherali.linkloot.data.extractor.WebViewMediaSniffer
import com.modatherali.linkloot.data.local.HistoryRepository
import com.modatherali.linkloot.data.local.LinkLootDatabase
import com.modatherali.linkloot.data.local.SettingsRepository
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(private val appContext: Context) {

    val sharedCookieJar: SharedCookieJar by lazy { SharedCookieJar() }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(sharedCookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val database: LinkLootDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            LinkLootDatabase::class.java,
            "linkloot.db"
        ).build()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    val fastHtmlExtractor: FastHtmlExtractor by lazy {
        FastHtmlExtractor(okHttpClient)
    }

    val webViewMediaSniffer: WebViewMediaSniffer by lazy {
        WebViewMediaSniffer(appContext)
    }

    val mediaMetadataProber: MediaMetadataProber by lazy {
        MediaMetadataProber(okHttpClient)
    }

    val extractionRepository: ExtractionRepository by lazy {
        ExtractionRepository(
            fastHtmlExtractor = fastHtmlExtractor,
            webViewMediaSniffer = webViewMediaSniffer,
            mediaMetadataProber = mediaMetadataProber,
            historyRepository = historyRepository
        )
    }

    val mediaStoreSaver: MediaStoreSaver by lazy {
        MediaStoreSaver(appContext)
    }

    val directFileDownloader: DirectFileDownloader by lazy {
        DirectFileDownloader(
            okHttpClient = okHttpClient,
            tempDir = File(appContext.filesDir, "temp_downloads")
        )
    }

    val hlsDownloadEngine: HlsDownloadEngine by lazy {
        HlsDownloadEngine(
            context = appContext,
            okHttpClient = okHttpClient,
            tempHlsRoot = File(appContext.filesDir, "temp_hls")
        )
    }

    val downloadRepository: DownloadRepository by lazy {
        DownloadRepository(
            downloadDao = database.downloadDao(),
            mediaStoreSaver = mediaStoreSaver
        )
    }

    val downloadCoordinator: DownloadCoordinator by lazy {
        DownloadCoordinator(
            context = appContext,
            downloadRepository = downloadRepository,
            settingsRepository = settingsRepository,
            directFileDownloader = directFileDownloader,
            hlsDownloadEngine = hlsDownloadEngine,
            mediaStoreSaver = mediaStoreSaver
        )
    }

    fun clearCookiesAndCache() {
        sharedCookieJar.clear()
        runCatching {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
        runCatching {
            WebStorage.getInstance().deleteAllData()
        }
        runCatching {
            appContext.cacheDir.listFiles()?.forEach { file ->
                if (file.name.contains("WebView", ignoreCase = true) ||
                    file.name.contains("http", ignoreCase = true) ||
                    file.name.contains("coil", ignoreCase = true)
                ) {
                    file.deleteRecursively()
                }
            }
        }
    }
}
