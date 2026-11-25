package com.modatherali.linkloot

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.modatherali.linkloot.data.extractor.UrlNormalizer
import com.modatherali.linkloot.ui.ExternalLaunchRequest
import com.modatherali.linkloot.ui.LinkLootApp

class MainActivity : AppCompatActivity() {

    private var externalRequest by mutableStateOf<ExternalLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIncomingIntent(intent)
        }

        val appContainer = (application as LinkLootApplication).appContainer
        setContent {
            LinkLootApp(
                appContainer = appContainer,
                externalRequest = externalRequest
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if ((intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) return
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            externalRequest = ExternalLaunchRequest(
                id = System.nanoTime(),
                openDownloads = true
            )
            return
        }

        val rawUrl = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        }

        val extracted = rawUrl?.let { UrlNormalizer.extractFirstUrl(it) }
        if (!extracted.isNullOrBlank()) {
            externalRequest = ExternalLaunchRequest(
                id = System.nanoTime(),
                urlToAnalyze = extracted
            )
        }
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.modatherali.linkloot.extra.OPEN_DOWNLOADS"
    }
}
