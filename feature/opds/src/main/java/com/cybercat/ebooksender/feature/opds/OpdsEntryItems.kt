package com.cybercat.ebooksender.feature.opds

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.data.opds.OpdsAcquisition
import com.cybercat.ebooksender.data.opds.OpdsEntry
import com.cybercat.ebooksender.data.opds.OpdsLink
import com.cybercat.ebooksender.data.opds.downloadFormatLabel
import com.cybercat.ebooksender.data.opds.supportedDownloadFormat
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

internal data class OpdsEntryRow(val key: String, val entry: OpdsEntry)

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
            navigationKey
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
            entry = entry
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val navigationLinks = remember(entry) { entry.navigation.distinctBy { link -> link.href } }
    val isNavigation = remember(entry, navigationLinks) {
        entry.acquisitions.isEmpty() &&
            navigationLinks.isNotEmpty()
    }
    val primaryNavigationLink = remember(entry, navigationLinks, isNavigation) {
        if (isNavigation && navigationLinks.size == 1) navigationLinks.first() else null
    }

    if (primaryNavigationLink != null) {
        Card(
            onClick = {
                view.performHapticIfAllowed(
                    context,
                    enableHaptics,
                    AppHapticFeedback.Press
                )
                onOpenLink(primaryNavigationLink)
            },
            enabled = enabled,
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
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
                onActionHaptic = { feedback ->
                    view.performHapticIfAllowed(context, enableHaptics, feedback)
                }
            )
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            )
        ) {
            OpdsEntryCardContent(
                entry = entry,
                isNavigation = isNavigation,
                navigationLinks = navigationLinks,
                showNavigationIndicator = false,
                enabled = enabled,
                strings = strings,
                onOpenLink = onOpenLink,
                onDownload = onDownload,
                onActionHaptic = { feedback ->
                    view.performHapticIfAllowed(context, enableHaptics, feedback)
                }
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
    strings: com.cybercat.ebooksender.localization.AppStrings,
    onOpenLink: (OpdsLink) -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    onActionHaptic: (AppHapticFeedback) -> Unit
) {
    Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryArtwork(
                coverUrl = entry.coverHref,
                isNavigation = isNavigation,
                title = entry.title
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.authors.isNotEmpty()) {
                    Text(
                        text = entry.authors.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showNavigationIndicator) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                overflow = TextOverflow.Ellipsis
            )
        }

        if (navigationLinks.isNotEmpty()) {
            CenteredActionRow {
                navigationLinks.forEach { link ->
                    OutlinedButton(
                        onClick = {
                            onActionHaptic(AppHapticFeedback.Press)
                            onOpenLink(link)
                        },
                        enabled = enabled,
                        modifier = Modifier.widthIn(max = NavigationActionButtonMaxWidth)
                    ) {
                        Icon(Icons.Outlined.Folder, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = link.displayTitle(strings),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        val supportedAcquisitions: List<Pair<OpdsAcquisition, String>> = remember(entry) {
            entry.acquisitions
                .mapNotNull { acquisition ->
                    acquisition.supportedDownloadFormat()?.let { format ->
                        acquisition to
                            format.label
                    }
                }
                .distinctBy { (_, label) -> label }
        }
        val visibleAcquisitions: List<Pair<OpdsAcquisition, String>> =
            remember(entry, supportedAcquisitions) {
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
                            onActionHaptic(AppHapticFeedback.Confirm)
                            onDownload(entry, acquisition)
                        },
                        enabled = enabled
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
internal fun EntryArtwork(coverUrl: String?, isNavigation: Boolean, title: String) {
    val placeholderIcon = when {
        isNavigation -> androidx.compose.material.icons.Icons.Outlined.Folder
        coverUrl != null -> androidx.compose.material.icons.Icons.Outlined.Image
        else -> androidx.compose.material.icons.Icons.AutoMirrored.Outlined.MenuBook
    }
    com.cybercat.ebooksender.ui.RemoteCover(
        coverUrl = coverUrl,
        title = title,
        placeholderIcon = placeholderIcon
    )
}

@Composable
private fun CenteredActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .widthIn(min = maxWidth),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun TrailingActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

internal fun OpdsLink.displayTitle(
    strings: com.cybercat.ebooksender.localization.AppStrings
): String {
    val linkTitle = title
    if (!linkTitle.isNullOrBlank()) return linkTitle
    val relValue = normalizedRelName() ?: return strings.opdsRelOpen
    return when (relValue) {
        "start" -> strings.opdsRelStart
        "next" -> strings.opdsRelNext
        "previous", "prev" -> strings.opdsRelPrevious
        "up" -> strings.opdsRelUp
        "open" -> strings.opdsRelOpen
        else -> strings.opdsRelOpen
    }
}

private val htmlTagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")

internal fun String.cleanSummary(): String = replace(htmlTagRegex, " ")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .replace(whitespaceRegex, " ")
    .trim()

private val NavigationActionButtonMaxWidth = 320.dp
