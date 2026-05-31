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
    val selectedIndexes by extractionViewModel.selectedIndexes.collectAsState()
    val chosenQualities by extractionViewModel.chosenQualities.collectAsState()

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
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(instagramGradient)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                    is ExtractionState.Loading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Running python extractor...", color = MaterialTheme.colorScheme.onBackground)
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
                            Text("No media found in this post.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row {
                                        TextButton(onClick = { extractionViewModel.selectAll(state.mediaList) }) {
                                            Text("Select All", color = MaterialTheme.colorScheme.primary)
                                        }
                                        TextButton(onClick = { extractionViewModel.selectNone() }) {
                                            Text("Select None", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                                    items(state.mediaList, key = { it.index ?: 0 }) { item ->
                                        val idx = item.index ?: 1
                                        MediaItemCard(
                                            item = item,
                                            isSelected = selectedIndexes.contains(idx),
                                            chosenUrl = chosenQualities[idx],
                                            onToggleSelect = { extractionViewModel.toggleSelection(idx) },
                                            onQualityChange = { newUrl -> extractionViewModel.changeQuality(idx, newUrl) }
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
                                    enabled = selectedIndexes.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "Download Selected (${selectedIndexes.size})",
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
    // Check if the current theme is dark to adjust status card colors properly
    val isDark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue } < 1.5f

    val cardColor = when (authState) {
        is InstagramAuthState.LoggedIn -> if (isDark) Color(0xFF1E3A1E) else Color(0xFFE8F5E9) // Slate Green vs Light Green
        is InstagramAuthState.SessionExpired -> if (isDark) Color(0xFF3D1D1D) else Color(0xFFFFEBEE) // Slate Red vs Light Red
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val borderColor = when (authState) {
        is InstagramAuthState.LoggedIn -> Color(0xFF4CAF50)
        is InstagramAuthState.SessionExpired -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outline
    }

    val textColor = when (authState) {
        is InstagramAuthState.LoggedIn -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        is InstagramAuthState.SessionExpired -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    when (authState) {
                        is InstagramAuthState.LoggedIn -> {
                            Text(
                                "Active (Logged in as: ${authState.dsUserId})",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        is InstagramAuthState.SessionExpired -> {
                            Text(
                                "Session Expired (ID: ${authState.dsUserId})",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        is InstagramAuthState.Checking -> {
                            Text(
                                "Checking session...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        else -> {
                            Text(
                                "No Session / Logged Out",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Logout", fontSize = 12.sp)
                        }
                    }
                    is InstagramAuthState.SessionExpired -> {
                        Button(
                            onClick = onLogin,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Login", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Used securely by Python engine (mo3.py) via netscape format file inside app's private filesDir to fetch posts and reels at full resolution.",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontSize = 11.sp,
                lineHeight = 14.sp
            )

            if (authState is InstagramAuthState.SessionExpired) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = textColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Features restricted. Please log in again.",
                        color = textColor,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.outline,
                    focusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
    chosenUrl: String?,
    onToggleSelect: () -> Unit,
    onQualityChange: (String) -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    var showQualityMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onToggleSelect() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Media Preview Image (Load from chosen quality URL or fallback)
            AsyncImage(
                model = chosenUrl ?: item.url,
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

            // Checkbox overlay at Top-Right (always styled with white check/border over image overlay)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.6f))
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

            // Media Info at Bottom (rendered over dark gradient, so text is white for legibility)
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
                            if (item.type == "video") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
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

                // Resolution Selection Pill
                val qualities = item.qualities ?: emptyList()
                val currentQuality = qualities.find { it.url == chosenUrl }
                val width = currentQuality?.width ?: item.width ?: 0
                val height = currentQuality?.height ?: item.height ?: 0
                val resolutionText = if (width > 0 && height > 0) "${width}x${height}" else "Default Quality"

                Box {
                    Surface(
                        modifier = Modifier.clickable { showQualityMenu = true },
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "$resolutionText ▾",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showQualityMenu,
                        onDismissRequest = { showQualityMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                    ) {
                        if (qualities.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Default", color = MaterialTheme.colorScheme.onSurface) },
                                onClick = { showQualityMenu = false }
                            )
                        } else {
                            qualities.forEachIndexed { i, q ->
                                val label = if (i == 0) "${q.width}x${q.height} (High)" else "${q.width}x${q.height}"
                                DropdownMenuItem(
                                    text = { Text(label, color = MaterialTheme.colorScheme.onSurface) },
                                    onClick = {
                                        q.url?.let(onQualityChange)
                                        showQualityMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

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
