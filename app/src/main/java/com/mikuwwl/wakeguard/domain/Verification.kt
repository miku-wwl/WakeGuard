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
) {
    fun hasValidScores(): Boolean =
        sink in 0f..1f && toothbrush in 0f..1f && toothpaste in 0f..1f
}

enum class VerificationStrategy {
    SIMULTANEOUS,
    SINK_ANCHORED,
    SESSION_ACCUMULATED,
}

/** Immutable rules captured when an active alarm is shown. */
data class VerificationPolicy(
    val name: String,
    val strategy: VerificationStrategy,
    val thresholds: VisionThresholds,
    val requiredConsecutiveFrames: Int = 2,
    val evidenceWindowMs: Long = 12_000L,
    val requiredFreshFrames: Int = 2,
) {
    init {
        require(name.isNotBlank())
        require(requiredConsecutiveFrames > 0)
        require(evidenceWindowMs > 0)
        require(requiredFreshFrames > 0)
    }

    fun newEvaluator() = VerificationEvaluator(this)

    companion object {
        private val LIGHT_THRESHOLDS = VisionThresholds(sink = 0.25f, toothbrush = 0.25f, toothpaste = 0.40f)

        val relaxedSimultaneous = VerificationPolicy(
            name = "relaxed-simultaneous",
            strategy = VerificationStrategy.SIMULTANEOUS,
            thresholds = LIGHT_THRESHOLDS,
        )

        val sinkAnchored = VerificationPolicy(
            name = "sink-anchored",
            strategy = VerificationStrategy.SINK_ANCHORED,
            thresholds = LIGHT_THRESHOLDS,
        )

        val sessionAccumulated = VerificationPolicy(
            name = "session-accumulated",
            strategy = VerificationStrategy.SESSION_ACCUMULATED,
            thresholds = LIGHT_THRESHOLDS,
            evidenceWindowMs = 12_000L,
            requiredFreshFrames = 2,
        )

        /** Round 2's strict three-object gate, retained only for historical diagnostics. */
        fun historicalStrict(thresholds: VisionThresholds = VisionThresholds()) = VerificationPolicy(
            name = "historical-strict-three-object",
            strategy = VerificationStrategy.SIMULTANEOUS,
            thresholds = thresholds,
            requiredConsecutiveFrames = 3,
        )

        val production = sessionAccumulated
    }
}

data class VerificationProgress(
    val consecutiveValidFrames: Int,
    val passed: Boolean,
    val sinkDetected: Boolean = false,
    val dentalDetected: Boolean = false,
    val freshFrames: Int = 0,
)

/** Evaluates a sequence of fresh in-memory camera scores for one verification attempt. */
class VerificationEvaluator(private val policy: VerificationPolicy) {
    constructor(thresholds: VisionThresholds, requiredConsecutiveFrames: Int = 3) : this(
        VerificationPolicy.historicalStrict(thresholds).copy(requiredConsecutiveFrames = requiredConsecutiveFrames),
    )

    private val thresholds = policy.thresholds.sanitized()
    private var consecutive = 0
    private var sinkEvidenceAt: Long? = null
    private var dentalEvidenceAt: Long? = null
    private var firstEvidenceAt: Long? = null
    private var lastFrameAt: Long? = null
    private var freshFrames = 0
    private var complete = false

    fun accept(frame: VisionFrame, nowMs: Long = 0L): VerificationProgress {
        if (complete) return progress()
        if (!frame.hasValidScores() || lastFrameAt?.let { nowMs < it } == true) {
            reset()
            return progress()
        }

        return when (policy.strategy) {
            VerificationStrategy.SIMULTANEOUS -> {
                lastFrameAt = nowMs
                val sink = frame.sink >= thresholds.sink
                val dental = frame.toothbrush >= thresholds.toothbrush && frame.toothpaste >= thresholds.toothpaste
                val qualifying = sink && dental
                consecutive = if (qualifying) {
                    (consecutive + 1).coerceAtMost(policy.requiredConsecutiveFrames)
                } else {
                    0
                }
                complete = consecutive >= policy.requiredConsecutiveFrames
                VerificationProgress(consecutive, complete, sink, dental, consecutive)
            }

            VerificationStrategy.SINK_ANCHORED -> {
                lastFrameAt = nowMs
                val sink = frame.sink >= thresholds.sink
                val dental = frame.toothbrush >= thresholds.toothbrush || frame.toothpaste >= thresholds.toothpaste
                val qualifying = sink && dental
                consecutive = if (qualifying) {
                    (consecutive + 1).coerceAtMost(policy.requiredConsecutiveFrames)
                } else {
                    0
                }
                complete = consecutive >= policy.requiredConsecutiveFrames
                VerificationProgress(consecutive, complete, sink, dental, consecutive)
            }

            VerificationStrategy.SESSION_ACCUMULATED -> acceptAccumulated(frame, nowMs)
        }
    }

    private fun acceptAccumulated(frame: VisionFrame, nowMs: Long): VerificationProgress {
        val first = firstEvidenceAt
        if (first != null && (nowMs < first || nowMs - first > policy.evidenceWindowMs)) {
            resetEvidence()
        }

        val sinkNow = frame.sink >= thresholds.sink
        val brushNow = frame.toothbrush >= thresholds.toothbrush
        val pasteNow = frame.toothpaste >= thresholds.toothpaste
        if (sinkNow || brushNow || pasteNow) {
            if (firstEvidenceAt == null) firstEvidenceAt = nowMs
            freshFrames++
            if (sinkNow) sinkEvidenceAt = nowMs
            if (brushNow || pasteNow) dentalEvidenceAt = nowMs
        }
        lastFrameAt = nowMs

        val windowStart = firstEvidenceAt
        val sink = sinkEvidenceAt?.let { windowStart != null && it >= windowStart && nowMs - it <= policy.evidenceWindowMs } == true
        val dental = dentalEvidenceAt?.let { windowStart != null && it >= windowStart && nowMs - it <= policy.evidenceWindowMs } == true
        complete = sink && dental && freshFrames >= policy.requiredFreshFrames
        return VerificationProgress(freshFrames, complete, sink, dental, freshFrames)
    }

    fun reset() {
        consecutive = 0
        resetEvidence()
        lastFrameAt = null
        complete = false
    }

    private fun resetEvidence() {
        sinkEvidenceAt = null
        dentalEvidenceAt = null
        firstEvidenceAt = null
        freshFrames = 0
    }

    private fun progress(): VerificationProgress {
        val sink = sinkEvidenceAt != null
        val dental = dentalEvidenceAt != null
        return VerificationProgress(consecutive, complete, sink, dental, freshFrames)
    }
}

/** A camera attempt owns one monotonic stream of frames on the UI thread. */
internal class VerificationAttempt(policy: VerificationPolicy) {
    constructor(thresholds: VisionThresholds) : this(VerificationPolicy.historicalStrict(thresholds))

    private val evaluator = policy.newEvaluator()
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
        if (generation == this.generation) {
            evaluator.reset()
            latestArrivalMs = null
        }
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
            latestArrivalMs = null
            return null
        }
        if (latestArrivalMs?.let { arrivalMs - it !in 1..MAX_FRAME_GAP_MS } == true) {
            evaluator.reset()
        }
        latestArrivalMs = arrivalMs
        return evaluator.accept(frame, arrivalMs).also { complete = it.passed }
    }

    private companion object {
        const val MAX_FRAME_AGE_MS = 3_000L
        const val MAX_FRAME_GAP_MS = 4_000L
    }
}
