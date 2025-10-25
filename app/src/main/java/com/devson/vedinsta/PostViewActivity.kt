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
    // Make currentPost nullable and initialize later
    private var currentPost: DownloadedPost? = null
    private var mediaFiles: MutableList<File> = mutableListOf()

    // Position tracking
    private var currentMediaPosition = 0
    private var lastReportedPage = -1
    private var isUserFlinging = false

    // Caption state
    private var isCaptionExpanded = false
    private val maxCaptionLength = 100

    // Store postId from intent separately
    private var intentPostId: String? = null

    companion object {
        private const val TAG = "PostViewActivity"
        const val EXTRA_POST_ID = "post_id"
        // Keep other extras for initial display if needed, but DB is primary source
        const val EXTRA_POST_URL = "post_url" // Optional
        const val EXTRA_THUMBNAIL_PATH = "thumbnail_path" // Optional
        const val EXTRA_TOTAL_IMAGES = "total_images" // Optional
        const val EXTRA_HAS_VIDEO = "has_video" // Optional
        const val EXTRA_DOWNLOAD_DATE = "download_date" // Optional
        const val EXTRA_USERNAME = "username" // Optional initial value
        const val EXTRA_CAPTION = "caption" // Optional initial value


        fun createIntent(context: Context, post: DownloadedPost): Intent {
            return Intent(context, PostViewActivity::class.java).apply {
                // Only pass the postId, load everything else from DB
                putExtra(EXTRA_POST_ID, post.postId)
                // Optionally pass initial values if needed for faster initial display
                putExtra(EXTRA_USERNAME, post.username)
                putExtra(EXTRA_CAPTION, post.caption)
                putExtra(EXTRA_THUMBNAIL_PATH, post.thumbnailPath)
                putExtra(EXTRA_TOTAL_IMAGES, post.totalImages)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "PostViewActivity onCreate started")

        // 1. Get postId from Intent
        intentPostId = intent.getStringExtra(EXTRA_POST_ID)
        Log.d(TAG, "Received postId from Intent: $intentPostId")

        if (intentPostId == null) {
            Log.e(TAG, "PostId is null in Intent, finishing activity")
            Toast.makeText(this, "Error: Post ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 2. Setup UI elements (RecyclerView, ClickListeners)
        setupRecyclerView()
        setupClickListeners()

        // 3. Show initial placeholder/loading state (Optional)
        // You could show temporary username/caption from Intent if passed
        val initialUsername = intent.getStringExtra(EXTRA_USERNAME) ?: "Loading..."
        val initialCaption = intent.getStringExtra(EXTRA_CAPTION) // Nullable
        binding.tvUsername.text = "@$initialUsername"
        setupExpandableCaption(initialCaption) // Show initial caption if available


        // 4. Load full post data and media files from Database
        loadDataFromDatabase(intentPostId!!) // Use non-null postId
    }

    // Removed extractPostData - We load directly from DB

    private fun setupRecyclerView() {
        Log.d(TAG, "Setting up RecyclerView")

        // Initialize adapter with empty list first
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

        // Dots setup will happen AFTER data loading completes and updates the adapter

        // High-frequency, real-time updates using onScrolled
        binding.rvMediaCarousel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isUserFlinging = newState == RecyclerView.SCROLL_STATE_SETTLING

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val page = findCurrentPageFast(layoutManager)
                    // Only update if mediaFiles is not empty
                    if (mediaFiles.isNotEmpty()) {
                        setSelectedPage(page, animate = false)
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (mediaFiles.isEmpty()) return // Check moved here

                val fractionalPosition = computeFractionalPosition(layoutManager)
                binding.dotsIndicator.updatePositionSmooth(fractionalPosition)

                val currentPage = fractionalPosition.roundToInt().coerceIn(0, mediaFiles.lastIndex)
                if (currentPage != lastReportedPage) {
                    lastReportedPage = currentPage
                    currentMediaPosition = currentPage
                    updateMediaCounter() // Update counter based on potentially loaded files
                }

                if (isUserFlinging) {
                    if (mediaFiles.isNotEmpty()) { // Check before accessing lastIndex
                        binding.dotsIndicator.setSelectedPosition(currentPage.coerceIn(0, mediaFiles.lastIndex), animate = false)
                    }
                }
            }
        })

        Log.d(TAG, "RecyclerView setup complete (Adapter initialized empty)")
    }

    // computeFractionalPosition remains the same
    private fun computeFractionalPosition(layoutManager: LinearLayoutManager): Float {
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        if (firstPos == RecyclerView.NO_POSITION) return currentMediaPosition.toFloat()

        val firstView = layoutManager.findViewByPosition(firstPos) ?: return firstPos.toFloat()

        val recyclerViewWidth = binding.rvMediaCarousel.width.takeIf { it > 0 } ?: return firstPos.toFloat()
        val childWidth = firstView.width.takeIf { it > 0 } ?: recyclerViewWidth

        // In a horizontal list, view.left goes negative as we scroll left -> right
        val offsetPx = -firstView.left.toFloat()
        val fraction = (offsetPx / childWidth).coerceIn(0f, 1f)

        // Ensure calculation uses the current size of mediaFiles
        val lastIndexFloat = (mediaFiles.size - 1).coerceAtLeast(0).toFloat()
        return (firstPos + fraction).coerceIn(0f, lastIndexFloat)
    }


    // findCurrentPageFast remains the same
    private fun findCurrentPageFast(layoutManager: LinearLayoutManager): Int {
        // Prefer completely visible if available, else center-most visible
        val completelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (completelyVisible != RecyclerView.NO_POSITION) return completelyVisible

        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return currentMediaPosition // Return current if no visible items
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
        // Ensure the returned position is valid for the current mediaFiles list
        return bestPosition.coerceIn(0, mediaFiles.lastIndex.coerceAtLeast(0))
    }


    private fun setSelectedPage(page: Int, animate: Boolean) {
        if (mediaFiles.isEmpty()) return // Don't update if no media yet

        val clampedPage = page.coerceIn(0, mediaFiles.lastIndex)
        currentMediaPosition = clampedPage
        binding.dotsIndicator.setSelectedPosition(clampedPage, animate)
        if (lastReportedPage != clampedPage) {
            lastReportedPage = clampedPage
            updateMediaCounter()
        }
    }

    private fun toggleImageScaleMode() {
        // Ensure adapter is initialized and has items
        if (::mediaAdapter.isInitialized && mediaFiles.isNotEmpty()) {
            mediaAdapter.toggleImageScaleMode()
        }
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
        // Use currentPost safely
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

    // This function now uses the fully loaded currentPost object
    private fun setupPostInfo() {
        val post = currentPost ?: run {
            Log.e(TAG, "setupPostInfo called but currentPost is null")
            return
        }
        Log.d(TAG, "Setting up post info for DB loaded post: ${post.username}")

        binding.tvUsername.text = "@${post.username}" // Use username from DB post
        setupExpandableCaption(post.caption) // Use caption from DB post

        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(post.downloadDate))
        binding.tvDownloadDate.text = "Downloaded on $formattedDate"
    }

    // setupExpandableCaption remains the same
    private fun setupExpandableCaption(caption: String?) {
        if (caption.isNullOrEmpty()) {
            binding.tvPostCaption.text = ""
            binding.tvPostCaption.visibility = View.GONE
            return
        }

        binding.tvPostCaption.visibility = View.VISIBLE

        if (caption.length <= maxCaptionLength) {
            binding.tvPostCaption.text = caption
            // Disable LinkMovementMethod if not expandable to allow normal text selection
            binding.tvPostCaption.movementMethod = null
            binding.tvPostCaption.isClickable = false
            binding.tvPostCaption.isFocusable = false

        } else {
            updateCaptionDisplay(caption)
            binding.tvPostCaption.isClickable = true
            binding.tvPostCaption.isFocusable = true
        }
    }


    // updateCaptionDisplay remains the same
    private fun updateCaptionDisplay(fullCaption: String) {
        val captionToShow = if (isCaptionExpanded) {
            createClickableCaption(fullCaption, " ... less", false)
        } else {
            createClickableCaption(fullCaption.take(maxCaptionLength), " ... more", true)
        }

        binding.tvPostCaption.text = captionToShow
        binding.tvPostCaption.movementMethod = LinkMovementMethod.getInstance()
    }


    // createClickableCaption remains the same
    private fun createClickableCaption(text: String, clickableText: String, isExpanding: Boolean): SpannableString {
        val fullText = text + clickableText
        val spannableString = SpannableString(fullText)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                isCaptionExpanded = isExpanding
                currentPost?.caption?.let { updateCaptionDisplay(it) }
                // Notify adapter about caption state change AFTER updating text view
                if (::mediaAdapter.isInitialized) {
                    mediaAdapter.setCaptionExpanded(isCaptionExpanded)
                }
                // When caption expands/collapses, re-affirm current selection without animation
                // Ensure mediaFiles is not empty before accessing lastIndex
                if (mediaFiles.isNotEmpty()) {
                    setSelectedPage(currentMediaPosition.coerceIn(0, mediaFiles.lastIndex), animate = false)
                }
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


    // Renamed loadMediaFilesFromDatabase to loadDataFromDatabase
    private fun loadDataFromDatabase(postId: String) {
        Log.d(TAG, "Loading post data and media files from database for postId: $postId")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val postFromDb = db.downloadedPostDao().getPostById(postId)

                Log.d(TAG, "Post retrieved from DB: ID=${postFromDb?.postId}, User=${postFromDb?.username}, Caption=${postFromDb?.caption?.take(20)}...")

                if (postFromDb == null) {
                    Log.e(TAG, "No post found in database for ID: $postId")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@PostViewActivity, "Error: Post not found in database", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    return@launch
                }

                // --- Update currentPost with data from DB ---
                currentPost = postFromDb
                // --- ---

                val paths = postFromDb.mediaPaths
                Log.d(TAG, "Media paths from DB for postId $postId: $paths")

                val validFiles = mutableListOf<File>()
                for (path in paths) {
                    try {
                        if (path.startsWith("content://")) {
                            Log.w(TAG, "Skipping content URI (not directly usable): $path")
                            continue // Skip content URIs for now
                        }
                        val file = File(path)
                        if (file.exists() && file.canRead() && file.length() > 0) {
                            validFiles.add(file)
                            Log.d(TAG, "Added valid file: ${file.name}")
                        } else {
                            Log.w(TAG, "Invalid or inaccessible file: ${file.absolutePath}, Exists=${file.exists()}, CanRead=${file.canRead()}, Length=${file.length()}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing path: $path", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Updating UI with DB data and ${validFiles.size} valid files")
                    mediaFiles.clear()
                    mediaFiles.addAll(validFiles)

                    // --- Update UI elements that depend on DB data ---
                    setupPostInfo() // Update username, caption, date using currentPost
                    updateUIWithFiles() // Update RecyclerView adapter, dots, counter, file size
                    // --- ---
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading data from database", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PostViewActivity, "Error loading post details: ${e.message}", Toast.LENGTH_LONG).show()
                    finish() // Finish if DB load fails critically
                }
            }
        }
    }


    // updateUIWithFiles remains mostly the same, but relies on mediaFiles being updated first
    private fun updateUIWithFiles() {
        Log.d(TAG, "Updating RecyclerView adapter and related UI for ${mediaFiles.size} files")

        // Update adapter's data (important if adapter was already created)
        mediaAdapter = MediaCarouselAdapter( // Recreate or update adapter
            mediaFiles = mediaFiles,
            onMediaClick = { toggleImageScaleMode() },
            onVideoPlayPause = { /* Handle if needed */ }
        )
        mediaAdapter.setCaptionExpanded(isCaptionExpanded) // Ensure caption state is synced
        binding.rvMediaCarousel.adapter = mediaAdapter // Set the adapter again


        // Reset scroll position and page tracking if files changed
        currentMediaPosition = 0
        lastReportedPage = -1
        binding.rvMediaCarousel.scrollToPosition(0)


        // Setup/Update dots, counter, file size
        setupDotsIndicator() // Now call setupDotsIndicator AFTER mediaFiles is populated
        updateMediaCounter() // Update counter based on loaded files
        calculateTotalFileSize()

        if (mediaFiles.isEmpty()) {
            Log.w(TAG, "No valid media files found after DB load")
            Toast.makeText(this, "No media files found for this post", Toast.LENGTH_LONG).show()
            // Optionally handle empty state UI here
        } else {
            Log.d(TAG, "Successfully updated UI for ${mediaFiles.size} media files")
            // Ensure the first page is correctly selected visually
            setSelectedPage(0, animate = false)
        }
    }

    // setupDotsIndicator remains the same
    private fun setupDotsIndicator() {
        val fileCount = mediaFiles.size

        if (fileCount <= 1) {
            binding.dotsIndicator.visibility = View.GONE
        } else {
            binding.dotsIndicator.visibility = View.VISIBLE
            binding.dotsIndicator.setDotCount(fileCount)
            // Initialize immediately at current position with no animation
            // Ensure currentMediaPosition is valid for the list size
            val initialPos = currentMediaPosition.coerceIn(0, fileCount -1)
            binding.dotsIndicator.setSelectedPosition(initialPos, animate = false)
        }
    }

    // calculateTotalFileSize remains the same
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

    // updateMediaCounter remains the same
    private fun updateMediaCounter() {
        val totalFiles = mediaFiles.size

        if (totalFiles == 0) {
            binding.tvMediaCounter.text = "0 / 0"
            return
        }

        // Ensure position is 1-based and within bounds
        val position = (currentMediaPosition + 1).coerceIn(1, totalFiles)
        binding.tvMediaCounter.text = "$position / $totalFiles"
    }

    // shareCurrentMedia remains the same
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
                Toast.makeText(this, "Error sharing media: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "Share error: currentMediaPosition ($currentMediaPosition) is out of bounds for mediaFiles size (${mediaFiles.size})")
            Toast.makeText(this, "Error sharing media: Invalid position", Toast.LENGTH_SHORT).show()
        }
    }


    // deletePost remains the same
    private fun deletePost() {
        // Use currentPost safely
        val postToDelete = currentPost
        if (postToDelete == null) {
            Toast.makeText(this, "Cannot delete, post data not loaded", Toast.LENGTH_SHORT).show()
            return
        }

        if (mediaFiles.isEmpty()) { // Check mediaFiles which holds the validated files
            Log.w(TAG, "Attempting to delete post ${postToDelete.postId}, but no valid media files were loaded.")
            // Allow deleting DB entry even if files are missing
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post and its downloaded media (${mediaFiles.size} files)? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val db = AppDatabase.getDatabase(applicationContext)

                        Log.d(TAG, "Deleting post: ${postToDelete.postId}")

                        var deletedFileCount = 0
                        // Iterate over the mediaPaths stored in the DB record
                        postToDelete.mediaPaths.forEach { path ->
                            try {
                                if (path.startsWith("content://")) {
                                    Log.w(TAG, "Cannot directly delete content URI: $path")
                                    // Optionally try to resolve and delete if possible, but might fail
                                    // val file = //... resolve URI to file if possible
                                    // if (file.exists() && file.delete()) deletedFileCount++
                                    return@forEach // Continue to next path
                                }
                                val file = File(path)
                                if (file.exists() && file.delete()) {
                                    deletedFileCount++
                                    Log.d(TAG, "Deleted file: ${file.absolutePath}")
                                } else if (file.exists()) {
                                    Log.w(TAG, "Failed to delete file: ${file.absolutePath}")
                                } else {
                                    Log.w(TAG, "File not found for deletion: ${file.absolutePath}")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error deleting file: $path", e)
                            }
                        }


                        db.downloadedPostDao().deleteByPostId(postToDelete.postId)
                        Log.d(TAG, "Deleted post from database: ${postToDelete.postId}")

                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@PostViewActivity, "Post deleted successfully ($deletedFileCount files)", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK) // Notify previous activity if needed
                            finish()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deleting post from DB or files", e)
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