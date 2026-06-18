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

    suspend fun getStoriesForUser(username: String): List<CachedStoryEntity> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.clearExpiredStories(now)

        val cached = dao.getCachedStories(username, now)
        if (cached.isNotEmpty()) {
            Log.d("FavoriteStoriesRepo", "Returning cached stories for @$username")
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

            val mediaItems = InstagramNativeExtractor.extractUserStories(
                username = username,
                cookieFilePath = cookieFile.absolutePath,
                userAgent = userAgent,
                appId = appId,
                timeoutSeconds = networkTimeoutSeconds
            )

            if (mediaItems.isEmpty()) {
                return@withContext emptyList()
            }

            val expiresAt = now + TimeUnit.HOURS.toMillis(4)

            val newStories = mediaItems.map { item ->
                CachedStoryEntity(
                    usernameFk = username,
                    mediaUrl = item.url,
                    isVideo = item.type == "video",
                    expiresAt = expiresAt,
                    isViewed = false
                )
            }

            dao.insertStories(newStories)
            return@withContext dao.getCachedStories(username, now)
        } catch (e: Exception) {
            Log.e("FavoriteStoriesRepo", "Error fetching stories for @$username", e)
            throw e
        }
    }

    suspend fun markStoriesAsViewed(username: String) {
        dao.markStoriesAsViewed(username)
    }
}
