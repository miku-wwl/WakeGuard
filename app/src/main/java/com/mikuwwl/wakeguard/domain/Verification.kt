package com.mikuwwl.wakeguard.domain

data class VisionThresholds(
    val sink: Float = 0.45f,
    val toothbrush: Float = 0.45f,
    val toothpaste: Float = 0.93f,
) {
    fun sanitized(): VisionThresholds = copy(
        sink = sink.coerceIn(0.2f, 0.95f),
        toothbrush = toothbrush.coerceIn(0.2f, 0.95f),
        toothpaste = toothpaste.coerceIn(0.2f, 0.99f),
    )
}

data class VisionFrame(
    val sink: Float,
    val toothbrush: Float,
    val toothpaste: Float,
)

data class VerificationProgress(val consecutiveValidFrames: Int, val passed: Boolean)

class VerificationEvaluator(
    private val thresholds: VisionThresholds,
    private val requiredConsecutiveFrames: Int = 3,
) {
    init { require(requiredConsecutiveFrames > 0) }

    private var consecutive = 0

    fun accept(frame: VisionFrame): VerificationProgress {
        val valid = frame.sink >= thresholds.sink &&
            frame.toothbrush >= thresholds.toothbrush &&
            frame.toothpaste >= thresholds.toothpaste
        consecutive = if (valid) consecutive + 1 else 0
        return VerificationProgress(consecutive, consecutive >= requiredConsecutiveFrames)
    }

    fun reset() { consecutive = 0 }
}
