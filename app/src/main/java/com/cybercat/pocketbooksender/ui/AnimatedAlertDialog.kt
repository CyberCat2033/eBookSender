package com.cybercat.pocketbooksender.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Provides the animated dismiss lambda inside [AnimatedAlertDialog] content.
 *
 * Read this inside confirm/dismiss button composables instead of calling the parent's
 * dismiss handler directly. This ensures the exit animation plays before the dialog
 * is removed from composition.
 *
 * Example:
 * ```
 * dismissButton = {
 *     val dismiss = LocalDismissDialog.current
 *     TextButton(onClick = { doCancel(); dismiss() }) { Text("Cancel") }
 * }
 * ```
 */
val LocalDismissDialog = compositionLocalOf<() -> Unit> { {} }

/**
 * Runs an action after the dialog's exit animation has completed.
 *
 * Use this for actions that repaint a large part of the UI, such as applying
 * a new language, so the dialog does not visibly recompose while closing.
 */
val LocalDismissDialogAfter = compositionLocalOf<((() -> Unit) -> Unit)> { { action -> action() } }

/**
 * Material Design 3 AlertDialog with smooth enter **and** exit animations.
 *
 * - **Enter**: subtle scale-in from 96% + fade-in
 * - **Exit**: subtle scale-out to 98% + fade-out
 *
 * Exit animation fires on:
 * 1. Back-press / outside-tap → intercepted internally.
 * 2. Button presses → when buttons call [LocalDismissDialog.current] instead of
 *    triggering the parent dismiss state directly.
 *
 * The [onDismissRequest] callback is invoked **after** the exit animation completes,
 * so the caller can safely remove the dialog from composition at that point.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    confirmButton: @Composable (() -> Unit)? = null,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    var contentVisible by remember { mutableStateOf(false) }
    var dismissStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Animated dismiss: fade-out content, then call the real onDismissRequest.
    val animatedDismiss: (afterDismiss: (() -> Unit)?) -> Unit = { afterDismiss ->
        if (!dismissStarted) {
            dismissStarted = true
            scope.launch {
                contentVisible = false
                delay(ExitMs.toLong())
                onDismissRequest()
                afterDismiss?.invoke()
            }
        }
    }

    // Trigger enter animation on first composition.
    LaunchedEffect(Unit) { contentVisible = true }

    BasicAlertDialog(
        onDismissRequest = { animatedDismiss(null) },
        modifier = modifier,
    ) {
        AnimatedVisibility(
            visible = contentVisible,
            enter = scaleIn(
                initialScale = 0.96f,
                animationSpec = tween(
                    durationMillis = EnterMs,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = FadeEnterMs,
                    easing = FastOutSlowInEasing,
                ),
            ),
            exit = scaleOut(
                targetScale = 0.98f,
                animationSpec = tween(
                    durationMillis = ExitMs,
                    easing = FastOutSlowInEasing,
                ),
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = ExitMs,
                    easing = FastOutSlowInEasing,
                ),
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                // Provide animated dismiss to button composables via CompositionLocal.
                CompositionLocalProvider(
                    LocalDismissDialog provides { animatedDismiss(null) },
                    LocalDismissDialogAfter provides { afterDismiss -> animatedDismiss(afterDismiss) },
                ) {
                    Column(
                        modifier = Modifier.padding(
                            start = 24.dp, top = 24.dp, end = 24.dp, bottom = 18.dp,
                        ),
                    ) {
                        if (title != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides AlertDialogDefaults.titleContentColor,
                            ) {
                                CompositionLocalProvider(
                                    LocalTextStyle provides LocalTextStyle.current
                                        .merge(MaterialTheme.typography.headlineSmall),
                                ) {
                                    Box(Modifier.padding(bottom = 16.dp)) { title() }
                                }
                            }
                        }
                        if (text != null) {
                            CompositionLocalProvider(
                                LocalContentColor provides AlertDialogDefaults.textContentColor,
                            ) {
                                CompositionLocalProvider(
                                    LocalTextStyle provides LocalTextStyle.current
                                        .merge(MaterialTheme.typography.bodyMedium),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f, fill = false)
                                            .padding(bottom = 24.dp),
                                    ) { text() }
                                }
                            }
                        }
                        if (dismissButton != null || confirmButton != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                dismissButton?.invoke()
                                if (dismissButton != null && confirmButton != null) {
                                    Spacer(Modifier.width(8.dp))
                                }
                                confirmButton?.invoke()
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val EnterMs = 260
private const val FadeEnterMs = 220
private const val ExitMs = 220
