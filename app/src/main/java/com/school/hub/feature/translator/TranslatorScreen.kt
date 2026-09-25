package com.school.hub.feature.translator

import android.content.Intent
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.school.hub.core.ui.components.GradientIcon
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.navigation.AppViewModelFactory
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslatorScreen(
    onOpenCamera: () -> Unit,
    onBack: () -> Unit,
    vm: TranslatorViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var picking by remember { mutableStateOf<Boolean?>(null) } // true — источник, false — перевод
    var managing by remember { mutableStateOf(false) }
    val downloading by vm.downloadingLangs.collectAsStateWithLifecycle()

    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    var ttsMsg by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { st ->
            if (st == TextToSpeech.SUCCESS) {
                tts.value = engine
                ttsMsg = null
            } else {
                ttsMsg = "Озвучка не работает — нет движка. Нажми на колокол, откроются настройки озвучки."
            }
        }
        onDispose { engine?.shutdown() }
    }

    fun openTtsSettings() {
        val direct = Intent("android.settings.TTS_SETTINGS")
        val ok = runCatching { context.startActivity(direct); true }.getOrElse {
            runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS)); true }.getOrElse { false }
        }
        if (!ok) ttsMsg = "Не удалось открыть настройки озвучки"
    }

    val speak = {
        val engine = tts.value
        if (engine == null) {
            ttsMsg = "Движок озвучки не установлен — открываю настройки…"
            openTtsSettings()
        } else {
            val locale = Locale.forLanguageTag(vm.target)
            val available = engine.isLanguageAvailable(locale)
            if (available < 0) {
                ttsMsg = "Нет голоса для языка «${vm.repo.displayName(vm.target)}» — открываю настройки озвучки…"
                openTtsSettings()
            } else {
                engine.language = locale
                val res = engine.speak(vm.output, TextToSpeech.QUEUE_FLUSH, null, "tr")
                ttsMsg = if (res == TextToSpeech.SUCCESS) null else "Не удалось озвучить текст"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Переводчик") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
                    }
                },
                actions = { TextButton(onClick = { managing = true }) { Text("Пакеты") } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LangButton(
                    if (vm.source == AUTO) "Авто" + (vm.detected?.let { " · ${vm.repo.displayName(it)}" } ?: "") else vm.repo.displayName(vm.source),
                    Modifier.weight(1f),
                ) { picking = true }
                IconButton(onClick = vm::swap) { Icon(Icons.Filled.SwapHoriz, "Поменять") }
                LangButton(vm.repo.displayName(vm.target), Modifier.weight(1f)) { picking = false }
            }
            OutlinedTextField(
                value = vm.input, onValueChange = { vm.input = it },
                modifier = Modifier.fillMaxWidth(), minLines = 5,
                placeholder = { Text("Введи или вставь текст…") },
                shape = RoundedCornerShape(20.dp),
                trailingIcon = {
                    if (vm.input.isNotEmpty()) IconButton(onClick = { vm.input = "" }) { Icon(Icons.Filled.Clear, "Очистить") }
                },
            )
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            vm.repo.displayName(vm.target), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f),
                        )
                        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                    Text(
                        vm.output.ifBlank { if (vm.busy) "Перевожу…" else "Здесь появится перевод" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (vm.output.isNotBlank()) {
                        Row {
                            IconButton(onClick = { clipboard.setText(AnnotatedString(vm.output)) }) { Icon(Icons.Filled.ContentCopy, "Копировать") }
                            IconButton(onClick = { speak() }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Озвучить") }
                        }
                    }
                }
            }
            ttsMsg?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            vm.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (downloading.isNotEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(
                    "Качаю пакеты: " + downloading.joinToString(", ") { vm.repo.displayName(it) } + "…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                )
            }

            Card(
                onClick = onOpenCamera,
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    GradientIcon(Icons.Filled.CameraAlt, AppGradients.Mint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Перевод с камеры", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Наведи на учебник — перевод поверх текста",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                "Работает без интернета после скачивания языковых пакетов. Камера распознаёт латиницу (английский, немецкий, французский…).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    picking?.let { isSource ->
        LanguagePickerDialog(
            languages = vm.repo.languages,
            downloaded = vm.downloaded,
            allowAuto = isSource,
            name = vm.repo::displayName,
            onDismiss = { picking = null },
            onPick = { code -> if (isSource) vm.setSourceLang(code) else vm.setTargetLang(code); picking = null },
        )
    }
    if (managing) {
        ManagePacksDialog(
            languages = vm.repo.languages,
            downloaded = vm.downloaded,
            downloading = downloading,
            name = vm.repo::displayName,
            onDownload = vm::downloadLanguage,
            onDelete = vm::deleteLanguage,
            onDismiss = { managing = false },
        )
    }
}

@Composable
private fun LangButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Text(text, maxLines = 1)
    }
}

@Composable
private fun LanguagePickerDialog(
    languages: List<String>,
    downloaded: Set<String>,
    allowAuto: Boolean,
    name: (String) -> String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Язык") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (allowAuto) item { LangRow("🔎 Определить автоматически", false) { onPick(AUTO) } }
                items(languages) { code -> LangRow(name(code), code in downloaded) { onPick(code) } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
private fun LangRow(title: String, isDownloaded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        if (isDownloaded) Icon(Icons.Filled.CheckCircle, "скачан", tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ManagePacksDialog(
    languages: List<String>,
    downloaded: Set<String>,
    downloading: Set<String> = emptySet(),
    name: (String) -> String,
    onDownload: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Folder, null) },
        title = { Text("Языковые пакеты") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(languages) { code ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name(code), modifier = Modifier.weight(1f))
                        if (code in downloaded) {
                            IconButton(onClick = { onDelete(code) }, enabled = code != "en") { Icon(Icons.Filled.Delete, "Удалить") }
                        } else if (code in downloading) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { onDownload(code) }) { Icon(Icons.Filled.CloudDownload, "Скачать") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
