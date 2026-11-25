package com.modatherali.linkloot.data.extractor

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.modatherali.linkloot.domain.model.MediaItem
import com.modatherali.linkloot.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

data class WebViewSniffOutput(
    val pageTitle: String,
    val resolvedUrl: String,
    val items: List<MediaItem>
)

@Serializable
private data class DomMediaDto(
    val url: String,
    val kind: String,
    val width: Int? = null,
    val height: Int? = null,
    val durationSec: Double? = null,
    val poster: String? = null
)

class WebViewMediaSniffer(private val appContext: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun sniff(
        targetUrl: String,
        timeoutMs: Long = 14_000L
    ): WebViewSniffOutput = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val mainHandler = Handler(Looper.getMainLooper())
            val captured = ConcurrentHashMap<String, MediaItem>()
            val resolvedUrlRef = AtomicReference(targetUrl)
            var pageTitle = UrlNormalizer.extractDomain(targetUrl)
            val finished = AtomicBoolean(false)
            val settleRunnables = mutableListOf<Runnable>()

            val webView = WebView(appContext)
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = FastHtmlExtractor.DEFAULT_USER_AGENT
            }

            fun cleanup() {
                mainHandler.removeCallbacksAndMessages(null)
                runCatching {
                    webView.stopLoading()
                    webView.loadUrl("about:blank")
                    webView.removeAllViews()
                    webView.destroy()
                }
            }

            fun completeNow() {
                if (!finished.compareAndSet(false, true)) return
                val items = captured.values.toList()
                cleanup()
                if (cont.isActive) {
                    cont.resume(
                        WebViewSniffOutput(
                            pageTitle = pageTitle,
                            resolvedUrl = resolvedUrlRef.get(),
                            items = items
                        )
                    )
                }
            }

            fun runDomInspection(onDone: () -> Unit) {
                if (finished.get()) {
                    onDone()
                    return
                }
                webView.evaluateJavascript(DOM_INSPECTION_JS) { rawResult ->
                    if (!rawResult.isNullOrBlank() && rawResult != "null") {
                        runCatching {
                            val unquoted = json.decodeFromString<String>(rawResult)
                            val domItems = json.decodeFromString<List<DomMediaDto>>(unquoted)
                            val currentUrl = webView.url ?: resolvedUrlRef.get()
                            val cookieStr = CookieManager.getInstance().getCookie(currentUrl).orEmpty()
                            val headers = buildMap {
                                put("Referer", currentUrl)
                                put("User-Agent", FastHtmlExtractor.DEFAULT_USER_AGENT)
                                if (cookieStr.isNotBlank()) put("Cookie", cookieStr)
                            }
                            for (dto in domItems) {
                                val resolved = UrlNormalizer.resolveUrl(currentUrl, dto.url) ?: continue
                                val type = if (dto.kind == "video") MediaType.VIDEO else MediaType.IMAGE
                                val format = FastHtmlExtractor.inferFormat(resolved)
                                val isHls = format == "m3u8"
                                val durationMs = dto.durationSec?.takeIf { it > 0.0 }?.let { (it * 1000.0).toLong() }
                                val poster = UrlNormalizer.resolveUrl(currentUrl, dto.poster)
                                val existing = captured[resolved]
                                captured[resolved] = MediaItem(
                                    id = FastHtmlExtractor.stableId(resolved),
                                    mediaUrl = resolved,
                                    resolvedUrl = resolved,
                                    type = if (existing?.type == MediaType.VIDEO || type == MediaType.VIDEO) {
                                        MediaType.VIDEO
                                    } else {
                                        type
                                    },
                                    format = existing?.format?.ifBlank { format } ?: format,
                                    mimeType = existing?.mimeType.orEmpty(),
                                    width = dto.width?.takeIf { it > 0 } ?: existing?.width,
                                    height = dto.height?.takeIf { it > 0 } ?: existing?.height,
                                    durationMs = durationMs ?: existing?.durationMs,
                                    isHls = existing?.isHls == true || isHls,
                                    refererUrl = currentUrl,
                                    headers = headers,
                                    thumbnailUrl = poster ?: existing?.thumbnailUrl
                                )
                            }
                        }
                    }
                    onDone()
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null
                    if (looksLikeMediaNetworkRequest(reqUrl, request.requestHeaders)) {
                        val isVideo = FastHtmlExtractor.isVideoUrl(reqUrl) ||
                            request.requestHeaders["Accept"]?.contains("video", ignoreCase = true) == true
                        val format = FastHtmlExtractor.inferFormat(reqUrl)
                        val referer = request.requestHeaders["Referer"] ?: resolvedUrlRef.get()
                        val cookie = runCatching {
                            CookieManager.getInstance().getCookie(reqUrl)
                        }.getOrNull().orEmpty()
                        val headers = buildMap {
                            put("Referer", referer)
                            put("User-Agent", FastHtmlExtractor.DEFAULT_USER_AGENT)
                            if (cookie.isNotBlank()) put("Cookie", cookie)
                        }
                        captured.putIfAbsent(
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
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (!url.isNullOrBlank()) resolvedUrlRef.set(url)
                    view?.title?.takeIf { it.isNotBlank() }?.let { pageTitle = it }

                    // Cancel previous settle runnables if onPageFinished fires multiple times on redirects
                    settleRunnables.forEach { mainHandler.removeCallbacks(it) }
                    settleRunnables.clear()

                    val scroll1 = Runnable {
                        if (!finished.get()) {
                            webView.evaluateJavascript(
                                "window.scrollTo({top: (document.body ? document.body.scrollHeight : 0) * 0.45, behavior: 'instant'});",
                                null
                            )
                        }
                    }
                    val scroll2 = Runnable {
                        if (!finished.get()) {
                            webView.evaluateJavascript(
                                "window.scrollTo({top: (document.body ? document.body.scrollHeight : 0) * 0.85, behavior: 'instant'});",
                                null
                            )
                            runDomInspection { }
                        }
                    }
                    val settle = Runnable {
                        runDomInspection {
                            completeNow()
                        }
                    }
                    settleRunnables += scroll1
                    settleRunnables += scroll2
                    settleRunnables += settle
                    mainHandler.postDelayed(scroll1, 600L)
                    mainHandler.postDelayed(scroll2, 1500L)
                    mainHandler.postDelayed(settle, 2600L)
                }
            }

            // Hard safety timeout
            mainHandler.postDelayed({
                runDomInspection {
                    completeNow()
                }
            }, timeoutMs)

            cont.invokeOnCancellation {
                finished.set(true)
                mainHandler.post { cleanup() }
            }

            webView.loadUrl(targetUrl)
        }
    }

    companion object {
        fun looksLikeMediaNetworkRequest(url: String, headers: Map<String, String>?): Boolean {
            val lower = url.substringBefore('?').substringBefore('#').lowercase()
            if (FastHtmlExtractor.hasDirectMediaExtension(url)) return true
            if (lower.endsWith(".ts") || lower.endsWith(".m4s")) return false // ignore raw HLS segments
            val accept = headers?.entries
                ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }
                ?.value
                ?.lowercase()
                .orEmpty()
            return accept.startsWith("image/") || accept.startsWith("video/")
        }

        const val DOM_INSPECTION_JS = """
            (function() {
                var results = [];
                var seen = new Set();
                function pushItem(url, kind, w, h, dur, poster) {
                    if (!url || seen.has(url)) return;
                    if (url.startsWith('data:') || url.startsWith('blob:')) return;
                    seen.add(url);
                    results.push({
                        url: url,
                        kind: kind,
                        width: w ? Math.round(w) : null,
                        height: h ? Math.round(h) : null,
                        durationSec: (dur && isFinite(dur)) ? dur : null,
                        poster: poster || null
                    });
                }
                document.querySelectorAll('img').forEach(function(img) {
                    var src = img.currentSrc || img.getAttribute('src') || img.getAttribute('data-src');
                    pushItem(src, 'image', img.naturalWidth || img.width, img.naturalHeight || img.height, null, null);
                });
                document.querySelectorAll('video').forEach(function(v) {
                    var poster = v.getAttribute('poster');
                    if (poster) pushItem(poster, 'image', v.videoWidth, v.videoHeight, null, null);
                    var src = v.currentSrc || v.getAttribute('src') || v.getAttribute('data-src');
                    if (src) pushItem(src, 'video', v.videoWidth, v.videoHeight, v.duration, poster);
                    v.querySelectorAll('source').forEach(function(s) {
                        var sSrc = s.getAttribute('src') || s.getAttribute('data-src');
                        if (sSrc) pushItem(sSrc, 'video', v.videoWidth, v.videoHeight, v.duration, poster);
                    });
                });
                document.querySelectorAll('*').forEach(function(el) {
                    var bg = window.getComputedStyle(el).backgroundImage;
                    if (bg && bg !== 'none' && bg.indexOf('url(') !== -1) {
                        var m = bg.match(/url\(["']?(.*?)["']?\)/);
                        if (m && m[1]) pushItem(m[1], 'image', el.clientWidth, el.clientHeight, null, null);
                    }
                });
                return JSON.stringify(results);
            })();
        """
    }
}
