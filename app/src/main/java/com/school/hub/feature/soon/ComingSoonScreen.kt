package com.school.hub.feature.soon

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.school.hub.core.data.DeviceInfo
import com.school.hub.core.ui.components.GradientIcon
import com.school.hub.core.ui.components.SmallBadge
import com.school.hub.core.ui.theme.AppGradients

enum class Upcoming(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val gradient: List<Color>,
    val bullets: List<String>,
) {
    SCHEDULE(
        "Расписание", "Одно расписание на весь класс", Icons.Filled.Schedule, AppGradients.Ocean,
        listOf("Сетка уроков по дням недели", "Совместное редактирование — обновил один, увидели все", "Уведомления о начале урока и звонках", "Замены и перенос уроков"),
    ),
    HOMEWORK(
        "Домашка", "Ничего не забудешь", Icons.Filled.TaskAlt, AppGradients.Sunset,
        listOf("ДЗ по предметам с дедлайнами", "Списки: сегодня / завтра / неделя", "Отметка «выполнено»", "Обмен ДЗ с классом, как у шпаргалок"),
    ),
    AI(
        "ИИ офлайн", "Нейросеть прямо в телефоне", Icons.Filled.AutoAwesome, AppGradients.Candy,
        listOf("Выбор модели: Llama 3.2, Gemma 2, Phi-3 (GGUF)", "Проверка «потянет ли телефон»", "Скачивание по Wi-Fi с прогрессом", "Чат через llama.cpp — полностью без интернета"),
    ),
    TRANSLATOR(
        "Переводчик", "Google ML Kit, работает офлайн", Icons.Filled.Translate, AppGradients.Mint,
        listOf("Перевод любого текста", "Языковые пакеты скачиваются один раз", "Перевод с камеры: распознавание + оверлей"),
    ),
}

@Composable
fun ComingSoonScreen(feature: Upcoming) {
    Scaffold { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                    .background(Brush.linearGradient(feature.gradient)).padding(24.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(feature.icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                    Text(feature.title, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                    Text(feature.subtitle, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                    SmallBadge("в разработке", Color.White.copy(alpha = 0.25f), Color.White)
                }
            }
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Что будет", style = MaterialTheme.typography.titleMedium)
                    feature.bullets.forEach {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, null, tint = feature.gradient.first(), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (feature == Upcoming.AI) DeviceCheckCard()
        }
    }
}

private data class ModelSpec(val name: String, val quant: String, val fileGb: Double, val minRamGb: Double)

private val models = listOf(
    ModelSpec("Llama 3.2 1B Instruct", "Q4_K_M", 0.81, 2.0),
    ModelSpec("Gemma 2 2B it", "Q4_K_M", 1.71, 3.0),
    ModelSpec("Llama 3.2 3B Instruct", "Q4_K_M", 2.02, 4.0),
    ModelSpec("Phi-3-mini 4K Instruct", "Q4_K_M", 2.39, 5.0),
)

/** Уже сейчас: реальная проверка RAM устройства через ActivityManager. */
@Composable
private fun DeviceCheckCard() {
    val context = LocalContext.current
    val specs = remember { DeviceInfo.read(context) }
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientIcon(Icons.Filled.Memory, AppGradients.Night)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Твоё устройство", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "RAM: %.1f ГБ (свободно %.1f) · %s".format(specs.totalRamGb, specs.availRamGb, specs.abi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            models.forEach { m ->
                val (label, color) = when {
                    !specs.is64Bit -> "не потянет" to Color(0xFFE74C3C)
                    specs.totalRamGb >= m.minRamGb * 1.5 -> "отлично" to Color(0xFF2ECC71)
                    specs.totalRamGb >= m.minRamGb -> "потянет" to Color(0xFFF39C12)
                    else -> "не потянет" to Color(0xFFE74C3C)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${m.quant} · файл %.1f ГБ · нужно от %.0f ГБ RAM".format(m.fileGb, m.minRamGb),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    SmallBadge(label, color.copy(alpha = 0.18f), color)
                }
            }
        }
    }
}
