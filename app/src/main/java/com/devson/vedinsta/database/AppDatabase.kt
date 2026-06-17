package com.devson.vedinsta.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [DownloadedPost::class, NotificationEntity::class, FavoriteAccount::class, CachedStoryTray::class],
    version = 4, // Increment version for CachedStoryTray table schema change
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedPostDao(): DownloadedPostDao
    abstract fun notificationDao(): NotificationDao
    abstract fun favoriteAccountDao(): FavoriteAccountDao
    abstract fun cachedStoryTrayDao(): CachedStoryTrayDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2 to add mediaPaths column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloaded_posts ADD COLUMN mediaPaths TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // Migration from version 2 to 3 to add favorite_accounts table
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `favorite_accounts` (" +
                    "`username` TEXT NOT NULL, " +
                    "`userId` TEXT NOT NULL, " +
                    "`profilePicUrl` TEXT NOT NULL, " +
                    "PRIMARY KEY(`username`))"
                )
            }
        }

        // Migration from version 3 to 4 to add cached_story_trays table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_story_trays` (" +
                    "`userId` TEXT NOT NULL, " +
                    "`username` TEXT NOT NULL, " +
                    "`profilePicUrl` TEXT NOT NULL, " +
                    "`isSeen` INTEGER NOT NULL, " +
                    "`latestReelMedia` INTEGER NOT NULL, " +
                    "`seen` INTEGER NOT NULL, " +
                    "`expiryTimestamp` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`userId`))"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vedInsta_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
