package com.cybercat.pocketbooksender.util

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Custom gesture detector that detects a quick long press followed by dragging.
 * Used for initiating drag-selection.
 */
suspend fun PointerInputScope.detectDragGesturesAfterQuickLongPress(
    onDragStart: (Offset) -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var currentChange = down
        val touchSlop = viewConfiguration.touchSlop

        val longPressReached: Boolean = withTimeoutOrNull<Boolean>(300L) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull false
                if (!change.pressed || change.isConsumed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > touchSlop) {
                    return@withTimeoutOrNull false
                }
                currentChange = change
            }
            true
        } ?: true

        if (!longPressReached) return@awaitEachGesture

        onDragStart(currentChange.position)
        currentChange.consume()

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onDragCancel()
                break
            }
            if (!change.pressed) {
                change.consume()
                onDragEnd()
                break
            }

            val dragAmount = change.positionChange()
            if (dragAmount != Offset.Zero) {
                onDrag(change, dragAmount)
                change.consume()
            }
        }
    }
}

/**
 * Calculates auto-scroll delta based on the current drag position, viewport height, and edge size.
 */
fun calculateAutoScrollDelta(
    currentY: Float,
    viewportHeight: Float,
    edgeSizePx: Float,
    maxSpeed: Float = 120f,
    minSpeed: Float = 5f,
): Float {
    if (viewportHeight <= 0f) return 0f

    return when {
        currentY < edgeSizePx -> {
            val distance = edgeSizePx - currentY
            val ratio = distance / edgeSizePx
            val speed = (maxSpeed * ratio * ratio).coerceIn(minSpeed, maxSpeed)
            -speed
        }
        currentY > viewportHeight - edgeSizePx -> {
            val distance = currentY - (viewportHeight - edgeSizePx)
            val ratio = distance / edgeSizePx
            val speed = (maxSpeed * ratio * ratio).coerceIn(minSpeed, maxSpeed)
            speed
        }
        else -> 0f
    }
}

@Stable
class DragSelectionState<T>(
    private val scope: CoroutineScope,
    private val lazyListState: LazyListState,
    private val hapticView: View,
    private val context: Context,
    private val enableHaptics: Boolean,
    private val getTargetAt: (Float) -> T?,
    private val getTargetIndex: (T) -> Int,
    private val getTargetId: (T) -> String,
    private val getInitialSelection: () -> Set<String>,
    private val getAllTargets: () -> List<T>,
    private val onSetSelected: (String, Boolean) -> Unit,
    private val edgeSizePx: Float,
    private val onDragStarted: () -> Unit = {}
) {
    var selectionActive by mutableStateOf(false)
        private set

    private var currentY by mutableFloatStateOf(0f)
    private var anchorIndex: Int? = null
    private var targetSelected by mutableStateOf(true)
    private var autoScrollJob: Job? = null
    private val appliedSelection = mutableMapOf<String, Boolean>()
    private var baselineSelection = emptySet<String>()

    fun startDrag(offsetY: Float) {
        currentY = offsetY
        val target = getTargetAt(currentY)
        if (target == null) {
            stopDrag()
        } else {
            selectionActive = true
            baselineSelection = getInitialSelection()
            val targetId = getTargetId(target)
            targetSelected = targetId !in baselineSelection
            anchorIndex = getTargetIndex(target)
            appliedSelection.clear()
            onDragStarted()
            hapticView.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.LONG_PRESS, ignoreDnd = true)
            applySelectionAt(currentY)
            startAutoScroll()
        }
    }

    fun drag(offsetY: Float) {
        currentY = offsetY
        if (selectionActive) {
            applySelectionAt(currentY)
        }
    }

    fun stopDrag() {
        selectionActive = false
        autoScrollJob?.cancel()
        autoScrollJob = null
        anchorIndex = null
        baselineSelection = emptySet()
        appliedSelection.clear()
    }

    private fun applySelectionAt(y: Float) {
        val target = getTargetAt(y) ?: return
        val anchor = anchorIndex ?: getTargetIndex(target)
        val targetIdx = getTargetIndex(target)
        val startIndex = anchor.coerceAtMost(targetIdx)
        val endIndex = anchor.coerceAtLeast(targetIdx)

        getAllTargets().forEach { item ->
            val idx = getTargetIndex(item)
            val id = getTargetId(item)
            val desiredSelected = if (idx in startIndex..endIndex) {
                targetSelected
            } else {
                id in baselineSelection
            }
            val currentSelected = appliedSelection[id] ?: (id in baselineSelection)
            if (currentSelected != desiredSelected) {
                appliedSelection[id] = desiredSelected
                onSetSelected(id, desiredSelected)
                hapticView.performHapticIfAllowed(context, enableHaptics, HapticFeedbackConstants.CLOCK_TICK, ignoreDnd = true)
            }
        }
    }

    private fun autoScrollDelta(): Float {
        val layoutHeight = lazyListState.layoutInfo.viewportSize.height.toFloat()
        return calculateAutoScrollDelta(
            currentY = currentY,
            viewportHeight = layoutHeight,
            edgeSizePx = edgeSizePx
        )
    }

    private fun startAutoScroll() {
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch {
            while (isActive) {
                val delta = autoScrollDelta()
                if (delta != 0f) {
                    lazyListState.scrollBy(delta)
                    applySelectionAt(currentY)
                }
                delay(16L)
            }
        }
    }
}

@Composable
fun <T> rememberDragSelectionState(
    lazyListState: LazyListState,
    hapticView: View,
    context: Context,
    enableHaptics: Boolean,
    getTargetAt: (Float) -> T?,
    getTargetIndex: (T) -> Int,
    getTargetId: (T) -> String,
    getInitialSelection: () -> Set<String>,
    getAllTargets: () -> List<T>,
    onSetSelected: (String, Boolean) -> Unit,
    edgeSizePx: Float,
    onDragStarted: () -> Unit = {}
): DragSelectionState<T> {
    val scope = rememberCoroutineScope()
    return remember(lazyListState, hapticView, context, enableHaptics, getTargetAt, getTargetIndex, getTargetId, getInitialSelection, getAllTargets, onSetSelected, edgeSizePx) {
        DragSelectionState(
            scope = scope,
            lazyListState = lazyListState,
            hapticView = hapticView,
            context = context,
            enableHaptics = enableHaptics,
            getTargetAt = getTargetAt,
            getTargetIndex = getTargetIndex,
            getTargetId = getTargetId,
            getInitialSelection = getInitialSelection,
            getAllTargets = getAllTargets,
            onSetSelected = onSetSelected,
            edgeSizePx = edgeSizePx,
            onDragStarted = onDragStarted
        )
    }
}

@Stable
class ClickSuppressionState {
    private var suppressUntilMillis by mutableStateOf(0L)

    fun suppress(durationMillis: Long = 250L) {
        suppressUntilMillis = SystemClock.uptimeMillis() + durationMillis
    }

    fun suppressUntilGestureEnds() {
        suppressUntilMillis = Long.MAX_VALUE
    }

    fun isSuppressed(): Boolean = SystemClock.uptimeMillis() < suppressUntilMillis
}

@Composable
fun rememberClickSuppressionState(): ClickSuppressionState {
    return remember { ClickSuppressionState() }
}

fun Modifier.pointerInputDragSelection(
    dragSelectionState: DragSelectionState<*>,
    clickSuppressionState: ClickSuppressionState? = null,
    enabled: Boolean = true,
    vararg keys: Any?,
): Modifier = this.pointerInput(keys = keys) {
    if (!enabled) return@pointerInput
    detectDragGesturesAfterQuickLongPress(
        onDragStart = { offset -> dragSelectionState.startDrag(offset.y) },
        onDrag = { change, _ ->
            change.consume()
            dragSelectionState.drag(change.position.y)
        },
        onDragEnd = {
            clickSuppressionState?.suppress()
            dragSelectionState.stopDrag()
        },
        onDragCancel = {
            clickSuppressionState?.suppress()
            dragSelectionState.stopDrag()
        }
    )
}
