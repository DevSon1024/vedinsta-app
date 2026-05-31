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
                                text = "@${state.dsUserId}",
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

            // Safety Warning Notice
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Security & Privacy",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your login cookies are stored safely on-device in secure preference files and used strictly to execute python scripts in a sandbox.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
