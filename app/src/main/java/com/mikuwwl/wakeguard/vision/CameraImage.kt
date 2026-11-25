package com.mikuwwl.wakeguard.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

internal fun ImageProxy.toUprightBitmap(): Bitmap {
    val plane = planes.single()
    return rgbaBitmap(plane.buffer, plane.rowStride, plane.pixelStride, width, height, cropRect, imageInfo.rotationDegrees)
}

internal fun rgbaBitmap(
    buffer: ByteBuffer,
    rowStride: Int,
    pixelStride: Int,
    width: Int,
    height: Int,
    crop: Rect,
    rotationDegrees: Int,
): Bitmap {
    require(crop.left >= 0 && crop.top >= 0 && crop.right <= width && crop.bottom <= height && !crop.isEmpty)
    require(pixelStride >= 4 && rowStride >= (width - 1L) * pixelStride + 4)
    require(rotationDegrees in setOf(0, 90, 180, 270))
    val base = buffer.position()
    val last = base + (crop.bottom - 1L) * rowStride + (crop.right - 1L) * pixelStride + 3
    require(last < buffer.limit()) { "Incomplete RGBA image plane" }
    val pixels = IntArray(crop.width() * crop.height())
    var index = 0
    for (y in crop.top until crop.bottom) {
        for (x in crop.left until crop.right) {
            val offset = base + y * rowStride + x * pixelStride
            val red = buffer.get(offset).toInt() and 0xff
            val green = buffer.get(offset + 1).toInt() and 0xff
            val blue = buffer.get(offset + 2).toInt() and 0xff
            val alpha = buffer.get(offset + 3).toInt() and 0xff
            pixels[index++] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    val bitmap = Bitmap.createBitmap(pixels, crop.width(), crop.height(), Bitmap.Config.ARGB_8888)
    if (rotationDegrees == 0) return bitmap
    try {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, false)
    } finally {
        bitmap.recycle()
    }
}
