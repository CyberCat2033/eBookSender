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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
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
import com.cybercat.pocketbooksender.ui.MangaUiState
import com.cybercat.pocketbooksender.ui.OpdsUiState
import com.cybercat.pocketbooksender.ui.WebContentMode
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpdsScreen(
    state: OpdsUiState,
    mangaState: MangaUiState,
    onSearchChanged: (String) -> Unit,
    onWebModeSelected: (WebContentMode) -> Unit,
    onSaveSource: (String, String) -> Unit,
    onRemoveSource: (String) -> Unit,
    onOpenSource: (String) -> Unit,
    onOpenLink: (OpdsLink) -> Unit,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
    onMangaSearchChanged: (String) -> Unit,
    onMangaSearch: () -> Unit,
    onOpenMangaBrowser: () -> Unit,
    onCloseMangaBrowser: () -> Unit,
    onMangaWebPageLoaded: (String, String) -> Unit,
    onOpenMangaSeries: (String) -> Unit,
    onToggleMangaChapter: (String, Boolean) -> Unit,
    onSelectNewMangaChapters: () -> Unit,
    onSelectAllMangaChapters: () -> Unit,
    onClearMangaChapterSelection: () -> Unit,
    onDownloadSelectedMangaChapters: () -> Unit,
) {
    var showAddSourceDialog by remember { mutableStateOf(false) }
    var newSourceUrl by remember { mutableStateOf("") }
    var newSourceTitle by remember { mutableStateOf("") }
    val webMode = state.webMode

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
                title = { Text("Web") },
                windowInsets = WindowInsets(0.dp),
                navigationIcon = {
                    if (webMode == WebContentMode.Opds && state.canGoBack) {
                        IconButton(
                            onClick = onBack,
                            enabled = !state.isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
                actions = {
                    if (webMode == WebContentMode.Opds) {
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
                    onModeSelected = onWebModeSelected,
                    modifier = Modifier.padding(bottom = 10.dp),
                )

                if (webMode == WebContentMode.Manga) {
                    MangaPane(
                        state = mangaState,
                        modifier = Modifier.weight(1f),
                        onSearchChanged = onMangaSearchChanged,
                        onSearch = onMangaSearch,
                        onOpenBrowser = onOpenMangaBrowser,
                        onCloseBrowser = onCloseMangaBrowser,
                        onWebPageLoaded = onMangaWebPageLoaded,
                        onOpenSeries = onOpenMangaSeries,
                        onToggleChapter = onToggleMangaChapter,
                        onSelectNewChapters = onSelectNewMangaChapters,
                        onSelectAllChapters = onSelectAllMangaChapters,
                        onClearChapterSelection = onClearMangaChapterSelection,
                        onDownloadSelected = onDownloadSelectedMangaChapters,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            SourcePicker(
                                state = state,
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

                        val catalog = state.catalog
                        if (catalog != null) {
                            item {
                                SearchPanel(
                                    query = state.searchInput,
                                    isSearchAvailable = catalog.hasSearch(),
                                    enabled = !state.isLoading,
                                    onSearchChanged = onSearchChanged,
                                    onSearch = onSearch,
                                )
                            }

                            val feedLinks = catalog.links.filter(OpdsLink::isBrowsableFeedLink)
                            if (feedLinks.isNotEmpty()) {
                                item {
                                    FeedLinksRow(
                                        links = feedLinks,
                                        enabled = !state.isLoading,
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

                            val entryRows = catalog.entries.withStableLazyKeys()
                            itemsIndexed(
                                entryRows,
                                key = { _, row -> row.key },
                            ) { _, row ->
                                OpdsEntryCard(
                                    entry = row.entry,
                                    enabled = !state.isLoading && !state.isDownloading,
                                    onOpenLink = onOpenLink,
                                    onDownload = onDownload,
                                )
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
    onModeSelected: (WebContentMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = selectedMode == WebContentMode.Opds,
            onClick = { onModeSelected(WebContentMode.Opds) },
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
            onClick = { onModeSelected(WebContentMode.Manga) },
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
    onOpenSource: (String) -> Unit,
    onRemoveSource: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentUrl = state.currentUrl.orEmpty().trimEnd('/')
    val selectedSource = state.sources.firstOrNull { source ->
        currentUrl.startsWith(source.url.trimEnd('/'))
    } ?: state.sources.firstOrNull()
    val selectedTitle = selectedSource?.title ?: "No OPDS catalogs"

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
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
                                expanded = false
                                onRemoveSource(source.id)
                            },
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete OPDS source")
                        }
                    },
                    onClick = {
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
    onSearchChanged: (String) -> Unit,
    onSearch: () -> Unit,
) {
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
            )
            Button(
                onClick = onSearch,
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
    onOpenLink: (OpdsLink) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        links.forEach { link ->
            OutlinedButton(
                onClick = { onOpenLink(link) },
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
    onOpenLink: (OpdsLink) -> Unit,
    onDownload: (OpdsEntry, OpdsAcquisition) -> Unit,
) {
    val isNavigation = entry.acquisitions.isEmpty() && entry.navigation.isNotEmpty()

    ElevatedCard(Modifier.fillMaxWidth()) {
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

            entry.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                Text(
                    text = summary.cleanSummary(),
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
                            onClick = { onOpenLink(link) },
                            enabled = enabled,
                        ) {
                            Icon(Icons.Outlined.Folder, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(link.displayTitle())
                        }
                    }
                }
            }

            if (entry.acquisitions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    entry.acquisitions.forEach { acquisition ->
                        Button(
                            onClick = { onDownload(entry, acquisition) },
                            enabled = enabled,
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(acquisition.displayFormat())
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
    var bitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(coverUrl) {
        bitmap = coverUrl?.let { loadRemoteBitmap(it) }
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

private suspend fun loadRemoteBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "image/*")
            setRequestProperty("User-Agent", "PocketBookSender/0.1")
        }
        try {
            if (connection.responseCode !in 200..299) return@runCatching null
            connection.inputStream.use(BitmapFactory::decodeStream)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()
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

private fun OpdsAcquisition.displayFormat(): String {
    val explicitTitle = title?.takeIf { it.isNotBlank() }
    if (explicitTitle != null && explicitTitle.length <= 18) return explicitTitle

    val mime = type.orEmpty().lowercase()
    return when {
        mime.contains("epub") -> "EPUB"
        mime.contains("fb2") -> "FB2"
        mime.contains("mobipocket") || mime.contains("mobi") -> "MOBI"
        mime.contains("pdf") -> "PDF"
        mime.contains("djvu") -> "DJVU"
        mime.contains("comicbook+zip") -> "CBZ"
        mime.contains("comicbook-rar") || mime.contains("rar") -> "CBR"
        mime.contains("zip") -> "ZIP"
        explicitTitle != null -> explicitTitle
        else -> "Download"
    }
}

private fun String.cleanSummary(): String =
    replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
