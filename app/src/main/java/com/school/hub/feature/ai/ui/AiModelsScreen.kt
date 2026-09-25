package com.school.hub.feature.ai.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.data.DeviceInfo
import com.school.hub.core.data.DeviceSpecs
import com.school.hub.core.data.SettingsStore
import com.school.hub.core.ui.components.GradientIcon
import com.school.hub.core.ui.components.SmallBadge
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.feature.ai.engine.LlmEngine
import com.school.hub.feature.ai.models.LlmModel
import com.school.hub.feature.ai.models.ModelCatalog
import com.school.hub.feature.ai.models.ModelDownloader
import com.school.hub.feature.ai.models.ModelStatus
import com.school.hub.feature.ai.models.verdict
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AiModelsViewModel(
    context: Context,
    private val downloader: ModelDownloader,
    private val engine: LlmEngine,
    val settings: SettingsStore,
) : ViewModel() {
    val specs: DeviceSpecs = DeviceInfo.read(context)
    val engineAvailable: Boolean get() = engine.isAvailable
    val loadedModelId: String? get() = engine.loadedModelId

    val statuses: StateFlow<Map<String, ModelStatus>> =
        downloader.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyMap())

    var message by mutableStateOf<String?>(null)
        private set

    fun download(m: LlmModel) {
        downloader.start(m, settings.wifiOnly.value).onFailure { message = it.message }
    }

    fun cancel(m: LlmModel) = downloader.cancel(m)

    fun delete(m: LlmModel) {
        viewModelScope.launch {
            if (engine.loadedModelId == m.id) engine.unload()
            downloader.delete(m)
        }
    }

    fun consumeMessage() { message = null }
}

@Composable
fun AiModelsScreen(
    onOpenChat: (String) -> Unit,
    hasPendingPrompt: Boolean,
    vm: AiModelsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val wifiOnly by vm.settings.wifiOnly.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("ИИ офлайн", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Нейросеть работает прямо в телефоне — без интернета",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { DeviceCard(vm.specs) }
            item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        "🧠 Ум — наша сводная оценка по открытым тестам (математика, знания, логика) для школьных вопросов.\n" +
                            "⚠️ Ошибается — примерно в скольких ответах из 100 будет неточность или выдумка. Всегда проверяй важное!\n" +
                            "📝 Читает только текст с фото — к модели можно прикрепить фото: текст с него распознаётся на телефоне и вставляется в вопрос. Рисунки, графики и схемы она не понимает.\n" +
                            "👁 Видит картинку целиком — мультимодальная модель: понимает рисунки, схемы, графики, а не только буквы. " +
                            "В этой версии приложения фото ей тоже передаётся через распознавание текста; полное зрение появится в обновлении.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            if (!vm.engineAvailable) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "ИИ-движок не встроен в эту сборку (нативная часть не собралась в GitHub Actions). Модели можно скачать заранее — чат заработает в следующей сборке.",
                                color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            if (hasPendingPrompt) {
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            "💡 Вопрос по шпаргалке уже готов — выбери скачанную модель и открой чат",
                            modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Скачивать только по Wi-Fi", modifier = Modifier.weight(1f))
                    Switch(checked = wifiOnly, onCheckedChange = { vm.settings.setWifiOnly(it) })
                }
            }
            items(ModelCatalog.models, key = { it.id }) { m ->
                ModelCard(
                    model = m,
                    status = statuses[m.id] ?: ModelStatus.NotDownloaded,
                    specs = vm.specs,
                    loaded = vm.loadedModelId == m.id,
                    onDownload = { vm.download(m) },
                    onCancel = { vm.cancel(m) },
                    onDelete = { vm.delete(m) },
                    onChat = { onOpenChat(m.id) },
                )
            }
            item {
                Text(
                    "Модели — квантованные GGUF (Q4) с Hugging Face. Работают через llama.cpp на процессоре. " +
                        "Совет: для русского языка лучше всего Qwen 2.5 1.5B.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(specs: DeviceSpecs) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(AppGradients.Candy)).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Memory, null, tint = Color.White) }
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Твой телефон", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text("%.1f ГБ RAM".format(specs.totalRamGb), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "свободно %.1f ГБ · %s".format(specs.availRamGb, specs.abi),
                    color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: LlmModel,
    status: ModelStatus,
    specs: DeviceSpecs,
    loaded: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onChat: () -> Unit,
) {
    val v = verdict(model, specs.totalRamGb, specs.is64Bit)
    val vColor = Color(v.color)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientIcon(Icons.Filled.Chat, if (loaded) AppGradients.Mint else AppGradients.Violet, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(model.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (model.isNew) SmallBadge("🆕 ${model.year}", Color(0x332ECC71), Color(0xFF2ECC71))
            SmartMeter(model)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (model.vision) SmallBadge("👁 Видит картинку целиком", Color(0x333498DB), Color(0xFF3498DB))
                else SmallBadge("📝 Читает только текст с фото", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SmallBadge("${"%.1f".format(model.sizeMb / 1024f)} ГБ", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                SmallBadge("RAM от ${model.minRamGb.toInt()} ГБ", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                SmallBadge("RU " + "★".repeat(model.russian) + "☆".repeat(3 - model.russian), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(vColor))
                Spacer(Modifier.width(8.dp))
                Text(v.label, color = vColor, style = MaterialTheme.typography.labelLarge)
                if (loaded) { Spacer(Modifier.width(8.dp)); SmallBadge("в памяти", vColor.copy(alpha = 0.15f), vColor) }
            }
            when (status) {
                is ModelStatus.Downloading -> {
                    LinearProgressIndicator(
                        progress = { status.progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${status.downloadedMb} / ${status.totalMb} МБ" + if (status.paused) " · ждёт Wi-Fi/сеть" else "",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onCancel) { Icon(Icons.Filled.Close, null); Text(" Отмена") }
                    }
                }
                ModelStatus.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onChat, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Chat, null); Spacer(Modifier.width(6.dp)); Text("Открыть чат")
                    }
                    OutlinedIconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Удалить модель") }
                }
                else -> {
                    if (status is ModelStatus.Failed) {
                        Text(status.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = onDownload, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.CloudDownload, null); Spacer(Modifier.width(6.dp))
                        Text(if (v == com.school.hub.feature.ai.models.Verdict.NO) "Скачать (может не хватить RAM)" else "Скачать ${model.sizeMb} МБ")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartMeter(model: LlmModel) {
    val c = when {
        model.smart >= 60 -> Color(0xFF2ECC71)
        model.smart >= 42 -> Color(0xFFF39C12)
        else -> Color(0xFFE74C3C)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🧠 Ум: ${model.smart}/100", style = MaterialTheme.typography.labelLarge, color = c, modifier = Modifier.weight(1f))
            Text("⚠️ Ошибается ≈ ${model.errorRate}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { model.smart / 100f },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = c,
        )
    }
}
