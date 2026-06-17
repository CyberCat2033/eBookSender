package com.cybercat.pocketbooksender.feature.catalog

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.cybercat.pocketbooksender.localization.LocalStrings
import com.cybercat.pocketbooksender.model.CatalogFile
import com.cybercat.pocketbooksender.model.CatalogGroup
import com.cybercat.pocketbooksender.model.MangaSeriesGroup
import com.cybercat.pocketbooksender.ui.theme.EmphasizedEasing
import com.cybercat.pocketbooksender.util.performHapticIfAllowed
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun CatalogMessage(
    title: String,
    text: String,
    isError: Boolean = false,
    isLoading: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .zIndex(CatalogSectionTitleZIndex)
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SelectionSlot(visible: Boolean, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val width by animateDpAsState(
        targetValue = if (visible) SelectionSlotWidth else 0.dp,
        animationSpec = tween(
            durationMillis = SelectionMotionDurationMillis,
            easing = EmphasizedEasing
        ),
        label = "SelectionSlotWidth"
    )
    val checkboxAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(
            durationMillis = SelectionMotionDurationMillis,
            easing = EmphasizedEasing
        ),
        label = "SelectionCheckboxAlpha"
    )
    val checkboxScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.86f,
        animationSpec = tween(
            durationMillis = SelectionMotionDurationMillis,
            easing = EmphasizedEasing
        ),
        label = "SelectionCheckboxScale"
    )

    Box(
        modifier = Modifier
            .width(width)
            .height(SelectionControlSize)
            .clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        CompactCheckbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.graphicsLayer {
                alpha = checkboxAlpha
                scaleX = checkboxScale
                scaleY = checkboxScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            }
        )
    }
}

@Composable
internal fun CompactCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.size(SelectionControlSize)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CatalogGroupCard(
    group: CatalogGroup,
    expanded: Boolean,
    isEditMode: Boolean,
    selectedFilePaths: Set<String>,
    enableHaptics: Boolean,
    onToggleFileSelection: (String) -> Unit,
    onToggleGroupSelection: (List<String>, Boolean) -> Unit,
    onEnterEditMode: () -> Unit,
    selectionClickSuppressed: () -> Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFileBoundsChanged: (String, androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filePaths = remember(group.files) { group.files.map { it.path } }
    val isGroupFullySelected = remember(selectedFilePaths, filePaths) {
        filePaths.isNotEmpty() && filePaths.all { it in selectedFilePaths }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    fun toggleGroup(checked: Boolean = !isGroupFullySelected) {
        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
        onToggleGroupSelection(filePaths, checked)
    }

    fun selectGroupFromLongPress() {
        if (filePaths.isEmpty() || selectionClickSuppressed()) return
        view.performHapticIfAllowed(
            context,
            enableHaptics,
            HapticFeedbackConstants.LONG_PRESS,
            ignoreDnd = true
        )
        if (!isEditMode) {
            onEnterEditMode()
        }
        onToggleGroupSelection(filePaths, true)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGroupFullySelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionSlot(
                    visible = isEditMode,
                    checked = isGroupFullySelected,
                    onCheckedChange = ::toggleGroup
                )
                ExpandableHeader(
                    title = group.name,
                    subtitle = group.files.summary(strings),
                    expanded = expanded,
                    enableHaptics = enableHaptics,
                    onToggle = { onExpandedChange(!expanded) },
                    titleClickEnabled = isEditMode,
                    onTitleClick = {
                        if (!selectionClickSuppressed()) {
                            toggleGroup()
                        }
                    },
                    onTitleLongClick = ::selectGroupFromLongPress,
                    modifier = Modifier.weight(1f)
                )
            }
            val animationDuration = remember(group.files.size) {
                minOf(750, 250 + (group.files.size * 35))
            }
            AnimatedVisibility(
                visible = expanded,
                enter =
                    expandVertically(animationSpec = tween(durationMillis = animationDuration)) +
                        fadeIn(animationSpec = tween(durationMillis = animationDuration)),
                exit =
                    shrinkVertically(animationSpec = tween(durationMillis = animationDuration)) +
                        fadeOut(animationSpec = tween(durationMillis = animationDuration))
            ) {
                FileList(
                    files = group.files,
                    isEditMode = isEditMode,
                    selectedFilePaths = selectedFilePaths,
                    enableHaptics = enableHaptics,
                    onToggleFileSelection = onToggleFileSelection,
                    selectionClickSuppressed = selectionClickSuppressed,
                    onFileBoundsChanged = onFileBoundsChanged
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MangaSeriesCard(
    group: MangaSeriesGroup,
    expanded: Boolean,
    isEditMode: Boolean,
    selectedFilePaths: Set<String>,
    enableHaptics: Boolean,
    onToggleFileSelection: (String) -> Unit,
    onToggleGroupSelection: (List<String>, Boolean) -> Unit,
    onEnterEditMode: () -> Unit,
    selectionClickSuppressed: () -> Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onFileBoundsChanged: (String, androidx.compose.ui.geometry.Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filePaths = remember(group.files) { group.files.map { it.path } }
    val isGroupFullySelected = remember(selectedFilePaths, filePaths) {
        filePaths.isNotEmpty() && filePaths.all { it in selectedFilePaths }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    fun toggleGroup(checked: Boolean = !isGroupFullySelected) {
        view.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.VIRTUAL_KEY)
        onToggleGroupSelection(filePaths, checked)
    }

    fun selectGroupFromLongPress() {
        if (filePaths.isEmpty() || selectionClickSuppressed()) return
        view.performHapticIfAllowed(
            context,
            enableHaptics,
            HapticFeedbackConstants.LONG_PRESS,
            ignoreDnd = true
        )
        if (!isEditMode) {
            onEnterEditMode()
        }
        onToggleGroupSelection(filePaths, true)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGroupFullySelected) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionSlot(
                    visible = isEditMode,
                    checked = isGroupFullySelected,
                    onCheckedChange = ::toggleGroup
                )
                ExpandableHeader(
                    title = group.name,
                    subtitle = group.latestFile?.let { file ->
                        strings.get(
                            "catalog_label_latest",
                            "${file.mangaDisplayTitle()}${file.progressSuffix(strings)}"
                        )
                    } ?: strings.catalogNoFiles,
                    subtitleMaxLines = 3,
                    expanded = expanded,
                    enableHaptics = enableHaptics,
                    onToggle = { onExpandedChange(!expanded) },
                    titleClickEnabled = isEditMode,
                    onTitleClick = {
                        if (!selectionClickSuppressed()) {
                            toggleGroup()
                        }
                    },
                    onTitleLongClick = ::selectGroupFromLongPress,
                    modifier = Modifier.weight(1f)
                )
            }
            val animationDuration = remember(group.files.size) {
                minOf(750, 250 + (group.files.size * 35))
            }
            AnimatedVisibility(
                visible = expanded,
                enter =
                    expandVertically(animationSpec = tween(durationMillis = animationDuration)) +
                        fadeIn(animationSpec = tween(durationMillis = animationDuration)),
                exit =
                    shrinkVertically(animationSpec = tween(durationMillis = animationDuration)) +
                        fadeOut(animationSpec = tween(durationMillis = animationDuration))
            ) {
                FileList(
                    files = group.files,
                    showProgress = false,
                    isEditMode = isEditMode,
                    selectedFilePaths = selectedFilePaths,
                    enableHaptics = enableHaptics,
                    onToggleFileSelection = onToggleFileSelection,
                    selectionClickSuppressed = selectionClickSuppressed,
                    onFileBoundsChanged = onFileBoundsChanged
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExpandableHeader(
    title: String,
    subtitle: String,
    subtitleMaxLines: Int = 1,
    expanded: Boolean,
    enableHaptics: Boolean,
    onToggle: () -> Unit,
    titleClickEnabled: Boolean = false,
    onTitleClick: () -> Unit = {},
    onTitleLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val titleInteractionSource = remember { MutableInteractionSource() }
    val titleInteractionModifier = if (titleClickEnabled) {
        Modifier.combinedClickable(
            interactionSource = titleInteractionSource,
            indication = null,
            onClick = onTitleClick,
            onLongClick = onTitleLongClick
        )
    } else {
        Modifier.pointerInput(onTitleLongClick) {
            detectTapGestures(
                onLongPress = {
                    onTitleLongClick()
                }
            )
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .weight(1f)
                .then(titleInteractionModifier)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis
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
                contentDescription = if (expanded) {
                    strings.catalogActionCollapse
                } else {
                    strings.catalogActionExpand
                },
                modifier = Modifier.rotate(rotationState)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileList(
    files: List<CatalogFile>,
    showProgress: Boolean = true,
    isEditMode: Boolean = false,
    selectedFilePaths: Set<String> = emptySet(),
    enableHaptics: Boolean = false,
    onToggleFileSelection: (String) -> Unit = {},
    selectionClickSuppressed: () -> Boolean = { false },
    onFileBoundsChanged: (String, androidx.compose.ui.geometry.Rect?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current

    var currentFiles by remember { mutableStateOf(files) }
    val deletedPaths = remember { mutableStateListOf<String>() }

    LaunchedEffect(files) {
        val newPaths = files.map { it.path }.toSet()
        val removed = currentFiles.filter { it.path !in newPaths && it.path !in deletedPaths }
        if (removed.isNotEmpty()) {
            deletedPaths.addAll(removed.map { it.path })
            currentFiles = currentFiles.map { current ->
                files.firstOrNull { it.path == current.path } ?: current
            }
        } else {
            deletedPaths.clear()
            currentFiles = files
        }
    }

    Column(
        modifier = Modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        currentFiles.forEach { file ->
            val isExiting = file.path in deletedPaths
            val isSelected = file.path in selectedFilePaths
            val fileInteractionSource = remember(file.path) { MutableInteractionSource() }

            DisposableEffect(file.path) {
                onDispose {
                    onFileBoundsChanged(file.path, null)
                }
            }

            AnimatedVisibility(
                visible = !isExiting,
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Top,
                    animationSpec = tween(
                        durationMillis = RemovalMotionDurationMillis,
                        easing = EmphasizedEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = RemovalMotionDurationMillis,
                        easing = EmphasizedEasing
                    )
                ),
                label = "FileRemoval"
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            onFileBoundsChanged(file.path, coordinates.boundsInRoot())
                        }
                        .clickable(
                            enabled = isEditMode,
                            interactionSource = fileInteractionSource,
                            indication = null
                        ) {
                            if (selectionClickSuppressed()) {
                                return@clickable
                            }
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                HapticFeedbackConstants.VIRTUAL_KEY
                            )
                            onToggleFileSelection(file.path)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SelectionSlot(
                        visible = isEditMode,
                        checked = isSelected,
                        onCheckedChange = {
                            if (!selectionClickSuppressed()) {
                                view.performHapticIfAllowed(
                                    context,
                                    enableHaptics,
                                    HapticFeedbackConstants.VIRTUAL_KEY
                                )
                                onToggleFileSelection(file.path)
                            }
                        }
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (showProgress) {
                                file.displayTitle()
                            } else {
                                file.mangaDisplayTitle()
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (showProgress) {
                            val currentPage = file.currentPage
                            val totalPages = file.totalPages
                            val progressDetailText = when {
                                file.completed -> strings.catalogStatusCompleted

                                currentPage != null && currentPage > 0 -> {
                                    if (totalPages != null && totalPages > 0) {
                                        strings.get("catalog_page_ratio", currentPage, totalPages)
                                    } else {
                                        strings.get("catalog_page_current", currentPage)
                                    }
                                }

                                else -> strings.catalogStatusNotStarted
                            }

                            val relativeTime = file.lastOpenedAtMillis?.let { time ->
                                DateUtils.getRelativeTimeSpanString(
                                    time,
                                    System.currentTimeMillis(),
                                    DateUtils.MINUTE_IN_MILLIS,
                                    DateUtils.FORMAT_ABBREV_RELATIVE
                                ).toString()
                            }
                            val lastReadText = relativeTime?.let { relative ->
                                strings.get("catalog_label_last_read", relative)
                            }

                            val subtitleParts = buildList {
                                val series = file.series
                                if (!series.isNullOrBlank()) {
                                    add(strings.get("catalog_label_series", series))
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
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showProgress) {
                        val percent = file.readProgressPercent
                        if (percent != null && percent > 0) {
                            Spacer(Modifier.width(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CatalogProgressIndicator(
                                    file = file,
                                    percent = percent
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
            LaunchedEffect(isExiting) {
                if (isExiting) {
                    delay(RemovalMotionDurationMillis.toLong())
                    currentFiles = currentFiles.filter { it.path != file.path }
                    deletedPaths.remove(file.path)
                }
            }
        }
    }
}

@Composable
private fun CatalogProgressIndicator(file: CatalogFile, percent: Int) {
    val completed = file.completed || percent >= 100
    val indicatorColor = if (completed) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier.size(18.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = Modifier.matchParentSize(),
            strokeWidth = 2.5.dp,
            color = indicatorColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        if (completed) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = indicatorColor
            )
        }
    }
}
