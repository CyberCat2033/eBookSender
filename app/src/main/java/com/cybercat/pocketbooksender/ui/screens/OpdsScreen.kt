package com.cybercat.pocketbooksender.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.data.opds.OpdsAcquisition
import com.cybercat.pocketbooksender.data.opds.OpdsCatalog
import com.cybercat.pocketbooksender.data.opds.OpdsEntry
import com.cybercat.pocketbooksender.data.opds.OpdsLink
import com.cybercat.pocketbooksender.data.opds.downloadFormatLabel
import com.cybercat.pocketbooksender.data.opds.supportedDownloadFormat
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.cybercat.pocketbooksender.ui.BitmapCache
import com.cybercat.pocketbooksender.ui.MangaUiState
import com.cybercat.pocketbooksender.ui.OpdsUiState
import com.cybercat.pocketbooksender.ui.WebContentMode
import com.cybercat.pocketbooksender.ui.loadCachedRemoteBitmap
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    state: OpdsUiState,
    mangaState: MangaUiState,
    opdsListState: LazyListState,
    mangaListState: LazyListState,
    onSearchChanged: (String) -> Unit,
    onWebModeSelected: (WebContentMode) -> Unit,
    onSaveSource: (String, String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenLink: (OpdsLink) -> Unit,
    onBack: () -> Unit,
    onMangaBack: () -> Unit,
    onSearch: () -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    onMangaSearchChanged: (String) -> Unit,
    onMangaSearch: () -> Unit,
    onOpenMangaBrowser: () -> Unit,
    onCloseMangaBrowser: () -> Unit,
    onMangaWebPageLoaded: (String, String) -> Unit,
    onOpenMangaSeries: (String) -> Unit,
    onToggleMangaChapter: (String, Boolean) -> Unit,
    onSetMangaSeriesFavorite: (Boolean) -> Unit,
    onSetMangaSeriesSubscribed: (Boolean) -> Unit,
    onCheckMangaSubscriptions: () -> Unit,
    onSelectNewMangaChapters: () -> Unit,
    onSelectAllMangaChapters: () -> Unit,
    onClearMangaChapterSelection: () -> Unit,
    onDownloadSelectedMangaChapters: () -> Unit,
    enableHaptics: Boolean,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }
    var newSourceTitle by remember { mutableStateOf("") }
    val webMode = state.webMode
    val selectedMangaChapterCount = mangaState.selectedChapterIds.size
    val mangaSelectionActive = webMode == WebContentMode.Manga && selectedMangaChapterCount > 0

    BackHandler(enabled = webMode == WebContentMode.Opds && state.canGoBack) {
        onBack()
    }

    BackHandler(enabled = webMode == WebContentMode.Manga && mangaState.selectedSeries != null) {
        onMangaBack()
    }

    BackHandler(enabled = mangaSelectionActive && !mangaState.isDownloading) {
        onClearMangaChapterSelection()
    }

    if (showAddSourceDialog) {
        AddSourceDialog(
            url = newSourceUrl,
            title = newSourceTitle,
            onUrlChanged = { newSourceUrl = it },
            onTitleChanged = { newSourceTitle = it },
            onDismiss = { showAddSourceDialog = false },
            onSaveSource = {
                onSaveSource(newSourceTitle, newSourceUrl)
                showAddSourceDialog = false
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (mangaSelectionActive) {
                            "$selectedMangaChapterCount selected"
                        } else {
                            "Web"
                        },
                    )
                },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    if (webMode == WebContentMode.Opds && state.canGoBack) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                onBack()
                            },
                            enabled = !state.isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    } else if (webMode == WebContentMode.Manga && mangaState.selectedSeries != null) {
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                onMangaBack()
                            },
                            enabled = !mangaState.isLoading && !mangaState.isDownloading,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (mangaSelectionActive) {
                        MangaSelectionActions(
                            enabled = !mangaState.isDownloading,
                            hasNewChapters = mangaState.hasNewChapters,
                            hasChapters = mangaState.chapters.isNotEmpty(),
                            enableHaptics = enableHaptics,
                            onSelectNew = onSelectNewMangaChapters,
                            onSelectAll = onSelectAllMangaChapters,
                            onClear = onClearMangaChapterSelection,
                        )
                    } else if (webMode == WebContentMode.Opds) {
                        IconButton(
                            onClick = {
                                newSourceUrl = ""
                                newSourceTitle = ""
                                showAddSourceDialog = true
                            },
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add OPDS source")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = webMode == WebContentMode.Manga &&
                    selectedMangaChapterCount > 0 &&
                    !mangaState.isDownloading,
                enter = fadeIn() + slideInVertically { height -> height },
                exit = fadeOut() + slideOutVertically { height -> height },
            ) {
                androidx.compose.material3.ExtendedFloatingActionButton(
                    onClick = {
                        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
                        onDownloadSelectedMangaChapters()
                    },
                    icon = { Icon(Icons.Outlined.Download, contentDescription = null) },
                    text = { Text("Download ($selectedMangaChapterCount)") },
                )
            }
        },
    ) { innerPadding ->
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val contentMaxWidth = if (maxWidth >= 900.dp) 980.dp else maxWidth
            Column(
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp),
            ) {
                WebModeSelector(
                    selectedMode = webMode,
                    enableHaptics = enableHaptics,
                    onModeSelected = onWebModeSelected,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                if (webMode == WebContentMode.Manga) {
                    MangaPane(
                        state = mangaState,
                        enableHaptics = enableHaptics,
                        listState = mangaListState,
                        modifier = Modifier.weight(1f),
                        onSearchChanged = onMangaSearchChanged,
                        onSearch = onMangaSearch,
                        onOpenBrowser = onOpenMangaBrowser,
                        onCloseBrowser = onCloseMangaBrowser,
                        onWebPageLoaded = onMangaWebPageLoaded,
                        onOpenSeries = onOpenMangaSeries,
                        onToggleChapter = onToggleMangaChapter,
                        onSetMangaSeriesFavorite = onSetMangaSeriesFavorite,
                        onSetMangaSeriesSubscribed = onSetMangaSeriesSubscribed,
                        onCheckSubscriptions = onCheckMangaSubscriptions,
                        onDownloadSelected = onDownloadSelectedMangaChapters,
                    )
                } else {
                    val catalog = state.catalog
                    val entryRows = remember(catalog) { catalog?.entries?.withStableLazyKeys() ?: emptyList() }
                    val feedLinks = remember(catalog) { catalog?.links?.filter(OpdsLink::isBrowsableFeedLink) ?: emptyList() }
                    val hasSearch = remember(catalog) { catalog?.hasSearch() ?: false }
                    LazyColumn(
                        state = opdsListState,
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            SourcePicker(
                                state = state,
                                enableHaptics = enableHaptics,
                                onOpenSource = onOpenSource,
                                onRemoveSource = onRemoveSource,
                            )
                        }

                        state.errorMessage?.let { message ->
                            item {
                                StatusMessage(
                                    text = message,
                                    isError = true,
                                )
                            }
                        }

                        item {
                            StatusMessageHost(text = state.statusMessage)
                        }

                        if (state.isLoading) {
                            item {
                                LoadingCard("Opening catalog")
                            }
                        }

                        if (catalog != null) {
                            item {
                                SearchPanel(
                                    query = state.searchInput,
                                    isSearchAvailable = hasSearch,
                                    enabled = !state.isLoading,
                                    enableHaptics = enableHaptics,
                                    onSearchChanged = onSearchChanged,
                                    onSearch = onSearch,
                                )
                            }

                            if (feedLinks.isNotEmpty()) {
                                item {
                                    FeedLinksRow(
                                        links = feedLinks,
                                        enabled = !state.isLoading,
                                        enableHaptics = enableHaptics,
                                        onOpenLink = onOpenLink,
                                    )
                                }
                            }

                            if (catalog.entries.isEmpty() && !state.isLoading) {
                                item {
                                    StatusMessage(
                                        text = "Catalog page is empty",
                                        isError = false,
                                    )
                                }
                            }

                            itemsIndexed(
                                entryRows,
                                key = { _, row -> row.key },
                                contentType = { _, _ -> "entry" },
                            ) { _, row ->
                                OpdsEntryCard(
                                    entry = row.entry,
                                    enabled = !state.isLoading && !state.isDownloading,
                                    enableHaptics = enableHaptics,
                                    onOpenLink = onOpenLink,
                                    onDownload = onDownload,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            if (feedLinks.isNotEmpty()) {
                                item {
                                    FeedLinksRow(
                                        links = feedLinks,
                                        enabled = !state.isLoading,
                                        enableHaptics = enableHaptics,
                                        onOpenLink = onOpenLink,
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class OpdsEntryRow(
    val key: String,
    val entry: OpdsEntry,
)

@Composable
private fun MangaSelectionActions(
    enabled: Boolean,
    hasNewChapters: Boolean,
    hasChapters: Boolean,
    enableHaptics: Boolean,
    onSelectNew: () -> Unit,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    IconButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onSelectNew()
        },
        enabled = enabled && hasNewChapters,
    ) {
        Icon(Icons.Outlined.Checklist, contentDescription = "Select new chapters")
    }
    IconButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onSelectAll()
        },
        enabled = enabled && hasChapters,
    ) {
        Icon(Icons.Outlined.SelectAll, contentDescription = "Select all chapters")
    }
    IconButton(
        onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onClear()
        },
        enabled = enabled,
    ) {
        Icon(Icons.Outlined.Close, contentDescription = "Clear chapter selection")
    }
}

private fun List<OpdsEntry>.withStableLazyKeys(): List<OpdsEntryRow> {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebModeSelector(
    selectedMode: WebContentMode,
    enableHaptics: Boolean,
    onModeSelected: (WebContentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedMode == WebContentMode.Opds,
            onClick = {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                onModeSelected(WebContentMode.Opds)
            },
            modifier = Modifier
                .width(104.dp)
                .height(40.dp),
            label = {
                Text(
                    text = "OPDS",
                    maxLines = 1,
                )
            },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null) },
        )
        FilterChip(
            selected = selectedMode == WebContentMode.Manga,
            onClick = {
                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                onModeSelected(WebContentMode.Manga)
            },
            modifier = Modifier
                .width(116.dp)
                .height(40.dp),
            label = {
                Text(
                    text = "Manga",
                    maxLines = 1,
                )
            },
            leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
        )
    }
}

@Composable
private fun AddSourceDialog(
    url: String,
    title: String,
    onUrlChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSaveSource: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add OPDS source") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = onUrlChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("OPDS URL") },
                    leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                    placeholder = { Text("https://example.org/opds") },
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Source title") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSaveSource,
                enabled = url.isNotBlank(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SourcePicker(
    state: OpdsUiState,
    enableHaptics: Boolean,
    onOpenSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentUrl = state.currentUrl.orEmpty().trimEnd('/')
    val selectedSource = state.sources.firstOrNull { source ->
        currentUrl.startsWith(source.url.trimEnd('/'))
    } ?: state.sources.firstOrNull()
    val selectedTitle = selectedSource?.title ?: "No OPDS catalogs"
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
                        IconButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.REJECT)
                                expanded = false
                                onRemoveSource(source.id)
                            },
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete OPDS source")
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
private fun SearchPanel(
    query: String,
    isSearchAvailable: Boolean,
    enabled: Boolean,
    enableHaptics: Boolean,
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onSearchChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = isSearchAvailable && enabled,
                label = { Text("Search catalog") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty() && enabled) {
                        IconButton(onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onSearchChanged("")
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear")
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
                Text("Search")
            }

            if (!isSearchAvailable) {
                Text(
                    text = "This catalog page does not expose OPDS search.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FeedLinksRow(
    links: List<OpdsLink>,
    enabled: Boolean,
    enableHaptics: Boolean,
    onOpenLink: (OpdsLink) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
                Text(link.displayTitle())
            }
        }
    }
}

@Composable
private fun OpdsEntryCard(
    entry: OpdsEntry,
    enabled: Boolean,
    enableHaptics: Boolean,
    onOpenLink: (OpdsLink) -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val isNavigation = remember(entry) { entry.acquisitions.isEmpty() && entry.navigation.isNotEmpty() }

    ElevatedCard(modifier.fillMaxWidth()) {
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

            if (entry.navigation.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entry.navigation.forEach { link ->
                        OutlinedButton(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                                onOpenLink(link)
                            },
                            enabled = enabled,
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(link.displayTitle())
                        }
                    }
                }
            }

            val supportedAcquisitions = remember(entry) {
                entry.acquisitions
                    .mapNotNull { acquisition ->
                        acquisition.supportedDownloadFormat()?.let { format -> acquisition to format.label }
                    }
                    .distinctBy { (_, label) -> label }
            }
            val visibleAcquisitions = remember(entry, supportedAcquisitions) {
                supportedAcquisitions.ifEmpty {
                    entry.acquisitions
                        .map { acquisition -> acquisition to acquisition.downloadFormatLabel() }
                        .distinctBy { (_, label) -> label }
                }
            }

            if (visibleAcquisitions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleAcquisitions.forEach { (acquisition, label) ->
                        Button(
                            onClick = {
                                view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CONFIRM)
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
}

@Composable
private fun EntryArtwork(
    coverUrl: String?,
    isNavigation: Boolean,
    title: String,
) {
    val context = LocalContext.current
    var bitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(coverUrl?.let { BitmapCache.getFromMemory(it) }) }

    LaunchedEffect(coverUrl) {
        if (coverUrl != null && bitmap == null) {
            delay(CoverLoadDelayMillis)
            if (bitmap == null) {
                bitmap = loadCachedRemoteBitmap(
                    context = context,
                    url = coverUrl,
                    reqWidth = CoverRequestWidth,
                    reqHeight = CoverRequestHeight,
                )
            }
        }
    }

    Surface(
        modifier = Modifier.size(width = 58.dp, height = 78.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        val cover = bitmap
        if (cover != null) {
            ComposeImage(
                bitmap = cover.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        isNavigation -> Icons.Outlined.Folder
                        coverUrl != null -> Icons.Outlined.Image
                        else -> Icons.AutoMirrored.Outlined.MenuBook
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun StatusMessageHost(text: String?) {
    var lastText by remember { mutableStateOf(text.orEmpty()) }

    LaunchedEffect(text) {
        if (!text.isNullOrBlank()) {
            lastText = text
        }
    }

    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + slideInVertically { height -> -height / 4 },
        exit = fadeOut() + slideOutVertically { height -> -height / 4 },
    ) {
        StatusMessage(
            text = lastText,
            isError = false,
        )
    }
}

@Composable
private fun StatusMessage(
    text: String,
    isError: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        )
    }
}

private fun OpdsLink.displayTitle(): String =
    title?.takeIf { it.isNotBlank() }
        ?: rel?.substringAfterLast('/')?.replaceFirstChar { it.uppercase() }
        ?: "Open"

private fun OpdsLink.isBrowsableFeedLink(): Boolean {
    val relValue = rel.orEmpty()
    val typeValue = type.orEmpty()
    return relValue in setOf("next", "previous", "up", "start") ||
        (relValue != "self" && typeValue.contains("profile=opds-catalog"))
}

private fun OpdsCatalog.hasSearch(): Boolean =
    links.any { link -> link.rel.orEmpty().equals("search", ignoreCase = true) }

private val htmlTagRegex = Regex("<[^>]+>")
private val whitespaceRegex = Regex("\\s+")

private fun String.cleanSummary(): String =
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
