package com.modatherali.linkloot.data.download

import android.webkit.CookieManager
import com.modatherali.linkloot.data.extractor.FastHtmlExtractor
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class SharedCookieJar : CookieJar {
    private val memoryStore = mutableMapOf<String, List<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        synchronized(memoryStore) {
            val host = url.host
            val existing = memoryStore[host].orEmpty().associateBy { it.name }.toMutableMap()
            for (cookie in cookies) {
                existing[cookie.name] = cookie
            }
            memoryStore[host] = existing.values.toList()
        }
        runCatching {
            val cm = CookieManager.getInstance()
            for (cookie in cookies) {
                cm.setCookie(url.toString(), cookie.toString())
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val fromMemory = synchronized(memoryStore) {
            val now = System.currentTimeMillis()
            memoryStore[url.host].orEmpty().filter { it.expiresAt >= now }
        }
        val webViewCookieStr = runCatching {
            CookieManager.getInstance().getCookie(url.toString())
        }.getOrNull()

        if (webViewCookieStr.isNullOrBlank()) return fromMemory

        val parsed = webViewCookieStr.split(';')
            .mapNotNull { Cookie.parse(url, it.trim()) }
        val merged = linkedMapOf<String, Cookie>()
        fromMemory.forEach { merged[it.name] = it }
        parsed.forEach { merged[it.name] = it }
        return merged.values.toList()
    }

    fun clear() {
        synchronized(memoryStore) {
            memoryStore.clear()
        }
    }
}

object CookieHeaderHelper {
    fun buildEffectiveHeaders(
        targetUrl: String,
        refererUrl: String,
        baseHeaders: Map<String, String>
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        result.putAll(baseHeaders)
        if (!result.containsKey("User-Agent")) {
            result["User-Agent"] = FastHtmlExtractor.DEFAULT_USER_AGENT
        }
        if (refererUrl.isNotBlank() && !result.containsKey("Referer")) {
            result["Referer"] = refererUrl
        }
        val webViewCookies = runCatching {
            CookieManager.getInstance().getCookie(targetUrl)
        }.getOrNull()
        if (!webViewCookies.isNullOrBlank()) {
            val existing = result["Cookie"].orEmpty()
            result["Cookie"] = if (existing.isBlank()) {
                webViewCookies
            } else {
                "$existing; $webViewCookies"
            }
        }
        return result
    }
}
