package com.devson.vedinsta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.vedinsta.viewmodel.InstagramAuthState
import com.devson.vedinsta.viewmodel.InstagramAuthViewModel

@Composable
fun SessionsScreen(
    authViewModel: InstagramAuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    val authState by authViewModel.authState.collectAsState()

    val isDark = MaterialTheme.colorScheme.background.let { it.red + it.green + it.blue } < 1.5f

    val cardColor = when (authState) {
        is InstagramAuthState.LoggedIn -> if (isDark) Color(0xFF1E3A1E) else Color(0xFFE8F5E9) // Sleek Dark Green vs Light Green
        is InstagramAuthState.SessionExpired -> if (isDark) Color(0xFF3D1D1D) else Color(0xFFFFEBEE) // Sleek Dark Red vs Light Red
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Session Manager",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "ACTIVE ACCOUNT SESSION",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    when (val state = authState) {
                        is InstagramAuthState.LoggedIn -> {
                            Text(
                                text = "@${state.username.ifEmpty { state.dsUserId }}",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Session is active and ready.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                        is InstagramAuthState.SessionExpired -> {
                            Text(
                                text = "Session Expired",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.reason,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        is InstagramAuthState.Checking -> {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        else -> {
                            Text(
                                text = "Not Signed In",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sign in to Instagram via secure WebView to download high-resolution posts and reels.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when (authState) {
                        is InstagramAuthState.LoggedIn -> {
                            Button(
                                onClick = { authViewModel.logout() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ),
                                shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(8.dp),
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
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Sign In with Instagram", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- Session Storage Info Card ---
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Security Info",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Session Storage & Privacy",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Where are cookies stored?
                    Text(
                        text = "🔒  Where are session files stored?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        // instagram_cookies.txt is written via context.filesDir - the app's
                        // private internal storage at /data/data/com.devson.vedinsta/files/.
                        // This directory is completely sandboxed by Android's Linux kernel
                        // permission model: no other app, file manager, or even ADB (on
                        // non-rooted devices) can read it. It is NOT inside the user-visible
                        // "Android/data/" folder on external storage.
                        text = "Your Instagram session cookies are stored inside this app's private internal storage sandbox - inaccessible to any other app, file manager, or ADB on non-rooted devices.\nit will be not shared on servers or any other platforms \n\nSafe and Secure\n\nPhysical path (internal, not visible to user):\n/data/data/com.devson.vedinsta/files/",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Uninstall behavior
                    Text(
                        text = "🗑️  What happens on uninstall?",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        // Android automatically wipes the entire /data/data/<package>/ directory
                        // when the app is uninstalled. The cookie file, encrypted preferences,
                        // and the Room database are all destroyed at that point.
                        text = "When VedInsta is uninstalled, Android automatically deletes the entire app sandbox - including session cookies and encrypted preferences. No data is left behind.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Logout clarification
                    Text(
                        text = "⚠️  \"Log Out Session\" vs. Instagram logout",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        // Pressing "Log Out Session" only deletes the local cookie file and
                        // clears EncryptedSharedPreferences inside VedInsta. It does NOT
                        // revoke the session on Instagram's servers. Your account stays
                        // logged in on the Instagram app, browser, and other devices.
                        // To truly end the session globally, log out from Instagram directly.
                        text = "Tapping \"Log Out Session\" only removes the saved cookies from this app - it does NOT log you out of Instagram globally. Your Instagram account remains active on the Instagram app, browsers, and all other devices. To invalidate the session everywhere, log out from Instagram itself.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
