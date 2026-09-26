package com.school.hub.feature.notebook

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private val Neon = Color(0xFF00FF9C)
private val Dark = Color(0xFF07070B)
private val Panel = Color(0xFF15151D)
private const val GLITCH_CHARS = "#$%&@!?*<>/|01"

fun glitchify(text: String): String = text.map { if (it == ' ') " " else "$it\u0337" }.joinToString("")

val NOTEBOOK_GLITCH_TITLE: String = glitchify("Notebook")

private enum class Stage { LOADING, LOGIN, DESKTOP }

data class Note(val id: Long, val title: String, val body: String)

private class NoteStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("notebook", Context.MODE_PRIVATE)

    fun load(): List<Note> = runCatching {
        val arr = JSONArray(prefs.getString("notes", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Note(o.getLong("id"), o.optString("title"), o.optString("body"))
        }
    }.getOrDefault(emptyList())

    fun save(notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { arr.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body)) }
        prefs.edit().putString("notes", arr.toString()).apply()
    }
}

@Composable
fun GlitchText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Neon,
    intensity: Float = 0.15f
) {
    var shown by remember(text) { mutableStateOf(text) }
    LaunchedEffect(text, intensity) {
        while (true) {
            shown = text.map { c ->
                if (c != ' ' && c != '\u0337' && Random.nextFloat() < intensity) GLITCH_CHARS[Random.nextInt(GLITCH_CHARS.length)] else c
            }.joinToString("")
            delay(90)
            shown = text
            delay(Random.nextLong(200, 700))
        }
    }
    Text(shown, modifier = modifier, style = style, color = color, fontFamily = FontFamily.Monospace)
}

@Composable
fun NotebookGlitchButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Dark)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        GlitchText(NOTEBOOK_GLITCH_TITLE, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun NotebookScreen(onBack: () -> Unit, onExitToMenuTop: () -> Unit) {
    var stage by rememberSaveable { mutableStateOf(Stage.LOADING) }
    BackHandler { onBack() }
    Box(Modifier.fillMaxSize().background(Dark)) {
        when (stage) {
            Stage.LOADING -> LoadingStage { stage = Stage.LOGIN }
            Stage.LOGIN -> LoginStage { stage = Stage.DESKTOP }
            Stage.DESKTOP -> DesktopStage(onBack, onExitToMenuTop)
        }
    }
}

@Composable
private fun LoadingStage(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (progress < 1f) {
            delay(40)
            progress = (progress + 0.015f + Random.nextFloat() * 0.02f).coerceAtMost(1f)
        }
        delay(300)
        done()
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GlitchText(NOTEBOOK_GLITCH_TITLE, style = MaterialTheme.typography.headlineMedium, intensity = 0.3f)
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Neon, trackColor = Panel)
        Spacer(Modifier.height(12.dp))
        Text("загрузка системы… ${(progress * 100).toInt()}%", color = Color(0xFF7A7A8C), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun LoginStage(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Panel), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = Neon, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(16.dp))
        GlitchText("guest", style = MaterialTheme.typography.titleLarge, color = Color.White, intensity = 0.1f)
        Spacer(Modifier.height(8.dp))
        Text("Вход в систему", color = Color(0xFF9A9AAC))
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Dark)
        ) { Text("Войти") }
    }
}

@Composable
private fun DesktopStage(onBack: () -> Unit, onExitToMenuTop: () -> Unit) {
    val context = LocalContext.current
    val store = remember { NoteStore(context) }
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    var dialogStep by rememberSaveable { mutableIntStateOf(0) }
    var reward by rememberSaveable { mutableStateOf(false) }

    fun invite() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Попробуй приложение «Парта» для школы!")
        runCatching {
            context.startActivity(Intent.createChooser(send, "Пригласить друзей").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlitchText(NOTEBOOK_GLITCH_TITLE, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Выйти", color = Neon) }
        }
        Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DesktopIcon(Icons.Filled.Edit, "Заметки") { notesOpen = true }
            DesktopIcon(Icons.Filled.Lock, "Секрет") { dialogStep = 1 }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                Modifier
                    .padding(end = 8.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .clickable { dialogStep = 1 }
                    .padding(12.dp)
            ) {
                Text("Привет! Нажми на меня", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Neon).clickable { dialogStep = 1 },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Face, contentDescription = "Персонаж", tint = Dark, modifier = Modifier.size(48.dp))
            }
        }
    }

    if (notesOpen) NotesApp(store) { notesOpen = false }

    when (dialogStep) {
        1 -> AlertDialog(
            onDismissRequest = { dialogStep = 0 },
            icon = { Icon(Icons.Filled.Face, contentDescription = null) },
            title = { Text("Хочешь открыть секретную функцию?") },
            confirmButton = { TextButton(onClick = { dialogStep = 2 }) { Text("Да") } },
            dismissButton = { TextButton(onClick = { dialogStep = 0 }) { Text("Нет") } }
        )
        2 -> AlertDialog(
            onDismissRequest = { dialogStep = 0 },
            icon = { Icon(Icons.Filled.Face, contentDescription = null) },
            title = { Text("Пригласи 5 друзей…") },
            text = { Text("Пригласи 5 друзей, чтобы открыть секретную функцию. Или загляни в награду уже сейчас.") },
            confirmButton = { TextButton(onClick = { dialogStep = 0; reward = true }) { Text("Показать награду") } },
            dismissButton = { TextButton(onClick = { invite() }) { Text("Пригласить") } }
        )
    }

    if (reward) RewardOverlay(onExitToMenuTop)
}

@Composable
private fun DesktopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Panel), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Neon, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@Composable
private fun RewardOverlay(onFinish: () -> Unit) {
    val finish by rememberUpdatedState(onFinish)
    var finished by remember { mutableStateOf(false) }
    fun done() {
        if (!finished) {
            finished = true
            finish()
        }
    }
    LaunchedEffect(Unit) {
        delay(3000)
        done()
    }
    BackHandler { done() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlitchText(glitchify("НАГРАДА"), style = MaterialTheme.typography.headlineLarge, intensity = 0.5f)
            GlitchText("ERR_REWARD_0x05", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFF3B5C), intensity = 0.4f)
            GlitchText(glitchify("доступ закрыт"), style = MaterialTheme.typography.bodyLarge, color = Color.White, intensity = 0.35f)
        }
    }
}

@Composable
private fun NotesApp(store: NoteStore, onClose: () -> Unit) {
    var notes by remember { mutableStateOf(store.load()) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    fun persist(list: List<Note>) {
        notes = list
        store.save(list)
    }

    fun saveCurrent() {
        val id = editingId ?: return
        if (title.isBlank() && body.isBlank()) {
            persist(notes.filterNot { it.id == id })
        } else {
            val note = Note(id, title.trim(), body)
            persist(if (notes.any { it.id == id }) notes.map { if (it.id == id) note else it } else listOf(note) + notes)
        }
    }

    fun closeEditor() {
        saveCurrent()
        editingId = null
    }

    LaunchedEffect(editingId, title, body) {
        if (editingId != null) {
            delay(600)
            saveCurrent()
        }
    }

    BackHandler { if (editingId != null) closeEditor() else onClose() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (editingId != null) closeEditor() else onClose() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    if (editingId == null) "Заметки" else "Заметка",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (editingId == null) {
                    IconButton(onClick = { editingId = System.currentTimeMillis(); title = ""; body = "" }) {
                        Icon(Icons.Filled.Add, contentDescription = "Новая заметка")
                    }
                } else {
                    IconButton(onClick = {
                        val id = editingId
                        persist(notes.filterNot { it.id == id })
                        editingId = null
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (editingId == null) {
                if (notes.isEmpty()) {
                    Text(
                        "Пока пусто. Нажми +, чтобы создать заметку.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notes, key = { it.id }) { n ->
                        Card(onClick = { editingId = n.id; title = n.title; body = n.body }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(n.title.ifBlank { "Без названия" }, style = MaterialTheme.typography.titleMedium)
                                if (n.body.isNotBlank()) {
                                    Text(n.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Заголовок") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth().weight(1f), label = { Text("Текст") })
            }
        }
    }
}
