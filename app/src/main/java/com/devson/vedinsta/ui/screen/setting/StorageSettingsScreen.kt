package com.devson.vedinsta.ui.screen.setting

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.vedinsta.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

// Predefined filename templates exposed to the user
private data class FilenameTemplate(
    val label: String,
    val template: String
)

private val PREDEFINED_TEMPLATES = listOf(
    FilenameTemplate("Username + Timestamp (Default)", "{username}_{milliseconds}"),
    FilenameTemplate("Username + Date", "{username}_{date}"),
    FilenameTemplate("Date + Short ID", "{date}_{short_id}"),
    FilenameTemplate("Short ID + Timestamp", "{short_id}_{milliseconds}"),
    FilenameTemplate("Custom", "" /* sentinel - handled separately */)
)

private const val CUSTOM_SENTINEL = ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // - Directory labels loaded from DocumentFile on IO thread
    var imagePath by remember { mutableStateOf("Loading...") }
    var videoPath by remember { mutableStateOf("Loading...") }

    LaunchedEffect(Unit) {
        imagePath = settingsViewModel.getImagePathLabel()
        videoPath = settingsViewModel.getVideoPathLabel()
    }

    // - Filename template state hoisted here
    val savedTemplate by settingsViewModel.filenameTemplate.collectAsStateWithLifecycle()

    // Determine which radio item is selected
    val selectedIndex by remember(savedTemplate) {
        derivedStateOf {
            val idx = PREDEFINED_TEMPLATES.indexOfFirst {
                it.template == savedTemplate && it.template != CUSTOM_SENTINEL
            }
            if (idx == -1) PREDEFINED_TEMPLATES.lastIndex else idx // last = Custom
        }
    }

    // Custom template text field value - pre-fill with saved value when "Custom" is active
    var customTemplateText by remember(selectedIndex, savedTemplate) {
        mutableStateOf(if (selectedIndex == PREDEFINED_TEMPLATES.lastIndex) savedTemplate else "")
    }

    // Live preview derived from current effective template
    val effectiveTemplate by remember(selectedIndex, customTemplateText, savedTemplate) {
        derivedStateOf {
            when {
                selectedIndex == PREDEFINED_TEMPLATES.lastIndex -> customTemplateText
                else -> PREDEFINED_TEMPLATES[selectedIndex].template
            }
        }
    }
    val previewText by remember(effectiveTemplate) {
        derivedStateOf { settingsViewModel.buildFilenamePreview(effectiveTemplate) }
    }

    // - Directory pickers
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                takePersistableUriPermission(context, uri)
                settingsViewModel.imageDirectoryUri = uri.toString()
                coroutineScope.launch {
                    imagePath = settingsViewModel.getImagePathLabel()
                }
                Toast.makeText(context, "Image save location updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.also { uri ->
                takePersistableUriPermission(context, uri)
                settingsViewModel.videoDirectoryUri = uri.toString()
                coroutineScope.launch {
                    videoPath = settingsViewModel.getVideoPathLabel()
                }
                Toast.makeText(context, "Video save location updated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Storage & Filenames",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Section 1 - Save Locations
            SettingsCategoryHeader("Save Locations")

            SettingsClickableItem(
                title = "Images Save Location",
                subtitle = imagePath,
                icon = Icons.Default.Image,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.tertiary,
                onClick = {
                    imagePicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                }
            )

            SettingsClickableItem(
                title = "Videos Save Location",
                subtitle = videoPath,
                icon = Icons.Default.Movie,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.tertiary,
                onClick = {
                    videoPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Section 2 - Filename Template
            SettingsCategoryHeader("Filename Template")

            // Info chip about available tags
            TagHintRow()

            Spacer(modifier = Modifier.height(8.dp))

            // Radio card list
            PREDEFINED_TEMPLATES.forEachIndexed { index, template ->
                val isCustom = index == PREDEFINED_TEMPLATES.lastIndex
                val isSelected = index == selectedIndex

                TemplateRadioCard(
                    label = template.label,
                    templatePreview = if (isCustom) "Enter your own pattern below" else template.template,
                    isSelected = isSelected,
                    onClick = {
                        if (isCustom) {
                            // Switching to custom - keep whatever custom text the user typed,
                            // but don't persist until they type/confirm
                            if (customTemplateText.isEmpty()) customTemplateText = savedTemplate
                        } else {
                            settingsViewModel.setFilenameTemplate(template.template)
                        }
                    }
                )
            }

            // Custom text field - only visible when Custom option is selected
            AnimatedVisibility(
                visible = selectedIndex == PREDEFINED_TEMPLATES.lastIndex,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = customTemplateText,
                        onValueChange = { customTemplateText = it },
                        label = { Text("Custom template") },
                        placeholder = { Text("{username}_{milliseconds}") },
                        supportingText = {
                            Text(
                                "Tags: {username}  {milliseconds}  {date}  {short_id}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                keyboardController?.hide()
                                val finalTemplate = customTemplateText.ifBlank {
                                    SettingsViewModel.DEFAULT_FILENAME_TEMPLATE
                                }
                                settingsViewModel.setFilenameTemplate(finalTemplate)
                                Toast.makeText(context, "Custom template saved", Toast.LENGTH_SHORT).show()
                            }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            val finalTemplate = customTemplateText.ifBlank {
                                SettingsViewModel.DEFAULT_FILENAME_TEMPLATE
                            }
                            settingsViewModel.setFilenameTemplate(finalTemplate)
                            Toast.makeText(context, "Custom template saved", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save Custom Template", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Preview Card
            LivePreviewCard(previewText = previewText)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Sub-composables

@Composable
private fun TemplateRadioCard(
    label: String,
    templatePreview: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null, // click handled by the Row
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = templatePreview,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
private fun LivePreviewCard(previewText: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DriveFileRenameOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Live Preview",
                    color = MaterialTheme.colorScheme.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = previewText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TagHintRow() {
    val tags = listOf("{username}", "{milliseconds}", "{date}", "{short_id}")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Tags:",
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            fontSize = 11.5.sp
        )
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tag,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 10.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Private helpers

private fun takePersistableUriPermission(context: Context, uri: Uri) {
    try {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(uri, flags)
    } catch (_: SecurityException) {
        // Ignored - permission may already be held or device may not support it
    }
}
