package com.devson.vedinsta.repository

import androidx.lifecycle.LiveData
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.database.DownloadedPostDao

class DownloadRepository(private val downloadedPostDao: DownloadedPostDao) {

    fun getAllDownloadedPosts(): LiveData<List<DownloadedPost>> =
        downloadedPostDao.getAllDownloadedPosts()

    suspend fun insertDownloadedPost(post: DownloadedPost) =
        downloadedPostDao.insert(post)

    suspend fun isPostDownloaded(postId: String): Boolean =
        downloadedPostDao.getPostById(postId) != null

    suspend fun getPostById(postId: String): DownloadedPost? =
        downloadedPostDao.getPostById(postId)

    suspend fun deleteDownloadedPost(post: DownloadedPost) =
        downloadedPostDao.delete(post)
}
