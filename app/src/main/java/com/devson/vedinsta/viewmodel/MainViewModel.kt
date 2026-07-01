package com.devson.vedinsta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.repository.DownloadRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import com.devson.vedinsta.viewmodel.SettingsViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DownloadRepository
    val allDownloadedPosts: LiveData<List<DownloadedPost>>
    val recentPostsHome: LiveData<List<DownloadedPost>>
    
    private val settingsViewModel: SettingsViewModel
    private val _favoritePostIds = MutableStateFlow<Set<String>>(emptySet())
    val favoritePostIds: StateFlow<Set<String>> = _favoritePostIds.asStateFlow()

    private val vpnMonitor = com.devson.vedinsta.repository.VpnAndNetworkMonitor(application)
    val isVpnActive: StateFlow<Boolean> = vpnMonitor.isVpnActive
    val isNetworkChanged: StateFlow<Boolean> = vpnMonitor.isNetworkChanged

    fun resetNetworkChangeWarning() {
        vpnMonitor.resetNetworkChangeWarning()
    }

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

    override fun onCleared() {
        super.onCleared()
        vpnMonitor.unregister()
    }
}
