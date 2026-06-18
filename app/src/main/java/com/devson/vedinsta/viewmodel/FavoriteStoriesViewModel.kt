package com.devson.vedinsta.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.CachedStoryEntity
import com.devson.vedinsta.database.FavoriteAccountEntity
import com.devson.vedinsta.repository.FavoriteStoriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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

    val favoriteAccounts = repository.getAllFavorites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val unviewedUsernames = repository.getUnviewedUsernamesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var isCheckingStatus = false

    fun triggerLazyStatusCheck(favorites: List<FavoriteAccountEntity> = emptyList()) {
        if (isCheckingStatus) return
        viewModelScope.launch(Dispatchers.IO) {
            isCheckingStatus = true
            try {
                var checkedAny = true
                while (checkedAny) {
                    checkedAny = false
                    val currentFavorites = repository.getAllFavoritesDirect()
                    val now = System.currentTimeMillis()
                    val fifteenMinutesMs = 15L * 60L * 1000L
                    
                    val pendingAccount = currentFavorites.firstOrNull { account ->
                        val lastCheck = account.lastStatusCheck ?: 0L
                        account.hasActiveStory == null || (now - lastCheck) > fifteenMinutesMs
                    }
                    
                    if (pendingAccount != null) {
                        val hasActiveStory = repository.checkStoryAvailabilityAnonymously(pendingAccount.username)
                        val lastCheck = pendingAccount.lastStatusCheck ?: 0L
                        val finalizedStatus = if (hasActiveStory) {
                            true
                        } else {
                            if (pendingAccount.hasActiveStory == false && (now - lastCheck) > 2 * 60 * 60 * 1000L) {
                                null
                            } else {
                                pendingAccount.hasActiveStory
                            }
                        }
                        repository.updateStoryAvailability(pendingAccount.username, finalizedStatus, System.currentTimeMillis())
                        checkedAny = true
                        kotlinx.coroutines.delay(2000L)
                    }
                }
            } catch (e: Exception) {
                // Status check error ignored
            } finally {
                isCheckingStatus = false
            }
        }
    }

    fun toggleFavorite(account: FavoriteAccountEntity, isFav: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isFav) {
                repository.addFavorite(account)
            } else {
                repository.removeFavorite(account.username)
            }
        }
    }

    fun loadStories(username: String) {
        _storyState.value = StoryState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stories = repository.getStoriesForUser(username)
                if (stories.isEmpty()) {
                    _storyState.value = StoryState.Error("No active stories found for this user.")
                } else {
                    _storyState.value = StoryState.Success(stories)
                    repository.markStoriesAsViewed(username)
                }
            } catch (e: Exception) {
                Log.e("FavoriteStoriesVM", "Error loading stories for $username", e)
                _storyState.value = StoryState.Error(e.message ?: "Failed to fetch stories.")
            }
        }
    }

    fun resetStoryState() {
        _storyState.value = StoryState.Loading
    }

    fun markStoriesAsViewed(username: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markStoriesAsViewed(username)
        }
    }

    fun markStoryAsViewed(storyId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.markStoryAsViewed(storyId)
        }
    }

    fun getUnviewedCountFlow(username: String): kotlinx.coroutines.flow.Flow<Int> {
        return repository.getUnviewedCountFlow(username)
    }

    fun getStoriesCountFlow(username: String): kotlinx.coroutines.flow.Flow<Int> {
        return repository.getStoriesCountFlow(username)
    }
}
