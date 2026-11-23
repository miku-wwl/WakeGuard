package com.mikuwwl.wakeguard.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector.ObjectDetectorOptions
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.mikuwwl.wakeguard.domain.VisionFrame
import java.nio.FloatBuffer
import kotlin.math.exp

/** CPU-only detector + local classifier. Photos stay in memory and are never written to disk. */
interface VisionEngine : AutoCloseable {
    fun analyze(bitmap: Bitmap): VisionFrame
}

class OnDeviceVisionEngine(context: Context) : VisionEngine {
    private val detector: ObjectDetector
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val inputName: String

    init {
        val appContext = context.applicationContext
        val detectorOptions = ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("efficientdet_lite0.tflite").build())
            .setRunningMode(RunningMode.IMAGE)
            .setMaxResults(20)
            .setScoreThreshold(0.25f)
            .build()
        detector = ObjectDetector.createFromOptions(appContext, detectorOptions)
        val model = appContext.assets.open("toothpaste_classifier.onnx").use { it.readBytes() }
        session = environment.createSession(model, OrtSession.SessionOptions())
        inputName = session.inputNames.first()
    }

    override fun analyze(bitmap: Bitmap): VisionFrame {
        val detections = detector.detect(BitmapImageBuilder(bitmap).build()).detections()
        var sink = 0f
        var toothbrush = 0f
        for (detection in detections) {
            for (category in detection.categories()) {
                when (category.categoryName().trim().lowercase()) {
                    "sink" -> sink = maxOf(sink, category.score())
                    "toothbrush" -> toothbrush = maxOf(toothbrush, category.score())
                }
            }
        }
        val crops = candidateCrops(bitmap)
        var toothpaste = 0f
        try {
            crops.forEach { crop -> toothpaste = maxOf(toothpaste, classifyToothpaste(crop)) }
        } finally {
            crops.forEach { crop -> if (crop !== bitmap && !crop.isRecycled) crop.recycle() }
        }
        return VisionFrame(sink = sink, toothbrush = toothbrush, toothpaste = toothpaste)
    }

    private fun candidateCrops(bitmap: Bitmap): List<Bitmap> {
        val width = bitmap.width
        val height = bitmap.height
        val cropWidth = (width * 0.62f).toInt().coerceAtLeast(1)
        val cropHeight = (height * 0.62f).toInt().coerceAtLeast(1)
        val positions = listOf(
            Rect(0, 0, width, height),
            Rect(0, 0, cropWidth, cropHeight),
            Rect(width - cropWidth, 0, width, cropHeight),
            Rect(0, height - cropHeight, cropWidth, height),
            Rect(width - cropWidth, height - cropHeight, width, height),
        )
        return positions.map { rect -> Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height()) }
    }

    private fun classifyToothpaste(bitmap: Bitmap): Float {
        val input = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val scaled = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        try {
            var pixelIndex = 0
            for (channel in 0..2) {
                val mean = MEAN[channel]
                val std = STD[channel]
                for (y in 0 until INPUT_SIZE) {
                    for (x in 0 until INPUT_SIZE) {
                        val pixel = scaled.getPixel(x, y)
                        val value = when (channel) {
                            0 -> (pixel shr 16) and 0xff
                            1 -> (pixel shr 8) and 0xff
                            else -> pixel and 0xff
                        } / 255f
                        input[pixelIndex++] = (value - mean) / std
                    }
                }
            }
            val tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong()))
            return tensor.use { value ->
                session.run(mapOf(inputName to value)).use { result ->
                    val logits = ((result[0].value as? Array<*>)?.firstOrNull() as? FloatArray) ?: return@use 0f
                    val peak = logits.maxOrNull() ?: return@use 0f
                    val probabilities = logits.map { exp((it - peak).toDouble()) }
                    val denominator = probabilities.sum()
                    ((probabilities[20] + probabilities[21]) / denominator).toFloat()
                }
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    override fun close() {
        detector.close()
        session.close()
    }

    companion object {
        private const val INPUT_SIZE = 224
        private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
        private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)
    }
}
