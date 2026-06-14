package com.cybercat.pocketbooksender.ui.screens

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.DeviceCatalog
import com.cybercat.pocketbooksender.model.MangaSeriesGroup

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    catalog: DeviceCatalog,
    isConnected: Boolean,
    enableHaptics: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catalog") },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    IconButton(
                        onClick = {
                            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
                            onRefresh()
                        },
                        enabled = isConnected && !catalog.isLoading,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh catalog")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
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
                        text = "Reading PocketBook library database with FTP fallback.",
                        isLoading = true,
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
            items(
                items = catalog.books,
                key = { "books:${it.path}" },
                contentType = { "catalog_group" }
            ) { group ->
                CatalogGroupCard(group = group, enableHaptics = enableHaptics, modifier = Modifier.animateItem())
            }

            item {
                SectionTitle("Programming", catalog.programming.sumOf { it.files.size })
            }
            items(
                items = catalog.programming,
                key = { "programming:${it.path}" },
                contentType = { "catalog_group" }
            ) { group ->
                CatalogGroupCard(group = group, enableHaptics = enableHaptics, modifier = Modifier.animateItem())
            }

            item {
                SectionTitle("Manga", catalog.manga.size)
            }
            items(
                items = catalog.manga,
                key = { "manga:${it.path}" },
                contentType = { "manga_series_group" }
            ) { group ->
                MangaSeriesCard(group = group, enableHaptics = enableHaptics, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun CatalogMessage(
    title: String,
    text: String,
    isError: Boolean = false,
    isLoading: Boolean = false,
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
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
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
private fun CatalogGroupCard(
    group: CatalogGroup,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember(group.path) { mutableStateOf(false) }

    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ExpandableHeader(
                title = group.name,
                subtitle = group.files.summary(),
                expanded = expanded,
                enableHaptics = enableHaptics,
                onToggle = { expanded = !expanded },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                FileList(group.files)
            }
        }
    }
}

@Composable
private fun MangaSeriesCard(
    group: MangaSeriesGroup,
    enableHaptics: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember(group.path) { mutableStateOf(false) }

    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ExpandableHeader(
                title = group.name,
                subtitle = group.latestFile?.let { file ->
                    "Latest: ${file.displayTitle()}${file.progressSuffix()}"
                } ?: "No files",
                subtitleMaxLines = 3,
                expanded = expanded,
                enableHaptics = enableHaptics,
                onToggle = { expanded = !expanded },
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                FileList(files = group.files, showProgress = false)
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
    enableHaptics: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
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
        val rotationState by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "ChevronRotation"
        )
        IconButton(onClick = {
            view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
            onToggle()
        }) {
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotationState)
            )
        }
    }
}

@Composable
private fun FileList(
    files: List<CatalogFile>,
    showProgress: Boolean = true,
) {
    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        files.forEach { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayTitle(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (showProgress) {
                        val progressDetailText = when {
                            file.completed -> "Completed"
                            file.currentPage != null && file.currentPage > 0 -> {
                                if (file.totalPages != null && file.totalPages > 0) {
                                    "Page ${file.currentPage} of ${file.totalPages}"
                                } else {
                                    "Page ${file.currentPage}"
                                }
                            }
                            else -> "Not started"
                        }

                        val lastReadText = file.lastOpenedAtMillis?.let { time ->
                            val relative = DateUtils.getRelativeTimeSpanString(
                                time,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                            ).toString()
                            "Last read: $relative"
                        }

                        val subtitleParts = buildList {
                            if (!file.series.isNullOrBlank()) {
                                add("Series: ${file.series}")
                            }
                            add(progressDetailText)
                            if (lastReadText != null) {
                                add(lastReadText)
                            }
                        }

                        Text(
                            text = subtitleParts.joinToString(" | "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (showProgress) {
                    val percent = file.readProgressPercent
                    if (percent != null && percent > 0) {
                        Spacer(Modifier.width(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircularProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.5.dp,
                                color = if (file.completed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = "$percent%",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun List<CatalogFile>.summary(): String {
    val withProgress = count { it.readProgressPercent != null }
    val completed = count(CatalogFile::completed)
    val fileCount = size
    return buildList {
        add("$fileCount files")
        if (withProgress > 0) add("$withProgress with progress")
        if (completed > 0) add("$completed completed")
    }.joinToString(", ")
}

private fun CatalogFile.displayTitle(): String =
    title?.takeIf { it.isNotBlank() } ?: name

private fun MangaSeriesGroup.subtitle(): String =
    lastReadFile?.let { file ->
        "Last read: ${file.displayTitle()}${file.progressSuffix()}"
    } ?: latestFile?.let { file ->
        "Latest: ${file.displayTitle()}"
    } ?: "No files"

private fun CatalogFile.progressSuffix(): String =
    progressText()?.let { " | $it" }.orEmpty()

private fun CatalogFile.progressText(): String? =
    when {
        completed -> "Completed"
        readProgressPercent != null -> "Read $readProgressPercent%"
        else -> null
    }
