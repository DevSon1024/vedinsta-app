package com.devson.vedinsta.ui.screen.setting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.vedinsta.repository.DownloadQuotaManager
import com.devson.vedinsta.ui.VedInstaTopAppBar
import com.devson.vedinsta.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityLimitsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var overshadowQuota by remember { mutableStateOf(settingsViewModel.overshadowQuota) }
    var overshadowRateLimit by remember { mutableStateOf(settingsViewModel.overshadowRateLimit) }

    val quotaManager = remember { DownloadQuotaManager(context) }
    var quotaStats by remember { mutableStateOf(quotaManager.getQuotaStats()) }

    LaunchedEffect(overshadowQuota) {
        quotaStats = quotaManager.getQuotaStats()
        while (true) {
            delay(10000L)
            quotaStats = quotaManager.getQuotaStats()
        }
    }

    Scaffold(
        topBar = {
            VedInstaTopAppBar(
                title = "Security & Limits",
                showBackButton = true,
                onBackClick = onNavigateBack
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
            SettingsSwitchItem(
                title = "Bypass Quota Limitation",
                subtitle = "Bypass download limits (Warning: increases risk of Instagram account flags)",
                icon = Icons.Default.Warning,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.error,
                checked = overshadowQuota,
                onCheckedChange = {
                    settingsViewModel.overshadowQuota = it
                    overshadowQuota = it
                },
                subtitleColor = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsSwitchItem(
                title = "Bypass Rate Limit Pauses",
                subtitle = "Bypass Instagram slowdown pauses (Warning: high risk of account bans/blocks)",
                icon = Icons.Default.Warning,
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconColor = MaterialTheme.colorScheme.error,
                checked = overshadowRateLimit,
                onCheckedChange = {
                    settingsViewModel.overshadowRateLimit = it
                    overshadowRateLimit = it
                },
                subtitleColor = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Download Quota Usage",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    QuotaProgressRow(
                        label = "Hourly Quota",
                        count = quotaStats.hourlyCount,
                        limit = quotaStats.hourlyLimit,
                        resetMs = quotaStats.hourlyResetMs
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    QuotaProgressRow(
                        label = "Daily Quota",
                        count = quotaStats.dailyCount,
                        limit = quotaStats.dailyLimit,
                        resetMs = quotaStats.dailyResetMs
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    QuotaProgressRow(
                        label = "Weekly Quota",
                        count = quotaStats.weeklyCount,
                        limit = quotaStats.weeklyLimit,
                        resetMs = quotaStats.weeklyResetMs
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Adjust Quota Limits",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Hourly Limit Slider
                    var hourlyLimitVal by remember { mutableStateOf(quotaManager.customLimitHourly.toFloat()) }
                    val hourlyRisk = when {
                        hourlyLimitVal <= 40f -> Pair("Safe", MaterialTheme.colorScheme.primary)
                        hourlyLimitVal <= 80f -> Pair("Medium Risk", Color(0xFFFFA726))
                        else -> Pair("High Risk", MaterialTheme.colorScheme.error)
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hourly Limit: ${hourlyLimitVal.toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = hourlyRisk.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = hourlyRisk.second
                            )
                        }
                        Slider(
                            value = hourlyLimitVal,
                            onValueChange = {
                                hourlyLimitVal = it
                                quotaManager.customLimitHourly = it.toInt()
                                quotaStats = quotaManager.getQuotaStats()
                            },
                            valueRange = 10f..200f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = hourlyRisk.second,
                                activeTrackColor = hourlyRisk.second
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Daily Limit Slider
                    var dailyLimitVal by remember { mutableStateOf(quotaManager.customLimitDaily.toFloat()) }
                    val dailyRisk = when {
                        dailyLimitVal <= 150f -> Pair("Safe", MaterialTheme.colorScheme.primary)
                        dailyLimitVal <= 300f -> Pair("Medium Risk", Color(0xFFFFA726))
                        else -> Pair("High Risk", MaterialTheme.colorScheme.error)
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Daily Limit: ${dailyLimitVal.toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dailyRisk.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = dailyRisk.second
                            )
                        }
                        Slider(
                            value = dailyLimitVal,
                            onValueChange = {
                                dailyLimitVal = it
                                quotaManager.customLimitDaily = it.toInt()
                                quotaStats = quotaManager.getQuotaStats()
                            },
                            valueRange = 50f..1000f,
                            steps = 18,
                            colors = SliderDefaults.colors(
                                thumbColor = dailyRisk.second,
                                activeTrackColor = dailyRisk.second
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Weekly Limit Slider
                    var weeklyLimitVal by remember { mutableStateOf(quotaManager.customLimitWeekly.toFloat()) }
                    val weeklyRisk = when {
                        weeklyLimitVal <= 500f -> Pair("Safe", MaterialTheme.colorScheme.primary)
                        weeklyLimitVal <= 1000f -> Pair("Medium Risk", Color(0xFFFFA726))
                        else -> Pair("High Risk", MaterialTheme.colorScheme.error)
                    }
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Weekly Limit: ${weeklyLimitVal.toInt()}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = weeklyRisk.first,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = weeklyRisk.second
                            )
                        }
                        Slider(
                            value = weeklyLimitVal,
                            onValueChange = {
                                weeklyLimitVal = it
                                quotaManager.customLimitWeekly = it.toInt()
                                quotaStats = quotaManager.getQuotaStats()
                            },
                            valueRange = 100f..3000f,
                            steps = 28,
                            colors = SliderDefaults.colors(
                                thumbColor = weeklyRisk.second,
                                activeTrackColor = weeklyRisk.second
                            )
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun QuotaProgressRow(
    label: String,
    count: Int,
    limit: Int,
    resetMs: Long
) {
    val progress = (count.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
    val progressColor = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.7f -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.primary
    }

    val resetText = if (resetMs <= 0L) {
        "No active limit"
    } else {
        val remainingMs = resetMs - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            "Resetting..."
        } else {
            val totalMinutes = remainingMs / (60 * 1000L)
            if (totalMinutes >= 24 * 60) {
                val days = totalMinutes / (24 * 60)
                "Reset in ${days}d"
            } else if (totalMinutes >= 60) {
                val hours = totalMinutes / 60
                "Reset in ${hours}h"
            } else {
                val mins = totalMinutes.coerceAtLeast(1)
                "Reset in ${mins}m"
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$count / $limit ($resetText)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
