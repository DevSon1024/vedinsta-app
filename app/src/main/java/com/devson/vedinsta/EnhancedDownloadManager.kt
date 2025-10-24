package com.devson.vedinsta

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class EnhancedDownloadManager(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "EnhancedDownloadManager"
    }

    override suspend fun doWork(): Result {
        val mediaUrl = inputData.getString("media_url") ?: return Result.failure()
        val filePath = inputData.getString("file_path") ?: return Result.failure()
        val mediaType = inputData.getString("media_type") ?: "image"

        return try {
            val success = downloadWithEnhancedHeaders(mediaUrl, filePath, mediaType)
            if (success) Result.success() else Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure()
        }
    }

    private suspend fun downloadWithEnhancedHeaders(
        url: String,
        filePath: String,
        mediaType: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = createEnhancedOkHttpClient()
            val request = createEnhancedRequest(url)

            Log.d(TAG, "Starting enhanced download: $url")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Download failed with code: ${response.code}")
                    return@withContext false
                }

                response.body?.byteStream()?.use { inputStream ->
                    FileOutputStream(File(filePath)).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Log.d(TAG, "Download completed successfully: $filePath")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Enhanced download error", e)
            false
        }
    }

    private fun createEnhancedOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val newRequest = originalRequest.newBuilder()
                    .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
                    .addHeader("Accept", "*/*")
                    .addHeader("Accept-Language", "en-US,en;q=0.9")
                    .addHeader("Accept-Encoding", "gzip, deflate, br")
                    .addHeader("DNT", "1")
                    .addHeader("Connection", "keep-alive")
                    .addHeader("Sec-Fetch-Dest", "video")
                    .addHeader("Sec-Fetch-Mode", "cors")
                    .addHeader("Sec-Fetch-Site", "cross-site")
                    .addHeader("sec-ch-ua", "\"Google Chrome\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
                    .addHeader("sec-ch-ua-mobile", "?1")
                    .addHeader("sec-ch-ua-platform", "\"Android\"")
                    .build()

                chain.proceed(newRequest)
            }
            .build()
    }

    private fun createEnhancedRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .build()
    }
}
