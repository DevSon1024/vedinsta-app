package com.devson.vedinsta.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DownloadedPostDao {

    @Query("SELECT * FROM downloaded_posts ORDER BY downloadDate DESC")
    fun getAllDownloadedPosts(): LiveData<List<DownloadedPost>>

    @Query("SELECT * FROM downloaded_posts WHERE postId = :postId")
    suspend fun getPostById(postId: String): DownloadedPost?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownloadedPost(post: DownloadedPost)

    @Delete
    suspend fun deleteDownloadedPost(post: DownloadedPost)

    @Query("DELETE FROM downloaded_posts")
    suspend fun deleteAllPosts()

    @Query("SELECT COUNT(*) FROM downloaded_posts")
    suspend fun getDownloadedPostsCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_posts WHERE postId = :postId)")
    suspend fun isPostDownloaded(postId: String): Boolean
}
