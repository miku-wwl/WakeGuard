package com.mikuwwl.wakeguard.vision

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Rational
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
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
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikuwwl.wakeguard.domain.VerificationAttempt
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.domain.VisionThresholds
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnVerified by rememberUpdatedState(onVerified)
    val attempt = remember(thresholds) { VerificationAttempt(thresholds) }
    var frame by remember { mutableStateOf<VisionFrame?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var consecutive by remember { mutableStateOf(0) }
    var failedAttemptsShown by remember { mutableStateOf(0) }
    var generation by remember(attempt) { mutableLongStateOf(attempt.generation) }
    var hasPermission by remember {
        mutableStateOf(cameraPermissionChecker(context))
    }
    var resumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && power.isInteractive)
    }
    fun resetAttempt() {
        attempt.invalidate()
        generation = attempt.generation
        frame = null
        consecutive = 0
        failure = null
    }
    DisposableEffect(lifecycleOwner, attempt) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasPermission = cameraPermissionChecker(context)
                    resumed = power.isInteractive
                }
                Lifecycle.Event.ON_PAUSE -> {
                    resumed = false
                    resetAttempt()
                }
                else -> Unit
            }
        }
        val displayReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        resumed = false
                        resetAttempt()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        ContextCompat.registerReceiver(
            context, displayReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            context.unregisterReceiver(displayReceiver)
            attempt.invalidate()
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
        resetAttempt()
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E1818)).safeDrawingPadding()) {
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
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            AndroidView(
                factory = { previewView },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
                    .onSizeChanged { previewSize = it }.testTag("camera_preview"),
            )
            if (resumed && previewSize.width > 0 && previewSize.height > 0) {
                val currentGeneration = generation
                CameraAnalysis(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    size = previewSize,
                    generation = currentGeneration,
                    onFrame = { timestamp, arrivalMs, scores ->
                        if (power.isInteractive &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                        ) {
                            val progress = attempt.accept(currentGeneration, timestamp, arrivalMs, SystemClock.elapsedRealtime(), scores)
                            if (progress != null) {
                                frame = scores
                                consecutive = progress.consecutiveValidFrames
                                failure = null
                                if (progress.passed) currentOnVerified()
                            }
                        }
                    },
                    onFailure = { message ->
                        if (currentGeneration == attempt.generation) {
                            attempt.failure(currentGeneration)
                            frame = null
                            consecutive = 0
                            failure = message
                        }
                    },
                    onStopped = { attempt.failure(currentGeneration) },
                    engineFactory = engineFactory,
                )
            }

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
                    failure?.let { Text(it, color = Color(0xFFFFB4AB)) }
                    Text("已连续通过 $consecutive / 3 帧。", color = Color(0xFFB7C9C8), style = MaterialTheme.typography.bodySmall)
                    if (frame != null && consecutive == 0 && failure == null) {
                        Text("在光线充足处靠近洗手台，保持三个物品都在画面内。", color = Color(0xFFB7C9C8), style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { resetAttempt(); failedAttemptsShown += 1; onRecordFailedAttempt() },
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
private fun CameraAnalysis(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    size: IntSize,
    generation: Long,
    onFrame: (Long, Long, VisionFrame) -> Unit,
    onFailure: (String) -> Unit,
    onStopped: () -> Unit,
    engineFactory: (Context) -> VisionEngine,
) {
    DisposableEffect(lifecycleOwner, previewView, size, generation) {
        val executor = Executors.newSingleThreadExecutor()
        val accepting = AtomicBoolean(true)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var cameraProvider: ProcessCameraProvider? = null
        val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val preview = Preview.Builder().setTargetRotation(rotation).build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }
        val analysis = ImageAnalysis.Builder()
            .setTargetRotation(rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
        val analyzer = WakeGuardFrameAnalyzer(context, accepting, onFrame, onFailure, engineFactory)
        providerFuture.addListener({
            if (!accepting.get() || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return@addListener
            }
            try {
                cameraProvider = providerFuture.get()
                val viewPort = ViewPort.Builder(Rational(size.width, size.height), rotation)
                    .setScaleType(ViewPort.FILL_CENTER).build()
                val useCases = UseCaseGroup.Builder().setViewPort(viewPort)
                    .addUseCase(preview).addUseCase(analysis).build()
                analysis.setAnalyzer(executor, analyzer)
                cameraProvider?.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, useCases)
            } catch (_: Exception) {
                onFailure("无法启动相机。请重试或使用紧急停止。")
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            accepting.set(false)
            analysis.clearAnalyzer()
            cameraProvider?.unbind(preview, analysis)
            executor.execute { analyzer.close() }
            executor.shutdown()
            onStopped()
        }
    }
}

private class WakeGuardFrameAnalyzer(
    context: Context,
    private val accepting: AtomicBoolean,
    private val onFrame: (Long, Long, VisionFrame) -> Unit,
    private val onFailure: (String) -> Unit,
    private val engineFactory: (Context) -> VisionEngine,
) : ImageAnalysis.Analyzer {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var engine: VisionEngine? = null
    private var lastFrameAt = 0L
    private var initializationFailed = false

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (!accepting.get() || initializationFailed || now - lastFrameAt < 700L) {
            image.close()
            return
        }
        lastFrameAt = now
        var bitmap: Bitmap? = null
        try {
            val timestamp = image.imageInfo.timestamp
            val detector = engine ?: engineFactory(appContext).also { engine = it }
            bitmap = image.toUprightBitmap()
            val result = detector.analyze(bitmap)
            mainHandler.post { if (accepting.get()) onFrame(timestamp, now, result) }
        } catch (_: Exception) {
            initializationFailed = engine == null
            mainHandler.post {
                if (accepting.get()) onFailure("本地识别暂不可用。请重试或使用紧急停止。")
            }
        } finally {
            bitmap?.recycle()
            image.close()
        }
    }

    fun close() {
        try {
            engine?.close()
        } finally {
            engine = null
        }
    }
}
