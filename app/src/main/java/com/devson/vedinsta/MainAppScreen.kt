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
    settingsManager: SettingsManager,
    intent: Intent?,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val screenStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = screenStack.last()
    val scope = rememberCoroutineScope()

    val posts by mainViewModel.allDownloadedPosts.observeAsState(emptyList())
    val notifications by notificationViewModel.allNotifications.observeAsState(emptyList())
    val unreadCount by notificationViewModel.unreadCount.observeAsState(0)

    var gridColumnCount by remember { mutableStateOf(settingsManager.gridColumnCount) }
    var isListView by remember { mutableStateOf(settingsManager.isListView) }
    var showViewSettingsSheet by remember { mutableStateOf(false) }
    val viewSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var maxNotificationsLimit by remember { mutableStateOf(settingsManager.maxNotificationsLimit) }
    var showNotificationSettingsSheet by remember { mutableStateOf(false) }
    val notificationSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(currentScreen) {
        if (currentScreen is Screen.Notifications) {
            notificationViewModel.pruneNotifications(settingsManager.maxNotificationsLimit)
        }
    }

    // Local states for Favorites tracking using SharedPreferences via SettingsManager
    val favoritesUpdated = remember { mutableStateOf(0) }
    val isFavoriteHelper: (String) -> Boolean = { postId ->
        favoritesUpdated.value // read to trigger recomposition
        settingsManager.isFavorite(postId)
    }
    val toggleFavoriteHelper: (String) -> Unit = { postId ->
        settingsManager.toggleFavorite(postId)
        favoritesUpdated.value++
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
                        } else if (currentScreen == Screen.Notifications) {
                            IconButton(onClick = { showNotificationSettingsSheet = true }) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Notification Settings",
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
                            recentPosts = posts,
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
                            posts = posts,
                            gridColumnCount = gridColumnCount,
                            onGridColumnsChanged = { cols ->
                                settingsManager.gridColumnCount = cols
                                gridColumnCount = cols
                            },
                            isListView = isListView,
                            onListViewChanged = { listMode ->
                                settingsManager.isListView = listMode
                                isListView = listMode
                            },
                            isFavorite = isFavoriteHelper,
                            onToggleFavorite = toggleFavoriteHelper,
                            onPostClick = { post -> navigateTo(Screen.PostView(post)) },
                            onPostLongClick = { post ->
                                showPostOptions(context, post, mainViewModel, toggleFavoriteHelper, isFavoriteHelper(post.postId))
                            }
                        )
                    }
                    is Screen.Favorites -> {
                        FavoritesScreen(
                            posts = posts,
                            gridColumnCount = gridColumnCount,
                            onGridColumnsChanged = { cols ->
                                settingsManager.gridColumnCount = cols
                                gridColumnCount = cols
                            },
                            isListView = isListView,
                            onListViewChanged = { listMode ->
                                settingsManager.isListView = listMode
                                isListView = listMode
                            },
                            isFavorite = isFavoriteHelper,
                            onToggleFavorite = toggleFavoriteHelper,
                            onPostClick = { post -> navigateTo(Screen.PostView(post)) },
                            onPostLongClick = { post ->
                                showPostOptions(context, post, mainViewModel, toggleFavoriteHelper, isFavoriteHelper(post.postId))
                            }
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
                            settingsManager = settingsManager,
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
                        LaunchedEffect(maxNotificationsLimit) {
                            notificationViewModel.pruneNotifications(maxNotificationsLimit)
                        }
                        NotificationsScreen(
                            notifications = notifications,
                            onNotificationClick = { notification ->
                                notificationViewModel.markAsRead(notification.id)
                                if (notification.type == com.devson.vedinsta.database.NotificationType.DOWNLOAD_COMPLETED && notification.postId != null) {
                                    scope.launch {
                                        val post = withContext(Dispatchers.IO) {
                                            com.devson.vedinsta.database.AppDatabase.getDatabase(context).downloadedPostDao().getPostById(notification.postId)
                                        }
                                        if (post != null) {
                                            navigateTo(Screen.PostView(post))
                                        } else {
                                            Toast.makeText(context, "Post files were deleted or moved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onDeleteClick = { id -> notificationViewModel.deleteNotification(id) }
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
                                post.mediaPaths.forEach { path ->
                                    try {
                                        val file = File(path)
                                        if (file.exists()) file.delete()
                                    } catch (e: Exception) {}
                                }
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
                settingsManager.isListView = listMode
                isListView = listMode
            },
            gridColumnCount = gridColumnCount,
            onGridColumnsChanged = { cols ->
                settingsManager.gridColumnCount = cols
                gridColumnCount = cols
            },
            onDismissRequest = { showViewSettingsSheet = false }
        )
    }

    // Notification Settings Bottom Sheet
    if (showNotificationSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNotificationSettingsSheet = false },
            sheetState = notificationSettingsSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Notification Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                var isUnrestricted by remember { mutableStateOf(maxNotificationsLimit == 0) }
                var sliderValue by remember { mutableStateOf(if (maxNotificationsLimit > 0) maxNotificationsLimit.toFloat() else 50f) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Unrestricted Keep Limit",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Keep all notifications history indefinitely",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isUnrestricted,
                        onCheckedChange = { checked ->
                            isUnrestricted = checked
                            val newLimit = if (checked) 0 else sliderValue.toInt()
                            settingsManager.maxNotificationsLimit = newLimit
                            maxNotificationsLimit = newLimit
                            notificationViewModel.pruneNotifications(newLimit)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Maximum Keep Count: ${sliderValue.toInt()}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (isUnrestricted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { valRounded ->
                            sliderValue = valRounded
                        },
                        onValueChangeFinished = {
                            if (!isUnrestricted) {
                                val newLimit = sliderValue.toInt()
                                settingsManager.maxNotificationsLimit = newLimit
                                maxNotificationsLimit = newLimit
                                notificationViewModel.pruneNotifications(newLimit)
                            }
                        },
                        valueRange = 10f..200f,
                        steps = 18,
                        enabled = !isUnrestricted,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// Helpers for downloaded items options dialog
private fun showPostOptions(
    context: Context,
    post: DownloadedPost,
    viewModel: MainViewModel,
    onToggleFavorite: (String) -> Unit,
    isFavorite: Boolean
) {
    val options = arrayOf(
        if (isFavorite) "Remove from Favorites" else "Add to Favorites",
        "Share Post File(s)",
        "Delete from History"
    )

    androidx.appcompat.app.AlertDialog.Builder(context)
        .setTitle("Post Options")
        .setItems(options) { _, which ->
            when (which) {
                0 -> onToggleFavorite(post.postId)
                1 -> {
                    if (post.mediaPaths.isNotEmpty()) {
                        val file = File(post.mediaPaths.first())
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = if (file.extension.lowercase() in listOf("mp4", "mov", "avi")) "video/*" else "image/*"
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share post"))
                    } else {
                        Toast.makeText(context, "No media files found to share", Toast.LENGTH_SHORT).show()
                    }
                }
                2 -> {
                    androidx.appcompat.app.AlertDialog.Builder(context)
                        .setTitle("Delete Post")
                        .setMessage("Are you sure you want to delete this post and its downloaded files? This cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            viewModel.deleteDownloadedPost(post)
                            post.mediaPaths.forEach { path ->
                                try {
                                    val f = File(path)
                                    if (f.exists()) f.delete()
                                } catch (e: Exception) {}
                            }
                            Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
        .show()
}

// Composable function helper to observe LiveData as State in Compose
@Composable
fun <T> LiveData<T>.observeAsState(initial: T): State<T> {
    val state = remember { mutableStateOf(initial) }
    DisposableEffect(this) {
        val observer = Observer<T> { value ->
            if (value != null) {
                state.value = value
            }
        }
        observeForever(observer)
        onDispose {
            removeObserver(observer)
        }
    }
    return state
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
