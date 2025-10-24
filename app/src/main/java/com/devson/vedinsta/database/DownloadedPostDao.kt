package com.devson.vedinsta.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DownloadedPostDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(downloadedPost: DownloadedPost)

    @Delete
    suspend fun delete(downloadedPost: DownloadedPost)

    @Query("DELETE FROM downloaded_posts WHERE postId = :postId")
    suspend fun deleteByPostId(postId: String)

    @Query("SELECT * FROM downloaded_posts ORDER BY downloadDate DESC")
    fun getAllDownloadedPosts(): LiveData<List<DownloadedPost>>

    @Query("SELECT * FROM downloaded_posts WHERE postId = :postId")
    suspend fun getPostById(postId: String): DownloadedPost?

    @Query("SELECT * FROM downloaded_posts WHERE username = :username ORDER BY downloadDate DESC")
    fun getPostsByUsername(username: String): LiveData<List<DownloadedPost>>

    @Query("SELECT COUNT(*) FROM downloaded_posts")
    suspend fun getPostCount(): Int

    @Query("UPDATE downloaded_posts SET mediaPaths = :mediaPaths WHERE postId = :postId")
    suspend fun updateMediaPaths(postId: String, mediaPaths: List<String>)

    @Query("SELECT mediaPaths FROM downloaded_posts WHERE postId = :postId")
    suspend fun getMediaPaths(postId: String): List<String>?

    @Query("DELETE FROM downloaded_posts")
    suspend fun deleteAll()
}
