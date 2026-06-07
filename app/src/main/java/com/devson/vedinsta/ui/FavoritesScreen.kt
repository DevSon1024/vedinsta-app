package com.devson.vedinsta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.vedinsta.database.DownloadedPost
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import com.devson.vedinsta.viewmodel.MainViewModel

@Composable
fun FavoritesScreen(
    mainViewModel: MainViewModel,
    gridColumnCount: Int,
    onGridColumnsChanged: (Int) -> Unit,
    isListView: Boolean,
    onListViewChanged: (Boolean) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onPostClick: (DownloadedPost) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val posts by mainViewModel.allDownloadedPosts.observeAsState(emptyList())
    val favoritePosts = posts.filter { isFavorite(it.postId) }

    if (favoritePosts.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No Favorites Yet",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap the heart icon on downloaded items to save them here.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    fontSize = 14.sp
                )
            }
        }
    } else {
        HistoryScreen(
            mainViewModel = mainViewModel,
            posts = favoritePosts,
            gridColumnCount = gridColumnCount,
            onGridColumnsChanged = onGridColumnsChanged,
            isListView = isListView,
            onListViewChanged = onListViewChanged,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onPostClick = onPostClick,
            contentPadding = contentPadding
        )
    }
}
