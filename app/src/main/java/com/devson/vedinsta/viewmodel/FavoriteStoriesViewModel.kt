package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.CachedStoryEntity
import com.devson.vedinsta.repository.FavoriteStoriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class StoryState {
    object Loading : StoryState()
    data class Success(val stories: List<CachedStoryEntity>) : StoryState()
    data class Error(val message: String) : StoryState()
}

class FavoriteStoriesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = FavoriteStoriesRepository(db.favoriteStoriesDao(), application)

    private val _storyState = MutableStateFlow<StoryState>(StoryState.Loading)
    val storyState: StateFlow<StoryState> = _storyState.asStateFlow()

    fun loadStories(username: String) {
        _storyState.value = StoryState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stories = repository.getStoriesForUser(username)
                if (stories.isEmpty()) {
                    _storyState.value = StoryState.Error("No active stories found for this user.")
                } else {
                    _storyState.value = StoryState.Success(stories)
                }
            } catch (e: Exception) {
                Log.e("FavoriteStoriesVM", "Error loading stories for $username", e)
                _storyState.value = StoryState.Error(e.message ?: "Failed to fetch stories.")
            }
        }
    }

    fun markStoriesAsViewed(username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markStoriesAsViewed(username)
        }
    }
}
