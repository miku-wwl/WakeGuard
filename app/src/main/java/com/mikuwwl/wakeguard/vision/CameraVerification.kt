package com.mikuwwl.wakeguard.vision

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikuwwl.wakeguard.domain.VerificationEvaluator
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.domain.VisionThresholds
import java.util.concurrent.Executors

@Composable
fun CameraVerificationScreen(
    thresholds: VisionThresholds,
    onVerified: () -> Unit,
    onRecordFailedAttempt: () -> Unit,
    onEmergencyStop: () -> Unit,
    onBackToAlarm: () -> Unit,
    engineFactory: (Context) -> VisionEngine = { OnDeviceVisionEngine(it) },
    cameraPermissionChecker: (Context) -> Boolean = {
        ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    },
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(cameraPermissionChecker(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = cameraPermissionChecker(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var frame by remember { mutableStateOf<VisionFrame?>(null) }
    var frameSequence by remember { mutableLongStateOf(0L) }
    var failure by remember { mutableStateOf<String?>(null) }
    var passed by remember { mutableStateOf(false) }
    var failedAttemptsShown by remember { mutableStateOf(0) }
    val evaluator = remember(thresholds) { VerificationEvaluator(thresholds) }

    LaunchedEffect(frameSequence) {
        val latest = frame ?: return@LaunchedEffect
        if (evaluator.accept(latest).passed && !passed) {
            passed = true
            onVerified()
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1818))) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBackToAlarm, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                Text(" 返回闹钟")
            }
            Spacer(Modifier.weight(1f))
            Text("拍摄验证", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }

        if (!hasPermission) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Icon(Icons.Default.CameraAlt, null, tint = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("需要相机权限进行现场识别", color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) { Text("允许相机") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
                    }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("打开应用设置") }
                }
            }
        } else {
            val previewView = remember(context) { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            CameraPreviewAndAnalysis(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                onFrame = { frame = it; frameSequence += 1L; failure = null },
                onFailure = { failure = it },
                engineFactory = engineFactory,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).testTag("camera_preview"),
            )

            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF193333)),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("请让洗手池、牙刷和牙膏同时出现在画面中", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    VisionRow("洗手池", frame?.sink, thresholds.sink, "vision_sink")
                    VisionRow("牙刷", frame?.toothbrush, thresholds.toothbrush, "vision_toothbrush")
                    VisionRow("牙膏", frame?.toothpaste, thresholds.toothpaste, "vision_toothpaste")
                    failure?.let { Text("识别暂不可用：$it", color = Color(0xFFFFB4AB)) }
                    Text("连续三帧达到门槛后才会通过。图像仅在设备内存中处理。", color = Color(0xFFB7C9C8), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(
                        onClick = { evaluator.reset(); failedAttemptsShown += 1; onRecordFailedAttempt() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    ) {
                        Text("记录本次未通过并重新扫描")
                    }
                    if (failedAttemptsShown > 0) Text("已记录未通过 $failedAttemptsShown 次", color = Color(0xFFB7C9C8), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            OutlinedButton(onClick = onEmergencyStop, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) { Text("紧急停止") }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun VisionRow(label: String, score: Float?, threshold: Float, testTag: String) {
    val found = score != null && score >= threshold
    Row(Modifier.fillMaxWidth().testTag(if (score == null) "${testTag}_waiting" else "${testTag}_score"), verticalAlignment = Alignment.CenterVertically) {
        Text(if (found) "✓" else "○", color = if (found) Color(0xFF8FE1B1) else Color(0xFFFFD08A))
        Text("  $label", color = Color.White, modifier = Modifier.weight(1f))
        Text(score?.let { "%.0f%% / %.0f%%".format(it * 100, threshold * 100) } ?: "等待画面", color = Color(0xFFB7C9C8))
    }
}

@Composable
private fun CameraPreviewAndAnalysis(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    onFrame: (VisionFrame) -> Unit,
    onFailure: (String) -> Unit,
    engineFactory: (Context) -> VisionEngine,
    modifier: Modifier = Modifier,
) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(lifecycleOwner, previewView) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        val analyzer = WakeGuardFrameAnalyzer(context, onFrame, onFailure, engineFactory)
        providerFuture.addListener({
            try {
                cameraProvider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (error: Exception) {
                Handler(Looper.getMainLooper()).post { onFailure(error.message ?: "无法启动相机") }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            cameraProvider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

private class WakeGuardFrameAnalyzer(
    context: Context,
    private val onFrame: (VisionFrame) -> Unit,
    private val onFailure: (String) -> Unit,
    private val engineFactory: (Context) -> VisionEngine,
) : ImageAnalysis.Analyzer {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: VisionEngine? = null
    private var lastFrameAt = 0L
    private var reportedError = false

    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastFrameAt < 700L) { image.close(); return }
        lastFrameAt = now
        var bitmap: Bitmap? = null
        try {
            val detector = engine ?: engineFactory(appContext).also { engine = it }
            bitmap = image.toUprightBitmap()
            val result = detector.analyze(bitmap)
            mainHandler.post { onFrame(result) }
            reportedError = false
        } catch (error: Exception) {
            if (!reportedError) {
                reportedError = true
                mainHandler.post { onFailure(error.message ?: "本地模型加载失败") }
            }
        } finally {
            bitmap?.recycle()
            image.close()
        }
    }

    fun close() { engine?.close(); engine = null }
}

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val plane = planes[0]
    val buffer = plane.buffer.duplicate()
    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val offset = y * plane.rowStride + x * plane.pixelStride
            val red = buffer.get(offset).toInt() and 0xff
            val green = buffer.get(offset + 1).toInt() and 0xff
            val blue = buffer.get(offset + 2).toInt() and 0xff
            val alpha = buffer.get(offset + 3).toInt() and 0xff
            pixels[y * width + x] = (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, width, 0, 0, width, height) }
    if (imageInfo.rotationDegrees == 0) return bitmap
    val rotation = Matrix().apply { postRotate(imageInfo.rotationDegrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, width, height, rotation, true).also { if (it !== bitmap) bitmap.recycle() }
}
