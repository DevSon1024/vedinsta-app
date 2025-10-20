package com.devson.vedinsta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.repository.DownloadRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: DownloadRepository
    val allDownloadedPosts: LiveData<List<DownloadedPost>>

    init {
        val dao = AppDatabase.getDatabase(application).downloadedPostDao()
        repository = DownloadRepository(dao)
        allDownloadedPosts = repository.getAllDownloadedPosts()
    }

    fun insertDownloadedPost(post: DownloadedPost) = viewModelScope.launch {
        repository.insertDownloadedPost(post)
    }

    fun checkIfPostDownloaded(postId: String, callback: (Boolean) -> Unit) = viewModelScope.launch {
        val isDownloaded = repository.isPostDownloaded(postId)
        callback(isDownloaded)
    }

    fun deleteDownloadedPost(post: DownloadedPost) = viewModelScope.launch {
        repository.deleteDownloadedPost(post)
    }
}
