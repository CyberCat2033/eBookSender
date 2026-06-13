package com.cybercat.pocketbooksender.ui.screens

import android.net.Uri
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.pocketbooksender.model.BookCategory
import com.cybercat.pocketbooksender.model.UploadItem
import com.cybercat.pocketbooksender.model.UploadStatus
import com.cybercat.pocketbooksender.ui.SenderUiState
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    state: SenderUiState,
    onFtpInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onQrScanned: (String) -> Unit,
    onDisconnect: () -> Unit,
    onAddUris: (List<Uri>) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearQueue: () -> Unit,
    onCategoryChanged: (String, BookCategory) -> Unit,
    onProgrammingTagChanged: (String, String) -> Unit,
    onMangaSeriesChanged: (String, String) -> Unit,
    onQueuedMangaSeriesChanged: (String) -> Unit,
    onUploadAll: () -> Unit,
) {
    val activeQueue = if (state.isTransferActive) {
        state.queue
    } else {
        state.queue.filterNot { it.status == UploadStatus.Uploaded }
    }
    val activeRows = activeQueue.withStableLazyKeys()
    val activeMangaQueue = activeQueue.filter { it.category == BookCategory.Manga }
    val uploadedQueue = if (state.isTransferActive) {
        emptyList()
    } else {
        state.queue.filter { it.status == UploadStatus.Uploaded }
    }
    val hasUploadableFiles = state.queue.any {
        it.status == UploadStatus.Pending ||
            it.status == UploadStatus.Failed ||
            it.status == UploadStatus.Skipped
    }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = onAddUris,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PocketBook Sender") },
                windowInsets = WindowInsets(0.dp),
                actions = {
                    if (state.queue.isNotEmpty()) {
                        IconButton(onClick = onClearQueue) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Clear queue")
                        }
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
            item {
                ConnectionPanel(
                    state = state,
                    onFtpInputChanged = onFtpInputChanged,
                    onConnect = onConnect,
                    onQrScanned = onQrScanned,
                    onDisconnect = onDisconnect,
                )
            }

            item {
                ActionRow(
                    canUpload = state.isConnected && hasUploadableFiles && !state.isTransferActive,
                    onAddFiles = { picker.launch(arrayOf("*/*")) },
                    onUploadAll = onUploadAll,
                )
            }

            if (state.isTransferActive) {
                item {
                    OverallProgressPanel(queue = state.queue)
                }
            }

            if (activeMangaQueue.size > 1) {
                item {
                    MangaBatchEditor(
                        count = activeMangaQueue.size,
                        currentSeries = activeMangaQueue.commonMangaSeries(),
                        suggestions = state.mangaSeriesSuggestions,
                        onApply = onQueuedMangaSeriesChanged,
                    )
                }
            }

            if (uploadedQueue.isNotEmpty()) {
                item(key = "uploaded-section") {
                    UploadedSection(
                        items = uploadedQueue,
                        modifier = Modifier.animateItem(
                            fadeInSpec = QueueFadeInSpec,
                            fadeOutSpec = QueueFadeOutSpec,
                            placementSpec = QueuePlacementSpec
                        ),
                    )
                }
            }

            if (state.errorMessage != null) {
                item {
                    Text(
                        text = state.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                QueueHeader(count = activeQueue.size)
            }

            if (activeQueue.isEmpty()) {
                item {
                    EmptyQueue()
                }
            } else {
                itemsIndexed(
                    items = activeRows,
                    key = { _, row -> row.key },
                    contentType = { _, _ -> "upload_item" }
                ) { _, row ->
                    val item = row.item
                    UploadItemRow(
                        item = item,
                        modifier = Modifier.animateItem(
                            fadeInSpec = QueueFadeInSpec,
                            fadeOutSpec = QueueFadeOutSpec,
                            placementSpec = QueuePlacementSpec
                        ),
                        programmingTags = state.programmingTags,
                        mangaSeriesSuggestions = state.mangaSeriesSuggestions,
                        onRemove = { onRemoveItem(item.id) },
                        onCategoryChanged = { category -> onCategoryChanged(item.id, category) },
                        onProgrammingTagChanged = { tag -> onProgrammingTagChanged(item.id, tag) },
                        onMangaSeriesChanged = { series -> onMangaSeriesChanged(item.id, series) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ConnectionPanel(
    state: SenderUiState,
    onFtpInputChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onQrScanned: (String) -> Unit,
    onDisconnect: () -> Unit,
) {
    val context = LocalContext.current

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.isConnected) Icons.Outlined.CheckCircle else Icons.Outlined.WifiTethering,
                    contentDescription = null,
                    tint = if (state.isConnected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            state.isConnected -> "PocketBook connected"
                            state.isConnecting -> "Checking FTP..."
                            else -> "Connect PocketBook"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = state.connectedDevice?.ftpUrl ?: "Scan QR or paste ftp://anonymous@host:2121/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.isConnected) {
                    IconButton(onClick = onDisconnect) {
                        Icon(Icons.Outlined.Close, contentDescription = "Disconnect")
                    }
                }
            }

            if (!state.isConnected) {
                OutlinedTextField(
                    value = state.ftpInput,
                    onValueChange = onFtpInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("FTP link or IP") },
                    leadingIcon = {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                    },
                    placeholder = { Text("ftp://anonymous@192.168.1.37:2121/") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            startQrScan(context, onQrScanned)
                        },
                        enabled = !state.isConnecting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan QR")
                    }
                    Button(
                        onClick = {
                            if (state.ftpInput.isBlank()) {
                                startQrScan(context, onQrScanned)
                            } else {
                                onConnect()
                            }
                        },
                        enabled = !state.isConnecting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.WifiTethering, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isConnecting) "Checking" else "Connect")
                    }
                }
            }
        }
    }
}

private data class QueueRow(
    val key: String,
    val item: UploadItem,
)

private fun List<UploadItem>.withStableLazyKeys(): List<QueueRow> {
    val seen = mutableMapOf<String, Int>()
    return mapIndexed { index, item ->
        val baseKey = "queue:${item.id}:${item.sourceUri}"
        val duplicateIndex = seen.getOrDefault(baseKey, 0)
        seen[baseKey] = duplicateIndex + 1
        QueueRow(
            key = if (duplicateIndex == 0) {
                baseKey
            } else {
                "$baseKey:duplicate:$duplicateIndex:$index"
            },
            item = item,
        )
    }
}

private fun startQrScan(
    context: Context,
    onQrScanned: (String) -> Unit,
) {
    GmsBarcodeScanning.getClient(context)
        .startScan()
        .addOnSuccessListener { barcode ->
            barcode.rawValue?.let(onQrScanned)
        }
}

@Composable
private fun ActionRow(
    canUpload: Boolean,
    onAddFiles: () -> Unit,
    onUploadAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onAddFiles,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add files")
        }
        OutlinedButton(
            onClick = onUploadAll,
            enabled = canUpload,
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Outlined.Upload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Upload")
        }
    }
}

@Composable
private fun MangaBatchEditor(
    count: Int,
    currentSeries: String,
    suggestions: List<String>,
    onApply: (String) -> Unit,
) {
    var series by remember(currentSeries) { mutableStateOf(currentSeries) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Manga batch",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "$count files",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = series,
                onValueChange = { series = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Series for all manga files") },
            )

            Button(
                onClick = { onApply(series) },
                enabled = series.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apply to $count files")
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions
                    .filter { it.isNotBlank() }
                    .distinctBy { it.lowercase() }
                    .forEach { suggestion ->
                        FilterChip(
                            selected = series.equals(suggestion, ignoreCase = true),
                            onClick = {
                                series = suggestion
                                onApply(suggestion)
                            },
                            label = { Text(suggestion) },
                        )
                    }
            }
        }
    }
}

@Composable
private fun QueueHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Queue",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = count.toString(),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyQueue() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "No files selected",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Add books from Android storage or share them into the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UploadItemRow(
    item: UploadItem,
    modifier: Modifier = Modifier,
    programmingTags: List<String>,
    mangaSeriesSuggestions: List<String>,
    onRemove: () -> Unit,
    onCategoryChanged: (BookCategory) -> Unit,
    onProgrammingTagChanged: (String) -> Unit,
    onMangaSeriesChanged: (String) -> Unit,
) {
    var detailsExpanded by remember(item.id) { mutableStateOf(false) }

    ElevatedCard(modifier.fillMaxWidth()) {
        Column {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BookCover(item)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.originalName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = onRemove,
                        enabled = item.status != UploadStatus.Uploading && item.status != UploadStatus.Uploaded,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove")
                    }
                }

                ItemTypeSummary(
                    item = item,
                    expanded = detailsExpanded,
                    onToggle = { detailsExpanded = !detailsExpanded },
                )

                AnimatedVisibility(
                    visible = detailsExpanded,
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CategorySelector(
                            selected = item.category,
                            lockedToManga = item.extension == "cbr" || item.extension == "cbz",
                            onCategoryChanged = onCategoryChanged,
                        )

                        if (item.category == BookCategory.Programming) {
                            ProgrammingTagEditor(
                                selectedTag = item.programmingTag.orEmpty(),
                                suggestions = programmingTags,
                                onTagChanged = onProgrammingTagChanged,
                            )
                        }

                        if (item.category == BookCategory.Manga) {
                            MangaSeriesEditor(
                                selectedSeries = item.mangaSeries.orEmpty(),
                                suggestions = mangaSeriesSuggestions,
                                onSeriesChanged = onMangaSeriesChanged,
                            )
                        }
                    }
                }

                HorizontalDivider()

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
                        UploadStatus.Uploading -> "Uploading (${(item.progress * 100).toInt()}%)"
                        else -> item.status.name
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }

            val isUploading = item.status == UploadStatus.Uploading
            val progressHeight by animateDpAsState(
                targetValue = if (isUploading) 4.dp else 0.dp,
                label = "ProgressHeight"
            )

            if (progressHeight > 0.dp) {
                SmoothProgressIndicator(
                    progress = item.progress,
                    modifier = Modifier.fillMaxWidth().height(progressHeight),
                )
            }
        }
    }
}

@Composable
private fun SmoothProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SmoothProgress"
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier,
    )
}

@Composable
private fun UploadedSection(
    items: List<UploadItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Uploaded",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${items.size} books",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val rotationState by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "ChevronRotation"
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Collapse uploaded" else "Show uploaded",
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = item.plannedPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemTypeSummary(
    item: UploadItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = item.category.iconFor(item.extension),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.category.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                item.typeDetail()?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val rotationState by animateFloatAsState(
                targetValue = if (expanded) 180f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "ChevronRotation"
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Hide item details" else "Edit item details",
                    modifier = Modifier.rotate(rotationState)
                )
            }
        }
    }
}

@Composable
private fun ProgrammingTagEditor(
    selectedTag: String,
    suggestions: List<String>,
    onTagChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = selectedTag,
            onValueChange = onTagChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Programming tag") },
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .forEach { suggestion ->
                    FilterChip(
                        selected = selectedTag.equals(suggestion, ignoreCase = true),
                        onClick = { onTagChanged(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
        }
    }
}

@Composable
private fun MangaSeriesEditor(
    selectedSeries: String,
    suggestions: List<String>,
    onSeriesChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = selectedSeries,
            onValueChange = onSeriesChanged,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Manga series") },
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
                .forEach { suggestion ->
                    FilterChip(
                        selected = selectedSeries.equals(suggestion, ignoreCase = true),
                        onClick = { onSeriesChanged(suggestion) },
                        label = { Text(suggestion) },
                    )
                }
        }
    }
}

@Composable
private fun BookCover(item: UploadItem) {
    Surface(
        modifier = Modifier.size(width = 58.dp, height = 78.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        val preview = item.preview
        if (preview != null) {
            ComposeImage(
                bitmap = preview.asImageBitmap(),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = item.category.iconFor(item.extension),
                contentDescription = null,
                modifier = Modifier.padding(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CategorySelector(
    selected: BookCategory,
    lockedToManga: Boolean,
    onCategoryChanged: (BookCategory) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BookCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                enabled = !lockedToManga || category == BookCategory.Manga,
                onClick = { onCategoryChanged(category) },
                label = { Text(category.label()) },
                leadingIcon = {
                    Icon(
                        imageVector = category.iconFor(""),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
            )
        }
    }
}

private fun BookCategory.label(): String = when (this) {
    BookCategory.Books -> "Books"
    BookCategory.Programming -> "Programming"
    BookCategory.Manga -> "Manga"
}

private fun UploadItem.typeDetail(): String? = when (category) {
    BookCategory.Books -> author?.takeIf { it.isNotBlank() }
    BookCategory.Programming -> programmingTag?.takeIf { it.isNotBlank() }
    BookCategory.Manga -> mangaSeries?.takeIf { it.isNotBlank() }
}

private fun BookCategory.iconFor(extension: String): ImageVector = when {
    this == BookCategory.Manga -> Icons.Outlined.Image
    extension == "pdf" -> Icons.Outlined.PictureAsPdf
    this == BookCategory.Programming -> Icons.Outlined.Code
    else -> Icons.AutoMirrored.Outlined.MenuBook
}

private fun List<UploadItem>.commonMangaSeries(): String {
    val series = mapNotNull { item -> item.mangaSeries?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
    return if (series.size == 1) series.first() else ""
}

@Composable
private fun OverallProgressPanel(
    queue: List<UploadItem>,
    modifier: Modifier = Modifier,
) {
    val uploadingItems = queue.filter { it.status == UploadStatus.Uploading }
    val uploadedCount = queue.count { it.status == UploadStatus.Uploaded }
    val totalCount = queue.size

    if (totalCount == 0) return

    val activeProgressSum = uploadingItems.sumOf { it.progress.toDouble() }.toFloat()
    val overallProgress = (uploadedCount + activeProgressSum) / totalCount

    val animatedProgress by animateFloatAsState(
        targetValue = overallProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "OverallProgress"
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uploadedCount == totalCount) "Upload complete" else "Uploading books...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${(overallProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Uploaded $uploadedCount of $totalCount files",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (uploadingItems.isNotEmpty()) {
                    Text(
                        text = uploadingItems.first().title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private val QueueFadeInSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow
)

private val QueueFadeOutSpec = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

private val QueuePlacementSpec = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMediumLow
)
