package com.cybercat.ebooksender.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2F5D50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7D8CB),
    onPrimaryContainer = Color(0xFF082019),
    secondary = Color(0xFF5B5F42),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E5BE),
    onSecondaryContainer = Color(0xFF191D08),
    tertiary = Color(0xFF6B5578),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2D8FF),
    onTertiaryContainer = Color(0xFF241331),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFCF8),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFCF8),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFE0E4DD),
    onSurfaceVariant = Color(0xFF404944),
    outline = Color(0xFF707974),
    outlineVariant = Color(0xFFBFC9C3),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9CCBBA),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF174D42),
    onPrimaryContainer = Color(0xFFB7D8CB),
    secondary = Color(0xFFC4C9A4),
    onSecondary = Color(0xFF2E331B),
    secondaryContainer = Color(0xFF454930),
    onSecondaryContainer = Color(0xFFE0E5BE),
    tertiary = Color(0xFFD6BCE4),
    onTertiary = Color(0xFF3A2948),
    tertiaryContainer = Color(0xFF523F60),
    onTertiaryContainer = Color(0xFFF2D8FF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121411),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF121411),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF444842),
    onSurfaceVariant = Color(0xFFC0C9C0),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404942),
)

@Composable
fun EBookSenderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
