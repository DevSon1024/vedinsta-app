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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    // Keep track of concurrent download tasks
    private val activeTasks = AtomicInteger(0)

    private data class DownloadRequest(
        val url: String,
        val filePath: String,
        val fileName: String,
        val notificationId: Int,
        val postId: String?,
        val postUrl: String?,
        val username: String?,
        val caption: String?,
        val totalImages: Int,
        val hasVideo: Boolean
    )

    // Unlimited channel acts as a memory queue for sequential downloads
    private val downloadChannel = kotlinx.coroutines.channels.Channel<DownloadRequest>(kotlinx.coroutines.channels.Channel.UNLIMITED)

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

        const val EXTRA_DOWNLOAD_URLS_LIST = "download_urls_list"
        const val EXTRA_FILE_PATHS_LIST = "file_paths_list"
        const val EXTRA_FILE_NAMES_LIST = "file_names_list"
        const val EXTRA_MEDIA_TYPES_LIST = "media_types_list"

        private const val TAG = "DownloadService"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.193 Mobile Safari/537.36 Instagram 314.0.0.19.113 Android (34/14; 450dpi; 1440x3088; samsung; SM-S918B; dm3q; qcom; en_US; 557876543)"

        // ConcurrentMaps for batch download progress tracking
        private val batchProgressMap = ConcurrentHashMap<String, Pair<AtomicInteger, AtomicInteger>>()
        private val batchCompletedFilesMap = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

        // Map to keep track of active download jobs for cancellation
        val activeJobs = ConcurrentHashMap<String, Job>()

        fun cancelDownload(postId: String): Boolean {
            val job = activeJobs.remove(postId)
            return if (job != null) {
                job.cancel()
                true
            } else {
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = VedInstaNotificationManager.getInstance(this)

        // ANR FIX: Semaphore(1) enforces strictly sequential downloads.
        // With Semaphore(3), three coroutines hammered the SAME notification ID concurrently,
        // causing system_server IPC deadlocks and glitched/stuck progress bars.
        // Sequential execution guarantees the progress bar moves smoothly 0-100% without races.
        val semaphore = Semaphore(1)
        serviceScope.launch {
            for (request in downloadChannel) {
                val jobId = request.postId ?: request.fileName
                val job = launch {
                    semaphore.withPermit {
                        try {
                            downloadFile(
                                url = request.url,
                                filePath = request.filePath,
                                fileName = request.fileName,
                                notificationId = request.notificationId,
                                postId = request.postId,
                                postUrl = request.postUrl,
                                username = request.username,
                                caption = request.caption,
                                totalImages = request.totalImages,
                                hasVideo = request.hasVideo
                            )
                        } finally {
                            val remaining = activeTasks.decrementAndGet()
                            if (remaining <= 0) {
                                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                                com.devson.vedinsta.VedInstaApplication.clearAppCache(applicationContext)
                                stopSelf()
                            }
                        }
                    }
                }
                activeJobs[jobId] = job
                job.invokeOnCompletion { activeJobs.remove(jobId) }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val urls = it.getStringArrayListExtra(EXTRA_DOWNLOAD_URLS_LIST)
            val filePaths = it.getStringArrayListExtra(EXTRA_FILE_PATHS_LIST)
            val fileNames = it.getStringArrayListExtra(EXTRA_FILE_NAMES_LIST)
            val mediaTypes = it.getStringArrayListExtra(EXTRA_MEDIA_TYPES_LIST)

            if (urls != null && filePaths != null && fileNames != null && urls.isNotEmpty()) {
                val size = minOf(urls.size, filePaths.size, fileNames.size)
                if (size == 0) {
                    if (activeTasks.get() == 0) {
                        stopSelfResult(startId)
                    }
                    return START_NOT_STICKY
                }
                val totalImages = it.getIntExtra(EXTRA_TOTAL_IMAGES, size)
                val postId = it.getStringExtra(EXTRA_POST_ID)
                val postUrl = it.getStringExtra(EXTRA_POST_URL)
                val username = it.getStringExtra(EXTRA_USERNAME)
                val caption = it.getStringExtra(EXTRA_CAPTION)

                val isBatch = postId != null && totalImages > 1
                val notificationId = if (isBatch) postId!!.hashCode() else (postId?.hashCode() ?: fileNames.firstOrNull()?.hashCode() ?: 4242)

                val displayUsername = username ?: "unknown"
                val startMsg = if (isBatch) {
                    "Downloading $totalImages files from @$displayUsername"
                } else {
                    "Downloading media from @$displayUsername"
                }
                notificationManager.showDownloadStartedPopup("Download Started", startMsg)

                if (isBatch) {
                    batchProgressMap.putIfAbsent(postId!!, Pair(AtomicInteger(0), AtomicInteger(0)))
                    batchCompletedFilesMap.putIfAbsent(postId, CopyOnWriteArrayList())
                    updateProgressInDb(postId, username, "0/$totalImages")
                } else {
                    updateProgressInDb(postId ?: fileNames.firstOrNull(), username, "0/1")
                }

                val currentTaskCount = activeTasks.addAndGet(size)
                val isFirstTask = (currentTaskCount - size) == 0

                if (isFirstTask) {
                    val notification = if (isBatch) {
                        val progress = batchProgressMap[postId]
                        val finished = progress?.first?.get() ?: 0
                        val remaining = totalImages - finished
                        val subText = when {
                            remaining <= 0 -> "All files downloaded"
                            remaining == 1 -> "$remaining file remaining"
                            else -> "$remaining files remaining"
                        }
                        NotificationCompat.Builder(this, VedInstaNotificationManager.CHANNEL_ID_SILENT)
                            .setSmallIcon(android.R.drawable.stat_sys_download)
                            .setContentTitle("VedInsta ⋅ Downloading")
                            .setContentText("Downloading... ($finished/$totalImages files)")
                            .setSubText(subText)
                            .setProgress(totalImages, finished, false)
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .build()
                    } else {
                        createForegroundNotification(fileNames.firstOrNull() ?: "media")
                    }

                    try {
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start foreground service", e)
                    }
                }

                // Send the download requests to the sequential queue channel
                serviceScope.launch {
                    for (i in 0 until size) {
                        val url = urls[i]
                        val filePath = filePaths[i]
                        val fileName = fileNames[i]
                        val mediaType = mediaTypes?.getOrNull(i) ?: "image"
                        val hasVideo = mediaType == "video"

                        downloadChannel.send(
                            DownloadRequest(
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
                        )
                    }
                }
            } else {
                // Fallback to single download parameters if list extra is not present
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
                    val isBatch = postId != null && totalImages > 1
                    val notificationId = if (isBatch) postId.hashCode() else (postId?.hashCode() ?: fileName.hashCode())

                    val displayUsername = username ?: "unknown"
                    val startMsg = if (isBatch) {
                        "Downloading $totalImages files from @$displayUsername"
                    } else {
                        "Downloading media from @$displayUsername"
                    }
                    notificationManager.showDownloadStartedPopup("Download Started", startMsg)
                    
                    val currentTaskCount = activeTasks.incrementAndGet()
                    val isFirstTask = currentTaskCount == 1

                    if (isBatch) {
                        batchProgressMap.putIfAbsent(postId!!, Pair(AtomicInteger(0), AtomicInteger(0)))
                        batchCompletedFilesMap.putIfAbsent(postId, CopyOnWriteArrayList())
                        updateProgressInDb(postId, username, "0/$totalImages")
                    } else {
                        updateProgressInDb(postId ?: fileName, username, "0/1")
                    }

                    if (isFirstTask) {
                        val notification = if (isBatch) {
                            val progress = batchProgressMap[postId]
                            val finished = progress?.first?.get() ?: 0
                            val remaining = totalImages - finished
                            val subText = when {
                                remaining <= 0 -> "All files downloaded"
                                remaining == 1 -> "$remaining file remaining"
                                else -> "$remaining files remaining"
                            }
                            NotificationCompat.Builder(this, VedInstaNotificationManager.CHANNEL_ID_SILENT)
                                .setSmallIcon(android.R.drawable.stat_sys_download)
                                .setContentTitle("VedInsta · Downloading")
                                .setContentText("Downloading... ($finished/$totalImages files)")
                                .setSubText(subText)
                                .setProgress(totalImages, finished, false)
                                .setPriority(NotificationCompat.PRIORITY_LOW)
                                .setOngoing(true)
                                .setOnlyAlertOnce(true)
                                .build()
                        } else {
                            createForegroundNotification(fileName)
                        }

                        try {
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
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start foreground service", e)
                        }
                    }

                    serviceScope.launch {
                        downloadChannel.send(
                            DownloadRequest(
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
                        )
                    }
                } else {
                    if (activeTasks.get() == 0) {
                        stopSelfResult(startId)
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun createForegroundNotification(fileName: String): android.app.Notification {
        return NotificationCompat.Builder(this, VedInstaNotificationManager.CHANNEL_ID_SILENT)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("VedInsta · Downloading")
            .setContentText("Downloading... (0/1 files)")
            .setProgress(0, 0, true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

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
        var downloadSuccess = false
        var fileToClean: File? = null
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    handleDownloadFailure(postId, postUrl, totalImages, username, fileName, "HTTP ${response.code}", notificationId)
                    return
                }

                val body = response.body ?: throw IOException("Empty response body")

                val file = File(filePath)
                fileToClean = file
                file.parentFile?.mkdirs()

                val contentLength = body.contentLength()
                var currentTotalBytesRead = 0L

                body.byteStream().use { inputStream ->
                    FileOutputStream(file).use { outputStream ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int

                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            if (!currentCoroutineContext().isActive) {
                                throw CancellationException("Download cancelled by service scope")
                            }
                            outputStream.write(buffer, 0, bytesRead)
                            currentTotalBytesRead += bytesRead
                        }
                    }
                }

                if (contentLength > 0 && currentTotalBytesRead != contentLength) {
                    throw IOException("Size mismatch: Expected=$contentLength, Got=$currentTotalBytesRead")
                }
                downloadSuccess = true
            }

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

            handleDownloadSuccess(postId, totalImages, username, fileName, notificationId, filePath)

        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d(TAG, "Download of $fileName cancelled silently")
                if (!downloadSuccess) {
                    fileToClean?.let {
                        if (it.exists()) {
                            try {
                                it.delete()
                                Log.d(TAG, "Cleaned up cancelled incomplete file: ${it.absolutePath}")
                            } catch (ex: Exception) {
                                Log.e(TAG, "Failed to delete incomplete file on cancel", ex)
                            }
                        }
                    }
                }
                return
            }
            Log.e(TAG, "Failed to download $fileName", e)
            if (!downloadSuccess) {
                fileToClean?.let {
                    if (it.exists()) {
                        try {
                            it.delete()
                            Log.d(TAG, "Cleaned up failed/incomplete file: ${it.absolutePath}")
                        } catch (ex: Exception) {
                            Log.e(TAG, "Failed to delete incomplete file", ex)
                        }
                    }
                }
            }
            handleDownloadFailure(postId, postUrl, totalImages, username, fileName, e.message ?: "Unknown error", notificationId)
        }
    }

    private fun handleDownloadSuccess(
        postId: String?,
        totalImages: Int,
        username: String?,
        fileName: String,
        notificationId: Int,
        filePath: String
    ) {
        val displayUsername = username ?: "unknown"
        if (postId != null && totalImages > 1) {
            val progress = batchProgressMap[postId]
            if (progress != null) {
                progress.second.incrementAndGet() // increment success count
                val finished = progress.first.incrementAndGet() // increment finished count
                batchCompletedFilesMap[postId]?.add(filePath)

                notificationManager.showDownloadProgress(
                    notificationId = postId.hashCode(),
                    completedFiles = finished,
                    totalFiles = totalImages
                )

                if (finished >= totalImages) {
                    val successes = progress.second.get()
                    val title = "Download Completed"
                    val msg = "Saved $successes/$totalImages files from @$displayUsername"
                    
                    notificationManager.showDownloadCompleted(
                        notificationId = postId.hashCode(),
                        title = title,
                        message = msg
                    )

                    serviceScope.launch {
                        // BUG FIX: NonCancellable prevents JobCancellationException when
                        // stopSelf() fires (cancelling serviceScope) before this DB insert
                        // completes. Terminal writes MUST survive service shutdown.
                        withContext(NonCancellable) {
                            try {
                                val completedPaths = batchCompletedFilesMap[postId]
                                val thumb = completedPaths?.firstOrNull() ?: filePath
                                val safeThumb = if (thumb.isNotEmpty()) {
                                    com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(applicationContext, thumb)
                                } else {
                                    ""
                                }
                                val db = com.devson.vedinsta.database.AppDatabase.getDatabase(applicationContext)
                                val pUrl = db.downloadedPostDao().getPostById(postId)?.postUrl

                                notificationManager.addCustomNotification(
                                    title = title,
                                    message = msg,
                                    type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED,
                                    priority = com.devson.vedinsta.database.NotificationPriority.NORMAL,
                                    postId = postId,
                                    postUrl = pUrl,
                                    thumbnailPath = thumb
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to insert success batch notification in DB", e)
                            }
                        }
                    }

                    removeProgressFromDb(postId)
                    batchProgressMap.remove(postId)
                    batchCompletedFilesMap.remove(postId)
                } else {
                    updateProgressInDb(postId, username, "$finished/$totalImages")
                }
            }
        } else {
            val isVideo = filePath.endsWith(".mp4", ignoreCase = true) || filePath.endsWith(".mov", ignoreCase = true) || filePath.endsWith(".avi", ignoreCase = true)
            val mediaTypeWord = if (isVideo) "reel" else "post"
            val title = "Download Completed"
            val msg = "Saved $mediaTypeWord from @$displayUsername"
            notificationManager.showDownloadProgress(notificationId = notificationId, completedFiles = 1, totalFiles = 1)
            updateProgressInDb(postId ?: fileName, username, "1/1")
            notificationManager.showDownloadCompleted(notificationId = notificationId, title = title, message = msg)

            serviceScope.launch {
                // BUG FIX: NonCancellable prevents JobCancellationException when
                // stopSelf() fires (cancelling serviceScope) before this DB insert
                // completes. Terminal writes MUST survive service shutdown.
                withContext(NonCancellable) {
                    try {
                        val db = com.devson.vedinsta.database.AppDatabase.getDatabase(applicationContext)
                        val pUrl = db.downloadedPostDao().getPostById(postId ?: fileName)?.postUrl

                        notificationManager.addCustomNotification(
                            title = title,
                            message = msg,
                            type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED,
                            priority = com.devson.vedinsta.database.NotificationPriority.NORMAL,
                            postId = postId ?: fileName,
                            postUrl = pUrl,
                            thumbnailPath = com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(applicationContext, filePath)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert success single notification in DB", e)
                    }
                }
            }
            removeProgressFromDb(postId ?: fileName)
        }
    }

    private fun handleDownloadFailure(
        postId: String?,
        postUrl: String?,
        totalImages: Int,
        username: String?,
        fileName: String,
        errorMessage: String,
        notificationId: Int
    ) {
        val displayUsername = username ?: "unknown"
        if (postId != null && totalImages > 1) {
            val progress = batchProgressMap[postId]
            if (progress != null) {
                val finished = progress.first.incrementAndGet() // increment finished count

                notificationManager.showDownloadProgress(
                    notificationId = postId.hashCode(),
                    completedFiles = finished,
                    totalFiles = totalImages
                )

                if (finished >= totalImages) {
                    val successes = progress.second.get()
                    if (successes > 0) {
                        val title = "Download Completed with errors"
                        val msg = "Saved $successes/$totalImages files from @$displayUsername"
                        
                        notificationManager.showDownloadCompleted(
                            notificationId = postId.hashCode(),
                            title = title,
                            message = msg
                        )

                        serviceScope.launch {
                            // BUG FIX: NonCancellable prevents JobCancellationException when
                            // stopSelf() races ahead of this DB insert on batch partial-success.
                            withContext(NonCancellable) {
                                try {
                                    notificationManager.addCustomNotification(
                                        title = title,
                                        message = msg,
                                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED,
                                        priority = com.devson.vedinsta.database.NotificationPriority.LOW,
                                        postId = postId,
                                        postUrl = postUrl
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to insert partial success notification in DB", e)
                                }
                            }
                        }
                    } else {
                        val title = "Download Failed"
                        val msg = "Could not download files from @$displayUsername"
                        
                        notificationManager.showDownloadError(
                            notificationId = postId.hashCode(),
                            fileName = "Batch Download Failed",
                            error = msg
                        )

                        serviceScope.launch {
                            // BUG FIX: NonCancellable prevents JobCancellationException when
                            // stopSelf() races ahead of this DB insert on batch full-failure.
                            withContext(NonCancellable) {
                                try {
                                    notificationManager.addCustomNotification(
                                        title = title,
                                        message = msg,
                                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                                        priority = com.devson.vedinsta.database.NotificationPriority.HIGH,
                                        postId = postId,
                                        postUrl = postUrl
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to insert batch error notification in DB", e)
                                }
                            }
                        }
                    }
                    removeProgressFromDb(postId)
                    batchProgressMap.remove(postId)
                    batchCompletedFilesMap.remove(postId)
                } else {
                    updateProgressInDb(postId, username, "$finished/$totalImages")
                }
            }
        } else {
            val title = "Download Failed"
            val msg = "Error downloading $fileName: $errorMessage"
            
            notificationManager.showDownloadError(
                notificationId = notificationId,
                fileName = fileName,
                error = errorMessage
            )

            serviceScope.launch {
                // BUG FIX: NonCancellable prevents JobCancellationException when
                // stopSelf() races ahead of this single-download error DB insert.
                withContext(NonCancellable) {
                    try {
                        notificationManager.addCustomNotification(
                            title = title,
                            message = msg,
                            type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                            priority = com.devson.vedinsta.database.NotificationPriority.HIGH,
                            postId = postId ?: fileName,
                            postUrl = postUrl
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to insert single error notification in DB", e)
                    }
                }
            }
            removeProgressFromDb(postId ?: fileName)
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

                    val rawThumbnailPath = when {
                        newFirstImagePath != null -> newFirstImagePath
                        currentThumbnailValid -> existingPost.thumbnailPath
                        newFirstVideoPath != null -> newFirstVideoPath
                        else -> ""
                    }
                    val updatedThumbnailPath = if (rawThumbnailPath.isNotEmpty()) {
                        com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(applicationContext, rawThumbnailPath)
                    } else {
                        ""
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
                    val rawThumbnailPath = firstImagePath ?: firstVideoPath ?: ""
                    val thumbnailPath = if (rawThumbnailPath.isNotEmpty()) {
                        com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(applicationContext, rawThumbnailPath)
                    } else {
                        ""
                    }

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
        } catch (e: Exception) {
            Log.e(TAG, "DATABASE SAVE/UPDATE ERROR for key $postId", e)
        }
    }

    private fun updateProgressInDb(postId: String?, username: String?, progressText: String) {
        if (postId == null) return
        serviceScope.launch {
            notificationManager.updateProgressInDb(postId, username, progressText)
        }
    }

    private fun removeProgressFromDb(postId: String?) {
        if (postId == null) return
        serviceScope.launch {
            notificationManager.removeProgressFromDb(postId)
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