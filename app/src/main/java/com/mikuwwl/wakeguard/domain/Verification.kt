package com.mikuwwl.wakeguard.domain

data class VisionThresholds(
    val sink: Float = 0.45f,
    val toothbrush: Float = 0.45f,
    val toothpaste: Float = 0.93f,
) {
    fun sanitized(): VisionThresholds {
        val defaults = VisionThresholds()
        return VisionThresholds(
            sink = bounded(sink, defaults.sink, 0.95f),
            toothbrush = bounded(toothbrush, defaults.toothbrush, 0.95f),
            toothpaste = bounded(toothpaste, defaults.toothpaste, 0.99f),
        )
    }

    private fun bounded(value: Float, fallback: Float, maximum: Float): Float =
        if (value.isFinite()) value.coerceIn(0.2f, maximum) else fallback
}

data class VisionFrame(
    val sink: Float,
    val toothbrush: Float,
    val toothpaste: Float,
)

data class VerificationProgress(val consecutiveValidFrames: Int, val passed: Boolean)

class VerificationEvaluator(
    thresholds: VisionThresholds,
    private val requiredConsecutiveFrames: Int = 3,
) {
    init { require(requiredConsecutiveFrames > 0) }

    private val thresholds = thresholds.sanitized()
    private var consecutive = 0

    fun accept(frame: VisionFrame): VerificationProgress {
        val valid = frame.sink in thresholds.sink..1f &&
            frame.toothbrush in thresholds.toothbrush..1f &&
            frame.toothpaste in thresholds.toothpaste..1f
        consecutive = if (valid) (consecutive + 1).coerceAtMost(requiredConsecutiveFrames) else 0
        return VerificationProgress(consecutive, consecutive >= requiredConsecutiveFrames)
    }

    fun reset() { consecutive = 0 }
}

/** A camera attempt owns one monotonic stream of frames on the UI thread. */
internal class VerificationAttempt(thresholds: VisionThresholds) {
    private val evaluator = VerificationEvaluator(thresholds)
    private var latestTimestamp = Long.MIN_VALUE
    private var latestArrivalMs: Long? = null
    private var complete = false
    var generation = 0L
        private set

    fun invalidate() {
        generation++
        latestTimestamp = Long.MIN_VALUE
        latestArrivalMs = null
        complete = false
        evaluator.reset()
    }

    fun failure(generation: Long) {
        if (generation == this.generation) evaluator.reset()
    }

    fun accept(
        generation: Long,
        sensorTimestamp: Long,
        arrivalMs: Long,
        nowMs: Long,
        frame: VisionFrame,
    ): VerificationProgress? {
        if (generation != this.generation || complete || sensorTimestamp <= latestTimestamp) return null
        latestTimestamp = sensorTimestamp
        if (nowMs - arrivalMs !in 0..MAX_FRAME_AGE_MS) {
            evaluator.reset()
            return null
        }
        if (latestArrivalMs?.let { arrivalMs - it !in 1..MAX_FRAME_GAP_MS } == true) {
            evaluator.reset()
        }
        latestArrivalMs = arrivalMs
        return evaluator.accept(frame).also { complete = it.passed }
    }

    private companion object {
        const val MAX_FRAME_AGE_MS = 3_000L
        const val MAX_FRAME_GAP_MS = 4_000L
    }
}
