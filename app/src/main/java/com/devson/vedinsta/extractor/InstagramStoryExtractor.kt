package com.devson.vedinsta.extractor

import android.util.Log
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.model.ThumbnailQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class StoryUrlInfo(
    val cleanUrl: String,
    val username: String? = null,
    val highlightId: String? = null,
    val storyId: String? = null
)

object InstagramStoryExtractor {

    private const val TAG = "InstagramStoryExtractor"
    private const val MOBILE_USER_AGENT = "Instagram 275.0.0.27.98 Android (33/13; 420dpi; 1080x2400; Google/google; Pixel 7; cheetah; arm64-v8a; en_US; 458229440)"

    /**
     * Cleans an Instagram story URL, strips tracking parameters, and extracts story metadata.
     */
    fun cleanAndParseStoryUrl(inputUrl: String): StoryUrlInfo {
        var rawUrl = inputUrl.trim()
        val fragmentIndex = rawUrl.indexOf("#")
        if (fragmentIndex != -1) {
            rawUrl = rawUrl.substring(0, fragmentIndex)
        }

        val urlNoQuery = if (rawUrl.contains("?")) rawUrl.substringBefore("?") else rawUrl
        val queryStr = if (rawUrl.contains("?")) rawUrl.substringAfter("?") else ""

        val queryParams = mutableMapOf<String, String>()
        if (queryStr.isNotEmpty()) {
            queryStr.split("&").forEach { param ->
                val parts = param.split("=")
                if (parts.size == 2) {
                    queryParams[parts[0].trim()] = parts[1].trim()
                }
            }
        }

        val storyMediaIdFromQuery = queryParams["story_media_id"]

        val cleanPath = urlNoQuery
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("instagram.com")
            .trim('/')

        val segments = cleanPath.split("/").filter { it.isNotEmpty() }

        var username: String? = null
        var highlightId: String? = null
        var storyId: String? = storyMediaIdFromQuery

        if (segments.size >= 2 && segments[0].equals("stories", ignoreCase = true)) {
            val second = segments[1]
            if (second.equals("highlights", ignoreCase = true)) {
                if (segments.size >= 3) {
                    highlightId = segments[2]
                }
                if (segments.size >= 4) {
                    storyId = segments[3]
                }
            } else {
                username = second
                if (segments.size >= 3) {
                    storyId = segments[2]
                }
            }
        }

        val cleanUrl = when {
            highlightId != null -> {
                val base = "https://www.instagram.com/stories/highlights/$highlightId/"
                if (storyId != null) "$base?story_media_id=$storyId" else base
            }
            username != null -> {
                val base = "https://www.instagram.com/stories/$username/"
                if (storyId != null) "$base$storyId/" else base
            }
            else -> rawUrl
        }

        return StoryUrlInfo(
            cleanUrl = cleanUrl,
            username = username,
            highlightId = highlightId,
            storyId = storyId
        )
    }

    /**
     * Downloads and extracts an Instagram story using account cookies.
     * Ensures only the specific target story item is extracted if a storyId is provided.
     */
    suspend fun extractStory(
        url: String,
        cookieFilePath: String,
        userAgent: String? = null,
        appId: String? = null,
        timeoutSeconds: Int = 15,
        userQualityPreference: MediaQuality = MediaQuality.HIGH,
        thumbnailQualityPreference: ThumbnailQuality = ThumbnailQuality.LOWEST
    ): String = withContext(Dispatchers.IO) {
        val cookieFile = File(cookieFilePath)
        if (!cookieFile.exists()) {
            return@withContext JSONObject().apply {
                put("status", "login_required")
                put("message", "Authentication required to download Instagram stories. Please log into your Instagram account.")
            }.toString()
        }

        val cookies = InstagramNativeExtractor.parseCookies(cookieFile)
        if (!cookies.containsKey("sessionid")) {
            return@withContext JSONObject().apply {
                put("status", "login_required")
                put("message", "Session cookie (sessionid) not found. Please log into Instagram again.")
            }.toString()
        }

        val storyInfo = cleanAndParseStoryUrl(url)

        // 1. Direct Single Story Lookup via media info endpoint if storyId is numeric
        val targetStoryId = storyInfo.storyId
        if (!targetStoryId.isNullOrEmpty() && targetStoryId.all { it.isDigit() }) {
            try {
                val mediaInfoUrl = "https://i.instagram.com/api/v1/media/$targetStoryId/info/"
                val mediaInfoStr = InstagramNativeExtractor.performGetRequest(
                    urlStr = mediaInfoUrl,
                    cookies = cookies,
                    userAgent = userAgent,
                    appId = appId,
                    timeoutSeconds = timeoutSeconds
                )
                val mediaInfoJson = JSONObject(mediaInfoStr)
                val items = mediaInfoJson.optJSONArray("items")
                if (items != null && items.length() > 0) {
                    val mediaList = InstagramNativeExtractor.parseItems(
                        data = mediaInfoJson,
                        userQualityPreference = userQualityPreference,
                        thumbnailQualityPreference = thumbnailQualityPreference
                    )
                    val ownerUsername = items.getJSONObject(0).optJSONObject("user")?.optString("username")
                        ?: storyInfo.username ?: "unknown"

                    return@withContext JSONObject().apply {
                        put("status", "success")
                        put("username", ownerUsername)
                        put("caption", "Story by @$ownerUsername")
                        put("media", mediaList)
                        put("media_count", mediaList.length())
                        put("shortcode", targetStoryId)
                        put("clean_url", storyInfo.cleanUrl)
                    }.toString()
                }
            } catch (e: InstagramRateLimitException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Direct story media info lookup failed for $targetStoryId: ${e.message}, falling back to reel feed")
            }
        }

        // 2. Reel Feed Lookup via username or highlight ID
        val targetId = when {
            !storyInfo.highlightId.isNullOrEmpty() -> "highlight:${storyInfo.highlightId}"
            !storyInfo.username.isNullOrEmpty() -> {
                resolveUserIdFromUsername(storyInfo.username, cookies, userAgent, appId, timeoutSeconds)
            }
            else -> null
        }

        if (targetId.isNullOrEmpty()) {
            return@withContext JSONObject().apply {
                put("status", "error")
                put("message", "Could not parse Instagram story account or highlight ID from link.")
            }.toString()
        }

        try {
            val apiUrl = "https://i.instagram.com/api/v1/feed/reels_media/?reel_ids=$targetId"
            val responseStr = InstagramNativeExtractor.performGetRequest(
                urlStr = apiUrl,
                cookies = cookies,
                userAgent = userAgent,
                appId = appId,
                timeoutSeconds = timeoutSeconds
            )

            val data = JSONObject(responseStr)
            val reelsObj = data.optJSONObject("reels")
            val reelKey = targetId.removePrefix("highlight:")
            val reelData = reelsObj?.optJSONObject(targetId)
                ?: reelsObj?.optJSONObject(reelKey)
                ?: reelsObj?.keys()?.asSequence()?.firstOrNull()?.let { reelsObj.optJSONObject(it) }
                ?: data.optJSONArray("reels_media")?.optJSONObject(0)

            if (reelData == null) {
                return@withContext JSONObject().apply {
                    put("status", "not_found")
                    put("message", "Story data not found for target user or highlight.")
                }.toString()
            }

            val items = reelData.optJSONArray("items")
            if (items == null || items.length() == 0) {
                return@withContext JSONObject().apply {
                    put("status", "not_found")
                    put("message", "No active stories found for this account or highlight.")
                }.toString()
            }

            val filteredItems = JSONArray()

            if (!targetStoryId.isNullOrEmpty()) {
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val pk = item.optString("pk")
                    val itemId = item.optString("id")
                    val code = item.optString("code")

                    val isMatch = pk == targetStoryId ||
                            itemId == targetStoryId ||
                            itemId.startsWith("${targetStoryId}_") ||
                            code.equals(targetStoryId, ignoreCase = true)

                    if (isMatch) {
                        filteredItems.put(item)
                        break
                    }
                }

                if (filteredItems.length() == 0) {
                    return@withContext JSONObject().apply {
                        put("status", "not_found")
                        put("message", "Requested story item ($targetStoryId) was not found or has expired.")
                    }.toString()
                }
            } else {
                for (i in 0 until items.length()) {
                    filteredItems.put(items.getJSONObject(i))
                }
            }

            val wrapperJson = JSONObject().apply {
                put("items", filteredItems)
            }

            val mediaList = InstagramNativeExtractor.parseItems(
                data = wrapperJson,
                userQualityPreference = userQualityPreference,
                thumbnailQualityPreference = thumbnailQualityPreference
            )

            val ownerUsername = reelData.optJSONObject("user")?.optString("username", "") ?: storyInfo.username ?: "unknown"
            val shortcode = targetStoryId ?: "story_${System.currentTimeMillis()}"

            JSONObject().apply {
                put("status", "success")
                put("username", ownerUsername)
                put("caption", "Story by @$ownerUsername")
                put("media", mediaList)
                put("media_count", mediaList.length())
                put("shortcode", shortcode)
                put("clean_url", storyInfo.cleanUrl)
            }.toString()

        } catch (e: InstagramRateLimitException) {
            throw e
        } catch (e: HTTPException) {
            if (e.statusCode == 401 || e.statusCode == 403) {
                JSONObject().apply {
                    put("status", "login_required")
                    put("message", "Authentication required by Instagram API (${e.statusCode}).")
                }.toString()
            } else {
                JSONObject().apply {
                    put("status", "error")
                    put("message", "Instagram Story API returned HTTP ${e.statusCode}")
                }.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching story: ${e.message}", e)
            JSONObject().apply {
                put("status", "error")
                put("message", "Story extraction failed: ${e.message}")
            }.toString()
        }
    }

    private fun resolveUserIdFromUsername(
        username: String,
        cookies: Map<String, String>,
        userAgent: String?,
        appId: String?,
        timeoutSeconds: Int
    ): String? {
        if (username.all { it.isDigit() }) {
            return username
        }

        try {
            val apiUrl = "https://i.instagram.com/api/v1/users/web_profile_info/?username=$username"
            val responseStr = InstagramNativeExtractor.performGetRequest(apiUrl, cookies, userAgent, appId, timeoutSeconds)
            val json = JSONObject(responseStr)
            val userId = json.optJSONObject("data")?.optJSONObject("user")?.optString("id")
            if (!userId.isNullOrEmpty()) {
                return userId
            }
        } catch (e: InstagramRateLimitException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "web_profile_info failed for @$username: ${e.message}")
        }

        try {
            val searchUrl = "https://i.instagram.com/api/v1/users/search/?q=$username"
            val responseStr = InstagramNativeExtractor.performGetRequest(
                urlStr = searchUrl,
                cookies = cookies,
                userAgent = userAgent ?: MOBILE_USER_AGENT,
                appId = appId,
                timeoutSeconds = timeoutSeconds
            )
            val json = JSONObject(responseStr)
            val users = json.optJSONArray("users")
            if (users != null) {
                for (i in 0 until users.length()) {
                    val userObj = users.optJSONObject(i)?.optJSONObject("user")
                    if (userObj?.optString("username")?.equals(username, ignoreCase = true) == true) {
                        val pk = userObj.optString("pk")
                        if (pk.isNotEmpty()) return pk
                    }
                }
            }
        } catch (e: InstagramRateLimitException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "users/search failed for @$username: ${e.message}")
        }

        return null
    }
}
