package com.cybercat.pocketbooksender.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    catalog: DeviceCatalog,
    isConnected: Boolean,
    onRefresh: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalog") },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = isConnected && !catalog.isLoading,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh catalog")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!isConnected) {
                item {
                    CatalogMessage(
                        title = "PocketBook is not connected",
                        text = "Connect by QR or FTP link on the Send tab first.",
                    )
                }
                return@LazyColumn
            }

            if (catalog.isLoading) {
                item {
                    CatalogMessage(
                        title = "Reading PocketBook catalog",
                        text = "Scanning Books, Programming, and Manga folders by FTP.",
                    )
                }
            }

            catalog.errorMessage?.let { message ->
                item {
                    CatalogMessage(
                        title = "Cannot read catalog",
                        text = message,
                        isError = true,
                    )
                }
            }

            if (!catalog.isLoading && catalog.isEmpty) {
                item {
                    CatalogMessage(
                        title = "Catalog is empty",
                        text = "No books were found under Books, Programming, or Manga.",
                    )
                }
            }

            item {
                SectionTitle("Books", catalog.books.sumOf { it.files.size })
            }
            items(catalog.books, key = { "books:${it.path}" }) { group ->
                CatalogGroupCard(group = group)
            }

            item {
                SectionTitle("Programming", catalog.programming.sumOf { it.files.size })
            }
            items(catalog.programming, key = { "programming:${it.path}" }) { group ->
                CatalogGroupCard(group = group)
            }

            item {
                SectionTitle("Manga", catalog.manga.size)
            }
            items(catalog.manga, key = { "manga:${it.path}" }) { group ->
                MangaSeriesCard(group = group)
            }
        }
    }
}

@Composable
private fun CatalogMessage(
    title: String,
    text: String,
    isError: Boolean = false,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CatalogGroupCard(group: CatalogGroup) {
    var expanded by remember(group.path) { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ExpandableHeader(
                title = group.name,
                subtitle = "${group.files.size} files",
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            if (expanded) {
                FileList(group.files)
            }
        }
    }
}

@Composable
private fun MangaSeriesCard(group: MangaSeriesGroup) {
    var expanded by remember(group.path) { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ExpandableHeader(
                title = group.name,
                subtitle = group.latestFile?.let { "Latest: ${it.name}" } ?: "No files",
                subtitleMaxLines = 3,
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
            if (expanded) {
                FileList(group.files)
            }
        }
    }
}

@Composable
private fun ExpandableHeader(
    title: String,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggle) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
    }
}

@Composable
private fun FileList(files: List<CatalogFile>) {
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        files.forEach { file ->
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
