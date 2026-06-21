package com.devson.vedinsta.ui.screen.setting

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.devson.vedinsta.ui.theme.AppThemePalette
import androidx.compose.foundation.isSystemInDarkTheme
import com.devson.vedinsta.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsScreen(
    onNavigateBack: () -> Unit,
    settingsViewModel: SettingsViewModel
) {
    val isDark        by settingsViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val dynamicColor  by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()
    val selectedPalette by settingsViewModel.selectedPalette.collectAsStateWithLifecycle()
    val navBarTransparent by settingsViewModel.isNavBarTransparent.collectAsStateWithLifecycle()
    val isAmoledTheme by settingsViewModel.isAmoledTheme.collectAsStateWithLifecycle()
    val isBlurEnabled by settingsViewModel.isBlurEnabled.collectAsStateWithLifecycle()
    val blurOpacity   by settingsViewModel.blurOpacity.collectAsStateWithLifecycle()
    val blurRadius    by settingsViewModel.blurRadius.collectAsStateWithLifecycle()
    val isEffectivelyDark = isDark ?: isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Display Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + 16.dp
                )
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            //  Colour preview strip 
            ColorPreviewStrip()

            Spacer(Modifier.height(20.dp))

            //  COLOUR PALETTE section
            AppearanceSectionLabel("Colour Palette")
            PalettePickerGrid(
                selected    = selectedPalette,
                isDark      = isEffectivelyDark,
                onSelect    = { settingsViewModel.setSelectedPalette(it) }
            )

            Spacer(Modifier.height(16.dp))

            //  THEME section 
            AppearanceSectionLabel("Appearance Theme")
            AppearanceCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Dark Theme Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Material 3 Styled Custom Toggle Segment Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val options = listOf("Light", "Dark", "System")
                        options.forEachIndexed { index, label ->
                            val isSelected = when (index) {
                                0 -> isDark == false
                                1 -> isDark == true
                                else -> isDark == null
                            }
                            Button(
                                onClick = {
                                    when (index) {
                                        0 -> settingsViewModel.setDarkTheme(false)
                                        1 -> settingsViewModel.setDarkTheme(true)
                                        2 -> settingsViewModel.resetDarkTheme()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                AnimatedVisibility(visible = isSelected) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                }
                                Text(label, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AppearanceDivider()
                    AppearanceToggleRow(
                        icon      = Icons.Default.Palette,
                        title     = "Dynamic Colour",
                        subtitle  = "Use wallpaper colour",
                        checked   = dynamicColor,
                        onCheckedChange = { settingsViewModel.setDynamicColor(it) }
                    )
                }

                AppearanceDivider()
                if (isEffectivelyDark) {
                    AppearanceToggleRow(
                        icon      = Icons.Default.Brightness1,
                        title     = "AMOLED Theme",
                        subtitle  = "Pure black background for dark mode",
                        checked   = isAmoledTheme,
                        onCheckedChange = { settingsViewModel.setAmoledTheme(it) }
                    )
                    AppearanceDivider()
                }

                AppearanceToggleRow(
                    icon      = Icons.Default.WebAsset,
                    title     = "Transparent Navigation Buttons",
                    subtitle  = "Content scrolls behind the system navigation buttons",
                    checked   = navBarTransparent,
                    onCheckedChange = { settingsViewModel.setNavBarTransparent(it) }
                )

                AppearanceDivider()
                AppearanceToggleRow(
                    icon      = Icons.Default.Opacity,
                    title     = "Blur Effect",
                    subtitle  = "Blurred Top & Bottom Navbars (glassmorphism)",
                    checked   = isBlurEnabled,
                    onCheckedChange = { settingsViewModel.setBlurEnabled(it) }
                )

                if (isBlurEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 72.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blur Tint Opacity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(blurOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = blurOpacity,
                            onValueChange = { settingsViewModel.setBlurOpacity(it) },
                            valueRange = 0.1f..0.9f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Blur Intensity (Radius)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${blurRadius.toInt()} dp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = blurRadius,
                            onValueChange = { settingsViewModel.setBlurRadius(it) },
                            valueRange = 5f..50f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                thumbColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            //  INFO chip
            Surface(
                modifier       = Modifier.fillMaxWidth(),
                shape          = RoundedCornerShape(12.dp),
                color          = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint     = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text  = "Select your color theme to change the feel of VedInsta.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

//  Palette Picker Grid
@Composable
private fun PalettePickerGrid(
    selected: AppThemePalette,
    isDark: Boolean,
    onSelect: (AppThemePalette) -> Unit
) {
    val palettes = AppThemePalette.entries
    val rows = palettes.chunked(2)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { palette ->
                    PaletteCard(
                        palette  = palette,
                        isDark   = isDark,
                        isSelected = palette == selected,
                        modifier   = Modifier.weight(1f),
                        onClick    = { onSelect(palette) }
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PaletteCard(
    palette: AppThemePalette,
    isDark: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val primary   = if (isDark) palette.darkPrimary   else palette.lightPrimary
    val secondary = if (isDark) palette.darkSecondary else palette.lightSecondary

    val borderWidth by animateDpAsState(
        targetValue   = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label         = "paletteBorder"
    )
    val borderColor by animateColorAsState(
        targetValue   = if (isSelected) primary else Color.Transparent,
        animationSpec = tween(250),
        label         = "paletteBorderColor"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape          = RoundedCornerShape(14.dp),
        color          = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (isSelected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        Brush.horizontalGradient(listOf(primary, secondary))
                    )
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint     = Color.White,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 8.dp)
                            .size(18.dp)
                    )
                }
            }

            Row(
                verticalAlignment      = Alignment.CenterVertically,
                horizontalArrangement  = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Text(
                    text  = palette.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) primary
                            else MaterialTheme.colorScheme.onSurface
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(secondary)
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun ColorPreviewStrip() {
    val swatches = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.outline,
    )

    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text  = "Current Color Palette Preview",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                swatches.forEach { raw ->
                    val animColor by animateColorAsState(
                        targetValue   = raw,
                        animationSpec = tween(400),
                        label         = "swatchAnim"
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(animColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceSectionLabel(label: String) {
    Text(
        text       = label,
        style      = MaterialTheme.typography.labelLarge,
        color      = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun AppearanceCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier       = Modifier.fillMaxWidth(),
        shape          = RoundedCornerShape(16.dp),
        color          = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
private fun AppearanceDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color    = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
private fun AppearanceToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
