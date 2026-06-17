package com.devson.vedinsta.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.ui.FavoritesScreen
import com.devson.vedinsta.ui.HistoryScreen
import com.devson.vedinsta.ui.HomeScreen
import com.devson.vedinsta.ui.SessionsScreen
import com.devson.vedinsta.ui.WhatsAppSaverScreen
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MainViewModel
import com.devson.vedinsta.viewmodel.WhatsAppViewModel
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun MainPagerScreen(
    pagerState: PagerState,
    mainViewModel: MainViewModel,
    authViewModel: InstagramAuthViewModel,
    whatsAppViewModel: WhatsAppViewModel,
    gridColumnCount: Int,
    onGridColumnsChanged: (Int) -> Unit,
    isListView: Boolean,
    onListViewChanged: (Boolean) -> Unit,
    isFavorite: (String) -> Boolean,
    onToggleFavorite: (String) -> Unit,
    onFabAction: () -> Unit,
    onPostClick: (DownloadedPost) -> Unit,
    onNavigateToWhatsAppStatus: (Int) -> Unit,
    onNavigateToInstagramStory: (Int) -> Unit,
    onNavigateToLogin: () -> Unit,
    contentPadding: PaddingValues
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 3) {
            whatsAppViewModel.checkPermission(context)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1
    ) { page ->
        when (page) {
            0 -> HomeScreen(
                mainViewModel = mainViewModel,
                onFabAction = onFabAction,
                onNavigateToFavorites = {
                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                },
                onNavigateToHistory = {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                },
                onNavigateToSessions = {
                    coroutineScope.launch { pagerState.animateScrollToPage(4) }
                },
                onNavigateToWhatsAppSaver = {
                    coroutineScope.launch { pagerState.animateScrollToPage(3) }
                },
                onPostClick = onPostClick,
                onNavigateToInstagramStory = onNavigateToInstagramStory,
                contentPadding = contentPadding
            )
            1 -> HistoryScreen(
                mainViewModel = mainViewModel,
                gridColumnCount = gridColumnCount,
                onGridColumnsChanged = onGridColumnsChanged,
                isListView = isListView,
                onListViewChanged = onListViewChanged,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPostClick = onPostClick,
                contentPadding = contentPadding
            )
            2 -> FavoritesScreen(
                mainViewModel = mainViewModel,
                gridColumnCount = gridColumnCount,
                onGridColumnsChanged = onGridColumnsChanged,
                isListView = isListView,
                onListViewChanged = onListViewChanged,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onPostClick = onPostClick,
                contentPadding = contentPadding
            )
            3 -> WhatsAppSaverScreen(
                viewModel = whatsAppViewModel,
                onStatusClick = onNavigateToWhatsAppStatus,
                contentPadding = contentPadding
            )
            4 -> SessionsScreen(
                authViewModel = authViewModel,
                onNavigateToLogin = onNavigateToLogin,
                contentPadding = contentPadding
            )
        }
    }
}
