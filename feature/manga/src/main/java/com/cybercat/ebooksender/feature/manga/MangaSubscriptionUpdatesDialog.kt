package com.cybercat.ebooksender.feature.manga

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cybercat.ebooksender.data.manga.MangaSubscriptionCheckResult
import com.cybercat.ebooksender.localization.LocalStrings
import com.cybercat.ebooksender.ui.AnimatedAlertDialog
import com.cybercat.ebooksender.ui.LocalDismissDialog
import com.cybercat.ebooksender.util.AppHapticFeedback
import com.cybercat.ebooksender.util.performHapticIfAllowed
import com.cybercat.ebooksender.util.pointerInputDragSelection
import com.cybercat.ebooksender.util.rememberClickSuppressionState
import com.cybercat.ebooksender.util.rememberDragSelectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaSubscriptionUpdatesDialog(
    updates: List<MangaSubscriptionCheckResult>,
    selectedChapterKeys: Set<String>,
    onToggleChapter: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    enableHaptics: Boolean
) {
    val context = LocalContext.current
    val view = LocalView.current
    val strings = LocalStrings.current
    val selectedCount = selectedChapterKeys.size
    val seriesKeys = remember(updates) {
        updates.map { update -> update.page.details.subscriptionUpdateSeriesKey() }
    }
    val updatesStateKey = remember(seriesKeys) { seriesKeys.joinToString(separator = "\n") }
    var collapsedSeriesKeys by rememberSaveable(updatesStateKey) {
        mutableStateOf<List<String>>(emptyList())
    }
    var downloadAfterDismiss by remember { mutableStateOf(false) }

    val selectedChapterKeysState = rememberUpdatedState(selectedChapterKeys)
    val onToggleChapterState = rememberUpdatedState(onToggleChapter)

    val listState = rememberLazyListState()
    var listBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val chapterRowBounds =
        remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }

    val chapterTargets = remember(updates, collapsedSeriesKeys) {
        val targets = mutableListOf<ChapterPointerTarget>()
        var index = 0
        updates.forEach { update ->
            val series = update.page.details
            val seriesKey = series.subscriptionUpdateSeriesKey()
            val collapsed = seriesKey in collapsedSeriesKeys
            if (!collapsed) {
                update.newChapters.forEach { chapter ->
                    val chapterKey = chapter.subscriptionUpdateSelectionKey()
                    targets.add(ChapterPointerTarget(index++, chapterKey))
                }
            }
        }
        targets
    }

    val clickSuppression = rememberClickSuppressionState()

    val dragSelectionState = rememberDragSelectionState(
        lazyListState = listState,
        hapticView = view,
        context = context,
        enableHaptics = enableHaptics,
        getTargetAt = { y ->
            val rootY = (listBounds?.top ?: return@rememberDragSelectionState null) + y
            val targetKey = chapterRowBounds.entries
                .firstOrNull { (_, bounds) ->
                    rootY >= bounds.top && rootY <= bounds.bottom
                }
                ?.key ?: return@rememberDragSelectionState null
            chapterTargets.firstOrNull { it.chapterId == targetKey }
        },
        getTargetIndex = { it.index },
        getTargetId = { it.chapterId },
        getInitialSelection = { selectedChapterKeysState.value },
        getAllTargets = { chapterTargets },
        onSetSelected = { key, selected -> onToggleChapterState.value(key, selected) },
        edgeSizePx = with(LocalDensity.current) { 48.dp.toPx() },
        onDragStarted = {
            clickSuppression.suppressUntilGestureEnds()
        }
    )

    fun toggleSeriesCollapsed(seriesKey: String) {
        view.performHapticIfAllowed(context, enableHaptics, AppHapticFeedback.Press)
        collapsedSeriesKeys = if (seriesKey in collapsedSeriesKeys) {
            collapsedSeriesKeys - seriesKey
        } else {
            collapsedSeriesKeys + seriesKey
        }
    }

    AnimatedAlertDialog(
        onDismissRequest = {
            if (downloadAfterDismiss) {
                downloadAfterDismiss = false
                onDownload()
            } else {
                onClose()
            }
        },
        modifier = Modifier.heightIn(max = 520.dp),
        title = {
            val dismiss = LocalDismissDialog.current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.mangaUpdatesTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        view.performHapticIfAllowed(
                            context,
                            enableHaptics,
                            AppHapticFeedback.Press
                        )
                        dismiss()
                    }
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = strings.get("action_close"))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    OutlinedButton(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                AppHapticFeedback.Press
                            )
                            onSelectAll()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            text = strings.mangaUpdatesSelectAll,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            view.performHapticIfAllowed(
                                context,
                                enableHaptics,
                                AppHapticFeedback.Press
                            )
                            onClearAll()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Text(
                            text = strings.mangaUpdatesDeselectAll,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            listBounds = coordinates.boundsInRoot()
                        }
                        .pointerInputDragSelection(
                            dragSelectionState,
                            clickSuppression,
                            true,
                            chapterTargets
                        ),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    updates.forEach { update ->
                        val series = update.page.details
                        val seriesKey = series.subscriptionUpdateSeriesKey()
                        val collapsed = seriesKey in collapsedSeriesKeys

                        item(key = seriesKey) {
                            val seriesInteractionSource =
                                remember(seriesKey) { MutableInteractionSource() }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = seriesInteractionSource,
                                            indication = null
                                        ) {
                                            toggleSeriesCollapsed(seriesKey)
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    MangaCover(
                                        coverUrl = series.coverUrl,
                                        title = series.title,
                                        modifier = Modifier.size(40.dp, 60.dp)
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = series.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val rotationState by animateFloatAsState(
                                        targetValue = if (collapsed) 0f else 180f,
                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                        label = "SubscriptionUpdateSeriesChevronRotation"
                                    )
                                    Icon(
                                        imageVector = Icons.Outlined.ExpandMore,
                                        contentDescription = if (collapsed) {
                                            strings.catalogActionExpand
                                        } else {
                                            strings.catalogActionCollapse
                                        },
                                        modifier = Modifier.rotate(rotationState)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = !collapsed,
                                    enter = expandVertically(
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        expandFrom = Alignment.Top
                                    ) + fadeIn(
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    ),
                                    exit = shrinkVertically(
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        shrinkTowards = Alignment.Top
                                    ) + fadeOut(
                                        animationSpec = spring(
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        update.newChapters.forEach { chapter ->
                                            val chapterKey =
                                                chapter.subscriptionUpdateSelectionKey()
                                            val isSelected = chapterKey in selectedChapterKeys

                                            DisposableEffect(chapterKey) {
                                                onDispose {
                                                    chapterRowBounds.remove(chapterKey)
                                                }
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned { coordinates ->
                                                        chapterRowBounds[chapterKey] =
                                                            coordinates.boundsInRoot()
                                                    }
                                                    .clickable {
                                                        if (clickSuppression.isSuppressed()) {
                                                            return@clickable
                                                        }
                                                        view.performHapticIfAllowed(
                                                            context,
                                                            enableHaptics,
                                                            AppHapticFeedback.Press
                                                        )
                                                        onToggleChapter(chapterKey, !isSelected)
                                                    }
                                                    .padding(
                                                        start = 12.dp,
                                                        top = 6.dp,
                                                        bottom = 6.dp
                                                    )
                                            ) {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        if (clickSuppression.isSuppressed()) {
                                                            return@Checkbox
                                                        }
                                                        view.performHapticIfAllowed(
                                                            context,
                                                            enableHaptics,
                                                            AppHapticFeedback.Press
                                                        )
                                                        onToggleChapter(chapterKey, checked)
                                                    }
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    text = chapter.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            val dismiss = LocalDismissDialog.current
            TextButton(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Press
                    )
                    downloadAfterDismiss = false
                    dismiss()
                }
            ) {
                Text(strings.mangaUpdatesBtnCancel)
            }
        },
        confirmButton = {
            val dismiss = LocalDismissDialog.current
            Button(
                onClick = {
                    view.performHapticIfAllowed(
                        context,
                        enableHaptics,
                        AppHapticFeedback.Confirm
                    )
                    downloadAfterDismiss = true
                    dismiss()
                },
                enabled = selectedCount > 0
            ) {
                Text(strings.get("manga_updates_btn_download", selectedCount))
            }
        }
    )
}
