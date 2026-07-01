package com.devson.vedinsta.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.vedinsta.viewmodel.InstagramAuthState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel

@Composable
fun SessionsScreen(
    authViewModel: InstagramAuthViewModel,
    onNavigateToLogin: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    val showRawIdDialog by authViewModel.showRawIdDialog.collectAsStateWithLifecycle()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showEditUsernameDialog by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue } < 1.5f

    val cardColor = when (authState) {
        is InstagramAuthState.LoggedIn -> {
            if (isDark) Color(0xFF1E3A1E) else Color(0xFFE8F5E9)
        }
        is InstagramAuthState.SessionExpired -> {
            if (isDark) Color(0xFF3D1D1D) else Color(0xFFFFEBEE)
        }
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = when (authState) {
        is InstagramAuthState.LoggedIn -> Color(0xFF4CAF50)
        is InstagramAuthState.SessionExpired -> Color(0xFFF44336)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val textColor = when (authState) {
        is InstagramAuthState.LoggedIn -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        is InstagramAuthState.SessionExpired -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(contentPadding.calculateTopPadding() + 16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "INSTAGRAM SESSION",
                                color = textColor.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        val statusText = when (authState) {
                            is InstagramAuthState.LoggedIn -> "ACTIVE"
                            is InstagramAuthState.SessionExpired -> "EXPIRED"
                            is InstagramAuthState.Checking -> "CHECKING"
                            else -> "DISCONNECTED"
                        }
                        val pillBg = when (authState) {
                            is InstagramAuthState.LoggedIn -> Color(0xFF2E7D32)
                            is InstagramAuthState.SessionExpired -> Color(0xFFC62828)
                            else -> MaterialTheme.colorScheme.outline
                        }
                        Box(
                            modifier = Modifier
                                .background(pillBg, RoundedCornerShape(100.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = statusText,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (val state = authState) {
                        is InstagramAuthState.LoggedIn -> {
                            // Username row with inline edit button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "@${state.username.ifEmpty { state.dsUserId }}",
                                    color = textColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = { showEditUsernameDialog = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit username",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Session is verified. You can download high-quality posts, reels, and stories.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        is InstagramAuthState.SessionExpired -> {
                            Text(
                                text = "Session Expired",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = state.reason,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        is InstagramAuthState.Checking -> {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = "Not Logged In",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Sign in to your Instagram account via secure WebView to enable private media downloading.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when (authState) {
                        is InstagramAuthState.LoggedIn -> {
                            Button(
                                onClick = { showLogoutDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Log Out Session", fontWeight = FontWeight.Bold)
                            }
                        }
                        is InstagramAuthState.SessionExpired -> {
                            Button(
                                onClick = onNavigateToLogin,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Re-login to Account", fontWeight = FontWeight.Bold)
                            }
                        }
                        is InstagramAuthState.Checking -> {
                            // Loading
                        }
                        else -> {
                            Button(
                                onClick = onNavigateToLogin,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sign In with Instagram", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            InfoSectionCard(
                icon = Icons.Default.Speed,
                title = "Download Safety Guidelines",
                introText = "Instagram implements strict rate limits on third-party requests. To shield your account from action blocks or temporary restrictions, please adhere to these precautions:",
                items = listOf(
                    InfoItem(
                        boldText = "Download Intervals:",
                        descText = "Wait at least 5-10 seconds between downloads. Rapid batch downloading mimics robotic queries and can flag your session."
                    ),
                    InfoItem(
                        boldText = "Hourly Safety Threshold:",
                        descText = "Keep your downloads below 30-50 media files per hour to remain safe."
                    ),
                    InfoItem(
                        boldText = "Daily Safety Threshold:",
                        descText = "Limit total daily downloads to 150-200 media files to avoid account flags."
                    ),
                    InfoItem(
                        boldText = "Simultaneous Activity:",
                        descText = "Avoid running automated bots or accessing your account via other scraping tools concurrently while utilizing this session."
                    )
                )
            )

            InfoSectionCard(
                icon = Icons.Default.Lock,
                title = "Secure Local Storage",
                introText = "VedInsta prioritizes your security. All session authentication keys are managed locally within Android's sandboxed environment:",
                items = listOf(
                    InfoItem(
                        boldText = "Sandboxed Cookies File:",
                        descText = "Cookies are saved strictly on your local phone storage in Netscape format: /data/data/com.devson.vedinsta/files/instagram_cookies.txt."
                    ),
                    InfoItem(
                        boldText = "Linux Kernel Security:",
                        descText = "Android isolates this app directory, meaning no other application, third party, or ADB utility can read your cookies file."
                    ),
                    InfoItem(
                        boldText = "Zero External Transmissions:",
                        descText = "Your cookies and credentials never leave your device. They are sent directly to Instagram's official servers for media authentication."
                    ),
                    InfoItem(
                        boldText = "Uninstall Wiping:",
                        descText = "When VedInsta is uninstalled, Android completely purges the entire sandbox. No credentials or files are left behind."
                    )
                )
            )

            InfoSectionCard(
                icon = Icons.Default.Info,
                title = "Session Preservation Tips",
                introText = "Follow these best practices to ensure your authenticated session remains active for months without expiring:",
                items = listOf(
                    InfoItem(
                        boldText = "Avoid Official App Logouts:",
                        descText = "Choosing 'Log out of all devices' or logging out from the official Instagram app will invalidate all active session tokens globally, including this one."
                    ),
                    InfoItem(
                        boldText = "Password Alterations:",
                        descText = "Changing your Instagram password automatically revokes all active cookies. If you change your password, you must re-login to VedInsta."
                    ),
                    InfoItem(
                        boldText = "Log Out Session Behavior:",
                        descText = "Tapping 'Log Out Session' in this app only deletes the cookies stored locally on this phone. It does not affect your login status on other devices."
                    )
                )
            )
            Spacer(modifier = Modifier.height(contentPadding.calculateBottomPadding() + 16.dp))
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(text = "Log Out Session", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(text = "Are you sure you want to log out? This will remove your saved Instagram session cookies from this app.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.logout()
                    }
                ) {
                    Text(text = "Log Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    // Edit username dialog - shown when the user taps the Edit icon on the session card
    if (showEditUsernameDialog) {
        val currentUsername = (authState as? InstagramAuthState.LoggedIn)
            ?.username?.ifEmpty { (authState as? InstagramAuthState.LoggedIn)?.dsUserId ?: "" } ?: ""
        UsernameEditDialog(
            currentUsername = currentUsername,
            onConfirm = { newName ->
                authViewModel.overrideSessionUsername(newName)
                showEditUsernameDialog = false
            },
            onDismiss = { showEditUsernameDialog = false }
        )
    }

    // Raw-ID fallback dialog - shown once when Instagram returns only a numeric user ID
    if (showRawIdDialog) {
        val rawId = (authState as? InstagramAuthState.LoggedIn)?.username ?: ""
        UsernameRawIdDialog(
            rawId = rawId,
            onSet = { chosenName -> authViewModel.overrideSessionUsername(chosenName) },
            onCancel = { authViewModel.dismissRawIdDialog() }
        )
    }
}


@Composable
fun InfoSectionCard(
    icon: ImageVector,
    title: String,
    introText: String,
    items: List<InfoItem>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = introText,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 5.dp)
                                .size(5.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = item.boldText,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.descText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

data class InfoItem(
    val boldText: String,
    val descText: String
)

// Username Edit Dialog - shown when user taps the Edit icon on the session card
@Composable
fun UsernameEditDialog(
    currentUsername: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember(currentUsername) { mutableStateOf(currentUsername) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Display Name",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Set a recognizable display name for this session. This is stored locally and does not affect your Instagram account.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(inputText) },
                enabled = inputText.isNotBlank()
            ) {
                Text(
                    text = "Set",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

// Raw-ID Fallback Dialog - shown once when Instagram returns a numeric user ID
@Composable
fun UsernameRawIdDialog(
    rawId: String,
    onSet: (String) -> Unit,
    onCancel: () -> Unit
) {
    var inputText by remember(rawId) { mutableStateOf(rawId) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Username Not Fetched",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column {
                Text(
                    text = "Instagram returned a raw ID instead of your username. Please set a recognizable display name for this session.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Display name") },
                    placeholder = { Text("e.g. myinstagram") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(inputText) },
                enabled = inputText.isNotBlank()
            ) {
                Text(
                    text = "Set",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = "Cancel")
            }
        }
    )
}
