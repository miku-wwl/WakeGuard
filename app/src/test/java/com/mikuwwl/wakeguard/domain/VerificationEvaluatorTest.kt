package com.mikuwwl.wakeguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationEvaluatorTest {
    private val thresholds = VisionThresholds(sink = 0.5f, toothbrush = 0.55f, toothpaste = 0.8f)

    @Test fun requiresAllThreeObjectsForThreeConsecutiveFrames() {
        val evaluator = VerificationEvaluator(thresholds)
        val valid = VisionFrame(0.70f, 0.75f, 0.91f)
        assertFalse(evaluator.accept(valid).passed)
        assertFalse(evaluator.accept(valid).passed)
        assertTrue(evaluator.accept(valid).passed)
    }

    @Test fun missingToothpasteNeverPasses() {
        val evaluator = VerificationEvaluator(thresholds)
        repeat(10) { assertFalse(evaluator.accept(VisionFrame(0.99f, 0.99f, 0.79f)).passed) }
    }

    @Test fun anInvalidFrameResetsTheConsecutiveCount() {
        val evaluator = VerificationEvaluator(thresholds)
        evaluator.accept(VisionFrame(0.8f, 0.8f, 0.9f))
        assertEquals(0, evaluator.accept(VisionFrame(0.8f, 0.4f, 0.9f)).consecutiveValidFrames)
        assertEquals(1, evaluator.accept(VisionFrame(0.8f, 0.8f, 0.9f)).consecutiveValidFrames)
        assertFalse(evaluator.accept(VisionFrame(0.8f, 0.8f, 0.9f)).passed)
    }

    @Test fun nonFiniteScoresCannotPass() {
        val evaluator = VerificationEvaluator(thresholds)
        repeat(4) { assertFalse(evaluator.accept(VisionFrame(Float.NaN, 1f, 1f)).passed) }
    }

    @Test fun thresholdsAreClampedToSafeRanges() {
        val safe = VisionThresholds(sink = -2f, toothbrush = 3f, toothpaste = 0.1f).sanitized()
        assertEquals(0.2f, safe.sink)
        assertEquals(0.95f, safe.toothbrush)
        assertEquals(0.2f, safe.toothpaste)
    }
}
