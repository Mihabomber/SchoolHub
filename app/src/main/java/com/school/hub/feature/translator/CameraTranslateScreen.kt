package com.school.hub.feature.translator

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CameraTranslateScreen(vm: TranslatorViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.processUri(uri) }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }
    DisposableEffect(Unit) { onDispose { vm.closeCamera() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val result = vm.camera
        when {
            result != null -> TranslatedOverlay(result)
            granted -> CameraPreview(onCapture = { vm.processBitmap(it) }, busy = vm.cameraBusy)
            else -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Нужен доступ к камере", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Разрешить") }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Text("Или выбрать фото из галереи", color = Color.White)
                }
            }
        }

        // верхняя панель
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                Text(
                    "${if (vm.source == AUTO) "Авто" else vm.repo.displayName(vm.source)} → ${vm.repo.displayName(vm.target)}",
                    color = Color.White, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        if (vm.cameraBusy) {
            Surface(Modifier.align(Alignment.Center), shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.7f)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Распознаю и перевожу…", color = Color.White)
                }
            }
        }

        // нижняя панель
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val clipboard = LocalClipboardManager.current
            FilledIconButton(
                onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                modifier = Modifier.size(56.dp),
            ) { Icon(Icons.Filled.PhotoLibrary, "Галерея") }
            if (result != null) {
                Button(onClick = { vm.closeCamera() }, modifier = Modifier.height(56.dp), shape = RoundedCornerShape(28.dp)) {
                    Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Снова")
                }
                FilledIconButton(
                    onClick = { clipboard.setText(AnnotatedString(result.blocks.joinToString("\n") { it.translated.ifBlank { it.text } })) },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = 0.2f), contentColor = Color.White),
                    modifier = Modifier.size(56.dp),
                ) { Icon(Icons.Filled.ContentCopy, "Копировать перевод") }
            } else {
                Spacer(Modifier.size(56.dp))
                Spacer(Modifier.size(56.dp))
            }
        }
    }

    vm.status?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(3000); vm.clearStatus() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Surface(Modifier.statusBarsPadding().padding(top = 64.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Text(msg, color = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun CameraPreview(onCapture: (android.graphics.Bitmap) -> Unit, busy: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Text(
            "Наведи на текст и нажми кнопку",
            color = Color.White,
            modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(10.dp),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                .size(80.dp).border(4.dp, Color.White, CircleShape).padding(8.dp)
                .background(if (busy) Color.Gray else Color.White, CircleShape),
        ) {
            IconButton(onClick = { if (!busy) previewView.bitmap?.let(onCapture) }, modifier = Modifier.fillMaxSize()) {}
        }
    }
}

@Composable
private fun TranslatedOverlay(result: CameraResult) {
    var showOriginal by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = result.bitmap.width.toFloat()
        val bh = result.bitmap.height.toFloat()
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val scale = min(cw / bw, ch / bh)
        val dx = (cw - bw * scale) / 2f
        val dy = (ch - bh * scale) / 2f

        Image(result.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (!showOriginal) {
            result.blocks.forEach { b ->
                val left = dx + b.rect.left * scale
                val top = dy + b.rect.top * scale
                val w = max(b.rect.width() * scale, 40f)
                val h = max(b.rect.height() * scale, 24f)
                val fontPx = (h / b.lines) * 0.6f
                with(density) {
                    Box(
                        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                            .width(w.toDp()).heightIn(min = h.toDp())
                            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
                            .padding(2.dp),
                    ) {
                        Text(
                            b.translated.ifBlank { "…" },
                            color = Color.Black,
                            fontSize = fontPx.coerceIn(20f, 64f).toSp(),
                            lineHeight = (fontPx.coerceIn(20f, 64f) * 1.1f).toSp(),
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }
        FilledIconButton(
            onClick = { showOriginal = !showOriginal },
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 64.dp, end = 12.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
        ) { Icon(if (showOriginal) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Оригинал") }
    }
}
