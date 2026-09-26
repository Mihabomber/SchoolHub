package com.school.hub.feature.ai.ui

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import com.school.hub.core.util.MarkdownWithCode
import com.school.hub.AppContainer
import com.school.hub.feature.ai.engine.LlmEngine
import com.school.hub.feature.ai.models.*
import com.school.hub.navigation.AppViewModelFactory
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ChatMessage(val fromUser: Boolean, val text: String, val image: Bitmap? = null)

enum class ChatMode(val title: String, val emoji: String, val system: String) {
    HELPER("Помощник", "", "Ты — дружелюбный помощник школьника. Отвечай по-русски, кратко и понятно."),
    SIMPLE("Объясни просто", "", "Ты объясняешь темы школьной программы максимально просто, как другу, с примерами из жизни. Отвечай по-русски."),
    STEPS("Реши по шагам", "", "Ты — репетитор по математике и физике. Решай задачи по шагам, каждый шаг с коротким пояснением, в конце — ответ. Отвечай по-русски."),
    CHECK("Проверь текст", "", "Ты — учитель русского языка. Найди ошибки в тексте ученика, объясни правила и дай исправленный вариант."),
    ENGLISH("English tutor", "", "You are a friendly English tutor for a Russian-speaking student. Answer in simple English and add short Russian explanations when useful."),
    QUIZ("Проверь меня", "", "Ты задаёшь ученику вопросы по теме, которую он назовёт, по одному за раз, и проверяешь ответы. Отвечай по-русски.")
}

data class ChatUiState(
    val modelName: String = "",
    val loading: Boolean = true,
    val error: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val generating: Boolean = false,
    val mode: ChatMode = ChatMode.HELPER,
    val tokensPerSec: Float = 0f
)

class AiChatViewModel(
    handle: SavedStateHandle,
    private val engine: LlmEngine,
    downloader: ModelDownloader,
    private val container: AppContainer
) : ViewModel() {
    private val model = ModelCatalog.byId(handle.get<String>("modelId") ?: "")
    private val _state = MutableStateFlow(ChatUiState(modelName = model?.name ?: "?"))
    val state = _state.asStateFlow()
    var input by mutableStateOf(container.pendingAiPrompt ?: "")
    var pendingImage by mutableStateOf<Bitmap?>(null)
    private var job: Job? = null
    val canSee get() = engine.visionLoaded

    fun clearImage() { pendingImage = null }
    fun stop() { engine.stop() }
    fun newChat(mode: ChatMode = _state.value.mode) {
        engine.stop()
        job?.cancel()
        _state.update { it.copy(messages = emptyList(), generating = false, mode = mode) }
    }

    init {
        container.pendingAiPrompt = null
        viewModelScope.launch {
            val m = model
            val ok = m != null && engine.isAvailable &&
                runCatching { engine.load(m.id, downloader.file(m), m.format, m.contextSize) }.getOrDefault(false)
            _state.update { it.copy(loading = false, error = if (ok) null else "Не удалось загрузить модель") }
        }
    }

    fun send() {
        val t = input.trim()
        if (t.isEmpty() || _state.value.generating || _state.value.loading) return
        input = ""
        val img = pendingImage
        pendingImage = null
        _state.update { it.copy(messages = it.messages + ChatMessage(true, t, img) + ChatMessage(false, ""), generating = true) }
        job = viewModelScope.launch {
            runCatching {
                engine.generate(_state.value.mode.system, t).collect { p ->
                    _state.update { s -> s.copy(messages = s.messages.dropLast(1) + ChatMessage(false, p)) }
                }
            }.onFailure { e ->
                if (e !is CancellationException) {
                    _state.update { s -> s.copy(messages = s.messages.dropLast(1) + ChatMessage(false, "Ошибка: ${e.message}")) }
                }
            }
            _state.update { it.copy(generating = false) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(onBack: () -> Unit, vm: AiChatViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(s.messages.size, s.messages.lastOrNull()?.text?.length) {
        if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(s.modelName)
                        Text(if (s.generating) "думает…" else "офлайн · ${s.mode.title}", style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                actions = { IconButton(onClick = { vm.newChat() }) { Icon(Icons.Default.DeleteSweep, "Новый чат") } }
            )
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ChatMode.entries) { m ->
                    FilterChip(s.mode == m, { vm.newChat(m) }, { Text(m.title) })
                }
            }
            Box(Modifier.weight(1f)) {
                if (s.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (s.error != null) {
                    Text(s.error!!, Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn(
                        state = list,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(s.messages) { i, m -> Bubble(m, s.generating && i == s.messages.lastIndex, clipboard) }
                    }
                }
            }
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(vm.input, { vm.input = it }, Modifier.weight(1f), placeholder = { Text("Сообщение…") }, maxLines = 5)
                    Spacer(Modifier.width(8.dp))
                    if (s.generating) {
                        FilledIconButton(onClick = { vm.stop() }) { Icon(Icons.Default.Stop, "Остановить") }
                    } else {
                        FilledIconButton(onClick = { vm.send() }, enabled = vm.input.isNotBlank() && !s.loading) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Отправить")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, typing: Boolean, clipboard: ClipboardManager) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(if (m.fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val bmp = m.image
            if (bmp != null) {
                val image = remember(bmp) { bmp.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    contentScale = ContentScale.Crop
                )
            }
            if (m.fromUser) {
                Text(m.text, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                MarkdownWithCode(m.text + (if (typing) " ▍" else ""), onCopied = {})
            }
        }
    }
}
