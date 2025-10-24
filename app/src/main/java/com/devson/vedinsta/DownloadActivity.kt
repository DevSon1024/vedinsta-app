package com.devson.vedinsta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ActivityDownloadBinding
import com.devson.vedinsta.notification.VedInstaNotificationManager
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private const val TAG = "DownloadActivity"
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

        Log.d(TAG, "=== DownloadActivity onCreate ===")
        Log.d(TAG, "PostId: $postId")
        Log.d(TAG, "PostUrl: $postUrl")

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
        Log.d(TAG, "=== Starting Download Process ===")
        Log.d(TAG, "Items to download: ${items.size}")
        Log.d(TAG, "PostId: $postId")

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

                    Log.d(TAG, "=== Calling downloadFiles ===")
                    val downloadedFiles = (application as VedInstaApplication).downloadFiles(this@DownloadActivity, highQualityItems, postId)

                    Log.d(TAG, "=== Download Result ===")
                    Log.d(TAG, "Downloaded files: $downloadedFiles")
                    Log.d(TAG, "Downloaded files count: ${downloadedFiles.size}")
                    downloadedFiles.forEachIndexed { index, path ->
                        Log.d(TAG, "File $index: $path")
                    }

                    if (downloadedFiles.isNotEmpty()) {
                        // Save to database with CORRECT media paths
                        saveDownloadedPostWithVerification(postId, postUrl ?: "", downloadedFiles)

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
                        Log.w(TAG, "No files were downloaded")
                        notificationManager.showDownloadError(
                            "${postUsername}_media",
                            "No files were downloaded"
                        )
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Download failed", e)
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

    private fun getHighestQualityUrl(originalUrl: String): String {
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

                    Log.d(TAG, "=== Python Result Processing ===")
                    Log.d(TAG, "Username: $postUsername")
                    Log.d(TAG, "Caption: $postCaption")
                    Log.d(TAG, "Media count: ${mediaArray.length()}")

                    for (i in 0 until mediaArray.length()) {
                        val mediaObject = mediaArray.getJSONObject(i)
                        val url = mediaObject.getString("url")
                        val type = mediaObject.getString("type")

                        Log.d(TAG, "Media $i - URL: $url, Type: $type")

                        mediaList.add(
                            ImageCard(
                                url = url,
                                type = type,
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
            Log.e(TAG, "Failed to parse result", e)
            Toast.makeText(this, "Failed to parse result: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun saveDownloadedPostWithVerification(postId: String?, postUrl: String, downloadedFiles: List<String>) {
        if (postId == null || downloadedFiles.isEmpty()) {
            Log.w(TAG, "Cannot save post - postId: $postId, files count: ${downloadedFiles.size}")
            return
        }

        Log.d(TAG, "=== Saving Post to Database ===")
        Log.d(TAG, "PostId: $postId")
        Log.d(TAG, "PostUrl: $postUrl")
        Log.d(TAG, "Username: $postUsername")
        Log.d(TAG, "Caption: $postCaption")
        Log.d(TAG, "Downloaded files count: ${downloadedFiles.size}")

        downloadedFiles.forEachIndexed { index, path ->
            Log.d(TAG, "MediaPath $index: $path")
        }

        val downloadedPost = DownloadedPost(
            postId = postId,
            postUrl = postUrl,
            thumbnailPath = downloadedFiles.first(),
            totalImages = downloadedFiles.size,
            downloadDate = System.currentTimeMillis(),
            hasVideo = downloadedFiles.any { it.endsWith(".mp4") },
            username = postUsername,
            caption = postCaption,
            mediaPaths = downloadedFiles // CRITICAL: Store the actual file paths
        )

        Log.d(TAG, "DownloadedPost object created: $downloadedPost")

        // Save and verify
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Save using ViewModel
                viewModel.insertDownloadedPost(downloadedPost)
                Log.d(TAG, "Post saved via ViewModel")

                // Verify by reading back from database
                val db = AppDatabase.getDatabase(applicationContext)
                val savedPost = db.downloadedPostDao().getPostById(postId)

                Log.d(TAG, "=== Verification - Post Read Back ===")
                Log.d(TAG, "Saved post: $savedPost")

                if (savedPost != null) {
                    Log.d(TAG, "Verification - MediaPaths: ${savedPost.mediaPaths}")
                    Log.d(TAG, "Verification - MediaPaths count: ${savedPost.mediaPaths.size}")
                } else {
                    Log.e(TAG, "Verification FAILED - Post not found in database!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error saving or verifying post", e)
            }
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