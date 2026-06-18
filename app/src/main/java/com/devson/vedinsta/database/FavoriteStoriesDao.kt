package com.devson.vedinsta.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteStoriesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(account: FavoriteAccountEntity)

    @Query("SELECT * FROM favorite_accounts ORDER BY addedAt DESC")
    fun getAllFavoritesFlow(): Flow<List<FavoriteAccountEntity>>

    @Query("SELECT * FROM favorite_accounts ORDER BY addedAt DESC")
    suspend fun getAllFavoritesDirect(): List<FavoriteAccountEntity>

    @Query("DELETE FROM favorite_accounts WHERE username = :username")
    suspend fun deleteFavorite(username: String)

    @Query("UPDATE favorite_accounts SET hasActiveStory = :hasActiveStory, lastStatusCheck = :lastStatusCheck WHERE username = :username")
    suspend fun updateStoryAvailability(username: String, hasActiveStory: Boolean?, lastStatusCheck: Long?)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_accounts WHERE username = :username)")
    suspend fun isFavorite(username: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<CachedStoryEntity>)

    @Query("SELECT * FROM cached_stories WHERE username_fk = :username AND expiresAt > :currentTime ORDER BY id ASC")
    suspend fun getCachedStories(username: String, currentTime: Long): List<CachedStoryEntity>

    @Query("UPDATE cached_stories SET is_viewed = 1 WHERE username_fk = :username")
    suspend fun markStoriesAsViewed(username: String)

    @Query("DELETE FROM cached_stories WHERE expiresAt <= :currentTime")
    suspend fun clearExpiredStories(currentTime: Long)

    @Query("SELECT DISTINCT username_fk FROM cached_stories WHERE is_viewed = 0 AND expiresAt > :currentTime")
    fun getUnviewedUsernamesFlow(currentTime: Long): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM cached_stories WHERE username_fk = :username AND is_viewed = 0")
    fun getUnviewedCountFlow(username: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_stories WHERE username_fk = :username")
    fun getStoriesCountFlow(username: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM cached_stories WHERE username_fk = :username")
    suspend fun getStoriesCount(username: String): Int

    @Query("UPDATE cached_stories SET is_viewed = 1 WHERE id = :storyId")
    suspend fun markStoryAsViewed(storyId: Long)

    @Query("DELETE FROM cached_stories WHERE local_file_path = :path")
    suspend fun deleteStoryByPath(path: String)

    @Query("SELECT * FROM cached_stories")
    suspend fun getAllStoriesDirect(): List<CachedStoryEntity>
}
