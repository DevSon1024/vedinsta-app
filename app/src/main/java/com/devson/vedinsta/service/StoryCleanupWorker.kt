package com.devson.vedinsta.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.devson.vedinsta.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StoryCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val twentyFourHoursMs = 24L * 60L * 60L * 1000L
            val threshold = now - twentyFourHoursMs

            val baseDir = File(applicationContext.filesDir, "favorite_stories")
            if (baseDir.exists() && baseDir.isDirectory) {
                val db = AppDatabase.getDatabase(applicationContext)
                val dao = db.favoriteStoriesDao()

                baseDir.listFiles()?.forEach { userDir ->
                    if (userDir.isDirectory) {
                        userDir.listFiles()?.forEach { file ->
                            if (file.isFile && file.lastModified() < threshold) {
                                val path = file.absolutePath
                                if (file.delete()) {
                                    dao.deleteStoryByPath(path)
                                    Log.d("StoryCleanupWorker", "Deleted old story file and database row: $path")
                                }
                            }
                        }
                        // Delete user dir if empty
                        if (userDir.list()?.isEmpty() == true) {
                            userDir.delete()
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("StoryCleanupWorker", "Error in story cleanup", e)
            Result.failure()
        }
    }
}
