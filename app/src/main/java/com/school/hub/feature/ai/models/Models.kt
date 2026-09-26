package com.school.hub.feature.ai.models

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.StatFs
import androidx.core.app.NotificationCompat
import com.school.hub.R
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.ai.engine.ChatFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.io.File

enum class Tier(val title: String) { LITE("Лёгкие — для любых телефонов"), MID("Средние — баланс ума и скорости"), PRO("Мощные — для флагманов") }

data class LlmModel(val id: String,val name: String,val tagline: String,val format: ChatFormat,val url: String,val fileName: String,val sizeMb: Int,val minRamGb: Double,val russian: Int,val contextSize: Int = 2048,val mmprojUrl: String? = null,val mmprojMb: Int = 0,val smart: Int = 50,val errorRate: Int = 30,val year: Int = 2024,val systemSuffix: String = "",val isNew: Boolean = false,val tier: Tier = Tier.MID,val needsNewEngine: Boolean = false) {
    val vision get() = mmprojUrl != null
    val mmprojFileName get() = "$id-mmproj.gguf"
    val totalMb get() = sizeMb + mmprojMb
}

private const val HF = "https://huggingface.co"
private fun hf(repo: String, file: String) = "$HF/$repo/resolve/main/$file?download=true"

object ModelCatalog {
    val models = listOf(
        LlmModel("gemma3-270m","Gemma 3 · 270M","Быстрые ответы",ChatFormat.GEMMA,hf("unsloth/gemma-3-270m-it-GGUF","gemma-3-270m-it-Q4_K_M.gguf"),"gemma-3-270m-it-Q4_K_M.gguf",253,1.5,1,tier=Tier.LITE,isNew=true),
        LlmModel("smollm2-360m","SmolLM2 · 360M","Для слабых телефонов",ChatFormat.CHATML,hf("bartowski/SmolLM2-360M-Instruct-GGUF","SmolLM2-360M-Instruct-Q4_K_M.gguf"),"SmolLM2-360M-Instruct-Q4_K_M.gguf",271,1.5,1,tier=Tier.LITE),
        LlmModel("qwen3-06b","Qwen 3 · 0.6B","Крошечная модель",ChatFormat.CHATML,hf("unsloth/Qwen3-0.6B-GGUF","Qwen3-0.6B-Q4_K_M.gguf"),"Qwen3-0.6B-Q4_K_M.gguf",397,2.0,2,tier=Tier.LITE),
        LlmModel("gemma3-1b","Gemma 3 · 1B","Быстрая и знает русский",ChatFormat.GEMMA,hf("unsloth/gemma-3-1b-it-GGUF","gemma-3-1b-it-Q4_K_M.gguf"),"gemma-3-1b-it-Q4_K_M.gguf",806,3.0,2,tier=Tier.LITE),
        LlmModel("qwen3-17b","Qwen 3 · 1.7B","Математика и русский",ChatFormat.CHATML,hf("unsloth/Qwen3-1.7B-GGUF","Qwen3-1.7B-Q4_K_M.gguf"),"Qwen3-1.7B-Q4_K_M.gguf",1110,4.0,3,contextSize=4096,tier=Tier.LITE),
        LlmModel("gemma4-e2b","Gemma 4 · E2B","Видит фото",ChatFormat.GEMMA,hf("unsloth/gemma-4-E2B-it-GGUF","gemma-4-E2B-it-Q4_K_M.gguf"),"gemma-4-E2B-it-Q4_K_M.gguf",3100,5.0,3,mmprojUrl=hf("unsloth/gemma-4-E2B-it-GGUF","mmproj-F16.gguf"),mmprojMb=600,tier=Tier.MID,needsNewEngine=true,isNew=true),
        LlmModel("qwen25vl-3b","Qwen 2.5 VL · 3B","Решает задачи по фото",ChatFormat.CHATML,hf("unsloth/Qwen2.5-VL-3B-Instruct-GGUF","Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf"),"Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",1930,6.0,3,mmprojUrl=hf("unsloth/Qwen2.5-VL-3B-Instruct-GGUF","mmproj-F16.gguf"),mmprojMb=1340,tier=Tier.MID),
        LlmModel("gemma3-4b","Gemma 3 · 4B","Русский и изображения",ChatFormat.GEMMA,hf("unsloth/gemma-3-4b-it-GGUF","gemma-3-4b-it-Q4_K_M.gguf"),"gemma-3-4b-it-Q4_K_M.gguf",2490,6.0,3,mmprojUrl=hf("unsloth/gemma-3-4b-it-GGUF","mmproj-F16.gguf"),mmprojMb=850,tier=Tier.MID),
        LlmModel("qwen3-4b","Qwen 3 · 4B","Умная текстовая модель",ChatFormat.CHATML,hf("unsloth/Qwen3-4B-GGUF","Qwen3-4B-Q4_K_M.gguf"),"Qwen3-4B-Q4_K_M.gguf",2500,6.0,3,contextSize=4096,tier=Tier.MID),
        LlmModel("phi4-mini","Phi-4 mini · 3.8B","Математика и логика",ChatFormat.PHI3,hf("unsloth/Phi-4-mini-instruct-GGUF","Phi-4-mini-instruct-Q4_K_M.gguf"),"Phi-4-mini-instruct-Q4_K_M.gguf",2490,6.0,2,contextSize=4096,tier=Tier.MID),
        LlmModel("gemma4-12b","Gemma 4 · 12B","Топ-ум и зрение",ChatFormat.GEMMA,hf("unsloth/gemma-4-12b-it-GGUF","gemma-4-12b-it-Q4_K_M.gguf"),"gemma-4-12b-it-Q4_K_M.gguf",7400,12.0,3,mmprojUrl=hf("unsloth/gemma-4-12b-it-GGUF","mmproj-F16.gguf"),mmprojMb=850,tier=Tier.PRO,needsNewEngine=true,isNew=true),
        LlmModel("qwen3-14b","Qwen 3 · 14B","Сложные задачи и код",ChatFormat.CHATML,hf("unsloth/Qwen3-14B-GGUF","Qwen3-14B-Q4_K_M.gguf"),"Qwen3-14B-Q4_K_M.gguf",9000,14.0,3,contextSize=4096,tier=Tier.PRO),
        LlmModel("phi4-14b","Phi-4 · 14B","Сильная логика",ChatFormat.CHATML,hf("unsloth/phi-4-GGUF","phi-4-Q4_K_M.gguf"),"phi-4-Q4_K_M.gguf",9050,14.0,2,contextSize=4096,tier=Tier.PRO)
    )
    fun byId(id: String) = models.firstOrNull { it.id == id }
}

enum class Verdict(val label: String,val color: Long) { GREAT("Потянет отлично",0xFF2ECC71), OK("Потянет, но медленно",0xFFF39C12), NO("Не хватит памяти",0xFFE74C3C) }
fun verdict(model: LlmModel,totalRamGb: Double,is64Bit: Boolean) = when { !is64Bit -> Verdict.NO; totalRamGb >= model.minRamGb*1.3 -> Verdict.GREAT; totalRamGb >= model.minRamGb*.85 -> Verdict.OK; else -> Verdict.NO }

sealed interface ModelStatus {
    data object NotDownloaded: ModelStatus
    data class Downloading(val downloadedMb: Long,val totalMb: Long,val paused: Boolean = false,val speedKbs: Long = 0,val etaSeconds: Long? = null): ModelStatus { val progress get() = if(totalMb<=0) 0f else (downloadedMb.toFloat()/totalMb).coerceIn(0f,1f) }
    data object Paused: ModelStatus
    data object WaitingForConnection: ModelStatus
    data object Verifying: ModelStatus
    data object Ready: ModelStatus
    data object Corrupted: ModelStatus
    data class Failed(val reason: String): ModelStatus
}

class ModelDownloader(private val context: Context, private val settings: SettingsStore) {
    private val controller = ResumableModelDownloadController(context)
    val dir get() = File(controllerDirectory(), "")
    private fun controllerDirectory() = (context.getExternalFilesDir("models") ?: File(context.filesDir,"models")).apply { mkdirs() }
    fun file(m: LlmModel) = File(controllerDirectory(),m.fileName)
    fun mmprojFile(m: LlmModel) = File(controllerDirectory(),m.mmprojFileName)
    fun freeSpaceMb() = StatFs(controllerDirectory().absolutePath).availableBytes / (1024*1024)
    fun isReady(m: LlmModel) = controller.state(m).status == ResumableModelDownloadController.Status.INSTALLED
    fun isVisionReady(m: LlmModel) = !m.vision || mmprojFile(m).exists()
    fun start(m: LlmModel,wifiOnly: Boolean): Result<Unit> = runCatching {
        if(wifiOnly) { val cm=context.getSystemService(ConnectivityManager::class.java); val c=cm?.getNetworkCapabilities(cm.activeNetwork); require(c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)==true || c?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)==true) { "Подключитесь к Wi‑Fi или выключите ограничение" } }
        controller.start(m); DownloadService.start(context)
    }
    fun pause(m: LlmModel) = controller.pause(m)
    fun resume(m: LlmModel) = controller.start(m)
    fun cancel(m: LlmModel) = controller.cancel(m)
    fun delete(m: LlmModel) = controller.delete(m)
    fun status(m: LlmModel): ModelStatus { val s=controller.state(m); return when(s.status) { ResumableModelDownloadController.Status.NOT_INSTALLED -> ModelStatus.NotDownloaded; ResumableModelDownloadController.Status.DOWNLOADING -> ModelStatus.Downloading(s.downloadedBytes/1048576,s.totalBytes/1048576,false,s.speedBytesPerSecond/1024,s.etaSeconds); ResumableModelDownloadController.Status.PAUSED -> ModelStatus.Paused; ResumableModelDownloadController.Status.WAITING_FOR_CONNECTION -> ModelStatus.WaitingForConnection; ResumableModelDownloadController.Status.VERIFYING -> ModelStatus.Verifying; ResumableModelDownloadController.Status.INSTALLED -> ModelStatus.Ready; ResumableModelDownloadController.Status.CORRUPTED -> ModelStatus.Corrupted; ResumableModelDownloadController.Status.ERROR -> ModelStatus.Failed(s.message ?: "Не удалось загрузить модель") } }
    fun observe(): Flow<Map<String,ModelStatus>> = controller.observe().map { ModelCatalog.models.associateWith { status(it) }.mapKeys { it.key.id } }.onStart { emit(ModelCatalog.models.associate { it.id to status(it) }) }
    val states: StateFlow<Map<String,ModelStatus>> get() = observe().let { flow -> MutableStateFlow(ModelCatalog.models.associate { it.id to status(it) }) }
}

class DownloadService: Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int { val nm=getSystemService(NotificationManager::class.java); if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(NotificationChannel("downloads","Загрузки",NotificationManager.IMPORTANCE_LOW)); val n: Notification=NotificationCompat.Builder(this,"downloads").setSmallIcon(R.drawable.ic_notification).setContentTitle("Загрузка модели ИИ…").setOngoing(true).build(); if(Build.VERSION.SDK_INT>=29) startForeground(77,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(77,n); return START_NOT_STICKY }
    companion object { fun start(c: Context)=runCatching { androidx.core.content.ContextCompat.startForegroundService(c,Intent(c,DownloadService::class.java)) }; fun stop(c: Context)=runCatching { c.stopService(Intent(c,DownloadService::class.java)) } }
}
