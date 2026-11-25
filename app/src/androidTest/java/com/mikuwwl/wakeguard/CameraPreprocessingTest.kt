package com.mikuwwl.wakeguard

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mikuwwl.wakeguard.vision.detectorTiles
import com.mikuwwl.wakeguard.vision.letterbox
import com.mikuwwl.wakeguard.vision.normalizedRgb
import com.mikuwwl.wakeguard.vision.productCrop
import com.mikuwwl.wakeguard.vision.rgbaBitmap
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraPreprocessingTest {
    @Test fun rgbaHonorsStrideAndPositionAndRotatesTheCrop() {
        val buffer = ByteBuffer.allocate(50)
        val source = intArrayOf(Color.WHITE, Color.RED, Color.GREEN, Color.BLACK, Color.BLUE, Color.YELLOW)
        for ((i, pixel) in source.withIndex()) {
            val start = 2 + (i / 3) * 23 + (i % 3) * 6
            buffer.put(start, Color.red(pixel).toByte())
            buffer.put(start + 1, Color.green(pixel).toByte())
            buffer.put(start + 2, Color.blue(pixel).toByte())
            buffer.put(start + 3, Color.alpha(pixel).toByte())
        }
        buffer.position(2)
        val crop = Rect(1, 0, 3, 2)
        val image = rgbaBitmap(buffer, 23, 6, 3, 2, crop, 90)
        try {
            assertEquals(2, image.width)
            assertEquals(2, image.height)
            val output = IntArray(4)
            image.getPixels(output, 0, 2, 0, 0, 2, 2)
            assertArrayEquals(intArrayOf(Color.BLUE, Color.RED, Color.YELLOW, Color.GREEN), output)
            assertEquals(2, buffer.position())
        } finally {
            image.recycle()
        }
    }

    @Test fun letterboxKeepsEdgesAndTheClassifierUsesRgbChw() {
        val source = Bitmap.createBitmap(intArrayOf(Color.RED, Color.BLUE), 2, 1, Bitmap.Config.ARGB_8888)
        val full = letterbox(source, 4)
        val product = productCrop(source, Rect(0, 0, 1, 1), 1)
        try {
            assertEquals(Color.GRAY, full.getPixel(0, 0))
            assertEquals(Color.RED, full.getPixel(0, 1))
            assertEquals(Color.BLUE, full.getPixel(3, 2))
            // PIL crop + torchvision ToTensor/Normalize on the same red pixel.
            assertArrayEquals(floatArrayOf(2.2489083f, -2.0357143f, -1.8044444f), normalizedRgb(product), 0.00001f)
            val tiles = detectorTiles(100, 230)
            assertEquals(Rect(0, 0, 100, 100), tiles.first())
            assertEquals(Rect(0, 130, 100, 230), tiles.last())
        } finally {
            product.recycle()
            full.recycle()
            source.recycle()
        }
    }
}
