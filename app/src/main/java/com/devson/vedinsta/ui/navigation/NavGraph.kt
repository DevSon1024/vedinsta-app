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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.ui.*
import com.devson.vedinsta.viewmodel.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.compose.foundation.pager.rememberPagerState
import com.devson.vedinsta.database.NotificationType
import com.devson.vedinsta.ui.screen.InstagramLoginScreen
import com.devson.vedinsta.ui.screen.MediaSelectionCarouselScreen
import com.devson.vedinsta.ui.screen.NotificationsScreen
import com.devson.vedinsta.ui.screen.PostViewScreen
import com.devson.vedinsta.ui.screen.WhatsAppStatusViewScreen
import com.devson.vedinsta.ui.screen.setting.AboutScreen
import com.devson.vedinsta.ui.screen.setting.AdvancedSettingsScreen
import com.devson.vedinsta.ui.screen.setting.AppearanceSettingsScreen
import com.devson.vedinsta.ui.screen.setting.PrivacyPolicyScreen
import com.devson.vedinsta.ui.screen.setting.SecurityLimitsScreen
import com.devson.vedinsta.ui.screen.setting.SettingsScreen

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
    val hazeState = remember { HazeState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()

    var gridColumnCount by remember { mutableStateOf(settingsViewModel.gridColumnCount) }
    var isListView by remember { mutableStateOf(settingsViewModel.isListView) }
    var showViewSettingsSheet by remember { mutableStateOf(false) }
    val viewSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val extractionState by extractionViewModel.extractionState.collectAsStateWithLifecycle()
    val showRateLimitDialog by extractionViewModel.showRateLimitDialog.collectAsStateWithLifecycle()
    val isVpnActive by mainViewModel.isVpnActive.collectAsStateWithLifecycle()
    val isNetworkChanged by mainViewModel.isNetworkChanged.collectAsStateWithLifecycle()
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val isSessionActive = authState is InstagramAuthState.LoggedIn

    val isBlurEnabled by settingsViewModel.isBlurEnabled.collectAsStateWithLifecycle()
    val blurOpacity by settingsViewModel.blurOpacity.collectAsStateWithLifecycle()
    val blurRadius by settingsViewModel.blurRadius.collectAsStateWithLifecycle()

    val favoritePostIds by mainViewModel.favoritePostIds.collectAsStateWithLifecycle()
    val isFavoriteHelper = { postId: String -> favoritePostIds.contains(postId) }
    val toggleFavoriteHelper: (String) -> Unit = { postId ->
        mainViewModel.toggleFavorite(postId)
    }

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

    BackHandler(enabled = currentRoute != Screen.MainPager.route || pagerState.currentPage > 0) {
        if (currentRoute == Screen.MainPager.route) {
            scope.launch { pagerState.animateScrollToPage(0) }
        } else {
            navController.popBackStack()
        }
    }

    LaunchedEffect(intent) {
        val url = intent?.getStringExtra("POST_URL") ?: intent?.getStringExtra("instagram_url")
        if (!url.isNullOrEmpty()) {
            com.devson.vedinsta.notification.VedInstaNotificationManager.getInstance(context).cancelMultipleContentNotification()
            extractionViewModel.extractMedia(url, authViewModel)
            intent?.removeExtra("POST_URL")
            intent?.removeExtra("instagram_url")
        }
    }

    LaunchedEffect(extractionState) {
        when (val state = extractionState) {
            is ExtractionState.Success -> {
                val extractedPost = state.extractedPost
                if (extractedPost.mediaList.size > 1) {
                    navController.navigate(Screen.DownloaderDetails.route)
                } else {
                    extractionViewModel.downloadSelected(extractedPost, extractionViewModel.lastExtractedUrl)
                    Toast.makeText(context, "Download started!", Toast.LENGTH_SHORT).show()
                    extractionViewModel.reset()
                }
            }
            is ExtractionState.Error -> {
                val msg = state.message
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                
                if (msg.contains("sessionid missing", ignoreCase = true) ||
                    msg.contains("re-export cookies", ignoreCase = true) ||
                    msg.contains("cookie file not found", ignoreCase = true) ||
                    msg.contains("Login required", ignoreCase = true) ||
                    msg.contains("API returned 401", ignoreCase = true) ||
                    msg.contains("API returned 403", ignoreCase = true) ||
                    msg.contains("Session expired", ignoreCase = true)) {
                    navController.navigate(Screen.Login.route)
                }
                
                extractionViewModel.reset()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            if (currentRoute != Screen.PostView.route &&
                currentRoute != Screen.Login.route &&
                currentRoute != Screen.Appearance.route &&
                currentRoute != Screen.DownloaderDetails.route &&
                currentRoute != Screen.AdvancedSettings.route &&
                currentRoute != Screen.WhatsAppStatusView.route) {
                VedInstaTopAppBar(
                    title = if (currentRoute == Screen.MainPager.route) {
                        when (pagerState.currentPage) {
                            0 -> "Home"
                            1 -> "History"
                            2 -> "Favorites"
                            3 -> "WA Status"
                            4 -> "Sessions"
                            else -> "VedInsta"
                        }
                    } else {
                        when (currentRoute) {
                            Screen.Settings.route -> "Settings"
                            Screen.About.route -> "About"
                            Screen.Notifications.route -> "Notifications"
                            Screen.PrivacyPolicy.route -> "Privacy Policy"
                            else -> "VedInsta"
                        }
                    },
                    showBackButton = currentRoute != Screen.MainPager.route,
                    onBackClick = { navController.popBackStack() },
                    containerColor = if (isBlurEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    modifier = if (isBlurEnabled) {
                        Modifier.hazeChild(
                            state = hazeState,
                            style = HazeStyle(
                                blurRadius = blurRadius.dp,
                                tint = MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity)
                            )
                        )
                    } else {
                        Modifier
                    },
                    actions = {
                        if (currentRoute == Screen.MainPager.route) {
                            if (pagerState.currentPage == 0) {
                                NotificationBadge(
                                    notificationViewModel = notificationViewModel,
                                    onClick = {
                                        notificationViewModel.markAllAsRead()
                                        navController.navigate(Screen.Notifications.route)
                                    }
                                )

                                IconButton(
                                    onClick = { navController.navigate(Screen.Settings.route) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (pagerState.currentPage == 1 || pagerState.currentPage == 2) {
                                IconButton(onClick = { showViewSettingsSheet = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = "View Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { navController.navigate(Screen.Settings.route) },
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
                    }
                )
            }
        },
        bottomBar = {
            if (currentRoute == Screen.MainPager.route) {
                NavigationBar(
                    containerColor = if (isBlurEnabled) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.background
                    },
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars,
                    modifier = Modifier
                        .let { modifier ->
                            if (isBlurEnabled) {
                                modifier.hazeChild(
                                    state = hazeState,
                                    style = HazeStyle(
                                        blurRadius = blurRadius.dp,
                                        tint = MaterialTheme.colorScheme.surface.copy(alpha = blurOpacity)
                                    )
                                )
                            } else {
                                modifier
                            }
                        }
                        .height(60.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                ) {
                    NavigationBarItem(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        icon = { Icon(Icons.Default.History, contentDescription = "History") },
                        label = { Text("History", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 2,
                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                        label = { Text("Favorites", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 3,
                        onClick = { scope.launch { pagerState.animateScrollToPage(3) } },
                        icon = { Icon(Icons.Default.Download, contentDescription = "WA Status") },
                        label = { Text("WA Status", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        alwaysShowLabel = false
                    )
                    NavigationBarItem(
                        selected = pagerState.currentPage == 4,
                        onClick = { scope.launch { pagerState.animateScrollToPage(4) } },
                        icon = { Icon(Icons.Default.AccountBox, contentDescription = "Sessions") },
                        label = { Text("Sessions", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        alwaysShowLabel = false
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentRoute == Screen.MainPager.route && pagerState.currentPage == 0) {
                val isLoading = extractionState is ExtractionState.Loading

                val loadingPolygons = remember {
                    listOf(
                        RoundedPolygon.star(
                            numVerticesPerRadius = 6,
                            innerRadius = 0.55f,
                            rounding = CornerRounding(radius = 0.15f)
                        ),
                        RoundedPolygon(
                            numVertices = 12,
                            rounding = CornerRounding(radius = 1f)
                        ),
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
        val applyPadding = currentRoute != Screen.PostView.route &&
            currentRoute != Screen.Login.route &&
            currentRoute != Screen.Appearance.route &&
            currentRoute != Screen.DownloaderDetails.route &&
            currentRoute != Screen.WhatsAppStatusView.route
        val screenPadding = if (isBlurEnabled && applyPadding) paddingValues else PaddingValues(0.dp)
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let { modifier ->
                    if (isBlurEnabled) {
                        modifier.haze(hazeState)
                    } else {
                        modifier
                    }
                }
                .padding(
                    top = if (applyPadding && !isBlurEnabled) paddingValues.calculateTopPadding() else 0.dp,
                    bottom = if (applyPadding && !isBlurEnabled) paddingValues.calculateBottomPadding() else 0.dp
                )
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.MainPager.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                exitTransition = {
                    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                },
                popEnterTransition = {
                    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                },
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                }
            ) {
                composable(Screen.MainPager.route) {
                    MainPagerScreen(
                        pagerState = pagerState,
                        mainViewModel = mainViewModel,
                        authViewModel = authViewModel,
                        whatsAppViewModel = whatsAppViewModel,
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
                        onFabAction = onFabAction,
                        onPostClick = { post ->
                            navController.navigate(Screen.PostView.createRoute(post.postId))
                        },
                        onNavigateToWhatsAppStatus = { index ->
                            navController.navigate(Screen.WhatsAppStatusView.createRoute(index))
                        },
                        onNavigateToLogin = {
                            navController.navigate(Screen.Login.route)
                        },
                        onNavigateToSecurityLimits = {
                            navController.navigate(Screen.SecurityLimits.route)
                        },
                        contentPadding = screenPadding
                    )
                }
                composable(Screen.DownloaderDetails.route) {
                    MediaSelectionCarouselScreen(
                        authViewModel = authViewModel,
                        extractionViewModel = extractionViewModel,
                        onNavigateBack = {
                            extractionViewModel.reset()
                            navController.popBackStack()
                        },
                        onNavigateToNotifications = {
                            extractionViewModel.reset()
                            navController.navigate(Screen.Notifications.route) {
                                popUpTo(Screen.MainPager.route)
                            }
                            notificationViewModel.markAllAsRead()
                        }
                    )
                }
                composable(Screen.Login.route) {
                    InstagramLoginScreen(
                        authViewModel = authViewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                    val authState by authViewModel.authState.collectAsStateWithLifecycle()
                    LaunchedEffect(authState) {
                        if (authState is InstagramAuthState.LoggedIn) {
                            navController.popBackStack()
                        }
                    }
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        settingsViewModel = settingsViewModel,
                        onNavigateToAbout = { navController.navigate(Screen.About.route) },
                        onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
                        onNavigateToPrivacyPolicy = { navController.navigate(Screen.PrivacyPolicy.route) },
                        onNavigateToAdvancedSettings = { navController.navigate(Screen.AdvancedSettings.route) },
                        onNavigateToSecurityLimits = { navController.navigate(Screen.SecurityLimits.route) },
                        onThemeChanged = onThemeChanged,
                        contentPadding = screenPadding
                    )
                }
                composable(Screen.AdvancedSettings.route) {
                    AdvancedSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        settingsViewModel = settingsViewModel
                    )
                }
                composable(Screen.Appearance.route) {
                    AppearanceSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        settingsViewModel = settingsViewModel
                    )
                }
                composable(Screen.SecurityLimits.route) {
                    SecurityLimitsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        settingsViewModel = settingsViewModel
                    )
                }
                composable(Screen.About.route) {
                    AboutScreen()
                }
                composable(Screen.PrivacyPolicy.route) {
                    PrivacyPolicyScreen()
                }
                composable(Screen.Notifications.route) {
                    val notifications by notificationViewModel.allNotifications.observeAsState(emptyList())
                    LaunchedEffect(Unit) {
                        notificationViewModel.pruneNotifications(settingsViewModel.maxNotificationsLimit)
                    }
                    NotificationsScreen(
                        notifications = notifications,
                        onNotificationClick = { notification ->
                            notificationViewModel.markAsRead(notification.id)
                            if (notification.type == NotificationType.DOWNLOAD_COMPLETED && notification.postId != null) {
                                scope.launch {
                                    val post = mainViewModel.getPostById(notification.postId)
                                    if (post != null) {
                                        navController.navigate(
                                            Screen.PostView.createRoute(
                                                notification.postId
                                            )
                                        )
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Post files were deleted or moved",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        onDeleteClick = { id -> notificationViewModel.deleteNotification(id) },
                        onCancelClick = { item -> notificationViewModel.cancelDownload(item) },
                        onRetryClick = { item -> notificationViewModel.retryDownload(item) },
                        settingsViewModel = settingsViewModel,
                        notificationViewModel = notificationViewModel,
                        contentPadding = screenPadding
                    )
                }
                composable(
                    route = Screen.PostView.route,
                    arguments = listOf(navArgument("postId") { type = NavType.StringType }),
                    enterTransition = { fadeIn(tween(250)) },
                    exitTransition = { fadeOut(tween(250)) },
                    popEnterTransition = { fadeIn(tween(250)) },
                    popExitTransition = { fadeOut(tween(250)) }
                ) { backStackEntry ->
                    val postId = backStackEntry.arguments?.getString("postId").orEmpty()
                    var post by remember { mutableStateOf<DownloadedPost?>(null) }
                    
                    LaunchedEffect(postId) {
                        post = mainViewModel.getPostById(postId)
                    }
                    
                    post?.let { loadedPost ->
                        PostViewScreen(
                            post = loadedPost,
                            isFavorite = isFavoriteHelper,
                            onToggleFavorite = toggleFavoriteHelper,
                            onBackClick = { navController.popBackStack() },
                            onDeletePost = { deletedPost ->
                                mainViewModel.deleteDownloadedPost(deletedPost)
                                Toast.makeText(context, "Post deleted", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            }
                        )
                    }
                }
                composable(
                    route = Screen.WhatsAppStatusView.route,
                    arguments = listOf(navArgument("initialIndex") { type = NavType.IntType }),
                    enterTransition = { fadeIn(tween(250)) },
                    exitTransition = { fadeOut(tween(250)) },
                    popEnterTransition = { fadeIn(tween(250)) },
                    popExitTransition = { fadeOut(tween(250)) }
                ) { backStackEntry ->
                    val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
                    WhatsAppStatusViewScreen(
                        viewModel = whatsAppViewModel,
                        initialIndex = initialIndex,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // 429 Too Many Requests Rate Limit Dialog
            if (showRateLimitDialog) {
                AlertDialog(
                    onDismissRequest = { extractionViewModel.dismissRateLimitDialog() },
                    title = {
                        Text(
                            text = "Too Many Downloads At Once",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    text = {
                        Text(text = "Instagram has asked us to slow down. To keep your account completely safe from flags, Vedinsta will pause downloads for 15 minutes. Please take a short break!")
                    },
                    confirmButton = {
                        TextButton(onClick = { extractionViewModel.dismissRateLimitDialog() }) {
                            Text("OK")
                        }
                    }
                )
            }

            var showVpnWarningDialog by remember { mutableStateOf(false) }
            var showNetworkChangeWarningDialog by remember { mutableStateOf(false) }

            LaunchedEffect(isVpnActive, isSessionActive) {
                if (isSessionActive && isVpnActive) {
                    showVpnWarningDialog = true
                } else {
                    showVpnWarningDialog = false
                }
            }

            LaunchedEffect(isNetworkChanged, isSessionActive) {
                if (isSessionActive && isNetworkChanged) {
                    showNetworkChangeWarningDialog = true
                }
            }

            if (showVpnWarningDialog) {
                AlertDialog(
                    onDismissRequest = { showVpnWarningDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "VPN Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text(
                            text = "VPN Active",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(text = "Using a VPN while downloading can sometimes cause Instagram to flag your account. We recommend turning it off for the best safety.")
                    },
                    confirmButton = {
                        TextButton(onClick = { showVpnWarningDialog = false }) {
                            Text("Understand")
                        }
                    }
                )
            }

            if (showNetworkChangeWarningDialog) {
                AlertDialog(
                    onDismissRequest = { 
                        showNetworkChangeWarningDialog = false
                        mainViewModel.resetNetworkChangeWarning()
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Network Change Warning",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    title = {
                        Text(
                            text = "Network Switched",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(text = "We detected a switch in your network connection. Switching networks or VPNs while downloading can look suspicious to Instagram. Please try to stay on a stable connection.")
                    },
                    confirmButton = {
                        TextButton(onClick = { 
                            showNetworkChangeWarningDialog = false
                            mainViewModel.resetNetworkChangeWarning()
                        }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
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
