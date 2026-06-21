package com.devson.vedinsta.ui.screen.setting

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.vedinsta.ui.VedInstaTopAppBar
import com.devson.vedinsta.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var userAgent by remember { mutableStateOf(settingsViewModel.customUserAgent) }
    var igAppId by remember { mutableStateOf(settingsViewModel.customIgAppId) }
    var timeoutSeconds by remember { mutableFloatStateOf(settingsViewModel.networkTimeoutSeconds.toFloat()) }
    var maxRetries by remember { mutableFloatStateOf(settingsViewModel.maxRetries.toFloat()) }

    var showHelpSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            VedInstaTopAppBar(
                title = "Advanced Network",
                showBackButton = true,
                onBackClick = onNavigateBack,
                actions = {
                    TextButton(onClick = {
                        userAgent = ""
                        igAppId = ""
                        timeoutSeconds = 15f
                        maxRetries = 3f

                        settingsViewModel.customUserAgent = ""
                        settingsViewModel.customIgAppId = ""
                        settingsViewModel.networkTimeoutSeconds = 15
                        settingsViewModel.maxRetries = 3

                        Toast.makeText(
                            context,
                            "Network settings reset to default",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text("Reset Defaults", fontWeight = FontWeight.Bold)
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Category: Header Fields
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HTTP Headers Override",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(onClick = { showHelpSheet = true }) {
                    Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "How to get headers",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Customize client headers to bypass Instagram connection blocks. Leave blank to use default values.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = userAgent,
                        onValueChange = {
                            userAgent = it
                            settingsViewModel.customUserAgent = it
                        },
                        label = { Text("Custom User-Agent") },
                        placeholder = { Text("Instagram 319.0.0.28.119 Android") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = igAppId,
                        onValueChange = {
                            igAppId = it
                            settingsViewModel.customIgAppId = it
                        },
                        label = { Text("Custom X-IG-App-ID") },
                        placeholder = { Text("567067343352427") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Category: Network Timeouts
            Text(
                text = "Network Tolerances",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Network Timeout: ${timeoutSeconds.toInt()}s",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = timeoutSeconds,
                        onValueChange = {
                            timeoutSeconds = it
                            settingsViewModel.networkTimeoutSeconds = it.toInt()
                        },
                        valueRange = 10f..60f,
                        steps = 50
                    )
                    Text(
                        text = "Determines how long connection attempts will wait before timing out.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Maximum Retry Count: ${maxRetries.toInt()}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = maxRetries,
                        onValueChange = {
                            maxRetries = it
                            settingsViewModel.maxRetries = it.toInt()
                        },
                        valueRange = 1f..10f,
                        steps = 9
                    )
                    Text(
                        text = "Number of times a request will re-attempt automatically on timeout or rate-limiting.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    if (showHelpSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHelpSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "How to Extract Custom Headers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Text(
                    text = "If Instagram limits your downloads, you can bypass constraints by copying header identities directly from your logged-in browser session:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val steps = listOf(
                    "Open Instagram in a desktop web browser (Chrome, Edge, or Firefox) and log in.",
                    "Press F12 (or right-click -> inspect) to open DevTools, then navigate to the Network tab.",
                    "Refresh the page, then filter request entries by typing 'graphql' in the filter input.",
                    "Select any matching request and navigate to its Headers sub-tab.",
                    "Scroll down to 'Request Headers' and locate the User-Agent and X-IG-App-ID header values.",
                    "Copy those values and paste them into the input fields on this screen."
                )

                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${index + 1}. ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = step,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showHelpSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Got it")
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
