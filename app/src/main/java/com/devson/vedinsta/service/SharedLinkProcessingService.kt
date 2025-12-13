package com.devson.vedinsta.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.devson.vedinsta.DownloadActivity
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.notification.VedInstaNotificationManager
import kotlinx.coroutines.*
import org.json.JSONObject

class SharedLinkProcessingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var notificationManager: VedInstaNotificationManager

    companion object {
        private const val TAG = "SharedLinkService"
        const val EXTRA_INSTAGRAM_URL = "instagram_url"
        const val ACTION_DOWNLOAD_ALL = "com.devson.vedinsta.ACTION_DOWNLOAD_ALL"
        const val ACTION_SELECT_ITEMS = "com.devson.vedinsta.ACTION_SELECT_ITEMS"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = VedInstaNotificationManager.getInstance(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")

        intent?.let {
            when (it.action) {
                ACTION_DOWNLOAD_ALL -> {
                    val url = it.getStringExtra(EXTRA_INSTAGRAM_URL)
                    handleDownloadAll(url, startId)
                }
                ACTION_SELECT_ITEMS -> {
                    val url = it.getStringExtra(EXTRA_INSTAGRAM_URL)
                    handleSelectItems(url, startId)
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
            try {
                Log.d(TAG, "Processing link: $url")
                notificationManager.showLinkProcessing()

                val postDataJson = fetchPostData(url)
                if (postDataJson == null) {
                    notificationManager.cancelLinkProcessingNotification()
                    notificationManager.showRestrictedPostError()
                    stopSelfResult(startId)
                    return@launch
                }

                val postData = JSONObject(postDataJson)
                val mediaCount = postData.optJSONArray("media")?.length() ?: 0

                notificationManager.cancelLinkProcessingNotification()

                when {
                    mediaCount > 1 -> {
                        notificationManager.showMultipleContentOptions(url, mediaCount)
                    }
                    mediaCount == 1 -> {
                        handleDownloadAll(url, startId)
                        return@launch
                    }
                    else -> {
                        notificationManager.showLinkError("No media found")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing link", e)
                notificationManager.cancelLinkProcessingNotification()
                notificationManager.showLinkError("Error: ${e.message}")
            } finally {
                stopSelfResult(startId)
            }
        }
    }

    private fun handleDownloadAll(url: String?, startId: Int) {
        if (url.isNullOrEmpty()) {
            stopSelfResult(startId)
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Downloading all items from: $url")
                notificationManager.cancelMultipleContentNotification()

                val postDataJson = fetchPostData(url)
                if (postDataJson == null) {
                    notificationManager.showLinkError("Failed to fetch post data")
                    stopSelfResult(startId)
                    return@launch
                }

                val postData = JSONObject(postDataJson)
                val mediaArray = postData.optJSONArray("media")
                if (mediaArray == null || mediaArray.length() == 0) {
                    notificationManager.showLinkError("No media found")
                    stopSelfResult(startId)
                    return@launch
                }

                val totalItems = mediaArray.length()

                // Download all items
                val app = application as VedInstaApplication
                for (i in 0 until totalItems) {
                    notificationManager.showBatchDownloadProgress(i + 1, totalItems)

                    val mediaObj = mediaArray.getJSONObject(i)
                    val downloadUrl = mediaObj.getString("url")
                    val mediaType = mediaObj.optString("type", "image")
                    val username = postData.optString("username", "unknown")

                    app.downloadSingleMedia(downloadUrl, mediaType, username, i + 1)

                    if (i < totalItems - 1) {
                        delay(500)
                    }
                }

                delay(1000)
                notificationManager.showBatchDownloadComplete(totalItems)

            } catch (e: Exception) {
                Log.e(TAG, "Error in download all", e)
                notificationManager.showLinkError("Download failed")
            } finally {
                stopSelfResult(startId)
            }
        }
    }

    private fun handleSelectItems(url: String?, startId: Int) {
        if (url.isNullOrEmpty()) {
            stopSelfResult(startId)
            return
        }

        serviceScope.launch {
            try {
                Log.d(TAG, "Opening selection for: $url")
                notificationManager.cancelMultipleContentNotification()

                val postDataJson = fetchPostData(url)
                if (postDataJson == null) {
                    notificationManager.showLinkError("Failed to fetch post data")
                    stopSelfResult(startId)
                    return@launch
                }

                // Launch DownloadActivity with the result JSON
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@SharedLinkProcessingService, DownloadActivity::class.java).apply {
                        putExtra("RESULT_JSON", postDataJson)
                        putExtra("POST_URL", url)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error opening selection", e)
                notificationManager.showLinkError("Failed to open selection")
            } finally {
                stopSelfResult(startId)
            }
        }
    }

    private suspend fun fetchPostData(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val app = application as VedInstaApplication
                val python = com.chaquo.python.Python.getInstance()
                val module = python.getModule("insta_downloader")
                val result = module.callAttr("get_media_urls", url)
                val resultString = result?.toString()

                if (resultString != null) {
                    val jsonResult = JSONObject(resultString)
                    val status = jsonResult.optString("status", "error")

                    if (status == "success") {
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
