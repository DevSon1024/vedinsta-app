package com.devson.vedinsta.extractor

import android.content.Context
import android.util.Log
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.ThumbnailQuality
import com.devson.vedinsta.model.MediaVariant
import com.devson.vedinsta.model.ExtractedMediaNode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.iterator
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

class InstagramRateLimitException(message: String) : Exception(message)

object InstagramNativeExtractor {

    private const val TAG = "InstagramNativeExtr"
    private val extractionMutex = Mutex()

    private val requestTimestamps = mutableListOf<Long>()
    private const val ROLLING_WINDOW_MS = 10 * 60 * 1000L // 10 minutes
    private const val MAX_REQUESTS_IN_WINDOW = 15 // safe limit

    private fun checkRollingLimit(): Boolean {
        val now = System.currentTimeMillis()
        requestTimestamps.removeAll { now - it > ROLLING_WINDOW_MS }
        if (requestTimestamps.size >= MAX_REQUESTS_IN_WINDOW) {
            return false
        }
        requestTimestamps.add(now)
        return true
    }

    private suspend fun applyAntiBanJitter() {
        val delayTime = Random.nextLong(1500L, 4500L)
        delay(delayTime)
    }

    suspend fun getMediaUrls(
        url: String, 
        cookieFilePath: String,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15,
        userQualityPreference: MediaQuality = MediaQuality.HIGH,
        thumbnailQualityPreference: ThumbnailQuality = ThumbnailQuality.LOWEST
    ): String = withContext(Dispatchers.IO) {
        extractionMutex.withLock {
            val context = try { com.devson.vedinsta.VedInstaApplication.instance } catch (e: Exception) { null }
            val securePrefs = context?.let { com.devson.vedinsta.repository.SecurePreferences(it) }
            val prefs = context?.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
            val overshadowRateLimit = prefs?.getBoolean("overshadow_rate_limit", false) ?: false

            // 1. Check Suspension Expiry
            if (securePrefs?.isSuspended() == true && !overshadowRateLimit) {
                val expiry = securePrefs.getSuspensionExpiry()
                val remainingMin = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(expiry - System.currentTimeMillis()) + 1
                return@withLock JSONObject().apply {
                    put("status", "rate_limit_429")
                    put("message", "Instagram has asked us to slow down. To keep your account completely safe, Vedinsta will pause downloads for $remainingMin minutes. Please take a short break!")
                }.toString()
            }

            // 2. Check Rolling Window Limit
            if (!checkRollingLimit() && !overshadowRateLimit) {
                return@withLock JSONObject().apply {
                    put("status", "rate_limit")
                    put("message", "Too many downloads at once! Please slow down the pace and wait a few minutes before trying again to keep your account safe.")
                }.toString()
            }

            // 3. Enforce Jitter
            applyAntiBanJitter()

            val result = withTimeoutOrNull(15000L) {
                if (url.contains("/stories/", ignoreCase = true)) {
                    JSONObject().apply {
                        put("status", "error")
                        put("message", "Stories downloading is not supported currently.")
                    }.toString()
                } else {
                    val sc = extractShortcode(url)
                    val cookieFile = File(cookieFilePath)
                    if (!cookieFile.exists()) {
                        JSONObject().apply {
                            put("status", "error")
                            put("message", "Cookie file not found: $cookieFilePath")
                        }.toString()
                    } else {
                        try {
                            val cookies = parseCookies(cookieFile)
                            if (!cookies.containsKey("sessionid")) {
                                JSONObject().apply {
                                    put("status", "login_required")
                                    put("message", "sessionid missing - re-export cookies")
                                }.toString()
                            } else {
                                val mediaId = shortcodeToId(sc)
                                val apiUrl = "https://i.instagram.com/api/v1/media/$mediaId/info/"
                                val jsonResponseStr = performGetRequest(apiUrl, cookies, userAgent, appId, timeoutSeconds)
                                val data = JSONObject(jsonResponseStr)

                                val items = data.optJSONArray("items")
                                if (items == null || items.length() == 0) {
                                    JSONObject().apply {
                                        put("status", "not_found")
                                        put("message", "Post not found or deleted.")
                                    }.toString()
                                } else {
                                    val item = items.getJSONObject(0)
                                    val username = item.optJSONObject("user")?.optString("username", "unknown") ?: "unknown"
                                    val caption = item.optJSONObject("caption")?.optString("text", "") ?: ""

                                    val mediaList = parseItems(data, userQualityPreference, thumbnailQualityPreference)

                                    JSONObject().apply {
                                        put("status", "success")
                                        put("username", username)
                                        put("caption", caption)
                                        put("media", mediaList)
                                        put("media_count", mediaList.length())
                                        put("shortcode", sc)
                                    }.toString()
                                }
                            }
                        } catch (e: HTTPException) {
                            if (e.statusCode == 401 || e.statusCode == 403) {
                                JSONObject().apply {
                                    put("status", "login_required")
                                    put("message", "API returned ${e.statusCode}. Login required.")
                                }.toString()
                            } else {
                                JSONObject().apply {
                                    put("status", "error")
                                    put("message", "API returned ${e.statusCode}")
                                }.toString()
                            }
                        } catch (e: Exception) {
                            JSONObject().apply {
                                put("status", "error")
                                put("message", "Extraction failed: ${e.message}")
                            }.toString()
                        }
                    }
                }
            }

            result ?: JSONObject().apply {
                put("status", "error")
                put("message", "Request timed out after 15 seconds")
            }.toString()
        }
    }

    suspend fun getLoggedInUsername(cookieFilePath: String): String = withContext(Dispatchers.IO) {
        val cookieFile = File(cookieFilePath)
        if (!cookieFile.exists()) return@withContext ""
        try {
            val cookies = parseCookies(cookieFile)
            if (!cookies.containsKey("sessionid")) return@withContext ""

            val dsUserId = cookies["ds_user_id"] ?: return@withContext ""
            val apiUrl = "https://i.instagram.com/api/v1/users/$dsUserId/info/"
            val jsonResponseStr = performGetRequest(apiUrl, cookies)
            val data = JSONObject(jsonResponseStr)
            data.optJSONObject("user")?.optString("username", "") ?: ""
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching username: ${e.message}")
            ""
        }
    }

    private fun extractShortcode(input: String): String {
        var sc = input.trim()
        if (sc.contains("instagram.com/")) {
            for (segment in listOf("/p/", "/reel/", "/reels/", "/tv/")) {
                if (sc.contains(segment)) {
                    sc = sc.substringAfter(segment).trimStart('/')
                    sc = sc.substringBefore('/')
                    sc = sc.substringBefore('?')
                    break
                }
            }
        }
        return sc
    }

    private fun shortcodeToId(sc: String): String {
        val alpha = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var n = BigInteger.ZERO
        val sixtyFour = BigInteger.valueOf(64)
        for (c in sc) {
            val index = alpha.indexOf(c)
            if (index != -1) {
                n = n.multiply(sixtyFour).add(BigInteger.valueOf(index.toLong()))
            }
        }
        return n.toString()
    }

    private fun parseCookies(cookieFile: File): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        try {
            cookieFile.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    val parts = trimmed.split("\t")
                    if (parts.size >= 7) {
                        val domain = parts[0].trimStart('.')
                        if (domain.contains("instagram.com")) {
                            val name = parts[5]
                            val value = parts[6]
                            cookies[name] = value
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing cookies: ${e.message}")
        }
        return cookies
    }

    private fun performGetRequest(
        urlStr: String, 
        cookies: Map<String, String>,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15
    ): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = timeoutSeconds * 1000
        conn.readTimeout = timeoutSeconds * 1000
        conn.instanceFollowRedirects = true

        val context = try { com.devson.vedinsta.VedInstaApplication.instance } catch (e: Exception) { null }
        val securePrefs = context?.let { com.devson.vedinsta.repository.SecurePreferences(it) }
        val savedUA = securePrefs?.getUserAgent()

        val prefs = context?.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
        val customAcceptLanguage = prefs?.getString("accept_language", "") ?: ""
        val customXAsbdId = prefs?.getString("x_asbd_id", "") ?: ""
        val customViewportWidth = prefs?.getString("viewport_width", "") ?: ""
        val customAppId = prefs?.getString("custom_ig_app_id", "") ?: ""

        val finalUserAgent = if (!savedUA.isNullOrBlank()) {
            savedUA
        } else if (!userAgent.isNullOrBlank()) {
            userAgent
        } else {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
        
        val finalAppId = if (!appId.isNullOrBlank()) {
            appId
        } else if (customAppId.isNotEmpty()) {
            customAppId
        } else {
            "936619743392459"
        }

        conn.setRequestProperty("User-Agent", finalUserAgent)
        conn.setRequestProperty("X-IG-App-ID", finalAppId)
        conn.setRequestProperty("X-CSRFToken", cookies["csrftoken"] ?: "")
        conn.setRequestProperty("Referer", "https://www.instagram.com/")

        val finalAcceptLanguage = if (customAcceptLanguage.isNotEmpty()) customAcceptLanguage else "en-US,en;q=0.9"
        conn.setRequestProperty("Accept-Language", finalAcceptLanguage)

        val finalAsbdId = if (customXAsbdId.isNotEmpty()) customXAsbdId else "198387"
        conn.setRequestProperty("X-ASBD-ID", finalAsbdId)

        val finalViewportWidth = if (customViewportWidth.isNotEmpty()) customViewportWidth else "1080"
        conn.setRequestProperty("Viewport-Width", finalViewportWidth)

        // Meta-Headers (Sec-Ch-Ua, Sec-Ch-Ua-Mobile, Sec-Ch-Ua-Platform)
        val isMobile = finalUserAgent.contains("Mobile", ignoreCase = true) || finalUserAgent.contains("Android", ignoreCase = true)
        val platform = when {
            finalUserAgent.contains("Windows", ignoreCase = true) -> "\"Windows\""
            finalUserAgent.contains("Macintosh", ignoreCase = true) || finalUserAgent.contains("OS X", ignoreCase = true) -> "\"macOS\""
            finalUserAgent.contains("Android", ignoreCase = true) -> "\"Android\""
            finalUserAgent.contains("iPhone", ignoreCase = true) || finalUserAgent.contains("iPad", ignoreCase = true) -> "\"iOS\""
            else -> "\"Android\""
        }
        conn.setRequestProperty("Sec-Ch-Ua-Mobile", if (isMobile) "?1" else "?0")
        conn.setRequestProperty("Sec-Ch-Ua-Platform", platform)

        var chromeVersion = "120"
        val chromeRegex = Regex("Chrome/(\\d+)")
        chromeRegex.find(finalUserAgent)?.let {
            chromeVersion = it.groupValues[1]
        }
        conn.setRequestProperty("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"$chromeVersion\", \"Google Chrome\";v=\"$chromeVersion\"")

        // App Fingerprinting claim header
        val savedClaim = securePrefs?.getXIgWwwClaim() ?: "0"
        conn.setRequestProperty("X-IG-WWW-Claim", savedClaim)

        conn.setRequestProperty("Accept", "*/*")

        val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (cookieString.isNotEmpty()) {
            conn.setRequestProperty("Cookie", cookieString)
        }

        val responseCode = conn.responseCode
        
        // 429 Too Many Requests Backoff
        if (responseCode == 429) {
            val overshadowRateLimit = prefs?.getBoolean("overshadow_rate_limit", false) ?: false
            if (!overshadowRateLimit) {
                securePrefs?.saveSuspensionExpiry(System.currentTimeMillis() + 15 * 60 * 1000L)
            }
            throw InstagramRateLimitException("Too many downloads at once! Instagram has asked us to slow down. Vedinsta will pause downloads for 15 minutes to keep your account safe.")
        }

        // Cache incoming claim header
        val responseClaim = conn.getHeaderField("x-ig-www-claim") ?: conn.getHeaderField("X-IG-WWW-Claim")
        if (!responseClaim.isNullOrEmpty()) {
            securePrefs?.saveXIgWwwClaim(responseClaim)
        }

        if (responseCode >= 400) {
            val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw HTTPException(responseCode, errorResponse)
        }

        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun selectDownloadUrls(versionsArray: JSONArray?, preference: MediaQuality): List<String> {
        val urls = mutableListOf<String>()
        if (versionsArray == null) return urls
        val size = versionsArray.length()
        if (size == 0) return urls

        when (preference) {
            MediaQuality.LOW -> {
                versionsArray.optJSONObject(size - 1)?.optString("url", "")?.let {
                    if (it.isNotEmpty()) urls.add(it)
                }
            }
            MediaQuality.MEDIUM -> {
                val medianIndex = size / 2
                versionsArray.optJSONObject(medianIndex)?.optString("url", "")?.let {
                    if (it.isNotEmpty()) urls.add(it)
                }
            }
            MediaQuality.HIGH -> {
                versionsArray.optJSONObject(0)?.optString("url", "")?.let {
                    if (it.isNotEmpty()) urls.add(it)
                }
            }
            MediaQuality.CUSTOM -> {
                for (i in 0 until size) {
                    versionsArray.optJSONObject(i)?.optString("url", "")?.let {
                        if (it.isNotEmpty()) urls.add(it)
                    }
                }
            }
        }
        return urls
    }

    private fun parseItems(data: JSONObject, userQualityPreference: MediaQuality, thumbnailQualityPreference: ThumbnailQuality): JSONArray {
        val results = JSONArray()
        val items = data.optJSONArray("items") ?: return results
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val mediaType = item.optInt("media_type")
            if (mediaType == 8) {
                val carouselMedia = item.optJSONArray("carousel_media") ?: continue
                for (j in 0 until carouselMedia.length()) {
                    val node = carouselMedia.getJSONObject(j)
                    val entry = parseBest(node, j + 1, userQualityPreference, thumbnailQualityPreference)
                    if (entry != null) {
                        results.put(entry)
                    }
                }
            } else {
                val entry = parseBest(item, i + 1, userQualityPreference, thumbnailQualityPreference)
                if (entry != null) {
                    results.put(entry)
                }
            }
        }
        return results
    }

    private fun parseBest(
        item: JSONObject,
        index: Int,
        userQualityPreference: MediaQuality,
        thumbnailQualityPreference: ThumbnailQuality
    ): JSONObject? {
        val mediaType = item.optInt("media_type")
        val candidates = item.optJSONObject("image_versions2")?.optJSONArray("candidates")
        val thumbnailSize = candidates?.length() ?: 0

        val targetThumbnailIndex = when (thumbnailQualityPreference) {
            ThumbnailQuality.LOWEST -> thumbnailSize - 1
            ThumbnailQuality.MEDIUM -> thumbnailSize / 2
            ThumbnailQuality.HIGHEST -> 0
            ThumbnailQuality.SAME_AS_DOWNLOAD -> when (userQualityPreference) {
                MediaQuality.LOW -> thumbnailSize - 1
                MediaQuality.MEDIUM -> thumbnailSize / 2
                MediaQuality.HIGH, MediaQuality.CUSTOM -> 0
            }
        }.coerceIn(0, thumbnailSize - 1)

        val thumbnailUrl = if (candidates != null && thumbnailSize > 0) {
            candidates.optJSONObject(targetThumbnailIndex)?.optString("url", "") ?: ""
        } else {
            ""
        }

        if (mediaType == 2) {
            val versions = item.optJSONArray("video_versions") ?: return null
            val size = versions.length()
            if (size == 0) return null

            val downloadUrls = selectDownloadUrls(versions, userQualityPreference)

            // Construct downloadVariants
            val downloadVariantsArray = JSONArray()
            if (userQualityPreference == MediaQuality.CUSTOM) {
                for (i in 0 until size) {
                    val ver = versions.optJSONObject(i) ?: continue
                    val verUrl = ver.optString("url", "")
                    if (verUrl.isNotEmpty()) {
                        val h = ver.optInt("height")
                        val label = if (h > 0) "${h}px" else "1080px"
                        downloadVariantsArray.put(JSONObject().apply {
                            put("url", verUrl)
                            put("resolution_label", label)
                        })
                    }
                }
            } else {
                val targetIndex = when (userQualityPreference) {
                    MediaQuality.LOW -> size - 1
                    MediaQuality.MEDIUM -> size / 2
                    MediaQuality.HIGH -> 0
                    else -> 0
                }.coerceIn(0, size - 1)
                val targetVer = versions.optJSONObject(targetIndex) ?: versions.getJSONObject(0)
                val primaryUrl = targetVer.optString("url", "")
                val h = targetVer.optInt("height")
                val label = if (h > 0) "${h}px" else "1080px"
                downloadVariantsArray.put(JSONObject().apply {
                    put("url", primaryUrl)
                    put("resolution_label", label)
                })
            }

            val targetIndex = when (userQualityPreference) {
                MediaQuality.LOW -> size - 1
                MediaQuality.MEDIUM -> size / 2
                MediaQuality.HIGH, MediaQuality.CUSTOM -> 0
            }.coerceIn(0, size - 1)

            val targetVer = versions.optJSONObject(targetIndex) ?: versions.getJSONObject(0)
            val primaryUrl = targetVer.optString("url", "")
            val width = targetVer.optInt("width")
            val height = targetVer.optInt("height")

            val qualities = JSONArray()
            if (userQualityPreference == MediaQuality.CUSTOM) {
                for (i in 0 until size) {
                    val ver = versions.optJSONObject(i) ?: continue
                    val verUrl = ver.optString("url", "")
                    if (verUrl.isNotEmpty()) {
                        qualities.put(JSONObject().apply {
                            put("url", verUrl)
                            put("width", ver.optInt("width"))
                            put("height", ver.optInt("height"))
                        })
                    }
                }
            } else {
                qualities.put(JSONObject().apply {
                    put("url", primaryUrl)
                    put("width", width)
                    put("height", height)
                })
            }

            val thumbnailQualities = JSONArray()
            if (candidates != null && thumbnailSize > 0) {
                val lastCand = candidates.optJSONObject(targetThumbnailIndex) ?: candidates.optJSONObject(thumbnailSize - 1)
                thumbnailQualities.put(JSONObject().apply {
                    put("url", thumbnailUrl)
                    put("width", lastCand?.optInt("width") ?: 0)
                    put("height", lastCand?.optInt("height") ?: 0)
                })
            }

            val downloadUrlsArray = JSONArray()
            for (url in downloadUrls) {
                downloadUrlsArray.put(url)
            }

            return JSONObject().apply {
                put("url", primaryUrl)
                put("download_urls", downloadUrlsArray)
                put("download_variants", downloadVariantsArray)
                put("type", "video")
                put("width", width)
                put("height", height)
                put("index", index)
                put("thumbnail_url", thumbnailUrl)
                put("thumbnail_qualities", thumbnailQualities)
                put("qualities", qualities)
            }
        } else {
            if (candidates == null || thumbnailSize == 0) return null

            val downloadUrls = selectDownloadUrls(candidates, userQualityPreference)

            // Construct downloadVariants
            val downloadVariantsArray = JSONArray()
            if (userQualityPreference == MediaQuality.CUSTOM) {
                for (i in 0 until thumbnailSize) {
                    val cand = candidates.optJSONObject(i) ?: continue
                    val candUrl = cand.optString("url", "")
                    if (candUrl.isNotEmpty()) {
                        val h = cand.optInt("height")
                        val label = if (h > 0) "${h}px" else "1080px"
                        downloadVariantsArray.put(JSONObject().apply {
                            put("url", candUrl)
                            put("resolution_label", label)
                        })
                    }
                }
            } else {
                val targetIndex = when (userQualityPreference) {
                    MediaQuality.LOW -> thumbnailSize - 1
                    MediaQuality.MEDIUM -> thumbnailSize / 2
                    MediaQuality.HIGH -> 0
                    else -> 0
                }.coerceIn(0, thumbnailSize - 1)
                val targetCand = candidates.optJSONObject(targetIndex) ?: candidates.getJSONObject(0)
                val primaryUrl = targetCand.optString("url", "")
                val h = targetCand.optInt("height")
                val label = if (h > 0) "${h}px" else "1080px"
                downloadVariantsArray.put(JSONObject().apply {
                    put("url", primaryUrl)
                    put("resolution_label", label)
                })
            }

            val targetIndex = when (userQualityPreference) {
                MediaQuality.LOW -> thumbnailSize - 1
                MediaQuality.MEDIUM -> thumbnailSize / 2
                MediaQuality.HIGH, MediaQuality.CUSTOM -> 0
            }.coerceIn(0, thumbnailSize - 1)

            val targetCand = candidates.optJSONObject(targetIndex) ?: candidates.getJSONObject(0)
            val primaryUrl = targetCand.optString("url", "")
            val width = targetCand.optInt("width")
            val height = targetCand.optInt("height")

            val qualities = JSONArray()
            if (userQualityPreference == MediaQuality.CUSTOM) {
                for (i in 0 until thumbnailSize) {
                    val cand = candidates.optJSONObject(i) ?: continue
                    val candUrl = cand.optString("url", "")
                    if (candUrl.isNotEmpty()) {
                        qualities.put(JSONObject().apply {
                            put("url", candUrl)
                            put("width", cand.optInt("width"))
                            put("height", cand.optInt("height"))
                        })
                    }
                }
            } else {
                qualities.put(JSONObject().apply {
                    put("url", primaryUrl)
                    put("width", width)
                    put("height", height)
                })
            }

            val downloadUrlsArray = JSONArray()
            for (url in downloadUrls) {
                downloadUrlsArray.put(url)
            }

            return JSONObject().apply {
                put("url", primaryUrl)
                put("download_urls", downloadUrlsArray)
                put("download_variants", downloadVariantsArray)
                put("type", "image")
                put("width", width)
                put("height", height)
                put("index", index)
                put("thumbnail_url", thumbnailUrl)
                put("qualities", qualities)
            }
        }
    }


    private class HTTPException(val statusCode: Int, message: String) : Exception(message)
}