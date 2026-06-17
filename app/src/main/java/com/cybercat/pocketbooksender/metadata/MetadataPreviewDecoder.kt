package com.cybercat.pocketbooksender.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory

internal object MetadataPreviewDecoder {
    const val PREVIEW_MAX_WIDTH = 360
    const val PREVIEW_MAX_HEIGHT = 540

    fun decodeBitmap(bytes: ByteArray): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = calculateInSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxWidth = PREVIEW_MAX_WIDTH,
                    maxHeight = PREVIEW_MAX_HEIGHT,
                )
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxWidth: Int,
        maxHeight: Int,
    ): Int {
        if (width <= 0 || height <= 0) return 1

        var sampleSize = 1
        var sampledWidth = width
        var sampledHeight = height
        while (sampledWidth / 2 >= maxWidth || sampledHeight / 2 >= maxHeight) {
            sampleSize *= 2
            sampledWidth /= 2
            sampledHeight /= 2
        }
        return sampleSize
    }
}
