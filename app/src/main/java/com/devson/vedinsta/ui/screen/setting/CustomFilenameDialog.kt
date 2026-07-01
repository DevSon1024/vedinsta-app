package com.devson.vedinsta.ui.screen.setting

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// All available insertable tags (in display order)
private data class TagChip(val label: String, val tag: String)

private val TAG_CHIPS = listOf(
    TagChip("{username}", "{username}"),
    TagChip("{date}", "{date}"),
    TagChip("{milliseconds}", "{milliseconds}"),
    TagChip("{short_id}", "{short_id}")
)

// Custom Filename Builder Dialog

/**
 * Fully stateless AlertDialog for building a custom filename template.
 *
 * @param inputValue         Current [TextFieldValue] - drives the OutlinedTextField.
 * @param isError            When true, the field shows the error border + supporting text.
 * @param isValid            Gates the Save button: must be true to enable it.
 * @param previewText        Live preview string already computed by the caller.
 * @param onValueChange      Emitted on every keystroke - caller validates and updates [inputValue].
 * @param onInsertTag        Emitted when a tag chip is tapped - caller injects at cursor position.
 * @param onSave             Emitted when Save is confirmed; carries the final template string.
 * @param onDismiss          Emitted on Cancel or outside-touch dismissal.
 */
@Composable
fun CustomFilenameDialog(
    inputValue: TextFieldValue,
    isError: Boolean,
    isValid: Boolean,
    previewText: String,
    onValueChange: (TextFieldValue) -> Unit,
    onInsertTag: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = FocusRequester()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Custom Filename",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Description
                Text(
                    text = "Build your own template by typing text and tapping tags to insert them.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Input field
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = onValueChange,
                    label = { Text("Filename template") },
                    placeholder = { Text("{username}_{milliseconds}") },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = "Filename can only include tags, letters, numbers, spaces, underscores (_), and hyphens (-).",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        } else {
                            // Non-error supporting text keeps the layout height stable
                            Text(
                                text = "Tap a chip below to insert a tag at the cursor",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (isError) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                )

                // Tag chip row - horizontally scrollable so all chips stay on one line
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "INSERT TAG",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TAG_CHIPS.forEach { chip ->
                            SuggestionChip(
                                onClick = { onInsertTag(chip.tag) },
                                label = {
                                    Text(
                                        text = chip.label,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = SuggestionChipDefaults.suggestionChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                // Live preview
                if (inputValue.text.isNotBlank() && !isError) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Preview",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = previewText,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onSave(inputValue.text)
                },
                enabled = isValid
            ) {
                Text(
                    text = "Save",
                    color = if (isValid) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
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
