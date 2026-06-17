package com.cybercat.pocketbooksender.feature.settings

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import com.cybercat.pocketbooksender.ui.theme.EmphasizedEasing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val FOCUSED_FIELD_SCROLL_DURATION_MILLIS = 260
private const val FOCUSED_FIELD_SCROLL_CORRECTION_DELAY_MILLIS = 90L
private const val FOCUSED_FIELD_SCROLL_CORRECTION_DURATION_MILLIS = 160
private const val FOCUSED_FIELD_SCROLL_THRESHOLD_PX = 2

internal val LocalSettingsFocusedFieldScroller =
    staticCompositionLocalOf<(LayoutCoordinates) -> Unit> { {} }

@Composable
internal fun SettingsFocusedFieldScrollHost(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollJob = remember { arrayOfNulls<Job>(1) }
    val viewportCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }

    val scrollFocusedField = remember(scrollState, scope) {
        { fieldCoordinates: LayoutCoordinates ->
            scrollJob[0]?.cancel()
            scrollJob[0] =
                scope.launch {
                    withFrameNanos { }
                    scrollState.animateFocusedFieldToUpperThird(
                        viewportCoordinates = viewportCoordinates[0],
                        fieldCoordinates = fieldCoordinates,
                        durationMillis = FOCUSED_FIELD_SCROLL_DURATION_MILLIS
                    )
                    delay(FOCUSED_FIELD_SCROLL_CORRECTION_DELAY_MILLIS)
                    scrollState.animateFocusedFieldToUpperThird(
                        viewportCoordinates = viewportCoordinates[0],
                        fieldCoordinates = fieldCoordinates,
                        durationMillis = FOCUSED_FIELD_SCROLL_CORRECTION_DURATION_MILLIS
                    )
                }
        }
    }

    DisposableEffect(Unit) {
        onDispose { scrollJob[0]?.cancel() }
    }

    CompositionLocalProvider(LocalSettingsFocusedFieldScroller provides scrollFocusedField) {
        Box(
            modifier = modifier.onGloballyPositioned { coordinates ->
                viewportCoordinates[0] = coordinates
            }
        ) {
            content()
        }
    }
}

private suspend fun ScrollState.animateFocusedFieldToUpperThird(
    viewportCoordinates: LayoutCoordinates?,
    fieldCoordinates: LayoutCoordinates,
    durationMillis: Int
) {
    val viewport = viewportCoordinates ?: return
    if (!viewport.isAttached || !fieldCoordinates.isAttached) return

    val fieldTop = viewport.localPositionOf(fieldCoordinates, Offset.Zero).y
    val fieldCenter = fieldTop + fieldCoordinates.size.height / 2f
    val targetScroll =
        (value + fieldCenter - viewport.size.height / 3f)
            .roundToInt()
            .coerceIn(0, maxValue)

    if (abs(targetScroll - value) <= FOCUSED_FIELD_SCROLL_THRESHOLD_PX) return

    animateScrollTo(
        value = targetScroll,
        animationSpec =
            tween(
                durationMillis = durationMillis,
                easing = EmphasizedEasing
            )
    )
}
