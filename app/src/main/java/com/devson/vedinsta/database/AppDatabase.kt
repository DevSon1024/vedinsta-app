package com.devson.vedinsta.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [DownloadedPost::class, NotificationEntity::class],
    version = 3, // Increment version to 3
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadedPostDao(): DownloadedPostDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from version 1 to 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS notifications (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        message TEXT NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        postId TEXT,
                        postUrl TEXT,
                        filePaths TEXT,
                        thumbnailPath TEXT,
                        priority TEXT NOT NULL DEFAULT 'NORMAL'
                    )
                """)
            }
        }

        // Migration from version 2 to 3
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE downloaded_posts ADD COLUMN username TEXT NOT NULL DEFAULT 'unknown'")
                database.execSQL("ALTER TABLE downloaded_posts ADD COLUMN caption TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vedinsta_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3) // Add new migration
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}