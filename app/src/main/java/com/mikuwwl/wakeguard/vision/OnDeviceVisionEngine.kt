package com.mikuwwl.wakeguard.vision

import android.content.Context
import android.graphics.Bitmap
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
            .setMaxResults(10)
            .setCategoryAllowlist(listOf("sink", "toothbrush"))
            .setScoreThreshold(0.25f)
            .build()
        detector = ObjectDetector.createFromOptions(appContext, detectorOptions)
        try {
            val model = appContext.assets.open("toothpaste_classifier.onnx").use { it.readBytes() }
            session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                environment.createSession(model, options)
            }
        } catch (error: Exception) {
            detector.close()
            throw error
        }
        inputName = session.inputNames.first()
    }

    override fun analyze(bitmap: Bitmap): VisionFrame {
        var sink = 0f
        var toothbrush = 0f
        fun detect(input: Bitmap) {
            val image = BitmapImageBuilder(input).build()
            try {
                for (detection in detector.detect(image).detections()) {
                    for (category in detection.categories()) {
                        when (category.categoryName()) {
                            "sink" -> sink = maxOf(sink, category.score())
                            "toothbrush" -> toothbrush = maxOf(toothbrush, category.score())
                        }
                    }
                }
            } finally {
                image.close()
            }
        }
        val entireView = letterbox(bitmap, DETECTOR_SIZE)
        try {
            detect(entireView)
        } finally {
            entireView.recycle()
        }
        for (tile in detectorTiles(bitmap.width, bitmap.height)) {
            val crop = productCrop(bitmap, tile, DETECTOR_SIZE)
            try {
                detect(crop)
            } finally {
                crop.recycle()
            }
        }
        var toothpaste = 0f
        for (region in productCropRegions(bitmap.width, bitmap.height)) {
            val crop = productCrop(bitmap, region, INPUT_SIZE)
            try {
                toothpaste = maxOf(toothpaste, classifyToothpaste(crop))
            } finally {
                crop.recycle()
            }
        }
        return VisionFrame(sink, toothbrush, toothpaste)
    }

    private fun classifyToothpaste(bitmap: Bitmap): Float {
        val input = normalizedRgb(bitmap)
        val shape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        return OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), shape).use { tensor ->
            session.run(mapOf(inputName to tensor)).use { result ->
                val logits = (result[0].value as Array<*>).single() as FloatArray
                check(logits.size == 28 && logits.all(Float::isFinite)) { "Invalid classifier output" }
                val peak = logits.max()
                val probabilities = logits.map { exp((it - peak).toDouble()) }
                ((probabilities[20] + probabilities[21]) / probabilities.sum()).toFloat()
            }
        }
    }

    override fun close() {
        try {
            detector.close()
        } finally {
            session.close()
        }
    }

    companion object {
        private const val INPUT_SIZE = 224
        private const val DETECTOR_SIZE = 320
    }
}
