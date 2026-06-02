package com.devson.vedinsta.extractor

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import kotlin.text.iterator

object InstagramNativeExtractor {

    private const val TAG = "InstagramNativeExtr"

    fun getMediaUrls(url: String, cookieFilePath: String): String {
        val sc = extractShortcode(url)
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

            val mediaId = shortcodeToId(sc)
            val apiUrl = "https://i.instagram.com/api/v1/media/$mediaId/info/"
            val jsonResponseStr = performGetRequest(apiUrl, cookies)
            val data = JSONObject(jsonResponseStr)

            val items = data.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return JSONObject().apply {
                    put("status", "not_found")
                    put("message", "Post not found or deleted.")
                }.toString()
            }

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

    fun getLoggedInUsername(cookieFilePath: String): String {
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

    private fun performGetRequest(urlStr: String, cookies: Map<String, String>): String {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 20000
        conn.readTimeout = 20000
        conn.instanceFollowRedirects = true

        conn.setRequestProperty("User-Agent", "Instagram 319.0.0.28.119 Android")
        conn.setRequestProperty("X-IG-App-ID", "567067343352427")
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
                val entry = parseBest(item, 1)
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

            return JSONObject().apply {
                put("url", first.optString("url", ""))
                put("type", "video")
                put("width", first.optInt("width"))
                put("height", first.optInt("height"))
                put("index", index)
                put("thumbnail_url", thumbnailUrl)
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

    private class HTTPException(val statusCode: Int, message: String) : Exception(message)
}