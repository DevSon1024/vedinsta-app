package com.devson.vedinsta.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.app.NotificationCompat
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Keep track of concurrent download tasks
    private val activeTasks = AtomicInteger(0)

    companion object {
        const val EXTRA_DOWNLOAD_URL = "download_url"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_POST_URL = "post_url"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_CAPTION = "caption"
        const val EXTRA_TOTAL_IMAGES = "total_images"
        const val EXTRA_HAS_VIDEO = "has_video"

        private val dbMutex = Mutex()
        private const val TAG = "DownloadService"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36 Instagram 314.0.0.19.113 Android (34/14; 450dpi; 1440x3088; samsung; SM-S918B; dm3q; qcom; en_US; 557876543)"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = VedInstaNotificationManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val url = it.getStringExtra(EXTRA_DOWNLOAD_URL)
            val filePath = it.getStringExtra(EXTRA_FILE_PATH)
            val fileName = it.getStringExtra(EXTRA_FILE_NAME)
            val postId = it.getStringExtra(EXTRA_POST_ID)
            val postUrl = it.getStringExtra(EXTRA_POST_URL)
            val username = it.getStringExtra(EXTRA_USERNAME)
            val caption = it.getStringExtra(EXTRA_CAPTION)
            val totalImages = it.getIntExtra(EXTRA_TOTAL_IMAGES, 1)
            val hasVideo = it.getBooleanExtra(EXTRA_HAS_VIDEO, false)

            if (url != null && filePath != null && fileName != null) {
                val notificationId = System.currentTimeMillis().toInt()
                
                activeTasks.incrementAndGet()

                val notification = createForegroundNotification(fileName)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        notificationId,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    ServiceCompat.startForeground(
                        this,
                        notificationId,
                        notification,
                        0
                    )
                }

                serviceScope.launch {
                    try {
                        downloadFile(
                            url = url,
                            filePath = filePath,
                            fileName = fileName,
                            notificationId = notificationId,
                            postId = postId,
                            postUrl = postUrl,
                            username = username,
                            caption = caption,
                            totalImages = totalImages,
                            hasVideo = hasVideo
                        )
                    } finally {
                        val remaining = activeTasks.decrementAndGet()
                        if (remaining <= 0) {
                            ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                }
            } else {
                if (activeTasks.get() == 0) {
                    stopSelfResult(startId)
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createForegroundNotification(fileName: String) =
        NotificationCompat.Builder(this, VedInstaNotificationManager.CHANNEL_ID_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta Download")
            .setContentText("Downloading: $fileName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private suspend fun downloadFile(
        url: String,
        filePath: String,
        fileName: String,
        notificationId: Int,
        postId: String?,
        postUrl: String?,
        username: String?,
        caption: String?,
        totalImages: Int,
        hasVideo: Boolean
    ) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    notificationManager.showDownloadError(fileName, "HTTP ${response.code}")
                    return
                }

                val body = response.body ?: throw IOException("Empty response body")

                val file = File(filePath)
                file.parentFile?.mkdirs()

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }
            }

            notificationManager.cancelDownloadNotification(notificationId)
            notificationManager.showDownloadCompleted(fileName, 1)

            // Index file in MediaStore so it displays in native gallery immediately
            scanFileWithMediaScanner(filePath)

            // Save to Room DB if postId is provided
            if (postId != null && postUrl != null) {
                saveDownloadedPostToDb(
                    postId = postId,
                    postUrl = postUrl,
                    downloadedFiles = listOf(filePath),
                    hasVideo = hasVideo,
                    username = username ?: "unknown",
                    caption = caption
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to download $fileName", e)
            notificationManager.showDownloadError(fileName, e.message ?: "Unknown error")
        }
    }

    private suspend fun saveDownloadedPostToDb(
        postId: String,
        postUrl: String,
        downloadedFiles: List<String>,
        hasVideo: Boolean,
        username: String,
        caption: String?
    ) {
        if (downloadedFiles.isEmpty()) return
        try {
            dbMutex.withLock {
                withContext(Dispatchers.IO) {
                    val db = com.devson.vedinsta.database.AppDatabase.getDatabase(applicationContext)
                    val dao = db.downloadedPostDao()
                    val existingPost = dao.getPostById(postId)

                    if (existingPost != null) {
                        val updatedPaths = (existingPost.mediaPaths + downloadedFiles).distinct()
                        
                        val isVideo = { path: String ->
                            val ext = path.substringAfterLast('.', "").lowercase()
                            ext in listOf("mp4", "mov", "avi", "mkv", "webm")
                        }
                        val newFirstImagePath = downloadedFiles.firstOrNull { !isVideo(it) }
                        val newFirstVideoPath = downloadedFiles.firstOrNull { isVideo(it) }
                        val currentThumbnailValid = existingPost.thumbnailPath.isNotBlank() && File(existingPost.thumbnailPath).exists()

                        val updatedThumbnailPath = when {
                            newFirstImagePath != null -> newFirstImagePath
                            currentThumbnailValid -> existingPost.thumbnailPath
                            newFirstVideoPath != null -> newFirstVideoPath
                            else -> ""
                        }

                        val updatedUsername = if (existingPost.username == "unknown" || existingPost.username == "downloading...") {
                            username
                        } else {
                            existingPost.username
                        }

                        val updatedCaption = existingPost.caption.takeIf { !it.isNullOrBlank() } ?: caption

                        val updatedPost = existingPost.copy(
                            mediaPaths = updatedPaths,
                            totalImages = updatedPaths.size,
                            downloadDate = System.currentTimeMillis(),
                            thumbnailPath = updatedThumbnailPath,
                            username = updatedUsername,
                            caption = updatedCaption,
                            hasVideo = existingPost.hasVideo || hasVideo
                        )
                        dao.insertOrReplace(updatedPost)
                        Log.d(TAG, "Updated existing post $postId in DB. Total media: ${updatedPaths.size}")
                    } else {
                        val isVideo = { path: String ->
                            val ext = path.substringAfterLast('.', "").lowercase()
                            ext in listOf("mp4", "mov", "avi", "mkv", "webm")
                        }
                        val firstImagePath = downloadedFiles.firstOrNull { !isVideo(it) }
                        val firstVideoPath = downloadedFiles.firstOrNull { isVideo(it) }
                        val thumbnailPath = firstImagePath ?: firstVideoPath ?: ""

                        val newPost = com.devson.vedinsta.database.DownloadedPost(
                            postId = postId,
                            postUrl = postUrl,
                            thumbnailPath = thumbnailPath,
                            totalImages = downloadedFiles.size,
                            downloadDate = System.currentTimeMillis(),
                            hasVideo = hasVideo,
                            username = username,
                            caption = caption,
                            mediaPaths = downloadedFiles.distinct()
                        )
                        dao.insert(newPost)
                        Log.d(TAG, "Inserted new post $postId in DB with ${downloadedFiles.size} media.")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DATABASE SAVE/UPDATE ERROR for key $postId", e)
        }
    }

    private fun scanFileWithMediaScanner(filePath: String) {
        try {
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(filePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Scanned $path -> MediaStore URI: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan file with MediaScannerConnection", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}