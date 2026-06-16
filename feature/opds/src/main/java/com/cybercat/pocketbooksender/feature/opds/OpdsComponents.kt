package com.cybercat.pocketbooksender.feature.opds

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.data.opds.OpdsAcquisition
import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
import com.cybercat.pocketbooksender.data.opds.OpdsEntry
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.OpdsSource
import com.cybercat.pocketbooksender.data.opds.downloadFormatLabel
import com.cybercat.pocketbooksender.data.opds.supportedDownloadFormat
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.ui.AnimatedAlertDialog
import com.cybercat.pocketbooksender.ui.AppOutlinedTextField
import com.cybercat.pocketbooksender.ui.LocalDismissDialog
import com.cybercat.pocketbooksender.ui.BitmapCache
import com.cybercat.pocketbooksender.ui.loadCachedRemoteBitmap
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.delay

internal data class OpdsEntryRow(
    val key: String,
    val entry: OpdsEntry,
)

internal fun List<OpdsEntry>.withStableLazyKeys(): List<OpdsEntryRow> {
    val seen = mutableMapOf<String, Int>()
    return mapIndexed { index, entry ->
        val acquisitionKey = entry.acquisitions.firstOrNull()?.href.orEmpty()
        val navigationKey = entry.navigation.firstOrNull()?.href.orEmpty()
        val baseKey = listOf(
            entry.id.orEmpty(),
            entry.title,
            entry.authors.joinToString("|"),
            acquisitionKey,
            navigationKey,
        )
            .joinToString(":")
            .ifBlank { "opds-entry" }
        val duplicateIndex = seen.getOrDefault(baseKey, 0)
        seen[baseKey] = duplicateIndex + 1
        OpdsEntryRow(
            key = if (duplicateIndex == 0) {
                baseKey
            } else {
                "$baseKey:duplicate:$duplicateIndex:$index"
            },
            entry = entry,
        )
    }
}

@Composable
internal fun AddSourceDialog(
    url: String,
    title: String,
    username: String,
    password: String,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveSource: () -> Unit,
) {
    val strings = LocalStrings.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.opdsAddTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppOutlinedTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsUrlField) },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    placeholderText = strings.opdsUrlPlaceholder,
                )
                AppOutlinedTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsTitleField) },
                )
                AppOutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsUsernameField) },
                )
                AppOutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsPasswordField) },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            Button(
                onClick = { onSaveSource(); dismiss() },
                enabled = url.isNotBlank(),
            ) {
                Text(strings.opdsBtnSave)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(onClick = dismiss) {
                Text(strings.opdsBtnCancel)
            }
        },
    )
}

@Composable
internal fun SourcePicker(
    state: OpdsUiState,
    enableHaptics: Boolean,
    onOpenSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onEditCredentials: (OpdsSource) -> Unit,
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
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                expanded = true
            },
            enabled = state.sources.isNotEmpty() && !state.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedTitle,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.Folder, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            state.sources.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = source.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingIcon = {
                        Row {
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                    expanded = false
                                    onEditCredentials(source)
                                },
                            ) {
                                Icon(Icons.Outlined.VpnKey, contentDescription = strings.get("opds_action_edit_credentials"))
                            }
                            IconButton(
                                onClick = {
                                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.REJECT)
                                    expanded = false
                                    onRemoveSource(source.id)
                                },
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = strings.get("opds_action_delete_source"))
                            }
                        }
                    },
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                        expanded = false
                        onOpenSource(source.url)
                    },
                )
            }
        }
    }
}

@Composable
internal fun OpdsCredentialsDialog(
    sourceTitle: String,
    username: String,
    password: String,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val strings = LocalStrings.current
    AnimatedAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.opdsAuthRequiredTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = sourceTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AppOutlinedTextField(
                    value = username,
                    onValueChange = onUsernameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsUsernameLabel) },
                    leadingIcon = { Icon(Icons.Outlined.VpnKey, contentDescription = null) },
                )
                AppOutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(strings.opdsPasswordLabel) },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            Button(onClick = { onSave(); dismiss() }) {
                Text(strings.opdsAuthBtnLogin)
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(onClick = dismiss) {
                Text(strings.opdsBtnCancel)
            }
        },
    )
}

@Composable
internal fun SearchPanel(
    query: String,
    isSearchAvailable: Boolean,
    enabled: Boolean,
    enableHaptics: Boolean,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onSearchChanged("")
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = strings.get("action_clear"))
                        }
                    }
                }
            )
            Button(
                onClick = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                    onSearch()
                },
                enabled = isSearchAvailable && enabled && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(strings.opdsSearchPlaceholder)
            }

            if (!isSearchAvailable) {
                Text(
                    text = strings.opdsNoSearchSupport,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    TrailingActionRow(modifier = modifier) {
        links.forEach { link ->
            OutlinedButton(
                onClick = {
                    view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                    onOpenLink(link)
                },
                enabled = enabled,
            ) {
                Icon(Icons.Outlined.Folder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(link.displayTitle(strings))
            }
        }
    }
}

@Composable
private fun TrailingActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OpdsEntryCard(
    entry: OpdsEntry,
    enabled: Boolean,
    enableHaptics: Boolean,
    onOpenLink: (OpdsLink) -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val navigationLinks = remember(entry) { entry.navigation.distinctBy { link -> link.href } }
    val isNavigation = remember(entry, navigationLinks) { entry.acquisitions.isEmpty() && navigationLinks.isNotEmpty() }
    val primaryNavigationLink = remember(entry, navigationLinks, isNavigation) {
        if (isNavigation && navigationLinks.size == 1) navigationLinks.first() else null
    }

    if (primaryNavigationLink != null) {
        ElevatedCard(
            onClick = {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                onOpenLink(primaryNavigationLink)
            },
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
        ) {
            OpdsEntryCardContent(
                entry = entry,
                isNavigation = isNavigation,
                navigationLinks = emptyList(),
                showNavigationIndicator = true,
                enabled = enabled,
                strings = strings,
                onOpenLink = onOpenLink,
                onDownload = onDownload,
                onActionHaptic = { feedbackConstant ->
                    view.performHapticIfAllowed(context, enableHaptics, feedbackConstant)
                },
            )
        }
    } else {
        ElevatedCard(modifier.fillMaxWidth()) {
            OpdsEntryCardContent(
                entry = entry,
                isNavigation = isNavigation,
                navigationLinks = navigationLinks,
                showNavigationIndicator = false,
                enabled = enabled,
                strings = strings,
                onOpenLink = onOpenLink,
                onDownload = onDownload,
                onActionHaptic = { feedbackConstant ->
                    view.performHapticIfAllowed(context, enableHaptics, feedbackConstant)
                },
            )
        }
    }
}

@Composable
private fun OpdsEntryCardContent(
    entry: OpdsEntry,
    isNavigation: Boolean,
    navigationLinks: List<OpdsLink>,
    showNavigationIndicator: Boolean,
    enabled: Boolean,
    strings: com.cybercat.pocketbooksender.localization.AppStrings,
    onOpenLink: (OpdsLink) -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    onActionHaptic: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryArtwork(
                coverUrl = entry.coverHref,
                isNavigation = isNavigation,
                title = entry.title,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.authors.isNotEmpty()) {
                    Text(
                        text = entry.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (showNavigationIndicator) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val cleanedSummary = remember(entry.summary) {
            entry.summary?.takeIf { it.isNotBlank() }?.cleanSummary()
        }
        if (cleanedSummary != null) {
            Text(
                text = cleanedSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (navigationLinks.isNotEmpty()) {
            TrailingActionRow {
                navigationLinks.forEach { link ->
                    OutlinedButton(
                        onClick = {
                            onActionHaptic(HapticFeedbackConstants.VIRTUAL_KEY)
                            onOpenLink(link)
                        },
                        enabled = enabled,
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(link.displayTitle(strings))
                    }
                }
            }
        }

        val supportedAcquisitions: List<Pair<OpdsAcquisition, String>> = remember(entry) {
            entry.acquisitions
                .mapNotNull { acquisition ->
                    acquisition.supportedDownloadFormat()?.let { format -> acquisition to format.label }
                }
                .distinctBy { (_, label) -> label }
        }
        val visibleAcquisitions: List<Pair<OpdsAcquisition, String>> = remember(entry, supportedAcquisitions) {
            supportedAcquisitions.ifEmpty {
                entry.acquisitions
                    .map { acquisition -> acquisition to acquisition.downloadFormatLabel() }
                    .distinctBy { (_, label) -> label }
            }
        }

        if (visibleAcquisitions.isNotEmpty()) {
            TrailingActionRow {
                visibleAcquisitions.forEach { (acquisition, label) ->
                    Button(
                        onClick = {
                            onActionHaptic(HapticFeedbackConstants.CONFIRM)
                            onDownload(entry, acquisition)
                        },
                        enabled = enabled,
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(label)
                    }
                }
            }
        }
    }
}
@Composable
internal fun EntryArtwork(
    coverUrl: String?,
    isNavigation: Boolean,
    title: String,
) {
    val placeholderIcon = when {
        isNavigation -> androidx.compose.material.icons.Icons.Outlined.Folder
        coverUrl != null -> androidx.compose.material.icons.Icons.Outlined.Image
        else -> androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook
    }
    com.cybercat.pocketbooksender.ui.RemoteCover(
        coverUrl = coverUrl,
        title = title,
        placeholderIcon = placeholderIcon,
    )
}


internal fun OpdsLink.displayTitle(strings: com.cybercat.pocketbooksender.localization.AppStrings): String {
    val linkTitle = title
    if (!linkTitle.isNullOrBlank()) return linkTitle
    val relValue = rel?.substringAfterLast('/')?.lowercase() ?: return strings.opdsRelOpen
    return when (relValue) {
        "start" -> strings.opdsRelStart
        "next" -> strings.opdsRelNext
        "previous", "prev" -> strings.opdsRelPrevious
        "up" -> strings.opdsRelUp
        "open" -> strings.opdsRelOpen
        else -> strings.opdsRelOpen
    }
}

internal fun OpdsLink.isBrowsableFeedLink(): Boolean {
    val relValue = rel.orEmpty()
    val typeValue = type.orEmpty()
    return relValue in setOf("next", "previous", "up", "start") ||
        (relValue != "self" && typeValue.contains("profile=opds-catalog"))
}

internal fun OpdsCatalog.hasSearch(): Boolean =
    links.any { link -> link.rel.orEmpty().equals("search", ignoreCase = true) }

private val htmlTagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")

internal fun String.cleanSummary(): String =
    replace(htmlTagRegex, " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(whitespaceRegex, " ")
        .trim()

private const val CoverLoadDelayMillis = 120L
private const val CoverRequestWidth = 160
private const val CoverRequestHeight = 220
