package com.devson.vedinsta.extractor

import android.util.Log
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

object InstagramNativeExtractor {

    private const val TAG = "InstagramNativeExtr"
    private val extractionMutex = Mutex()

    suspend fun getMediaUrls(
        url: String, 
        cookieFilePath: String,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15
    ): String = extractionMutex.withLock {
        delay(Random.nextLong(3000L, 8000L))

        val result = withTimeoutOrNull(15000L) {
            if (isStoryUrl(url)) {
                val storyRegex = Regex("instagram\\.com/stories/([A-Za-z0-9_.-]+)")
                val matchResult = storyRegex.find(url)
                val cleanedUrl = if (matchResult != null) {
                    "https://www.instagram.com/stories/${matchResult.groupValues[1]}/"
                } else {
                    url
                }
                getStoryMediaUrls(cleanedUrl, cookieFilePath, userAgent, appId, timeoutSeconds)
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

                                val mediaList = parseItems(data)

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

    suspend fun getLoggedInUsername(cookieFilePath: String): String {
        val cookieFile = File(cookieFilePath)
        if (!cookieFile.exists()) return ""
        return try {
            val cookies = parseCookies(cookieFile)
            if (!cookies.containsKey("sessionid")) return ""

            val dsUserId = cookies["ds_user_id"] ?: return ""
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

        val baseUA = if (!savedUA.isNullOrBlank()) {
            savedUA
        } else if (!userAgent.isNullOrBlank()) {
            userAgent
        } else {
            "Instagram 319.0.0.28.119 Android"
        }
        val finalUserAgent = if (baseUA.contains("Instagram")) {
            baseUA
        } else {
            "$baseUA Instagram 319.0.0.28.119 Android"
        }
        val finalAppId = if (!appId.isNullOrBlank()) appId else "567067343352427"

        conn.setRequestProperty("User-Agent", finalUserAgent)
        conn.setRequestProperty("X-IG-App-ID", finalAppId)
        conn.setRequestProperty("X-CSRFToken", cookies["csrftoken"] ?: "")
        conn.setRequestProperty("Referer", "https://www.instagram.com/")
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        conn.setRequestProperty("Accept", "*/*")

        val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        if (cookieString.isNotEmpty()) {
            conn.setRequestProperty("Cookie", cookieString)
        }

        val responseCode = conn.responseCode
        if (responseCode >= 400) {
            val errorResponse = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw HTTPException(responseCode, errorResponse)
        }

        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    private fun parseItems(data: JSONObject): JSONArray {
        val results = JSONArray()
        val items = data.optJSONArray("items") ?: return results
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val mediaType = item.optInt("media_type")
            if (mediaType == 8) {
                val carouselMedia = item.optJSONArray("carousel_media") ?: continue
                for (j in 0 until carouselMedia.length()) {
                    val node = carouselMedia.getJSONObject(j)
                    val entry = parseBest(node, j + 1)
                    if (entry != null) {
                        results.put(entry)
                    }
                }
            } else {
                val entry = parseBest(item, i + 1)
                if (entry != null) {
                    results.put(entry)
                }
            }
        }
        return results
    }

    private fun parseBest(item: JSONObject, index: Int): JSONObject? {
        val mediaType = item.optInt("media_type")
        if (mediaType == 2) {
            val versions = item.optJSONArray("video_versions") ?: return null
            if (versions.length() == 0) return null
            val first = versions.getJSONObject(0)
            val qualities = JSONArray()
            for (i in 0 until versions.length()) {
                val ver = versions.getJSONObject(i)
                val verUrl = ver.optString("url", "")
                if (verUrl.isNotEmpty()) {
                    qualities.put(JSONObject().apply {
                        put("url", verUrl)
                        put("width", ver.optInt("width"))
                        put("height", ver.optInt("height"))
                    })
                }
            }

            val candidates = item.optJSONObject("image_versions2")?.optJSONArray("candidates")
            val thumbnailUrl = if (candidates != null && candidates.length() > 0) {
                candidates.getJSONObject(0).optString("url", "")
            } else {
                ""
            }
            val thumbnailQualities = JSONArray()
            if (candidates != null) {
                for (i in 0 until candidates.length()) {
                    val cand = candidates.getJSONObject(i)
                    val candUrl = cand.optString("url", "")
                    if (candUrl.isNotEmpty()) {
                        thumbnailQualities.put(JSONObject().apply {
                            put("url", candUrl)
                            put("width", cand.optInt("width"))
                            put("height", cand.optInt("height"))
                        })
                    }
                }
            }

            return JSONObject().apply {
                put("url", first.optString("url", ""))
                put("type", "video")
                put("width", first.optInt("width"))
                put("height", first.optInt("height"))
                put("index", index)
                put("thumbnail_url", thumbnailUrl)
                put("thumbnail_qualities", thumbnailQualities)
                put("qualities", qualities)
            }
        } else {
            val imageVersions = item.optJSONObject("image_versions2") ?: return null
            val candidates = imageVersions.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val first = candidates.getJSONObject(0)
            val qualities = JSONArray()
            for (i in 0 until candidates.length()) {
                val cand = candidates.getJSONObject(i)
                val candUrl = cand.optString("url", "")
                if (candUrl.isNotEmpty()) {
                    qualities.put(JSONObject().apply {
                        put("url", candUrl)
                        put("width", cand.optInt("width"))
                        put("height", cand.optInt("height"))
                    })
                }
            }
            return JSONObject().apply {
                put("url", first.optString("url", ""))
                put("type", "image")
                put("width", first.optInt("width"))
                put("height", first.optInt("height"))
                put("index", index)
                put("qualities", qualities)
            }
        }
    }

    private fun isStoryUrl(url: String): Boolean {
        return url.contains("/stories/", ignoreCase = true)
    }

    fun parseStoryUrl(url: String): Pair<String, String> {
        val pattern = Regex("/stories/([A-Za-z0-9_.-]+)(?:/([0-9]+))?")
        val match = pattern.find(url)
        if (match != null) {
            val username = match.groupValues[1]
            val storyId = match.groupValues.getOrNull(2) ?: ""
            return Pair(username, storyId)
        }
        return Pair("", "")
    }

    private suspend fun getUserIdFromUsername(
        username: String,
        cookies: Map<String, String>,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15
    ): String {
        val apiUrl = "https://i.instagram.com/api/v1/users/$username/usernameinfo/"
        val jsonResponseStr = performGetRequest(apiUrl, cookies, userAgent, appId, timeoutSeconds)
        val data = JSONObject(jsonResponseStr)
        val user = data.optJSONObject("user")
        val pkId = user?.optString("pk_id", "") ?: ""
        return if (pkId.isNotEmpty()) {
            pkId
        } else {
            user?.optString("pk", "") ?: ""
        }
    }

    private suspend fun getStoryMediaUrls(
        url: String,
        cookieFilePath: String,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15
    ): String {
        val (username, storyId) = parseStoryUrl(url)
        if (username.isEmpty()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "Could not parse username from story URL")
            }.toString()
        }

        val cookieFile = File(cookieFilePath)
        if (!cookieFile.exists()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "Cookie file not found: $cookieFilePath")
            }.toString()
        }

        return try {
            val cookies = parseCookies(cookieFile)
            if (!cookies.containsKey("sessionid")) {
                return JSONObject().apply {
                    put("status", "login_required")
                    put("message", "sessionid missing - re-export cookies")
                }.toString()
            }

            val userId = getUserIdFromUsername(username, cookies, userAgent, appId, timeoutSeconds)
            if (userId.isEmpty()) {
                return JSONObject().apply {
                    put("status", "error")
                    put("message", "Failed to resolve username to user ID")
                }.toString()
            }

            val reelApiUrl = "https://i.instagram.com/api/v1/feed/user/$userId/reel_media/"
            val jsonResponseStr = performGetRequest(reelApiUrl, cookies, userAgent, appId, timeoutSeconds)
            val data = JSONObject(jsonResponseStr)

            val items = data.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return JSONObject().apply {
                    put("status", "not_found")
                    put("message", "No active stories found for this user.")
                }.toString()
            }

            var filteredItems = JSONArray()
            if (storyId.isNotEmpty()) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val pk = item.optString("pk", "")
                    val id = item.optString("id", "")
                    if (pk == storyId || id.startsWith(storyId)) {
                        filteredItems.put(item)
                        break
                    }
                }
            }

            if (filteredItems.length() == 0) {
                filteredItems = items
            }

            val filteredData = JSONObject().apply {
                put("items", filteredItems)
            }
            val mediaList = parseItems(filteredData)

            if (mediaList.length() == 0) {
                return JSONObject().apply {
                    put("status", "not_found")
                    put("message", "Failed to parse story media items")
                }.toString()
            }

            val userObj = data.optJSONObject("user")
            val realUsername = userObj?.optString("username", username) ?: username

            JSONObject().apply {
                put("status", "success")
                put("username", realUsername)
                put("caption", "")
                put("media", mediaList)
                put("media_count", mediaList.length())
                put("shortcode", if (storyId.isNotEmpty() && filteredItems.length() == 1) "story_${realUsername}_$storyId" else "story_${realUsername}_reel")
            }.toString()
        } catch (e: HTTPException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                JSONObject().apply {
                    put("status", "login_required")
                    put("message", "Session expired or access denied (API returned ${e.statusCode}). Please login again.")
                }.toString()
            } else {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "API returned ${e.statusCode} for story request")
                }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "error")
                put("message", "Story extraction failed: ${e.message}")
            }.toString()
        }
    }

    suspend fun extractUserStories(
        username: String,
        cookieFilePath: String,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15
    ): List<com.devson.vedinsta.model.MediaItem> {
        val cookieFile = File(cookieFilePath)
        if (!cookieFile.exists()) {
            throw Exception("Cookie file not found: $cookieFilePath")
        }

        val cookies = parseCookies(cookieFile)
        if (!cookies.containsKey("sessionid")) {
            throw Exception("Login required: sessionid missing")
        }

        val userId = getUserIdFromUsername(username, cookies, userAgent, appId, timeoutSeconds)
        if (userId.isEmpty()) {
            throw Exception("Failed to resolve username to user ID")
        }

        val reelApiUrl = "https://i.instagram.com/api/v1/feed/user/$userId/reel_media/"
        val jsonResponseStr = performGetRequest(reelApiUrl, cookies, userAgent, appId, timeoutSeconds)
        val data = JSONObject(jsonResponseStr)

        val items = data.optJSONArray("items") ?: return emptyList()
        val mediaItems = mutableListOf<com.devson.vedinsta.model.MediaItem>()

        val filteredData = JSONObject().apply {
            put("items", items)
        }
        val mediaList = parseItems(filteredData)

        for (i in 0 until mediaList.length()) {
            val mediaObj = mediaList.getJSONObject(i)
            val downloadUrl = mediaObj.getString("url")
            val mediaType = mediaObj.getString("type")
            val index = mediaObj.optInt("index", i + 1)
            mediaItems.add(
                com.devson.vedinsta.model.MediaItem(
                    url = downloadUrl,
                    type = mediaType,
                    index = index,
                    isSelected = true
                )
            )
        }
        return mediaItems
    }

    private class HTTPException(val statusCode: Int, message: String) : Exception(message)
}