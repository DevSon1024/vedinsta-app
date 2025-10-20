package com.devson.vedinsta

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chaquo.python.Python
import com.devson.vedinsta.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var postsAdapter: PostsGridAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupRecyclerView()
        setupBottomNavigation()
        setupFab()
    }

    private fun setupUI() {
        // Show empty state initially
        binding.emptyState.visibility = View.VISIBLE
        binding.rvPosts.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        postsAdapter = PostsGridAdapter { post ->
            // Handle post click - navigate to detail view
            // You can implement this later
        }

        binding.rvPosts.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = postsAdapter
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Already on home
                    true
                }
                R.id.nav_search -> {
                    // Navigate to search
                    Toast.makeText(this, "Search feature coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_favorites -> {
                    // Navigate to favorites
                    Toast.makeText(this, "Favorites feature coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }

        // Set home as selected by default
        binding.bottomNavigation.selectedItemId = R.id.nav_home
    }

    private fun setupFab() {
        binding.fabDownload.setOnClickListener {
            handleDownloadFabClick()
        }
    }

    private fun handleDownloadFabClick() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(this, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val clipData = clipboard.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(this, "No URL found in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = clipData.getItemAt(0).text.toString()
        if (!url.contains("instagram.com")) {
            Toast.makeText(this, "No Instagram URL in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        fetchMediaFromUrl(url)
    }

    private fun fetchMediaFromUrl(url: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.fabDownload.hide()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val pyModule = py.getModule("insta_downloader")
                val resultJson = pyModule.callAttr("get_media_urls", url).toString()

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()

                    // Navigate to selection page
                    val intent = Intent(this@MainActivity, DownloadActivity::class.java).apply {
                        putExtra("RESULT_JSON", resultJson)
                    }
                    startActivity(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
