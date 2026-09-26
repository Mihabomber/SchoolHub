package com.school.hub.feature.ai.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.ai.engine.LlmEngine
import com.school.hub.feature.ai.models.*
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.flow.StateFlow

class AiModelsViewModel(context: Context,private val downloader: ModelDownloader,private val engine: LlmEngine,val settings: SettingsStore): ViewModel() {
    val statuses: StateFlow<Map<String,ModelStatus>> = downloader.observe().let { kotlinx.coroutines.flow.MutableStateFlow(ModelCatalog.models.associate { it.id to downloader.status(it) }) }
    fun download(m:LlmModel)=downloader.start(m,settings.wifiOnly.value)
    fun pause(m:LlmModel)=downloader.pause(m)
    fun resume(m:LlmModel)=downloader.resume(m)
    fun cancel(m:LlmModel)=downloader.cancel(m)
    fun delete(m:LlmModel)=downloader.delete(m)
}

@Composable fun AiModelsScreen(onOpenChat:(String)->Unit,hasPendingPrompt:Boolean,vm:AiModelsViewModel=viewModel(factory=AppViewModelFactory.Factory)) {
    val statuses by vm.statuses.collectAsStateWithLifecycle(); val wifi by vm.settings.wifiOnly.collectAsStateWithLifecycle()
    Scaffold { padding -> LazyColumn(Modifier.padding(padding).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Модели ИИ",style=MaterialTheme.typography.headlineMedium); Text("Скачайте модель для работы без сети") }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("Только по Wi‑Fi"); Switch(wifi,{ vm.settings.setWifiOnly(it) }) } }
        ModelCatalog.models.forEach { model -> item { ModelCard(model,statuses[model.id] ?: ModelStatus.NotDownloaded,{vm.download(model)},{vm.pause(model)},{vm.resume(model)},{vm.cancel(model)},{vm.delete(model)},{onOpenChat(model.id)}) } }
    } }
}

@Composable private fun ModelCard(model:LlmModel,status:ModelStatus,onDownload:()->Unit,onPause:()->Unit,onResume:()->Unit,onCancel:()->Unit,onDelete:()->Unit,onChat:()->Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(model.name,style=MaterialTheme.typography.titleMedium); Text(model.tagline,style=MaterialTheme.typography.bodySmall)
        when(status) {
            is ModelStatus.Downloading -> { Text("Загрузка"); LinearProgressIndicator({status.progress},Modifier.fillMaxWidth()); Text("${gb(status.downloadedMb)} / ${gb(status.totalMb)}" + if(status.speedKbs>0) " · ${"%.1f".format(status.speedKbs/1024f)} МБ/с" else "" + (status.etaSeconds?.let { " · ETA ${formatEta(it)}" } ?: "")); Row { TextButton(onPause){Icon(Icons.Filled.Pause,null);Text("Пауза")}; TextButton(onCancel){Icon(Icons.Filled.Close,null);Text("Отмена")} } }
            ModelStatus.Paused -> { Text("Пауза"); Text("${gb((model.sizeMb*1024L))} максимум"); Row { Button(onResume){Icon(Icons.Filled.PlayArrow,null);Text("Продолжить")}; TextButton(onCancel){Text("Отмена")} } }
            ModelStatus.WaitingForConnection -> { Text("Ожидание сети…"); TextButton(onResume){Text("Возобновить")} }
            ModelStatus.Verifying -> { Text("Проверка…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            ModelStatus.Ready -> { Text("Установлена"); Row { Button(onChat){Text("Открыть чат")}; IconButton(onDelete){Icon(Icons.Filled.Delete,"Удалить")} } }
            ModelStatus.Corrupted -> { Text("Повреждена",color=MaterialTheme.colorScheme.error); Button(onDownload){Icon(Icons.Filled.CloudDownload,null);Text("Скачать заново")} }
            is ModelStatus.Failed -> { Text("Ошибка: ${status.reason}",color=MaterialTheme.colorScheme.error); Button(onResume){Text("Повторить")} }
            ModelStatus.NotDownloaded -> Button(onDownload){Icon(Icons.Filled.CloudDownload,null);Text("Скачать ${model.totalMb} МБ")}
        }
    } }
}
private fun gb(mb:Long) = "%.2f ГБ".format(mb/1024f)
private fun formatEta(seconds:Long) = if(seconds<60) "${seconds} с" else "${seconds/60} мин"
