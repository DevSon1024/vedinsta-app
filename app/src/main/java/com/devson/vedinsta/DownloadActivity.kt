package com.devson.vedinsta

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
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
    private val selectedItems = mutableSetOf<Int>()
    private var currentPosition = 0

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

        // Initialize components
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        notificationManager = VedInstaNotificationManager.getInstance(this)

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Get data from intent
        postUrl = intent.getStringExtra("POST_URL")
        postId = intent.getStringExtra("POST_ID")

        Log.d(TAG, "DownloadActivity started for postId: $postId")

        setupRecyclerView()
        setupClickListeners()

        val resultJson = intent.getStringExtra("RESULT_JSON")
        if (resultJson != null) {
            handlePythonResult(resultJson)
        } else {
            Toast.makeText(this, "Error: No data received", Toast.LENGTH_SHORT).show()
            finish()
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

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(
            mediaList = mediaList,
            selectedItems = selectedItems,
            onSelectionChanged = { position, isSelected ->
                if (isSelected) {
                    selectedItems.add(position)
                } else {
                    selectedItems.remove(position)
                }
                updateDownloadButton()
                updateSelectAllButton()
            }
        )

        binding.recyclerViewImages.apply {
            adapter = imageAdapter
            layoutManager = LinearLayoutManager(this@DownloadActivity, LinearLayoutManager.HORIZONTAL, false)

            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updateCurrentPosition()
                    }
                }
            })
        }
    }

    private fun setupClickListeners() {
        // Select All button
        binding.btnSelectAll.setOnClickListener {
            toggleSelectAll()
        }

        // Download button
        binding.btnDownload.setOnClickListener {
            startSelectedDownload()
        }
    }

    private fun toggleSelectAll() {
        if (selectedItems.size == mediaList.size) {
            // Deselect all
            selectedItems.clear()
        } else {
            // Select all
            selectedItems.clear()
            selectedItems.addAll(0 until mediaList.size)
        }
        imageAdapter.notifyDataSetChanged()
        updateDownloadButton()
        updateSelectAllButton()
    }

    private fun updateSelectAllButton() {
        if (selectedItems.size == mediaList.size) {
            binding.btnSelectAll.setImageResource(R.drawable.ic_check_circle_filled)
        } else {
            binding.btnSelectAll.setImageResource(R.drawable.ic_check_circle_outline)
        }
    }

    private fun updateCurrentPosition() {
        val layoutManager = binding.recyclerViewImages.layoutManager as? LinearLayoutManager
        val newPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0

        if (newPosition != RecyclerView.NO_POSITION && newPosition != currentPosition) {
            currentPosition = newPosition
            binding.dotsIndicator.setSelectedPosition(currentPosition, true)
        }
    }

    private fun updateDownloadButton() {
        val selectedCount = selectedItems.size
        val totalCount = mediaList.size

        if (selectedCount > 0) {
            binding.btnDownload.text = "DOWNLOAD ($selectedCount/$totalCount)"
            binding.btnDownload.isEnabled = true
            binding.btnDownload.alpha = 1.0f
        } else {
            binding.btnDownload.text = "DOWNLOAD (0/$totalCount)"
            binding.btnDownload.isEnabled = false
            binding.btnDownload.alpha = 0.6f
        }
    }

    private fun startSelectedDownload() {
        val selectedMediaList = selectedItems.map { mediaList[it] }

        if (selectedMediaList.isNotEmpty()) {
            Log.d(TAG, "Starting download for ${selectedMediaList.size} selected items")

            lifecycleScope.launch(Dispatchers.Main) {
                // Show initial notification
                val notificationId = notificationManager.showDownloadStarted(
                    if (selectedMediaList.size == 1) "${postUsername}_media"
                    else "${selectedMediaList.size} files"
                )

                // Start download in background
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val downloadedFiles = (application as VedInstaApplication).downloadFiles(
                            this@DownloadActivity,
                            selectedMediaList,
                            postId
                        )

                        if (downloadedFiles.isNotEmpty()) {
                            // Save to database
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

                                // Exit the activity after successful download
                                finish()
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
        } else {
            Toast.makeText(this, "Please select at least one item to download", Toast.LENGTH_SHORT).show()
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

                    Log.d(TAG, "Processing ${mediaArray.length()} media items")
                    binding.tvUsername.text = "@$postUsername"

                    mediaList.clear()
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

                    // Setup dots indicator
                    binding.dotsIndicator.setDotCount(mediaList.size)
                    binding.dotsIndicator.setSelectedPosition(0, false)

                    // Show dots only if multiple media
                    binding.dotsIndicator.visibility = if (mediaList.size > 1) View.VISIBLE else View.GONE

                    imageAdapter.notifyDataSetChanged()
                    updateDownloadButton()
                    updateSelectAllButton()
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

    private fun saveDownloadedPost(postId: String?, postUrl: String, downloadedFiles: List<String>) {
        if (postId != null && downloadedFiles.isNotEmpty()) {
            Log.d(TAG, "Saving post to database:")
            Log.d(TAG, "  PostId: $postId")
            Log.d(TAG, "  Downloaded files: $downloadedFiles")
            Log.d(TAG, "  Files count: ${downloadedFiles.size}")

            val downloadedPost = DownloadedPost(
                postId = postId,
                postUrl = postUrl,
                thumbnailPath = downloadedFiles.first(),
                totalImages = downloadedFiles.size,
                downloadDate = System.currentTimeMillis(),
                hasVideo = downloadedFiles.any { it.endsWith(".mp4") },
                username = postUsername,
                caption = postCaption,
                mediaPaths = downloadedFiles
            )

            viewModel.insertDownloadedPost(downloadedPost)
            Log.d(TAG, "Post saved to database successfully")
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
}