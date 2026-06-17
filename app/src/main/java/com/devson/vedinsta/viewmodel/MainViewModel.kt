package com.devson.vedinsta.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.database.FavoriteAccount
import com.devson.vedinsta.model.StoryTrayItem
import com.devson.vedinsta.repository.DownloadRepository
import com.devson.vedinsta.repository.StoryRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.devson.vedinsta.viewmodel.SettingsViewModel

class RateLimitCooldownException(message: String) : Exception(message)


class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DownloadRepository
    val allDownloadedPosts: LiveData<List<DownloadedPost>>
    val recentPostsHome: LiveData<List<DownloadedPost>>
    
    private val settingsViewModel: SettingsViewModel
    private val _favoritePostIds = MutableStateFlow<Set<String>>(emptySet())
    val favoritePostIds: StateFlow<Set<String>> = _favoritePostIds.asStateFlow()

    private val favoriteAccountDao = AppDatabase.getDatabase(application).favoriteAccountDao()
    val favoriteAccounts: LiveData<List<FavoriteAccount>> = favoriteAccountDao.getAllFavoriteAccountsLive()

    private val _isRefreshingStories = MutableStateFlow(false)
    val isRefreshingStories = _isRefreshingStories.asStateFlow()

    private val _storyTray = MutableStateFlow<List<StoryTrayItem>>(emptyList())
    val storyTray = _storyTray.asStateFlow()

    private val storyRepository = StoryRepository(application)

    private val _currentStoryMedia = MutableStateFlow<List<com.devson.vedinsta.model.MediaResult>>(emptyList())
    val currentStoryMedia = _currentStoryMedia.asStateFlow()

    private val _currentStoryUser = MutableStateFlow("")
    val currentStoryUser = _currentStoryUser.asStateFlow()

    private val _currentStoryProfilePic = MutableStateFlow("")
    val currentStoryProfilePic = _currentStoryProfilePic.asStateFlow()

    private val _isLoadingStories = MutableStateFlow(false)
    val isLoadingStories = _isLoadingStories.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).downloadedPostDao()
        repository = DownloadRepository(dao)
        allDownloadedPosts = repository.getAllDownloadedPosts()
        recentPostsHome = repository.getRecentDownloadedPosts(8)
        settingsViewModel = SettingsViewModel(application)
        
        viewModelScope.launch(Dispatchers.IO) {
            _favoritePostIds.value = settingsViewModel.favoritePostIds
        }
        cleanUpGhostRecords()
        fetchReelsTray(force = false)
    }

    fun fetchReelsTray(force: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = getApplication<Application>().getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()

            if (force) {
                val lastNetworkCall = prefs.getLong("last_reels_tray_network_call_time", 0L)
                val elapsed = now - lastNetworkCall
                if (elapsed < 5 * 60 * 1000L) { // 5 minutes rate limit
                    try {
                        throw RateLimitCooldownException("Rate limit cooldown active")
                    } catch (e: RateLimitCooldownException) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                getApplication(),
                                "Stories are up to date",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }
                }
            }

            _isRefreshingStories.value = true
            try {
                storyRepository.fetchStoryTray(force)
                    .collect { trayItems ->
                        _storyTray.value = trayItems
                    }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error fetching reels tray", e)
            } finally {
                _isRefreshingStories.value = false
            }
        }
    }

    fun loadStoriesForUser(userId: String, username: String, profilePicUrl: String, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingStories.value = true
            _currentStoryUser.value = username
            _currentStoryProfilePic.value = profilePicUrl
            try {
                val cookieFile = java.io.File(getApplication<Application>().filesDir, "instagram_cookies.txt")
                val prefs = getApplication<Application>().getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
                val userAgent = prefs.getString("custom_user_agent", "")
                val appId = prefs.getString("custom_ig_app_id", "")
                val timeout = prefs.getInt("network_timeout_seconds", 15)

                val resultJson = if (userId.isNotEmpty()) {
                    com.devson.vedinsta.extractor.InstagramNativeExtractor.getUserStoriesByUserId(
                        userId = userId,
                        username = username,
                        cookieFilePath = cookieFile.absolutePath,
                        userAgent = userAgent,
                        appId = appId,
                        timeoutSeconds = timeout
                    )
                } else {
                    com.devson.vedinsta.extractor.InstagramNativeExtractor.getMediaUrls(
                        url = "https://www.instagram.com/stories/$username/",
                        cookieFilePath = cookieFile.absolutePath,
                        userAgent = userAgent,
                        appId = appId,
                        timeoutSeconds = timeout
                    )
                }

                val response = com.google.gson.Gson().fromJson(resultJson, com.devson.vedinsta.model.UserStoryResponse::class.java)
                if (response != null && response.status == "success" && !response.media.isNullOrEmpty()) {
                    _currentStoryMedia.value = response.media
                    withContext(Dispatchers.Main) {
                        onFinished(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onFinished(false)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error loading user stories", e)
                withContext(Dispatchers.Main) {
                    onFinished(false)
                }
            } finally {
                _isLoadingStories.value = false
            }
        }
    }

    fun addFavoriteAccount(username: String, onFinished: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cleanedUsername = username.trim().removePrefix("@")
                if (cleanedUsername.isEmpty()) {
                    withContext(Dispatchers.Main) { onFinished("Username cannot be empty") }
                    return@launch
                }

                val existing = favoriteAccountDao.getFavoriteAccountByUsername(cleanedUsername)
                if (existing != null) {
                    withContext(Dispatchers.Main) { onFinished("Account is already a favorite") }
                    return@launch
                }

                val cookieFile = java.io.File(getApplication<Application>().filesDir, "instagram_cookies.txt")
                val prefs = getApplication<Application>().getSharedPreferences("VedInstaPrefs", Context.MODE_PRIVATE)
                val userAgent = prefs.getString("custom_user_agent", "")
                val appId = prefs.getString("custom_ig_app_id", "")

                val resultJson = com.devson.vedinsta.extractor.InstagramNativeExtractor.getUserInfoByUsername(
                    username = cleanedUsername,
                    cookieFilePath = cookieFile.absolutePath,
                    userAgent = userAgent,
                    appId = appId
                )

                val response = com.google.gson.Gson().fromJson(resultJson, com.devson.vedinsta.model.UserInfoResponse::class.java)
                if (response != null && response.status == "success" && response.userId != null && response.profilePicUrl != null) {
                    val newFav = FavoriteAccount(
                        username = response.username ?: cleanedUsername,
                        userId = response.userId,
                        profilePicUrl = response.profilePicUrl
                    )
                    favoriteAccountDao.insert(newFav)
                    withContext(Dispatchers.Main) { onFinished(null) }
                } else {
                    val msg = response?.message ?: "Failed to resolve username. Check internet/login."
                    withContext(Dispatchers.Main) { onFinished(msg) }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error adding favorite account", e)
                withContext(Dispatchers.Main) { onFinished("Error: ${e.message}") }
            }
        }
    }

    fun removeFavoriteAccount(username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            favoriteAccountDao.deleteByUsername(username)
        }
    }

    fun insertDownloadedPost(post: DownloadedPost) = viewModelScope.launch {
        repository.insertDownloadedPost(post)
    }

    fun checkIfPostDownloaded(postId: String, callback: (Boolean) -> Unit) = viewModelScope.launch {
        val isDownloaded = repository.isPostDownloaded(postId)
        callback(isDownloaded)
    }

    fun deleteDownloadedPost(post: DownloadedPost) = viewModelScope.launch(Dispatchers.IO) {
        repository.deleteDownloadedPost(post)
        post.mediaPaths.forEach { path ->
            try {
                val file = java.io.File(path)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: java.lang.Exception) {}
        }
    }

    fun toggleFavorite(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsViewModel.toggleFavorite(postId)
            _favoritePostIds.value = settingsViewModel.favoritePostIds
        }
    }

    suspend fun getPostById(postId: String): DownloadedPost? {
        return repository.getPostById(postId)
    }

    fun cleanUpGhostRecords() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val posts = repository.getAllDownloadedPostsDirect()
                for (post in posts) {
                    val exists = java.io.File(post.thumbnailPath).exists()
                    if (!exists) {
                        repository.deleteDownloadedPost(post)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Error in cleanUpGhostRecords", e)
            }
        }
    }
}
