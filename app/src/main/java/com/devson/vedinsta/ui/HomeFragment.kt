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
import com.devson.vedinsta.SettingsManager
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.databinding.FragmentHomeBinding
import com.devson.vedinsta.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var postsAdapter: PostsGridAdapter
    private lateinit var viewModel: MainViewModel
    private lateinit var settingsManager: SettingsManager // ADD THIS
    private var currentColumnCount = 3

    private val workingItems = mutableListOf<GridPostItem>()
    private val workIdMap = ConcurrentHashMap<String, UUID>()

    private val observedWorkIdsInFragment = ConcurrentHashMap.newKeySet<UUID>()


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
        settingsManager = SettingsManager(requireContext())
        currentColumnCount = settingsManager.gridColumnCount

        setupUI()
        setupRecyclerView()
        setupFab()
        observeDownloadedPosts()
        reObserveOngoingWork()
    }

    private fun setupUI() {
        updateEmptyState(workingItems.isEmpty())
    }

    private fun setupRecyclerView() {
        postsAdapter = PostsGridAdapter(
            onPostClick = { post ->
                val intent = com.devson.vedinsta.PostViewActivity.createIntent(requireContext(), post)
                startActivity(intent)
            },
            onPostLongClick = { post ->
                val key = post.postId
                showPostOptionsDialog(post, key)
            }
        )

        binding.rvPosts.apply {
            layoutManager = GridLayoutManager(context, currentColumnCount) // CHANGE FROM 3
            adapter = postsAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(10)
        }
    }

    fun showColumnSizeDialog() {
        val dialog = ColumnSizeDialog(currentColumnCount) { newColumnCount ->
            updateGridColumns(newColumnCount)
        }
        dialog.show(childFragmentManager, ColumnSizeDialog.TAG)
    }

    // ADD THIS METHOD
    private fun updateGridColumns(columnCount: Int) {
        if (currentColumnCount == columnCount) return

        currentColumnCount = columnCount

        // Smoothly update the grid with animation
        binding.rvPosts.apply {
            // Save scroll position
            val layoutManager = layoutManager as? GridLayoutManager
            val firstVisiblePosition = layoutManager?.findFirstVisibleItemPosition() ?: 0

            // Apply fade animation
            alpha = 0.7f
            animate()
                .alpha(1f)
                .setDuration(200)
                .start()

            // Update span count
            layoutManager?.spanCount = columnCount

            // Restore scroll position (approximately)
            post {
                layoutManager?.scrollToPosition(firstVisiblePosition)
            }
        }
    }

    private fun observeDownloadedPosts() {
        viewModel.allDownloadedPosts.observe(viewLifecycleOwner) { posts ->
            val realById = posts.associateBy { it.postId } // postId can now be shortcode or story item ID
            val rebuilt = mutableListOf<GridPostItem>()

            // Add real items from DB
            posts.forEach { dp ->
                workIdMap[dp.postId]?.let { fragmentCancelWorkObserver(it) }
                workIdMap.remove(dp.postId)
                rebuilt.add(GridPostItem(post = dp, isDownloading = false, downloadProgress = 100))
            }

            // Keep dummy items only if tracked and work hasn't finished
            workingItems.filter { it.isDownloading }.forEach { dummy ->
                val key = dummy.uniqueKey // uniqueKey handles postId or tempId
                if (!realById.containsKey(key)) {
                    val workId = workIdMap[key]
                    workId?.let { id ->
                        if (!observedWorkIdsInFragment.contains(id)) {
                            observeWorkProgress(key, id)
                        }
                        rebuilt.add(dummy) // Assume running until observer confirms otherwise
                    } ?: run {
                        Log.w(TAG, "Removing dummy item $key as its workId is not tracked by fragment.")
                    }
                } else {
                    workIdMap[key]?.let { fragmentCancelWorkObserver(it) }
                    workIdMap.remove(key)
                }
            }

            // Update list, sort, and submit
            workingItems.clear()
            val downloadingItems = rebuilt.filter { it.isDownloading }
            // Sort downloaded items (posts/stories) by date
            val downloadedItems = rebuilt.filter { !it.isDownloading }.sortedByDescending { it.post?.downloadDate ?: 0L }
            workingItems.addAll(downloadingItems)
            workingItems.addAll(downloadedItems)

            postsAdapter.submitList(workingItems.toList()) {
                if (workingItems.isNotEmpty() && workingItems.first().isDownloading && workingItems.size > rebuilt.size) {
                    binding.rvPosts.scrollToPosition(0)
                }
            }
            updateEmptyState(workingItems.isEmpty())
        }
    }


    private fun updateEmptyState(isEmpty: Boolean) {
        if (!isAdded) return
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvPosts.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun setupFab() {
        binding.fabDownload.setOnClickListener {
            handleDownloadFabClick()
        }
    }

    private fun handleDownloadFabClick() {
        if (!isAdded) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip
        val text = clipData?.getItemAt(0)?.text?.toString()?.trim()

        if (text.isNullOrEmpty()) {
            Toast.makeText(context, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }

        // Basic check for Instagram URL or potential username
        val isStoryUrl = text.contains("instagram.com/stories/")
        val isPostUrl = text.contains("instagram.com/") && (text.contains("/p/") || text.contains("/reel/") || text.contains("/tv/"))
        // Basic username check (no spaces, slashes, etc.) - might need refinement
        val isPotentialUsername = !text.contains("/") && !text.contains(" ") && !text.contains("?") && !text.contains("=")

        if (!isStoryUrl && !isPostUrl && !isPotentialUsername) {
            Toast.makeText(context, "No Instagram URL or username found in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        val contentIdentifier: String?
        val inputType: String // "url", "username"

        if (isStoryUrl) {
            // Extract username from story URL
            contentIdentifier = extractUsernameFromStoryUrl(text)
            inputType = if (contentIdentifier != null) "username" else "url" // Use URL if username extraction fails
            Log.d(TAG, "Detected Story URL, using username: $contentIdentifier")
        } else if (isPostUrl) {
            contentIdentifier = extractShortcodeFromUrl(text)
            inputType = "url"
            Log.d(TAG, "Detected Post/Reel URL, using shortcode: $contentIdentifier")
        } else if (isPotentialUsername) {
            contentIdentifier = text // Treat the text directly as username
            inputType = "username"
            Log.d(TAG, "Detected potential Username for stories: $contentIdentifier")
        } else {
            // Fallback: treat as URL if logic somehow fails
            contentIdentifier = text
            inputType = "url"
            Log.d(TAG, "Fallback: Treating as URL: $contentIdentifier")
        }


        // Key for checking downloads/DB: Use username for stories, shortcode for posts, or full input as fallback
        val keyToCheck = when {
            isStoryUrl && contentIdentifier != null -> contentIdentifier // Username for stories check
            isPostUrl && contentIdentifier != null -> contentIdentifier // Shortcode for posts check
            isPotentialUsername -> contentIdentifier // Username for potential story check
            else -> text // Fallback to full input text
        }

        if (keyToCheck.isNullOrEmpty()){
            Toast.makeText(context, "Could not identify content.", Toast.LENGTH_SHORT).show()
            return
        }


        // Check against fragment's workIdMap using the determined key
        // Need to be careful here - workIdMap keys might be specific story item IDs later
        // Check if ANY work is ongoing for this general identifier (username or shortcode)
        val isCurrentlyDownloading = workIdMap.keys.any { it.startsWith(keyToCheck) }

        if (isCurrentlyDownloading) {
            Toast.makeText(context, "Content related to this is already downloading.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check DB asynchronously - Check based on username for stories, shortcode for posts
        // This DB check might be less reliable for stories if multiple stories are saved under different IDs.
        // A simple check might just prevent re-fetching the *list* of stories.
        viewModel.checkIfPostDownloaded(keyToCheck) { isDownloaded ->
            activity?.runOnUiThread {
                if (isAdded) {
                    if (isDownloaded && !isStoryUrl && !isPotentialUsername) { // Only prompt re-download for posts/reels
                        showRedownloadDialog(text, keyToCheck)
                    } else {
                        // Pass the original text (URL or username) to fetchMedia
                        fetchMedia(text) // Simplified call
                    }
                }
            }
        }
    }

    // Renamed from fetchMediaFromUrl
    private fun fetchMedia(inputUrlOrUsername: String) {
        if (!isAdded) return
        binding.progressBar.visibility = View.VISIBLE
        binding.fabDownload.hide()

        lifecycleScope.launch(Dispatchers.IO) {
            var fetchedUsername: String? = null
            var resultJson: String? = null

            try {
                val py = Python.getInstance()
                val pyModule = py.getModule("insta_downloader")
                // Pass the raw input (URL or username) to the Python script
                resultJson = pyModule.callAttr("get_media_urls", inputUrlOrUsername).toString()

                // Preliminary parsing
                kotlin.runCatching {
                    val preliminaryResult = JSONObject(resultJson)
                    if (preliminaryResult.optString("status") == "success") {
                        fetchedUsername = preliminaryResult.optString("username", "unknown")
                        // Caption is handled later based on type
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()

                    if (resultJson == null) {
                        Toast.makeText(context, "Error: Failed to get result from script", Toast.LENGTH_LONG).show()
                        return@withContext
                    }

                    val result = kotlin.runCatching { JSONObject(resultJson) }.getOrNull()
                    val status = result?.optString("status")
                    val message = result?.optString("message", "Unknown error fetching media") ?: "Failed to parse result"
                    val contentType = result?.optString("type") // "post" or "story"

                    when (status) {
                        "success" -> {
                            val mediaArray = result.optJSONArray("media")
                            val mediaCount = mediaArray?.length() ?: 0
                            val finalUsername = result.optString("username", "unknown") // Get final username

                            // Post Key: Shortcode for posts, Username for stories (as group identifier)
                            val postKey = if (contentType == "story") finalUsername else result.optString("shortcode", inputUrlOrUsername)


                            if (mediaCount == 0 && contentType == "story") {
                                Toast.makeText(context, result.optString("message", "No active stories found."), Toast.LENGTH_SHORT).show()
                                return@withContext
                            }

                            if (mediaCount == 1) { // Auto-download single post OR single story item
                                autoDownloadSingleMedia(resultJson, postKey)
                            } else { // Multiple items (carousel post OR multiple stories)
                                // Need to pass unique identifiers for stories if possible
                                val intent = Intent(context, DownloadActivity::class.java).apply {
                                    putExtra("RESULT_JSON", resultJson) // Pass full JSON
                                    putExtra("POST_URL", inputUrlOrUsername) // Original input
                                    putExtra("POST_ID", postKey) // Pass shortcode or username as the main ID
                                    // DownloadActivity needs to handle parsing story_item_id from JSON if needed
                                }
                                startActivity(intent)
                            }
                        }
                        // Handle other statuses (private, login_required, etc.) as before
                        "private", "login_required" -> {
                            Toast.makeText(context, "Cannot download: $message", Toast.LENGTH_LONG).show()
                        }
                        "not_found" -> {
                            Toast.makeText(context, "Error: Content or User not found.", Toast.LENGTH_LONG).show()
                        }
                        "rate_limited" -> {
                            Toast.makeText(context, "Rate limited. Please try again later.", Toast.LENGTH_LONG).show()
                        }
                        "error", "connection_error" -> {
                            Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Python script error ($status): $resultJson")
                        }
                        else -> {
                            Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Python script failed or unexpected status: $resultJson")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(context, "Error running script: ${e.message?.take(100)}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Python execution error: ", e)
                }
            }
        }
    }


    private fun extractUsernameFromStoryUrl(url: String): String? {
        val pattern = Pattern.compile("instagram\\.com/stories/([^/]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    // Keep extractShortcodeFromUrl for posts/reels
    private fun extractShortcodeFromUrl(url: String): String? {
        val patterns = listOf(
            "instagram\\.com/(?:p|reel|tv)/([^/?]+)"
        )
        for (patternString in patterns) {
            val pattern = Pattern.compile(patternString)
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun showRedownloadDialog(url: String, identifier: String) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Content Already Downloaded")
            .setMessage("This post/reel has already been downloaded. Download again?")
            .setPositiveButton("Download Again") { _, _ ->
                fetchMedia(url) // Pass original URL for re-fetching
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    // Updated to handle both posts and stories based on JSON
    private fun autoDownloadSingleMedia(jsonString: String, postKey: String) {
        if (!isAdded) return
        binding.progressBar.visibility = View.GONE
        binding.fabDownload.hide()

        val appContext = requireContext().applicationContext

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = JSONObject(jsonString)
                val mediaArray = result.getJSONArray("media")
                if (mediaArray.length() == 0) throw Exception("No media found in JSON")

                val mediaObject = mediaArray.getJSONObject(0)
                val mediaUrl = mediaObject.getString("url")
                val mediaType = mediaObject.getString("type")
                val username = result.optString("username", "unknown")
                val caption = result.optString("caption", null) // Caption only relevant for posts
                // Unique ID: Use story_item_id if present (from stories), otherwise use postKey (shortcode or username)
                val uniqueItemId = mediaObject.optString("story_item_id", postKey)

                // Check if this specific item ID is already being downloaded
                if (workIdMap.containsKey(uniqueItemId)) {
                    withContext(Dispatchers.Main){
                        if(isAdded){
                            Toast.makeText(context, "Download already in progress.", Toast.LENGTH_SHORT).show()
                            binding.fabDownload.show()
                        }
                    }
                    return@launch
                }


                // Enqueue using Application context
                val workId = (appContext as VedInstaApplication).enqueueSingleDownload(
                    appContext,
                    mediaUrl,
                    mediaType,
                    username,
                    uniqueItemId, // Use the specific item ID for tracking and DB key
                    caption // Pass caption (will be null for stories)
                )

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.fabDownload.show()

                    if (workId != null) {
                        Log.d(TAG, "Enqueued single download work $workId for item $uniqueItemId")
                        workIdMap[uniqueItemId] = workId // Track specific item
                        addOrUpdateDummyCard(uniqueItemId, workId, 0)
                        observeWorkProgress(uniqueItemId, workId)
                        Toast.makeText(requireContext(), "Download started...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "✗ Failed to start download", Toast.LENGTH_SHORT).show()
                        // workIdMap.remove(uniqueItemId) // Cleanup handled in enqueue failure/observer
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(requireContext(), "✗ Download failed: ${e.message?.take(100)}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error processing/enqueuing single download for key $postKey", e)
                    // Attempt to clean up tracking map on failure, using postKey initially, might need refinement
                    // workIdMap.remove(postKey)?.let { fragmentCancelWorkObserver(it) } // Less reliable now
                }
            }
        }
    }

    // --- Dummy Card, Observation, and Cleanup Logic (largely unchanged, uses uniqueKey which handles different ID types) ---

    private fun addOrUpdateDummyCard(key: String, workId: UUID, progress: Int) {
        if (!isAdded) return

        val existingIndex = workingItems.indexOfFirst { it.uniqueKey == key }

        if (existingIndex != -1) {
            val existingItem = workingItems[existingIndex]
            if (existingItem.isDownloading) {
                if (existingItem.downloadProgress != progress) {
                    workingItems[existingIndex] = existingItem.copy(downloadProgress = progress)
                    postsAdapter.notifyItemChanged(existingIndex, PAYLOAD_PROGRESS)
                }
            } else {
                Log.w(TAG, "Attempted to update progress on a non-dummy item for key $key.")
                fragmentCancelWorkObserver(workId)
                workIdMap.remove(key)
            }
        } else {
            Log.d(TAG, "Adding new dummy card for key $key, workId $workId")
            // Create placeholder with the specific key
            val placeholderPost = DownloadedPost(
                postId = key, // Use the specific item ID (story or post)
                postUrl = "",
                thumbnailPath = "",
                totalImages = 1,
                downloadDate = System.currentTimeMillis(),
                hasVideo = false, // Assume image initially, can be updated later if needed
                username = "downloading...",
                caption = null,
                mediaPaths = emptyList()
            )
            val newItem = GridPostItem(
                post = placeholderPost,
                isDownloading = true,
                // tempId logic might be less relevant if key is always unique from start
                tempId = if (key.length > 30) key else null, // Keep for very long keys?
                downloadProgress = progress,
                workId = workId
            )
            workingItems.add(0, newItem)
            postsAdapter.submitList(workingItems.toList()) {
                if (binding.rvPosts.computeVerticalScrollOffset() > 0) {
                    binding.rvPosts.scrollToPosition(0)
                }
            }
            updateEmptyState(false)
        }
    }

    private fun observeWorkProgress(key: String, workId: UUID) {
        if (!isAdded || observedWorkIdsInFragment.contains(workId)) {
            Log.d(TAG, "Fragment observer for $workId not added (fragment not attached or already observing).")
            return
        }
        Log.d(TAG, "Fragment starting observer for work ID $workId (key $key)")
        observedWorkIdsInFragment.add(workId)

        val workManager = WorkManager.getInstance(requireContext().applicationContext)
        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(workId)

        workInfoLiveData.observe(viewLifecycleOwner, Observer { workInfo ->
            if (!isAdded) return@Observer

            if (workInfo == null) {
                Log.w(TAG, "Observer received null WorkInfo for $workId (key $key). Removing dummy card.")
                removeDummyCard(key)
                fragmentCancelWorkObserver(workId)
                workIdMap.remove(key)
                return@Observer
            }

            val progress = workInfo.progress.getInt(EnhancedDownloadManager.PROGRESS, -1)

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    addOrUpdateDummyCard(key, workId, progress)
                }
                WorkInfo.State.SUCCEEDED -> {
                    Log.d(TAG, "Fragment Observer: Work $workId SUCCEEDED for key $key. DB LiveData triggers UI update.")
                    addOrUpdateDummyCard(key, workId, 100) // Show 100 briefly
                    fragmentCancelWorkObserver(workId)
                    workIdMap.remove(key)
                    // DB observer handles replacement
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Log.w(TAG, "Fragment Observer: Work $workId ${workInfo.state} for key $key")
                    removeDummyCard(key)
                    fragmentCancelWorkObserver(workId)
                    workIdMap.remove(key)
                    if (isAdded) {
                        Toast.makeText(context, "Download ${workInfo.state.name.lowercase()}", Toast.LENGTH_SHORT).show()
                    }
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    addOrUpdateDummyCard(key, workId, 0) // Show initial state
                }
            }
        })
    }

    private fun fragmentCancelWorkObserver(workId: UUID) {
        if(observedWorkIdsInFragment.remove(workId)) {
            Log.d(TAG, "Fragment stopped tracking observer state for work ID $workId")
        }
    }


    private fun removeDummyCard(key: String) {
        if (!isAdded) return

        val removedWorkId = workIdMap.remove(key)
        removedWorkId?.let { fragmentCancelWorkObserver(it) }

        val idx = workingItems.indexOfFirst { it.uniqueKey == key && it.isDownloading }
        if (idx >= 0) {
            Log.d(TAG, "Removing dummy card for key $key at index $idx")
            workingItems.removeAt(idx)
            postsAdapter.submitList(workingItems.toList()) // Submit updated list
            updateEmptyState(workingItems.isEmpty())
        } else {
            Log.w(TAG, "Tried to remove dummy card for key $key, but not found.")
        }
    }

    private fun reObserveOngoingWork() {
        if (!isAdded) return

        Log.d(TAG, "Re-observing ${workIdMap.size} potential ongoing works tracked by fragment.")
        val keysToReObserve = workIdMap.keys.toList()
        keysToReObserve.forEach { key ->
            workIdMap[key]?.let { workId ->
                Log.d(TAG, "Re-attaching observer for key $key, workId $workId")
                observedWorkIdsInFragment.remove(workId) // Clear tracking state first
                observeWorkProgress(key, workId)
            }
        }
    }


    private fun showPostOptionsDialog(post: DownloadedPost?, itemKey: String?) {
        val key = itemKey ?: post?.postId ?: return // Use provided key or postId
        if (!isAdded) return

        lifecycleScope.launch {
            var workInfo: WorkInfo? = null
            val workId = workIdMap[key]

            if (workId != null) {
                workInfo = withContext(Dispatchers.IO) {
                    try {
                        WorkManager.getInstance(requireContext().applicationContext).getWorkInfoById(workId).get()
                    } catch (e: Exception) { null }
                }
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                val options = mutableListOf<String>()
                // Find item in current list to check if it's a dummy
                val gridItem = workingItems.find { it.uniqueKey == key }
                val isDummy = gridItem?.isDownloading ?: false // Assume not dummy if not found in list
                val isCancellable = workInfo != null && !workInfo.state.isFinished

                // Allow View Media only if it's NOT a dummy AND post data is available
                if (!isDummy && post != null) {
                    options.add("View Media")
                }
                // Allow Cancel only if it's a dummy AND work is cancellable
                if (isDummy && isCancellable) {
                    options.add("Cancel Download")
                }
                // Always allow delete
                options.add("Delete from History")

                if (options.isEmpty()) {
                    Toast.makeText(context, "No actions available.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                AlertDialog.Builder(requireContext())
                    .setTitle("Options") // Simpler title
                    .setItems(options.toTypedArray()) { _, which ->
                        if (!isAdded) return@setItems

                        when (options[which]) {
                            "View Media" -> {
                                post?.let {
                                    val intent = com.devson.vedinsta.PostViewActivity.createIntent(requireContext(), it)
                                    startActivity(intent)
                                } ?: Toast.makeText(context, "Cannot view item.", Toast.LENGTH_SHORT).show()
                            }
                            "Cancel Download" -> {
                                workId?.let { idToCancel ->
                                    Log.d(TAG, "Requesting cancellation for work ID: $idToCancel (key: $key)")
                                    WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(idToCancel)
                                    Toast.makeText(context, "Cancelling download...", Toast.LENGTH_SHORT).show()
                                    // Observer handles removal
                                }
                            }
                            "Delete from History" -> {
                                workId?.let { idToCancel ->
                                    Log.d(TAG, "Cancelling work ID $idToCancel due to deletion (key: $key).")
                                    WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(idToCancel)
                                }
                                post?.let { viewModel.deleteDownloadedPost(it) } // Delete from DB if real
                                removeDummyCard(key) // Remove dummy card if present
                                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Fragment onDestroyView called.")
        observedWorkIdsInFragment.clear()
        _binding = null
    }


    companion object {
        private const val TAG = "HomeFragment"
        const val PAYLOAD_PROGRESS = "payload_progress"
    }
}