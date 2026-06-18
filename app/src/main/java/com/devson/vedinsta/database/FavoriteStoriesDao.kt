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

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_accounts WHERE username = :username)")
    suspend fun isFavorite(username: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStories(stories: List<CachedStoryEntity>)

    @Query("SELECT * FROM cached_stories WHERE usernameFk = :username AND expiresAt > :currentTime ORDER BY id ASC")
    suspend fun getCachedStories(username: String, currentTime: Long): List<CachedStoryEntity>

    @Query("UPDATE cached_stories SET isViewed = 1 WHERE usernameFk = :username")
    suspend fun markStoriesAsViewed(username: String)

    @Query("DELETE FROM cached_stories WHERE expiresAt <= :currentTime")
    suspend fun clearExpiredStories(currentTime: Long)

    @Query("SELECT DISTINCT usernameFk FROM cached_stories WHERE isViewed = 0 AND expiresAt > :currentTime")
    fun getUnviewedUsernamesFlow(currentTime: Long): Flow<List<String>>
}
