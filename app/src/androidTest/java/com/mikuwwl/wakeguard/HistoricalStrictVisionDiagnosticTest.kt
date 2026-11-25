package com.mikuwwl.wakeguard

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.domain.VerificationEvaluator
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.domain.VisionThresholds
import com.mikuwwl.wakeguard.vision.OnDeviceVisionEngine
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.ceil

@LargeTest
@RunWith(AndroidJUnit4::class)
class HistoricalStrictVisionDiagnosticTest {
    @Test fun historicalNinetyPercentThreeObjectGateIsRecordedAsDiagnostic() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val positives = listOf(
            "pexels-toothbrush-toothpaste-at-sink.jpeg",
            "pexels-sink-toothpaste-8091115.jpeg",
            "pexels-toothbrush-toothpaste-sink-8091047.jpeg",
            "pexels-toothpaste-sink-7622547.jpg",
        )
        val negatives = listOf("macau-toothbrush-toothpaste.jpg", "washingtoothbrush.jpg")
        val outcomes = mutableMapOf<String, Boolean>()
        OnDeviceVisionEngine(instrumentation.targetContext).use { engine ->
            for (file in positives + negatives) {
                val bitmap = assets.open("vision/$file").use {
                    checkNotNull(BitmapFactory.decodeStream(it))
                }
                try {
                    val evaluator = VerificationEvaluator(VisionThresholds())
                    var passed = false
                    repeat(3) { attempt ->
                        val start = SystemClock.elapsedRealtimeNanos()
                        val frame = engine.analyze(bitmap)
                        val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
                        check(frame.allScoresValid()) { "Invalid score for $file" }
                        val progress = evaluator.accept(frame)
                        passed = progress.passed
                        Log.i("WakeGuardFixture", JSONObject().apply {
                            put("id", file.substringBeforeLast('.'))
                            put("is_complete", file in positives)
                            put("attempt", attempt + 1)
                            put("scores", frame.toJson())
                            put("latency_ms", elapsed)
                            put("consecutive", progress.consecutiveValidFrames)
                            put("passed", passed)
                        }.toString())
                    }
                    outcomes[file] = passed
                } finally {
                    bitmap.recycle()
                }
            }
        }
        val falseAccepts = negatives.filter { outcomes.getValue(it) }
        assertEquals("Incomplete scene accepted: $falseAccepts", 0, falseAccepts.size)
        val accepted = positives.count { outcomes.getValue(it) }
        val required = ceil(positives.size * 0.90).toInt()
        assertTrue("Actual historical positive acceptance $accepted/${positives.size}; required $required", accepted >= required)
    }
}

internal fun VisionFrame.allScoresValid(): Boolean = sink in 0f..1f && toothbrush in 0f..1f && toothpaste in 0f..1f

internal fun VisionFrame.toJson(): JSONObject = JSONObject().apply {
    put("sink", sink.toDouble())
    put("toothbrush", toothbrush.toDouble())
    put("toothpaste", toothpaste.toDouble())
}
