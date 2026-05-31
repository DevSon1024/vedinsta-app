package com.devson.vedinsta.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.devson.vedinsta.model.MediaResult
import com.devson.vedinsta.viewmodel.ExtractionState
import com.devson.vedinsta.viewmodel.InstagramAuthState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel
import com.devson.vedinsta.viewmodel.MediaExtractionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSelectionScreen(
    authViewModel: InstagramAuthViewModel,
    extractionViewModel: MediaExtractionViewModel,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()
    val extractionState by extractionViewModel.extractionState.collectAsState()
    val selectedItems by extractionViewModel.selectedItems.collectAsState()

    var instagramUrl by remember { mutableStateOf("") }

    // Instagram gradient brush
    val instagramGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF833AB4), // Purple
                Color(0xFFFD1D1D), // Red
                Color(0xFFF77737)  // Orange
            )
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "VedInsta Downloader",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF121212)
                ),
                modifier = Modifier.background(instagramGradient)
            )
        },
        containerColor = Color(0xFF1A1A1A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 1. Session Status Indicator Card
            SessionStatusCard(
                authState = authState,
                onLogout = { authViewModel.logout() },
                onLogin = onNavigateToLogin
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Link Input & Paste Box
            LinkInputSection(
                url = instagramUrl,
                onUrlChange = { instagramUrl = it },
                onPasteClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                    instagramUrl = text
                },
                onExtractClick = {
                    extractionViewModel.extractMedia(instagramUrl, authViewModel)
                },
                isEnabled = authState is InstagramAuthState.LoggedIn
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Media Extraction Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (val state = extractionState) {
                    is ExtractionState.Idle -> {
                        Text(
                            "Paste an Instagram link above and click Extract to fetch posts/reels.",
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    is ExtractionState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFFD1D1D))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Running python extractor...", color = Color.White)
                        }
                    }
                    is ExtractionState.Error -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    is ExtractionState.Success -> {
                        if (state.mediaList.isEmpty()) {
                            Text("No media found in this post.", color = Color.Gray)
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Selection control headers
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${state.mediaList.size} media items found",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row {
                                        TextButton(onClick = { extractionViewModel.selectAll(state.mediaList) }) {
                                            Text("Select All", color = Color(0xFFF77737))
                                        }
                                        TextButton(onClick = { extractionViewModel.selectNone() }) {
                                            Text("Select None", color = Color.Gray)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Media Grid
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(state.mediaList) { item ->
                                        MediaItemCard(
                                            item = item,
                                            isSelected = selectedItems.contains(item.url),
                                            onToggleSelect = { item.url?.let { url -> extractionViewModel.toggleSelection(url) } }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Download Action Button
                                Button(
                                    onClick = {
                                        extractionViewModel.downloadSelected(state.mediaList, instagramUrl)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = selectedItems.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFFD1D1D),
                                        disabledContainerColor = Color.Gray
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "Download Selected (${selectedItems.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionStatusCard(
    authState: InstagramAuthState,
    onLogout: () -> Unit,
    onLogin: () -> Unit
) {
    val cardColor = when (authState) {
        is InstagramAuthState.LoggedIn -> Color(0xFF1E3A1E) // Sleek Dark Green
        is InstagramAuthState.SessionExpired -> Color(0xFF3D1D1D) // Sleek Dark Red
        else -> Color(0xFF2D2D2D) // Dark Grey
    }

    val borderColor = when (authState) {
        is InstagramAuthState.LoggedIn -> Color(0xFF4CAF50)
        is InstagramAuthState.SessionExpired -> Color(0xFFF44336)
        else -> Color(0xFF555555)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Session Status",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    when (authState) {
                        is InstagramAuthState.LoggedIn -> {
                            Text(
                                "Active (Logged in as: ${authState.dsUserId})",
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        is InstagramAuthState.SessionExpired -> {
                            Text(
                                "Session Expired (ID: ${authState.dsUserId})",
                                color = Color(0xFFE57373),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        is InstagramAuthState.Checking -> {
                            Text(
                                "Checking session...",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        else -> {
                            Text(
                                "No Session / Logged Out",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                when (authState) {
                    is InstagramAuthState.LoggedIn -> {
                        Button(
                            onClick = onLogout,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD1D1D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Logout", fontSize = 12.sp)
                        }
                    }
                    is InstagramAuthState.SessionExpired -> {
                        Button(
                            onClick = onLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFD1D1D)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Re-login", fontSize = 12.sp)
                        }
                    }
                    is InstagramAuthState.Checking -> {
                        // Empty/Loading
                    }
                    else -> {
                        Button(
                            onClick = onLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF833AB4)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Login", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = borderColor.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Used securely by Python engine (mo3.py) via netscape format file inside app's private filesDir to fetch posts and reels at full resolution.",
                color = Color.LightGray,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            if (authState is InstagramAuthState.SessionExpired) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Features restricted. Please log in again.",
                        color = Color(0xFFE57373),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LinkInputSection(
    url: String,
    onUrlChange: (String) -> Unit,
    onPasteClick: () -> Unit,
    onExtractClick: () -> Unit,
    isEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text("Instagram Post/Reel URL") },
                placeholder = { Text("https://www.instagram.com/p/...") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFD1D1D),
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color(0xFFFD1D1D),
                    unfocusedLabelColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                enabled = isEnabled
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onPasteClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.Gray),
                    enabled = isEnabled
                ) {
                    Text("Paste URL")
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onExtractClick,
                    modifier = Modifier.weight(1.5f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFD1D1D),
                        disabledContainerColor = Color.Gray
                    ),
                    enabled = isEnabled && url.isNotBlank()
                ) {
                    Text("Extract Media", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MediaItemCard(
    item: MediaResult,
    isSelected: Boolean,
    onToggleSelect: () -> Unit
) {
    val borderColor = if (isSelected) Color(0xFFFD1D1D) else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Media Preview Image
            AsyncImage(
                model = item.url,
                contentDescription = "Media Preview",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )

            // Dark overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            startY = 100f
                        )
                    )
            )

            // Checkbox overlay at Top-Right
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFFFD1D1D) else Color.Black.copy(alpha = 0.6f))
                    .border(1.5.dp, Color.White, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Media Info at Bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                // Type Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            if (item.type == "video") Color(0xFFFD1D1D) else Color(0xFF833AB4)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    if (item.type == "video") {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = item.type?.uppercase() ?: "IMAGE",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Resolution text
                val width = item.width ?: 0
                val height = item.height ?: 0
                Text(
                    text = if (width > 0 && height > 0) "${width}x${height}" else "Resolution unknown",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                // Item index info
                Text(
                    text = "Part ${item.index ?: 1}",
                    color = Color.LightGray,
                    fontSize = 10.sp
                )
            }
        }
    }
}
