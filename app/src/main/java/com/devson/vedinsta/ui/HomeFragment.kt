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
    // Removed pendingTempIds, rely on workIdMap keys
    // Use ConcurrentHashMap for thread safety, although primary access is main thread
    private val workIdMap = ConcurrentHashMap<String, UUID>() // Map tempKey/postId to Work ID

    // Map to keep track of active observers *managed by this fragment instance*
    // Storing only the Observer isn't needed here as LiveData handles removal with LifecycleOwner
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
            setItemViewCacheSize(10)
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
                    val workState = workId?.let { getWorkState(it) }

                    // Only keep if tracked by fragment and work is potentially active
                    if (workId != null && (workState == null || !workState.isFinished)) {
                        rebuilt.add(dummy)
                        // Ensure observer is active (it should be if workId is in map)
                        if (!observedWorkIdsInFragment.contains(workId)) {
                            observeWorkProgress(key, workId) // Re-attach observer if needed
                        }
                    } else {
                        // Work finished unsuccessfully or dummy no longer tracked by fragment
                        Log.w(TAG, "Removing dummy item $key as associated work finished unsuccessfully ($workState) or not tracked by fragment.")
                        workId?.let { fragmentCancelWorkObserver(it) } // Clean up fragment observer state
                        workIdMap.remove(key)
                    }
                } else {
                    // Real post exists, ensure full cleanup in fragment tracking
                    workIdMap[key]?.let { fragmentCancelWorkObserver(it) }
                    workIdMap.remove(key)
                }
            }


            // Update list and submit
            workingItems.clear()
            val downloadingItems = rebuilt.filter { it.isDownloading }
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

        if (postId != null) {
            viewModel.checkIfPostDownloaded(postId) { isDownloaded ->
                activity?.runOnUiThread { // Ensure UI operations on main thread
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
        val pattern = Pattern.compile("instagram\\.com/(?:p|reel|tv)/([^/?]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
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
            try {
                val py = Python.getInstance()
                val pyModule = py.getModule("insta_downloader")
                val resultJson = pyModule.callAttr("get_media_urls", url).toString()

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

                    val result = kotlin.runCatching { JSONObject(resultJson) }.getOrNull()
                    if (result == null || result.optString("status") != "success") {
                        val message = result?.optString("message", "Unknown error fetching media") ?: "Failed to parse result"
                        Toast.makeText(context, "Error: $message", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Python script failed or returned error: $resultJson")
                        return@withContext
                    }

                    fetchedUsername = result.optString("username", "unknown")
                    fetchedCaption = result.optString("caption", null)

                    val trackingKey = postId ?: url

                    // Check fragment's map
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
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


    private fun getWorkState(workId: UUID): WorkInfo.State? {
        if (!isAdded) return null
        return try {
            // Use application context to get WorkManager instance
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
        username: String?,
        caption: String?
    ) {
        if (!isAdded) return
        binding.progressBar.visibility = View.GONE
        binding.fabDownload.hide()

        // Check fragment's map
        if (workIdMap.containsKey(trackingKey)) {
            Toast.makeText(context, "Download already in progress.", Toast.LENGTH_SHORT).show()
            binding.fabDownload.show()
            return
        }

        // Use application context for enqueueing
        val appContext = requireContext().applicationContext

        lifecycleScope.launch(Dispatchers.IO) { // Still IO for JSON parsing
            try {
                val result = JSONObject(jsonString)
                val finalUsername = username ?: result.optString("username", "unknown")
                val mediaArray = result.getJSONArray("media")
                val mediaObject = mediaArray.getJSONObject(0)
                val mediaUrl = mediaObject.getString("url")
                val mediaType = mediaObject.getString("type")

                // Enqueue using Application context
                val workId = (appContext as VedInstaApplication).enqueueSingleDownload(
                    appContext,
                    mediaUrl,
                    mediaType,
                    finalUsername,
                    trackingKey,
                    caption
                )

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.fabDownload.show()

                    if (workId != null) {
                        Log.d(TAG, "Enqueued single download work $workId for key $trackingKey")
                        workIdMap[trackingKey] = workId // Track in fragment
                        addOrUpdateDummyCard(trackingKey, workId, 0) // Show initial dummy card
                        observeWorkProgress(trackingKey, workId) // Start observing in fragment
                        Toast.makeText(requireContext(), "Download started...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "✗ Failed to start download", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    binding.progressBar.visibility = View.GONE
                    binding.fabDownload.show()
                    Toast.makeText(requireContext(), "✗ Download failed: ${e.message?.take(100)}", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error processing/enqueuing single download", e)
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
                // Only notify if progress actually changes or if it wasn't indeterminate before
                if (existingItem.downloadProgress != progress || (existingItem.downloadProgress == -1 && progress >= 0)) {
                    workingItems[existingIndex] = existingItem.copy(downloadProgress = progress)
                    // Use a payload to avoid full rebind, only update progress view
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
            val placeholderPost = DownloadedPost(
                postId = key, // Use the key directly
                postUrl = "", thumbnailPath = "", totalImages = 1,
                downloadDate = System.currentTimeMillis(), hasVideo = false,
                username = "downloading...", caption = null, mediaPaths = emptyList()
            )
            val newItem = GridPostItem(
                post = placeholderPost, // Use placeholder
                isDownloading = true,
                tempId = if (key.length > 20 || !key.matches(Regex("[A-Za-z0-9_-]+"))) key else null, // Keep tempId logic
                downloadProgress = progress,
                workId = workId
            )
            // Insert at the beginning
            workingItems.add(0, newItem)
            // Submit the whole list for DiffUtil to handle insertion animation
            postsAdapter.submitList(workingItems.toList()) {
                // Scroll only after the list update is complete
                binding.rvPosts.scrollToPosition(0)
            }
            updateEmptyState(false)
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
        workInfoLiveData.observe(viewLifecycleOwner, Observer<WorkInfo> { workInfo ->
            if (workInfo == null || !isAdded) return@Observer // Check fragment state again

            val progress = workInfo.progress.getInt(EnhancedDownloadManager.PROGRESS, -2) // Default to -2 to distinguish from indeterminate -1

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    // Update dummy card progress
                    // Use -1 for indeterminate if progress value is not valid (e.g., -2 or < 0)
                    addOrUpdateDummyCard(key, workId, if (progress < 0) -1 else progress)
                }
                WorkInfo.State.SUCCEEDED -> {
                    Log.d(TAG, "Fragment Observer: Work $workId SUCCEEDED for key $key. DB LiveData triggers UI update.")
                    // Briefly show 100%
                    addOrUpdateDummyCard(key, workId, 100)
                    // Cleanup fragment-specific tracking
                    fragmentCancelWorkObserver(workId) // Removes from observedWorkIdsInFragment
                    workIdMap.remove(key) // Remove from fragment's workId tracking
                }
                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                    Log.w(TAG, "Fragment Observer: Work $workId ${workInfo.state} for key $key")
                    removeDummyCard(key) // Remove the dummy card immediately on failure/cancel
                    // Cleanup fragment-specific tracking
                    fragmentCancelWorkObserver(workId)
                    workIdMap.remove(key)
                    Toast.makeText(context, "Download ${workInfo.state.name.lowercase()}", Toast.LENGTH_SHORT).show()
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
    }


    private fun removeDummyCard(key: String) {
        if (!isAdded) return

        // Stop tracking in fragment
        workIdMap.remove(key)?.let { fragmentCancelWorkObserver(it) }

        val idx = workingItems.indexOfFirst { it.uniqueKey == key && it.isDownloading }
        if (idx >= 0) {
            Log.d(TAG, "Removing dummy card for key $key at index $idx")
            workingItems.removeAt(idx)
            // Submit the updated list
            postsAdapter.submitList(workingItems.toList())
            updateEmptyState(workingItems.isEmpty())
        } else {
            Log.w(TAG, "Tried to remove dummy card for key $key, but not found.")
        }
    }

    // Re-attach observers for work tracked by this fragment if view is recreated
    private fun reObserveOngoingWork() {
        Log.d(TAG, "Re-observing ${workIdMap.size} potential ongoing works.")
        // Iterate over a copy of the keys to avoid concurrent modification issues if cleanup happens during iteration
        val keysToReObserve = workIdMap.keys.toList()
        keysToReObserve.forEach { key ->
            workIdMap[key]?.let { workId ->
                // Check current state before re-observing
                val state = getWorkState(workId)
                if (state != null && !state.isFinished) {
                    Log.d(TAG, "Re-attaching observer for key $key, workId $workId (State: $state)")
                    // Clear previous tracking just in case, then re-observe
                    observedWorkIdsInFragment.remove(workId)
                    observeWorkProgress(key, workId)
                } else {
                    // Work likely finished while fragment was down, clean up tracking
                    Log.d(TAG, "Work $workId (key $key) finished while fragment view was destroyed. Cleaning up tracking.")
                    fragmentCancelWorkObserver(workId)
                    workIdMap.remove(key)
                    // UI should be updated by the main DB observer
                }
            }
        }
    }


    private fun showPostOptionsDialog(post: DownloadedPost?, itemKey: String?) {
        val key = itemKey ?: post?.postId ?: return // Need a key
        if (!isAdded) return

        val options = mutableListOf<String>()
        val isDummy = workingItems.find { it.uniqueKey == key }?.isDownloading ?: false
        val workId = workIdMap[key] // Get from fragment's map
        var workInfo: WorkInfo? = null

        if (workId != null) {
            try {
                // Use application context
                workInfo = WorkManager.getInstance(requireContext().applicationContext).getWorkInfoById(workId).get()
            } catch (e: Exception) { Log.e(TAG, "Error getting work info for cancel check", e) }
        }

        val isCancellable = workInfo != null && !workInfo.state.isFinished

        // Only allow View Media if it's NOT a dummy card
        if (!isDummy && post != null) {
            options.add("View Media")
        }

        if (isCancellable) {
            options.add("Cancel Download")
        }
        // Always allow delete
        options.add("Delete from History")

        if (options.isEmpty()) {
            Toast.makeText(context, "No actions available.", Toast.LENGTH_SHORT).show()
            return
        }


        AlertDialog.Builder(requireContext())
            .setTitle("Post Options")
            .setItems(options.toTypedArray()) { _, which ->
                if (!isAdded) return@setItems // Check fragment state again
                when (options[which]) {
                    "View Media" -> {
                        // Check post is not null and has valid data
                        if (post != null && post.postId.isNotBlank() && post.thumbnailPath.isNotBlank()) {
                            val intent = com.devson.vedinsta.PostViewActivity.createIntent(requireContext(), post)
                            startActivity(intent)
                        } else {
                            // This case should be less likely now due to the check above
                            Toast.makeText(context, "Cannot view item.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    "Cancel Download" -> {
                        workId?.let {
                            Log.d(TAG, "Requesting cancellation for work ID: $it (key: $key)")
                            // Use application context
                            WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(it)
                            Toast.makeText(context, "Cancelling download...", Toast.LENGTH_SHORT).show()
                            // Observer will handle UI update (removing dummy card)
                        }
                    }
                    "Delete from History" -> {
                        // Cancel work if it's running
                        workId?.let {
                            Log.d(TAG, "Cancelling work ID $it due to deletion request (key: $key).")
                            // Use application context
                            WorkManager.getInstance(requireContext().applicationContext).cancelWorkById(it)
                        }
                        // Delete from DB if it exists (use post if available)
                        post?.let { viewModel.deleteDownloadedPost(it) }
                        // Remove dummy card immediately
                        removeDummyCard(key)
                        Toast.makeText(context, "Removed from history", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        Log.d(TAG, "Fragment onDestroyView called. Observers attached to viewLifecycleOwner will be removed automatically.")
        observedWorkIdsInFragment.clear()
        _binding = null
    }


    companion object {
        private const val TAG = "HomeFragment"
        const val PAYLOAD_PROGRESS = "payload_progress"
    }
}