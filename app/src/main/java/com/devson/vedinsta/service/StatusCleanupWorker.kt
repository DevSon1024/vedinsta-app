package com.devson.vedinsta.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StatusCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val preserverDir = applicationContext.getExternalFilesDir("WAPreserver")
            if (preserverDir != null && preserverDir.exists()) {
                val now = System.currentTimeMillis()
                val threshold = 7L * 24L * 60L * 60L * 1000L // 7 days in milliseconds
                val files = preserverDir.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (now - file.lastModified() > threshold) {
                            file.delete()
                        }
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
