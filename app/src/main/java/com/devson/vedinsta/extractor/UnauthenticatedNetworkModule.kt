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
        val builder = original.newBuilder()
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("X-Requested-With", "XMLHttpRequest")

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
