package com.school.hub.feature.social

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.data.ImageStorage
import com.school.hub.core.util.relativeTime
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SocialViewModel(val repo: SocialRepository, private val images: ImageStorage) : ViewModel() {
    val messages = repo.messages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val requests = repo.requestList.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var input by mutableStateOf("")
    var replyTo by mutableStateOf<ChatDto?>(null)
    var photo by mutableStateOf<android.graphics.Bitmap?>(null)

    fun pick(uri: android.net.Uri) = viewModelScope.launch { photo = images.decodeBitmap(uri, 1200) }
    fun replyToRequest(r: RequestDto) {
        replyTo = messages.value.lastOrNull { it.requestId == r.uuid && it.fromDevice == r.fromDevice }
            ?: ChatDto(r.uuid, r.fromDevice, r.fromName, "🆘 ${r.subject}, ${r.grade} класс", requestId = r.uuid, createdAt = r.createdAt)
        if (input.isBlank()) input = "Держи шпору по предмету «${r.subject}» 👇"
    }
    fun send() {
        val t = input; val p = photo; val r = replyTo
        if (t.isBlank() && p == null) return
        input = ""; photo = null; replyTo = null
        viewModelScope.launch { repo.send(t, p, r) }
    }
    fun delete(m: ChatDto) = viewModelScope.launch { repo.delete(m) }
    fun request(subject: String, grade: String, text: String) = viewModelScope.launch { repo.sendRequest(subject, grade, text) }
    fun close(r: RequestDto) = viewModelScope.launch { repo.closeRequest(r) }
}

private val imgCache = mutableMapOf<String, ImageBitmap?>()
private fun decode(b64: String): ImageBitmap? = imgCache.getOrPut(b64.hashCode().toString() + b64.length) {
    runCatching { Base64.decode(b64, Base64.DEFAULT).let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }.getOrNull()
}

/** Кнопка + диалог «Срочно запросить шпаргалку». */
@Composable
fun UrgentRequestDialog(onDismiss: () -> Unit, onSend: (String, String, String) -> Unit) {
    var subject by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text("🆘", style = MaterialTheme.typography.headlineMedium) },
        title = { Text("Срочно нужна шпаргалка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(subject, { subject = it }, label = { Text("Предмет") }, singleLine = true)
                OutlinedTextField(grade, { grade = it.filter(Char::isDigit).take(2) }, label = { Text("Класс") }, singleLine = true)
                OutlinedTextField(text, { text = it }, label = { Text("Что именно нужно (необязательно)") }, maxLines = 3)
                Text("Запрос разлетится всем рядом по Wi-Fi/Bluetooth и через интернет. Ответят в «Общем чате».",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = subject.isNotBlank() && grade.isNotBlank(), onClick = { onSend(subject.trim(), grade, text.trim()); onDismiss() }) { Text("Отправить всем") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Лента входящих срочных запросов — показывается над шпаргалками. */
@Composable
fun RequestsStrip(onAnswer: (RequestDto) -> Unit, vm: SocialViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val list by vm.requests.collectAsStateWithLifecycle()
    val mine = vm.repo.myId
    val others = list.filter { it.fromDevice != mine }
    if (others.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        others.take(3).forEach { r ->
            Card(
                onClick = { vm.replyToRequest(r); onAnswer(r) },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🆘", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${r.fromName} просит: ${r.subject}, ${r.grade} кл.", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        if (r.text.isNotBlank()) Text(r.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Нажми, чтобы ответить в чате", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GlobalChatScreen(onBack: () -> Unit, vm: SocialViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val msgs by vm.messages.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    var ask by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::pick) }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }
    if (ask) UrgentRequestDialog({ ask = false }) { s, g, t -> vm.request(s, g, t) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Column { Text("Общий чат"); Text("Wi-Fi · Bluetooth · интернет", style = MaterialTheme.typography.labelSmall) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            actions = { TextButton(onClick = { ask = true }) { Text("🆘 Шпора") } },
        )
    }) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).imePadding()) {
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (msgs.isEmpty()) item { Text("Пока тихо. Напиши первым 👋", Modifier.padding(24.dp)) }
                items(msgs, key = { it.uuid }) { m ->
                    val mine = m.fromDevice == vm.repo.myId
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                        Column(
                            Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(18.dp))
                                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .combinedClickable(onClick = { vm.replyTo = m }, onLongClick = { if (mine) vm.delete(m) })
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (!mine) Text(m.fromName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            m.replyPreview?.let {
                                Text("↪ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(6.dp))
                            }
                            m.image?.let { b -> decode(b)?.let { Image(it, null, Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) } }
                            if (m.text.isNotBlank()) Text(m.text)
                            Text(relativeTime(m.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            vm.replyTo?.let { r ->
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null)
                    Text("  Ответ ${r.fromName}: ${r.text.take(40)}", Modifier.weight(1f), maxLines = 1)
                    IconButton(onClick = { vm.replyTo = null }) { Icon(Icons.Filled.Close, "Отмена") }
                }
            }
            vm.photo?.let { p ->
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(p.asImageBitmap(), null, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                    IconButton(onClick = { vm.photo = null }) { Icon(Icons.Filled.Delete, "Убрать фото") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Filled.AddPhotoAlternate, "Фото")
                }
                OutlinedTextField(vm.input, { vm.input = it }, Modifier.weight(1f), placeholder = { Text("Сообщение всем…") },
                    maxLines = 4, shape = RoundedCornerShape(24.dp))
                Spacer(Modifier.width(6.dp))
                FilledIconButton(onClick = vm::send, enabled = vm.input.isNotBlank() || vm.photo != null) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить") }
            }
        }
    }
}


/** Кнопка «🆘 Срочно запросить шпаргалку»: запрос расходится по интернету, Wi-Fi и Bluetooth всем рядом. */
@Composable
fun UrgentButton(onSent: () -> Unit, vm: SocialViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    var ask by remember { mutableStateOf(false) }
    FilledTonalButton(
        onClick = { ask = true },
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) { Text("🆘 Срочно") }
    if (ask) UrgentRequestDialog({ ask = false }) { s, g, t -> vm.request(s, g, t); ask = false; onSent() }
}
