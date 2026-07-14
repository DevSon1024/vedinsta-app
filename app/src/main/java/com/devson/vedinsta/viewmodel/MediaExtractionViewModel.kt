package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.model.MediaItem
import com.devson.vedinsta.model.ExtractedMediaNode
import com.devson.vedinsta.model.ExtractedPost
import com.devson.vedinsta.model.MediaQuality
import com.devson.vedinsta.repository.MediaFetcherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.devson.vedinsta.repository.DownloadQuotaManager

sealed class ExtractionState {
    object Idle : ExtractionState()
    object Loading : ExtractionState()
    data class Success(val extractedPost: ExtractedPost) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

class MediaExtractionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaFetcherRepository(application.applicationContext)
    private val app = application as VedInstaApplication

    private val quotaManager = DownloadQuotaManager(application.applicationContext)
    private val _quotaState = MutableStateFlow<DownloadQuotaManager.QuotaStatus>(quotaManager.checkQuota())
    val quotaState: StateFlow<DownloadQuotaManager.QuotaStatus> = _quotaState.asStateFlow()

    fun refreshQuota() {
        _quotaState.value = quotaManager.checkQuota()
    }

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    private val _showRateLimitDialog = MutableStateFlow(false)
    val showRateLimitDialog: StateFlow<Boolean> = _showRateLimitDialog.asStateFlow()

    fun dismissRateLimitDialog() {
        _showRateLimitDialog.value = false
    }

    private val _showAuthRequiredDialog = MutableStateFlow(false)
    val showAuthRequiredDialog: StateFlow<Boolean> = _showAuthRequiredDialog.asStateFlow()

    fun dismissAuthRequiredDialog() {
        _showAuthRequiredDialog.value = false
    }

    // Holds set of selected media item indexes
    private val _selectedIndexes = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIndexes: StateFlow<Set<Int>> = _selectedIndexes.asStateFlow()

    // Holds chosen quality URL for each item index
    private val _chosenQualities = MutableStateFlow<Map<Int, String>>(emptyMap())
    val chosenQualities: StateFlow<Map<Int, String>> = _chosenQualities.asStateFlow()

    // Holds the URL of the currently extracted post
    var lastExtractedUrl: String = ""
        private set

    /**
     * Triggers the native media extraction and updates flow states.
     */
    fun extractMedia(urlOrShortcode: String, authViewModel: InstagramAuthViewModel) {
        if (urlOrShortcode.isBlank()) {
            _extractionState.value = ExtractionState.Error("Please enter a valid Instagram URL or shortcode")
            return
        }
        lastExtractedUrl = urlOrShortcode

        refreshQuota()
        val currentQuota = _quotaState.value
        if (currentQuota is DownloadQuotaManager.QuotaStatus.Exceeded) {
            val limitWord = when (currentQuota.limitType) {
                DownloadQuotaManager.LimitType.HOURLY -> "Hourly"
                DownloadQuotaManager.LimitType.DAILY -> "Daily"
                DownloadQuotaManager.LimitType.WEEKLY -> "Weekly"
            }
            _extractionState.value = ExtractionState.Error("$limitWord quota limit reached. You have already used more than your set up downloading limit. Please wait for the limit to reset before downloading again.")
            return
        }

        viewModelScope.launch {
            _extractionState.value = ExtractionState.Loading
            _selectedIndexes.value = emptySet()
            _chosenQualities.value = emptyMap()
            try {
                val extractedPost = repository.fetchMedia(urlOrShortcode)
                _extractionState.value = ExtractionState.Success(extractedPost)
                
                // Select all extracted item indexes by default
                val defaultIndexes = extractedPost.mediaList.mapNotNull { it.index }.toSet()
                _selectedIndexes.value = defaultIndexes
                
                // Populate default chosen qualities to highest resolution url
                val defaultQualities = extractedPost.mediaList.associate { item ->
                    val defaultUrl = item.qualities?.firstOrNull()?.url ?: item.url ?: ""
                    (item.index ?: 1) to defaultUrl
                }
                _chosenQualities.value = defaultQualities
            } catch (e: com.devson.vedinsta.extractor.AuthRequiredException) {
                val errorMessage = e.message ?: "Authentication required"
                _extractionState.value = ExtractionState.Error(errorMessage)
                _showAuthRequiredDialog.value = true
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Extraction failed"
                _extractionState.value = ExtractionState.Error(errorMessage)
                
                if (errorMessage.contains("Too Many Requests", ignoreCase = true) ||
                    errorMessage.contains("rate_limit_429", ignoreCase = true) ||
                    errorMessage.contains("wait 15 minutes", ignoreCase = true)) {
                    _showRateLimitDialog.value = true
                }
                
                // If a cookie-related authorization error occurs, alert AuthViewModel to refresh states
                if (errorMessage.contains("sessionid missing", ignoreCase = true) ||
                    errorMessage.contains("re-export cookies", ignoreCase = true) ||
                    errorMessage.contains("cookie file not found", ignoreCase = true) ||
                    errorMessage.contains("Login required", ignoreCase = true) ||
                    errorMessage.contains("API returned 401", ignoreCase = true) ||
                    errorMessage.contains("API returned 403", ignoreCase = true)) {
                    authViewModel.notifySessionExpired(errorMessage)
                }
            }
        }
    }

    fun reset() {
        _extractionState.value = ExtractionState.Idle
        _selectedIndexes.value = emptySet()
        _chosenQualities.value = emptyMap()
        lastExtractedUrl = ""
    }

    fun toggleSelection(index: Int) {
        val current = _selectedIndexes.value.toMutableSet()
        if (current.contains(index)) {
            current.remove(index)
        } else {
            current.add(index)
        }
        _selectedIndexes.value = current
    }

    fun changeQuality(index: Int, newUrl: String) {
        val current = _chosenQualities.value.toMutableMap()
        current[index] = newUrl
        _chosenQualities.value = current
    }

    fun selectAll(mediaList: List<ExtractedMediaNode>) {
        _selectedIndexes.value = mediaList.mapNotNull { it.index }.toSet()
    }

    fun selectNone() {
        _selectedIndexes.value = emptySet()
    }

    /**
     * Map selected results to MediaSelectionAdapter.MediaItem and download them.
     */
    fun downloadSelected(extractedPost: ExtractedPost, postUrl: String, globalQuality: MediaQuality) {
        refreshQuota()
        val currentQuota = _quotaState.value
        if (currentQuota is DownloadQuotaManager.QuotaStatus.Exceeded) {
            val limitWord = when (currentQuota.limitType) {
                DownloadQuotaManager.LimitType.HOURLY -> "Hourly"
                DownloadQuotaManager.LimitType.DAILY -> "Daily"
                DownloadQuotaManager.LimitType.WEEKLY -> "Weekly"
            }
            _extractionState.value = ExtractionState.Error("$limitWord quota limit reached. You have already used more than your set up downloading limit. Please wait for the limit to reset before downloading again.")
            return
        }

        val selectedIdxs = _selectedIndexes.value
        val chosenUrls = _chosenQualities.value
        
        val itemsToDownload = extractedPost.mediaList.filter { selectedIdxs.contains(it.index ?: -1) }.map { result ->
            val index = result.index ?: 1
            val chosenUrl = if (globalQuality != MediaQuality.CUSTOM) {
                result.downloadVariants.firstOrNull()?.url ?: result.url ?: ""
            } else {
                chosenUrls[index] ?: result.url ?: ""
            }
            MediaItem(
                url = chosenUrl,
                type = result.type ?: "image",
                index = index,
                isSelected = true
            )
        }

        if (itemsToDownload.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                app.downloadSelectedMedia(
                    mediaItems = itemsToDownload,
                    postUrl = postUrl,
                    username = extractedPost.username,
                    caption = extractedPost.caption,
                    postId = extractedPost.postId
                )
            } catch (e: Exception) {
                Log.e("MediaExtractionVM", "Failed to start download of selected media", e)
            }
        }
    }
}
