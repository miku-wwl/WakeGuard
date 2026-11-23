package com.mikuwwl.wakeguard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.domain.VerificationEvaluator
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.domain.VisionThresholds
import com.mikuwwl.wakeguard.vision.OnDeviceVisionEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionEngineInstrumentedTest {
    @Test fun localModelsScoreFixturesAndRejectBothIncompleteCombinations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val product = loadFixture(testContext, "vision/macau-toothbrush-toothpaste.jpg")
        val incompleteScene = loadFixture(testContext, "vision/washingtoothbrush.jpg")
        val engine = OnDeviceVisionEngine(context)
        try {
            val productFrame = engine.analyze(product)
            val incompleteFrame = engine.analyze(incompleteScene)
            Log.i(
                TAG,
                "product_frame=$productFrame incomplete_sink_scene=$incompleteFrame",
            )

            assertTrue(
                "All detector and classifier scores must be finite",
                listOf(productFrame.sink, productFrame.toothbrush, productFrame.toothpaste,
                    incompleteFrame.sink, incompleteFrame.toothbrush, incompleteFrame.toothpaste)
                    .all(Float::isFinite),
            )
            assertTrue(
                "A real toothpaste photo should score above the sink-only scene: $productFrame vs $incompleteFrame",
                productFrame.toothpaste > incompleteFrame.toothpaste,
            )

            val productOnlyEvaluator = VerificationEvaluator(VisionThresholds())
            repeat(3) {
                assertFalse("Toothbrush and toothpaste without a sink must not verify", productOnlyEvaluator.accept(productFrame).passed)
            }
            val sinkOnlyEvaluator = VerificationEvaluator(VisionThresholds())
            repeat(3) {
                assertFalse("A sink and toothbrush without toothpaste must not verify", sinkOnlyEvaluator.accept(incompleteFrame).passed)
            }
        } finally {
            engine.close()
            product.recycle()
            incompleteScene.recycle()
        }
    }

    private fun loadFixture(context: Context, path: String): Bitmap =
        context.assets.open(path).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Could not decode fixture $path" }
        }

    companion object { private const val TAG = "WakeGuardVisionTest" }
}
