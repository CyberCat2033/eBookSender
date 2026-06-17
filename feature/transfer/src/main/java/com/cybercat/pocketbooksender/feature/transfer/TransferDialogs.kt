package com.cybercat.pocketbooksender.feature.transfer

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.AppOutlinedTextField
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.util.performHapticIfAllowed

@Composable
fun MangaBatchEditorDialog(
    activeMangaQueue: List<UploadItem>,
    suggestions: List<String>,
    enableHaptics: Boolean,
    onDismiss: () -> Unit,
    onApply: (String?, String) -> Unit
) {
    val uniqueSeries = remember(activeMangaQueue) {
        activeMangaQueue.mapNotNull { item -> item.mangaSeries?.takeIf(String::isNotBlank) }
            .distinctBy { it.lowercase() }
    }

    var targetSeries by remember { mutableStateOf<String?>(uniqueSeries.firstOrNull()) }
    var series by remember(targetSeries) { mutableStateOf(targetSeries ?: "") }

    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.sendRenameMangaTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (uniqueSeries.size > 1) {
                    Text(
                        text = strings.mangaUpdatesSelectSeriesToRename,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = targetSeries == null,
                            onClick = {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                targetSeries = null
                            },
                            label = { Text(strings.mangaUpdatesAllSeries) }
                        )
                        uniqueSeries.forEach { s ->
                            FilterChip(
                                selected = targetSeries == s,
                                onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        enableHaptics,
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    )
                                    targetSeries = s
                                },
                                label = { Text(s) }
                            )
                        }
                    }
                }

                Text(
                    text = if (targetSeries == null) {
                        strings.get("send_batch_rename_desc", activeMangaQueue.size)
                    } else {
                        val countForTarget = activeMangaQueue.count {
                            it.mangaSeries?.equals(targetSeries, ignoreCase = true) ==
                                true
                        }
                        strings.get("manga_updates_rename_desc", countForTarget, targetSeries ?: "")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MangaSeriesRenamePanel(
                    selectedSeries = series,
                    suggestions = suggestions,
                    onSeriesChanged = { series = it },
                    onSuggestionSelected = { suggestion ->
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            HapticFeedbackConstants.VIRTUAL_KEY
                        )
                        series = suggestion
                    }
                )
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        HapticFeedbackConstants.CONFIRM
                    )
                    onApply(targetSeries, series.trim())
                    dismiss()
                },
                enabled = series.isNotBlank()
            ) {
                Text(strings.sendRenameMangaApply)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(onClick = {
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    HapticFeedbackConstants.VIRTUAL_KEY
                )
                dismiss()
            }) {
                Text(strings.sendRenameMangaCancel)
            }
        }
    )
}

@Composable
internal fun MangaSeriesRenamePanel(
    selectedSeries: String,
    suggestions: List<String>,
    onSeriesChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSuggestionSelected: (String) -> Unit = onSeriesChanged
) {
    val strings = LocalStrings.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppOutlinedTextField(
            value = selectedSeries,
            onValueChange = onSeriesChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.sendRenameMangaSeries) }
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestions
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .forEach { suggestion ->
                    FilterChip(
                        selected = selectedSeries.equals(suggestion, ignoreCase = true),
                        onClick = { onSuggestionSelected(suggestion) },
                        label = { Text(suggestion) }
                    )
                }
        }
    }
}
