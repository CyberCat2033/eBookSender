package com.cybercat.pocketbooksender.ui

import android.graphics.Bitmap
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
import com.cybercat.pocketbooksender.util.UploadPreviewCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun UploadPreviewCover(
    itemId: String,
    title: String,
    modifier: Modifier = Modifier,
    reloadKey: Any? = null,
    placeholderIcon: ImageVector = Icons.Outlined.Image
) {
    val context = LocalContext.current
    val cacheKey = remember(itemId) { UploadPreviewCache.memoryKey(itemId) }
    var bitmap by remember(itemId) {
        mutableStateOf<Bitmap?>(BitmapCache.getFromMemory(cacheKey))
    }

    LaunchedEffect(itemId, reloadKey) {
        if (bitmap != null) return@LaunchedEffect

        bitmap = withContext(Dispatchers.IO) {
            UploadPreviewCache.load(
                context = context,
                itemId = itemId,
                reqWidth = UploadPreviewCache.DEFAULT_REQUEST_WIDTH,
                reqHeight = UploadPreviewCache.DEFAULT_REQUEST_HEIGHT
            )?.also { loadedBitmap ->
                BitmapCache.putInMemory(cacheKey, loadedBitmap)
            }
        }
    }

    Surface(
        modifier = modifier.size(width = 58.dp, height = 78.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        val cover = bitmap
        if (cover != null) {
            ComposeImage(
                bitmap = cover.asImageBitmap(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = placeholderIcon,
                    contentDescription = null,
                    modifier = Modifier.padding(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
