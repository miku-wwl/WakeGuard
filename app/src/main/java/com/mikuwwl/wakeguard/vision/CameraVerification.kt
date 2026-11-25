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
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mikuwwl.wakeguard.R
import com.mikuwwl.wakeguard.domain.VerificationAttempt
import com.mikuwwl.wakeguard.domain.VisionFrame
import com.mikuwwl.wakeguard.domain.VerificationPolicy
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun CameraVerificationScreen(
    eventId: Long,
    policy: VerificationPolicy,
    onVerified: () -> Unit,
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
    val attempt = remember(eventId, policy) { VerificationAttempt(policy) }
    var failure by remember { mutableStateOf<String?>(null) }
    var sinkDetected by remember { mutableStateOf(false) }
    var dentalDetected by remember { mutableStateOf(false) }
    var freshFrames by remember { mutableStateOf(0) }
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
        sinkDetected = false
        dentalDetected = false
        freshFrames = 0
        failure = null
    }
    BackHandler(enabled = true) {
        resetAttempt()
        onBackToAlarm()
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
                    androidx.compose.material3.Icon(painterResource(R.drawable.ic_camera), null, tint = Color.White)
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
            val previewView = remember(context) {
                PreviewView(context).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
            }
            var previewSize by remember { mutableStateOf(IntSize.Zero) }
            AndroidView(
                factory = { previewView },
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clipToBounds()
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
                                sinkDetected = progress.sinkDetected
                                dentalDetected = progress.dentalDetected
                                freshFrames = progress.freshFrames
                                failure = null
                                if (progress.passed) currentOnVerified()
                            }
                        }
                    },
                    onFailure = { message ->
                        if (currentGeneration == attempt.generation) {
                            attempt.failure(currentGeneration)
                            sinkDetected = false
                            dentalDetected = false
                            freshFrames = 0
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
                    Text("请前往洗手池附近", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    VerificationStatus("洗手池", sinkDetected, "vision_sink_status")
                    VerificationStatus("牙刷或牙膏", dentalDetected, "vision_dental_status")
                    Text(
                        if (sinkDetected && dentalDetected) "正在确认连续实时画面（$freshFrames 帧）…" else "将洗手池或洗漱用品对准摄像头。",
                        color = Color(0xFFB7C9C8),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        failure ?: "只使用本次闹钟中的新鲜相机画面；返回或切到后台后需要重新识别。",
                        color = if (failure == null) Color(0xFFB7C9C8) else Color(0xFFFFB4AB),
                        minLines = 2,
                        style = MaterialTheme.typography.bodySmall,
                    )
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
private fun VerificationStatus(label: String, found: Boolean, testTag: String) {
    Row(Modifier.fillMaxWidth().testTag(testTag), verticalAlignment = Alignment.CenterVertically) {
        Text(if (found) "✓" else "○", color = if (found) Color(0xFF8FE1B1) else Color(0xFFFFD08A))
        Text("  $label", color = Color.White, modifier = Modifier.weight(1f))
        Text(if (found) "已检测到" else "等待画面", color = Color(0xFFB7C9C8))
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
                    .setScaleType(ViewPort.FIT).build()
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
            try {
                analysis.clearAnalyzer()
                cameraProvider?.unbind(preview, analysis)
            } finally {
                executor.execute { analyzer.close() }
                executor.shutdown()
                onStopped()
            }
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
            val elapsedMs = (SystemClock.elapsedRealtime() - now).coerceAtLeast(0L)
            android.util.Log.i(
                "WakeGuardVision",
                "event camera frame=$timestamp latencyMs=$elapsedMs sink=${result.sink} toothbrush=${result.toothbrush} toothpaste=${result.toothpaste}",
            )
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
