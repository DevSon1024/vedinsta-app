package com.devson.vedinsta.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface DownloadedPostDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)  // CHANGED: Use IGNORE instead of REPLACE
    suspend fun insert(downloadedPost: DownloadedPost)

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Keep REPLACE for forced updates
    suspend fun insertOrReplace(downloadedPost: DownloadedPost)

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

    @Query("UPDATE downloaded_posts SET mediaPaths = :mediaPaths, totalImages = :totalImages WHERE postId = :postId")
    suspend fun updateMediaPathsAndCount(postId: String, mediaPaths: List<String>, totalImages: Int)

    @Query("SELECT mediaPaths FROM downloaded_posts WHERE postId = :postId")
    suspend fun getMediaPaths(postId: String): List<String>?

    @Query("DELETE FROM downloaded_posts")
    suspend fun deleteAll()

    @Query("SELECT * FROM downloaded_posts WHERE postId = :postId LIMIT 1")
    fun getPostByIdLive(postId: String): LiveData<DownloadedPost?>

    @Query("SELECT COUNT(*) FROM downloaded_posts WHERE postId = :postId")
    suspend fun isPostDownloaded(postId: String): Int

    @Query("SELECT * FROM downloaded_posts WHERE hasVideo = 1 ORDER BY downloadDate DESC")
    fun getVideoPostsOnly(): LiveData<List<DownloadedPost>>

    @Query("SELECT * FROM downloaded_posts WHERE hasVideo = 0 ORDER BY downloadDate DESC")
    fun getImagePostsOnly(): LiveData<List<DownloadedPost>>

    @Query("UPDATE downloaded_posts SET username = :username, caption = :caption WHERE postId = :postId AND mediaPaths != '[]'")
    suspend fun updatePostMetadataIfHasMedia(postId: String, username: String, caption: String?)

    // CRITICAL: Method to ensure media paths are preserved during metadata updates
    @Query("""
        UPDATE downloaded_posts 
        SET username = :username, caption = :caption 
        WHERE postId = :postId 
        AND (mediaPaths IS NULL OR mediaPaths = '[]' OR LENGTH(mediaPaths) <= 2)
    """)
    suspend fun updatePostMetadataOnlyIfEmpty(postId: String, username: String, caption: String?)
}
