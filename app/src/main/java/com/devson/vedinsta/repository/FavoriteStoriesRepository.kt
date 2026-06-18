package com.devson.vedinsta.repository

import android.content.Context
import android.util.Log
import com.devson.vedinsta.database.FavoriteAccountEntity
import com.devson.vedinsta.database.CachedStoryEntity
import com.devson.vedinsta.database.FavoriteStoriesDao
import com.devson.vedinsta.extractor.InstagramNativeExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class FavoriteStoriesRepository(
    private val dao: FavoriteStoriesDao,
    private val context: Context
) {
    private val unauthenticatedClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun getAllFavorites(): Flow<List<FavoriteAccountEntity>> = dao.getAllFavoritesFlow()

    suspend fun getAllFavoritesDirect(): List<FavoriteAccountEntity> = dao.getAllFavoritesDirect()

    suspend fun addFavorite(account: FavoriteAccountEntity) = dao.insertFavorite(account)

    suspend fun removeFavorite(username: String) = dao.deleteFavorite(username)

    suspend fun isFavorite(username: String): Boolean = dao.isFavorite(username)

    fun getUnviewedUsernamesFlow(): Flow<List<String>> {
        return dao.getUnviewedUsernamesFlow(System.currentTimeMillis())
    }

    suspend fun fetchUsersAnonymously(query: String): List<FavoriteAccountEntity> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val results = mutableListOf<FavoriteAccountEntity>()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.instagram.com/api/v1/web/search/topsearch/?query=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("X-IG-App-ID", "936619743392459")
                .header("Accept", "*/*")
                .build()

            unauthenticatedClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val usersArray = json.optJSONArray("users")
                        if (usersArray != null) {
                            for (i in 0 until usersArray.length()) {
                                val userObj = usersArray.getJSONObject(i).optJSONObject("user")
                                if (userObj != null) {
                                    val username = userObj.optString("username", "")
                                    val displayName = userObj.optString("full_name", "")
                                    val profilePicUrl = userObj.optString("profile_pic_url", "")
                                    if (username.isNotEmpty()) {
                                        results.add(
                                            FavoriteAccountEntity(
                                                username = username,
                                                displayName = if (displayName.isEmpty()) username else displayName,
                                                profilePicUrl = profilePicUrl,
                                                addedAt = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FavoriteStoriesRepo", "Error searching users in topsearch: ${e.message}")
        }

        if (results.isEmpty() && query.matches(Regex("[a-zA-Z0-9_\\.]+"))) {
            try {
                val url = "https://i.instagram.com/api/v1/users/web_profile_info/?username=$query"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("X-IG-App-ID", "936619743392459")
                    .header("Accept", "*/*")
                    .build()

                unauthenticatedClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string()
                        if (!bodyString.isNullOrEmpty()) {
                            val json = JSONObject(bodyString)
                            val userObj = json.optJSONObject("data")?.optJSONObject("user")
                            if (userObj != null) {
                                val username = userObj.optString("username", "")
                                val displayName = userObj.optString("full_name", "")
                                val profilePicUrl = userObj.optString("profile_pic_url", "")
                                if (username.isNotEmpty()) {
                                    results.add(
                                        FavoriteAccountEntity(
                                            username = username,
                                            displayName = if (displayName.isEmpty()) username else displayName,
                                            profilePicUrl = profilePicUrl,
                                            addedAt = System.currentTimeMillis()
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FavoriteStoriesRepo", "Error fetching web profile info: ${e.message}")
            }
        }

        results.distinctBy { it.username }
    }

    private suspend fun downloadFile(url: String, destFile: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        unauthenticatedClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw java.io.IOException("Failed to download file: $response")
            val body = response.body ?: throw java.io.IOException("Empty response body")
            destFile.parentFile?.mkdirs()
            destFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }

    suspend fun getStoriesForUser(username: String): List<CachedStoryEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.clearExpiredStories(now)

        val cached = dao.getCachedStories(username, now)
        if (cached.isNotEmpty()) {
            Log.d("FavoriteStoriesRepo", "Returning cached stories for @$username")
            dao.updateStoryAvailability(username, true, now)
            return@withContext cached
        }

        Log.d("FavoriteStoriesRepo", "Cache expired or empty. Fetching on-demand stories for @$username")
        try {
            val cookieFile = File(context.filesDir, "instagram_cookies.txt")
            if (!cookieFile.exists()) {
                throw Exception("Login required: cookies not found")
            }

            val prefs = context.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
            val userAgent = prefs.getString("custom_user_agent", "") ?: ""
            val appId = prefs.getString("custom_ig_app_id", "") ?: ""
            val networkTimeoutSeconds = prefs.getInt("network_timeout_seconds", 15)

            // Auto-update profile details if profile picture is missing
            val currentFav = dao.getAllFavoritesDirect().firstOrNull { it.username == username }
            if (currentFav != null && currentFav.profilePicUrl.isEmpty()) {
                val cookies = parseCookies(cookieFile)
                updateProfileDetails(username, cookies, userAgent, appId, networkTimeoutSeconds)
            }

            val mediaItems = InstagramNativeExtractor.extractUserStories(
                username = username,
                cookieFilePath = cookieFile.absolutePath,
                userAgent = userAgent,
                appId = appId,
                timeoutSeconds = networkTimeoutSeconds
            )

            if (mediaItems.isEmpty()) {
                dao.updateStoryAvailability(username, false, now)
                return@withContext emptyList()
            }

            val expiresAt = now + TimeUnit.HOURS.toMillis(24)

            val newStories = mutableListOf<CachedStoryEntity>()
            mediaItems.forEachIndexed { index, item ->
                try {
                    val extension = if (item.type == "video") "mp4" else "jpg"
                    val filename = "story_${index}_${System.currentTimeMillis()}.$extension"
                    val destFile = File(context.filesDir, "favorite_stories/$username/$filename")

                    downloadFile(item.url, destFile)

                    newStories.add(
                        CachedStoryEntity(
                            usernameFk = username,
                            localFilePath = destFile.absolutePath,
                            isVideo = item.type == "video",
                            expiresAt = expiresAt,
                            isViewed = false
                        )
                    )
                } catch (e: Exception) {
                    Log.e("FavoriteStoriesRepo", "Failed to download story file: ${e.message}")
                }
            }

            dao.insertStories(newStories)
            dao.updateStoryAvailability(username, true, now)
            return@withContext dao.getCachedStories(username, now)
        } catch (e: Exception) {
            Log.e("FavoriteStoriesRepo", "Error fetching stories for @$username", e)
            throw e
        }
    }

    suspend fun markStoriesAsViewed(username: String) {
        dao.markStoriesAsViewed(username)
    }

    suspend fun markStoryAsViewed(storyId: Long) {
        dao.markStoryAsViewed(storyId)
    }

    fun getUnviewedCountFlow(username: String): kotlinx.coroutines.flow.Flow<Int> {
        return dao.getUnviewedCountFlow(username)
    }

    fun getStoriesCountFlow(username: String): kotlinx.coroutines.flow.Flow<Int> {
        return dao.getStoriesCountFlow(username)
    }

    suspend fun checkStoryAvailabilityAnonymously(username: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "https://i.instagram.com/api/v1/users/web_profile_info/?username=$username"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("X-IG-App-ID", "936619743392459")
                .header("Accept", "*/*")
                .build()

            unauthenticatedClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val userObj = json.optJSONObject("data")?.optJSONObject("user")
                        if (userObj != null) {
                            if (userObj.has("latest_reel_media") && !userObj.isNull("latest_reel_media")) {
                                val latestReelMediaSeconds = userObj.optLong("latest_reel_media", 0L)
                                val latestReelMediaMs = latestReelMediaSeconds * 1000L
                                val now = System.currentTimeMillis()
                                val oneDayMs = 24L * 60L * 60L * 1000L
                                return@withContext latestReelMediaMs > (now - oneDayMs)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FavoriteStoriesRepo", "Error checking story availability anonymously for $username: ${e.message}")
        }
        return@withContext false
    }

    suspend fun updateStoryAvailability(username: String, hasActiveStory: Boolean?, lastStatusCheck: Long?) = withContext(Dispatchers.IO) {
        dao.updateStoryAvailability(username, hasActiveStory, lastStatusCheck)
    }

    private fun parseCookies(file: File): Map<String, String> {
        val cookies = mutableMapOf<String, String>()
        try {
            file.forEachLine { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEachLine
                val parts = line.split("\t")
                if (parts.size >= 7) {
                    val name = parts[5]
                    val value = parts[6]
                    cookies[name] = value
                }
            }
        } catch (e: Exception) {
            Log.e("FavoriteStoriesRepo", "Error parsing cookies: ${e.message}")
        }
        return cookies
    }

    private suspend fun updateProfileDetails(
        username: String,
        cookies: Map<String, String>,
        userAgent: String?,
        appId: String?,
        timeoutSeconds: Int
    ) = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://i.instagram.com/api/v1/users/$username/usernameinfo/"
            val request = Request.Builder()
                .url(apiUrl)
                .header("User-Agent", userAgent ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("X-IG-App-ID", appId ?: "936619743392459")
                .header("Accept", "*/*")

            val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
            if (cookieHeader.isNotEmpty()) {
                request.header("Cookie", cookieHeader)
            }

            unauthenticatedClient.newCall(request.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrEmpty()) {
                        val json = JSONObject(body)
                        val userObj = json.optJSONObject("user")
                        if (userObj != null) {
                            val profilePicUrl = userObj.optString("profile_pic_url", "")
                            val fullName = userObj.optString("full_name", "")
                            if (profilePicUrl.isNotEmpty()) {
                                val current = dao.getAllFavoritesDirect().firstOrNull { it.username == username }
                                if (current != null) {
                                    val updated = current.copy(
                                        profilePicUrl = profilePicUrl,
                                        displayName = if (fullName.isNotEmpty()) fullName else current.displayName
                                    )
                                    dao.insertFavorite(updated)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FavoriteStoriesRepo", "Error updating profile details for $username: ${e.message}")
        }
    }
}
