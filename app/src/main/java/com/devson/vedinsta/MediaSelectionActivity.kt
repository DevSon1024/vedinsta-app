package com.devson.vedinsta

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chaquo.python.Python
import com.devson.vedinsta.adapters.MediaSelectionAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MediaSelectionActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var downloadButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var adapter: MediaSelectionAdapter
    private var instagramUrl: String? = null

    companion object {
        const val EXTRA_INSTAGRAM_URL = "instagram_url"
        private const val TAG = "MediaSelectionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_selection)

        // Initialize views
        recyclerView = findViewById(R.id.mediaSelectionRecyclerView)
        downloadButton = findViewById(R.id.downloadSelectedButton)
        progressBar = findViewById(R.id.loadingProgressBar)
        statusText = findViewById(R.id.statusTextView)

        // Setup toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Select Media"

        // Get Instagram URL from intent
        instagramUrl = intent.getStringExtra(EXTRA_INSTAGRAM_URL)

        if (instagramUrl == null) {
            Toast.makeText(this, "Invalid URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup RecyclerView
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = MediaSelectionAdapter()
        recyclerView.adapter = adapter

        // Load media
        loadMedia()

        // Setup download button
        downloadButton.setOnClickListener {
            downloadSelectedMedia()
        }
    }

    private fun loadMedia() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                statusText.text = "Loading media..."

                val mediaList = withContext(Dispatchers.IO) {
                    fetchMediaFromUrl(instagramUrl!!)
                }

                if (mediaList.isEmpty()) {
                    statusText.text = "No media found"
                    Toast.makeText(this@MediaSelectionActivity, "No media found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@launch
                }

                adapter.submitList(mediaList)
                showLoading(false)
                updateDownloadButton()

            } catch (e: Exception) {
                Log.e(TAG, "Error loading media", e)
                statusText.text = "Error loading media"
                Toast.makeText(this@MediaSelectionActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
        }
    }

    private fun fetchMediaFromUrl(url: String): List<MediaSelectionAdapter.MediaItem> {
        try {
            val python = Python.getInstance()
            val module = python.getModule("insta_downloader")
            val result = module.callAttr("get_media_urls", url)
            val resultString = result?.toString() ?: return emptyList()

            val jsonResult = JSONObject(resultString)
            val status = jsonResult.optString("status", "error")

            if (status != "success") {
                return emptyList()
            }

            val mediaArray = jsonResult.optJSONArray("media") ?: return emptyList()
            val mediaList = mutableListOf<MediaSelectionAdapter.MediaItem>()

            for (i in 0 until mediaArray.length()) {
                val mediaObj = mediaArray.getJSONObject(i)
                mediaList.add(
                    MediaSelectionAdapter.MediaItem(
                        url = mediaObj.getString("url"),
                        type = mediaObj.optString("type", "image"),
                        index = mediaObj.optInt("index", i + 1),
                        isSelected = false
                    )
                )
            }

            return mediaList
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching media", e)
            return emptyList()
        }
    }

    private fun updateDownloadButton() {
        val selectedCount = adapter.getSelectedCount()
        downloadButton.isEnabled = selectedCount > 0
        downloadButton.text = if (selectedCount > 0) {
            "Download ($selectedCount)"
        } else {
            "Select items to download"
        }
    }

    private fun downloadSelectedMedia() {
        val selectedItems = adapter.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val app = application as VedInstaApplication
                app.downloadSelectedMedia(selectedItems, instagramUrl ?: "")
                Toast.makeText(this@MediaSelectionActivity, "Downloading ${selectedItems.size} item(s)", Toast.LENGTH_SHORT).show()
                finish()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting download", e)
                Toast.makeText(this@MediaSelectionActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        downloadButton.isEnabled = !show
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}