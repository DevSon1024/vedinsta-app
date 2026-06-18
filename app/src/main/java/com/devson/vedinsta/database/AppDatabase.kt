package com.devson.vedinsta.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [DownloadedPost::class, NotificationEntity::class, FavoriteAccountEntity::class, CachedStoryEntity::class],
    version = 5, // Increment version to 5 for schema change
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedPostDao(): DownloadedPostDao
    abstract fun notificationDao(): NotificationDao
    abstract fun favoriteStoriesDao(): FavoriteStoriesDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2 to add mediaPaths column
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloaded_posts ADD COLUMN mediaPaths TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // Migration from version 2 to 3 to add Favorite Accounts and Cached Stories
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `favorite_accounts` (`username` TEXT NOT NULL, `profilePicUrl` TEXT NOT NULL, `displayName` TEXT NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`username`))")
                database.execSQL("CREATE TABLE IF NOT EXISTS `cached_stories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `usernameFk` TEXT NOT NULL, `mediaUrl` TEXT NOT NULL, `isVideo` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `isViewed` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        // Migration from version 3 to 4 to add status check columns
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favorite_accounts ADD COLUMN hasActiveStory INTEGER")
                database.execSQL("ALTER TABLE favorite_accounts ADD COLUMN lastStatusCheck INTEGER")
            }
        }

        // Migration from version 4 to 5 to update cached stories columns to snake_case and local file path
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS `cached_stories`")
                database.execSQL("CREATE TABLE IF NOT EXISTS `cached_stories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `username_fk` TEXT NOT NULL, `local_file_path` TEXT NOT NULL, `isVideo` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, `is_viewed` INTEGER NOT NULL DEFAULT 0)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vedInsta_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
