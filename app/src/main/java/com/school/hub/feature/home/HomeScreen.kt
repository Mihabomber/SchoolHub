package com.school.hub.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.ui.components.GradientIcon
import com.school.hub.core.ui.components.SmallBadge
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.core.util.plural
import com.school.hub.feature.cheatsheets.model.CheatSheet
import com.school.hub.navigation.AppViewModelFactory
import com.school.hub.navigation.Routes
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onOpenCheat: (Long) -> Unit,
    vm: HomeViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru")))
    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }

    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text(
                        date.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (s.userName.isBlank()) "$greeting! 👋" else "$greeting, ${s.userName}! 👋",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
            item { HeroCard(s, onAdd = { onNavigate(Routes.cheatEdit()) }) }
            item { SyncStatusRow(s, onClick = { onNavigate(Routes.SYNC) }) }
            item { Text("Разделы", style = MaterialTheme.typography.titleLarge) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCard("Шпаргалки", "Формулы и правила всего класса", Icons.Filled.Lightbulb, AppGradients.Violet, null, Modifier.weight(1f)) { onNavigate(Routes.CHEATS) }
                        FeatureCard("Расписание", "Уроки и звонки", Icons.Filled.Schedule, AppGradients.Ocean, "скоро", Modifier.weight(1f)) { onNavigate(Routes.SCHEDULE) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FeatureCard("Домашка", "Дедлайны и отметки", Icons.Filled.TaskAlt, AppGradients.Sunset, "скоро", Modifier.weight(1f)) { onNavigate(Routes.HOMEWORK) }
                        FeatureCard("ИИ офлайн", "Чат без интернета", Icons.Filled.AutoAwesome, AppGradients.Candy, "скоро", Modifier.weight(1f)) { onNavigate(Routes.AI) }
                    }
                    FeatureCard("Переводчик", "Текст и камера, работает офлайн", Icons.Filled.Translate, AppGradients.Mint, "скоро", Modifier.fillMaxWidth()) { onNavigate(Routes.TRANSLATOR) }
                }
            }
            if (s.recent.isNotEmpty()) {
                item { Text("Недавние шпаргалки", style = MaterialTheme.typography.titleLarge) }
                items(s.recent, key = { it.id }) { RecentRow(it) { onOpenCheat(it.id) } }
            }
        }
    }
}

@Composable
private fun HeroCard(s: HomeUiState, onAdd: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
            .background(Brush.linearGradient(AppGradients.Violet)).padding(24.dp),
    ) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-70).dp).size(200.dp)
                .background(Color.White.copy(alpha = 0.10f), CircleShape),
        )
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = 20.dp, y = 50.dp).size(120.dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Учебная база класса", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Text(
                plural(s.total, "шпаргалка", "шпаргалки", "шпаргалок"),
                color = Color.White, style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                "${plural(s.subjects, "предмет", "предмета", "предметов")} · от одноклассников ${s.fromClassmates} · ⭐ ${s.favorites}",
                color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF5B4CF0)),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Новая шпаргалка")
            }
        }
    }
}

@Composable
private fun SyncStatusRow(s: HomeUiState, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GradientIcon(
                if (s.online) Icons.Filled.Cloud else Icons.Filled.Bluetooth,
                if (s.online) AppGradients.Ocean else AppGradients.Violet, size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Обмен с классом", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        s.nearbyRunning -> "Рядом: ${plural(s.nearbyPeers, "устройство", "устройства", "устройств")}"
                        s.online -> "Онлайн · нажми, чтобы настроить"
                        else -> "Офлайн · обменяйся по Bluetooth"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                Modifier.size(10.dp).background(
                    if (s.online || s.nearbyRunning) Color(0xFF2ECC71) else Color(0xFFB0B0B0), CircleShape,
                ),
            )
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    gradient: List<Color>,
    badge: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                GradientIcon(icon, gradient)
                Spacer(Modifier.weight(1f))
                if (badge != null) SmallBadge(badge, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentRow(sheet: CheatSheet, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(Color(sheet.subject.color).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Text(sheet.subject.emoji, style = MaterialTheme.typography.titleLarge) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(sheet.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    sheet.content.lineSequence().firstOrNull { it.isNotBlank() } ?: sheet.subject.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
