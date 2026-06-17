package com.devson.vedinsta.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CachedStoryTrayDao {
    @Query("SELECT * FROM cached_story_trays")
    suspend fun getCachedStoryTray(): List<CachedStoryTray>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CachedStoryTray>)

    @Query("DELETE FROM cached_story_trays")
    suspend fun clearAll()

    @Transaction
    suspend fun refreshStoryTray(items: List<CachedStoryTray>) {
        clearAll()
        insertAll(items)
    }
}
