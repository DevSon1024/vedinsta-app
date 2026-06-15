package com.devson.vedinsta.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.devson.vedinsta.model.ImageCard
import com.devson.vedinsta.viewmodel.SettingsViewModel
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.extractor.InstagramNativeExtractor
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.*
import java.io.File
import org.json.JSONObject

class SharedLinkProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager
    private lateinit var settingsViewModel: SettingsViewModel

    companion object {
        private const val TAG = "SharedLinkService"
        const val EXTRA_INSTAGRAM_URL = "instagram_url"
        const val ACTION_DOWNLOAD_ALL = "com.devson.vedinsta.ACTION_DOWNLOAD_ALL"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = VedInstaNotificationManager.getInstance(this)
        settingsViewModel = SettingsViewModel(application)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_DOWNLOAD_ALL -> {
                    val url = it.getStringExtra(EXTRA_INSTAGRAM_URL)
                    handleDownloadAll(url, startId)
                }
                else -> {
                    val url = it.getStringExtra(EXTRA_INSTAGRAM_URL)
                    if (url != null) {
                        processSharedLink(url, startId)
                    } else {
                        stopSelfResult(startId)
                    }
                }
            }
        } ?: stopSelfResult(startId)

        return START_NOT_STICKY
    }

    private fun processSharedLink(url: String, startId: Int) {
        serviceScope.launch {
            var shouldStopSelf = true
            try {
                notificationManager.showLinkProcessing()

                val postDataJson = fetchPostData(url)
                if (postDataJson == null) {
                    notificationManager.showLinkError("Failed to fetch content. Private or removed.")
                    try {
                        notificationManager.addCustomNotification(
                            title = "Link Processing Failed",
                            message = "Failed to fetch content for shared link. Private or removed.",
                            type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                            priority = com.devson.vedinsta.database.NotificationPriority.HIGH
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error adding fail notification to DB", ex)
                    }
                    return@launch
                }

                val postData = JSONObject(postDataJson)
                val mediaArray = postData.optJSONArray("media")
                val mediaCount = mediaArray?.length() ?: 0
                val username = postData.optString("username", "unknown")
                val caption = postData.optString("caption", "")

                // Cache data so Activity/App can use it without re-fetching
                (application as VedInstaApplication).cachePostMetadata(url, username, caption)

                notificationManager.cancelLinkProcessingNotification()

                // Check media count and settings
                if (mediaCount == 1) {
                    // SINGLE POST: Always Auto-download silently
                    val mediaObj = mediaArray!!.getJSONObject(0)
                    val downloadUrl = mediaObj.getString("url")
                    val mediaType = mediaObj.getString("type")
                    val itemId = mediaObj.optString("story_item_id", postData.optString("shortcode", url))

                    (application as VedInstaApplication).enqueueSingleDownload(
                        applicationContext,
                        downloadUrl,
                        mediaType,
                        username,
                        itemId,
                        caption
                    )

                    /*
                    try {
                        notificationManager.addCustomNotification(
                            title = "Shared Link Auto-Download",
                            message = "Starting download of single post from @$username",
                            type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_STARTED,
                            priority = com.devson.vedinsta.database.NotificationPriority.LOW
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error adding start notification to DB", ex)
                    }
                    */
                } else if (mediaCount > 1) {
                    // Removed/Commented out to prevent cluttering the NotificationScreen
                    /*
                    try {
                        notificationManager.addCustomNotification(
                            title = "Shared Link Processed",
                            message = "Found $mediaCount files in shared link from @$username",
                            type = com.devson.vedinsta.database.NotificationType.SYSTEM_INFO,
                            priority = com.devson.vedinsta.database.NotificationPriority.LOW
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error adding process notification to DB", ex)
                    }
                    */

                    // MULTIPLE CONTENT: Check Settings
                    when (settingsViewModel.defaultLinkAction) {
                        SettingsViewModel.ACTION_DOWNLOAD_ALL -> {
                            handleDownloadAll(url, startId)
                            shouldStopSelf = false
                        }
                        SettingsViewModel.ACTION_OPEN_SELECTION -> {
                            notificationManager.showMultipleContentOptions(url, mediaCount, autoOpenSelection = true)
                        }
                        else -> {
                            notificationManager.showMultipleContentOptions(url, mediaCount)
                        }
                    }
                } else {
                    notificationManager.showLinkError("No media found in link.")
                    try {
                        notificationManager.addCustomNotification(
                            title = "Shared Link Error",
                            message = "No downloadable media found in shared link.",
                            type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                            priority = com.devson.vedinsta.database.NotificationPriority.NORMAL
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error adding no-media notification to DB", ex)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing link", e)
                notificationManager.showLinkError("Error: ${e.message}")
            } finally {
                // Only stop if we are not delegating to download all via setting
                if (shouldStopSelf) {
                    stopSelfResult(startId)
                }
            }
        }
    }

    private fun handleDownloadAll(url: String?, startId: Int) {
        if (url.isNullOrEmpty()) {
            if (startId != -1) stopSelfResult(startId)
            return
        }

        serviceScope.launch {
            try {
                notificationManager.cancelMultipleContentNotification()

                val postDataJson = fetchPostData(url)

                if (postDataJson == null) {
                    notificationManager.showLinkError("Failed to fetch post data")
                    return@launch
                }

                val postData = JSONObject(postDataJson)
                val mediaArray = postData.optJSONArray("media")
                val username = postData.optString("username", "unknown")
                val caption = postData.optString("caption", "")
                val shortcode = postData.optString("shortcode", "batch_${System.currentTimeMillis()}")

                if (mediaArray == null || mediaArray.length() == 0) return@launch

                val itemsToDownload = mutableListOf<ImageCard>()
                for (i in 0 until mediaArray.length()) {
                    val obj = mediaArray.getJSONObject(i)
                    itemsToDownload.add(
                        ImageCard(
                            url = obj.getString("url"),
                            type = obj.getString("type"),
                            username = username
                        )
                    )
                }

                // Enqueue batch download
                (application as VedInstaApplication).enqueueMultipleDownloads(
                    applicationContext,
                    itemsToDownload,
                    shortcode,
                    caption
                )

                // Removed/Commented out to prevent cluttering the NotificationScreen
                /*
                try {
                    notificationManager.addCustomNotification(
                        title = "Shared Link Batch Download",
                        message = "Starting batch download of ${itemsToDownload.size} files from @$username",
                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_STARTED,
                        priority = com.devson.vedinsta.database.NotificationPriority.NORMAL
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Error adding batch start notification to DB", ex)
                }
                */

            } catch (e: Exception) {
                Log.e(TAG, "Error in download all", e)
                notificationManager.showLinkError("Batch download failed")
                try {
                    notificationManager.addCustomNotification(
                        title = "Download Failed",
                        message = "Batch download of shared post failed: ${e.message}",
                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                        priority = com.devson.vedinsta.database.NotificationPriority.HIGH
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Error adding batch fail notification to DB", ex)
                }
            } finally {
                if (startId != -1) stopSelfResult(startId)
            }
        }
    }

    private suspend fun fetchPostData(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cookieFile = File(filesDir, "instagram_cookies.txt").absolutePath
                val resultString = InstagramNativeExtractor.getMediaUrls(
                    url = url,
                    cookieFilePath = cookieFile,
                    userAgent = settingsViewModel.customUserAgent,
                    appId = settingsViewModel.customIgAppId,
                    timeoutSeconds = settingsViewModel.networkTimeoutSeconds
                )

                if (resultString.isNotEmpty()) {
                    val jsonResult = JSONObject(resultString)
                    if (jsonResult.optString("status") == "success") {
                        return@withContext resultString
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching post data", e)
                null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}