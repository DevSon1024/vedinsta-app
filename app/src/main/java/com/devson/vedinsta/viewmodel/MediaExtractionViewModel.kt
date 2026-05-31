package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.VedInstaApplication
import com.devson.vedinsta.adapters.MediaSelectionAdapter
import com.devson.vedinsta.model.MediaResult
import com.devson.vedinsta.repository.MediaFetcherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ExtractionState {
    object Idle : ExtractionState()
    object Loading : ExtractionState()
    data class Success(val mediaList: List<MediaResult>) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

class MediaExtractionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaFetcherRepository(application.applicationContext)
    private val app = application as VedInstaApplication

    private val _extractionState = MutableStateFlow<ExtractionState>(ExtractionState.Idle)
    val extractionState: StateFlow<ExtractionState> = _extractionState.asStateFlow()

    // Holds set of selected media URLs
    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    /**
     * Triggers the Python media extraction script and updates flow states.
     */
    fun extractMedia(urlOrShortcode: String, authViewModel: InstagramAuthViewModel) {
        if (urlOrShortcode.isBlank()) {
            _extractionState.value = ExtractionState.Error("Please enter a valid Instagram URL or shortcode")
            return
        }

        viewModelScope.launch {
            _extractionState.value = ExtractionState.Loading
            _selectedItems.value = emptySet()
            try {
                val results = repository.fetchMedia(urlOrShortcode)
                _extractionState.value = ExtractionState.Success(results)
                // Select all extracted items by default
                _selectedItems.value = results.mapNotNull { it.url }.toSet()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Extraction failed"
                _extractionState.value = ExtractionState.Error(errorMessage)
                
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

    fun toggleSelection(url: String) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(url)) {
            current.remove(url)
        } else {
            current.add(url)
        }
        _selectedItems.value = current
    }

    fun selectAll(mediaList: List<MediaResult>) {
        _selectedItems.value = mediaList.mapNotNull { it.url }.toSet()
    }

    fun selectNone() {
        _selectedItems.value = emptySet()
    }

    /**
     * Map selected results to MediaSelectionAdapter.MediaItem and download them.
     */
    fun downloadSelected(mediaList: List<MediaResult>, postUrl: String) {
        val selectedUrls = _selectedItems.value
        val itemsToDownload = mediaList.filter { selectedUrls.contains(it.url) }.map { result ->
            MediaSelectionAdapter.MediaItem(
                url = result.url ?: "",
                type = result.type ?: "image",
                index = result.index ?: 1,
                isSelected = true
            )
        }

        if (itemsToDownload.isEmpty()) {
            return
        }

        viewModelScope.launch {
            try {
                app.downloadSelectedMedia(itemsToDownload, postUrl)
            } catch (e: Exception) {
                Log.e("MediaExtractionVM", "Failed to start download of selected media", e)
            }
        }
    }
}
