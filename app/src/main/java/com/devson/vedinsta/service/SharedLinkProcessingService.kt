package com.devson.vedinsta.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.devson.vedinsta.ImageCard
import com.devson.vedinsta.SettingsManager
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.*
import org.json.JSONObject

class SharedLinkProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager
    private lateinit var settingsManager: SettingsManager

    companion object {
        private const val TAG = "SharedLinkService"
        const val EXTRA_INSTAGRAM_URL = "instagram_url"
        const val ACTION_DOWNLOAD_ALL = "com.devson.vedinsta.ACTION_DOWNLOAD_ALL"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = VedInstaNotificationManager.getInstance(this)
        settingsManager = SettingsManager(this)
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
                } else if (mediaCount > 1) {
                    // MULTIPLE CONTENT: Check Settings
                    when (settingsManager.defaultLinkAction) {
                        SettingsManager.ACTION_DOWNLOAD_ALL -> {
                            handleDownloadAll(url, -1)
                            shouldStopSelf = false
                        }
                        SettingsManager.ACTION_OPEN_SELECTION -> {
                            notificationManager.showMultipleContentOptions(url, mediaCount, autoOpenSelection = true)
                        }
                        else -> {
                            notificationManager.showMultipleContentOptions(url, mediaCount)
                        }
                    }
                } else {
                    notificationManager.showLinkError("No media found in link.")
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

            } catch (e: Exception) {
                Log.e(TAG, "Error in download all", e)
                notificationManager.showLinkError("Batch download failed")
            } finally {
                if (startId != -1) stopSelfResult(startId)
            }
        }
    }

    private suspend fun fetchPostData(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val python = com.chaquo.python.Python.getInstance()
                val module = python.getModule("insta_downloader")
                val result = module.callAttr("get_media_urls", url)
                val resultString = result?.toString()

                if (resultString != null) {
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