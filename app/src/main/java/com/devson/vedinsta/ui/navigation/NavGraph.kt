package com.devson.vedinsta.ui.navigation

import android.content.ClipboardManager
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.ui.*
import com.devson.vedinsta.viewmodel.*
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    authViewModel: InstagramAuthViewModel,
    extractionViewModel: MediaExtractionViewModel,
    mainViewModel: MainViewModel,
    notificationViewModel: NotificationViewModel,
    settingsViewModel: SettingsViewModel,
    whatsAppViewModel: WhatsAppViewModel,
    intent: Intent?,
    onThemeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val screenStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = screenStack.last()
    val scope = rememberCoroutineScope()
    val saveableStateHolder = rememberSaveableStateHolder()

    var gridColumnCount by remember { mutableStateOf(settingsViewModel.gridColumnCount) }
    var isListView by remember { mutableStateOf(settingsViewModel.isListView) }
    var showViewSettingsSheet by remember { mutableStateOf(false) }
    val viewSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Collect extraction state for FAB animation and reactive routing
    val extractionState by extractionViewModel.extractionState.collectAsStateWithLifecycle()

    LaunchedEffect(currentScreen) {
        if (currentScreen is Screen.Notifications) {
            notificationViewModel.pruneNotifications(settingsViewModel.maxNotificationsLimit)
        }
        if (currentScreen is Screen.WhatsAppSaver) {
            whatsAppViewModel.checkPermission(context)
        }
    }

    // Local states for Favorites tracking using SharedPreferences via SettingsViewModel
    val favoritePostIds by mainViewModel.favoritePostIds.collectAsStateWithLifecycle()
    val isFavoriteHelper = { postId: String -> favoritePostIds.contains(postId) }
    val toggleFavoriteHelper: (String) -> Unit = { postId ->
        mainViewModel.toggleFavorite(postId)
    }

    fun navigateTo(screen: Screen) {
        if (screen is Screen.Home || screen is Screen.History ||
            screen is Screen.Favorites || screen is Screen.Sessions ||
            screen is Screen.Settings || screen is Screen.WhatsAppSaver) {
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

    // Clipboard extraction action - invoked from FAB and HomeScreen
    val onFabAction: () -> Unit = {
        if (extractionState !is ExtractionState.Loading) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            val rawText = clip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty().trim()
            val isInstagramUrl = rawText.contains("instagram.com", ignoreCase = true) ||
                rawText.contains("instagr.am", ignoreCase = true)
            if (rawText.isNotEmpty() && isInstagramUrl) {
                extractionViewModel.extractMedia(rawText, authViewModel)
            } else {
                Toast.makeText(context, "Please copy an Instagram link first!", Toast.LENGTH_SHORT).show()
            }
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
            // Navigation is handled by the reactive extractionState router below
        }
    }

    //  Reactive extraction router 
    LaunchedEffect(extractionState) {
        when (val state = extractionState) {
            is ExtractionState.Success -> {
                val extractedPost = state.extractedPost
                if (extractedPost.mediaList.size > 1) {
                    // Multiple items → send to Carousel selection screen
                    navigateTo(Screen.DownloaderDetails)
                } else {
                    // Single item → auto-download immediately, stay on HomeScreen
                    extractionViewModel.downloadSelected(extractedPost, extractionViewModel.lastExtractedUrl)
                    Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                    extractionViewModel.reset()
                }
            }
            is ExtractionState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                extractionViewModel.reset()
            }
            else -> { /* Idle / Loading - no routing action needed */ }
        }
    }

    Scaffold(
        topBar = {
            if (currentScreen !is Screen.PostView && currentScreen !is Screen.Login && currentScreen !is Screen.Appearance && currentScreen !is Screen.DownloaderDetails && currentScreen !is Screen.AdvancedSettings && currentScreen !is Screen.WhatsAppStatusView) {
                VedInstaTopAppBar(
                    title = when(currentScreen) {
                        Screen.Home -> "Home"
                        Screen.History -> "History"
                        Screen.Favorites -> "Favorites"
                        Screen.Sessions -> "Sessions"
                        Screen.Settings -> "Settings"
                        Screen.About -> "About"
                        Screen.Notifications -> "Notifications"
                        Screen.PrivacyPolicy -> "Privacy Policy"
                        Screen.WhatsAppSaver -> "WA Status"
                        else -> "VedInsta"
                    },
                    showBackButton = currentScreen !is Screen.Home,
                    onBackClick = { navigateBack() },
                    actions = {
                        if (currentScreen == Screen.Home) {
                            // Notification badge icon
                            NotificationBadge(
                                notificationViewModel = notificationViewModel,
                                onClick = {
                                    notificationViewModel.markAllAsRead()
                                    navigateTo(Screen.Notifications)
                                }
                            )

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
        bottomBar = {
            if (currentScreen is Screen.Home || currentScreen is Screen.History ||
                currentScreen is Screen.Favorites || currentScreen is Screen.Sessions ||
                currentScreen is Screen.WhatsAppSaver) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.background,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Home,
                        onClick = { navigateTo(Screen.Home) },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.History,
                        onClick = { navigateTo(Screen.History) },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History") }
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Favorites,
                        onClick = { navigateTo(Screen.Favorites) },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                        label = { Text("Favorites") }
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.WhatsAppSaver,
                        onClick = { navigateTo(Screen.WhatsAppSaver) },
                        icon = { Icon(Icons.Default.Download, contentDescription = "WA Status") },
                        label = { Text("WA Status") }
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Sessions,
                        onClick = { navigateTo(Screen.Sessions) },
                        icon = { Icon(Icons.Default.AccountBox, contentDescription = "Sessions") },
                        label = { Text("Sessions") }
                    )
                }
            }
        },
        floatingActionButton = {
            // FAB is only visible on the HomeScreen
            if (currentScreen is Screen.Home) {
                val isLoading = extractionState is ExtractionState.Loading

                // Pre-compute the morphing polygon list once - avoids per-frame allocations
                // and keeps the GPU-driven animation fully smooth at 60/120fps.
                val loadingPolygons = remember {
                    listOf(
                        // 1. Rounded star with 6 points
                        RoundedPolygon.star(
                            numVerticesPerRadius = 6,
                            innerRadius = 0.55f,
                            rounding = CornerRounding(radius = 0.15f)
                        ),
                        // 2. Soft circle (12-sided with heavy rounding → looks circular)
                        RoundedPolygon(
                            numVertices = 12,
                            rounding = CornerRounding(radius = 1f)
                        ),
                        // 3. Squircle-like blob (4 vertices, heavy rounding)
                        RoundedPolygon(
                            numVertices = 4,
                            rounding = CornerRounding(radius = 0.5f)
                        )
                    )
                }

                FloatingActionButton(
                    onClick = { if (!isLoading) onFabAction() },
                    containerColor = if (isLoading)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                    else
                        MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    AnimatedContent(
                        targetState = isLoading,
                        transitionSpec = {
                            fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                        },
                        label = "FabContentAnim"
                    ) { loading ->
                        if (loading) {
                            // Material3 Expressive morphing LoadingIndicator
                            LoadingIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                polygons = loadingPolygons
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Paste & Download"
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val applyPadding = currentScreen !is Screen.PostView && currentScreen !is Screen.Login && currentScreen !is Screen.Appearance && currentScreen !is Screen.DownloaderDetails && currentScreen !is Screen.WhatsAppStatusView
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (applyPadding) paddingValues.calculateTopPadding() else 0.dp,
                    bottom = if (applyPadding) paddingValues.calculateBottomPadding() else 0.dp
                )
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    // Use smooth fade for PostView and WhatsAppStatusView (edge-to-edge) to avoid ditch effect
                    if (targetState is Screen.PostView || initialState is Screen.PostView ||
                        targetState is Screen.WhatsAppStatusView || initialState is Screen.WhatsAppStatusView) {
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
                saveableStateHolder.SaveableStateProvider(targetScreen::class.java.name) {
                    when (targetScreen) {
                        is Screen.Home -> {
                            HomeScreen(
                                mainViewModel = mainViewModel,
                                onFabAction = onFabAction,
                                onNavigateToFavorites = { navigateTo(Screen.Favorites) },
                                onNavigateToHistory = { navigateTo(Screen.History) },
                                onNavigateToSessions = { navigateTo(Screen.Sessions) },
                                onNavigateToWhatsAppSaver = { navigateTo(Screen.WhatsAppSaver) },
                                onPostClick = { post -> navigateTo(Screen.PostView(post)) }
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
                            val authState by authViewModel.authState.collectAsStateWithLifecycle()
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
                                onNavigateToAdvancedSettings = { navigateTo(Screen.AdvancedSettings) },
                                onThemeChanged = onThemeChanged
                            )
                        }
                        is Screen.AdvancedSettings -> {
                            AdvancedSettingsScreen(
                                onNavigateBack = { navigateBack() },
                                settingsViewModel = settingsViewModel
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
                            val notifications by notificationViewModel.allNotifications.observeAsState(emptyList())
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
                        is Screen.WhatsAppSaver -> {
                            WhatsAppSaverScreen(
                                viewModel = whatsAppViewModel,
                                onStatusClick = { index -> navigateTo(Screen.WhatsAppStatusView(index)) }
                            )
                        }
                        is Screen.WhatsAppStatusView -> {
                            WhatsAppStatusViewScreen(
                                viewModel = whatsAppViewModel,
                                initialIndex = targetScreen.initialIndex,
                                onNavigateBack = { navigateBack() }
                            )
                        }
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

@Composable
fun NotificationBadge(
    notificationViewModel: NotificationViewModel,
    onClick: () -> Unit
) {
    val unreadCount by notificationViewModel.unreadCount.observeAsState(0)
    IconButton(onClick = onClick) {
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
}
