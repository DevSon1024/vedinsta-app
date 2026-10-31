package com.devson.vedinsta.extractor

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Module providing a stateless, unauthenticated OkHttpClient with cookie capabilities.
 *
 * This client is designed to perform public Instagram extractions.
 * It does not persist or read the app's standard cookies to prevent rate limits,
 * but uses a transient in-memory CookieJar to maintain CSRF token states.
 */
object UnauthenticatedNetworkModule {

    private const val BROWSER_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    val cookieJar = InMemoryCookieJar()

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath
        val isWebpage = path.startsWith("/p/") ||
                path.startsWith("/reel/") ||
                path.startsWith("/reels/") ||
                path.startsWith("/tv/") ||
                (original.header("Accept")?.contains("text/html") == true)

        val builder = original.newBuilder()
        if (original.header("User-Agent") == null) {
            builder.header("User-Agent", BROWSER_USER_AGENT)
        }
        if (original.header("Accept-Language") == null) {
            builder.header("Accept-Language", "en-US,en;q=0.9")
        }

        if (isWebpage) {
            if (original.header("Accept") == null) {
                builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            }
            if (original.header("Sec-Fetch-Dest") == null) {
                builder.header("Sec-Fetch-Dest", "document")
            }
            if (original.header("Sec-Fetch-Mode") == null) {
                builder.header("Sec-Fetch-Mode", "navigate")
            }
            if (original.header("Sec-Fetch-Site") == null) {
                builder.header("Sec-Fetch-Site", "none")
            }
        } else {
            if (original.header("Accept") == null) {
                builder.header("Accept", "*/*")
            }
            if (original.header("Sec-Fetch-Dest") == null) {
                builder.header("Sec-Fetch-Dest", "empty")
            }
            if (original.header("Sec-Fetch-Mode") == null) {
                builder.header("Sec-Fetch-Mode", "cors")
            }
            if (original.header("Sec-Fetch-Site") == null) {
                builder.header("Sec-Fetch-Site", "same-origin")
            }
            if (original.header("X-Requested-With") == null) {
                builder.header("X-Requested-With", "XMLHttpRequest")
            }
        }

        // Retrieve cookies for instagram.com and set X-CSRFToken if present
        val cookies = cookieJar.loadForRequest(original.url)
        val csrfCookie = cookies.firstOrNull { it.name == "csrftoken" }
        if (csrfCookie != null) {
            builder.header("X-CSRFToken", csrfCookie.value)
            builder.header("X-Instagram-AJAX", "1")
        }

        chain.proceed(builder.build())
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor(headerInterceptor)
            .build()
    }

    class InMemoryCookieJar : CookieJar {
        private val cookieStore = HashMap<String, List<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (url.host.contains("instagram.com")) {
                val existing = cookieStore[url.host]?.associateBy { it.name }?.toMutableMap() ?: mutableMapOf()
                cookies.forEach { existing[it.name] = it }
                cookieStore[url.host] = existing.values.toList()
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return cookieStore[url.host] ?: emptyList()
        }
    }
}
