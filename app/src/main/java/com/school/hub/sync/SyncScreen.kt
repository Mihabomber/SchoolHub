package com.school.hub.sync

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.ui.components.GradientIcon
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.navigation.AppViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    vm: SyncViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val context = LocalContext.current
    val nearby by vm.nearbyState.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val cloud by vm.cloudStatus.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) { permissionDenied = false; vm.startNearby() } else permissionDenied = true
    }
    val startNearby = {
        val perms = NearbySyncManager.requiredPermissions()
        val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.startNearby() else permLauncher.launch(missing.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Обмен шпаргалками") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---------- Профиль ----------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientIcon(Icons.Filled.Groups, AppGradients.Violet)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Твой класс", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "У всех одноклассников должен быть одинаковый код",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = vm.userName, onValueChange = vm::onUserName, singleLine = true,
                    label = { Text("Твоё имя") }, leadingIcon = { Icon(Icons.Filled.Person, null) },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = vm.classCode, onValueChange = vm::onClassCode, singleLine = true,
                    label = { Text("Код класса") }, placeholder = { Text("например: school57-9b") },
                    supportingText = { Text("Только латиница, цифры, - и _") },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
            }

            // ---------- Рядом: Bluetooth / Wi-Fi Direct ----------
            NearbyCard(nearby, permissionDenied, onStart = startNearby, onStop = vm::stopNearby)

            // ---------- Интернет ----------
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientIcon(if (online) Icons.Filled.Cloud else Icons.Filled.CloudOff, if (online) AppGradients.Ocean else AppGradients.Night)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Через интернет", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (online) "Интернет есть — синхронизация автоматическая" else "Интернета нет",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = vm.serverUrl, onValueChange = vm::onServerUrl, singleLine = true,
                    label = { Text("Адрес сервера класса") },
                    placeholder = { Text("https://my-class.onrender.com") },
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
                if (cloud.message.isNotBlank()) {
                    Text(
                        cloud.message, style = MaterialTheme.typography.bodySmall,
                        color = if (cloud.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = vm::syncCloud, enabled = !cloud.syncing && online && vm.serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                ) {
                    if (cloud.syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Sync, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Синхронизировать сейчас")
                }
            }

            Text(
                "Как это работает: каждое устройство хранит все шпаргалки класса. При встрече телефоны " +
                    "обмениваются изменениями и передают их дальше, поэтому новая шпаргалка доходит до всех, " +
                    "даже если вы никогда не были рядом одновременно.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun NearbyCard(state: NearbyState, permissionDenied: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(AppGradients.Violet)).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).scale(if (state.running) scale else 1f)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Bluetooth, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Рядом, без интернета", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(
                        "Bluetooth · Wi-Fi Direct · локальная сеть",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
            if (state.running) {
                Text(
                    "📥 получено ${state.receivedTotal}   📤 отправлено ${state.sentTotal}",
                    color = Color.White, style = MaterialTheme.typography.labelLarge,
                )
                if (state.peers.isEmpty()) {
                    Text("Ищу одноклассников рядом…", color = Color.White.copy(alpha = 0.85f))
                    LinearProgressIndicator(
                        Modifier.fillMaxWidth().clip(CircleShape),
                        color = Color.White, trackColor = Color.White.copy(alpha = 0.25f),
                    )
                }
                state.peers.forEach { p ->
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.15f)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📱", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(10.dp))
                            Text(p.name, color = Color.White, modifier = Modifier.weight(1f))
                            Text(p.status.label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (permissionDenied) {
                Text(
                    "Нужны разрешения на Bluetooth и устройства поблизости — без них обмен не работает.",
                    color = Color.White, style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = if (state.running) onStop else onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF5B4CF0)),
            ) {
                Icon(if (state.running) Icons.Filled.Stop else Icons.Filled.Sync, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.running) "Остановить обмен" else "Начать обмен с классом")
            }
            if (state.log.isNotEmpty()) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.Black.copy(alpha = 0.18f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        state.log.take(6).forEach {
                            Text(
                                it, color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }
            Text(
                "Включи Bluetooth и Wi-Fi (интернет не нужен), а на Android 8–11 ещё и геолокацию.",
                color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
