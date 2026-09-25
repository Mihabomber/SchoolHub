package com.school.hub.feature.settings

import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.BuildConfig
import com.school.hub.core.data.SettingsStore
import com.school.hub.navigation.AppViewModelFactory
import com.school.hub.reminders.ReminderScheduler

class SettingsViewModel(val settings: SettingsStore, private val reminders: ReminderScheduler, val auth: com.school.hub.feature.auth.AuthRepository) : ViewModel() {
    fun logout() = viewModelScope.launch { auth.logout() }
    var name by mutableStateOf(settings.userName.value)
        private set
    var classCode by mutableStateOf(settings.classCode.value)
        private set

    fun onName(v: String) { name = v; settings.setUserName(v) }
    fun onClass(v: String) { classCode = v; settings.setClassCode(v) }
    fun lessonReminders(v: Boolean) { settings.setLessonReminders(v); reminders.requestReschedule() }
    fun homeworkReminders(v: Boolean) { settings.setHomeworkReminders(v); reminders.requestReschedule() }
    fun minutes(v: Int) { settings.setRemindMinutes(v); reminders.requestReschedule() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSync: () -> Unit,
    vm: SettingsViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val st = vm.settings
    val theme by st.themeMode.collectAsStateWithLifecycle()
    val dynamic by st.dynamicColor.collectAsStateWithLifecycle()
    val lessons by st.lessonReminders.collectAsStateWithLifecycle()
    val hw by st.homeworkReminders.collectAsStateWithLifecycle()
    val minutes by st.remindMinutes.collectAsStateWithLifecycle()
    val wifi by st.wifiOnly.collectAsStateWithLifecycle()
    val glass by st.liquidGlass.collectAsStateWithLifecycle()
    val font by st.fontChoice.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Настройки") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        )
    }) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("👤 Профиль") {
                val acc = vm.auth.profile
                Text(acc?.fullName ?: vm.name, style = MaterialTheme.typography.titleLarge)
                Text((acc?.email ?: "") + if (acc?.isAdmin == true) " · 🛡 админ" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    vm.classCode, vm::onClass, label = { Text("Код класса") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Одинаковый у всех одноклассников — по нему делятся шпоры, расписание и ДЗ") },
                )
            }
            Section("🎨 Оформление") {
                Text("Тема", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("Системная", "Светлая", "Тёмная").forEachIndexed { i, label ->
                        SegmentedButton(selected = theme == i, onClick = { st.setThemeMode(i) }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                    }
                }
                SwitchRow("Цвета из обоев (Android 12+)", dynamic) { st.setDynamicColor(it) }
                SwitchRow("💧 Жидкое стекло (живой фон, прозрачные карточки)", glass) { st.setLiquidGlass(it) }
                Text("Шрифт", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    com.school.hub.core.ui.theme.FontChoices.forEachIndexed { i, label ->
                        SegmentedButton(selected = font == i, onClick = { st.setFontChoice(i) }, shape = SegmentedButtonDefaults.itemShape(i, 4)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
            }
            Section("🔔 Напоминания") {
                SwitchRow("Перед уроком", lessons, vm::lessonReminders)
                if (lessons) {
                    Text("За $minutes мин до звонка", style = MaterialTheme.typography.bodyMedium)
                    Slider(minutes.toFloat(), { vm.minutes(it.toInt()) }, valueRange = 1f..15f, steps = 13)
                }
                SwitchRow("Вечером о несделанной домашке", hw, vm::homeworkReminders)
            }
            Section("📶 Сеть") {
                SwitchRow("Качать модели ИИ и языки только по Wi-Fi", wifi) { st.setWifiOnly(it) }
                OutlinedButton(onClick = onOpenSync, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Синхронизация и обмен по Bluetooth", Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("Выйти из аккаунта") }
            Text(
                "Парта ${BuildConfig.VERSION_NAME} · ИИ-движок: ${if (BuildConfig.WITH_LLAMA) "встроен" else "не встроен"}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    com.school.hub.core.ui.theme.GlassCard(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
