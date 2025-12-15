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

    private var currentPosition = 0
    private var lastReportedPage = -1
    private var isUserFlinging = false

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

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        notificationManager = VedInstaNotificationManager.getInstance(this)

        // FIX: Clear the "Select Options" notification if it exists
        notificationManager.cancelMultipleContentNotification()

        requestNotificationPermission()

        postUrl = intent.getStringExtra("POST_URL")
        postId = intent.getStringExtra("POST_ID")

        setupRecyclerView()
        setupClickListeners()

        val resultJson = intent.getStringExtra("RESULT_JSON")
        if (resultJson != null) {
            handlePythonResult(resultJson)
        } else if (postUrl != null) {
            fetchDataFromUrl(postUrl!!)
        } else {
            Toast.makeText(this, "Error: No data received", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchDataFromUrl(url: String) {
        binding.dotsIndicator.visibility = View.GONE
        Toast.makeText(this, "Loading items...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val python = com.chaquo.python.Python.getInstance()
                val module = python.getModule("insta_downloader")
                val result = module.callAttr("get_media_urls", url).toString()

                launch(Dispatchers.Main) {
                    handlePythonResult(result)
                }
            } catch (e: Exception) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@DownloadActivity, "Failed to load: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
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

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.recyclerViewImages.apply {
            adapter = imageAdapter
            this.layoutManager = layoutManager
            itemAnimator = null
        }

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.recyclerViewImages)

        binding.recyclerViewImages.doOnNextLayout {
            setupDotsIndicator()
        }

        binding.recyclerViewImages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                isUserFlinging = newState == RecyclerView.SCROLL_STATE_SETTLING

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val page = findCurrentPageFast(layoutManager)
                    setSelectedPage(page, animate = false)
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (mediaList.isEmpty()) return

                val fractionalPosition = computeFractionalPosition(layoutManager)
                binding.dotsIndicator.updatePositionSmooth(fractionalPosition)

                val currentPage = fractionalPosition.roundToInt().coerceIn(0, mediaList.lastIndex)
                if (currentPage != lastReportedPage) {
                    lastReportedPage = currentPage
                    currentPosition = currentPage
                }

                if (isUserFlinging) {
                    binding.dotsIndicator.setSelectedPosition(currentPage, animate = false)
                }
            }
        })
    }

    private fun computeFractionalPosition(layoutManager: LinearLayoutManager): Float {
        val firstPos = layoutManager.findFirstVisibleItemPosition()
        if (firstPos == RecyclerView.NO_POSITION) return currentPosition.toFloat()

        val firstView = layoutManager.findViewByPosition(firstPos) ?: return firstPos.toFloat()
        val recyclerViewWidth = binding.recyclerViewImages.width.takeIf { it > 0 } ?: return firstPos.toFloat()
        val childWidth = firstView.width.takeIf { it > 0 } ?: recyclerViewWidth
        val offsetPx = -firstView.left.toFloat()
        val fraction = (offsetPx / childWidth).coerceIn(0f, 1f)

        return (firstPos + fraction).coerceIn(0f, (mediaList.size - 1).toFloat())
    }

    private fun findCurrentPageFast(layoutManager: LinearLayoutManager): Int {
        val completelyVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
        if (completelyVisible != RecyclerView.NO_POSITION) return completelyVisible

        val first = layoutManager.findFirstVisibleItemPosition()
        val last = layoutManager.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return currentPosition
        }

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
        binding.btnSelectAll.setOnClickListener {
            toggleSelectAll()
        }

        binding.btnDownload.setOnClickListener {
            startSelectedDownload()
        }
    }

    private fun toggleSelectAll() {
        if (selectedItems.size == mediaList.size) {
            selectedItems.clear()
        } else {
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
            binding.dotsIndicator.setSelectedPosition(currentPosition, animate = false)
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
            binding.btnDownload.isEnabled = false
            binding.btnDownload.alpha = 0.6f
            binding.btnDownload.text = "ENQUEUED..."

            val applicationContext = this.applicationContext

            workRequestIds = (application as VedInstaApplication).enqueueMultipleDownloads(
                applicationContext,
                selectedMediaList,
                postId ?: postUrl,
                postCaption
            )

            if (workRequestIds.isNotEmpty()) {
                Toast.makeText(this, "Download started in background", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "Failed to start downloads", Toast.LENGTH_SHORT).show()
                updateDownloadButton()
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
                    postUsername = result.optString("username", "unknown")
                    postCaption = result.optString("caption", null)

                    if (postId == null) {
                        postId = result.optString("shortcode", null)
                    }

                    val mediaArray = result.getJSONArray("media")

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

                    selectedItems.clear()
                    selectedItems.addAll(0 until mediaList.size)

                    setupDotsIndicator()

                    imageAdapter.notifyDataSetChanged()
                    updateDownloadButton()
                    updateSelectAllButton()
                }
                else -> {
                    val message = result.optString("message", "Unknown error")
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