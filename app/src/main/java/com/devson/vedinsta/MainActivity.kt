package com.devson.vedinsta

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chaquo.python.Python
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.ActivityMainBinding
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var postsAdapter: PostsGridAdapter
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        setupUI()
        setupRecyclerView()
        setupBottomNavigation()
        setupFab()
        observeDownloadedPosts()
    }

    private fun setupUI() {
        // Initially show empty state
        updateEmptyState(emptyList())
    }

    private fun setupRecyclerView() {
        postsAdapter = PostsGridAdapter(
            onPostClick = { post ->
                // Navigate to detail view or open downloaded images
                Toast.makeText(this, "View downloaded post: ${post.postId}", Toast.LENGTH_SHORT).show()
            },
            onPostLongClick = { post ->
                // Show options to delete or share
                showPostOptionsDialog(post)
            }
        )

        binding.rvPosts.apply {
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = postsAdapter
        }
    }

    private fun observeDownloadedPosts() {
        viewModel.allDownloadedPosts.observe(this) { posts ->
            postsAdapter.submitList(posts)
            updateEmptyState(posts)
        }
    }

    private fun updateEmptyState(posts: List<DownloadedPost>) {
        if (posts.isEmpty()) {
            binding.emptyState.visibility = View.VISIBLE
            binding.rvPosts.visibility = View.GONE
        } else {
            binding.emptyState.visibility = View.GONE
            binding.rvPosts.visibility = View.VISIBLE
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
                    Toast.makeText(this, "Search feature coming soon", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_favorites -> {
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

        // Extract post ID from URL to check if already downloaded
        val postId = extractPostIdFromUrl(url)
        if (postId != null) {
            viewModel.checkIfPostDownloaded(postId) { isDownloaded ->
                runOnUiThread {
                    if (isDownloaded) {
                        showRedownloadDialog(url, postId)
                    } else {
                        fetchMediaFromUrl(url, postId)
                    }
                }
            }
        } else {
            fetchMediaFromUrl(url, null)
        }
    }

    private fun extractPostIdFromUrl(url: String): String? {
        // Extract post ID from Instagram URL
        // Patterns: https://www.instagram.com/p/POST_ID/ or https://instagram.com/p/POST_ID/
        val pattern = Pattern.compile("instagram\\.com/p/([^/?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun showRedownloadDialog(url: String, postId: String) {
        AlertDialog.Builder(this)
            .setTitle("Post Already Downloaded")
            .setMessage("This post has already been downloaded. Do you want to download it again?")
            .setPositiveButton("Download Again") { _, _ ->
                fetchMediaFromUrl(url, postId)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchMediaFromUrl(url: String, postId: String?) {
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
                        putExtra("POST_URL", url)
                        putExtra("POST_ID", postId)
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

    private fun showPostOptionsDialog(post: DownloadedPost) {
        AlertDialog.Builder(this)
            .setTitle("Post Options")
            .setItems(arrayOf("View Images", "Delete from History")) { _, which ->
                when (which) {
                    0 -> {
                        // View images - you can implement this
                        Toast.makeText(this, "View images feature coming soon", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // Delete from database
                        viewModel.deleteDownloadedPost(post)
                        Toast.makeText(this, "Removed from history", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }
}