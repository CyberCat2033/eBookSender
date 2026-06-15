package com.cybercat.pocketbooksender.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
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
            val event = awaitPointerEvent()
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
