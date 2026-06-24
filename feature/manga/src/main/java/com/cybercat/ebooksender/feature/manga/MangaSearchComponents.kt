package com.cybercat.ebooksender.feature.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.data.manga.MangaSeriesSearchResult
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.ui.AppOutlinedTextField
import com.cybercat.ebooksender.ui.SourceSelectionItem
import com.cybercat.ebooksender.ui.SourceSelectionMenu
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed

@Composable
internal fun MangaSourceSelector(
    state: MangaUiState,
    enableHaptics: Boolean,
    onSelectSource: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.sources.size <= 1) return
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val selectedTitle = state.sources
        .firstOrNull { it.id == state.selectedSourceId }?.title
        ?: state.sources.first().title
    val items = state.sources.map { source ->
        SourceSelectionItem(id = source.id, title = source.title)
    }

    SourceSelectionMenu(
        selectedTitle = selectedTitle,
        items = items,
        enabled = !state.isLoading && !state.isDownloading,
        contentDescription = strings.mangaSource,
        onItemSelected = { item ->
            if (item.id != state.selectedSourceId) onSelectSource(item.id)
        },
        modifier = modifier,
        leadingIcon = Icons.AutoMirrored.Outlined.MenuBook,
        onPress = {
            view.performHapticIfAllowed(
                context,
                enableHaptics,
                AppHapticFeedback.Press
            )
        }
    )
}

@Composable
internal fun MangaSearchPanel(
    state: MangaUiState,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenBrowser: () -> Unit,
    enableHaptics: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            val configuration = LocalConfiguration.current
            val isWideScreen = configuration.screenWidthDp >= 640

            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MangaSearchField(
                        state = state,
                        onSearchChanged = onSearchChanged,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.weight(1f)
                    )
                    MangaSearchButton(
                        state = state,
                        onSearch = onSearch,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.width(180.dp)
                    )
                    if (!state.isAuthorized) {
                        MangaLoginButton(
                            state = state,
                            enableHaptics = enableHaptics,
                            onOpenBrowser = onOpenBrowser
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MangaSearchField(
                        state = state,
                        onSearchChanged = onSearchChanged,
                        enableHaptics = enableHaptics,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MangaSearchButton(
                            state = state,
                            onSearch = onSearch,
                            enableHaptics = enableHaptics,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        if (!state.isAuthorized) {
                            MangaLoginButton(
                                state = state,
                                enableHaptics = enableHaptics,
                                onOpenBrowser = onOpenBrowser,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MangaSearchField(
    state: MangaUiState,
    onSearchChanged: (String) -> Unit,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val enabled = !state.isLoading && !state.isDownloading
    AppOutlinedTextField(
        value = state.searchInput,
        onValueChange = onSearchChanged,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        label = { Text(strings.mangaSearchManga) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (state.searchInput.isNotEmpty() && enabled) {
                IconButton(onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Press
                    )
                    onSearchChanged("")
                }) {
                    Icon(Icons.Outlined.Close, contentDescription = strings.get("action_clear"))
                }
            }
        }
    )
}

@Composable
internal fun MangaSearchButton(
    state: MangaUiState,
    onSearch: () -> Unit,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    Button(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Confirm)
            onSearch()
        },
        enabled = state.searchInput.isNotBlank() && !state.isLoading && !state.isDownloading,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.mangaSearchMangaPlaceholder)
    }
}

@Composable
internal fun MangaLoginButton(
    state: MangaUiState,
    enableHaptics: Boolean,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    OutlinedButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Press)
            onOpenBrowser()
        },
        enabled = !state.isDownloading,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(strings.mangaBtnLogin)
    }
}

@Composable
internal fun MangaSearchResultCard(
    result: MangaSeriesSearchResult,
    enabled: Boolean,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier,
    onOpenSeries: (String) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    Card(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Press)
            onOpenSeries(result.seriesId)
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MangaCover(
                coverUrl = result.coverUrl,
                title = result.title
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                result.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val strings = LocalStrings.current
            TextButton(
                onClick = { onOpenSeries(result.seriesId) },
                enabled = enabled
            ) {
                Text(strings.mangaBtnOpen)
            }
        }
    }
}

@Composable
internal fun MangaSectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}
