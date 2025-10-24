package com.devson.vedinsta

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.doOnNextLayout
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
import kotlin.math.abs
import kotlin.math.roundToInt

class PostViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostViewBinding
    private lateinit var mediaAdapter: MediaCarouselAdapter
    private var currentPost: DownloadedPost? = null
    private var mediaFiles: MutableList<File> = mutableListOf()

    // Position tracking
    private var currentMediaPosition = 0
    private var lastReportedPage = -1
    private var isUserFlinging = false

    // Caption state
    private var isCaptionExpanded = false
    private val maxCaptionLength = 100

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
        val downloadDate = intent.getLongExtra(EXTRA_DOWNLOAD_DATE, System.currentTimeMillis()) // FIXED: was EXRA_DOWNLOAD_DATE
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
                Log.d(TAG, "Media clicked - toggling full screen")
                toggleImageScaleMode()
            },
            onVideoPlayPause = { isPlaying ->
                Log.d(TAG, "Video play/pause: $isPlaying")
            }
        )

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.rvMediaCarousel.apply {
            adapter = mediaAdapter
            this.layoutManager = layoutManager
            itemAnimator = null // Remove default item animation for snappier updates
        }

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvMediaCarousel)

        // Ensure dots prepare once sizes are laid out
        binding.rvMediaCarousel.doOnNextLayout {
            setupDotsIndicator()
            updateMediaCounter()
        }

        // High-frequency, real-time updates using onScrolled
        binding.rvMediaCarousel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isUserFlinging = newState == RecyclerView.SCROLL_STATE_SETTLING

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Snap settled, finalize the selected page instantly without animation
                    val page = findCurrentPageFast(layoutManager)
                    setSelectedPage(page, animate = false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (mediaFiles.isEmpty()) return

                // Compute fractional page based on the first visible child offset
                val fractionalPosition = computeFractionalPosition(layoutManager)

                // Drive the dots smoothly with fractional position
                binding.dotsIndicator.updatePositionSmooth(fractionalPosition)

                // For the numeric counter, update when integer page changes
                val currentPage = fractionalPosition.roundToInt().coerceIn(0, mediaFiles.lastIndex)
                if (currentPage != lastReportedPage) {
                    lastReportedPage = currentPage
                    currentMediaPosition = currentPage
                    updateMediaCounter()
                }

                // If the user is flinging fast, also preemptively set selected without animation
                if (isUserFlinging) {
                    binding.dotsIndicator.setSelectedPosition(currentPage, animate = false)
                }
            }
        })

        Log.d(TAG, "RecyclerView setup complete")
    }

    private fun computeFractionalPosition(layoutManager: LinearLayoutManager): Float {
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        if (firstPos == RecyclerView.NO_POSITION) return currentMediaPosition.toFloat()

        val firstView = layoutManager.findViewByPosition(firstPos) ?: return firstPos.toFloat()

        val recyclerViewWidth = binding.rvMediaCarousel.width.takeIf { it > 0 } ?: return firstPos.toFloat()
        val childWidth = firstView.width.takeIf { it > 0 } ?: recyclerViewWidth

        // In a horizontal list, view.left goes negative as we scroll left -> right
        val offsetPx = -firstView.left.toFloat()
        val fraction = (offsetPx / childWidth).coerceIn(0f, 1f)

        return (firstPos + fraction).coerceIn(0f, (mediaFiles.size - 1).toFloat())
    }

    private fun findCurrentPageFast(layoutManager: LinearLayoutManager): Int {
        // Prefer completely visible if available, else center-most visible
        val completelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (completelyVisible != RecyclerView.NO_POSITION) return completelyVisible

        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return currentMediaPosition
        }

        // Pick the view whose center is closest to RecyclerView center
        val recyclerViewCenter = binding.rvMediaCarousel.width / 2
        var bestPosition = first
        var bestDistance = Int.MAX_VALUE

        for (position in first..last) {
            val view = layoutManager.findViewByPosition(position) ?: continue
            val viewCenter = (view.left + view.right) / 2
            val distance = abs(viewCenter - recyclerViewCenter)
            if (distance < bestDistance) {
                bestDistance = distance
                bestPosition = position
            }
        }
        return bestPosition
    }

    private fun setSelectedPage(page: Int, animate: Boolean) {
        val clampedPage = page.coerceIn(0, mediaFiles.lastIndex)
        currentMediaPosition = clampedPage
        binding.dotsIndicator.setSelectedPosition(clampedPage, animate)
        if (lastReportedPage != clampedPage) {
            lastReportedPage = clampedPage
            updateMediaCounter()
        }
    }

    private fun toggleImageScaleMode() {
        mediaAdapter.toggleImageScaleMode()
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
        setupExpandableCaption(post.caption)

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(post.downloadDate))
        binding.tvDownloadDate.text = "Downloaded on $formattedDate"
    }

    private fun setupExpandableCaption(caption: String?) {
        if (caption.isNullOrEmpty()) {
            binding.tvPostCaption.text = ""
            binding.tvPostCaption.visibility = View.GONE
            return
        }

        binding.tvPostCaption.visibility = View.VISIBLE

        if (caption.length <= maxCaptionLength) {
            binding.tvPostCaption.text = caption
            binding.tvPostCaption.movementMethod = null
        } else {
            updateCaptionDisplay(caption)
        }
    }

    private fun updateCaptionDisplay(fullCaption: String) {
        val captionToShow = if (isCaptionExpanded) {
            createClickableCaption(fullCaption, " ... less", false)
        } else {
            createClickableCaption(fullCaption.take(maxCaptionLength), " ... more", true)
        }

        binding.tvPostCaption.text = captionToShow
        binding.tvPostCaption.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun createClickableCaption(text: String, clickableText: String, isExpanding: Boolean): SpannableString {
        val fullText = text + clickableText
        val spannableString = SpannableString(fullText)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                isCaptionExpanded = isExpanding
                currentPost?.caption?.let { updateCaptionDisplay(it) }
                mediaAdapter.setCaptionExpanded(isCaptionExpanded)
                // When caption expands/collapses, re-affirm current selection without animation
                setSelectedPage(currentMediaPosition, animate = false)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@PostViewActivity, android.R.color.holo_blue_light)
                ds.isUnderlineText = false
            }
        }

        val startIndex = text.length
        val endIndex = fullText.length
        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return spannableString
    }

    private fun loadMediaFilesFromDatabase() {
        val postId = currentPost?.postId
        if (postId == null) {
            Log.e(TAG, "PostId is null, cannot load media")
            return
        }

        Log.d(TAG, "Loading media files from database for postId: $postId")

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
                Log.d(TAG, "Media paths from DB for postId $postId: $paths")

                val validFiles = mutableListOf<File>()

                for (path in paths) {
                    try {
                        if (path.startsWith("content://")) {
                            Log.d(TAG, "Skipping content URI: $path")
                            continue
                        }

                        val file = File(path)
                        if (file.exists() && file.canRead() && file.length() > 0) {
                            validFiles.add(file)
                            Log.d(TAG, "Added valid file: ${file.name}")
                        } else {
                            Log.w(TAG, "Invalid file: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing path: $path", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Updating UI with ${validFiles.size} valid files")
                    currentPost = post
                    mediaFiles.clear()
                    mediaFiles.addAll(validFiles)
                    updateUIWithFiles()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading media from database", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PostViewActivity, "Error loading media files: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUIWithFiles() {
        Log.d(TAG, "Updating UI with ${mediaFiles.size} files")

        mediaAdapter = MediaCarouselAdapter(
            mediaFiles = mediaFiles,
            onMediaClick = {
                Log.d(TAG, "Media clicked - toggling scale mode")
                toggleImageScaleMode()
            },
            onVideoPlayPause = { isPlaying ->
                Log.d(TAG, "Video play/pause: $isPlaying")
            }
        )

        mediaAdapter.setCaptionExpanded(isCaptionExpanded)
        binding.rvMediaCarousel.adapter = mediaAdapter

        setupDotsIndicator()
        updateMediaCounter()
        calculateTotalFileSize()

        if (mediaFiles.isEmpty()) {
            Log.w(TAG, "No valid media files found")
            Toast.makeText(this, "No media files found for this post", Toast.LENGTH_LONG).show()
        } else {
            Log.d(TAG, "Successfully loaded ${mediaFiles.size} media files")
        }
    }

    private fun setupDotsIndicator() {
        val fileCount = mediaFiles.size

        if (fileCount <= 1) {
            binding.dotsIndicator.visibility = View.GONE
        } else {
            binding.dotsIndicator.visibility = View.VISIBLE
            binding.dotsIndicator.setDotCount(fileCount)
            // Initialize immediately at current position with no animation
            binding.dotsIndicator.setSelectedPosition(currentMediaPosition, animate = false)
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
        binding.tvFileSize.text = String.format(Locale.getDefault(), "%.1f MB", sizeInMB)
        Log.d(TAG, "Total file size calculated: %.1f MB".format(sizeInMB))
    }

    private fun updateMediaCounter() {
        val totalFiles = mediaFiles.size

        if (totalFiles == 0) {
            binding.tvMediaCounter.text = "0 / 0"
            return
        }

        val position = (currentMediaPosition + 1).coerceAtMost(totalFiles.coerceAtLeast(1))
        binding.tvMediaCounter.text = "$position / $totalFiles"
    }

    private fun shareCurrentMedia() {
        if (mediaFiles.isEmpty()) {
            Toast.makeText(this, "No media to share", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentMediaPosition < mediaFiles.size) {
            val currentFile = mediaFiles[currentMediaPosition]

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