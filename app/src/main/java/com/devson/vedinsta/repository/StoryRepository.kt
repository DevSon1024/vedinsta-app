package com.devson.vedinsta.repository

import android.content.Context
import android.util.Log
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.CachedStoryTray
import com.devson.vedinsta.model.StoryTrayItem
import com.devson.vedinsta.model.StoryTrayResponse
import com.devson.vedinsta.extractor.InstagramNativeExtractor
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class StoryRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val cachedStoryTrayDao = database.cachedStoryTrayDao()
    private val gson = Gson()

    fun fetchStoryTray(force: Boolean): Flow<List<StoryTrayItem>> = flow {
        val now = System.currentTimeMillis()
        
        // 1. Check cached data
        val cached = cachedStoryTrayDao.getCachedStoryTray()
        
        if (!force && cached.isNotEmpty()) {
            val firstItem = cached.first()
            if (now < firstItem.expiryTimestamp) {
                Log.d("StoryRepository", "Emitting valid cache for reels tray (TTL not expired)")
                emit(cached.map {
                    StoryTrayItem(
                        userId = it.userId,
                        username = it.username,
                        profilePicUrl = it.profilePicUrl,
                        isSeen = it.isSeen,
                        latestReelMedia = it.latestReelMedia,
                        seen = it.seen
                    )
                })
                return@flow
            } else {
                Log.d("StoryRepository", "Cache expired (now: $now, expiry: ${firstItem.expiryTimestamp})")
            }
        }

        // 2. Fetch from network
        Log.d("StoryRepository", "Fetching Reels tray from network")
        val cookieFile = File(context.filesDir, "instagram_cookies.txt")
        val prefs = context.getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
        val userAgent = prefs.getString("custom_user_agent", "")
        val appId = prefs.getString("custom_ig_app_id", "")
        val timeout = prefs.getInt("network_timeout_seconds", 15)

        val resultJson = InstagramNativeExtractor.getReelsTray(
            cookieFilePath = cookieFile.absolutePath,
            userAgent = userAgent,
            appId = appId,
            timeoutSeconds = timeout
        )

        val response = gson.fromJson(resultJson, StoryTrayResponse::class.java)
        if (response != null && response.status == "success" && response.tray != null) {
            val trayList = response.tray
            val expiry = System.currentTimeMillis() + (30 * 60 * 1000L) // 30 minutes TTL
            
            val entities = trayList.map {
                CachedStoryTray(
                    userId = it.userId,
                    username = it.username,
                    profilePicUrl = it.profilePicUrl,
                    isSeen = it.isSeen,
                    latestReelMedia = it.latestReelMedia,
                    seen = it.seen,
                    expiryTimestamp = expiry
                )
            }
            cachedStoryTrayDao.refreshStoryTray(entities)
            
            // Persist successful network call timestamp in preferences for throttling
            prefs.edit().putLong("last_reels_tray_network_call_time", System.currentTimeMillis()).apply()
            
            emit(trayList)
        } else {
            val errorMsg = response?.message ?: "Unknown API response"
            Log.e("StoryRepository", "API Error: $errorMsg")
            
            // Fallback to expired cache if available to prevent blank screens
            if (cached.isNotEmpty()) {
                Log.d("StoryRepository", "Emitting expired cache as fallback due to network failure")
                emit(cached.map {
                    StoryTrayItem(
                        userId = it.userId,
                        username = it.username,
                        profilePicUrl = it.profilePicUrl,
                        isSeen = it.isSeen,
                        latestReelMedia = it.latestReelMedia,
                        seen = it.seen
                    )
                })
            } else {
                throw Exception(errorMsg)
            }
        }
    }.flowOn(Dispatchers.IO)
}
