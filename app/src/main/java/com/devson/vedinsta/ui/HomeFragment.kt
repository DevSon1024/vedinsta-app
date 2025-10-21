package com.devson.vedinsta.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.chaquo.python.Python
import com.devson.vedinsta.DownloadActivity
import com.devson.vedinsta.PostsGridAdapter
import com.devson.vedinsta.R
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.FragmentHomeBinding
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.regex.Pattern

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var postsAdapter: PostsGridAdapter
    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]

        setupUI()
        setupRecyclerView()
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
                Toast.makeText(context, "View downloaded post: ${post.postId}", Toast.LENGTH_SHORT).show()
            },
            onPostLongClick = { post ->
                // Show options to delete or share
                showPostOptionsDialog(post)
            }
        )

        binding.rvPosts.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = postsAdapter
        }
    }

    private fun observeDownloadedPosts() {
        viewModel.allDownloadedPosts.observe(viewLifecycleOwner) { posts ->
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

    private fun setupFab() {
        binding.fabDownload.setOnClickListener {
            handleDownloadFabClick()
        }
    }

    private fun handleDownloadFabClick() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(context, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val clipData = clipboard.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(context, "No URL found in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = clipData.getItemAt(0).text.toString()
        if (!url.contains("instagram.com")) {
            Toast.makeText(context, "No Instagram URL in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        // Extract post ID from URL to check if already downloaded
        val postId = extractPostIdFromUrl(url)
        if (postId != null) {
            viewModel.checkIfPostDownloaded(postId) { isDownloaded ->
                activity?.runOnUiThread {
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
        val pattern = Pattern.compile("instagram\\.com/(?:p|reel|tv)/([^/?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun showRedownloadDialog(url: String, postId: String) {
        AlertDialog.Builder(requireContext())
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

                    // Check if single media - download directly
                    if (shouldAutoDownload(resultJson)) {
                        autoDownloadSingleMedia(resultJson, url, postId)
                    } else {
                        // Navigate to selection page for multiple media
                        val intent = Intent(context, DownloadActivity::class.java).apply {
                            putExtra("RESULT_JSON", resultJson)
                            putExtra("POST_URL", url)
                            putExtra("POST_ID", postId)
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shouldAutoDownload(jsonString: String): Boolean {
        try {
            val result = JSONObject(jsonString)
            if (result.getString("status") == "success") {
                val mediaArray = result.getJSONArray("media")
                return mediaArray.length() == 1 // Only auto-download single media posts
            }
        } catch (e: Exception) {
            // If parsing fails, go to selection page
        }
        return false
    }

    private fun autoDownloadSingleMedia(jsonString: String, postUrl: String, postId: String?) {
        // Show immediate feedback
        binding.progressBar.visibility = View.VISIBLE
        binding.fabDownload.hide()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = JSONObject(jsonString)
                val username = result.getString("username")
                val mediaArray = result.getJSONArray("media")
                val mediaObject = mediaArray.getJSONObject(0)

                val mediaUrl = mediaObject.getString("url")
                val mediaType = mediaObject.getString("type")

                // Download with notifications
                val downloadedFiles = (requireActivity().application as com.devson.vedinsta.VedInstaApplication)
                    .downloadSingleFile(requireContext(), mediaUrl, mediaType, username)

                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()

                    if (downloadedFiles.isNotEmpty()) {
                        // Save to database
                        saveDownloadedPost(postId, postUrl, downloadedFiles, mediaType == "video")

                        Toast.makeText(
                            requireContext(),
                            "✓ ${if (mediaType == "video") "Video" else "Image"} downloaded successfully!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "✗ Failed to download ${if (mediaType == "video") "video" else "image"}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(
                        requireContext(),
                        "✗ Download failed: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun saveDownloadedPost(postId: String?, postUrl: String, downloadedFiles: List<String>, hasVideo: Boolean) {
        if (postId != null && downloadedFiles.isNotEmpty()) {
            val downloadedPost = DownloadedPost(
                postId = postId,
                postUrl = postUrl,
                thumbnailPath = downloadedFiles.first(),
                totalImages = downloadedFiles.size,
                downloadDate = System.currentTimeMillis(),
                hasVideo = hasVideo
            )

            viewModel.insertDownloadedPost(downloadedPost)
        }
    }

    private fun showPostOptionsDialog(post: DownloadedPost) {
        AlertDialog.Builder(requireContext())
            .setTitle("Post Options")
            .setItems(arrayOf("View Media", "Delete from History")) { _, which ->
                when (which) {
                    0 -> {
                        // View media - you can implement this
                        Toast.makeText(context, "View media feature coming soon", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // Delete from database
                        viewModel.deleteDownloadedPost(post)
                        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}