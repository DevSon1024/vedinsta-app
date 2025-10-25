package com.devson.vedinsta.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.chaquo.python.Python
import com.devson.vedinsta.DownloadActivity
import com.devson.vedinsta.EnhancedDownloadManager
import com.devson.vedinsta.GridPostItem
import com.devson.vedinsta.PostsGridAdapter
import com.devson.vedinsta.R
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.FragmentHomeBinding
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.regex.Pattern

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var postsAdapter: PostsGridAdapter
    private lateinit var viewModel: MainViewModel

    // Working list and pending tracking
    private val workingItems = mutableListOf<GridPostItem>()
    private val pendingTempIds = mutableSetOf<String>()
    private val workIdMap = mutableMapOf<String, UUID>() // Map tempKey/postId to Work ID

    // Map to keep track of active observers associated with this fragment instance
    private val activeObservers = mutableMapOf<UUID, Pair<LiveData<WorkInfo>, Observer<WorkInfo>>>()


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
        // Update empty state based on the current (potentially empty) list
        updateEmptyState(workingItems.isEmpty())
    }

    private fun setupRecyclerView() {
        postsAdapter = PostsGridAdapter(
            onPostClick = { post ->
                // Navigate to detail view
                val intent = com.devson.vedinsta.PostViewActivity.createIntent(requireContext(), post)
                startActivity(intent)
            },
            onPostLongClick = { post ->
                // Use post.postId or a tempId if post is null (shouldn't happen for long click)
                val key = post.postId ?: (workingItems.find { it.post == post }?.tempId)
                showPostOptionsDialog(post, key)
            }
        )

        binding.rvPosts.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = postsAdapter
            // Improve performance
            setHasFixedSize(true)
            setItemViewCacheSize(10) // Cache a few more views
        }
    }

    private fun observeDownloadedPosts() {
        viewModel.allDownloadedPosts.observe(viewLifecycleOwner) { posts ->
            val realById = posts.associateBy { it.postId }
            val rebuilt = mutableListOf<GridPostItem>()

            // Add real posts
            posts.forEach { dp ->
                // If this real post corresponds to a pending download, clean up
                workIdMap[dp.postId]?.let { cancelWorkObserver(it) } // Stop observing
                workIdMap.remove(dp.postId)
                pendingTempIds.remove(dp.postId)

                rebuilt.add(GridPostItem(post = dp, isDownloading = false, downloadProgress = 100))
            }

            // Keep any dummy still not fulfilled AND not failed/cancelled
            workingItems.filter { it.isDownloading }.forEach { dummy ->
                val key = dummy.post?.postId ?: dummy.tempId
                if (key != null && !realById.containsKey(key)) {
                    val workId = workIdMap[key]
                    val workState = workId?.let { getWorkState(it) }

                    // Only keep dummy if it's still potentially running or queued
                    if (workState == null || workState == WorkInfo.State.ENQUEUED || workState == WorkInfo.State.RUNNING || workState == WorkInfo.State.BLOCKED) {
                        rebuilt.add(dummy)
                        // Ensure observer is active if not already and workId exists
                        if (workId != null && !isObserving(workId)) {
                            observeWorkProgress(key, workId)
                        }
                    } else {
                        // Work associated with dummy failed or cancelled, remove it
                        Log.w(TAG, "Removing dummy item $key as associated work finished unsuccessfully ($workState)")
                        pendingTempIds.remove(key)
                        workIdMap.remove(key)
                        // Observer should have been removed already by the observer itself
                    }
                } else if (key != null) {
                    // Real post exists now, ensure full cleanup
                    workIdMap[key]?.let { cancelWorkObserver(it) }
                    workIdMap.remove(key)
                    pendingTempIds.remove(key)
                }
            }

            // Update list and submit
            workingItems.clear()
            // Add downloading items first, then downloaded sorted by date
            val downloadingItems = rebuilt.filter { it.isDownloading }
            val downloadedItems = rebuilt.filter { !it.isDownloading }.sortedByDescending { it.post?.downloadDate ?: 0L }
            workingItems.addAll(downloadingItems)
            workingItems.addAll(downloadedItems)

            postsAdapter.submitList(workingItems.toList()) {
                // Optional: Callback after list update is complete
                if (workingItems.isNotEmpty() && workingItems.first().isDownloading && workingItems.size > rebuilt.size) {
                    // If a new dummy was added, scroll to top
                    binding.rvPosts.scrollToPosition(0)
                }
            }

            updateEmptyState(workingItems.isEmpty()) // Update based on combined list
        }
    }


    private fun updateEmptyState(isEmpty: Boolean) { // Corrected parameter type
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvPosts.visibility = if (isEmpty) View.GONE else View.VISIBLE
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

        val url = clipData.getItemAt(0).text?.toString() // Safer conversion
        if (url.isNullOrEmpty()) {
            Toast.makeText(context, "Clipboard content is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!url.contains("instagram.com")) {
            Toast.makeText(context, "No Instagram URL in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        // Extract post ID from URL to check if already downloaded or currently downloading
        val postId = extractPostIdFromUrl(url)
        val keyToCheck = postId ?: url // Use URL as key if postId extraction fails

        // Check if currently downloading this item (using workIdMap keys)
        if (workIdMap.containsKey(keyToCheck)) {
            Toast.makeText(context, "This post is already downloading.", Toast.LENGTH_SHORT).show()
            return
        }


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
            // If postId is null, still proceed but use URL as the key later
            fetchMediaFromUrl(url, null)
        }
    }

    private fun extractPostIdFromUrl(url: String): String? {
        val pattern = Pattern.compile("instagram\\.com/(?:p|reel|tv)/([^/?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun showRedownloadDialog(url: String, postId: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Post Already Downloaded")
            .setMessage("This post has already been downloaded. Do you want to download it again?")
            .setPositiveButton("Download Again") { _, _ ->
                fetchMediaFromUrl(url, postId, forceRedownload = true) // Add a flag if needed for specific re-download logic
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchMediaFromUrl(url: String, postId: String?, forceRedownload: Boolean = false) {
        binding.progressBar.visibility = View.VISIBLE
        binding.fabDownload.hide()

        lifecycleScope.launch(Dispatchers.IO) {
            var fetchedCaption: String? = null // Variable to store caption
            var fetchedUsername: String? = null // Variable to store username
            try {
                val py = Python.getInstance()
                val pyModule = py.getModule("insta_downloader")
                val resultJson = pyModule.callAttr("get_media_urls", url).toString()

                // Try to parse username and caption early if possible
                kotlin.runCatching {
                    val preliminaryResult = JSONObject(resultJson)
                    if (preliminaryResult.optString("status") == "success") {
                        fetchedUsername = preliminaryResult.optString("username", "unknown")
                        fetchedCaption = preliminaryResult.optString("caption", null)
                    }
                }


                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()

                    val result = kotlin.runCatching { JSONObject(resultJson) }.getOrNull()
                    if (result == null || result.optString("status") != "success") {
                        val message = result?.optString("message", "Unknown error fetching media") ?: "Failed to parse result"
                        Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Python script failed or returned error: $resultJson")
                        return@withContext
                    }

                    // Update fetchedUsername and fetchedCaption again to be sure
                    fetchedUsername = result.optString("username", "unknown")
                    fetchedCaption = result.optString("caption", null)


                    val trackingKey = postId ?: url

                    if (workIdMap.containsKey(trackingKey) && !forceRedownload) {
                        Toast.makeText(context, "Download already in progress.", Toast.LENGTH_SHORT).show()
                        return@withContext
                    }

                    if (shouldAutoDownload(resultJson)) {
                        // Pass fetched caption and username to autoDownloadSingleMedia
                        autoDownloadSingleMedia(resultJson, url, trackingKey, fetchedUsername, fetchedCaption)
                    } else {
                        val intent = Intent(context, DownloadActivity::class.java).apply {
                            putExtra("RESULT_JSON", resultJson)
                            putExtra("POST_URL", url)
                            putExtra("POST_ID", trackingKey)
                        }
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(context, "Error fetching media info: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Python error: ", e)
                }
            }
        }
    }

    private fun shouldAutoDownload(jsonString: String): Boolean {
        try {
            val result = JSONObject(jsonString)
            if (result.getString("status") == "success") {
                val mediaArray = result.getJSONArray("media")
                return mediaArray.length() == 1
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing error for auto-download check", e)
        }
        return false
    }


    private fun isObserving(workId: UUID): Boolean {
        return activeObservers.containsKey(workId)
    }

    private fun getWorkState(workId: UUID): WorkInfo.State? {
        return try {
            WorkManager.getInstance(requireContext().applicationContext).getWorkInfoById(workId).get()?.state
        } catch (e: Exception) {
            Log.e(TAG, "Error getting work state for $workId", e)
            null
        }
    }

    private fun autoDownloadSingleMedia(
        jsonString: String,
        postUrl: String,
        trackingKey: String,
        username: String?, // Receive username
        caption: String? // Receive caption
    ) {
        binding.progressBar.visibility = View.GONE
        binding.fabDownload.hide()

        if (workIdMap.containsKey(trackingKey)) {
            Toast.makeText(context, "Download already in progress.", Toast.LENGTH_SHORT).show()
            binding.fabDownload.show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = JSONObject(jsonString)
                // Use the passed username or extract again as fallback
                val finalUsername = username ?: result.optString("username", "unknown")
                val mediaArray = result.getJSONArray("media")
                val mediaObject = mediaArray.getJSONObject(0)
                val mediaUrl = mediaObject.getString("url")
                val mediaType = mediaObject.getString("type")

                // Enqueue the download using WorkManager, passing the caption
                val workId = (requireActivity().application as VedInstaApplication).enqueueSingleDownload(
                    requireContext().applicationContext,
                    mediaUrl,
                    mediaType,
                    finalUsername,
                    trackingKey,
                    caption // Pass the caption here
                )

                withContext(Dispatchers.Main) {
                    binding.fabDownload.show()

                    if (workId != null) {
                        Log.d(TAG, "Enqueued single download work $workId for key $trackingKey")
                        workIdMap[trackingKey] = workId
                        addOrUpdateDummyCard(trackingKey, workId, 0)
                        observeWorkProgress(trackingKey, workId)
                        Toast.makeText(requireContext(), "Download started...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "✗ Failed to start download", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(requireContext(), "✗ Download failed: ${e.message?.take(100)}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error processing/enqueuing single download", e)
                }
            }
        }
    }

    private fun addOrUpdateDummyCard(key: String, workId: UUID, progress: Int) {
        if (!isAdded) return // Don't update UI if fragment not attached

        val existingIndex = workingItems.indexOfFirst { it.uniqueKey == key }

        if (existingIndex != -1) {
            val existingItem = workingItems[existingIndex]
            if (existingItem.isDownloading) {
                if (existingItem.downloadProgress != progress) { // Update only if progress changed
                    workingItems[existingIndex] = existingItem.copy(downloadProgress = progress)
                    postsAdapter.notifyItemChanged(existingIndex, PAYLOAD_PROGRESS)
                }
            } else {
                // Trying to update a non-dummy item? This might happen if DB update raced with progress update. Ignore.
                Log.w(TAG, "Attempted to update progress on a non-dummy item for key $key")
            }
        } else {
            // Add new dummy card
            val placeholderPost = DownloadedPost(
                postId = key,
                postUrl = "", thumbnailPath = "", totalImages = 1,
                downloadDate = System.currentTimeMillis(), hasVideo = false,
                username = "downloading...", caption = null, mediaPaths = emptyList()
            )
            val newItem = GridPostItem(
                post = placeholderPost,
                isDownloading = true,
                // Only set tempId if key is likely a generated UUID or URL, not a typical postId format
                tempId = if (key.length > 20 || !key.matches(Regex("[A-Za-z0-9_-]+"))) key else null,
                downloadProgress = progress,
                workId = workId
            )
            // Insert at the beginning of the list
            workingItems.add(0, newItem)
            // Update the adapter with the modified list
            postsAdapter.submitList(workingItems.toList()) {
                binding.rvPosts.scrollToPosition(0) // Scroll after list update
            }
            updateEmptyState(false) // Ensure empty state is hidden
        }
    }


    private fun observeWorkProgress(key: String, workId: UUID) {
        if (!isAdded || isObserving(workId)) {
            Log.d(TAG, "Not observing work $workId: Fragment not added or already observing.")
            return
        }
        Log.d(TAG, "Starting observer for work ID $workId for key $key")

        val workManager = WorkManager.getInstance(requireContext().applicationContext)
        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(workId)

        val observer = Observer<WorkInfo> { workInfo ->
            if (workInfo == null) return@Observer

            val progress = workInfo.progress.getInt(EnhancedDownloadManager.PROGRESS, -2)

            activity?.runOnUiThread {
                if (!isAdded) {
                    Log.w(TAG, "Observer fired but fragment not attached for key $key, workId $workId. Removing observer.")
                    cancelWorkObserver(workId)
                    return@runOnUiThread
                }

                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        // Log.d(TAG, "Observer: Work $workId RUNNING for key $key, Progress: $progress")
                        addOrUpdateDummyCard(key, workId, if (progress < 0) -1 else progress)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        Log.d(TAG, "Observer: Work $workId SUCCEEDED for key $key. DB LiveData will handle UI update.")
                        addOrUpdateDummyCard(key, workId, 100) // Show 100% briefly
                        cancelWorkObserver(workId)
                    }
                    WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                        Log.w(TAG, "Observer: Work $workId ${workInfo.state} for key $key")
                        removeDummyCard(key)
                        cancelWorkObserver(workId)
                        Toast.makeText(context, "Download ${workInfo.state.name.lowercase()}", Toast.LENGTH_SHORT).show()
                    }
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                        //Log.d(TAG, "Observer: Work $workId ${workInfo.state} for key $key")
                        addOrUpdateDummyCard(key, workId, 0) // Show 0 progress, not indeterminate
                    }
                }
            }
        }

        activeObservers[workId] = Pair(workInfoLiveData, observer)
        workInfoLiveData.observe(viewLifecycleOwner, observer)
    }

    private fun cancelWorkObserver(workId: UUID) {
        activity?.runOnUiThread {
            activeObservers.remove(workId)?.let { (liveData, observer) ->
                Log.d(TAG, "Removing observer for work ID $workId")
                liveData.removeObserver(observer)
            }
        }
    }

    private fun removeDummyCard(key: String) {
        if (!isAdded) return

        pendingTempIds.remove(key)
        workIdMap.remove(key)?.let { cancelWorkObserver(it) }

        val idx = workingItems.indexOfFirst { it.uniqueKey == key && it.isDownloading }
        if (idx >= 0) {
            Log.d(TAG, "Removing dummy card for key $key at index $idx")
            workingItems.removeAt(idx)
            postsAdapter.submitList(workingItems.toList())
            updateEmptyState(workingItems.isEmpty()) // Update empty state if needed
        } else {
            Log.w(TAG, "Tried to remove dummy card for key $key, but not found.")
        }
    }


    // This function is less critical now as save happens in Application, but can be kept for reference
    private fun saveDownloadedPost(
        postId: String, postUrl: String, downloadedFiles: List<String>,
        hasVideo: Boolean, username: String, caption: String?
    ) {
        if (downloadedFiles.isNotEmpty()) {
            Log.d(TAG, "HomeFragment: Requesting save for $postId")
            val downloadedPost = DownloadedPost(
                postId = postId, postUrl = postUrl, thumbnailPath = downloadedFiles.first(),
                totalImages = downloadedFiles.size, downloadDate = System.currentTimeMillis(),
                hasVideo = hasVideo, username = username, caption = caption, mediaPaths = downloadedFiles
            )
            viewModel.insertDownloadedPost(downloadedPost)
        }
    }

    private fun showPostOptionsDialog(post: DownloadedPost, itemKey: String?) {
        val key = itemKey ?: post.postId
        val options = mutableListOf("View Media")
        val workId = workIdMap[key]
        var workInfo: WorkInfo? = null

        if (workId != null) {
            try {
                workInfo = WorkManager.getInstance(requireContext().applicationContext).getWorkInfoById(workId).get()
            } catch (e: Exception) { Log.e(TAG, "Error getting work info for cancel check", e) }
        }

        val isCancellable = workInfo != null && !workInfo.state.isFinished

        if (isCancellable) {
            options.add("Cancel Download")
        }
        options.add("Delete from History")

        AlertDialog.Builder(requireContext())
            .setTitle("Post Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "View Media" -> {
                        if (post.postId.isNotBlank() && post.thumbnailPath.isNotBlank()) {
                            val intent = com.devson.vedinsta.PostViewActivity.createIntent(requireContext(), post)
                            startActivity(intent)
                        } else {
                            Toast.makeText(context, "Cannot view while downloading", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Cancel Download" -> {
                        workId?.let {
                            Log.d(TAG, "Requesting cancellation for work ID: $it (key: $key)")
                            WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(it)
                            Toast.makeText(context, "Cancelling download...", Toast.LENGTH_SHORT).show()
                            // Observer will handle UI update
                        }
                    }
                    "Delete from History" -> {
                        workId?.let {
                            Log.d(TAG, "Cancelling work ID $it due to deletion request (key: $key).")
                            WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(it)
                        }
                        viewModel.deleteDownloadedPost(post)
                        removeDummyCard(key)
                        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Cleaning up ${activeObservers.size} observers in onDestroyView")
        activeObservers.clear()
        _binding = null
    }

    companion object {
        private const val TAG = "HomeFragment"
        const val PAYLOAD_PROGRESS = "payload_progress"
    }
}