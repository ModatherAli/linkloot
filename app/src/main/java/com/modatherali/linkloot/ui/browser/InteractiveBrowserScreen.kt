package com.modatherali.linkloot.ui.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.modatherali.linkloot.R
import com.modatherali.linkloot.data.extractor.FastHtmlExtractor
import com.modatherali.linkloot.data.extractor.UrlNormalizer
import com.modatherali.linkloot.data.extractor.WebViewMediaSniffer
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import com.modatherali.linkloot.ui.theme.TabularTextStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@Serializable
private data class BrowserDomMedia(
    val url: String,
    val kind: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationSec: Double? = null,
    val poster: String? = null
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InteractiveBrowserScreen(
    initialUrl: String,
    onClose: () -> Unit,
    onImportCapturedMedia: (pageUrl: String, pageTitle: String, items: List<MediaItem>) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val json = remember { Json { ignoreUnknownKeys = true } }

    val startUrl = remember(initialUrl) {
        UrlNormalizer.normalize(initialUrl) ?: "https://duckduckgo.com"
    }

    var addressBarUrl by remember { mutableStateOf(startUrl) }
    var currentPageUrl by remember { mutableStateOf(startUrl) }
    var currentPageTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var imageCount by remember { mutableIntStateOf(0) }
    var videoCount by remember { mutableIntStateOf(0) }

    val capturedMedia = remember { ConcurrentHashMap<String, MediaItem>() }
    val currentPageUrlRef = remember { AtomicReference(startUrl) }
    val isDisposed = remember { AtomicBoolean(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun refreshCounts() {
        if (isDisposed.get()) return
        val values = capturedMedia.values
        val imgs = values.count { it.type == MediaType.IMAGE }
        val vids = values.count { it.type == MediaType.VIDEO }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            imageCount = imgs
            videoCount = vids
        } else {
            mainHandler.post {
                if (isDisposed.get()) return@post
                imageCount = imgs
                videoCount = vids
            }
        }
    }

    fun inspectDom(view: WebView, onDone: (() -> Unit)? = null) {
        if (isDisposed.get()) return
        view.evaluateJavascript(WebViewMediaSniffer.DOM_INSPECTION_JS) { rawResult ->
            if (isDisposed.get()) return@evaluateJavascript
            if (!rawResult.isNullOrBlank() && rawResult != "null") {
                runCatching {
                    val unquoted = json.decodeFromString<String>(rawResult)
                    val list = json.decodeFromString<List<BrowserDomMedia>>(unquoted)
                    val urlNow = view.url ?: currentPageUrlRef.get()
                    val cookie = CookieManager.getInstance().getCookie(urlNow).orEmpty()
                    val headers = buildMap {
                        put("Referer", urlNow)
                        put("User-Agent", FastHtmlExtractor.DEFAULT_USER_AGENT)
                        if (cookie.isNotBlank()) put("Cookie", cookie)
                    }
                    list.forEach { dto ->
                        val resolved = UrlNormalizer.resolveUrl(urlNow, dto.url) ?: return@forEach
                        val type = if (dto.kind == "video") MediaType.VIDEO else MediaType.IMAGE
                        val format = FastHtmlExtractor.inferFormat(resolved)
                        val poster = UrlNormalizer.resolveUrl(urlNow, dto.poster)
                        val existing = capturedMedia[resolved]
                        capturedMedia[resolved] = MediaItem(
                            id = FastHtmlExtractor.stableId(resolved),
                            mediaUrl = resolved,
                            resolvedUrl = resolved,
                            type = if (existing?.type == MediaType.VIDEO || type == MediaType.VIDEO) {
                                MediaType.VIDEO
                            } else {
                                type
                            },
                            format = existing?.format?.ifBlank { format } ?: format,
                            width = dto.width?.takeIf { it > 0 } ?: existing?.width,
                            height = dto.height?.takeIf { it > 0 } ?: existing?.height,
                            durationMs = dto.durationSec?.takeIf { it > 0 }?.let { (it * 1000.0).toLong() }
                                ?: existing?.durationMs,
                            isHls = existing?.isHls == true || format == "m3u8",
                            refererUrl = urlNow,
                            headers = headers,
                            thumbnailUrl = poster ?: existing?.thumbnailUrl
                        )
                    }
                    refreshCounts()
                }
            }
            if (!isDisposed.get()) {
                onDone?.invoke()
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = FastHtmlExtractor.DEFAULT_USER_AGENT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    if (isDisposed.get()) return
                    isLoading = true
                    if (!url.isNullOrBlank()) {
                        addressBarUrl = url
                        currentPageUrl = url
                        currentPageUrlRef.set(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (isDisposed.get()) return
                    isLoading = false
                    if (!url.isNullOrBlank()) {
                        addressBarUrl = url
                        currentPageUrl = url
                        currentPageUrlRef.set(url)
                    }
                    currentPageTitle = view?.title.orEmpty()
                    canGoBack = view?.canGoBack() == true
                    canGoForward = view?.canGoForward() == true
                    if (view != null) {
                        inspectDom(view)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (isDisposed.get()) return null
                    val reqUrl = request?.url?.toString() ?: return null
                    if (WebViewMediaSniffer.looksLikeMediaNetworkRequest(reqUrl, request.requestHeaders)) {
                        val isVideo = FastHtmlExtractor.isVideoUrl(reqUrl) ||
                            request.requestHeaders.entries.any { (k, v) ->
                                k.equals("Accept", ignoreCase = true) && v.contains("video", ignoreCase = true)
                            }
                        val format = FastHtmlExtractor.inferFormat(reqUrl)
                        val referer = request.requestHeaders.entries
                            .firstOrNull { it.key.equals("Referer", ignoreCase = true) }
                            ?.value
                            ?: currentPageUrlRef.get()
                        val cookie = runCatching {
                            CookieManager.getInstance().getCookie(reqUrl)
                        }.getOrNull().orEmpty()
                        val headers = buildMap {
                            put("Referer", referer)
                            put("User-Agent", FastHtmlExtractor.DEFAULT_USER_AGENT)
                            if (cookie.isNotBlank()) put("Cookie", cookie)
                        }
                        capturedMedia.putIfAbsent(
                            reqUrl,
                            MediaItem(
                                id = FastHtmlExtractor.stableId(reqUrl),
                                mediaUrl = reqUrl,
                                resolvedUrl = reqUrl,
                                type = if (isVideo) MediaType.VIDEO else MediaType.IMAGE,
                                format = format,
                                isHls = format == "m3u8",
                                refererUrl = referer,
                                headers = headers
                            )
                        )
                        refreshCounts()
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            loadUrl(startUrl)
        }
    }

    DisposableEffect(webView) {
        onDispose {
            isDisposed.set(true)
            mainHandler.removeCallbacksAndMessages(null)
            runCatching {
                webView.stopLoading()
                webView.removeAllViews()
                webView.destroy()
            }
        }
    }

    BackHandler {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            onClose()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Browser Toolbar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                        }
                        OutlinedTextField(
                            value = addressBarUrl,
                            onValueChange = { addressBarUrl = it },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    focusManager.clearFocus()
                                    val normalized = UrlNormalizer.normalize(addressBarUrl)
                                    if (normalized != null) {
                                        webView.loadUrl(normalized)
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        IconButton(
                            onClick = { webView.goBack() },
                            enabled = canGoBack
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                        IconButton(
                            onClick = { webView.goForward() },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.browser_forward)
                            )
                        }
                        IconButton(onClick = { webView.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.browser_reload))
                        }
                    }
                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // WebView Content + Floating Capture Bar
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.browser_captured_pill, imageCount, videoCount),
                            style = MaterialTheme.typography.labelMedium.merge(TabularTextStyle),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                inspectDom(webView) {
                                    onImportCapturedMedia(
                                        webView.url ?: currentPageUrl,
                                        webView.title ?: currentPageTitle,
                                        capturedMedia.values.toList()
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.browser_import_action))
                        }
                    }
                }
            }
        }
    }
}
