// src/main/java/com/devson/vedinsta/MainActivity.kt
package com.devson.vedinsta

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.devson.vedinsta.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imageAdapter: ImageAdapter
    private val mediaList = mutableListOf<ImageCard>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()

        binding.btnFetch.setOnClickListener {
            val url = binding.etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                fetchMedia(url)
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownloadSelected.setOnClickListener {
            val selected = imageAdapter.getSelectedItems()
            if (selected.isNotEmpty()) {
                downloadFiles(selected)
            } else {
                Toast.makeText(this, "No items selected", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDownloadAll.setOnClickListener {
            if (mediaList.isNotEmpty()) {
                downloadFiles(mediaList)
            } else {
                Toast.makeText(this, "No media to download", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter(mediaList)
        binding.recyclerViewImages.apply {
            adapter = imageAdapter
            layoutManager = GridLayoutManager(this@MainActivity, 2) // 2 columns
        }
    }

    // In src/main/java/com/devson/vedinsta/MainActivity.kt

    private fun fetchMedia(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewImages.visibility = View.GONE
        mediaList.clear()
        imageAdapter.notifyDataSetChanged()

        lifecycleScope.launch(Dispatchers.IO) {
            // We no longer need to check if Python is started here!
            val py = Python.getInstance()
            val pyModule = py.getModule("insta_downloader")

            // Call the Python function and get the JSON string result
            val resultJson = pyModule.callAttr("get_media_urls", url).toString()

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                binding.recyclerViewImages.visibility = View.VISIBLE
                handlePythonResult(resultJson)
            }
        }
    }

    private fun handlePythonResult(jsonString: String) {
        try {
            val result = JSONObject(jsonString)
            when (result.getString("status")) {
                "success" -> {
                    val mediaArray = result.getJSONArray("media")
                    for (i in 0 until mediaArray.length()) {
                        val mediaObject = mediaArray.getJSONObject(i)
                        mediaList.add(
                            ImageCard(
                                url = mediaObject.getString("url"),
                                type = mediaObject.getString("type")
                            )
                        )
                    }
                    imageAdapter.notifyDataSetChanged()
                }
                "private" -> {
                    Toast.makeText(this, "Post is private or restricted.", Toast.LENGTH_LONG).show()
                }
                "error" -> {
                    val message = result.getString("message")
                    Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to parse result: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun downloadFiles(filesToDownload: List<ImageCard>) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var downloadCount = 0
        filesToDownload.forEach { media ->
            val uri = Uri.parse(media.url)
            val fileExtension = if (media.type == "video") ".mp4" else ".jpg"
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "VedInsta_${timestamp}_${downloadCount++}$fileExtension"

            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("Downloading via VedInsta")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    if (media.type == "video") Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    fileName
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadManager.enqueue(request)
        }
        Toast.makeText(this, "Started downloading ${filesToDownload.size} files.", Toast.LENGTH_SHORT).show()
    }
}