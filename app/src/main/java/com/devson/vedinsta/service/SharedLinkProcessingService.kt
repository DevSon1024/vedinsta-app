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
import com.devson.vedinsta.repository.DownloadQuotaManager

class SharedLinkProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager
    private lateinit var settingsViewModel: SettingsViewModel

    companion object {
        private const val TAG = "SharedLinkService"
        const val EXTRA_INSTAGRAM_URL = "instagram_url"
        const val ACTION_DOWNLOAD_ALL = "com.devson.vedinsta.ACTION_DOWNLOAD_ALL"
        const val ACTION_ACTIVATE_SESSION_AND_RETRY = "com.devson.vedinsta.ACTION_ACTIVATE_SESSION_AND_RETRY"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
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
                ACTION_ACTIVATE_SESSION_AND_RETRY -> {
                    val url = it.getStringExtra(EXTRA_INSTAGRAM_URL)
                    val notificationId = it.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                    if (notificationId != -1) {
                        notificationManager.cancelDownloadNotification(notificationId)
                    }
                    if (!url.isNullOrEmpty()) {
                        val securePrefs = com.devson.vedinsta.repository.SecurePreferences(applicationContext)
                        securePrefs.setSessionActive(true)
                        processSharedLink(url, startId)
                    } else {
                        stopSelfResult(startId)
                    }
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

                val processedUrl = url

                val quotaManager = DownloadQuotaManager(applicationContext)
                val quotaStatus = quotaManager.checkQuota()
                if (quotaStatus is DownloadQuotaManager.QuotaStatus.Exceeded) {
                    val msg = "You have already used more than your set up downloading limit. Please wait for the limit to reset before downloading again."
                    notificationManager.showLinkError(msg)
                    try {
                        notificationManager.addCustomNotification(
                            title = "Download Limit Reached",
                            message = msg,
                            type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                            priority = com.devson.vedinsta.database.NotificationPriority.HIGH
                        )
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error adding quota fail notification to DB", ex)
                    }
                    return@launch
                }

                val postDataJson = fetchPostData(processedUrl)

                val postData = JSONObject(postDataJson)
                val mediaArray = postData.optJSONArray("media")
                val mediaCount = mediaArray?.length() ?: 0
                val username = postData.optString("username", "unknown")
                val caption = postData.optString("caption", "")

                // Cache data so Activity/App can use it without re-fetching
                (application as VedInstaApplication).cachePostMetadata(processedUrl, username, caption)

                notificationManager.cancelLinkProcessingNotification()

                // Check media count and settings
                if (mediaCount == 1) {
                    // SINGLE POST: Always Auto-download silently
                    val mediaObj = mediaArray!!.getJSONObject(0)
                    val downloadUrl = mediaObj.getString("url")
                    val mediaType = mediaObj.getString("type")
                    val itemId = mediaObj.optString("story_item_id", postData.optString("shortcode", processedUrl))

                    notificationManager.cancelLinkProcessingNotification()
                    (application as VedInstaApplication).enqueueSingleDownload(
                        applicationContext,
                        downloadUrl,
                        mediaType,
                        username,
                        itemId,
                        caption
                    )
                } else if (mediaCount > 1) {
                    // MULTIPLE CONTENT: Check Settings
                    when (settingsViewModel.defaultLinkAction) {
                        SettingsViewModel.ACTION_DOWNLOAD_ALL -> {
                            notificationManager.cancelLinkProcessingNotification()
                            handleDownloadAll(processedUrl, startId)
                            shouldStopSelf = false
                        }
                        SettingsViewModel.ACTION_OPEN_SELECTION -> {
                            notificationManager.showMultipleContentOptions(processedUrl, mediaCount, autoOpenSelection = true)
                        }
                        else -> {
                            notificationManager.showMultipleContentOptions(processedUrl, mediaCount)
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

            } catch (e: com.devson.vedinsta.extractor.AuthRequiredException) {
                Log.e(TAG, "AuthRequiredException processing link", e)
                val securePrefs = com.devson.vedinsta.repository.SecurePreferences(applicationContext)
                notificationManager.showPrivatePostNotification(url, securePrefs.hasValidSession())
                try {
                    notificationManager.addCustomNotification(
                        title = "Private Post Detected",
                        message = "Private or age-restricted content. Authentication required.",
                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                        priority = com.devson.vedinsta.database.NotificationPriority.HIGH
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Error adding auth fail notification to DB", ex)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing link", e)
                notificationManager.showLinkError("Error: ${e.message}")
                try {
                    notificationManager.addCustomNotification(
                        title = "Link Processing Failed",
                        message = "Failed to fetch content for shared link: ${e.message}",
                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                        priority = com.devson.vedinsta.database.NotificationPriority.HIGH
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Error adding fail notification to DB", ex)
                }
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

                val processedUrl = url

                val quotaManager = DownloadQuotaManager(applicationContext)
                val quotaStatus = quotaManager.checkQuota()
                if (quotaStatus is DownloadQuotaManager.QuotaStatus.Exceeded) {
                    val msg = "You have already used more than your set up downloading limit. Please wait for the limit to reset before downloading again."
                    notificationManager.showLinkError(msg)
                    return@launch
                }

                val postDataJson = fetchPostData(processedUrl)

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

            } catch (e: com.devson.vedinsta.extractor.AuthRequiredException) {
                Log.e(TAG, "AuthRequiredException in download all", e)
                val securePrefs = com.devson.vedinsta.repository.SecurePreferences(applicationContext)
                notificationManager.showPrivatePostNotification(url, securePrefs.hasValidSession())
                try {
                    notificationManager.addCustomNotification(
                        title = "Private Post Detected",
                        message = "Private or age-restricted content. Authentication required.",
                        type = com.devson.vedinsta.database.NotificationType.DOWNLOAD_FAILED,
                        priority = com.devson.vedinsta.database.NotificationPriority.HIGH
                    )
                } catch (ex: Exception) {
                    Log.e(TAG, "Error adding auth fail notification to DB", ex)
                }
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

    private suspend fun fetchPostData(url: String): String {
        return withContext(Dispatchers.IO) {
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
                val status = jsonResult.optString("status")
                if (status == "success") {
                    return@withContext resultString
                } else if (status == "login_required") {
                    throw com.devson.vedinsta.extractor.AuthRequiredException(jsonResult.optString("message", "Login required"))
                } else {
                    throw Exception(jsonResult.optString("message", "Unknown error"))
                }
            }
            throw Exception("Empty response from extractor")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}