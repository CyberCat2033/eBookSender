package com.cybercat.ebooksender.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class AppWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

data class AdaptiveLayoutInfo(
    val widthClass: AppWindowWidthClass = AppWindowWidthClass.Compact,
) {
    val screenHorizontalPadding: Dp
        get() = when (widthClass) {
            AppWindowWidthClass.Compact -> 16.dp
            AppWindowWidthClass.Medium -> 24.dp
            AppWindowWidthClass.Expanded -> 32.dp
        }
}

val LocalAdaptiveLayoutInfo = staticCompositionLocalOf { AdaptiveLayoutInfo() }

fun currentAdaptiveLayoutInfo(maxWidth: Dp): AdaptiveLayoutInfo =
    AdaptiveLayoutInfo(widthClass = maxWidth.toAppWindowWidthClass())

fun Dp.toAppWindowWidthClass(): AppWindowWidthClass = when {
    this < 600.dp -> AppWindowWidthClass.Compact
    this < 840.dp -> AppWindowWidthClass.Medium
    else -> AppWindowWidthClass.Expanded
}
