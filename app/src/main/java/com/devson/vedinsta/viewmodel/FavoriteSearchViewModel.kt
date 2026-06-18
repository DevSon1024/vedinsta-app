package com.devson.vedinsta.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.vedinsta.database.AppDatabase
import com.devson.vedinsta.database.FavoriteAccountEntity
import com.devson.vedinsta.repository.FavoriteStoriesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class FavoriteSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = FavoriteStoriesRepository(db.favoriteStoriesDao(), application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResultState: StateFlow<List<FavoriteAccountEntity>> = _searchQuery
        .debounce(400L)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                flow {
                    emit(repository.fetchUsersAnonymously(cleanQuery(query)))
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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

    fun cleanQuery(query: String): String {
        val trimmed = query.trim()
        val pattern = Regex("(?:https?:\\/\\/)?(?:www\\.)?instagram\\.com\\/([a-zA-Z0-9_\\.]+)")
        val match = pattern.find(trimmed)
        return if (match != null) {
            match.groupValues[1]
        } else {
            trimmed.removePrefix("@").removeSuffix("/").trim()
        }
    }
}
