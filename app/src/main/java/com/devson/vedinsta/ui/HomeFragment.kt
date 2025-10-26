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
import java.util.concurrent.ConcurrentHashMap // Import ConcurrentHashMap
import java.util.regex.Pattern

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var postsAdapter: PostsGridAdapter
    private lateinit var viewModel: MainViewModel

    private val workingItems = mutableListOf<GridPostItem>()
    // Use ConcurrentHashMap for thread safety, although primary access is main thread
    private val workIdMap = ConcurrentHashMap<String, UUID>() // Map tempKey/postId to Work ID

    // Map to keep track of active observers *managed by this fragment instance*
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

        setupUI()
        setupRecyclerView()
        setupFab()
        observeDownloadedPosts()
        // Re-observe any ongoing work relevant to this fragment instance upon recreation
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
                // Ensure the key exists in workIdMap if it's currently downloading
                val key = post.postId ?: workingItems.find { it.post == post }?.uniqueKey
                showPostOptionsDialog(post, key)
            }
        )

        binding.rvPosts.apply {
            layoutManager = GridLayoutManager(context, 3)
            adapter = postsAdapter
            setHasFixedSize(true)
            setItemViewCacheSize(10) // Cache more items for smoother grid scrolling
        }
    }

    private fun observeDownloadedPosts() {
        viewModel.allDownloadedPosts.observe(viewLifecycleOwner) { posts ->
            val realById = posts.associateBy { it.postId }
            val rebuilt = mutableListOf<GridPostItem>()

            // Add real posts from DB
            posts.forEach { dp ->
                // If a real post appears, stop observing its WorkManager ID via the fragment
                workIdMap[dp.postId]?.let { fragmentCancelWorkObserver(it) }
                workIdMap.remove(dp.postId) // Remove from fragment's tracking too
                rebuilt.add(GridPostItem(post = dp, isDownloading = false, downloadProgress = 100))
            }

            // Keep dummy items only if they are still actively tracked by this fragment instance
            // and their corresponding work hasn't finished in a terminal state (checked via WorkManager state)
            workingItems.filter { it.isDownloading }.forEach { dummy ->
                val key = dummy.uniqueKey
                if (!realById.containsKey(key)) { // Check if a real post replaced it
                    val workId = workIdMap[key] // Get ID from fragment's map

                    // Use LiveData to check state asynchronously without blocking
                    workId?.let { id ->
                        if (!observedWorkIdsInFragment.contains(id)) {
                            observeWorkProgress(key, id) // Start observing if not already
                        }
                        // Check state via LiveData observation rather than blocking getWorkState() here
                        // The observer itself will handle removal if work finishes
                        rebuilt.add(dummy) // Assume it might still be running until observer confirms otherwise
                    } ?: run {
                        // If workId is null but dummy exists, it's an inconsistent state, remove dummy
                        Log.w(TAG, "Removing dummy item $key as its workId is not tracked by fragment.")
                    }

                } else {
                    // Real post exists, ensure full cleanup in fragment tracking
                    workIdMap[key]?.let { fragmentCancelWorkObserver(it) }
                    workIdMap.remove(key)
                }
            }


            // Update list and submit
            workingItems.clear()
            // Sort downloading items first, then downloaded items by date
            val downloadingItems = rebuilt.filter { it.isDownloading }
            val downloadedItems = rebuilt.filter { !it.isDownloading }.sortedByDescending { it.post?.downloadDate ?: 0L }
            workingItems.addAll(downloadingItems)
            workingItems.addAll(downloadedItems)

            postsAdapter.submitList(workingItems.toList()) {
                // Optional: Scroll to top only if a new download was added *at the top*
                if (workingItems.isNotEmpty() && workingItems.first().isDownloading && workingItems.size > rebuilt.size) {
                    binding.rvPosts.scrollToPosition(0)
                }
            }
            updateEmptyState(workingItems.isEmpty())
        }
    }


    private fun updateEmptyState(isEmpty: Boolean) {
        if (!isAdded) return // Check if fragment is attached
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
        if (!clipboard.hasPrimaryClip()) {
            Toast.makeText(context, "Clipboard is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        val clipData = clipboard.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(context, "No URL found in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }
        val url = clipData.getItemAt(0).text?.toString()
        if (url.isNullOrEmpty()) {
            Toast.makeText(context, "Clipboard content is empty.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!url.contains("instagram.com")) {
            Toast.makeText(context, "No Instagram URL in clipboard.", Toast.LENGTH_SHORT).show()
            return
        }

        val postId = extractPostIdFromUrl(url)
        val keyToCheck = postId ?: url

        // Check against fragment's workIdMap
        if (workIdMap.containsKey(keyToCheck)) {
            Toast.makeText(context, "This post is already downloading.", Toast.LENGTH_SHORT).show()
            return
        }

        // Check DB asynchronously
        if (postId != null) {
            viewModel.checkIfPostDownloaded(postId) { isDownloaded ->
                // Ensure we are still attached and on the main thread
                activity?.runOnUiThread {
                    if (isAdded) { // Check fragment state again
                        if (isDownloaded) {
                            showRedownloadDialog(url, postId)
                        } else {
                            fetchMediaFromUrl(url, postId)
                        }
                    }
                }
            }
        } else {
            // If no postId, assume it's not downloaded (or use URL as key if needed)
            fetchMediaFromUrl(url, null)
        }
    }


    private fun extractPostIdFromUrl(url: String): String? {
        // Regex patterns for various Instagram URL types
        val patterns = listOf(
            "instagram\\.com/(?:p|reel|tv)/([^/?]+)", // Matches /p/, /reel/, /tv/
            "instagram\\.com/stories/([^/]+)/(\\d+)" // Matches /stories/username/story_id/ (might capture username instead) - Less reliable for uniqueness
        )
        for (patternString in patterns) {
            val pattern = Pattern.compile(patternString)
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                // Return the first captured group (usually the shortcode or ID)
                return matcher.group(1)
            }
        }
        return null // No match found
    }

    private fun showRedownloadDialog(url: String, postId: String) {
        // Ensure context is available
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("Post Already Downloaded")
            .setMessage("This post has already been downloaded. Do you want to download it again?")
            .setPositiveButton("Download Again") { _, _ ->
                fetchMediaFromUrl(url, postId, forceRedownload = true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchMediaFromUrl(url: String, postId: String?, forceRedownload: Boolean = false) {
        if (!isAdded) return // Check fragment state
        binding.progressBar.visibility = View.VISIBLE
        binding.fabDownload.hide()

        lifecycleScope.launch(Dispatchers.IO) {
            var fetchedCaption: String? = null
            var fetchedUsername: String? = null
            var resultJson: String? = null // Store raw JSON result

            try {
                val py = Python.getInstance()
                val pyModule = py.getModule("insta_downloader")
                resultJson = pyModule.callAttr("get_media_urls", url).toString() // Execute python script

                // Preliminary parsing to get username/caption early if successful
                kotlin.runCatching {
                    val preliminaryResult = JSONObject(resultJson)
                    if (preliminaryResult.optString("status") == "success") {
                        fetchedUsername = preliminaryResult.optString("username", "unknown")
                        fetchedCaption = preliminaryResult.optString("caption", null)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext // Check again before UI update
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()

                    if (resultJson == null) {
                        Toast.makeText(context, "Error: Failed to get result from script", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Python script execution failed or returned null")
                        return@withContext
                    }

                    val result = kotlin.runCatching { JSONObject(resultJson) }.getOrNull()
                    val status = result?.optString("status")
                    val message = result?.optString("message", "Unknown error fetching media") ?: "Failed to parse result"

                    when (status) {
                        "success" -> {
                            // Already got username/caption above, re-fetch just in case
                            fetchedUsername = result.optString("username", "unknown")
                            fetchedCaption = result.optString("caption", null)

                            val trackingKey = postId ?: url // Use postId if available, else URL

                            // Check fragment's map again before proceeding
                            if (workIdMap.containsKey(trackingKey) && !forceRedownload) {
                                Toast.makeText(context, "Download already in progress.", Toast.LENGTH_SHORT).show()
                                return@withContext
                            }

                            if (shouldAutoDownload(resultJson)) {
                                autoDownloadSingleMedia(resultJson, url, trackingKey, fetchedUsername, fetchedCaption)
                            } else {
                                // Pass key to DownloadActivity
                                val intent = Intent(context, DownloadActivity::class.java).apply {
                                    putExtra("RESULT_JSON", resultJson)
                                    putExtra("POST_URL", url)
                                    putExtra("POST_ID", trackingKey) // Use trackingKey
                                }
                                startActivity(intent)
                            }
                        }
                        "private", "login_required" -> {
                            Toast.makeText(context, "Cannot download: $message", Toast.LENGTH_LONG).show()
                        }
                        "not_found" -> {
                            Toast.makeText(context, "Error: Post not found or unavailable.", Toast.LENGTH_LONG).show()
                        }
                        "rate_limited" -> {
                            Toast.makeText(context, "Rate limited by Instagram. Please try again later.", Toast.LENGTH_LONG).show()
                        }
                        "error" -> {
                            // More generic error
                            Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Python script returned error: $resultJson")
                        }
                        else -> {
                            // Fallback for unexpected status or parsing failure
                            Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Python script failed or returned unexpected status: $resultJson")
                        }
                    }
                }
            } catch (e: Exception) { // Catch errors during Python execution
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


    private fun shouldAutoDownload(jsonString: String): Boolean {
        return try {
            val result = JSONObject(jsonString)
            result.getString("status") == "success" && result.getJSONArray("media").length() == 1
        } catch (e: Exception) {
            Log.e(TAG, "JSON parsing error for auto-download check", e)
            false
        }
    }

    // Function to get WorkInfo state asynchronously (example using LiveData)
    // Note: This might be less useful here now, as state checking is integrated into observeDownloadedPosts observer logic.
    // Kept for reference or potential future use.
//    private fun getWorkStateLiveData(workId: UUID): LiveData<WorkInfo.State?> {
//        if (!isAdded) return MutableLiveData(null) // Return empty LiveData if not attached
//        val workManager = WorkManager.getInstance(requireContext().applicationContext)
//        return Transformations.map(workManager.getWorkInfoByIdLiveData(workId)) { it?.state }
//    }

    private fun autoDownloadSingleMedia(
        jsonString: String,
        postUrl: String,
        trackingKey: String,
        username: String?,
        caption: String?
    ) {
        if (!isAdded) return
        binding.progressBar.visibility = View.GONE // Hide progress bar used for fetching info
        binding.fabDownload.hide() // Keep FAB hidden initially for download

        // Check fragment's map immediately
        if (workIdMap.containsKey(trackingKey)) {
            Toast.makeText(context, "Download already in progress.", Toast.LENGTH_SHORT).show()
            binding.fabDownload.show() // Show FAB again if already downloading
            return
        }

        // Use application context for enqueueing
        val appContext = requireContext().applicationContext

        lifecycleScope.launch(Dispatchers.IO) { // IO for JSON parsing and enqueuing
            try {
                val result = JSONObject(jsonString)
                val finalUsername = username ?: result.optString("username", "unknown") // Use fetched or default
                val mediaArray = result.getJSONArray("media")
                if (mediaArray.length() == 0) throw Exception("No media found in JSON")
                val mediaObject = mediaArray.getJSONObject(0)
                val mediaUrl = mediaObject.getString("url")
                val mediaType = mediaObject.getString("type")

                // Enqueue using Application context and get the Work ID
                val workId = (appContext as VedInstaApplication).enqueueSingleDownload(
                    appContext,
                    mediaUrl,
                    mediaType,
                    finalUsername,
                    trackingKey,
                    caption // Pass caption here
                )

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.fabDownload.show() // Show FAB after attempting enqueue

                    if (workId != null) {
                        Log.d(TAG, "Enqueued single download work $workId for key $trackingKey")
                        workIdMap[trackingKey] = workId // Track in fragment
                        addOrUpdateDummyCard(trackingKey, workId, 0) // Show initial dummy card immediately
                        observeWorkProgress(trackingKey, workId) // Start observing in fragment
                        Toast.makeText(requireContext(), "Download started...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "✗ Failed to start download", Toast.LENGTH_SHORT).show()
                        // Optionally remove tracking if enqueue failed instantly, though observer should handle it
                        // workIdMap.remove(trackingKey)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.progressBar.visibility = View.GONE // Ensure progress bar is hidden on error
                    binding.fabDownload.show() // Show FAB on error
                    Toast.makeText(requireContext(), "✗ Download failed: ${e.message?.take(100)}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error processing/enqueuing single download for key $trackingKey", e)
                    // Clean up tracking map on failure
                    workIdMap.remove(trackingKey)?.let { fragmentCancelWorkObserver(it) }
                }
            }
        }
    }


    private fun addOrUpdateDummyCard(key: String, workId: UUID, progress: Int) {
        if (!isAdded) return // Check fragment state

        val existingIndex = workingItems.indexOfFirst { it.uniqueKey == key }

        if (existingIndex != -1) {
            val existingItem = workingItems[existingIndex]
            if (existingItem.isDownloading) {
                // Update progress only if it changed or state allows
                // Use payload to avoid full rebind, only update progress view
                if (existingItem.downloadProgress != progress) {
                    workingItems[existingIndex] = existingItem.copy(downloadProgress = progress)
                    postsAdapter.notifyItemChanged(existingIndex, PAYLOAD_PROGRESS)
                }
            } else {
                Log.w(TAG, "Attempted to update progress on a non-dummy item for key $key. DB update likely occurred.")
                // If the item exists but is no longer marked as downloading,
                // ensure the observer and tracking are cleaned up.
                fragmentCancelWorkObserver(workId)
                workIdMap.remove(key)
            }
        } else {
            // Add new dummy card
            Log.d(TAG, "Adding new dummy card for key $key, workId $workId")
            // Create a minimal placeholder post for the dummy card
            val placeholderPost = DownloadedPost(
                postId = key, // Use the key
                postUrl = "", // Not needed for dummy
                thumbnailPath = "", // No thumbnail yet
                totalImages = 1, // Assume 1 initially
                downloadDate = System.currentTimeMillis(),
                hasVideo = false, // Assume image initially
                username = "downloading...",
                caption = null,
                mediaPaths = emptyList()
            )
            val newItem = GridPostItem(
                post = placeholderPost,
                isDownloading = true,
                tempId = if (key.length > 20 || !key.matches(Regex("[A-Za-z0-9_-]+"))) key else null, // Keep tempId logic if key isn't a valid postId format
                downloadProgress = progress,
                workId = workId
            )
            // Insert at the beginning of the list
            workingItems.add(0, newItem)
            // Submit the whole list for DiffUtil to handle insertion animation smoothly
            postsAdapter.submitList(workingItems.toList()) {
                // Scroll only after the list update is complete and if needed
                if (binding.rvPosts.computeVerticalScrollOffset() > 0) { // Only scroll if not already at top
                    binding.rvPosts.scrollToPosition(0)
                }
            }
            updateEmptyState(false) // Grid is no longer empty
        }
    }

    // This observer is managed by the Fragment's LifecycleOwner
    private fun observeWorkProgress(key: String, workId: UUID) {
        if (!isAdded || observedWorkIdsInFragment.contains(workId)) {
            Log.d(TAG, "Fragment observer for $workId not added (fragment not attached or already observing).")
            return
        }
        Log.d(TAG, "Fragment starting observer for work ID $workId (key $key)")
        observedWorkIdsInFragment.add(workId) // Track that fragment is observing

        // Use application context for WorkManager
        val workManager = WorkManager.getInstance(requireContext().applicationContext)
        val workInfoLiveData = workManager.getWorkInfoByIdLiveData(workId)

        // Observe using viewLifecycleOwner - automatically removes observer on view destruction
        workInfoLiveData.observe(viewLifecycleOwner, Observer { workInfo ->
            // Note: Observer lambda gets a nullable WorkInfo
            if (!isAdded) return@Observer // Check fragment state

            if (workInfo == null) {
                // WorkInfo might be null if the work is pruned or otherwise removed
                Log.w(TAG, "Observer received null WorkInfo for $workId (key $key). Removing dummy card.")
                removeDummyCard(key)
                fragmentCancelWorkObserver(workId) // Ensure cleanup
                workIdMap.remove(key)
                return@Observer
            }

            // Get progress, default to -1 for indeterminate if PROGRESS key isn't present
            val progress = workInfo.progress.getInt(EnhancedDownloadManager.PROGRESS, -1)

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    addOrUpdateDummyCard(key, workId, progress)
                }
                WorkInfo.State.SUCCEEDED -> {
                    Log.d(TAG, "Fragment Observer: Work $workId SUCCEEDED for key $key. DB LiveData triggers UI update.")
                    // Briefly show 100% before DB update removes the dummy
                    addOrUpdateDummyCard(key, workId, 100)
                    // Cleanup fragment-specific tracking immediately
                    fragmentCancelWorkObserver(workId) // Removes from observedWorkIdsInFragment
                    workIdMap.remove(key) // Remove from fragment's workId tracking
                    // No need to manually remove the dummy card here, let the DB observer handle the replacement
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Log.w(TAG, "Fragment Observer: Work $workId ${workInfo.state} for key $key")
                    removeDummyCard(key) // Remove the dummy card immediately on failure/cancel
                    // Cleanup fragment-specific tracking
                    fragmentCancelWorkObserver(workId)
                    workIdMap.remove(key)
                    // Show toast only if fragment is still added
                    if (isAdded) {
                        Toast.makeText(context, "Download ${workInfo.state.name.lowercase()}", Toast.LENGTH_SHORT).show()
                    }
                }
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                    // Show initial state (0% or indeterminate)
                    addOrUpdateDummyCard(key, workId, 0)
                }
            }
        })
    }

    // Renamed to avoid confusion with Application's cleanup
    private fun fragmentCancelWorkObserver(workId: UUID) {
        // This primarily manages the fragment's *tracking* set,
        // as the LiveData observer is handled by viewLifecycleOwner
        if(observedWorkIdsInFragment.remove(workId)) {
            Log.d(TAG, "Fragment stopped tracking observer state for work ID $workId")
        }
        // No need to manually remove observer from LiveData here, viewLifecycleOwner handles it
    }


    private fun removeDummyCard(key: String) {
        if (!isAdded) return

        // Stop tracking in fragment map (if not already removed)
        val removedWorkId = workIdMap.remove(key)
        removedWorkId?.let { fragmentCancelWorkObserver(it) } // Also clear observer tracking state

        val idx = workingItems.indexOfFirst { it.uniqueKey == key && it.isDownloading }
        if (idx >= 0) {
            Log.d(TAG, "Removing dummy card for key $key at index $idx")
            workingItems.removeAt(idx)
            // Submit the updated list to RecyclerView
            postsAdapter.submitList(workingItems.toList())
            updateEmptyState(workingItems.isEmpty()) // Update empty state if needed
        } else {
            Log.w(TAG, "Tried to remove dummy card for key $key, but not found in workingItems.")
        }
    }

    // Re-attach observers for work tracked by this fragment if view is recreated
    private fun reObserveOngoingWork() {
        if (!isAdded) return // Don't run if fragment isn't attached

        Log.d(TAG, "Re-observing ${workIdMap.size} potential ongoing works tracked by fragment.")
        // Iterate over a copy of the keys to avoid concurrent modification issues
        val keysToReObserve = workIdMap.keys.toList()
        keysToReObserve.forEach { key ->
            workIdMap[key]?.let { workId ->
                // No need to block checking state here, just re-attach observer if tracked.
                // The observer itself will handle the current state when it first receives data.
                Log.d(TAG, "Re-attaching observer for key $key, workId $workId")
                // Clear previous *tracking state* just in case, then re-observe
                observedWorkIdsInFragment.remove(workId)
                observeWorkProgress(key, workId)
            }
        }
    }


    private fun showPostOptionsDialog(post: DownloadedPost?, itemKey: String?) {
        val key = itemKey ?: post?.postId ?: return // Need a key (postId or tempId)
        if (!isAdded) return

        // Launch coroutine to check WorkInfo state off the main thread
        lifecycleScope.launch {
            var workInfo: WorkInfo? = null
            val workId = workIdMap[key] // Get from fragment's map

            if (workId != null) {
                // Fetch WorkInfo asynchronously on IO dispatcher
                workInfo = withContext(Dispatchers.IO) {
                    try {
                        WorkManager.getInstance(requireContext().applicationContext).getWorkInfoById(workId).get()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error getting work info for cancel check on IO thread", e)
                        null // Return null on error
                    }
                }
            }

            // Continue on the Main thread to build and show the dialog
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext // Check fragment state again after background work

                val options = mutableListOf<String>()
                val isDummy = workingItems.find { it.uniqueKey == key }?.isDownloading ?: false
                val isCancellable = workInfo != null && !workInfo.state.isFinished

                // Only allow View Media if it's NOT a dummy card AND post data is available
                if (!isDummy && post != null) {
                    options.add("View Media")
                }

                if (isCancellable) {
                    options.add("Cancel Download")
                }
                // Always allow delete option (handles both dummy and real posts)
                options.add("Delete from History")

                if (options.isEmpty()) {
                    Toast.makeText(context, "No actions available.", Toast.LENGTH_SHORT).show()
                    return@withContext
                }

                // Show the dialog
                AlertDialog.Builder(requireContext())
                    .setTitle("Post Options")
                    .setItems(options.toTypedArray()) { _, which ->
                        if (!isAdded) return@setItems // Check fragment state again inside click listener

                        when (options[which]) {
                            "View Media" -> {
                                // Double-check post is not null (should be guaranteed by options logic)
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
                                    // The observer will handle removing the dummy card when cancellation completes
                                }
                            }
                            "Delete from History" -> {
                                // Cancel work if it's running
                                workId?.let { idToCancel ->
                                    Log.d(TAG, "Cancelling work ID $idToCancel due to deletion request (key: $key).")
                                    WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(idToCancel)
                                }
                                // Delete from DB if it's a real post
                                post?.let { viewModel.deleteDownloadedPost(it) }
                                // Remove dummy card immediately if it exists (won't affect real posts)
                                removeDummyCard(key)
                                Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
            } // End withContext(Dispatchers.Main)
        } // End lifecycleScope.launch
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Fragment onDestroyView called. Observers attached to viewLifecycleOwner will be removed automatically.")
        // Clear fragment-specific tracking sets
        observedWorkIdsInFragment.clear()
        // workIdMap is cleared implicitly as observers are removed and items potentially transition
        _binding = null // Crucial: Clear binding reference
    }


    companion object {
        private const val TAG = "HomeFragment"
        const val PAYLOAD_PROGRESS = "payload_progress" // Payload for progress updates
    }
}