package com.devson.vedinsta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.devson.vedinsta.database.DownloadedPost
import com.devson.vedinsta.database.FavoriteAccount
import com.devson.vedinsta.model.StoryTrayItem
import com.devson.vedinsta.viewmodel.MainViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    onFabAction: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSessions: () -> Unit,
    onNavigateToWhatsAppSaver: () -> Unit,
    onPostClick: (DownloadedPost) -> Unit,
    onNavigateToInstagramStory: (Int) -> Unit,
    contentPadding: PaddingValues
) {
    val recentPosts by mainViewModel.recentPostsHome.observeAsState(emptyList())
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val isRefreshingStories by mainViewModel.isRefreshingStories.collectAsState()
    val storyTray by mainViewModel.storyTray.collectAsState()
    val favoriteAccounts by mainViewModel.favoriteAccounts.observeAsState(emptyList())
    val isLoadingStories by mainViewModel.isLoadingStories.collectAsState()

    var showAddFavoriteDialog by remember { mutableStateOf(false) }
    var addFavoriteErrorMessage by remember { mutableStateOf<String?>(null) }
    var isAddingFavorite by remember { mutableStateOf(false) }

    if (showAddFavoriteDialog) {
        AddFavoriteDialog(
            onDismiss = {
                showAddFavoriteDialog = false
                addFavoriteErrorMessage = null
                isAddingFavorite = false
            },
            onAdd = { username ->
                isAddingFavorite = true
                addFavoriteErrorMessage = null
                mainViewModel.addFavoriteAccount(username) { error ->
                    isAddingFavorite = false
                    if (error == null) {
                        showAddFavoriteDialog = false
                    } else {
                        addFavoriteErrorMessage = error
                    }
                }
            },
            errorMessage = addFavoriteErrorMessage,
            isAdding = isAddingFavorite
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshingStories,
        onRefresh = { mainViewModel.fetchReelsTray(force = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding() + 8.dp))

            // Stories Section
            StoriesSection(
                storyTray = storyTray,
                favoriteAccounts = favoriteAccounts,
                onAddFavoriteClick = { showAddFavoriteDialog = true },
                onUserStoryClick = { userId, username, profilePicUrl ->
                    if (isLoadingStories) return@StoriesSection
                    mainViewModel.loadStoriesForUser(userId, username, profilePicUrl) { success ->
                        if (success) {
                            onNavigateToInstagramStory(0)
                        } else {
                            android.widget.Toast.makeText(context, "No active stories found for @$username", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onRemoveFavoriteClick = { username ->
                    mainViewModel.removeFavoriteAccount(username)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

        // 2. Quick Navigation Section Title
        Text(
            text = "Quick Actions",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // 2. Three columns of quick access cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Go to Favorites
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToFavorites() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorites",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Favorites",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Starred posts",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Card 2: Go to WA Status
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToWhatsAppSaver() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "WA Status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "WA Status",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Save statuses",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Card 3: Go to Sessions
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onNavigateToSessions() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.secondaryContainer,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBox,
                            contentDescription = "Sessions",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Sessions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Auth & login",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Recent Downloads Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent Downloads",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (recentPosts.isNotEmpty()) {
                TextButton(
                    onClick = { onNavigateToHistory() }
                ) {
                    Text(
                        text = "View all",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4. Horizontal Uncontained Carousel
        if (recentPosts.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Empty",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No media downloaded yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Start downloading high quality media now.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onFabAction() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Paste & Download")
                    }
                }
            }
        } else {
            val carouselCount = remember(recentPosts) {
                if (recentPosts.size > 9) 10 else recentPosts.size + 1
            }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(carouselCount) { i ->
                    if (i == carouselCount - 1) {
                        // See More card
                        Card(
                            modifier = Modifier
                                .width(186.dp)
                                .height(205.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable { onNavigateToHistory() },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "See More",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "See More",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "View history",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    } else {
                        // Post card
                        val post = recentPosts[i]
                        Card(
                            modifier = Modifier
                                .width(186.dp)
                                .height(205.dp)
                                .clip(MaterialTheme.shapes.extraLarge)
                                .clickable { onPostClick(post) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val imageRequest = remember(post.thumbnailPath) {
                                    ImageRequest.Builder(context)
                                        .data(if (post.thumbnailPath.isNotEmpty()) File(post.thumbnailPath) else null)
                                        .size(300, 300)
                                        .crossfade(true)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .memoryCacheKey(post.thumbnailPath)
                                        .diskCacheKey(post.thumbnailPath)
                                        .error(android.R.drawable.ic_menu_report_image)
                                        .build()
                                }
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = "Post Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(0.45f)
                                        .align(Alignment.BottomCenter)
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.85f)
                                                )
                                            )
                                        )
                                )

                                if (post.hasVideo) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .align(Alignment.Center)
                                            .background(
                                                Color.Black.copy(alpha = 0.6f),
                                                RoundedCornerShape(18.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Video",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "@${post.username}",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 24.dp))
        }

        if (isLoadingStories) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // Scrim blocking interaction
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFFE1306C), // IG Theme Pink-Red
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading stories...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StoriesSection(
    storyTray: List<StoryTrayItem>,
    favoriteAccounts: List<FavoriteAccount>,
    onAddFavoriteClick: () -> Unit,
    onUserStoryClick: (userId: String, username: String, profilePicUrl: String) -> Unit,
    onRemoveFavoriteClick: (String) -> Unit
) {
    var favoriteToRemove by remember { mutableStateOf<String?>(null) }
    
    if (favoriteToRemove != null) {
        RemoveFavoriteDialog(
            username = favoriteToRemove!!,
            onDismiss = { favoriteToRemove = null },
            onRemove = { onRemoveFavoriteClick(favoriteToRemove!!) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "Favorites",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(80.dp)
                        .clickable { onAddFavoriteClick() }
                        .padding(horizontal = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .padding(3.dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Favorite",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add Star",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            items(favoriteAccounts) { fav ->
                val trayMatch = storyTray.firstOrNull { it.username.equals(fav.username, ignoreCase = true) }
                val isSeen = trayMatch?.isSeen

                StoryAvatarItem(
                    username = fav.username,
                    profilePicUrl = fav.profilePicUrl,
                    isSeen = isSeen,
                    onClick = {
                        onUserStoryClick(trayMatch?.userId ?: "", fav.username, fav.profilePicUrl)
                    },
                    onLongClick = {
                        favoriteToRemove = fav.username
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Latest Stories",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        if (storyTray.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "No active stories found from followed accounts.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        } else {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(storyTray) { item ->
                    StoryAvatarItem(
                        username = item.username,
                        profilePicUrl = item.profilePicUrl,
                        isSeen = item.isSeen,
                        onClick = {
                            onUserStoryClick(item.userId, item.username, item.profilePicUrl)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StoryAvatarItem(
    username: String,
    profilePicUrl: String,
    isSeen: Boolean?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .then(
                if (onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    Modifier.clickable(onClick = onClick)
                }
            )
            .padding(horizontal = 4.dp)
    ) {
        val borderModifier = when (isSeen) {
            false -> {
                val igGradient = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF833AB4),
                        Color(0xFFFD1D1D),
                        Color(0xFFF56040),
                        Color(0xFFFCAF45),
                        Color(0xFF833AB4)
                    )
                )
                Modifier.border(2.5.dp, igGradient, CircleShape)
            }
            true -> {
                Modifier.border(1.5.dp, Color.Gray, CircleShape)
            }
            null -> {
                Modifier
            }
        }

        Box(
            modifier = Modifier
                .size(68.dp)
                .padding(3.dp)
                .then(borderModifier)
                .padding(3.dp)
        ) {
            AsyncImage(
                model = profilePicUrl,
                contentDescription = username,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = username,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AddFavoriteDialog(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    errorMessage: String?,
    isAdding: Boolean
) {
    var usernameInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Favorite Account") },
        text = {
            Column {
                Text("Enter the Instagram username of the account to star:", fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    label = { Text("Username") },
                    placeholder = { Text("e.g. instagram") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(usernameInput) },
                enabled = usernameInput.isNotBlank() && !isAdding
            ) {
                if (isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAdding) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RemoveFavoriteDialog(
    username: String,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove Favorite") },
        text = { Text("Are you sure you want to remove @$username from your Favorites?") },
        confirmButton = {
            TextButton(
                onClick = {
                    onRemove()
                    onDismiss()
                }
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
