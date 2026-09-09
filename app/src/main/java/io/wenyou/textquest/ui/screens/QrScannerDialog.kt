package io.wenyou.textquest.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.wenyou.textquest.data.repo.ShareCode
import io.wenyou.textquest.ui.common.QrCode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** 全屏相机扫码：正方形识别框。大分享码分片会自动按序号累积，收齐后回调完整分享码。 */
@Composable
fun QrScannerDialog(onResult: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val config = LocalConfiguration.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g -> granted = g }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    var scanning by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("识别中…") }
    val done = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val collected = remember { ConcurrentHashMap<Int, String>() }
    val pendingTotal = remember { java.util.concurrent.atomic.AtomicInteger(0) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // 在新线程判定，成功则切回主线程上报
    fun handleDetected(text: String) {
        if (done.get()) return
        val chunk = runCatching { ShareCode.parseChunk(text) }.getOrNull()
        if (chunk != null) {
            if (collected.putIfAbsent(chunk.index, chunk.data) == null) {
                pendingTotal.set(chunk.total)
            }
            val total = pendingTotal.get()
            val got = collected.size
            if (total > 0 && got >= total) {
                val assembled = ShareCode.assembleChunks(collected.toMap(), total)
                if (assembled != null && done.compareAndSet(false, true)) {
                    scanning = false
                    mainExecutor.execute { onResult(assembled) }
                }
            } else {
                mainExecutor.execute { status = "已识别 $got/${total.coerceAtLeast(1)}，继续扫描下一张…" }
            }
        } else {
            if (done.compareAndSet(false, true)) {
                scanning = false
                mainExecutor.execute { onResult(text.trim()) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val previewView = remember { PreviewView(context) }
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            val frame = (config.screenWidthDp * 0.7f).dp
            Box(
                Modifier
                    .size(frame)
                    .align(Alignment.Center)
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
            )
            Text(
                "将二维码对准框内",
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).offset(y = frame + 20.dp)
            )
            Text(
                status,
                color = Color(0xFF8DFFA0),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
            )

            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopStart).statusBarsPadding()) {
                Icon(Icons.Filled.Close, "关闭", tint = Color.White)
            }

            if (!granted) {
                Text(
                    "需要相机权限才能扫码\n请在系统弹窗中点击「允许」",
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (granted && scanning) {
                LaunchedEffect(lifecycleOwner) {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                        analysis.setAnalyzer(analyzerExecutor) { image ->
                            try {
                                val text = QrCode.decode(image.toBitmap())
                                if (!text.isNullOrBlank()) handleDetected(text)
                            } catch (_: Throwable) {
                                // 忽略单帧解析异常，继续扫描
                            } finally {
                                image.close()
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }, ContextCompat.getMainExecutor(context))
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }
}
