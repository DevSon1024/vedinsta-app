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
import androidx.core.view.doOnNextLayout
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
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.work.WorkInfo
import androidx.work.WorkManager
import java.util.UUID

class DownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadBinding
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var notificationManager: VedInstaNotificationManager
    private val mediaList = mutableListOf<ImageCard>()

    private var workRequestIds: List<UUID> = emptyList()
    private val selectedItems = mutableSetOf<Int>()

    // Position tracking for real-time updates
    private var currentPosition = 0
    private var lastReportedPage = -1
    private var isUserFlinging = false

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
        Log.d(TAG, "Setting up RecyclerView with real-time dots indicator")

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

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerViewImages.apply {
            adapter = imageAdapter
            this.layoutManager = layoutManager
            itemAnimator = null // Remove default item animation for snappier updates
        }

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.recyclerViewImages)

        // Ensure dots prepare once sizes are laid out
        binding.recyclerViewImages.doOnNextLayout {
            setupDotsIndicator()
        }

        // High-frequency, real-time updates using onScrolled
        binding.recyclerViewImages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isUserFlinging = newState == RecyclerView.SCROLL_STATE_SETTLING

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Snap settled, finalize the selected page instantly without animation
                    val page = findCurrentPageFast(layoutManager)
                    setSelectedPage(page, animate = false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (mediaList.isEmpty()) return

                // Compute fractional page based on the first visible child offset
                val fractionalPosition = computeFractionalPosition(layoutManager)

                // Drive the dots smoothly with fractional position
                binding.dotsIndicator.updatePositionSmooth(fractionalPosition)

                // Update current position when integer page changes
                val currentPage = fractionalPosition.roundToInt().coerceIn(0, mediaList.lastIndex)
                if (currentPage != lastReportedPage) {
                    lastReportedPage = currentPage
                    currentPosition = currentPage
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
        if (firstPos == RecyclerView.NO_POSITION) return currentPosition.toFloat()

        val firstView = layoutManager.findViewByPosition(firstPos) ?: return firstPos.toFloat()

        val recyclerViewWidth = binding.recyclerViewImages.width.takeIf { it > 0 } ?: return firstPos.toFloat()
        val childWidth = firstView.width.takeIf { it > 0 } ?: recyclerViewWidth

        // In a horizontal list, view.left goes negative as we scroll left -> right
        val offsetPx = -firstView.left.toFloat()
        val fraction = (offsetPx / childWidth).coerceIn(0f, 1f)

        return (firstPos + fraction).coerceIn(0f, (mediaList.size - 1).toFloat())
    }

    private fun findCurrentPageFast(layoutManager: LinearLayoutManager): Int {
        // Prefer completely visible if available, else center-most visible
        val completelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (completelyVisible != RecyclerView.NO_POSITION) return completelyVisible

        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return currentPosition
        }

        // Pick the view whose center is closest to RecyclerView center
        val recyclerViewCenter = binding.recyclerViewImages.width / 2
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
        val clampedPage = page.coerceIn(0, mediaList.lastIndex)
        currentPosition = clampedPage
        binding.dotsIndicator.setSelectedPosition(clampedPage, animate)
        if (lastReportedPage != clampedPage) {
            lastReportedPage = clampedPage
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

    private fun setupDotsIndicator() {
        val mediaCount = mediaList.size

        if (mediaCount <= 1) {
            binding.dotsIndicator.visibility = View.GONE
        } else {
            binding.dotsIndicator.visibility = View.VISIBLE
            binding.dotsIndicator.setDotCount(mediaCount)
            // Initialize immediately at current position with no animation
            binding.dotsIndicator.setSelectedPosition(currentPosition, animate = false)
        }

        Log.d(TAG, "Dots indicator setup complete for $mediaCount items")
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
            Log.d(TAG, "Enqueueing download for ${selectedMediaList.size} selected items")

            // Disable button immediately
            binding.btnDownload.isEnabled = false
            binding.btnDownload.alpha = 0.6f
            binding.btnDownload.text = "ENQUEUED..."

            // Use Application context for WorkManager
            val applicationContext = this.applicationContext

            // Enqueue work using the application method
            workRequestIds = (application as VedInstaApplication).enqueueMultipleDownloads(
                applicationContext,
                selectedMediaList,
                postId
            )

            if (workRequestIds.isNotEmpty()) {
                Toast.makeText(this, "Download started in background", Toast.LENGTH_SHORT).show()
                // Optionally observe the combined progress here if needed,
                // or rely on the Application class observer and individual notifications.
                // observeCombinedWorkProgress(workRequestIds)

                // Finish activity after enqueuing
                finish()
            } else {
                Toast.makeText(this, "Failed to start downloads", Toast.LENGTH_SHORT).show()
                // Re-enable button on failure
                updateDownloadButton() // Reset button state based on selection
            }

        } else {
            Toast.makeText(this, "Please select at least one item to download", Toast.LENGTH_SHORT).show()
        }
    }

    private fun observeCombinedWorkProgress(workIds: List<UUID>) {
        val workManager = WorkManager.getInstance(this)
        workManager.getWorkInfosForUniqueWorkLiveData(postId ?: VedInstaApplication.UNIQUE_DOWNLOAD_WORK_NAME)
            .observe(this) { workInfos ->
                if (workInfos == null || workInfos.isEmpty()) return@observe

                val relevantWorkInfos = workInfos.filter { it.id in workIds }
                if (relevantWorkInfos.isEmpty()) return@observe

                var completedCount = 0
                var totalProgress = 0
                var runningCount = 0

                relevantWorkInfos.forEach { workInfo ->
                    if (workInfo.state.isFinished) {
                        completedCount++
                    } else if (workInfo.state == WorkInfo.State.RUNNING) {
                        runningCount++
                        totalProgress += workInfo.progress.getInt(EnhancedDownloadManager.PROGRESS, 0)
                    }
                    // Handle other states if needed (FAILED, CANCELLED)
                }

                val averageProgress = if (runningCount > 0) totalProgress / runningCount else 0

                // Update UI - e.g., show overall progress if desired
                // binding.someProgressBar.progress = averageProgress
                // binding.someTextView.text = "Downloading $runningCount files ($averageProgress%)... $completedCount completed."

                if (completedCount == workIds.size) {
                    // All finished
                    Log.d(TAG, "All downloads completed for this batch.")
                    // Can potentially finish activity here if not already done
                    // finish()
                }
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

                    // Setup dots indicator with real-time functionality
                    setupDotsIndicator()

                    imageAdapter.notifyDataSetChanged()
                    updateDownloadButton()
                    updateSelectAllButton()

                    Log.d(TAG, "Successfully processed ${mediaList.size} media items with real-time dots")
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