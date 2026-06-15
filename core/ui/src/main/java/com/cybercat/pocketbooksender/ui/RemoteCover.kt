package com.cybercat.pocketbooksender.ui

import android.graphics.Bitmap
import android.webkit.CookieManager
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val CoverLoadDelayMillis = 120L
private const val CoverRequestWidth = 160
private const val CoverRequestHeight = 220

/**
 * A reusable, optimized cover image loader component that supports memory caching,
 * delayed network fetch, optional cookies retrieval, and custom placholder icons.
 */
@Composable
fun RemoteCover(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Outlined.Image,
    useCookies: Boolean = false,
) {
    val context = LocalContext.current
    var bitmap by remember(coverUrl) { mutableStateOf<Bitmap?>(coverUrl?.let { BitmapCache.getFromMemory(it) }) }

    LaunchedEffect(coverUrl) {
        if (coverUrl != null && bitmap == null) {
            delay(CoverLoadDelayMillis)
            val cookie = if (useCookies) {
                withContext(Dispatchers.IO) {
                    runCatching { CookieManager.getInstance().getCookie(coverUrl) }.getOrNull()
                }
            } else null
            if (bitmap == null) {
                bitmap = loadCachedRemoteBitmap(
                    context = context,
                    url = coverUrl,
                    cookie = cookie,
                    reqWidth = CoverRequestWidth,
                    reqHeight = CoverRequestHeight,
                )
            }
        }
    }

    Surface(
        modifier = modifier.size(width = 58.dp, height = 78.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        val cover = bitmap
        if (cover != null) {
            ComposeImage(
                bitmap = cover.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
