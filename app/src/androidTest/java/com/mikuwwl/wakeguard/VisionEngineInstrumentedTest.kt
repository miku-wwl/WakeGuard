package com.mikuwwl.wakeguard

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.domain.VerificationEvaluator
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.domain.VisionThresholds
import com.mikuwwl.wakeguard.vision.CameraVerificationScreen
import com.mikuwwl.wakeguard.vision.OnDeviceVisionEngine
import com.mikuwwl.wakeguard.vision.VisionEngine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisionEngineInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun localModelsScoreFixturesAndRejectBothIncompleteCombinations() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val product = loadFixture(testContext, "vision/macau-toothbrush-toothpaste.jpg")
        val incompleteScene = loadFixture(testContext, "vision/washingtoothbrush.jpg")
        val completeScene = loadFixture(testContext, "vision/pexels-toothbrush-toothpaste-at-sink.jpeg")
        val sinkVisibleScene = loadFixture(testContext, "vision/pexels-sink-toothpaste-8091115.jpeg")
        val closeScene = loadFixture(testContext, "vision/pexels-toothbrush-toothpaste-sink-8091047.jpeg")
        val widePositiveScene = loadFixture(testContext, "vision/pexels-toothpaste-sink-7622547.jpg")
        val bathroomScene = loadFixture(testContext, "vision/pexels-bathroom-sink-toothbrush-4154164.jpg")
        val engine = OnDeviceVisionEngine(context)
        try {
            val productFrame = engine.analyze(product)
            val incompleteFrame = engine.analyze(incompleteScene)
            val completeFrame = engine.analyze(completeScene)
            val sinkVisibleFrame = engine.analyze(sinkVisibleScene)
            val closeFrame = engine.analyze(closeScene)
            val widePositiveFrame = engine.analyze(widePositiveScene)
            val bathroomFrame = engine.analyze(bathroomScene)
            Log.i(
                TAG,
                "product_frame=$productFrame incomplete_sink_scene=$incompleteFrame complete_scene=$completeFrame sink_visible_scene=$sinkVisibleFrame close_scene=$closeFrame wide_positive_scene=$widePositiveFrame bathroom_scene=$bathroomFrame",
            )

            assertTrue(
                "All detector and classifier scores must be finite",
                listOf(productFrame.sink, productFrame.toothbrush, productFrame.toothpaste,
                    incompleteFrame.sink, incompleteFrame.toothbrush, incompleteFrame.toothpaste,
                    completeFrame.sink, completeFrame.toothbrush, completeFrame.toothpaste,
                    sinkVisibleFrame.sink, sinkVisibleFrame.toothbrush, sinkVisibleFrame.toothpaste,
                    closeFrame.sink, closeFrame.toothbrush, closeFrame.toothpaste,
                    widePositiveFrame.sink, widePositiveFrame.toothbrush, widePositiveFrame.toothpaste,
                    bathroomFrame.sink, bathroomFrame.toothbrush, bathroomFrame.toothpaste)
                    .all(Float::isFinite),
            )
            val productOnlyEvaluator = VerificationEvaluator(VisionThresholds())
            repeat(3) {
                assertFalse("Toothbrush and toothpaste without a sink must not verify", productOnlyEvaluator.accept(productFrame).passed)
            }
            val sinkOnlyEvaluator = VerificationEvaluator(VisionThresholds())
            repeat(3) {
                assertFalse("A sink and toothbrush without toothpaste must not verify", sinkOnlyEvaluator.accept(incompleteFrame).passed)
            }
            val positiveEvaluator = VerificationEvaluator(VisionThresholds())
            val positiveProgress = (1..3).map { positiveEvaluator.accept(completeFrame) }
            positiveEvaluator.reset()
            val sinkVisibleProgress = (1..3).map { positiveEvaluator.accept(sinkVisibleFrame) }
            positiveEvaluator.reset()
            val closeProgress = (1..3).map { positiveEvaluator.accept(closeFrame) }
            positiveEvaluator.reset()
            val widePositiveProgress = (1..3).map { positiveEvaluator.accept(widePositiveFrame) }
            positiveEvaluator.reset()
            val bathroomProgress = (1..3).map { positiveEvaluator.accept(bathroomFrame) }
            Log.i(
                TAG,
                "complete_scene_verification=$positiveProgress sink_visible_scene_verification=$sinkVisibleProgress close_scene_verification=$closeProgress wide_positive_scene_verification=$widePositiveProgress bathroom_scene_verification=$bathroomProgress",
            )
        } finally {
            engine.close()
            product.recycle()
            incompleteScene.recycle()
            completeScene.recycle()
            sinkVisibleScene.recycle()
            closeScene.recycle()
            widePositiveScene.recycle()
            bathroomScene.recycle()
        }
    }

    @Test fun threeIdenticalValidCameraFramesCompleteVerification() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(appContext.packageName, Manifest.permission.CAMERA)
        val verified = AtomicBoolean(false)
        val analyzedFrames = AtomicInteger(0)
        composeRule.setContent {
            MaterialTheme {
                CameraVerificationScreen(
                    thresholds = VisionThresholds(sink = 0.5f, toothbrush = 0.5f, toothpaste = 0.5f),
                    onVerified = { verified.set(true) },
                    onRecordFailedAttempt = {},
                    onEmergencyStop = {},
                    onBackToAlarm = {},
                    engineFactory = { ConstantVisionEngine(analyzedFrames) },
                )
            }
        }
        composeRule.waitUntil(30_000) { verified.get() }
        assertTrue("The same passing scene must be accepted over three analyzed frames", analyzedFrames.get() >= 3)
    }

    @Test fun cameraPermissionDenialKeepsEmergencyExitAvailable() {
        val emergencyRequested = AtomicBoolean(false)
        composeRule.setContent {
            MaterialTheme {
                CameraVerificationScreen(
                    thresholds = VisionThresholds(),
                    onVerified = {},
                    onRecordFailedAttempt = {},
                    onEmergencyStop = { emergencyRequested.set(true) },
                    onBackToAlarm = {},
                    cameraPermissionChecker = { false },
                )
            }
        }
        composeRule.onNodeWithText("需要相机权限进行现场识别").assertIsDisplayed()
        composeRule.onNodeWithText("紧急停止").assertIsDisplayed().performClick()
        assertTrue("The explicit emergency path remains available without camera permission", emergencyRequested.get())
    }

    private fun loadFixture(context: Context, path: String): Bitmap =
        context.assets.open(path).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Could not decode fixture $path" }
        }

    companion object { private const val TAG = "WakeGuardVisionTest" }

    private class ConstantVisionEngine(private val analyzedFrames: AtomicInteger) : VisionEngine {
        override fun analyze(bitmap: Bitmap): VisionFrame {
            analyzedFrames.incrementAndGet()
            return VisionFrame(sink = 1f, toothbrush = 1f, toothpaste = 1f)
        }

        override fun close() = Unit
    }
}
