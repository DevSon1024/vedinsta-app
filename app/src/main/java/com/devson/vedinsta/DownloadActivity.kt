package com.devson.vedinsta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ActivityDownloadBinding
import com.devson.vedinsta.notification.VedInstaNotificationManager
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class DownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadBinding
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var notificationManager: VedInstaNotificationManager
    private val mediaList = mutableListOf<ImageCard>()

    // Get data from intent
    private var postUrl: String? = null
    private var postId: String? = null
    private var postCaption: String? = null
    private var postUsername: String = "unknown"

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize components
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        notificationManager = VedInstaNotificationManager.getInstance(this)

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Get data from intent
        postUrl = intent.getStringExtra("POST_URL")
        postId = intent.getStringExtra("POST_ID")

        setupRecyclerView()

        val resultJson = intent.getStringExtra("RESULT_JSON")
        if (resultJson != null) {
            handlePythonResult(resultJson)
        } else {
            Toast.makeText(this, "Error: No data received", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Download button logic
        binding.btnDownloadSelected.setOnClickListener {
            val selected = imageAdapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                startDownloadWithNotification(selected)
            } else {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownloadAll.setOnClickListener {
            if (mediaList.isNotEmpty()) {
                startDownloadWithNotification(mediaList)
            } else {
                Toast.makeText(this, "No media to download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun startDownloadWithNotification(items: List<ImageCard>) {
        lifecycleScope.launch(Dispatchers.Main) {
            // Show initial notification
            val notificationId = notificationManager.showDownloadStarted(
                if (items.size == 1) "${postUsername}_media"
                else "${items.size} files"
            )

            // Start download in background
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Get highest quality URLs and ensure username is set
                    val highQualityItems = items.map { item ->
                        item.copy(
                            url = getHighestQualityUrl(item.url),
                            username = if (item.username == "unknown") postUsername else item.username
                        )
                    }

                    val downloadedFiles = downloadFilesWithProgress(highQualityItems, postId) { progress: Int, fileName: String ->
                        // Update notification progress
                        notificationManager.updateDownloadProgress(
                            notificationId,
                            fileName,
                            progress
                        )
                    }

                    if (downloadedFiles.isNotEmpty()) {
                        // Save to database with media paths
                        saveDownloadedPost(postId, postUrl ?: "", downloadedFiles)

                        // Show completion notification
                        notificationManager.cancelDownloadNotification(notificationId)
                        notificationManager.showDownloadCompleted(
                            "${postUsername}_media",
                            downloadedFiles.size
                        )

                        runOnUiThread {
                            Toast.makeText(this@DownloadActivity,
                                "Downloaded ${downloadedFiles.size} files successfully!",
                                Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        notificationManager.showDownloadError(
                            "${postUsername}_media",
                            "No files were downloaded"
                        )
                    }

                } catch (e: Exception) {
                    notificationManager.showDownloadError(
                        "${postUsername}_media",
                        e.message ?: "Download failed"
                    )
                    runOnUiThread {
                        Toast.makeText(this@DownloadActivity,
                            "Download failed: ${e.message}",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private suspend fun downloadFilesWithProgress(
        items: List<ImageCard>,
        postId: String?,
        progressCallback: (progress: Int, fileName: String) -> Unit
    ): List<String> {
        return (application as VedInstaApplication).downloadFiles(this, items, postId)
    }

    private fun getHighestQualityUrl(originalUrl: String): String {
        // Instagram URL patterns for highest quality
        return when {
            originalUrl.contains("instagram.com") && originalUrl.contains("_n.jpg") -> {
                originalUrl.replace("_n.jpg", ".jpg")
            }
            originalUrl.contains("instagram.com") && originalUrl.contains("_n.mp4") -> {
                originalUrl.replace("_n.mp4", ".mp4")
            }
            originalUrl.contains("scontent") -> {
                if (originalUrl.contains("?")) {
                    "$originalUrl&_nc_ohc=original"
                } else {
                    "$originalUrl?_nc_ohc=original"
                }
            }
            else -> originalUrl
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(mediaList)
        binding.recyclerViewImages.apply {
            adapter = imageAdapter
            layoutManager = GridLayoutManager(this@DownloadActivity, 2)
        }
    }

    private fun handlePythonResult(jsonString: String) {
        try {
            val result = JSONObject(jsonString)
            when (result.getString("status")) {
                "success" -> {
                    postUsername = result.getString("username")
                    postCaption = result.getString("caption")
                    val mediaArray = result.getJSONArray("media")

                    for (i in 0 until mediaArray.length()) {
                        val mediaObject = mediaArray.getJSONObject(i)
                        mediaList.add(
                            ImageCard(
                                url = mediaObject.getString("url"),
                                type = mediaObject.getString("type"),
                                username = postUsername
                            )
                        )
                    }
                    imageAdapter.notifyDataSetChanged()
                }
                else -> {
                    val message = result.getString("message")
                    Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to parse result: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveDownloadedPost(postId: String?, postUrl: String, downloadedFiles: List<String>) {
        if (postId != null && downloadedFiles.isNotEmpty()) {
            val downloadedPost = DownloadedPost(
                postId = postId,
                postUrl = postUrl,
                thumbnailPath = downloadedFiles.first(),
                totalImages = downloadedFiles.size,
                downloadDate = System.currentTimeMillis(),
                hasVideo = downloadedFiles.any { it.endsWith(".mp4") },
                username = postUsername,
                caption = postCaption,
                mediaPaths = downloadedFiles // Store all downloaded file paths
            )

            viewModel.insertDownloadedPost(downloadedPost)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,
                        "Notification permission denied. Download progress won't be shown.",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
