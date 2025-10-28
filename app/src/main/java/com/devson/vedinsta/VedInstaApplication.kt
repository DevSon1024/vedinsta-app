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
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList // Keep this import

class VedInstaApplication : Application() {

    lateinit var settingsManager: SettingsManager
    // Use SupervisorJob to prevent failure of one child from cancelling others
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) // Use immediate for faster UI updates from observers

    // Cache for temporarily holding username and caption during download initiation
    private val postMetadataCache = ConcurrentHashMap<String, Pair<String?, String?>>() // Key: postId/tag, Value: Pair(username, caption)


    companion object {
        private const val TAG = "VedInstaApplication"
        const val UNIQUE_DOWNLOAD_WORK_NAME = "vedInstaDownloadWork" // Can be used for specific single tasks if needed
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        // Initialize Python interpreter if not already started
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        // Prune finished WorkManager jobs on app start to clean up
        WorkManager.getInstance(this).pruneWork()
    }

    // --- Caching Username and Caption ---
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


    fun enqueueMultipleDownloads(
        context: Context,
        filesToDownload: List<ImageCard>,
        postId: String?, // The actual Instagram post ID/shortcode, if available
        postCaption: String? // Pass the fetched caption
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
                    settingsManager.videoDirectoryUri
                } else {
                    settingsManager.imageDirectoryUri
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
                settingsManager.videoDirectoryUri
            } else {
                settingsManager.imageDirectoryUri
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
                EnhancedDownloadManager.KEY_MEDIA_TYPE to mediaType
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
    // Use ConcurrentHashMap for groupCompletedFiles for better thread safety if accessed from multiple observers concurrently
    private val groupCompletedFiles = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>() // Stores successful file paths per group tag
    private val tagObservers = ConcurrentHashMap<String, Observer<List<WorkInfo>>>() // Maps tag -> observer instance
    private val idObservers = ConcurrentHashMap<UUID, Observer<WorkInfo>>() // Maps workId -> observer instance


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
        Log.i(TAG, "Starting observer for single Work ID: $workId (Tag: $tag)")

        val workManager = WorkManager.getInstance(context)
        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(workId)

        // Create the observer instance
        val observer = Observer<WorkInfo> { workInfo ->
            if (workInfo == null) {
                Log.w(TAG, "Observer for $workId received null WorkInfo. Cleaning up.")
                cleanupObserver(workId, workInfoLiveData)
                handleSingleFailure(context, "Unknown File (null WorkInfo)", tag) // Treat as failure
                return@Observer
            }

            // Get file path from OUTPUT data only on SUCCEEDED state
            val filePath = if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                workInfo.outputData.getString(EnhancedDownloadManager.KEY_FILE_PATH)
            } else {
                null // Path is only guaranteed in output data on success
            }
            // **FIX**: Get filename/type from workInfo.progress or workInfo.inputData
            val fileName = workInfo.progress.getString(EnhancedDownloadManager.KEY_FILE_NAME)
                ?: workInfo.tags.firstOrNull() ?: "file" // Fallback
            val mediaType = workInfo.progress.getString(EnhancedDownloadManager.KEY_MEDIA_TYPE)
                ?: workInfo.tags.firstOrNull() ?: "image" // Fallback

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    if (filePath != null) {
                        Log.i(TAG, "Observer: Work $workId ($fileName) SUCCEEDED. Path: $filePath")
                        // TODO: Implement SAF move here if required
                        // val requiresMove = workInfo.inputData.getBoolean("requires_manual_move", false)
                        // val targetSafUri = workInfo.inputData.getString("target_saf_uri")
                        // if (requiresMove && targetSafUri != null) { moveFileToSaf(context, filePath, targetSafUri) } else { /* proceed */ }

                        handleSingleCompletion(context, tag, postUrl, filePath, mediaType)
                    } else {
                        Log.e(TAG, "Observer: Work $workId ($fileName) SUCCEEDED but outputData missing KEY_FILE_PATH!")
                        handleSingleFailure(context, fileName, tag) // Treat as failure if path is missing
                    }
                    cleanupObserver(workId, workInfoLiveData) // Clean up after terminal state
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Log.w(TAG, "Observer: Work $workId ($fileName) ${workInfo.state}.")
                    handleSingleFailure(context, fileName, tag)
                    cleanupObserver(workId, workInfoLiveData) // Clean up after terminal state
                }
                WorkInfo.State.RUNNING -> {
                    // Progress is primarily handled by the worker's foreground notification
                    // Log.d(TAG, "Observer: Work $workId ($fileName) RUNNING.")
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    Log.d(TAG, "Observer: Work $workId ($fileName) ${workInfo.state}.")
                }
            }
        } // End of observer lambda

        // Store the observer instance before starting observation
        idObservers[workId] = observer

        // Observe forever using the applicationScope.
        applicationScope.launch { // Launch on the main thread for LiveData observation
            workInfoLiveData.observeForever(observer)
        }
    }


    private fun observeWorkProgressByTag(
        context: Context,
        groupTag: String,
        expectedCount: Int,
        postUrl: String // Original URL for DB entry
    ) {
        // Prevent adding multiple observers for the same tag
        if (tagObservers.containsKey(groupTag)) {
            Log.d(TAG, "Already observing group tag: $groupTag")
            return
        }
        Log.i(TAG, "Starting observer for group tag: $groupTag (expecting $expectedCount files)")

        val workManager = WorkManager.getInstance(context)
        val workInfosLiveData = workManager.getWorkInfosByTagLiveData(groupTag)

        // Initialize completed files list for this tag if absent
        groupCompletedFiles.putIfAbsent(groupTag, CopyOnWriteArrayList()) // Use thread-safe list

        // Create the observer instance
        val observer = Observer<List<WorkInfo>> { workInfos ->
            if (workInfos.isNullOrEmpty()) {
                Log.d(TAG, "Observer for tag '$groupTag' received null or empty list.")
                return@Observer
            }

            // Check if observation should continue
            if (!tagObservers.containsKey(groupTag)) {
                Log.d(TAG, "Observer for tag '$groupTag' triggered after cleanup. Ignoring.")
                return@Observer
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
                            // **FIX**: Use standard contains check + add for thread safety with CopyOnWriteArrayList
                            if (!currentCompletedFiles.contains(filePath)) {
                                currentCompletedFiles.add(filePath)
                                Log.d(TAG, "Observer: Recorded success for ${workInfo.id} (Tag: $groupTag), Path: $filePath")
                                // TODO: Implement SAF move here if needed for group downloads
                            }
                        } else {
                            Log.e(TAG, "Observer: Work ${workInfo.id} (Tag: $groupTag) SUCCEEDED but outputData missing KEY_FILE_PATH!")
                        }
                    }
                }
            }


            // Check if ALL expected jobs are finished
            if (finishedCount >= expectedCount) {
                Log.i(TAG, "All $expectedCount works for tag '$groupTag' have finished.")

                // 1. Cleanup the observer FIRST
                val observerToRemove = tagObservers[groupTag]
                if (observerToRemove != null) {
                    cleanupTagObserver(groupTag, workInfosLiveData, observerToRemove)
                }

                // 2. Process the results
                val completedFilePaths = groupCompletedFiles.remove(groupTag)?.toList() ?: emptyList()
                val (cachedUsername, cachedCaption) = getCachedMetadata(groupTag)
                removeCachedMetadata(groupTag) // Clean cache AFTER processing

                val finalUsername = cachedUsername ?: "unknown"

                if (completedFilePaths.isNotEmpty()) {
                    Log.i(TAG, "Tag '$groupTag': Processing ${completedFilePaths.size} successfully downloaded files.")
                    val hasVideo = completedFilePaths.any { path ->
                        val extension = File(path).extension.lowercase()
                        extension == "mp4" || extension == "mov" || extension == "avi" || extension == "mkv" || extension == "webm"
                    }

                    // Save to DB on IO thread
                    applicationScope.launch(Dispatchers.IO) {
                        saveDownloadedPostToDb(context, groupTag, postUrl, completedFilePaths, hasVideo, finalUsername, cachedCaption)
                        scanFiles(context, completedFilePaths) // Scan files after saving

                        // Show summary notification/toast on Main thread
                        withContext(Dispatchers.Main) {
                            val message = "Downloaded ${completedFilePaths.size} / $expectedCount files for $finalUsername."
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            VedInstaNotificationManager.getInstance(context).showDownloadCompleted(finalUsername, completedFilePaths.size)
                        }
                    }
                } else {
                    Log.w(TAG, "All work finished for tag '$groupTag', but no files succeeded.")
                    applicationScope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Download failed for post $finalUsername.", Toast.LENGTH_LONG).show()
                        VedInstaNotificationManager.getInstance(context).showDownloadError("Post $finalUsername", "All downloads failed")
                    }
                }
            } else {
                Log.d(TAG, "Tag '$groupTag': Waiting for ${expectedCount - finishedCount} more job(s) to finish.")
            }
        } // End of observer lambda

        // Store the observer instance before starting observation
        tagObservers[groupTag] = observer

        // Observe forever using the applicationScope.
        applicationScope.launch {
            workInfosLiveData.observeForever(observer)
        }
    }


    // Handle completion for single file downloads (called by ID observer)
    private fun handleSingleCompletion(
        context: Context,
        tag: String, // postId or generated key
        postUrl: String,
        filePath: String,
        mediaType: String
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

            // Show completion feedback on Main thread
            withContext(Dispatchers.Main) {
                val fileName = File(filePath).name
                Toast.makeText(context, "Download complete: $fileName", Toast.LENGTH_SHORT).show()
                VedInstaNotificationManager.getInstance(context).showDownloadCompleted(finalUsername, 1)
            }
        }
    }

    // Handle failure for single file downloads (called by ID observer)
    private fun handleSingleFailure(context: Context, fileName: String, tag: String) {
        removeCachedMetadata(tag) // Clean cache on failure
        applicationScope.launch(Dispatchers.Main) {
            Toast.makeText(context, "Download failed: $fileName", Toast.LENGTH_SHORT).show()
            // Optionally show a more persistent error notification
            VedInstaNotificationManager.getInstance(context).showDownloadError(fileName, "Download failed or cancelled")
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

                val updatedThumbnailPath = when {
                    newFirstImagePath != null -> newFirstImagePath // Prioritize new image
                    currentThumbnailValid -> existingPost.thumbnailPath // Keep existing valid one
                    newFirstVideoPath != null -> newFirstVideoPath // Use new video as last resort
                    else -> "" // Fallback to empty
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
                val thumbnailPath = firstImagePath ?: firstVideoPath ?: ""

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

    // Cleanup for ID observers (must be called from Main thread or use post)
    private fun cleanupObserver(workId: UUID, liveData: LiveData<WorkInfo>) {
        applicationScope.launch { // Ensure running on the main thread
            idObservers.remove(workId)?.let { observer ->
                liveData.removeObserver(observer) // Remove the specific observer instance
                observedWorkIds.remove(workId) // Remove from tracking set
                Log.i(TAG, "Removed observer for Work ID: $workId")
            } ?: Log.w(TAG, "Attempted to cleanup observer for $workId, but it was already removed.")
        }
    }

    // Cleanup for Tag observers (must be called from Main thread or use post)
    private fun cleanupTagObserver(tag: String, liveData: LiveData<List<WorkInfo>>, observerToRemove: Observer<List<WorkInfo>>) {
        applicationScope.launch { // Ensure running on the main thread
            // Remove the observer ONLY if it's the correct instance we stored
            if (tagObservers.remove(tag, observerToRemove)) {
                liveData.removeObserver(observerToRemove)
                Log.i(TAG, "Removed observer for tag: $tag")
            } else {
                Log.w(TAG, "Attempted to cleanup observer for tag $tag, but it was already removed or mismatched.")
            }
        }
    }
}