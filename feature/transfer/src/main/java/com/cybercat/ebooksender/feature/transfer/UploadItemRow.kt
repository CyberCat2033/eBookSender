package com.cybercat.ebooksender.feature.transfer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.model.AppSettings
import com.cybercat.ebooksender.model.BookCategory
import com.cybercat.ebooksender.model.UploadItem
import com.cybercat.ebooksender.model.UploadStatus
import com.cybercat.ebooksender.ui.AnimatedLinearProgressIndicator
import com.cybercat.ebooksender.ui.AppOutlinedTextField
import com.cybercat.ebooksender.ui.SingleLineMarqueeText
import com.cybercat.ebooksender.ui.UploadPreviewCover
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
fun UploadItemRow(
    item: UploadItem,
    progress: Float,
    modifier: Modifier = Modifier,
    documentsTags: List<String>,
    mangaSeriesSuggestions: List<String>,
    enableHaptics: Boolean,
    canRemove: Boolean,
    settings: AppSettings,
    onRemove: () -> Unit,
    onCategoryChanged: (BookCategory) -> Unit,
    onDocumentsTagChanged: (String) -> Unit,
    onMangaSeriesChanged: (String) -> Unit
) {
    var detailsExpanded by remember(item.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(item)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        SingleLineMarqueeText(
                            text = item.originalName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                AppHapticFeedback.Reject
                            )
                            onRemove()
                        },
                        enabled =
                            canRemove &&
                                item.status != UploadStatus.Uploading &&
                                item.status != UploadStatus.Uploaded
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = strings.sendBtnRemove)
                    }
                }

                Spacer(Modifier.height(12.dp))

                ItemTypeSummary(
                    item = item,
                    expanded = detailsExpanded,
                    onToggle = {
                        detailsExpanded = !detailsExpanded
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            AppHapticFeedback.Press
                        )
                    },
                    settings = settings
                )

                AnimatedVisibility(
                    visible = detailsExpanded,
                    enter = expandVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        expandFrom = Alignment.Top
                    ) + fadeIn(spring(stiffness = Spring.StiffnessMediumLow)),
                    exit = shrinkVertically(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        shrinkTowards = Alignment.Top
                    ) + fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CategorySelector(
                            selected = item.category,
                            lockedToManga = item.extension == "cbr" || item.extension == "cbz",
                            onCategoryChanged = { cat ->
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    AppHapticFeedback.Press
                                )
                                onCategoryChanged(cat)
                            },
                            settings = settings
                        )

                        if (item.category == BookCategory.Documents) {
                            DocumentsTagEditor(
                                selectedTag = item.documentsTag.orEmpty(),
                                suggestions = documentsTags,
                                onTagChanged = onDocumentsTagChanged,
                                categoryName = settings.documentsFolderName
                            )
                        }

                        if (item.category == BookCategory.Manga) {
                            MangaSeriesRenamePanel(
                                selectedSeries = item.mangaSeries.orEmpty(),
                                suggestions = mangaSeriesSuggestions,
                                onSeriesChanged = onMangaSeriesChanged
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.plannedPath,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))

                    val statusColor = when (item.status) {
                        UploadStatus.Uploaded -> MaterialTheme.colorScheme.primary
                        UploadStatus.Failed -> MaterialTheme.colorScheme.error
                        UploadStatus.Uploading -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val statusText = when (item.status) {
                        UploadStatus.Uploading -> strings.get(
                            "send_status_uploading",
                            (
                                progress *
                                    100
                                ).toInt()
                        )

                        UploadStatus.Pending -> strings.sendStatusPending

                        UploadStatus.Preparing -> strings.sendStatusPreparing

                        UploadStatus.Uploaded -> strings.sendStatusUploaded

                        UploadStatus.Failed -> strings.sendStatusFailed

                        UploadStatus.Skipped -> strings.sendStatusSkipped
                    }

                    SingleLineMarqueeText(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }

            val isUploading = item.status == UploadStatus.Uploading
            val progressHeight by animateDpAsState(
                targetValue = if (isUploading) 4.dp else 0.dp,
                label = "ProgressHeight"
            )

            if (progressHeight > 0.dp) {
                AnimatedLinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(progressHeight),
                    label = "SmoothProgress"
                )
            }
        }
    }
}

@Composable
private fun ItemTypeSummary(
    item: UploadItem,
    expanded: Boolean,
    onToggle: () -> Unit,
    settings: AppSettings
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.category.iconFor(item.extension),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.category.label(settings),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                item.typeDetail()?.let { detail ->
                    SingleLineMarqueeText(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val rotationState by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "ChevronRotation"
            )
            val strings = LocalStrings.current
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) {
                        strings.sendActionHideDetails
                    } else {
                        strings.sendActionEditDetails
                    },
                    modifier = Modifier.rotate(rotationState)
                )
            }
        }
    }
}

@Composable
private fun DocumentsTagEditor(
    selectedTag: String,
    suggestions: List<String>,
    onTagChanged: (String) -> Unit,
    categoryName: String
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppOutlinedTextField(
            value = selectedTag,
            onValueChange = onTagChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(strings.get("send_tag_field", categoryName)) }
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
                        selected = selectedTag.equals(suggestion, ignoreCase = true),
                        onClick = { onTagChanged(suggestion) },
                        label = { Text(suggestion) }
                    )
                }
        }
    }
}

@Composable
private fun BookCover(item: UploadItem) {
    UploadPreviewCover(
        itemId = item.id,
        title = item.title,
        reloadKey = item.status,
        placeholderIcon = item.category.iconFor(item.extension)
    )
}

@Composable
private fun CategorySelector(
    selected: BookCategory,
    lockedToManga: Boolean,
    onCategoryChanged: (BookCategory) -> Unit,
    settings: AppSettings
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BookCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                enabled = !lockedToManga || category == BookCategory.Manga,
                onClick = { onCategoryChanged(category) },
                label = { Text(category.label(settings)) },
                leadingIcon = {
                    Icon(
                        imageVector = category.iconFor(""),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

private fun BookCategory.label(settings: AppSettings): String = when (this) {
    BookCategory.Books -> settings.booksFolderName
    BookCategory.Documents -> settings.documentsFolderName
    BookCategory.Manga -> settings.mangaFolderName
}

private fun UploadItem.typeDetail(): String? = when (category) {
    BookCategory.Books -> author?.takeIf { it.isNotBlank() }
    BookCategory.Documents -> documentsTag?.takeIf { it.isNotBlank() }
    BookCategory.Manga -> mangaSeries?.takeIf { it.isNotBlank() }
}

private fun BookCategory.iconFor(extension: String): ImageVector = when {
    this == BookCategory.Manga -> Icons.Outlined.Image
    extension == "pdf" -> Icons.Outlined.PictureAsPdf
    this == BookCategory.Documents -> Icons.Outlined.Description
    else -> Icons.AutoMirrored.Outlined.MenuBook
}

fun List<UploadItem>.commonMangaSeries(): String {
    val series = mapNotNull { item -> item.mangaSeries?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
    return if (series.size == 1) series.first() else ""
}
