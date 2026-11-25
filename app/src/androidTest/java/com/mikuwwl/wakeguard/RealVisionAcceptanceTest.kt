package com.mikuwwl.wakeguard

import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.mikuwwl.wakeguard.domain.VerificationPolicy
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.vision.OnDeviceVisionEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class RealVisionAcceptanceTest {
    @Test fun bundledModelsMeetTheLightweightWashingAreaGateAndRejectObviousUnrelatedScenes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val positives = listOf(
            Fixture("pexels-toothbrush-toothpaste-at-sink", "vision/pexels-toothbrush-toothpaste-at-sink.jpeg", independent = false),
            Fixture("pexels-sink-toothpaste-8091115", "vision/pexels-sink-toothpaste-8091115.jpeg", independent = false),
            Fixture("pexels-toothbrush-toothpaste-sink-8091047", "vision/pexels-toothbrush-toothpaste-sink-8091047.jpeg", independent = false),
            Fixture("pexels-toothpaste-sink-7622547", "vision/pexels-toothpaste-sink-7622547.jpg", independent = false),
            Fixture("wm-64176029", "vision/round3/wm-64176029.jpg", independent = true),
            Fixture("wm-119921527", "vision/round3/wm-119921527.jpg", independent = true),
        )
        val unrelated = listOf(
            Fixture("wm-193088243-pharmacy", "vision/round3/wm-193088243.jpg", independent = false),
            Fixture("wm-117409408-wood-sticks", "vision/round3/wm-117409408.jpg", independent = false),
            Fixture("wm-28250893-pliers", "vision/round3/wm-28250893.jpg", independent = false),
            Fixture("wm-39393966-foam", "vision/round3/wm-39393966.jpg", independent = false),
            Fixture("wm-57000467-toy-animals", "vision/round3/wm-57000467.jpg", independent = false),
        )
        val policies = listOf(
            VerificationPolicy.relaxedSimultaneous,
            VerificationPolicy.sinkAnchored,
            VerificationPolicy.sessionAccumulated,
        )
        val rows = JSONArray()
        val results = mutableMapOf<String, Map<String, Boolean>>()
        val allFixtures = positives + unrelated

        OnDeviceVisionEngine(instrumentation.targetContext).use { engine ->
            allFixtures.forEach { fixture ->
                val bitmap = assets.open(fixture.path).use { checkNotNull(BitmapFactory.decodeStream(it)) }
                val startedAt = SystemClock.elapsedRealtimeNanos()
                val scores = try {
                    engine.analyze(bitmap)
                } finally {
                    bitmap.recycle()
                }
                val latencyMs = (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0
                assertTrue("Invalid model output for ${fixture.id}: $scores", scores.hasValidScores())
                val policyOutcomes = policies.associate { policy ->
                    val evaluator = policy.newEvaluator()
                    var progress = evaluator.accept(scores, 1_000L)
                    progress = evaluator.accept(scores, 1_700L)
                    policy.name to progress.passed
                }
                results[fixture.id] = policyOutcomes
                val row = JSONObject().apply {
                    put("id", fixture.id)
                    put("group", if (fixture in positives) "positive" else "obvious_unrelated")
                    put("independent_positive", fixture.independent)
                    put("scores", policyJson(scores))
                    put("latency_ms", latencyMs)
                    put("policy_acceptance", JSONObject(policyOutcomes))
                }
                rows.put(row)
                Log.i(TAG, row.toString())
            }
        }

        val selected = VerificationPolicy.production.name
        val acceptedPositiveIds = positives.filter { results.getValue(it.id).getValue(selected) }.map { it.id }
        val acceptedIndependentIds = positives.filter { it.independent && results.getValue(it.id).getValue(selected) }.map { it.id }
        val acceptedUnrelatedIds = unrelated.filter { results.getValue(it.id).getValue(selected) }.map { it.id }
        val summary = JSONObject().apply {
            put("selected_policy", selected)
            put("thresholds", JSONObject().apply {
                put("sink", VerificationPolicy.production.thresholds.sink.toDouble())
                put("toothbrush", VerificationPolicy.production.thresholds.toothbrush.toDouble())
                put("toothpaste", VerificationPolicy.production.thresholds.toothpaste.toDouble())
            })
            put("required_fresh_frames", VerificationPolicy.production.requiredFreshFrames)
            put("evidence_window_ms", VerificationPolicy.production.evidenceWindowMs)
            put("positive_accepted", acceptedPositiveIds.size)
            put("positive_total", positives.size)
            put("accepted_positive_ids", JSONArray(acceptedPositiveIds))
            put("independent_positive_accepted", acceptedIndependentIds.size)
            put("independent_positive_total", positives.count { it.independent })
            put("accepted_independent_ids", JSONArray(acceptedIndependentIds))
            put("obvious_unrelated_accepted", acceptedUnrelatedIds.size)
            put("obvious_unrelated_total", unrelated.size)
            put("accepted_unrelated_ids", JSONArray(acceptedUnrelatedIds))
            put("fixtures", rows)
        }
        Log.i(TAG, "SUMMARY $summary")

        assertTrue("Selected policy must accept at least 4/6 labeled washing-area fixtures; got $acceptedPositiveIds", acceptedPositiveIds.size >= 4)
        assertTrue("Selected policy must accept an independent positive; got $acceptedIndependentIds", acceptedIndependentIds.isNotEmpty())
        assertEquals("Clearly unrelated scenes must remain rejected: $acceptedUnrelatedIds", 0, acceptedUnrelatedIds.size)
    }

    private data class Fixture(val id: String, val path: String, val independent: Boolean)

    private companion object { const val TAG = "WakeGuardPolicy" }
}

private fun policyJson(frame: VisionFrame): JSONObject = JSONObject().apply {
    put("sink", frame.sink.toDouble())
    put("toothbrush", frame.toothbrush.toDouble())
    put("toothpaste", frame.toothpaste.toDouble())
}
