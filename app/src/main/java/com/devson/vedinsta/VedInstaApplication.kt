package com.devson.vedinsta

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer // Standard Observer import
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
import java.util.concurrent.CopyOnWriteArrayList

class VedInstaApplication : Application() {

    lateinit var settingsManager: SettingsManager
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val TAG = "VedInstaApplication"
        const val UNIQUE_DOWNLOAD_WORK_NAME = "vedInstaDownloadWork"
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        WorkManager.getInstance(this).pruneWork()
    }

    fun enqueueMultipleDownloads(
        context: Context,
        filesToDownload: List<ImageCard>,
        postId: String?
    ): List<UUID> {
        Log.d(TAG, "=== enqueueMultipleDownloads called ===")
        Log.d(TAG, "Files to enqueue: ${filesToDownload.size}")
        Log.d(TAG, "PostId: $postId")

        val workRequests = mutableListOf<OneTimeWorkRequest>() // Explicit type
        val workManager = WorkManager.getInstance(context.applicationContext) // Use application context
        val requestIds = mutableListOf<UUID>()
        val groupTag = postId ?: UUID.randomUUID().toString()

        filesToDownload.forEachIndexed { index, media ->
            try {
                val fileName = PostMediaManager.generateUniqueFileName(media.username, media.type)
                // Use var for targetFilePath as it might change based on SAF check
                var targetFilePath: String
                val targetDirectoryUriString = if (media.type.lowercase() == "video") {
                    settingsManager.videoDirectoryUri
                } else {
                    settingsManager.imageDirectoryUri
                }

                if (targetDirectoryUriString != null) {
                    val cacheDir = File(context.cacheDir, "downloads")
                    cacheDir.mkdirs()
                    targetFilePath = File(cacheDir, fileName).absolutePath
                    Log.w(TAG, "SAF chosen but using internal cache path for Worker: $targetFilePath. Manual move needed.")
                    // TODO: Implement post-download move to SAF URI
                } else {
                    val targetDir = if (media.type.lowercase() == "video") {
                        PostMediaManager.getVideoDirectory()
                    } else {
                        PostMediaManager.getImageDirectory()
                    }
                    // Ensure targetDir exists before creating the File path
                    targetDir.mkdirs()
                    targetFilePath = File(targetDir, fileName).absolutePath
                }

                Log.d(TAG, "Media $index: URL=${media.url}, Path=$targetFilePath, Name=$fileName")

                val inputData = workDataOf(
                    EnhancedDownloadManager.KEY_MEDIA_URL to media.url,
                    EnhancedDownloadManager.KEY_FILE_PATH to targetFilePath,
                    EnhancedDownloadManager.KEY_FILE_NAME to fileName,
                    EnhancedDownloadManager.KEY_POST_ID to postId, // Pass original postId if available
                    EnhancedDownloadManager.KEY_MEDIA_TYPE to media.type
                )

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val downloadWorkRequest = OneTimeWorkRequestBuilder<EnhancedDownloadManager>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag(groupTag) // Tag all work in the batch with the same groupTag
                    .build()

                workRequests.add(downloadWorkRequest) // Add to list
                requestIds.add(downloadWorkRequest.id)
                Log.d(TAG, "Prepared WorkRequest ${downloadWorkRequest.id} for $fileName (Tag: $groupTag)")

            } catch (e: Exception) {
                Log.e(TAG, "Error creating WorkRequest for media $index", e)
            }
        }

        if (workRequests.isNotEmpty()) {
            workManager.beginUniqueWork(
                groupTag,
                ExistingWorkPolicy.APPEND_OR_REPLACE, // Or KEEP, REPLACE
                workRequests // Pass the whole list here
            ).enqueue() // Call enqueue() at the end

            Log.d(TAG, "Enqueued ${workRequests.size} requests with tag: $groupTag")

            observeWorkProgressByTag(context.applicationContext, groupTag, requestIds.size, postUrl = filesToDownload.firstOrNull()?.url ?: "")
        }

        Log.d(TAG, "=== enqueueMultipleDownloads completed ===")
        return requestIds
    }


    fun enqueueSingleDownload(
        context: Context,
        mediaUrl: String,
        mediaType: String,
        username: String,
        postIdOrKey: String // Can be postId or a generated key/URL
    ): UUID? {
        Log.d(TAG, "=== enqueueSingleDownload called ===")
        Log.d(TAG, "Media URL: $mediaUrl, Type: $mediaType, User: $username, Key: $postIdOrKey")
        val workManager = WorkManager.getInstance(context.applicationContext) // Use application context
        try {
            val fileName = PostMediaManager.generateUniqueFileName(username, mediaType)
            var targetFilePath: String // Use var
            val targetDirectoryUriString = if (mediaType.lowercase() == "video") settingsManager.videoDirectoryUri else settingsManager.imageDirectoryUri

            if (targetDirectoryUriString != null) {
                val cacheDir = File(context.cacheDir, "downloads"); cacheDir.mkdirs()
                targetFilePath = File(cacheDir, fileName).absolutePath
                Log.w(TAG, "SAF chosen but using internal cache path: $targetFilePath.")
                // TODO: Implement post-download move
            } else {
                val targetDir = if (mediaType.lowercase() == "video") PostMediaManager.getVideoDirectory() else PostMediaManager.getImageDirectory()
                targetDir.mkdirs()
                targetFilePath = File(targetDir, fileName).absolutePath
            }

            Log.d(TAG, "Single Download: Path=$targetFilePath, Name=$fileName")
            val inputData = workDataOf(
                EnhancedDownloadManager.KEY_MEDIA_URL to mediaUrl,
                EnhancedDownloadManager.KEY_FILE_PATH to targetFilePath,
                EnhancedDownloadManager.KEY_FILE_NAME to fileName,
                EnhancedDownloadManager.KEY_POST_ID to postIdOrKey,
                EnhancedDownloadManager.KEY_MEDIA_TYPE to mediaType
            )
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val downloadWorkRequest = OneTimeWorkRequestBuilder<EnhancedDownloadManager>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(postIdOrKey)
                .build()

            workManager.enqueueUniqueWork(postIdOrKey, ExistingWorkPolicy.KEEP, downloadWorkRequest)
            Log.d(TAG, "Enqueued single WorkRequest ${downloadWorkRequest.id} for key $postIdOrKey")
            observeWorkProgressById(context.applicationContext, downloadWorkRequest.id, postIdOrKey, postUrl = mediaUrl, username = username, mediaType = mediaType)
            Log.d(TAG, "=== enqueueSingleDownload completed ===")
            return downloadWorkRequest.id
        } catch (e: Exception) {
            Log.e(TAG, "Error enqueuing single download", e)
            VedInstaNotificationManager.getInstance(context).showDownloadError("File", e.message ?: "Enqueue failed")
            return null
        }
    }


    // --- Observation Logic ---
    private val observedWorkIds = ConcurrentHashMap.newKeySet<UUID>()
    private val groupCompletedFiles = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val singleCompletedFiles = ConcurrentHashMap<UUID, String>()
    private val tagObservers = ConcurrentHashMap<String, Observer<List<WorkInfo>>>()
    private val idObservers = ConcurrentHashMap<UUID, Observer<WorkInfo>>()


    private fun observeWorkProgressById(
        context: Context,
        workId: UUID,
        tag: String,
        postUrl: String,
        username: String,
        mediaType: String
    ) {
        if (!observedWorkIds.add(workId)) {
            Log.d(TAG, "Already observing Work ID: $workId")
            return
        }
        Log.d(TAG, "Observing single Work ID: $workId for tag: $tag")

        val workManager = WorkManager.getInstance(context)
        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(workId)

        val observer = Observer<WorkInfo> { workInfo ->
            if (workInfo == null) return@Observer

            // *** Get file path from OUTPUT data on SUCCESS ***
            var filePath = if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                workInfo.outputData.getString(EnhancedDownloadManager.KEY_FILE_PATH)
            } else {
                // Otherwise, try input data (stored in progress) as fallback
                workInfo.progress.getString(EnhancedDownloadManager.KEY_FILE_PATH)
            }
            val fileName = workInfo.progress.getString(EnhancedDownloadManager.KEY_FILE_NAME) ?: "file"


            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    Log.i(TAG, "Work $workId ($fileName) SUCCEEDED.")
                    if (filePath != null) {
                        singleCompletedFiles[workId] = filePath
                        handleSingleCompletion(context, tag, postUrl, filePath, username, mediaType)
                        // TODO: Handle SAF move
                    } else {
                        // Log error if path is missing even on success
                        Log.e(TAG, "Work $workId ($fileName) succeeded BUT outputData missing KEY_FILE_PATH!")
                        handleSingleFailure(context, fileName) // Treat as failure
                    }
                    cleanupObserver(workId, workInfoLiveData)
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Log.w(TAG, "Work $workId ($fileName) ${workInfo.state}.")
                    handleSingleFailure(context, fileName)
                    cleanupObserver(workId, workInfoLiveData)
                }
                WorkInfo.State.RUNNING -> { /* Progress handled by worker notification */ }
                else -> { /* ENQUEUED, BLOCKED */ }
            }
        }

        idObservers[workId] = observer

        applicationScope.launch {
            workInfoLiveData.observeForever(observer)
        }
    }


    private fun observeWorkProgressByTag(
        context: Context,
        groupTag: String,
        expectedCount: Int,
        postUrl: String
    ) {
        if (tagObservers.containsKey(groupTag)) {
            Log.d(TAG, "Already observing group tag: $groupTag")
            return
        }
        Log.d(TAG, "Observing group tag: $groupTag (expecting $expectedCount)")

        val workManager = WorkManager.getInstance(context)
        val workInfosLiveData = workManager.getWorkInfosByTagLiveData(groupTag)

        groupCompletedFiles.putIfAbsent(groupTag, CopyOnWriteArrayList())

        val observer = Observer<List<WorkInfo>> { workInfos -> // Lambda syntax is fine
            if (workInfos.isNullOrEmpty()) return@Observer
            val completionHandled = !tagObservers.containsKey(groupTag)
            if (completionHandled) {
                Log.d(TAG, "Completion appears handled for tag $groupTag (observer removed), ignoring update.")
                return@Observer
            }

            val finishedCount = workInfos.count { it.state.isFinished }
            val succeededCount = workInfos.count { it.state == WorkInfo.State.SUCCEEDED }
            val runningCount = workInfos.count { it.state == WorkInfo.State.RUNNING }

            Log.d(TAG, "Tag '$groupTag' update: Total=${workInfos.size}, Finished=$finishedCount, Succeeded=$succeededCount, Running=$runningCount")

            // Collect successful file paths ONCE per work ID when SUCCEEDED
            workInfos.filter { it.state == WorkInfo.State.SUCCEEDED }.forEach { workInfo ->
                // *** Get file path from OUTPUT data on SUCCESS ***
                val filePath = workInfo.outputData.getString(EnhancedDownloadManager.KEY_FILE_PATH)
                // Fallback to input data if needed (less ideal)
                    ?: workInfo.progress.getString(EnhancedDownloadManager.KEY_FILE_PATH)

                if (filePath != null) {
                    groupCompletedFiles[groupTag]?.addIfAbsent(filePath)
                } else {
                    Log.e(TAG, "Work ${workInfo.id} (Tag: $groupTag) succeeded BUT outputData missing KEY_FILE_PATH!")
                }
            }

            if (finishedCount >= expectedCount) {
                Log.i(TAG, "All $expectedCount works for tag '$groupTag' have finished.")
                // Cleanup observer FIRST
                val observerToRemove = tagObservers[groupTag]
                if (observerToRemove != null) {
                    cleanupTagObserver(groupTag, workInfosLiveData, observerToRemove)
                }

                // THEN get completed files and process
                val completedFiles = groupCompletedFiles.remove(groupTag)?.toList() ?: emptyList()

                if (completedFiles.isNotEmpty()) {
                    val firstSuccessInfo = workInfos.find { it.state == WorkInfo.State.SUCCEEDED }
                    // Get username from Input data (stored in progress) of first success
                    val username = firstSuccessInfo?.progress?.getString(EnhancedDownloadManager.KEY_POST_ID)?.substringBefore('_') ?: "unknown"
                    val hasVideo = completedFiles.any { it.endsWith(".mp4", ignoreCase = true) }

                    applicationScope.launch(Dispatchers.IO) {
                        saveDownloadedPostToDb(context, groupTag, postUrl, completedFiles, hasVideo, username, null)
                        // TODO: Handle SAF move
                        scanFiles(context, completedFiles)
                        withContext(Dispatchers.Main) { Toast.makeText(context, "Downloaded ${completedFiles.size} / $expectedCount files.", Toast.LENGTH_LONG).show() }
                        VedInstaNotificationManager.getInstance(context).showDownloadCompleted(username, completedFiles.size)
                    }
                } else {
                    Log.w(TAG, "All work finished for tag '$groupTag', but no files succeeded.")
                    applicationScope.launch(Dispatchers.Main) { Toast.makeText(context, "Download failed for post.", Toast.LENGTH_LONG).show() }
                    // Get username from Input data of *any* workInfo for notification fallback
                    val username = workInfos.firstOrNull()?.progress?.getString(EnhancedDownloadManager.KEY_POST_ID)?.substringBefore('_') ?: groupTag
                    VedInstaNotificationManager.getInstance(context).showDownloadError("Post $username", "Download failed")
                }
                // groupCompletedFiles entry already removed above
            } else {
                Log.d(TAG, "Tag '$groupTag': Waiting for ${expectedCount - finishedCount} more jobs.")
            }
        } // End lambda observer

        tagObservers[groupTag] = observer // Store before observing

        applicationScope.launch {
            workInfosLiveData.observeForever(observer)
        }
    }


    // --- Other methods remain largely the same ---
    // Handle completion for single file downloads
    private fun handleSingleCompletion(
        context: Context,
        tag: String, // postId or generated key
        postUrl: String,
        filePath: String,
        username: String,
        mediaType: String
    ) {
        applicationScope.launch(Dispatchers.IO) {
            val hasVideo = mediaType.lowercase() == "video"
            saveDownloadedPostToDb(
                context = context,
                postId = tag,
                postUrl = postUrl,
                downloadedFiles = listOf(filePath),
                hasVideo = hasVideo,
                username = username,
                caption = null // TODO: Get caption if needed
            )
            scanFiles(context, listOf(filePath))
        }
    }

    // Handle failure for single file downloads
    private fun handleSingleFailure(context: Context, fileName: String) {
        applicationScope.launch(Dispatchers.Main) {
            Toast.makeText(context, "Download failed for $fileName", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to safely get input data
    private fun inputDataForWork(workManager: WorkManager, workId: UUID): Data? {
        return try {
            workManager.getWorkInfoById(workId).get()?.progress
        } catch (e: Exception) {
            Log.e(TAG, "Error getting WorkInfo synchronously for $workId", e)
            null
        }
    }

    private suspend fun saveDownloadedPostToDb(
        context: Context, postId: String, postUrl: String, downloadedFiles: List<String>,
        hasVideo: Boolean, username: String, caption: String?
    ) {
        if (downloadedFiles.isEmpty()) {
            Log.w(TAG, "Attempted to save post $postId with empty downloaded files list.")
            return
        }
        Log.d(TAG, "Saving/Updating post to database: PostId=$postId, Files=${downloadedFiles.size}")
        try {
            val db = AppDatabase.getDatabase(context.applicationContext)
            val existingPost = db.downloadedPostDao().getPostById(postId)

            if (existingPost != null) {
                val updatedPaths = (existingPost.mediaPaths + downloadedFiles).distinct()
                val updatedPost = existingPost.copy(
                    mediaPaths = updatedPaths,
                    totalImages = updatedPaths.size,
                    downloadDate = System.currentTimeMillis(),
                    thumbnailPath = if (existingPost.thumbnailPath.isBlank() || !File(existingPost.thumbnailPath).exists() || existingPost.thumbnailPath.endsWith(".mp4")) downloadedFiles.first() else existingPost.thumbnailPath,
                    username = if (existingPost.username == "unknown" || existingPost.username == "downloading...") username else existingPost.username,
                    caption = existingPost.caption ?: caption,
                    hasVideo = existingPost.hasVideo || hasVideo
                )
                db.downloadedPostDao().insertOrReplace(updatedPost)
                Log.d(TAG, "Updated existing post $postId with ${updatedPaths.size} media paths.")
            } else {
                val downloadedPost = DownloadedPost(
                    postId = postId, postUrl = postUrl,
                    thumbnailPath = downloadedFiles.first(),
                    totalImages = downloadedFiles.size,
                    downloadDate = System.currentTimeMillis(),
                    hasVideo = hasVideo, username = username, caption = caption,
                    mediaPaths = downloadedFiles
                )
                db.downloadedPostDao().insert(downloadedPost)
                Log.d(TAG, "Inserted new post $postId with ${downloadedFiles.size} media paths.")
                val insertedPost = db.downloadedPostDao().getPostById(postId)
                if (insertedPost != null && insertedPost.mediaPaths.isEmpty()) {
                    Log.w(TAG, "Post $postId insert IGNORED, updating media paths manually.")
                    db.downloadedPostDao().updateMediaPaths(postId, downloadedFiles)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "DATABASE SAVE ERROR for postId $postId", e)
        }
    }

    private fun scanFiles(context: Context, filePaths: List<String>) {
        if (filePaths.isEmpty()) return
        Log.d(TAG, "Requesting media scan for ${filePaths.size} files.")
        try {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                filePaths.toTypedArray(),
                null,
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "MediaScannerConnection failed", e)
        }
    }

    // Cleanup for ID observers
    private fun cleanupObserver(workId: UUID, liveData: LiveData<WorkInfo>) {
        applicationScope.launch {
            idObservers.remove(workId)?.let { observer ->
                liveData.removeObserver(observer)
                observedWorkIds.remove(workId)
                Log.d(TAG, "Removed ID observer for Work ID: $workId")
            }
        }
    }

    // Cleanup for Tag observers
    private fun cleanupTagObserver(tag: String, liveData: LiveData<List<WorkInfo>>, observerToRemove: Observer<List<WorkInfo>>) {
        applicationScope.launch {
            if (tagObservers.remove(tag, observerToRemove)) {
                liveData.removeObserver(observerToRemove)
                Log.d(TAG, "Removed tag observer for: $tag")
            } else {
                Log.w(TAG, "Attempted to remove tag observer for $tag, but it was not found.")
            }
        }
    }

    // --- Deprecated/Placeholder Download Methods ---
    suspend fun downloadFiles(context: Context, filesToDownload: List<ImageCard>, postId: String?): List<String> {
        Log.e(TAG, "Deprecated downloadFiles called! Use enqueueMultipleDownloads.")
        return emptyList()
    }

    suspend fun downloadSingleFile(
        context: Context, mediaUrl: String, mediaType: String,
        username: String, postId: String?
    ): List<String> {
        Log.e(TAG, "Deprecated downloadSingleFile called! Use enqueueSingleDownload.")
        val key = postId ?: UUID.randomUUID().toString()
        enqueueSingleDownload(context, mediaUrl, mediaType, username, key)
        return emptyList()
    }
}