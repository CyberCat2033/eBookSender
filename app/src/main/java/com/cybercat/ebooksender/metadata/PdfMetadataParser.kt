package com.cybercat.ebooksender.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PdfMetadataParser @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun extract(uri: Uri, fallbackTitle: String): BookMetadata {
        val preview = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            renderFirstPage(descriptor)
        }
        return BookMetadata(title = fallbackTitle, preview = preview)
    }

    private fun renderFirstPage(descriptor: ParcelFileDescriptor): Bitmap? =
        runCatching {
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val scale = minOf(
                        MetadataPreviewDecoder.PREVIEW_MAX_WIDTH / page.width.toFloat(),
                        MetadataPreviewDecoder.PREVIEW_MAX_HEIGHT / page.height.toFloat(),
                    )
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    Canvas(bitmap).drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }.getOrNull()
}
