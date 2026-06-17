package com.cybercat.pocketbooksender.feature.opds

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsSource
import com.cybercat.pocketbooksender.localization.AppStrings
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.AppOutlinedTextField
import com.cybercat.pocketbooksender.util.performHapticIfAllowed

@Composable
internal fun SourcePicker(
    state: OpdsUiState,
    enableHaptics: Boolean,
    onOpenSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onEditCredentials: (OpdsSource) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    val currentUrl = state.currentUrl.orEmpty().trimEnd('/')
    val selectedSource = state.sources.firstOrNull { source ->
        currentUrl.startsWith(source.url.trimEnd('/'))
    } ?: state.sources.firstOrNull()
    val selectedTitle = selectedSource?.title ?: strings.get("opds_no_sources")
    val context = LocalContext.current
    val view = LocalView.current

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    HapticFeedbackConstants.VIRTUAL_KEY
                )
                expanded = true
            },
            enabled = state.sources.isNotEmpty() && !state.isLoading && !state.isDownloading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedTitle,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Outlined.Folder, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            state.sources.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = source.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        enableHaptics,
                                        HapticFeedbackConstants.VIRTUAL_KEY
                                    )
                                    expanded = false
                                    onEditCredentials(source)
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.VpnKey,
                                    contentDescription = strings.get("opds_action_edit_credentials")
                                )
                            }
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(
                                        context,
                                        enableHaptics,
                                        HapticFeedbackConstants.REJECT
                                    )
                                    expanded = false
                                    onRemoveSource(source.id)
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = strings.get("opds_action_delete_source")
                                )
                            }
                        }
                    },
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            HapticFeedbackConstants.VIRTUAL_KEY
                        )
                        expanded = false
                        onOpenSource(source.url)
                    }
                )
            }
        }
    }
}

@Composable
internal fun SearchPanel(
    query: String,
    isSearchAvailable: Boolean,
    enabled: Boolean,
    enableHaptics: Boolean,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AppOutlinedTextField(
                value = query,
                onValueChange = onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isSearchAvailable && enabled,
                label = { Text(strings.opdsSearchCatalog) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty() && enabled) {
                        IconButton(onClick = {
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                HapticFeedbackConstants.VIRTUAL_KEY
                            )
                            onSearchChanged("")
                        }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = strings.get("action_clear")
                            )
                        }
                    }
                }
            )
            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        HapticFeedbackConstants.CONFIRM
                    )
                    onSearch()
                },
                enabled = isSearchAvailable && enabled && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.opdsSearchPlaceholder)
            }

            if (!isSearchAvailable) {
                Text(
                    text = strings.opdsNoSearchSupport,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun FeedLinksRow(
    links: List<OpdsLink>,
    enabled: Boolean,
    enableHaptics: Boolean,
    onOpenLink: (OpdsLink) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        links.forEach { link ->
            FeedNavigationChip(
                icon = link.feedNavigationIcon(),
                enabled = enabled,
                label = link.displayTitle(strings),
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        HapticFeedbackConstants.VIRTUAL_KEY
                    )
                    onOpenLink(link)
                }
            )
        }
    }
}

@Composable
internal fun OpdsPaginationBar(
    paging: OpdsPagingState,
    enabled: Boolean,
    enableHaptics: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val canGoPrevious = enabled && paging.canGoPrevious
    val canGoNext = enabled && paging.canGoNext

    AnimatedVisibility(
        visible = paging.shouldShow,
        modifier = modifier.widthIn(min = 300.dp, max = 380.dp),
        enter = fadeIn() + slideInVertically { height -> height / 2 },
        exit = fadeOut() + slideOutVertically { height -> height / 2 }
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary
            ),
            tonalElevation = 6.dp,
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledIconButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            HapticFeedbackConstants.VIRTUAL_KEY
                        )
                        onPreviousPage()
                    },
                    enabled = canGoPrevious,
                    modifier = Modifier
                        .width(68.dp)
                        .height(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = strings.get("opds_page_previous")
                    )
                }
                Text(
                    text = paging.displayLabel(strings),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FilledIconButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            HapticFeedbackConstants.VIRTUAL_KEY
                        )
                        onNextPage()
                    },
                    enabled = canGoNext,
                    modifier = Modifier
                        .width(68.dp)
                        .height(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = strings.get("opds_page_next")
                    )
                }
            }
        }
    }
}

@Composable
internal fun OpdsDownloadProgressOverlay(
    progressInfo: OpdsDownloadUiProgress?,
    enableHaptics: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val totalCount = progressInfo?.totalCount?.coerceAtLeast(1) ?: 1
    val completedCount = progressInfo?.completedCount?.coerceIn(0, totalCount) ?: 0
    val progress = progressInfo?.overallProgress
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "OpdsDownloadProgress"
    )
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val isCanceling = progressInfo?.isCanceling == true

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = contentColor,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (completedCount == 0 || isCanceling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = contentColor,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = contentColor
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isCanceling) {
                            strings.get("opds_download_canceling_title")
                        } else {
                            strings.get("opds_download_progress_title")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = opdsDownloadProgressDetail(
                            strings = strings,
                            progressInfo = progressInfo,
                            completedCount = completedCount,
                            totalCount = totalCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedIconButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            HapticFeedbackConstants.REJECT
                        )
                        onCancel()
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = !isCanceling
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = strings.get("opds_download_cancel"),
                        tint = if (isCanceling) {
                            contentColor.copy(alpha = 0.38f)
                        } else {
                            contentColor
                        }
                    )
                }
            }
            if (progress == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f)
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f)
                )
            }
        }
    }
}

private fun opdsDownloadProgressDetail(
    strings: AppStrings,
    progressInfo: OpdsDownloadUiProgress?,
    completedCount: Int,
    totalCount: Int
): String {
    val currentPercent = progressInfo?.currentFilePercent
    val currentItem = progressInfo?.let {
        activeItemLabel(it.currentItemTitle, it.currentItemAuthors)
    }
    return when {
        totalCount <= 1 && currentPercent != null -> {
            currentItem?.let {
                strings.get("opds_download_progress_single_percent_with_file", it, currentPercent)
            } ?: strings.get("opds_download_progress_single_percent", currentPercent)
        }

        totalCount <= 1 -> {
            currentItem?.let {
                strings.get("opds_download_progress_single_unknown_with_file", it)
            } ?: strings.get("opds_download_progress_single_unknown")
        }

        currentPercent != null -> {
            currentItem?.let {
                strings.get(
                    "opds_download_progress_detail_with_file_and_metadata",
                    completedCount,
                    totalCount,
                    it,
                    currentPercent
                )
            } ?: strings.get(
                "opds_download_progress_detail_with_file",
                completedCount,
                totalCount,
                currentPercent
            )
        }

        else -> {
            currentItem?.let {
                strings.get(
                    "opds_download_progress_detail_current_unknown_with_file",
                    completedCount,
                    totalCount,
                    it
                )
            } ?: strings.get(
                "opds_download_progress_detail_current_unknown",
                completedCount,
                totalCount
            )
        }
    }
}

private fun activeItemLabel(title: String?, authors: List<String>): String? {
    val safeTitle = title?.takeIf { it.isNotBlank() } ?: return null
    val safeAuthors = authors.filter { it.isNotBlank() }.joinToString(", ")
    return if (safeAuthors.isBlank()) safeTitle else "$safeTitle — $safeAuthors"
}

@Composable
private fun FeedNavigationChip(
    icon: ImageVector,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit
) {
    val strings = LocalStrings.current
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.widthIn(max = 280.dp),
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
        }
    )
}

private fun OpdsLink.feedNavigationIcon() = when (normalizedRelName()) {
    "start" -> Icons.Outlined.Home
    "up" -> Icons.Outlined.ArrowUpward
    "previous", "prev" -> Icons.AutoMirrored.Outlined.ArrowBack
    "next" -> Icons.AutoMirrored.Outlined.ArrowForward
    else -> Icons.Outlined.Folder
}

private fun OpdsPagingState.displayLabel(
    strings: com.cybercat.pocketbooksender.localization.AppStrings
): String = totalPages?.let { total -> strings.get("opds_page_ratio", currentPage, total) }
    ?: strings.get("opds_page_current", currentPage)

internal fun OpdsCatalog.hasSearch(): Boolean =
    links.any { link -> link.rel.orEmpty().equals("search", ignoreCase = true) }
