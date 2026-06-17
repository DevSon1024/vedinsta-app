package com.devson.vedinsta.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface FavoriteAccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favoriteAccount: FavoriteAccount)

    @Delete
    suspend fun delete(favoriteAccount: FavoriteAccount)

    @Query("DELETE FROM favorite_accounts WHERE username = :username")
    suspend fun deleteByUsername(username: String)

    @Query("SELECT * FROM favorite_accounts ORDER BY username ASC")
    fun getAllFavoriteAccountsLive(): LiveData<List<FavoriteAccount>>

    @Query("SELECT * FROM favorite_accounts")
    suspend fun getAllFavoriteAccounts(): List<FavoriteAccount>

    @Query("SELECT * FROM favorite_accounts WHERE username = :username LIMIT 1")
    suspend fun getFavoriteAccountByUsername(username: String): FavoriteAccount?
}
