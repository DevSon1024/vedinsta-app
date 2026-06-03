package com.devson.vedinsta

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.*
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.request.CachePolicy

import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.database.PostMediaManager
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.os.Environment
import kotlinx.coroutines.withContext
import java.util.UUID
import com.devson.vedinsta.model.MediaItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject
import android.content.Intent
import com.devson.vedinsta.extractor.InstagramNativeExtractor
import com.devson.vedinsta.model.ImageCard
import com.devson.vedinsta.service.DownloadService
import com.devson.vedinsta.viewmodel.SettingsViewModel

class VedInstaApplication : Application(), ImageLoaderFactory {

    lateinit var settingsViewModel: SettingsViewModel
    // Use SupervisorJob to prevent failure of one child from cancelling others
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) // Use immediate for faster UI updates from observers

    // Cache for temporarily holding username and caption during download initiation
    private val postMetadataCache = ConcurrentHashMap<String, Pair<String?, String?>>()


    companion object {
        private const val TAG = "VedInstaApplication"
        const val UNIQUE_DOWNLOAD_WORK_NAME = "vedInstaDownloadWork" // Can be used for specific single tasks if needed

        fun clearAppCache(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val downloadCacheDir = File(context.cacheDir, "download_cache")
                    if (downloadCacheDir.exists()) {
                        deleteRecursive(downloadCacheDir, excludeSelf = false)
                        Log.d(TAG, "Download cache directory cleared successfully.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to clear download cache", e)
                }
            }
        }

        private fun deleteRecursive(file: File, excludeSelf: Boolean = false) {
            if (file.isDirectory) {
                val children = file.listFiles()
                if (children != null) {
                    for (child in children) {
                        deleteRecursive(child, excludeSelf = false)
                    }
                }
            }
            if (!excludeSelf) {
                val deleted = file.delete()
                if (!deleted) {
                    Log.w(TAG, "Failed to delete file/dir: ${file.absolutePath}")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsViewModel = SettingsViewModel(this)

        WorkManager.getInstance(this).pruneWork()
        clearAppCache(this)
    }

    override fun newImageLoader(): ImageLoader {
        val videoFrameDiskCache = DiskCache.Builder()
            .directory(File(cacheDir, "coil_video_frames"))
            .maxSizeBytes(20L * 1024 * 1024) // 20 MB cap – prevents storage bloat
            .build()

        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .diskCache(videoFrameDiskCache) // Persist decoded frames to disk
            .diskCachePolicy(CachePolicy.ENABLED) // Allow read & write to disk cache
            .memoryCachePolicy(CachePolicy.ENABLED) // Keep in-memory cache as well
            .crossfade(true)
            .build()
    }

    // Caching Username and Caption
    fun cachePostMetadata(key: String, username: String?, caption: String?) {
        if (!username.isNullOrBlank() || !caption.isNullOrBlank()) {
            postMetadataCache[key] = Pair(username ?: "unknown", caption) // Store "unknown" if username is null/blank
            Log.d(TAG, "Cached metadata for key $key: User=$username, Caption=${caption?.take(30)}...")
        } else {
            Log.w(TAG, "Attempted to cache null/blank metadata for key $key")
        }
    }

    private fun getCachedMetadata(key: String): Pair<String?, String?> {
        // Return cached data or default Pair("unknown", null) if not found
        return postMetadataCache[key] ?: Pair("unknown", null)
    }

    private fun removeCachedMetadata(key: String) {
        if (postMetadataCache.containsKey(key)) {
            postMetadataCache.remove(key)
            Log.d(TAG, "Removed cached metadata for key $key")
        }
    }
    /**
     * Fetches metadata for an Instagram post URL via the native Instagram extractor.
     *
     * PERF FIX: Converted to a suspend function.  All extraction execution
     * and JSON parsing are dispatched to [Dispatchers.IO] via
     * [withContext], ensuring the main thread is never blocked.  This eliminates
     * the primary source of heavy GC churn and "application may be doing too much
     * work on its main thread" errors that were visible in Logcat.
     */
    suspend fun getPostInfo(url: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            Log.d("VedInstaApp", "Getting post info for: $url")

            val cookieFile = File(filesDir, "instagram_cookies.txt")
            if (!cookieFile.exists()) {
                return@withContext JSONObject().apply { put("status", "login_required") }
            }

            // Call the native extractor
            val resultString = InstagramNativeExtractor.getMediaUrls(url, cookieFile.absolutePath)

            Log.d("VedInstaApp", "Native result: $resultString")

            val jsonResult = JSONObject(resultString)
            val status = jsonResult.optString("status", "error")

            when (status) {
                "success" -> {
                    // Post is accessible
                    jsonResult
                }
                "private" -> {
                    // Post is private
                    Log.w("VedInstaApp", "Post is private")
                    JSONObject().apply {
                        put("is_private", true)
                        put("media_count", 0)
                    }
                }
                "login_required" -> {
                    // Login required
                    Log.w("VedInstaApp", "Login required for post")
                    JSONObject().apply {
                        put("is_private", true)
                        put("media_count", 0)
                    }
                }
                "not_found" -> {
                    // Post not found
                    Log.w("VedInstaApp", "Post not found")
                    null
                }
                else -> {
                    // Other error
                    Log.e("VedInstaApp", "Error status: $status")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("VedInstaApp", "Error getting post info", e)
            null
        }
    }

    suspend fun downloadSelectedMedia(
        mediaItems: List<MediaItem>,
        postUrl: String,
        username: String,
        caption: String?,
        postId: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                val totalItems = mediaItems.size
                val urlsList = ArrayList<String>()
                val filePathsList = ArrayList<String>()
                val fileNamesList = ArrayList<String>()
                val mediaTypesList = ArrayList<String>()

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val vedInstaDir = File(downloadDir, "VedInsta")
                vedInstaDir.mkdirs()

                for ((index, mediaItem) in mediaItems.withIndex()) {
                    val timestamp = System.currentTimeMillis()
                    val extension = if (mediaItem.type == "video") "mp4" else "jpg"
                    val fileName = "${username}_${timestamp + index}.$extension"
                    val filePath = File(vedInstaDir, fileName).absolutePath

                    urlsList.add(mediaItem.url)
                    filePathsList.add(filePath)
                    fileNamesList.add(fileName)
                    mediaTypesList.add(mediaItem.type)
                }

                Log.d("VedInstaApp", "Queueing batch download of $totalItems items in a single Service Intent")

                val downloadIntent = Intent(this@VedInstaApplication, DownloadService::class.java).apply {
                    putStringArrayListExtra("download_urls_list", urlsList)
                    putStringArrayListExtra("file_paths_list", filePathsList)
                    putStringArrayListExtra("file_names_list", fileNamesList)
                    putStringArrayListExtra("media_types_list", mediaTypesList)
                    putExtra(DownloadService.EXTRA_POST_ID, postId)
                    putExtra(DownloadService.EXTRA_POST_URL, postUrl)
                    putExtra(DownloadService.EXTRA_USERNAME, username)
                    putExtra(DownloadService.EXTRA_CAPTION, caption)
                    putExtra(DownloadService.EXTRA_TOTAL_IMAGES, totalItems)
                }
                startService(downloadIntent)

            } catch (e: Exception) {
                Log.e("VedInstaApp", "Error in batch download queueing", e)
            }
        }
    }

    suspend fun downloadPostFromUrl(url: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("VedInstaApp", "Starting download for: $url")

                val cookieFile = File(filesDir, "instagram_cookies.txt")
                if (!cookieFile.exists()) {
                    throw Exception("Please log in to Instagram first")
                }
                val resultString = InstagramNativeExtractor.getMediaUrls(url, cookieFile.absolutePath)

                val postData = JSONObject(resultString)
                val status = postData.optString("status", "error")

                if (status != "success") {
                    val message = postData.optString("message", "Unknown error")
                    throw Exception(message)
                }

                val mediaArray = postData.optJSONArray("media")
                if (mediaArray == null || mediaArray.length() == 0) {
                    throw Exception("No media found in post")
                }

                val totalItems = mediaArray.length()
                val username = postData.optString("username", "unknown")
                val shortcode = postData.optString("shortcode", "unknown")
                val caption = postData.optString("caption", "")

                val urlsList = ArrayList<String>()
                val filePathsList = ArrayList<String>()
                val fileNamesList = ArrayList<String>()
                val mediaTypesList = ArrayList<String>()

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val vedInstaDir = File(downloadDir, "VedInsta")
                vedInstaDir.mkdirs()

                for (i in 0 until totalItems) {
                    val mediaObj = mediaArray.getJSONObject(i)
                    val downloadUrl = mediaObj.getString("url")
                    val mediaType = mediaObj.optString("type", "image")

                    val timestamp = System.currentTimeMillis()
                    val extension = if (mediaType == "video") "mp4" else "jpg"
                    val fileName = "${username}_${timestamp + i}.$extension"
                    val filePath = File(vedInstaDir, fileName).absolutePath

                    urlsList.add(downloadUrl)
                    filePathsList.add(filePath)
                    fileNamesList.add(fileName)
                    mediaTypesList.add(mediaType)
                }

                Log.d("VedInstaApp", "Queueing URL batch download of $totalItems items in a single Service Intent")

                val downloadIntent = Intent(this@VedInstaApplication, DownloadService::class.java).apply {
                    putStringArrayListExtra("download_urls_list", urlsList)
                    putStringArrayListExtra("file_paths_list", filePathsList)
                    putStringArrayListExtra("file_names_list", fileNamesList)
                    putStringArrayListExtra("media_types_list", mediaTypesList)
                    putExtra(DownloadService.EXTRA_POST_ID, shortcode)
                    putExtra(DownloadService.EXTRA_POST_URL, url)
                    putExtra(DownloadService.EXTRA_USERNAME, username)
                    putExtra(DownloadService.EXTRA_CAPTION, caption)
                    putExtra(DownloadService.EXTRA_TOTAL_IMAGES, totalItems)
                }
                startService(downloadIntent)

            } catch (e: Exception) {
                Log.e("VedInstaApp", "Error downloading post", e)
                val notificationManager = VedInstaNotificationManager.getInstance(this@VedInstaApplication)
                notificationManager.cancelBatchDownloadNotification()
                notificationManager.showLinkError("Download failed: ${e.message}")
            }
        }
    }

    private fun extractUsernameFromUrl(url: String): String {
        return try {
            val regex = Regex("instagram\\.com/([^/]+)")
            regex.find(url)?.groupValues?.get(1) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
    private fun queueDownload(
        url: String,
        fileName: String,
        postId: String?,
        postUrl: String?,
        username: String?,
        caption: String?,
        totalImages: Int,
        hasVideo: Boolean
    ) {
        try {
            // Use the SAME download path as in-app downloads
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val vedInstaDir = File(downloadDir, "VedInsta")
            vedInstaDir.mkdirs()
            val filePath = File(vedInstaDir, fileName).absolutePath

            Log.d("VedInstaApp", "Queueing download: $fileName to $filePath")

            // Use your existing DownloadService to ensure consistency
            val downloadIntent = Intent(this, DownloadService::class.java).apply {
                putExtra(DownloadService.EXTRA_DOWNLOAD_URL, url)
                putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
                putExtra(DownloadService.EXTRA_FILE_PATH, filePath)
                putExtra(DownloadService.EXTRA_POST_ID, postId)
                putExtra(DownloadService.EXTRA_POST_URL, postUrl)
                putExtra(DownloadService.EXTRA_USERNAME, username)
                putExtra(DownloadService.EXTRA_CAPTION, caption)
                putExtra(DownloadService.EXTRA_TOTAL_IMAGES, totalImages)
                putExtra(DownloadService.EXTRA_HAS_VIDEO, hasVideo)
            }
            startService(downloadIntent)

        } catch (e: Exception) {
            Log.e("VedInstaApp", "Error queueing download", e)
        }
    }

    private fun getDownloadPath(fileName: String): String {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val vedInstaDir = File(downloadDir, "VedInsta")
        vedInstaDir.mkdirs()
        return File(vedInstaDir, fileName).absolutePath
    }

    fun enqueueMultipleDownloads(
        context: Context,
        filesToDownload: List<ImageCard>,
        postId: String?,
        postCaption: String?
    ): List<UUID> {
        Log.i(TAG, "Enqueueing multiple downloads (${filesToDownload.size}) for Post ID/Key: $postId")

        if (filesToDownload.isEmpty()) {
            Log.w(TAG, "enqueueMultipleDownloads called with empty list.")
            return emptyList()
        }

        val workRequests = mutableListOf<OneTimeWorkRequest>()
        val workManager = WorkManager.getInstance(context.applicationContext) // Use application context
        val requestIds = mutableListOf<UUID>()
        // Use postId if available, otherwise generate a unique tag for this batch
        val groupTag = postId ?: "batch_${UUID.randomUUID()}"

        // Cache metadata using the groupTag as the key
        val firstUsername = filesToDownload.firstOrNull()?.username // Already passed from DownloadActivity
        cachePostMetadata(groupTag, firstUsername, postCaption)

        filesToDownload.forEachIndexed { index, media ->
            try {
                // Generate filename based on cached username or fallback
                val finalUsername = firstUsername ?: media.username ?: "unknown"
                val fileName = PostMediaManager.generateUniqueFileName(finalUsername, media.type)

                // Determine target directory (prioritize SAF URI if set)
                val targetDirectoryUriString = if (media.type.lowercase() == "video") {
                    settingsViewModel.videoDirectoryUri
                } else {
                    settingsViewModel.imageDirectoryUri
                }

                // Determine the actual file path for the worker (might be cache)
                val (workerFilePath, requiresManualMove) = determineWorkerFilePath(
                    context, fileName, media.type, targetDirectoryUriString
                )

                Log.d(TAG, "Media $index: URL=${media.url}, Type=${media.type}, WorkerPath=$workerFilePath, RequiresMove=$requiresManualMove")

                val inputData = workDataOf(
                    EnhancedDownloadManager.KEY_MEDIA_URL to media.url,
                    EnhancedDownloadManager.KEY_FILE_PATH to workerFilePath, // Path worker will write to
                    EnhancedDownloadManager.KEY_FILE_NAME to fileName,
                    EnhancedDownloadManager.KEY_POST_ID to groupTag, // Use groupTag for all items in batch
                    EnhancedDownloadManager.KEY_MEDIA_TYPE to media.type,
                    "is_batch" to true
                    // Add flag indicating if manual move to SAF is needed after download
                    // "requires_manual_move" to requiresManualMove, // TODO: Implement worker reading this
                    // "target_saf_uri" to targetDirectoryUriString // TODO: Implement worker reading this
                )

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val downloadWorkRequest = OneTimeWorkRequestBuilder<EnhancedDownloadManager>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag(groupTag) // Tag all work in the batch
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS) // Add backoff policy
                    .build()

                workRequests.add(downloadWorkRequest)
                requestIds.add(downloadWorkRequest.id)
                Log.d(TAG, "Prepared WorkRequest ${downloadWorkRequest.id} for $fileName (Tag: $groupTag)")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating WorkRequest for media $index (URL: ${media.url})", e)
                // Optionally show an immediate error notification/toast for this specific item
            }
        }

        if (workRequests.isNotEmpty()) {
            // Use APPEND_OR_REPLACE: If work with this tag exists, append new requests. If it's finished, replace.
            // Consider KEEP if you never want to re-download automatically.
            workManager.beginUniqueWork(
                groupTag, // Use unique work name based on tag
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                workRequests // Enqueue the whole list
            ).enqueue()

            Log.i(TAG, "Enqueued ${workRequests.size} download requests with tag: $groupTag")

            // Start observing the progress/completion of the batch using the tag
            observeWorkProgressByTag(context.applicationContext, groupTag, workRequests.size, filesToDownload.first().url)
        } else {
            Log.w(TAG, "No valid WorkRequests were created for tag: $groupTag")
            // Clean up cache if nothing was enqueued
            removeCachedMetadata(groupTag)
        }

        return requestIds
    }


    fun enqueueSingleDownload(
        context: Context,
        mediaUrl: String,
        mediaType: String,
        username: String?, // Can be null initially
        postIdOrKey: String, // Can be postId or a generated key/URL
        postCaption: String? // Pass the fetched caption
    ): UUID? {
        Log.i(TAG, "Enqueueing single download for Key: $postIdOrKey")

        val workManager = WorkManager.getInstance(context.applicationContext)

        // Cache metadata before enqueuing
        cachePostMetadata(postIdOrKey, username, postCaption)

        try {
            val finalUsername = username ?: "unknown"
            val fileName = PostMediaManager.generateUniqueFileName(finalUsername, mediaType)

            // Determine target directory and worker path
            val targetDirectoryUriString = if (mediaType.lowercase() == "video") {
                settingsViewModel.videoDirectoryUri
            } else {
                settingsViewModel.imageDirectoryUri
            }
            val (workerFilePath, requiresManualMove) = determineWorkerFilePath(
                context, fileName, mediaType, targetDirectoryUriString
            )

            Log.d(TAG, "Single Download: URL=$mediaUrl, Type=$mediaType, WorkerPath=$workerFilePath, RequiresMove=$requiresManualMove")

            val inputData = workDataOf(
                EnhancedDownloadManager.KEY_MEDIA_URL to mediaUrl,
                EnhancedDownloadManager.KEY_FILE_PATH to workerFilePath, // Path worker writes to
                EnhancedDownloadManager.KEY_FILE_NAME to fileName,
                EnhancedDownloadManager.KEY_POST_ID to postIdOrKey, // Use the key consistently
                EnhancedDownloadManager.KEY_MEDIA_TYPE to mediaType,
                "is_batch" to false
                // TODO: Add requiresManualMove and target_saf_uri flags if implementing SAF move
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val downloadWorkRequest = OneTimeWorkRequestBuilder<EnhancedDownloadManager>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(postIdOrKey) // Tag with the unique key
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, java.util.concurrent.TimeUnit.SECONDS) // Add backoff
                .build()

            // Use enqueueUniqueWork with KEEP policy: If a download for this key is already pending/running, do nothing.
            workManager.enqueueUniqueWork(postIdOrKey, ExistingWorkPolicy.KEEP, downloadWorkRequest)

            Log.i(TAG, "Enqueued single WorkRequest ${downloadWorkRequest.id} for key $postIdOrKey (Policy: KEEP)")

            // Start observing the progress/completion using the Work ID
            observeWorkProgressById(context.applicationContext, downloadWorkRequest.id, postIdOrKey, mediaUrl)

            return downloadWorkRequest.id

        } catch (e: Exception) {
            Log.e(TAG, "Error enqueuing single download for key $postIdOrKey", e)
            // Show error notification/toast
            VedInstaNotificationManager.getInstance(context).showDownloadError(postIdOrKey, e.message ?: "Enqueue failed")
            // Clean cache on enqueue failure
            removeCachedMetadata(postIdOrKey)
            return null
        }
    }

    // Helper to determine where the worker should save the file
    private fun determineWorkerFilePath(
        context: Context,
        fileName: String,
        mediaType: String,
        targetDirectoryUriString: String?
    ): Pair<String, Boolean> {
        return if (targetDirectoryUriString != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // SAF URI is selected AND device supports scoped storage properly
            // Worker cannot write directly to SAF URI. Save to internal cache first.
            val cacheDir = File(context.cacheDir, "download_cache").apply { mkdirs() }
            val cachePath = File(cacheDir, fileName).absolutePath
            Log.w(TAG, "SAF directory selected. Worker will use cache path: $cachePath. Manual move required post-download.")
            Pair(cachePath, true) // Path, requires manual move = true
        } else {
            // No SAF URI or older Android version, use legacy public storage
            val targetDir = if (mediaType.lowercase() == "video") {
                PostMediaManager.getVideoDirectory()
            } else {
                PostMediaManager.getImageDirectory()
            }
            targetDir.mkdirs() // Ensure directory exists
            val publicPath = File(targetDir, fileName).absolutePath
            Log.d(TAG, "Using public storage path: $publicPath")
            Pair(publicPath, false) // Path, requires manual move = false
        }
    }


    // --- Observation Logic ---
    private val observedWorkIds = ConcurrentHashMap.newKeySet<UUID>() // Tracks IDs actively being observed
    private val observedTags = ConcurrentHashMap.newKeySet<String>() // Tracks tags actively being observed
    // Use ConcurrentHashMap for groupCompletedFiles for better thread safety if accessed from multiple observers concurrently
    private val groupCompletedFiles = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>() // Stores successful file paths per group tag

    private fun observeWorkProgressById(
        context: Context,
        workId: UUID,
        tag: String, // postId or key used for caching/DB
        postUrl: String // Original URL for DB entry
    ) {
        // Prevent adding multiple observers for the same work ID
        if (!observedWorkIds.add(workId)) {
            Log.d(TAG, "Already observing Work ID: $workId (Tag: $tag)")
            return
        }
        Log.i(TAG, "Starting Flow collection for single Work ID: $workId (Tag: $tag)")

        val workManager = WorkManager.getInstance(context)

        applicationScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo == null) {
                    Log.w(TAG, "Flow for $workId received null WorkInfo. Cleaning up.")
                    observedWorkIds.remove(workId)
                    handleSingleFailure(context, "Unknown File (null WorkInfo)", tag, workId.hashCode() + 1000) // Treat as failure
                    throw kotlinx.coroutines.CancellationException("Work Finished")
                }

                // Get file path from OUTPUT data only on SUCCEEDED state
                val filePath = if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    workInfo.outputData.getString(EnhancedDownloadManager.KEY_FILE_PATH)
                } else {
                    null // Path is only guaranteed in output data on success
                }
                val fileName = workInfo.progress.getString(EnhancedDownloadManager.KEY_FILE_NAME)
                    ?: workInfo.tags.firstOrNull() ?: "file" // Fallback
                val mediaType = workInfo.progress.getString(EnhancedDownloadManager.KEY_MEDIA_TYPE)
                    ?: workInfo.tags.firstOrNull() ?: "image" // Fallback

                val notificationManager = VedInstaNotificationManager.getInstance(context)
                val cachedMetadata = getCachedMetadata(tag)
                val displayUsername = cachedMetadata.first ?: "unknown"
                val targetNotificationId = workId.hashCode() + 1000

                when (workInfo.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        if (filePath != null) {
                            Log.i(TAG, "Observer: Work $workId ($fileName) SUCCEEDED. Path: $filePath")
                            handleSingleCompletion(context, tag, postUrl, filePath, mediaType, targetNotificationId)
                        } else {
                            Log.e(TAG, "Observer: Work $workId ($fileName) SUCCEEDED but outputData missing KEY_FILE_PATH!")
                            handleSingleFailure(context, fileName, tag, targetNotificationId) // Treat as failure if path is missing
                        }
                        observedWorkIds.remove(workId)
                        throw kotlinx.coroutines.CancellationException("Work Finished")
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        Log.w(TAG, "Observer: Work $workId ($fileName) ${workInfo.state}.")
                        handleSingleFailure(context, fileName, tag, targetNotificationId)
                        observedWorkIds.remove(workId)
                        throw kotlinx.coroutines.CancellationException("Work Finished")
                    }
                    WorkInfo.State.RUNNING -> {
                        val progressVal = workInfo.progress.getInt("Progress", -1)
                        val progressText = if (progressVal >= 0) "$progressVal%" else "Downloading..."
                        withContext(Dispatchers.IO) {
                            notificationManager.updateProgressInDb(postId = tag, username = displayUsername, progressText = progressText)
                        }
                    }
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        Log.d(TAG, "Observer: Work $workId ($fileName) ${workInfo.state}.")
                    }
                }
            }
        }
    }

    fun downloadSingleMedia(url: String, type: String, username: String, index: Int) {
        try {
            val timestamp = System.currentTimeMillis()
            val extension = if (type == "video") "mp4" else "jpg"
            val fileName = "${username}_${timestamp + index}.$extension"

            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val vedInstaDir = File(downloadDir, "VedInsta")
            vedInstaDir.mkdirs()
            val filePath = File(vedInstaDir, fileName).absolutePath

            Log.d("VedInstaApp", "Downloading: $fileName to $filePath")

            // Start download service
            val downloadIntent = Intent(this, DownloadService::class.java).apply {
                putExtra(DownloadService.EXTRA_DOWNLOAD_URL, url)
                putExtra(DownloadService.EXTRA_FILE_NAME, fileName)
                putExtra(DownloadService.EXTRA_FILE_PATH, filePath)
                putExtra(DownloadService.EXTRA_USERNAME, username)
                putExtra(DownloadService.EXTRA_TOTAL_IMAGES, 1)
                putExtra(DownloadService.EXTRA_HAS_VIDEO, type == "video")
            }
            startService(downloadIntent)

        } catch (e: Exception) {
            Log.e("VedInstaApp", "Error downloading media", e)
        }
    }

    private fun observeWorkProgressByTag(
        context: Context,
        groupTag: String,
        expectedCount: Int,
        postUrl: String // Original URL for DB entry
    ) {
        // Prevent adding multiple observers for the same tag
        if (!observedTags.add(groupTag)) {
            Log.d(TAG, "Already observing group tag: $groupTag")
            return
        }
        Log.i(TAG, "Starting Flow collection for group tag: $groupTag (expecting $expectedCount files)")

        val workManager = WorkManager.getInstance(context)

        // Initialize completed files list for this tag if absent
        groupCompletedFiles.putIfAbsent(groupTag, CopyOnWriteArrayList()) // Use thread-safe list

        applicationScope.launch {
            workManager.getWorkInfosByTagFlow(groupTag).collect { workInfos ->
                if (workInfos.isNullOrEmpty()) {
                    Log.d(TAG, "Observer for tag '$groupTag' received null or empty list.")
                    return@collect
                }

                val finishedCount = workInfos.count { it.state.isFinished }
                val succeededCount = workInfos.count { it.state == WorkInfo.State.SUCCEEDED }
                val failedCount = workInfos.count { it.state == WorkInfo.State.FAILED }
                val cancelledCount = workInfos.count { it.state == WorkInfo.State.CANCELLED }
                val runningCount = workInfos.count { it.state == WorkInfo.State.RUNNING }

                Log.d(TAG, "Tag '$groupTag' update: Total=${workInfos.size}, Expected=$expectedCount, Finished=$finishedCount (S:$succeededCount F:$failedCount C:$cancelledCount), Running=$runningCount")

                // Collect successful file paths ONCE per work ID when it SUCCEEDS
                val currentCompletedFiles = groupCompletedFiles[groupTag]
                if (currentCompletedFiles != null) {
                    workInfos.forEach { workInfo ->
                        if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                            val filePath = workInfo.outputData.getString(EnhancedDownloadManager.KEY_FILE_PATH)
                            if (filePath != null) {
                                if (!currentCompletedFiles.contains(filePath)) {
                                    currentCompletedFiles.add(filePath)
                                    Log.d(TAG, "Observer: Recorded success for ${workInfo.id} (Tag: $groupTag), Path: $filePath")
                                }
                            } else {
                                Log.e(TAG, "Observer: Work ${workInfo.id} (Tag: $groupTag) SUCCEEDED but outputData missing KEY_FILE_PATH!")
                            }
                        }
                    }
                }

                val notificationManager = VedInstaNotificationManager.getInstance(context)

                // Check if ALL expected jobs are finished
                if (finishedCount >= expectedCount) {
                    Log.i(TAG, "All $expectedCount works for tag '$groupTag' have finished.")

                    // Process the results
                    val completedFilePaths = groupCompletedFiles.remove(groupTag)?.toList() ?: emptyList()
                    val (cachedUsername, cachedCaption) = getCachedMetadata(groupTag)
                    removeCachedMetadata(groupTag) // Clean cache AFTER processing

                    val finalUsername = cachedUsername ?: "unknown"

                    if (completedFilePaths.isNotEmpty()) {
                        Log.i(TAG, "Tag '$groupTag': Processing ${completedFilePaths.size} successfully downloaded files.")
                        val hasVideo = completedFilePaths.any { path ->
                            val extension = File(path).extension.lowercase()
                            extension in listOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
                        }

                        // Save to DB on IO thread
                        withContext(Dispatchers.IO) {
                            saveDownloadedPostToDb(context, groupTag, postUrl, completedFilePaths, hasVideo, finalUsername, cachedCaption)
                            scanFiles(context, completedFilePaths) // Scan files after saving
                            // Clear temporary cache
                            clearAppCache(context)
                            notificationManager.removeProgressFromDb(groupTag)

                            try {
                                val rawThumb = completedFilePaths.firstOrNull() ?: ""
                                val safeThumb = if (rawThumb.isNotEmpty()) {
                                    com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(context, rawThumb)
                                } else {
                                    ""
                                }
                                notificationManager.addCustomNotification(
                                    title = "Download Completed",
                                    message = "Saved ${completedFilePaths.size}/$expectedCount files from @$finalUsername",
                                    type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED,
                                    priority = com.devson.vedinsta.database.NotificationPriority.NORMAL,
                                    postId = groupTag,
                                    postUrl = postUrl,
                                    thumbnailPath = safeThumb
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to insert batch success notification to DB", e)
                            }

                            // Show summary notification/toast on Main thread
                            withContext(Dispatchers.Main) {
                                val message = "Downloaded ${completedFilePaths.size}/$expectedCount files for $finalUsername."
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                notificationManager.showDownloadCompleted(
                                    notificationId = groupTag.hashCode(),
                                    title = "Download Completed",
                                    message = "Saved ${completedFilePaths.size}/$expectedCount files from @$finalUsername"
                                )
                            }
                        }
                    } else {
                        Log.w(TAG, "All work finished for tag '$groupTag', but no files succeeded.")
                        withContext(Dispatchers.IO) {
                            notificationManager.removeProgressFromDb(groupTag)
                            try {
                                notificationManager.addCustomNotification(
                                    title = "Download Failed",
                                    message = "Could not download files from @$finalUsername",
                                    type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                                    priority = com.devson.vedinsta.database.NotificationPriority.HIGH,
                                    postId = groupTag,
                                    postUrl = postUrl
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to insert batch failure notification to DB", e)
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Download failed for post $finalUsername.", Toast.LENGTH_LONG).show()
                                notificationManager.showDownloadError(
                                    notificationId = groupTag.hashCode(),
                                    fileName = "Post $finalUsername",
                                    error = "All downloads failed"
                                )
                            }
                        }
                    }

                    observedTags.remove(groupTag)
                    // Break the flow collection
                    throw kotlinx.coroutines.CancellationException("Work Finished")
                } else {
                    Log.d(TAG, "Tag '$groupTag': Waiting for ${expectedCount - finishedCount} more job(s) to finish.")
                    val cachedMetadata = getCachedMetadata(groupTag)
                    val displayUsername = cachedMetadata.first ?: "unknown"
                    withContext(Dispatchers.IO) {
                        notificationManager.updateProgressInDb(postId = groupTag, username = displayUsername, progressText = "$finishedCount/$expectedCount")
                    }
                    notificationManager.showBatchDownloadProgress(
                        notificationId = groupTag.hashCode(),
                        current = finishedCount,
                        total = expectedCount,
                        title = "Downloading from @$displayUsername"
                    )
                }
            }
        }
    }


    // Handle completion for single file downloads (called by ID observer)
    private fun handleSingleCompletion(
        context: Context,
        tag: String, // postId or generated key
        postUrl: String,
        filePath: String,
        mediaType: String,
        notificationId: Int
    ) {
        val (cachedUsername, cachedCaption) = getCachedMetadata(tag)
        removeCachedMetadata(tag) // Clean cache after processing

        val finalUsername = cachedUsername ?: "unknown"
        val hasVideo = mediaType.lowercase() == "video"

        applicationScope.launch(Dispatchers.IO) {
            saveDownloadedPostToDb(
                context = context,
                postId = tag, // Use tag (which might be postId or URL/key)
                postUrl = postUrl,
                downloadedFiles = listOf(filePath),
                hasVideo = hasVideo,
                username = finalUsername,
                caption = cachedCaption
            )
            scanFiles(context, listOf(filePath))
            // Clear temporary cache
            clearAppCache(context)
            val notificationManager = VedInstaNotificationManager.getInstance(context)
            notificationManager.removeProgressFromDb(tag)

            val fileName = File(filePath).name
            val isVideo = filePath.endsWith(".mp4", ignoreCase = true) || filePath.endsWith(".mov", ignoreCase = true) || filePath.endsWith(".avi", ignoreCase = true)
            val mediaTypeWord = if (isVideo) "reel" else "post"
            try {
                notificationManager.addCustomNotification(
                    title = "Download Completed",
                    message = "Saved $mediaTypeWord from @$finalUsername",
                    type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED,
                    priority = com.devson.vedinsta.database.NotificationPriority.NORMAL,
                    postId = tag,
                    postUrl = postUrl,
                    thumbnailPath = com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(context, filePath)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert single success notification to DB", e)
            }

            // Show completion feedback on Main thread
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download complete: $fileName", Toast.LENGTH_SHORT).show()
                notificationManager.showDownloadCompleted(
                    notificationId = notificationId,
                    title = "Download Completed",
                    message = "Saved $mediaTypeWord from @$finalUsername"
                )
            }
        }
    }

    // Handle failure for single file downloads (called by ID observer)
    private fun handleSingleFailure(context: Context, fileName: String, tag: String, notificationId: Int) {
        val (cachedUsername, _) = getCachedMetadata(tag)
        removeCachedMetadata(tag) // Clean cache on failure
        val finalUsername = cachedUsername ?: "unknown"
        applicationScope.launch(Dispatchers.IO) {
            val notificationManager = VedInstaNotificationManager.getInstance(context)
            notificationManager.removeProgressFromDb(tag)
            try {
                notificationManager.addCustomNotification(
                    title = "Download Failed",
                    message = "Error downloading $fileName from @$finalUsername",
                    type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                    priority = com.devson.vedinsta.database.NotificationPriority.HIGH,
                    postId = tag
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert single failure notification to DB", e)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: $fileName", Toast.LENGTH_SHORT).show()
                notificationManager.showDownloadError(notificationId = notificationId, fileName = fileName, error = "Download failed or cancelled")
            }
        }
    }

    private suspend fun saveDownloadedPostToDb(
        context: Context,
        postId: String, // This is the tag (postId or generated key)
        postUrl: String, // Original post URL
        downloadedFiles: List<String>, // List of successfully downloaded file paths
        hasVideo: Boolean, // Whether any videos were downloaded
        username: String, // Username from cache or default
        caption: String? // Caption from cache
    ) {
        if (downloadedFiles.isEmpty()) {
            Log.w(TAG, "Attempted to save post $postId with empty downloaded files list.")
            return
        }
        Log.i(TAG, "Saving/Updating post to DB: Key=$postId, Files=${downloadedFiles.size}, User=$username")
        try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val dao = db.downloadedPostDao()
            val existingPost = dao.getPostById(postId) // Check if post with this key exists

            if (existingPost != null) {
                // Post exists - UPDATE it
                Log.d(TAG, "Updating existing post in DB: $postId")

                // Merge new file paths with existing ones, ensuring uniqueness and preserving order
                val updatedPaths = (existingPost.mediaPaths + downloadedFiles).distinct()

                // Choose thumbnail: prefer new first image, else keep old, else new first video, else blank
                val newFirstImagePath = downloadedFiles.firstOrNull { !isVideoFile(it) }
                val newFirstVideoPath = downloadedFiles.firstOrNull { isVideoFile(it) }
                val currentThumbnailValid = !existingPost.thumbnailPath.isBlank() && File(existingPost.thumbnailPath).exists()

                val rawThumbnailPath = when {
                    newFirstImagePath != null -> newFirstImagePath // Prioritize new image
                    currentThumbnailValid -> existingPost.thumbnailPath // Keep existing valid one
                    newFirstVideoPath != null -> newFirstVideoPath // Use new video as last resort
                    else -> "" // Fallback to empty
                }
                val updatedThumbnailPath = if (rawThumbnailPath.isNotEmpty()) {
                    com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(context, rawThumbnailPath)
                } else {
                    ""
                }

                // Update username only if the existing one is a placeholder
                val updatedUsername = if (existingPost.username == "unknown" || existingPost.username == "downloading...") {
                    username
                } else {
                    existingPost.username // Keep existing non-placeholder username
                }

                // Update caption only if the existing one is null/blank
                val updatedCaption = existingPost.caption.takeIf { !it.isNullOrBlank() } ?: caption

                val updatedPost = existingPost.copy(
                    mediaPaths = updatedPaths,
                    totalImages = updatedPaths.size, // Correct count based on merged list
                    downloadDate = System.currentTimeMillis(), // Update timestamp to reflect latest download
                    thumbnailPath = updatedThumbnailPath,
                    username = updatedUsername,
                    caption = updatedCaption,
                    hasVideo = existingPost.hasVideo || hasVideo // Combine hasVideo flags
                )
                dao.insertOrReplace(updatedPost) // Use REPLACE to ensure update
                Log.d(TAG, "Updated existing post $postId. Total media paths now: ${updatedPaths.size}")

            } else {
                // Post doesn't exist - INSERT new entry
                Log.d(TAG, "Inserting new post into DB: $postId")

                // Choose thumbnail: prefer first image, else first video, else blank
                val firstImagePath = downloadedFiles.firstOrNull { !isVideoFile(it) }
                val firstVideoPath = downloadedFiles.firstOrNull { isVideoFile(it) }
                val rawThumbnailPath = firstImagePath ?: firstVideoPath ?: ""
                val thumbnailPath = if (rawThumbnailPath.isNotEmpty()) {
                    com.devson.vedinsta.ui.ThumbnailHelper.getSafeThumbnailPath(context, rawThumbnailPath)
                } else {
                    ""
                }

                val newPost = DownloadedPost(
                    postId = postId, // Use the provided key
                    postUrl = postUrl,
                    thumbnailPath = thumbnailPath,
                    totalImages = downloadedFiles.size,
                    downloadDate = System.currentTimeMillis(),
                    hasVideo = hasVideo,
                    username = username, // Use provided/cached username
                    caption = caption, // Use provided/cached caption
                    mediaPaths = downloadedFiles.distinct() // Ensure paths are unique
                )
                dao.insert(newPost) // Use simple insert for new post
                Log.d(TAG, "Inserted new post $postId with ${downloadedFiles.size} media paths.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "DATABASE SAVE/UPDATE ERROR for key $postId", e)
            // Consider showing an error to the user or logging more details
        }
    }

    // Helper to check if a file path points to a likely video file
    private fun isVideoFile(path: String): Boolean {
        if (path.isBlank()) return false
        return try {
            val extension = File(path).extension.lowercase()
            extension in listOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
        } catch (e: Exception) {
            false // Handle potential errors with File creation/extension access
        }
    }


    private fun scanFiles(context: Context, filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        Log.d(TAG, "Requesting media scan for ${filePaths.size} files.")
        try {
            // Convert to array for the API
            val pathsArray = filePaths.toTypedArray()
            // Scan files to make them visible in gallery apps
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                pathsArray,
                null // No specific MIME types needed, let scanner determine
            ) { path, uri ->
                Log.d(TAG, "Media Scanner finished for path: $path, URI: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaScannerConnection.scanFile failed", e)
        }
    }

}