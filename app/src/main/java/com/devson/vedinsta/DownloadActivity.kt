package com.devson.vedinsta

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider  // Add this import
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ActivityDownloadBinding
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class DownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDownloadBinding
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var viewModel: MainViewModel  // Add this
    private val mediaList = mutableListOf<ImageCard>()

    // Get data from intent
    private var postUrl: String? = null
    private var postId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Get data from intent
        postUrl = intent.getStringExtra("POST_URL")
        postId = intent.getStringExtra("POST_ID")

        setupRecyclerView()

        val resultJson = intent.getStringExtra("RESULT_JSON")
        if (resultJson != null) {
            handlePythonResult(resultJson)
        } else {
            Toast.makeText(this, "Error: No data received", Toast.LENGTH_SHORT).show()
            finish()
        }

        // Download button logic
        binding.btnDownloadSelected.setOnClickListener {
            val selected = imageAdapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val downloadedFiles = (application as VedInstaApplication).downloadFiles(this@DownloadActivity, selected)

                    // Save to database after successful download
                    if (downloadedFiles.isNotEmpty()) {
                        saveDownloadedPost(postId, postUrl ?: "", downloadedFiles)
                    }
                }
            } else {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownloadAll.setOnClickListener {
            if (mediaList.isNotEmpty()) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val downloadedFiles = (application as VedInstaApplication).downloadFiles(this@DownloadActivity, mediaList)

                    // Save to database after successful download
                    if (downloadedFiles.isNotEmpty()) {
                        saveDownloadedPost(postId, postUrl ?: "", downloadedFiles)
                    }
                }
            } else {
                Toast.makeText(this, "No media to download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(mediaList)
        binding.recyclerViewImages.apply {
            adapter = imageAdapter
            layoutManager = GridLayoutManager(this@DownloadActivity, 2)
        }
    }

    private fun handlePythonResult(jsonString: String) {
        try {
            val result = JSONObject(jsonString)
            when (result.getString("status")) {
                "success" -> {
                    val username = result.getString("username")
                    val mediaArray = result.getJSONArray("media")

                    for (i in 0 until mediaArray.length()) {
                        val mediaObject = mediaArray.getJSONObject(i)
                        mediaList.add(
                            ImageCard(
                                url = mediaObject.getString("url"),
                                type = mediaObject.getString("type"),
                                username = username
                            )
                        )
                    }
                    imageAdapter.notifyDataSetChanged()
                }
                else -> {
                    val message = result.getString("message")
                    Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to parse result: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Fixed method to save downloaded post to database
    private fun saveDownloadedPost(postId: String?, postUrl: String, downloadedFiles: List<String>) {
        if (postId != null && downloadedFiles.isNotEmpty()) {
            val downloadedPost = DownloadedPost(
                postId = postId,
                postUrl = postUrl,
                thumbnailPath = downloadedFiles.first(), // First image as thumbnail
                totalImages = downloadedFiles.size,
                downloadDate = System.currentTimeMillis(),
                hasVideo = downloadedFiles.any { it.endsWith(".mp4") }
            )

            // Save to database via ViewModel
            viewModel.insertDownloadedPost(downloadedPost)

            // Show success message on UI thread
            runOnUiThread {
                Toast.makeText(this, "Download completed and saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}