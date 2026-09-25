package com.school.hub.feature.calc

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.school.hub.AppContainer
import com.school.hub.core.ui.components.rememberPhotoActions
import com.school.hub.core.util.markdown
import com.school.hub.feature.ai.models.ModelCatalog
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CalculatorViewModel(private val c: AppContainer) : ViewModel() {
    var expr by mutableStateOf("")
        private set
    var preview by mutableStateOf("")
        private set
    var solution by mutableStateOf<Solution?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf<String?>(null)
        private set
    var aiMode by mutableStateOf(false)
    var aiAnswer by mutableStateOf("")
        private set

    fun press(k: String) {
        error = null; solution = null
        expr = when (k) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "√" -> "$expr√("
            "sin", "cos", "tan", "log", "ln" -> "$expr$k("
            "π" -> "${expr}π"
            else -> expr + k
        }
        preview = MathEngine.evalOrNull(autoClose(expr))?.let { "= ${fmt(it)}" } ?: ""
    }

    fun edit(v: String) { expr = v; error = null; solution = null; preview = MathEngine.evalOrNull(autoClose(v))?.let { "= ${fmt(it)}" } ?: "" }

    private fun autoClose(s: String): String {
        val open = s.count { it == '(' } - s.count { it == ')' }
        return s + ")".repeat(open.coerceAtLeast(0))
    }

    fun equals() {
        runCatching { MathEngine.solve(autoClose(expr)) }
            .onSuccess { solution = it; expr = it.resultText; preview = ""; c.stats.inc("calc") }
            .onFailure { error = it.message ?: "Ошибка" }
    }

    fun photo(uri: Uri) = viewModelScope.launch {
        error = null; solution = null; aiAnswer = ""
        val bmp = c.imageStorage.decodeBitmap(uri, 2048) ?: run { error = "Не удалось открыть фото"; return@launch }
        if (aiMode) return@launch solveWithVision(bmp)
        busy = "Распознаю пример…"
        val text = runCatching {
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try { rec.process(InputImage.fromBitmap(bmp, 0)).await() } finally { rec.close() }
        }.getOrNull()
        busy = null
        // Берём строку, больше всего похожую на пример
        val line = text?.textBlocks?.flatMap { it.lines }?.map { it.text }
            ?.maxByOrNull { l -> l.count { it.isDigit() || it in "+-−×x*/÷:()^=" } }
        if (line.isNullOrBlank()) { error = "На фото не нашёл пример. Сфотографируй ближе и ровнее."; return@launch }
        expr = MathEngine.normalize(line)
        equals()
        if (error != null) error = "Распознал «$line», но: $error. Поправь пример вручную."
    }

    private suspend fun solveWithVision(bmp: android.graphics.Bitmap) {
        val eng = c.llmEngine
        if (!eng.visionBuilt) { error = "❌ В этой сборке приложения нет «зрения» ИИ. Выключи режим «ИИ-зрение» — обычный калькулятор решит пример по фото мгновенно."; return }
        var model = eng.loadedModelId?.let(ModelCatalog::byId)
        if (model == null || !model.vision || !eng.visionLoaded) {
            val loadedName = model?.name
            val candidate = ModelCatalog.models.filter { it.vision && c.modelDownloader.isReady(it) && c.modelDownloader.isVisionReady(it) }.maxByOrNull { it.smart }
            if (candidate == null) {
                error = if (loadedName != null)
                    "👁❌ Сейчас включена модель «$loadedName» — она читает только ТЕКСТ с фото (OCR) и не видит картинку целиком. " +
                        "Скачай модель с пометкой «👁 Видит картинку целиком» (Gemma 4, Gemma 3 4B, Qwen 2.5 VL) во вкладке ИИ или выключи режим «ИИ-зрение»."
                else "👁❌ Нет скачанной модели со зрением. Скачай во вкладке ИИ модель с пометкой «👁 Видит картинку целиком» или выключи режим «ИИ-зрение»."
                return
            }
            busy = "Загружаю ${candidate.name}…"
            val ok = eng.load(candidate.id, c.modelDownloader.file(candidate), candidate.format, candidate.contextSize) &&
                eng.loadVision(c.modelDownloader.mmprojFile(candidate))
            busy = null
            if (!ok) { error = "Не удалось загрузить «${candidate.name}» (мало памяти?)"; return }
            model = candidate
        }
        busy = "ИИ смотрит на фото… (может занять до минуты)"
        runCatching {
            eng.generateWithImage(
                "Ты — учитель математики. Отвечай по-русски." + model.systemSuffix,
                "Перепиши пример с картинки, затем реши его по шагам и в конце напиши «Ответ: …».",
                bmp,
            ).collect { aiAnswer = it }
        }.onFailure { error = it.message }
        busy = null
        c.stats.inc("calc")
    }
}

private val keys = listOf(
    "C", "(", ")", "⌫", "÷",
    "sin", "7", "8", "9", "×",
    "cos", "4", "5", "6", "−",
    "√", "1", "2", "3", "+",
    "^", "π", "0", ".", "=",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorScreen(onBack: () -> Unit, vm: CalculatorViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val photo = rememberPhotoActions(vm::photo)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Калькулятор") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = {
                IconButton(onClick = photo.camera) { Icon(Icons.Filled.CameraAlt, "Сфотографировать пример") }
                IconButton(onClick = photo.gallery) { Icon(Icons.Filled.PhotoLibrary, "Фото из галереи") }
            },
        )
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = vm.aiMode, onClick = { vm.aiMode = !vm.aiMode },
                    label = { Text(if (vm.aiMode) "👁 ИИ-зрение: вкл" else "👁 ИИ-зрение: выкл") })
                Spacer(Modifier.width(8.dp))
                Text(if (vm.aiMode) "фото смотрит ИИ (медленнее, точнее)" else "фото → мгновенное распознавание",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Bottom) {
                vm.busy?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  $it") } }
                vm.error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), shape = RoundedCornerShape(16.dp)) {
                        Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                if (vm.aiAnswer.isNotBlank()) Card(shape = RoundedCornerShape(16.dp)) { Text(markdown(vm.aiAnswer), Modifier.padding(12.dp)) }
                vm.solution?.let { s ->
                    AnimatedVisibility(true) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Решение по шагам", fontWeight = FontWeight.Bold)
                                s.steps.forEachIndexed { i, st -> Text(if (i == 0) st else "= $st", fontFamily = com.school.hub.core.ui.theme.AppFonts.mono) }
                                Text("Ответ: ${s.resultText}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                BasicDisplay(vm.expr, vm::edit)
                Text(vm.preview, Modifier.fillMaxWidth(), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 22.sp)
            }
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(keys) { k ->
                    val accent = k in listOf("÷", "×", "−", "+", "^")
                    val colors = when {
                        k == "=" -> ButtonDefaults.buttonColors()
                        k == "C" -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        accent -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        else -> ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
                    }
                    Button(
                        onClick = { if (k == "=") vm.equals() else vm.press(if (k == "−") "-" else k) },
                        modifier = Modifier.height(58.dp), shape = RoundedCornerShape(18.dp), colors = colors, contentPadding = PaddingValues(0.dp),
                    ) { Text(k, fontSize = if (k.length > 1) 15.sp else 22.sp) }
                }
            }
        }
    }
}

@Composable
private fun BasicDisplay(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(),
        textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.End, fontFamily = com.school.hub.core.ui.theme.AppFonts.mono),
        placeholder = { Text("2 × (3 + 4)^2", Modifier.fillMaxWidth(), textAlign = TextAlign.End) },
        shape = RoundedCornerShape(20.dp), singleLine = true,
    )
}
