package com.devson.vedinsta

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.compose.runtime.livedata.observeAsState
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.ui.*
import com.devson.vedinsta.viewmodel.*
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

sealed class Screen {
    object Home : Screen()
    object Downloader : Screen()
    object DownloaderDetails : Screen()
    object History : Screen()
    object Favorites : Screen()
    object Sessions : Screen()
    object Settings : Screen()
    object Appearance : Screen()
    data class PostView(val post: DownloadedPost) : Screen()
    object About : Screen()
    object Notifications : Screen()
    object Login : Screen()
    object PrivacyPolicy : Screen()
}

@Composable
fun MainAppScreen(
    authViewModel: InstagramAuthViewModel,
    extractionViewModel: MediaExtractionViewModel,
    mainViewModel: MainViewModel,
    notificationViewModel: NotificationViewModel,
    settingsViewModel: SettingsViewModel,
    intent: Intent?,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val screenStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = screenStack.last()
    val scope = rememberCoroutineScope()

    val notifications by notificationViewModel.allNotifications.observeAsState(emptyList())
    val unreadCount by notificationViewModel.unreadCount.observeAsState(0)

    var gridColumnCount by remember { mutableStateOf(settingsViewModel.gridColumnCount) }
    var isListView by remember { mutableStateOf(settingsViewModel.isListView) }
    var showViewSettingsSheet by remember { mutableStateOf(false) }
    val viewSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(currentScreen) {
        if (currentScreen is Screen.Notifications) {
            notificationViewModel.pruneNotifications(settingsViewModel.maxNotificationsLimit)
        }
    }

    // Local states for Favorites tracking using SharedPreferences via SettingsViewModel
    val favoritePostIds by mainViewModel.favoritePostIds.collectAsState()
    val isFavoriteHelper = remember(favoritePostIds) {
        { postId: String -> favoritePostIds.contains(postId) }
    }
    val toggleFavoriteHelper: (String) -> Unit = { postId ->
        mainViewModel.toggleFavorite(postId)
    }

    fun navigateTo(screen: Screen) {
        if (screen is Screen.Home || screen is Screen.Downloader || screen is Screen.History ||
            screen is Screen.Favorites || screen is Screen.Sessions ||
            screen is Screen.Settings) {
            screenStack.clear()
        }
        screenStack.add(screen)
    }

    fun navigateBack() {
        if (screenStack.size > 1) {
            screenStack.removeAt(screenStack.lastIndex)
        } else {
            navigateTo(Screen.Home)
        }
    }

    // Handle system back button
    BackHandler(enabled = currentScreen !is Screen.Home) {
        navigateBack()
    }

    // Handle deep link intents reactively
    LaunchedEffect(intent) {
        val url = intent?.getStringExtra("POST_URL") ?: intent?.getStringExtra("instagram_url")
        if (!url.isNullOrEmpty()) {
            extractionViewModel.extractMedia(url, authViewModel)
            intent?.removeExtra("POST_URL")
            intent?.removeExtra("instagram_url")
            navigateTo(Screen.Downloader)
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen !is Screen.PostView && currentScreen !is Screen.Login && currentScreen !is Screen.Appearance && currentScreen !is Screen.Downloader && currentScreen !is Screen.DownloaderDetails) {
                VedInstaTopAppBar(
                    title = when(currentScreen) {
                        Screen.Home -> "Home"
                        Screen.Downloader -> "Downloader"
                        Screen.History -> "History"
                        Screen.Favorites -> "Favorites"
                        Screen.Sessions -> "Sessions"
                        Screen.Settings -> "Settings"
                        Screen.About -> "About"
                        Screen.Notifications -> "Notifications"
                        Screen.PrivacyPolicy -> "Privacy Policy"
                        else -> "VedInsta"
                    },
                    showBackButton = currentScreen !is Screen.Home,
                    onBackClick = { navigateBack() },
                    actions = {
                        if (currentScreen == Screen.Home) {
                            // Notification badge icon
                            IconButton(onClick = {
                                notificationViewModel.markAllAsRead()
                                navigateTo(Screen.Notifications)
                            }) {
                                Box(modifier = Modifier.wrapContentSize()) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notifications",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (unreadCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-4).dp)
                                                .background(MaterialTheme.colorScheme.error, androidx.compose.foundation.shape.CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                color = MaterialTheme.colorScheme.onError,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                        includeFontPadding = false
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Settings Button in TopAppBar
                            IconButton(
                                onClick = { navigateTo(Screen.Settings) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (currentScreen == Screen.History || currentScreen == Screen.Favorites) {
                            // View settings bottom sheet trigger (using hamburger menu)
                            IconButton(onClick = { showViewSettingsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "View Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { navigateTo(Screen.Settings) },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Settings",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (currentScreen !is Screen.Downloader && currentScreen !is Screen.DownloaderDetails && currentScreen !is Screen.Login && currentScreen !is Screen.PostView && currentScreen !is Screen.Appearance && currentScreen !is Screen.Settings && currentScreen !is Screen.Notifications) {
                FloatingActionButton(
                    onClick = { navigateTo(Screen.Downloader) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "New Download"
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val applyPadding = currentScreen !is Screen.PostView && currentScreen !is Screen.Login && currentScreen !is Screen.Appearance && currentScreen !is Screen.Downloader && currentScreen !is Screen.DownloaderDetails
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (applyPadding) paddingValues.calculateTopPadding() else 0.dp,
                    bottom = 0.dp
                )
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    // Use smooth fade for PostView (edge-to-edge) to avoid ditch effect
                    if (targetState is Screen.PostView || initialState is Screen.PostView) {
                        fadeIn(animationSpec = tween(250)) togetherWith
                            fadeOut(animationSpec = tween(250))
                    } else {
                        val initialOrder = getScreenOrderValue(initialState)
                        val targetOrder = getScreenOrderValue(targetState)
                        if (targetOrder > initialOrder) {
                            // Opening: Left to Right
                            slideInHorizontally(initialOffsetX = { -it }) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                        } else {
                            // Closing: Right to Left
                            slideInHorizontally(initialOffsetX = { it }) + fadeIn() togetherWith
                                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
                        }
                    }
                },
                label = "ScreenTransition",
                modifier = Modifier.fillMaxSize()
            ) { targetScreen ->
                when (targetScreen) {
                    is Screen.Home -> {
                        HomeScreen(
                            mainViewModel = mainViewModel,
                            onNavigateToDownloader = { navigateTo(Screen.Downloader) },
                            onNavigateToFavorites = { navigateTo(Screen.Favorites) },
                            onNavigateToHistory = { navigateTo(Screen.History) },
                            onNavigateToSessions = { navigateTo(Screen.Sessions) },
                            onPostClick = { post -> navigateTo(Screen.PostView(post)) }
                        )
                    }
                    is Screen.Downloader -> {
                        MediaSelectionScreen(
                            authViewModel = authViewModel,
                            extractionViewModel = extractionViewModel,
                            onNavigateToLogin = { navigateTo(Screen.Login) },
                            onNavigateToDetails = { navigateTo(Screen.DownloaderDetails) },
                            onNavigateBack = { navigateBack() }
                        )
                    }
                    is Screen.DownloaderDetails -> {
                        MediaSelectionCarouselScreen(
                            authViewModel = authViewModel,
                            extractionViewModel = extractionViewModel,
                            onNavigateBack = {
                                extractionViewModel.reset()
                                navigateBack()
                            },
                            onNavigateToNotifications = {
                                extractionViewModel.reset()
                                if (screenStack.lastOrNull() == Screen.DownloaderDetails) {
                                    screenStack.removeAt(screenStack.lastIndex)
                                }
                                notificationViewModel.markAllAsRead()
                                navigateTo(Screen.Notifications)
                            }
                        )
                    }
                    is Screen.History -> {
                        HistoryScreen(
                            mainViewModel = mainViewModel,
                            gridColumnCount = gridColumnCount,
                            onGridColumnsChanged = { cols ->
                                gridColumnCount = cols
                                scope.launch(Dispatchers.IO) {
                                    settingsViewModel.gridColumnCount = cols
                                }
                            },
                            isListView = isListView,
                            onListViewChanged = { listMode ->
                                settingsViewModel.isListView = listMode
                                isListView = listMode
                            },
                            isFavorite = isFavoriteHelper,
                            onToggleFavorite = toggleFavoriteHelper,
                            onPostClick = { post -> navigateTo(Screen.PostView(post)) }
                        )
                    }
                    is Screen.Favorites -> {
                        FavoritesScreen(
                            mainViewModel = mainViewModel,
                            gridColumnCount = gridColumnCount,
                            onGridColumnsChanged = { cols ->
                                gridColumnCount = cols
                                scope.launch(Dispatchers.IO) {
                                    settingsViewModel.gridColumnCount = cols
                                }
                            },
                            isListView = isListView,
                            onListViewChanged = { listMode ->
                                settingsViewModel.isListView = listMode
                                isListView = listMode
                            },
                            isFavorite = isFavoriteHelper,
                            onToggleFavorite = toggleFavoriteHelper,
                            onPostClick = { post -> navigateTo(Screen.PostView(post)) }
                        )
                    }
                    is Screen.Sessions -> {
                        SessionsScreen(
                            authViewModel = authViewModel,
                            onNavigateToLogin = { navigateTo(Screen.Login) }
                        )
                    }
                    is Screen.Login -> {
                        InstagramLoginScreen(
                            authViewModel = authViewModel,
                            onBackClick = { navigateBack() }
                        )
                        // Navigate back automatically on login completion
                        val authState by authViewModel.authState.collectAsState()
                        LaunchedEffect(authState) {
                            if (authState is InstagramAuthState.LoggedIn) {
                                navigateBack()
                            }
                        }
                    }
                    is Screen.Settings -> {
                        SettingsScreen(
                            settingsViewModel = settingsViewModel,
                            onNavigateToAbout = { navigateTo(Screen.About) },
                            onNavigateToAppearance = { navigateTo(Screen.Appearance) },
                            onNavigateToPrivacyPolicy = { navigateTo(Screen.PrivacyPolicy) },
                            onThemeChanged = onThemeChanged
                        )
                    }
                    is Screen.Appearance -> {
                        AppearanceSettingsScreen(
                            onNavigateBack = { navigateBack() },
                            settingsViewModel = settingsViewModel
                        )
                    }
                    is Screen.About -> {
                        AboutScreen()
                    }
                    is Screen.PrivacyPolicy -> {
                        PrivacyPolicyScreen()
                    }
                    is Screen.Notifications -> {
                        NotificationsScreen(
                            notifications = notifications,
                            onNotificationClick = { notification ->
                                notificationViewModel.markAsRead(notification.id)
                                if (notification.type == com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED && notification.postId != null) {
                                    scope.launch {
                                        val post = mainViewModel.getPostById(notification.postId)
                                        if (post != null) {
                                            navigateTo(Screen.PostView(post))
                                        } else {
                                            Toast.makeText(context, "Post files were deleted or moved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onDeleteClick = { id -> notificationViewModel.deleteNotification(id) },
                            settingsViewModel = settingsViewModel,
                            notificationViewModel = notificationViewModel
                        )
                    }
                    is Screen.PostView -> {
                        PostViewScreen(
                            post = targetScreen.post,
                            isFavorite = isFavoriteHelper,
                            onToggleFavorite = toggleFavoriteHelper,
                            onBackClick = { navigateBack() },
                            onDeletePost = { post ->
                                mainViewModel.deleteDownloadedPost(post)
                                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                                navigateBack()
                            }
                        )
                    }
                }
            }
        }
    }

    // View Settings Bottom Sheet
    if (showViewSettingsSheet) {
        ViewSettingBottomSheet(
            sheetState = viewSettingsSheetState,
            isListView = isListView,
            onListViewChanged = { listMode ->
                settingsViewModel.isListView = listMode
                isListView = listMode
            },
            gridColumnCount = gridColumnCount,
            onGridColumnsChanged = { cols ->
                gridColumnCount = cols
                scope.launch(Dispatchers.IO) {
                    settingsViewModel.gridColumnCount = cols
                }
            },
            onDismissRequest = { showViewSettingsSheet = false }
        )
    }

}

private fun getScreenOrderValue(screen: Screen): Int {
    return when (screen) {
        is Screen.Home -> 0
        is Screen.Downloader -> 1
        is Screen.DownloaderDetails -> 2
        is Screen.History -> 3
        is Screen.Favorites -> 4
        is Screen.Sessions -> 5
        is Screen.Settings -> 6
        is Screen.Appearance -> 7
        is Screen.Notifications -> 8
        is Screen.About -> 9
        is Screen.Login -> 10
        is Screen.PostView -> 11
        is Screen.PrivacyPolicy -> 12
    }
}
