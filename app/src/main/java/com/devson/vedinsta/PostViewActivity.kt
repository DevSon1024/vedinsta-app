package com.devson.vedinsta

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.devson.vedinsta.adapters.MediaCarouselAdapter
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ActivityPostViewBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PostViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostViewBinding
    private lateinit var mediaAdapter: MediaCarouselAdapter
    private var currentPost: DownloadedPost? = null
    private var mediaFiles: List<File> = emptyList()

    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_POST_URL = "post_url"
        const val EXTRA_THUMBNAIL_PATH = "thumbnail_path"
        const val EXTRA_TOTAL_IMAGES = "total_images"
        const val EXTRA_HAS_VIDEO = "has_video"
        const val EXTRA_DOWNLOAD_DATE = "download_date"

        fun createIntent(context: Context, post: DownloadedPost): Intent {
            return Intent(context, PostViewActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, post.postId)
                putExtra(EXTRA_POST_URL, post.postUrl)
                putExtra(EXTRA_THUMBNAIL_PATH, post.thumbnailPath)
                putExtra(EXTRA_TOTAL_IMAGES, post.totalImages)
                putExtra(EXTRA_HAS_VIDEO, post.hasVideo)
                putExtra(EXTRA_DOWNLOAD_DATE, post.downloadDate)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupBackPressed()
        extractPostData()
        setupRecyclerView() // Set up RecyclerView before calling loadMediaFiles
        setupClickListeners()
        setupPostInfo()
    }

    private fun setupUI() {
        // Setup toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.title = ""
    }

    private fun setupBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun extractPostData() {
        val postId = intent.getStringExtra(EXTRA_POST_ID) ?: return
        val postUrl = intent.getStringExtra(EXTRA_POST_URL) ?: ""
        val thumbnailPath = intent.getStringExtra(EXTRA_THUMBNAIL_PATH) ?: return
        val totalImages = intent.getIntExtra(EXTRA_TOTAL_IMAGES, 1)
        val hasVideo = intent.getBooleanExtra(EXTRA_HAS_VIDEO, false)
        val downloadDate = intent.getLongExtra(EXTRA_DOWNLOAD_DATE, System.currentTimeMillis())

        currentPost = DownloadedPost(
            postId = postId,
            postUrl = postUrl,
            thumbnailPath = thumbnailPath,
            totalImages = totalImages,
            downloadDate = downloadDate,
            hasVideo = hasVideo
        )

        // Load media files but don't update counter yet (RecyclerView not set up)
        loadMediaFilesOnly()
    }

    private fun loadMediaFilesOnly() {
        val post = currentPost ?: return
        val thumbnailFile = File(post.thumbnailPath)
        val parentDir = thumbnailFile.parentFile ?: return

        mediaFiles = parentDir.listFiles { file ->
            val name = file.name.lowercase()
            name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".mp4")
        }?.sortedBy { it.name } ?: emptyList()

        // Don't update counter here - RecyclerView not ready yet
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaCarouselAdapter(
            mediaFiles = mediaFiles,
            onMediaClick = { /* No action needed - removed fullscreen */ },
            onVideoPlayPause = { isPlaying ->
                // Handle video play/pause if needed
            }
        )

        binding.rvMediaCarousel.apply {
            adapter = mediaAdapter
            layoutManager = LinearLayoutManager(this@PostViewActivity, LinearLayoutManager.HORIZONTAL, false)

            // Add snap helper for smooth paging
            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)

            // Add scroll listener to update counter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updateMediaCounter()
                    }
                }
            })
        }

        // Now that RecyclerView is set up, update the counter
        updateMediaCounter()
    }

    private fun setupClickListeners() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnShare.setOnClickListener {
            shareCurrentMedia()
        }

        binding.btnDelete.setOnClickListener {
            deletePost()
        }
    }

    private fun setupPostInfo() {
        val post = currentPost ?: return

        // Extract username from URL or use default
        val username = extractUsernameFromUrl(post.postUrl)
        binding.tvUsername.text = "@$username"

        // Set sample caption (you can extract this from post metadata if available)
        binding.tvPostCaption.text = "Wishing you a bright and joyful Diwali filled with love, light, and happiness ❤️ 🪔 🌸 ✨ 🌙"

        // Format download date
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(post.downloadDate))
        binding.tvDownloadDate.text = "Downloaded on $formattedDate"

        // Calculate and show file size
        calculateTotalFileSize()

        // Format post date (sample - you can calculate based on actual post date)
        binding.tvPostDate.text = "2 days ago"
    }

    private fun extractUsernameFromUrl(url: String): String {
        return try {
            // Extract username from Instagram URL
            val regex = "instagram\\.com/([^/]+)".toRegex()
            regex.find(url)?.groupValues?.get(1) ?: "user"
        } catch (e: Exception) {
            "user"
        }
    }

    private fun calculateTotalFileSize() {
        var totalSize = 0L
        mediaFiles.forEach { file ->
            totalSize += file.length()
        }

        val sizeInMB = (totalSize / (1024.0 * 1024.0))
        binding.tvFileSize.text = String.format("%.1f MB", sizeInMB)
    }

    private fun updateMediaCounter() {
        // Add null check to prevent crashes
        val layoutManager = binding.rvMediaCarousel.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            val currentPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (currentPosition != RecyclerView.NO_POSITION) {
                val position = currentPosition + 1
                binding.tvMediaCounter.text = "$position / ${mediaFiles.size}"
            } else {
                // Default to showing first item
                binding.tvMediaCounter.text = "1 / ${mediaFiles.size}"
            }
        } else {
            // Fallback if layout manager is not ready
            binding.tvMediaCounter.text = "1 / ${mediaFiles.size}"
        }
    }

    private fun shareCurrentMedia() {
        if (mediaFiles.isEmpty()) return

        val layoutManager = binding.rvMediaCarousel.layoutManager as? LinearLayoutManager
        val currentPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0

        if (currentPosition != RecyclerView.NO_POSITION && currentPosition < mediaFiles.size) {
            val currentFile = mediaFiles[currentPosition]

            try {
                // Create share intent
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = if (currentFile.extension.lowercase() in listOf("mp4", "mov", "avi")) {
                        "video/*"
                    } else {
                        "image/*"
                    }
                    putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                        this@PostViewActivity,
                        "${applicationContext.packageName}.fileprovider",
                        currentFile
                    ))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                startActivity(Intent.createChooser(shareIntent, "Share media"))
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle sharing error - maybe show a Toast
            }
        }
    }

    private fun deletePost() {
        if (mediaFiles.isEmpty()) return

        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                // Delete files and close activity
                mediaFiles.forEach { file ->
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Delete from database (you'll need to implement this)
                // deletePostFromDatabase()

                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
