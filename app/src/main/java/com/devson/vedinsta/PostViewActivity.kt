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

    companion object {
        private const val TAG = "PostViewActivity"
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

        Log.d(TAG, "PostViewActivity onCreate started")

        extractPostData()
        setupRecyclerView()
        setupClickListeners()
        setupPostInfo()
        loadMediaFilesFromDatabase()
    }

    private fun extractPostData() {
        val postId = intent.getStringExtra(EXTRA_POST_ID)
        Log.d(TAG, "Extracting post data for postId: $postId")

        if (postId == null) {
            Log.e(TAG, "PostId is null, finishing activity")
            finish()
            return
        }

        val postUrl = intent.getStringExtra(EXTRA_POST_URL) ?: ""
        val thumbnailPath = intent.getStringExtra(EXTRA_THUMBNAIL_PATH) ?: ""
        val totalImages = intent.getIntExtra(EXTRA_TOTAL_IMAGES, 1)
        val hasVideo = intent.getBooleanExtra(EXTRA_HAS_VIDEO, false)
        val downloadDate = intent.getLongExtra(EXTRA_DOWNLOAD_DATE, System.currentTimeMillis())
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "unknown"
        val caption = intent.getStringExtra(EXTRA_CAPTION)

        Log.d(TAG, "Post data - ID: $postId, Username: $username, TotalImages: $totalImages, HasVideo: $hasVideo")

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
        Log.d(TAG, "Setting up RecyclerView")

        mediaAdapter = MediaCarouselAdapter(
            mediaFiles = mediaFiles,
            onMediaClick = {
                Log.d(TAG, "Media clicked")
            },
            onVideoPlayPause = { isPlaying ->
                Log.d(TAG, "Video play/pause: $isPlaying")
            }
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

        updateMediaCounter()
        Log.d(TAG, "RecyclerView setup complete")
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            Log.d(TAG, "Back button clicked")
            finish()
        }
        binding.btnShare.setOnClickListener {
            Log.d(TAG, "Share button clicked")
            shareCurrentMedia()
        }
        binding.btnDelete.setOnClickListener {
            Log.d(TAG, "Delete button clicked")
            deletePost()
        }
        binding.btnCopyCaption.setOnClickListener {
            Log.d(TAG, "Copy caption clicked")
            copyCaptionToClipboard()
        }
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
        Log.d(TAG, "Setting up post info for: ${post.username}")

        binding.tvUsername.text = "@${post.username}"
        binding.tvPostCaption.text = post.caption ?: ""

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(post.downloadDate))
        binding.tvDownloadDate.text = "Downloaded on $formattedDate"
    }

    private fun loadMediaFilesFromDatabase() {
        val postId = currentPost?.postId
        if (postId == null) {
            Log.e(TAG, "PostId is null, cannot load media")
            return
        }

        Log.d(TAG, "Loading media files ONLY from database for postId: $postId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val post = db.downloadedPostDao().getPostById(postId)

                Log.d(TAG, "Post retrieved from DB: $post")

                if (post == null) {
                    Log.w(TAG, "No post found in database for ID: $postId")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PostViewActivity, "Post not found in database", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                val paths = post.mediaPaths
                Log.d(TAG, "STRICT - Media paths from DB for postId $postId: $paths")
                Log.d(TAG, "STRICT - Number of media paths: ${paths.size}")

                if (paths.isEmpty()) {
                    Log.w(TAG, "STRICT - No media paths found for post $postId")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PostViewActivity, "No media files stored for this post", Toast.LENGTH_LONG).show()
                        mediaFiles.clear()
                        updateUIWithFiles()
                    }
                    return@launch
                }

                val validFiles = mutableListOf<File>()

                for (path in paths) {
                    Log.d(TAG, "STRICT - Processing path for postId $postId: $path")

                    try {
                        if (path.startsWith("content://")) {
                            Log.d(TAG, "STRICT - Skipping content URI for postId $postId: $path")
                            continue
                        }

                        val file = File(path)
                        Log.d(TAG, "STRICT - File for postId $postId: ${file.absolutePath}")
                        Log.d(TAG, "STRICT - File exists: ${file.exists()}, canRead: ${file.canRead()}, size: ${file.length()}")

                        if (file.exists() && file.canRead() && file.length() > 0) {
                            validFiles.add(file)
                            Log.d(TAG, "STRICT - Added valid file for postId $postId: ${file.name}")
                        } else {
                            Log.w(TAG, "STRICT - Invalid file for postId $postId: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "STRICT - Error processing path for postId $postId: $path", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "STRICT - Final: Updating UI for postId $postId with ${validFiles.size} valid files")

                    currentPost = post
                    mediaFiles.clear()
                    mediaFiles.addAll(validFiles)

                    updateUIWithFiles()
                }

            } catch (e: Exception) {
                Log.e(TAG, "STRICT - Error loading media from database for postId $postId", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PostViewActivity, "Error loading media files: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUIWithFiles() {
        Log.d(TAG, "Updating UI with ${mediaFiles.size} files")

        // Recreate adapter with new data
        mediaAdapter = MediaCarouselAdapter(
            mediaFiles = mediaFiles,
            onMediaClick = {
                Log.d(TAG, "Media clicked in updated adapter")
            },
            onVideoPlayPause = { isPlaying ->
                Log.d(TAG, "Video play/pause in updated adapter: $isPlaying")
            }
        )

        binding.rvMediaCarousel.adapter = mediaAdapter
        updateMediaCounter()
        calculateTotalFileSize()

        if (mediaFiles.isEmpty()) {
            Log.w(TAG, "No valid media files found")
            Toast.makeText(this, "No media files found for this post", Toast.LENGTH_LONG).show()
        } else {
            Log.d(TAG, "Successfully loaded ${mediaFiles.size} media files")
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
        Log.d(TAG, "Total file size calculated: %.1f MB".format(sizeInMB))
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

                        Log.d(TAG, "Deleting post: ${post.postId}")

                        // Delete files from disk
                        var deletedCount = 0
                        mediaFiles.forEach { file ->
                            try {
                                if (file.exists() && file.delete()) {
                                    deletedCount++
                                    Log.d(TAG, "Deleted file: ${file.absolutePath}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting file: ${file.absolutePath}", e)
                            }
                        }

                        // Remove DB row
                        db.downloadedPostDao().deleteByPostId(post.postId)
                        Log.d(TAG, "Deleted post from database: ${post.postId}")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PostViewActivity, "Post deleted successfully ($deletedCount files)", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting post", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PostViewActivity, "Error deleting post: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}