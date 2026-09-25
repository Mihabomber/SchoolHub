package com.school.hub.feature.ai.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.tasks.await
import com.school.hub.AppContainer
import com.school.hub.feature.ai.engine.LlmEngine
import com.school.hub.feature.ai.models.ModelCatalog
import com.school.hub.feature.ai.models.ModelDownloader
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatMessage(val fromUser: Boolean, val text: String, val image: android.graphics.Bitmap? = null)

enum class ChatMode(val title: String, val emoji: String, val system: String) {
    HELPER("Помощник", "🤖", "Ты — дружелюбный помощник школьника. Отвечай по-русски, кратко и понятно."),
    SIMPLE("Объясни просто", "🧒", "Ты объясняешь темы школьной программы максимально просто, как другу, с примерами из жизни. Отвечай по-русски."),
    STEPS("Реши по шагам", "🧮", "Ты — репетитор по математике и физике. Решай задачи по шагам, каждый шаг с коротким пояснением, в конце — ответ. Отвечай по-русски."),
    CHECK("Проверь текст", "✍️", "Ты — учитель русского языка. Найди ошибки в тексте ученика, объясни правила и дай исправленный вариант."),
    ENGLISH("English tutor", "🇬🇧", "You are a friendly English tutor for a Russian-speaking student. Answer in simple English and add short Russian explanations when useful."),
    QUIZ("Проверь меня", "❓", "Ты задаёшь ученику вопросы по теме, которую он назовёт, по одному за раз, и проверяешь ответы. Отвечай по-русски."),
}

data class ChatUiState(
    val modelName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val mode: ChatMode = ChatMode.HELPER,
    val tokensPerSec: Float = 0f,
)

class AiChatViewModel(
    handle: SavedStateHandle,
    private val engine: LlmEngine,
    downloader: ModelDownloader,
    private val container: AppContainer,
) : ViewModel() {
    var ocrBusy by mutableStateOf(false)
        private set
    /** Фото для «настоящего» зрения (если модель его умеет). */
    var pendingImage by mutableStateOf<android.graphics.Bitmap?>(null)
        private set
    fun clearImage() { pendingImage = null }
    val canSee: Boolean get() = engine.visionLoaded

    /** Прикрепить фото: текст распознаётся ML Kit прямо на телефоне и добавляется к вопросу. */
    fun attachImage(uri: android.net.Uri) {
        viewModelScope.launch {
            ocrBusy = true
            if (engine.visionLoaded) {
                pendingImage = container.imageStorage.decodeBitmap(uri, 1024)
                ocrBusy = false
                if (input.isBlank()) input = "Что на картинке? Если это задание — реши по шагам."
                return@launch
            }
            val text = runCatching {
                val bmp = container.imageStorage.decodeBitmap(uri, 2048) ?: error("no image")
                val rec = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS,
                )
                try {
                    rec.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)).await().text
                } finally { rec.close() }
            }.getOrNull()?.trim().orEmpty()
            ocrBusy = false
            if (text.isBlank()) {
                _state.update { st -> st.copy(messages = st.messages + ChatMessage(false, "📷 Не удалось найти текст на фото.")) }
                return@launch
            }
            input = buildString {
                if (input.isNotBlank()) append(input.trim()).append("\n\n")
                append("Текст с фото:\n\"\"\"\n").append(text.take(3000)).append("\n\"\"\"\n")
                if (input.isBlank()) append("Объясни и реши, если это задание.")
            }
        }
    }

    private fun cleanThink(raw: String): String {
        var t = raw.replace(Regex("(?s)<think>.*?</think>"), "")
        val open = t.indexOf("<think>")
        if (open >= 0) t = t.substring(0, open) + if (t.substring(0, open).isBlank()) "🤔 думает…" else ""
        return t.trimStart()
    }

    private val model = ModelCatalog.byId(handle.get<String>("modelId") ?: "")
    private val _state = MutableStateFlow(ChatUiState(modelName = model?.name ?: "?"))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    var input by mutableStateOf(container.pendingAiPrompt ?: "")
    private var job: Job? = null

    init {
        container.pendingAiPrompt = null
        viewModelScope.launch {
            val m = model
            when {
                m == null -> _state.update { it.copy(loading = false, error = "Модель не найдена") }
                !engine.isAvailable -> _state.update { it.copy(loading = false, error = "ИИ-движок не встроен в эту сборку приложения") }
                else -> {
                    val ok = runCatching { engine.load(m.id, downloader.file(m), m.format, m.contextSize) }.getOrDefault(false)
                    if (ok && m.vision && downloader.isVisionReady(m)) runCatching { engine.loadVision(downloader.mmprojFile(m)) }
                    _state.update {
                        if (ok) it.copy(loading = false)
                        else it.copy(loading = false, error = "Не удалось загрузить модель. Возможно, не хватает памяти — попробуй модель поменьше.")
                    }
                }
            }
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || _state.value.generating || _state.value.loading || _state.value.error != null) return
        input = ""
        container.stats.inc("ai")
        val img = pendingImage
        pendingImage = null
        _state.update { it.copy(messages = it.messages + ChatMessage(true, text, img) + ChatMessage(false, ""), generating = true) }
        job = viewModelScope.launch {
            val start = System.currentTimeMillis()
            var chunks = 0
            runCatching {
                val sys = _state.value.mode.system + (model?.systemSuffix ?: "")
                (if (img != null) engine.generateWithImage(sys, text, img) else engine.generate(sys, text)).collect { partial ->
                    chunks++
                    val secs = (System.currentTimeMillis() - start) / 1000f
                    _state.update { s ->
                        s.copy(
                            messages = s.messages.dropLast(1) + ChatMessage(false, cleanThink(partial)),
                            tokensPerSec = if (secs > 0.5f) chunks / secs else s.tokensPerSec,
                        )
                    }
                }
            }.onFailure { e ->
                _state.update { s -> s.copy(messages = s.messages.dropLast(1) + ChatMessage(false, "⚠️ ${e.message}")) }
            }
            _state.update { s ->
                val last = s.messages.lastOrNull()
                val fixed = if (last != null && !last.fromUser && last.text.isBlank()) s.messages.dropLast(1) + ChatMessage(false, "…") else s.messages
                s.copy(messages = fixed, generating = false)
            }
        }
    }

    fun stop() { engine.stop() }

    fun newChat(mode: ChatMode = _state.value.mode) {
        engine.stop()
        job?.cancel()
        viewModelScope.launch {
            engine.reset()
            _state.update { it.copy(messages = emptyList(), generating = false, mode = mode) }
        }
    }
}

private val SUGGESTIONS = listOf(
    "Объясни теорему Пифагора",
    "Как решать квадратные уравнения?",
    "Чем отличается Present Perfect от Past Simple?",
    "Придумай 5 вопросов по Великой Отечественной войне",
    "Как запомнить таблицу умножения на 7?",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AiChatScreen(onBack: () -> Unit, vm: AiChatViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(s.messages.size, s.messages.lastOrNull()?.text?.length) {
        if (s.messages.isNotEmpty()) listState.animateScrollToItem(s.messages.size)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s.modelName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                s.loading -> "загружаю в память…"
                                s.generating && s.tokensPerSec > 0 -> "думает · %.1f ток/с".format(s.tokensPerSec)
                                s.generating -> "думает…"
                                else -> "офлайн · ${s.mode.emoji} ${s.mode.title}"
                            },
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = { vm.newChat() }) { Icon(Icons.Filled.DeleteSweep, "Новый чат") } },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ChatMode.entries) { m ->
                    FilterChip(
                        selected = s.mode == m, onClick = { if (s.mode != m) vm.newChat(m) },
                        label = { Text(m.title) }, leadingIcon = { Text(m.emoji) },
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                when {
                    s.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Загружаю модель в память…")
                        Text("Первый раз это 5–30 секунд", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    s.error != null -> Text(
                        "⚠️ ${s.error}", modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (s.messages.isEmpty()) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${s.mode.emoji} Спроси что угодно", style = MaterialTheme.typography.titleLarge)
                                    Text(
                                        "Всё работает на телефоне — можно в самолёте и в метро.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    SUGGESTIONS.forEach { q ->
                                        SuggestionChip(onClick = { vm.input = q; vm.send() }, label = { Text(q) })
                                    }
                                }
                            }
                        }
                        itemsIndexed(s.messages) { i, m ->
                            val isTyping = s.generating && i == s.messages.lastIndex && !m.fromUser
                            Bubble(m, isTyping, onLongClick = { clipboard.setText(AnnotatedString(m.text)) })
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }
            vm.pendingImage?.let { img ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(img.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    Text("  👁 Модель посмотрит на фото целиком", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = vm::clearImage) { Icon(Icons.Filled.Close, "Убрать") }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    ) { uri -> if (uri != null) vm.attachImage(uri) }
                    IconButton(
                        onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !s.loading && s.error == null && !vm.ocrBusy,
                    ) {
                        if (vm.ocrBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.AddPhotoAlternate, "Фото")
                    }
                    OutlinedTextField(
                        value = vm.input, onValueChange = { vm.input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Сообщение…") },
                        maxLines = 5, shape = RoundedCornerShape(24.dp),
                        enabled = !s.loading && s.error == null,
                    )
                    Spacer(Modifier.width(8.dp))
                    if (s.generating) {
                        FilledIconButton(onClick = vm::stop, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Stop, "Стоп") }
                    } else {
                        FilledIconButton(
                            onClick = vm::send, modifier = Modifier.size(52.dp),
                            enabled = vm.input.isNotBlank() && !s.loading && s.error == null,
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: ChatMessage, typing: Boolean, onLongClick: () -> Unit) {
    val shape = if (m.fromUser) RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp) else RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 320.dp).clip(shape)
                .background(if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            m.image?.let {
                androidx.compose.foundation.Image(
                    it.asImageBitmap(), null,
                    Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Text(
                if (typing && m.text.isEmpty()) AnnotatedString("…") else com.school.hub.core.util.markdown(m.text + if (typing) " ▍" else ""),
                color = if (m.fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
            )
          }
        }
    }
}
