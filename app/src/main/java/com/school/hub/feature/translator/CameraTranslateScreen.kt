package com.school.hub.feature.translator

import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.ui.graphics.luminance
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

        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FilterChip(
                selected = vm.aiPhoto, onClick = { vm.aiPhoto = !vm.aiPhoto },
                label = { Text(if (vm.aiPhoto) "🤖 Перевод через ИИ" else "⚡ Мгновенный перевод", color = Color.White) },
                colors = FilterChipDefaults.filterChipColors(containerColor = Color.Black.copy(alpha = 0.5f), selectedContainerColor = Color(0xCC5B4CF0)),
            )
            if (vm.aiPhoto) Text("ИИ переводит точнее, но это может занять некоторое время.\nБез ИИ перевод моментальный.",
                color = Color.White, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(8.dp))
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
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    // Полноразмерный снимок (а не скриншот превью) — OCR намного точнее
    val capture = remember {
        androidx.camera.core.ImageCapture.Builder()
            .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var shooting by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
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
                .background(if (busy || shooting) Color.Gray else Color.White, CircleShape),
        ) {
            IconButton(onClick = {
                if (busy || shooting) return@IconButton
                shooting = true
                capture.takePicture(ContextCompat.getMainExecutor(context), object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        val rot = image.imageInfo.rotationDegrees
                        val raw = runCatching { image.toBitmap() }.getOrNull()
                        image.close()
                        shooting = false
                        val bmp = raw?.let {
                            val k = 2048f / maxOf(it.width, it.height)
                            val m = android.graphics.Matrix().apply { if (k < 1f) postScale(k, k); postRotate(rot.toFloat()) }
                            android.graphics.Bitmap.createBitmap(it, 0, 0, it.width, it.height, m, true)
                        } ?: previewView.bitmap
                        bmp?.let(onCapture)
                    }
                    override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                        shooting = false
                        previewView.bitmap?.let(onCapture)
                    }
                })
            }, modifier = Modifier.fillMaxSize()) {}
        }
    }
}

/** Средний цвет области — чтобы плашка с переводом «сливалась» с фоном, как в Google Lens. */
private fun avgColor(b: android.graphics.Bitmap, r: android.graphics.Rect): Color {
    var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
    val stepX = (r.width() / 8).coerceAtLeast(1); val stepY = (r.height() / 4).coerceAtLeast(1)
    var y = r.top.coerceAtLeast(0)
    while (y < r.bottom.coerceAtMost(b.height)) {
        var x = r.left.coerceAtLeast(0)
        while (x < r.right.coerceAtMost(b.width)) {
            val c = b.getPixel(x, y); rs += (c shr 16) and 255; gs += (c shr 8) and 255; bs += c and 255; n++
            x += stepX
        }
        y += stepY
    }
    if (n == 0) return Color.White
    return Color((rs / n).toInt(), (gs / n).toInt(), (bs / n).toInt())
}

@Composable
private fun AutoFitText(text: String, color: Color, maxSp: Float, modifier: Modifier) {
    var size by remember(text, maxSp) { mutableStateOf(maxSp) }
    Text(
        text, modifier, color = color, fontSize = size.sp, lineHeight = (size * 1.15f).sp,
        onTextLayout = { if (it.hasVisualOverflow && size > 7f) size *= 0.9f },
    )
}

@Composable
private fun TranslatedOverlay(result: CameraResult) {
    var showOriginal by remember { mutableStateOf(false) }
    var asList by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val colors = remember(result.bitmap, result.blocks.size) { result.blocks.map { avgColor(result.bitmap, it.rect) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bw = result.bitmap.width.toFloat()
        val bh = result.bitmap.height.toFloat()
        val cw = constraints.maxWidth.toFloat()
        val ch = constraints.maxHeight.toFloat()
        val scale = min(cw / bw, ch / bh)
        val dx = (cw - bw * scale) / 2f
        val dy = (ch - bh * scale) / 2f

        Image(result.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        if (!showOriginal && !asList) {
            result.blocks.forEachIndexed { i, b ->
                val left = dx + b.rect.left * scale
                val top = dy + b.rect.top * scale
                val w = max(b.rect.width() * scale, 40f)
                val h = max(b.rect.height() * scale, 20f)
                val bg = colors.getOrElse(i) { Color.White }
                val fg = if (bg.luminance() > 0.5f) Color.Black else Color.White
                with(density) {
                    Box(
                        Modifier.offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                            .size(w.toDp(), h.toDp())
                            .background(bg.copy(alpha = 0.96f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        AutoFitText(b.translated.ifBlank { "…" }, fg, (h / b.lines * 0.75f).toSp().value.coerceIn(8f, 40f), Modifier.fillMaxSize())
                    }
                }
            }
        }
        if (asList) {
            Column(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).statusBarsPadding().padding(top = 110.dp, bottom = 120.dp)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                result.blocks.forEach { b ->
                    Text(b.text, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
                    Text(b.translated.ifBlank { "…" }, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Column(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 110.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledIconButton(
                onClick = { showOriginal = !showOriginal },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(if (showOriginal) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, "Оригинал") }
            FilledIconButton(
                onClick = { asList = !asList },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f), contentColor = Color.White),
            ) { Icon(Icons.AutoMirrored.Filled.List, "Списком") }
        }
    }
}
