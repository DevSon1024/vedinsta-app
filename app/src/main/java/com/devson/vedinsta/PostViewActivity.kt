package com.devson.vedinsta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
    private var mediaFiles: List<File> = emptyList()

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

        extractPostData()
        setupRecyclerView()
        setupClickListeners()
        setupPostInfo()
        // Load the canonical media list for this post from Room
        loadMediaFilesFromDatabase()
    }

    private fun extractPostData() {
        val postId = intent.getStringExtra(EXTRA_POST_ID) ?: return
        val postUrl = intent.getStringExtra(EXTRA_POST_URL) ?: ""
        val thumbnailPath = intent.getStringExtra(EXTRA_THUMBNAIL_PATH) ?: return
        val totalImages = intent.getIntExtra(EXTRA_TOTAL_IMAGES, 1)
        val hasVideo = intent.getBooleanExtra(EXTRA_HAS_VIDEO, false)
        val downloadDate = intent.getLongExtra(EXTRA_DOWNLOAD_DATE, System.currentTimeMillis())
        val username = intent.getStringExtra(EXTRA_USERNAME) ?: "unknown"
        val caption = intent.getStringExtra(EXTRA_CAPTION)

        currentPost = DownloadedPost(
            postId = postId,
            postUrl = postUrl,
            thumbnailPath = thumbnailPath,
            totalImages = totalImages,
            downloadDate = downloadDate,
            hasVideo = hasVideo,
            username = username,
            caption = caption,
            mediaPaths = currentPost?.mediaPaths ?: emptyList()
        )
    }

    private fun setupRecyclerView() {
        mediaAdapter = MediaCarouselAdapter(
            mediaFiles = mediaFiles,
            onMediaClick = { /* optional */ },
            onVideoPlayPause = { _ -> }
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
        calculateTotalFileSize()
        binding.tvPostDate.text = "2 days ago"
    }

    private fun loadMediaFilesFromDatabase() {
        val postId = currentPost?.postId ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            val post = db.downloadedPostDao().getPostById(postId)
            val paths = post?.mediaPaths ?: emptyList()
            val files = paths.mapNotNull { p -> runCatching { File(p) }.getOrNull() }.filter { it.exists() }

            withContext(Dispatchers.Main) {
                currentPost = post ?: currentPost
                mediaFiles = files
                mediaAdapter = MediaCarouselAdapter(
                    mediaFiles = mediaFiles,
                    onMediaClick = { /* optional */ },
                    onVideoPlayPause = { _ -> }
                )
                binding.rvMediaCarousel.adapter = mediaAdapter

                updateMediaCounter()
                calculateTotalFileSize()
            }
        }
    }

    private fun calculateTotalFileSize() {
        var totalSize = 0L
        mediaFiles.forEach { file -> totalSize += file.length() }
        val sizeInMB = (totalSize / (1024.0 * 1024.0))
        binding.tvFileSize.text = String.format("%.1f MB", sizeInMB)
    }

    private fun updateMediaCounter() {
        val layoutManager = binding.rvMediaCarousel.layoutManager as? LinearLayoutManager
        if (layoutManager != null) {
            val currentPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
            if (currentPosition != RecyclerView.NO_POSITION) {
                val position = currentPosition + 1
                binding.tvMediaCounter.text = "$position / ${mediaFiles.size}"
            } else {
                binding.tvMediaCounter.text = "1 / ${mediaFiles.size}"
            }
        } else {
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
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    type = if (currentFile.extension.lowercase() in listOf("mp4", "mov", "avi")) "video/*" else "image/*"
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
            }
        }
    }

    private fun deletePost() {
        if (mediaFiles.isEmpty()) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    val post = currentPost ?: return@launch
                    // Delete files from disk
                    mediaFiles.forEach { runCatching { it.delete() } }
                    // Remove DB row
                    db.downloadedPostDao().deleteByPostId(post.postId)
                    withContext(Dispatchers.Main) {
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
