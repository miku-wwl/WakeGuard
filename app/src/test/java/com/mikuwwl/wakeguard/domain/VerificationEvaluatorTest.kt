package com.mikuwwl.wakeguard.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        for (invalid in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 1.01f, -0.1f)) {
            evaluator.accept(VisionFrame(1f, 1f, 1f))
            evaluator.accept(VisionFrame(1f, 1f, 1f))
            assertEquals(0, evaluator.accept(VisionFrame(invalid, 1f, 1f)).consecutiveValidFrames)
        }
    }

    @Test fun thresholdsAreClampedToSafeRanges() {
        val safe = VisionThresholds(sink = -2f, toothbrush = 3f, toothpaste = 0.1f).sanitized()
        assertEquals(0.2f, safe.sink)
        assertEquals(0.95f, safe.toothbrush)
        assertEquals(0.2f, safe.toothpaste)
        assertEquals(VisionThresholds(), VisionThresholds(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN).sanitized())
    }

    @Test fun oldAttemptAndDuplicateFramesCannotCompleteANewAttempt() {
        val frames = VerificationAttempt(VisionThresholds())
        val accurate = VisionFrame(1f, 1f, 1f)
        frames.accept(0, 100, 100, 101, accurate)
        frames.accept(0, 101, 200, 201, accurate)
        frames.invalidate()
        assertNull(frames.accept(0, 102, 300, 301, accurate))
        assertEquals(1, frames.accept(1, 104, 400, 401, accurate)?.consecutiveValidFrames)
        assertNull(frames.accept(1, 104, 500, 501, accurate))
        assertNull(frames.accept(1, 103, 600, 601, accurate))
        assertEquals(2, frames.accept(1, 105, 700, 701, accurate)?.consecutiveValidFrames)
        assertTrue(frames.accept(1, 106, 800, 801, accurate)?.passed == true)
        assertNull(frames.accept(1, 107, 900, 901, accurate))
    }

    @Test fun delayedInferenceAndFailuresBreakConsecutiveEvidence() {
        val frames = VerificationAttempt(VisionThresholds())
        val accurate = VisionFrame(1f, 1f, 1f)
        frames.accept(0, 100, 100, 101, accurate)
        frames.accept(0, 101, 200, 201, accurate)
        assertNull(frames.accept(0, 102, 300, 3_400, accurate))
        assertEquals(1, frames.accept(0, 103, 3_500, 3_501, accurate)?.consecutiveValidFrames)
        frames.failure(0)
        assertEquals(1, frames.accept(0, 104, 3_600, 3_601, accurate)?.consecutiveValidFrames)
        assertEquals(1, frames.accept(0, 105, 8_000, 8_001, accurate)?.consecutiveValidFrames)
    }
}
