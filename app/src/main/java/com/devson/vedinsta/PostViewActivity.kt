package com.devson.vedinsta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.devson.vedinsta.adapters.MediaCarouselAdapter
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ActivityPostViewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PostViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostViewBinding
    private lateinit var mediaAdapter: MediaCarouselAdapter
    private var currentPost: DownloadedPost? = null
    private var mediaFiles: MutableList<File> = mutableListOf()

    private val TAG = "PostViewActivity"

    companion object {
        const val EXTRA_POST_ID = "post_id"
        const val EXTRA_POST_URL = "post_url"
        const val EXTRA_THUMBNAIL_PATH = "thumbnail_path"
        const val EXTRA_TOTAL_IMAGES = "total_images"
        const val EXTRA_HAS_VIDEO = "has_video"
        const val EXTRA_DOWNLOAD_DATE = "download_date"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_CAPTION = "caption"

        fun createIntent(context: Context, post: DownloadedPost): Intent {
            return Intent(context, PostViewActivity::class.java).apply {
                putExtra(EXTRA_POST_ID, post.postId)
                putExtra(EXTRA_POST_URL, post.postUrl)
                putExtra(EXTRA_THUMBNAIL_PATH, post.thumbnailPath)
                putExtra(EXTRA_TOTAL_IMAGES, post.totalImages)
                putExtra(EXTRA_HAS_VIDEO, post.hasVideo)
                putExtra(EXTRA_DOWNLOAD_DATE, post.downloadDate)
                putExtra(EXTRA_USERNAME, post.username)
                putExtra(EXTRA_CAPTION, post.caption)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "onCreate started")

        extractPostData()
        setupRecyclerView()
        setupClickListeners()
        setupPostInfo()

        // Load media files - try both database and fallback methods
        loadMediaFiles()
    }

    private fun extractPostData() {
        val postId = intent.getStringExtra(EXTRA_POST_ID)
        val postUrl = intent.getStringExtra(EXTRA_POST_URL) ?: ""
        val thumbnailPath = intent.getStringExtra(EXTRA_THUMBNAIL_PATH)
        val totalImages = intent.getIntExtra(EXTRA_TOTAL_IMAGES, 1)
        val hasVideo = intent.getBooleanExtra(EXTRA_HAS_VIDEO, false)
        val downloadDate = intent.getLongExtra(EXTRA_DOWNLOAD_DATE, System.currentTimeMillis())
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "unknown"
        val caption = intent.getStringExtra(EXTRA_CAPTION)

        Log.d(TAG, "Extracted data - PostId: $postId, ThumbnailPath: $thumbnailPath, Username: $username")

        if (postId == null || thumbnailPath == null) {
            Log.e(TAG, "Missing required data - PostId: $postId, ThumbnailPath: $thumbnailPath")
            Toast.makeText(this, "Error: Missing post data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        currentPost = DownloadedPost(
            postId = postId,
            postUrl = postUrl,
            thumbnailPath = thumbnailPath,
            totalImages = totalImages,
            downloadDate = downloadDate,
            hasVideo = hasVideo,
            username = username,
            caption = caption,
            mediaPaths = emptyList()
        )
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaCarouselAdapter(
            mediaFiles = mediaFiles,
            onMediaClick = { },
            onVideoPlayPause = { }
        )

        binding.rvMediaCarousel.apply {
            adapter = mediaAdapter
            layoutManager = LinearLayoutManager(this@PostViewActivity, LinearLayoutManager.HORIZONTAL, false)

            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)

            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        updateMediaCounter()
                    }
                }
            })
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnShare.setOnClickListener { shareCurrentMedia() }
        binding.btnDelete.setOnClickListener { deletePost() }
        binding.btnCopyCaption.setOnClickListener { copyCaptionToClipboard() }
    }

    private fun copyCaptionToClipboard() {
        val caption = currentPost?.caption
        if (!caption.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("caption", caption)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Caption copied to clipboard", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No caption to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPostInfo() {
        val post = currentPost ?: return
        binding.tvUsername.text = "@${post.username}"
        binding.tvPostCaption.text = post.caption ?: ""
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(post.downloadDate))
        binding.tvDownloadDate.text = "Downloaded on $formattedDate"
        binding.tvPostDate.text = "2 days ago"
    }

    private fun loadMediaFiles() {
        val postId = currentPost?.postId ?: return
        Log.d(TAG, "Loading media files for post: $postId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // First, try to load from database
                val dbFiles = loadFromDatabase(postId)

                if (dbFiles.isNotEmpty()) {
                    Log.d(TAG, "Loaded ${dbFiles.size} files from database")
                    updateUI(dbFiles)
                } else {
                    Log.w(TAG, "No files found in database, trying fallback method")
                    // Fallback: try to load from the old folder structure or thumbnail directory
                    val fallbackFiles = loadFromFallback()
                    updateUI(fallbackFiles)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading media files", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PostViewActivity, "Error loading media files: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun loadFromDatabase(postId: String): List<File> {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val post = db.downloadedPostDao().getPostById(postId)

            Log.d(TAG, "Database post found: ${post != null}")
            Log.d(TAG, "Media paths from DB: ${post?.mediaPaths}")

            val paths = post?.mediaPaths ?: emptyList()
            val validFiles = mutableListOf<File>()

            for (path in paths) {
                Log.d(TAG, "Processing path: $path")

                val file = when {
                    path.startsWith("content://") -> {
                        Log.d(TAG, "Skipping content URI: $path")
                        continue // Skip content URIs for now
                    }
                    else -> File(path)
                }

                if (file.exists() && file.canRead()) {
                    validFiles.add(file)
                    Log.d(TAG, "Added valid file: ${file.absolutePath}")
                } else {
                    Log.w(TAG, "File not accessible: $path (exists: ${file.exists()}, canRead: ${file.canRead()})")
                }
            }

            validFiles
        } catch (e: Exception) {
            Log.e(TAG, "Database loading failed", e)
            emptyList()
        }
    }

    private suspend fun loadFromFallback(): List<File> {
        return try {
            val post = currentPost ?: return emptyList()
            val thumbnailFile = File(post.thumbnailPath)

            Log.d(TAG, "Fallback: Using thumbnail path: ${post.thumbnailPath}")
            Log.d(TAG, "Thumbnail file exists: ${thumbnailFile.exists()}")

            val parentDir = thumbnailFile.parentFile
            Log.d(TAG, "Parent directory: ${parentDir?.absolutePath}")
            Log.d(TAG, "Parent directory exists: ${parentDir?.exists()}")

            if (parentDir?.exists() == true) {
                val files = parentDir.listFiles { file ->
                    val name = file.name.lowercase()
                    name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                            name.endsWith(".png") || name.endsWith(".mp4")
                }?.sortedBy { it.name } ?: emptyList()

                Log.d(TAG, "Fallback found ${files.size} files in directory")
                files.forEach { Log.d(TAG, "Fallback file: ${it.absolutePath}") }

                files
            } else {
                Log.w(TAG, "Fallback: Parent directory not accessible")
                // Last resort: try to find the thumbnail file itself
                if (thumbnailFile.exists()) {
                    Log.d(TAG, "Using thumbnail file only")
                    listOf(thumbnailFile)
                } else {
                    Log.e(TAG, "Even thumbnail file is not accessible")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback loading failed", e)
            emptyList()
        }
    }

    private suspend fun updateUI(files: List<File>) {
        withContext(Dispatchers.Main) {
            Log.d(TAG, "Updating UI with ${files.size} files")

            mediaFiles.clear()
            mediaFiles.addAll(files)

            // Recreate adapter
            mediaAdapter = MediaCarouselAdapter(
                mediaFiles = mediaFiles,
                onMediaClick = { },
                onVideoPlayPause = { }
            )

            binding.rvMediaCarousel.adapter = mediaAdapter
            updateMediaCounter()
            calculateTotalFileSize()

            if (mediaFiles.isEmpty()) {
                Toast.makeText(this@PostViewActivity, "No media files found for this post", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "No media files to display")
            } else {
                Log.d(TAG, "Successfully loaded ${mediaFiles.size} media files")
            }
        }
    }

    private fun calculateTotalFileSize() {
        var totalSize = 0L
        mediaFiles.forEach { file ->
            try {
                totalSize += file.length()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file size: ${file.absolutePath}", e)
            }
        }
        val sizeInMB = (totalSize / (1024.0 * 1024.0))
        binding.tvFileSize.text = String.format("%.1f MB", sizeInMB)
    }

    private fun updateMediaCounter() {
        val layoutManager = binding.rvMediaCarousel.layoutManager as? LinearLayoutManager
        val totalFiles = mediaFiles.size

        if (totalFiles == 0) {
            binding.tvMediaCounter.text = "0 / 0"
            return
        }

        if (layoutManager != null) {
            val currentPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (currentPosition != RecyclerView.NO_POSITION) {
                val position = currentPosition + 1
                binding.tvMediaCounter.text = "$position / $totalFiles"
            } else {
                binding.tvMediaCounter.text = "1 / $totalFiles"
            }
        } else {
            binding.tvMediaCounter.text = "1 / $totalFiles"
        }
    }

    private fun shareCurrentMedia() {
        if (mediaFiles.isEmpty()) {
            Toast.makeText(this, "No media to share", Toast.LENGTH_SHORT).show()
            return
        }

        val layoutManager = binding.rvMediaCarousel.layoutManager as? LinearLayoutManager
        val currentPosition = layoutManager?.findFirstCompletelyVisibleItemPosition() ?: 0

        if (currentPosition != RecyclerView.NO_POSITION && currentPosition < mediaFiles.size) {
            val currentFile = mediaFiles[currentPosition]

            try {
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
                Log.e(TAG, "Error sharing file", e)
                Toast.makeText(this, "Error sharing media", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePost() {
        if (mediaFiles.isEmpty()) {
            Toast.makeText(this, "No media to delete", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val post = currentPost ?: return@launch

                        // Delete files from disk
                        mediaFiles.forEach { file ->
                            try {
                                if (file.exists()) {
                                    file.delete()
                                    Log.d(TAG, "Deleted file: ${file.absolutePath}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
                            }
                        }

                        // Remove DB row
                        db.downloadedPostDao().deleteByPostId(post.postId)

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PostViewActivity, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting post", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PostViewActivity, "Error deleting post", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}