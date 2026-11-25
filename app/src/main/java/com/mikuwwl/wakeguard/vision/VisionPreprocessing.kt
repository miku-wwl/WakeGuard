package com.mikuwwl.wakeguard.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

internal fun productCropRegions(width: Int, height: Int): List<Rect> {
    val cropWidth = (width * 0.62).toInt().coerceAtLeast(1)
    val cropHeight = (height * 0.62).toInt().coerceAtLeast(1)
    return listOf(
        Rect(0, 0, width, height),
        Rect(0, 0, cropWidth, cropHeight),
        Rect(width - cropWidth, 0, width, cropHeight),
        Rect(0, height - cropHeight, cropWidth, height),
        Rect(width - cropWidth, height - cropHeight, width, height),
    )
}

/** Center-fit a source region as in PIL ImageOps.fit. Fractional offsets retain pixel centers. */
internal fun productCrop(bitmap: Bitmap, region: Rect, size: Int): Bitmap {
    val scale = size.toFloat() / minOf(region.width(), region.height())
    val centerX = region.exactCenterX()
    val centerY = region.exactCenterY()
    return scaledSquare(bitmap, size, scale, centerX, centerY, Color.BLACK)
}

/** Retain the entire view, including the image edges, in a square detector input. */
internal fun letterbox(bitmap: Bitmap, size: Int): Bitmap {
    val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
    return scaledSquare(bitmap, size, scale, bitmap.width / 2f, bitmap.height / 2f, Color.GRAY)
}

private fun scaledSquare(
    bitmap: Bitmap,
    size: Int,
    scale: Float,
    centerX: Float,
    centerY: Float,
    fill: Int,
): Bitmap {
    val left = size / 2f - centerX * scale
    val top = size / 2f - centerY * scale
    val destination = RectF(left, top, left + bitmap.width * scale, top + bitmap.height * scale)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        eraseColor(fill)
        Canvas(this).drawBitmap(bitmap, null, destination, Paint(Paint.FILTER_BITMAP_FLAG))
    }
}

internal fun detectorTiles(width: Int, height: Int): List<Rect> {
    val side = minOf(width, height)
    val travelX = width - side
    val travelY = height - side
    val extra = maxOf(travelX, travelY)
    if (extra == 0) return emptyList()
    // Cover both edges. Intermediate overlap prevents a narrow object across a tile boundary
    // from being seen only in the small letterboxed view.
    val intervals = ((extra * 2 + side - 1) / side).coerceIn(2, 4)
    return (0..intervals).map { index ->
        val left = travelX * index / intervals
        val top = travelY * index / intervals
        Rect(left, top, left + side, top + side)
    }.distinct()
}

internal fun normalizedRgb(bitmap: Bitmap): FloatArray {
    val count = bitmap.width * bitmap.height
    val pixels = IntArray(count)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val input = FloatArray(3 * count)
    for ((i, pixel) in pixels.withIndex()) {
        input[i] = (((pixel shr 16) and 0xff) / 255f - 0.485f) / 0.229f
        input[count + i] = (((pixel shr 8) and 0xff) / 255f - 0.456f) / 0.224f
        input[2 * count + i] = ((pixel and 0xff) / 255f - 0.406f) / 0.225f
    }
    return input
}
