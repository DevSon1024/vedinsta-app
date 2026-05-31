package com.devson.vedinsta

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.database.NotificationEntity
import com.devson.vedinsta.database.NotificationType
import com.devson.vedinsta.ui.*
import com.devson.vedinsta.ui.theme.VedinstaTheme
import com.devson.vedinsta.viewmodel.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

sealed class Screen {
    object Downloader : Screen()
    object History : Screen()
    object Favorites : Screen()
    object Sessions : Screen()
    object Settings : Screen()
    data class PostView(val post: DownloadedPost) : Screen()
    object About : Screen()
    object Notifications : Screen()
    object Login : Screen()
}

class MainActivity : ComponentActivity() {

    private val authViewModel: InstagramAuthViewModel by viewModels()
    private val extractionViewModel: MediaExtractionViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

    private var pendingUrlAction: ((String) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val settingsManager = SettingsManager(this)

        setContent {
            var appThemeState by remember { mutableStateOf(settingsManager.appTheme) }
            val darkTheme = when (appThemeState) {
                1 -> false
                2 -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            VedinstaTheme(darkTheme = darkTheme) {
                val screenStack = remember { mutableStateListOf<Screen>(Screen.Downloader) }
                val currentScreen = screenStack.last()

                val posts by mainViewModel.allDownloadedPosts.observeAsState(emptyList())
                val notifications by notificationViewModel.allNotifications.observeAsState(emptyList())
                val unreadCount by notificationViewModel.unreadCount.observeAsState(0)

                var gridColumnCount by remember { mutableStateOf(settingsManager.gridColumnCount) }
                var showGridSizeDialog by remember { mutableStateOf(false) }

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
                    if (screen is Screen.Downloader || screen is Screen.History ||
                        screen is Screen.Favorites || screen is Screen.Sessions ||
                        screen is Screen.Settings) {
                        screenStack.clear()
                    }
                    screenStack.add(screen)
                }

                fun navigateBack() {
                    if (screenStack.size > 1) {
                        screenStack.removeAt(screenStack.lastIndex)
                    }
                }

                // Handle system back button
                BackHandler(enabled = screenStack.size > 1) {
                    navigateBack()
                }

                // Setup callback for shared links / onNewIntent
                LaunchedEffect(Unit) {
                    pendingUrlAction = { url ->
                        navigateTo(Screen.Downloader)
                    }
                    handleIntent(intent)
                }

                Scaffold(
                    topBar = {
                        if (currentScreen !is Screen.PostView && currentScreen !is Screen.Login) {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = when(currentScreen) {
                                            Screen.Downloader -> "Downloader"
                                            Screen.History -> "History"
                                            Screen.Favorites -> "Favorites"
                                            Screen.Sessions -> "Sessions"
                                            Screen.Settings -> "Settings"
                                            Screen.About -> "About"
                                            Screen.Notifications -> "Notifications"
                                            else -> "VedInsta"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                navigationIcon = {
                                    if (screenStack.size > 1) {
                                        IconButton(onClick = { navigateBack() }) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = "Back",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                actions = {
                                    // Grid Size Controller (only in History or Favorites screen)
                                    if (currentScreen == Screen.History || currentScreen == Screen.Favorites) {
                                        IconButton(onClick = { showGridSizeDialog = true }) {
                                            Icon(
                                                imageVector = Icons.Default.Menu,
                                                contentDescription = "Grid Size",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Notification badge icon
                                    if (currentScreen != Screen.Notifications) {
                                        Box(
                                            modifier = Modifier.padding(end = 8.dp)
                                        ) {
                                            IconButton(onClick = {
                                                notificationViewModel.markAllAsRead()
                                                navigateTo(Screen.Notifications)
                                            }) {
                                                Icon(
                                                    imageVector = Icons.Default.Notifications,
                                                    contentDescription = "Notifications",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (unreadCount > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .align(Alignment.TopEnd)
                                                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                                        color = MaterialTheme.colorScheme.onError,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    },
                    bottomBar = {
                        // Display bottom bar only for the main screens
                        if (currentScreen is Screen.Downloader || currentScreen is Screen.History ||
                            currentScreen is Screen.Favorites || currentScreen is Screen.Sessions ||
                            currentScreen is Screen.Settings) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.AddCircle, contentDescription = "Downloader") },
                                    label = { Text("Downloader") },
                                    selected = currentScreen is Screen.Downloader,
                                    onClick = { navigateTo(Screen.Downloader) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.List, contentDescription = "History") },
                                    label = { Text("History") },
                                    selected = currentScreen is Screen.History,
                                    onClick = { navigateTo(Screen.History) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Favorites") },
                                    label = { Text("Favorites") },
                                    selected = currentScreen is Screen.Favorites,
                                    onClick = { navigateTo(Screen.Favorites) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.AccountBox, contentDescription = "Sessions") },
                                    label = { Text("Sessions") },
                                    selected = currentScreen is Screen.Sessions,
                                    onClick = { navigateTo(Screen.Sessions) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings") },
                                    selected = currentScreen is Screen.Settings,
                                    onClick = { navigateTo(Screen.Settings) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        indicatorColor = Color.Transparent
                                    )
                                )
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        when (currentScreen) {
                            is Screen.Downloader -> {
                                MediaSelectionScreen(
                                    authViewModel = authViewModel,
                                    extractionViewModel = extractionViewModel,
                                    onNavigateToLogin = { navigateTo(Screen.Login) }
                                )
                            }
                            is Screen.History -> {
                                HistoryScreen(
                                    posts = posts,
                                    gridColumnCount = gridColumnCount,
                                    isFavorite = isFavoriteHelper,
                                    onToggleFavorite = toggleFavoriteHelper,
                                    onPostClick = { post -> navigateTo(Screen.PostView(post)) },
                                    onPostLongClick = { post ->
                                        // Show options dialog or delete
                                        showPostOptions(this@MainActivity, post, mainViewModel, toggleFavoriteHelper, isFavoriteHelper(post.postId))
                                    }
                                )
                            }
                            is Screen.Favorites -> {
                                FavoritesScreen(
                                    posts = posts,
                                    gridColumnCount = gridColumnCount,
                                    isFavorite = isFavoriteHelper,
                                    onToggleFavorite = toggleFavoriteHelper,
                                    onPostClick = { post -> navigateTo(Screen.PostView(post)) },
                                    onPostLongClick = { post ->
                                        showPostOptions(this@MainActivity, post, mainViewModel, toggleFavoriteHelper, isFavoriteHelper(post.postId))
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
                                    onThemeChanged = { newTheme ->
                                        appThemeState = newTheme
                                    }
                                )
                            }
                            is Screen.About -> {
                                AboutScreen()
                            }
                            is Screen.Notifications -> {
                                NotificationsScreen(
                                    notifications = notifications,
                                    onNotificationClick = { id -> notificationViewModel.markAsRead(id) },
                                    onDeleteClick = { id -> notificationViewModel.deleteNotification(id) }
                                )
                            }
                            is Screen.PostView -> {
                                PostViewScreen(
                                    post = currentScreen.post,
                                    onBackClick = { navigateBack() },
                                    onDeletePost = { post ->
                                        mainViewModel.deleteDownloadedPost(post)
                                        // Delete physical files
                                        post.mediaPaths.forEach { path ->
                                            try {
                                                val file = File(path)
                                                if (file.exists()) file.delete()
                                            } catch (e: Exception) {}
                                        }
                                        Toast.makeText(this@MainActivity, "Post deleted", Toast.LENGTH_SHORT).show()
                                        navigateBack()
                                    }
                                )
                            }
                        }
                    }
                }

                // Grid Columns Selector Dialog
                if (showGridSizeDialog) {
                    AlertDialog(
                        onDismissRequest = { showGridSizeDialog = false },
                        title = { Text("Grid Columns", color = Color.White) },
                        text = {
                            Row(
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                (2..4).forEach { cols ->
                                    Button(
                                        onClick = {
                                            settingsManager.gridColumnCount = cols
                                            gridColumnCount = cols
                                            showGridSizeDialog = false
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (gridColumnCount == cols) Color(0xFFFD1D1D) else Color(0xFF333333)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("$cols Cols")
                                    }
                                }
                            }
                        },
                        confirmButton = {},
                        containerColor = Color(0xFF1E1E1E)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.getStringExtra("POST_URL") ?: intent?.getStringExtra("instagram_url")
        if (!url.isNullOrEmpty()) {
            extractionViewModel.extractMedia(url, authViewModel)
            pendingUrlAction?.invoke(url)
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

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val version = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "Version ${packageInfo.versionName}"
        } catch (e: Exception) {
            "Version Unknown"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "VedInsta",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = version,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "VedInsta is a modern, high-fidelity application that allows you to download Instagram posts, carousels, reels, and stories at multiple resolutions and qualities. Engineered with a secure on-device Python extraction sandbox.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DevSon1024/vedinsta-app"))
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text("Visit GitHub Repository", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DevSon1024/vedinsta-app/issues"))
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Text("Report an Issue")
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    notifications: List<NotificationEntity>,
    onNotificationClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit
) {
    if (notifications.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("No Notifications", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(notifications, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNotificationClick(item.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { onDeleteClick(item.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}