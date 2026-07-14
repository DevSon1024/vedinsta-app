package com.devson.vedinsta.extractor

import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Module providing a stateless, unauthenticated OkHttpClient.
 *
 * This client is designed to perform public Instagram extractions.
 * It does not persist or read the app's standard cookies to prevent rate limits.
 */
object UnauthenticatedNetworkModule {

    // Googlebot user agent allows us to bypass the Instagram login redirect wall for public media
    private const val GOOGLEBOT_USER_AGENT = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val request = original.newBuilder()
            .header("User-Agent", GOOGLEBOT_USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .header("X-Requested-With", "XMLHttpRequest")
            .method(original.method, original.body)
            .build()
        chain.proceed(request)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(headerInterceptor)
            .build()
    }
}
