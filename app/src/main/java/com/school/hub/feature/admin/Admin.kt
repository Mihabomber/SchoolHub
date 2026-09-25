package com.school.hub.feature.admin

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.school.hub.core.ui.theme.GlassCard
import com.school.hub.core.util.relativeTime
import com.school.hub.feature.auth.AuthRepository
import com.school.hub.feature.auth.UserProfile
import com.school.hub.feature.auth.toProfile
import com.school.hub.feature.social.JsonStore
import com.school.hub.feature.social.StatsDto
import com.school.hub.feature.social.StatsTracker
import com.school.hub.navigation.AppViewModelFactory
import com.school.hub.sync.SyncJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class BanEntry(val email: String, val reason: String, val by: String, val at: Long)

/**
 * Статистика уходит в Firestore:
 *  - своя — сразу; без сети Firestore держит запись в очереди и отправит при подключении к Wi-Fi/интернету;
 *  - чужая (пришла по Bluetooth/Wi-Fi Direct от одноклассников) — её выгружает любой телефон, где появился интернет.
 * Админ рядом без интернета видит статистику из локального хранилища (обмен по Bluetooth).
 */
class StatsUploader(context: Context, private val store: JsonStore<StatsDto>, private val auth: AuthRepository, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("stats_upload", Context.MODE_PRIVATE)

    @OptIn(FlowPreview::class)
    fun start() {
        if (!auth.available) return
        scope.launch {
            combine(store.items, auth.state) { m, _ -> m }.debounce(5_000).collect { m ->
                if (!auth.isSignedIn) return@collect
                val db = FirebaseFirestore.getInstance()
                val e = prefs.edit()
                for (s in m.values) {
                    if (s.updatedAt <= prefs.getLong(s.uuid, 0L)) continue
                    val map: Map<String, Any?> = SyncJson.gson.fromJson(SyncJson.gson.toJsonTree(s), Map::class.java)
                        .entries.associate { it.key.toString() to it.value }
                    db.collection("stats").document(s.uuid).set(map, SetOptions.merge()) // очередь офлайн — отправится само
                    e.putLong(s.uuid, s.updatedAt)
                }
                e.apply()
            }
        }
    }
}

class AdminRepository(private val auth: AuthRepository) {
    private val db get() = FirebaseFirestore.getInstance()

    val users: Flow<List<UserProfile>> = callbackFlow {
        val reg = db.collection("users").addSnapshotListener { s, _ ->
            if (s != null) trySend(s.documents.map { it.toProfile() }.sortedByDescending { it.lastSeen })
        }
        awaitClose { reg.remove() }
    }

    val bans: Flow<List<BanEntry>> = callbackFlow {
        val reg = db.collection("banned").addSnapshotListener { s, _ ->
            if (s != null) trySend(s.documents.map {
                BanEntry(it.id, it.getString("reason").orEmpty(), it.getString("by").orEmpty(), it.getLong("at") ?: 0)
            }.sortedByDescending { it.at })
        }
        awaitClose { reg.remove() }
    }

    val cloudStats: Flow<List<StatsDto>> = callbackFlow {
        val reg = db.collection("stats").addSnapshotListener { s, _ ->
            if (s != null) trySend(s.documents.mapNotNull { d ->
                runCatching { SyncJson.gson.fromJson(SyncJson.gson.toJsonTree(d.data), StatsDto::class.java) }.getOrNull()
            })
        }
        awaitClose { reg.remove() }
    }

    private val me get() = auth.profile?.email.orEmpty()

    suspend fun block(email: String, reason: String): String? = runCatching {
        val e = email.trim().lowercase()
        require(e.contains('@')) { "Введи почту" }
        require(e != me) { "Нельзя заблокировать себя" }
        val why = reason.ifBlank { "Ненастоящие данные / нарушение правил" }
        db.collection("banned").document(e).set(mapOf("reason" to why, "by" to me, "at" to System.currentTimeMillis())).await()
        db.collection("users").whereEqualTo("email", e).get().await().documents.forEach {
            it.reference.update(mapOf("blocked" to true, "blockReason" to why)).await()
        }
    }.exceptionOrNull()?.let { it.message ?: "Ошибка" }

    suspend fun unblock(email: String): String? = runCatching {
        val e = email.trim().lowercase()
        db.collection("banned").document(e).delete().await()
        db.collection("users").whereEqualTo("email", e).get().await().documents.forEach {
            it.reference.update(mapOf("blocked" to false, "blockReason" to "")).await()
        }
    }.exceptionOrNull()?.let { it.message ?: "Ошибка" }

    suspend fun setAdmin(u: UserProfile, admin: Boolean): String? = runCatching {
        db.collection("users").document(u.uid).update("role", if (admin) "admin" else "user").await()
    }.exceptionOrNull()?.let { it.message ?: "Ошибка" }
}

class AdminViewModel(private val repo: AdminRepository, local: JsonStore<StatsDto>, private val tracker: StatsTracker) : ViewModel() {
    val users = repo.users.catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val bans = repo.bans.catch { emit(emptyList()) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    /** Облако + то, что пришло по Bluetooth: берём самую свежую запись каждого устройства. */
    val stats = combine(repo.cloudStats.catch { emit(emptyList()) }.onStart { emit(emptyList()) }, local.items) { cloud, loc ->
        (cloud + loc.values).groupBy { it.uuid }.map { (_, v) -> v.maxBy { it.updatedAt } }.sortedByDescending { it.updatedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var message by mutableStateOf<String?>(null)

    fun refresh() = viewModelScope.launch { tracker.publish() }
    fun block(e: String, r: String) = viewModelScope.launch { message = repo.block(e, r) ?: "Заблокирован: $e" }
    fun unblock(e: String) = viewModelScope.launch { message = repo.unblock(e) ?: "Разблокирован: $e" }
    fun setAdmin(u: UserProfile, a: Boolean) = viewModelScope.launch { message = repo.setAdmin(u, a) ?: if (a) "${u.email} теперь админ" else "${u.email} больше не админ" }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(vm: AdminViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    var tab by rememberSaveableInt()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    LaunchedEffect(Unit) { vm.refresh() }
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                TopAppBar(title = { Text("🛡 Админ-панель") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent))
                TabRow(tab, containerColor = androidx.compose.ui.graphics.Color.Transparent) {
                    listOf("Статистика", "Аккаунты", "Блокировки").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
                }
            }
        },
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (tab) {
                0 -> StatsTab(vm)
                1 -> AccountsTab(vm)
                else -> BansTab(vm)
            }
        }
    }
}

@Composable
private fun rememberSaveableInt() = androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

@Composable
private fun StatsTab(vm: AdminViewModel) {
    val list by vm.stats.collectAsStateWithLifecycle()
    val now = System.currentTimeMillis()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("📱", "${list.size}", "устройств", Modifier.weight(1f))
                Stat("🟢", "${list.count { now - it.updatedAt < 86_400_000L }}", "за сутки", Modifier.weight(1f))
                Stat("🤖", "${list.sumOf { it.aiRequests }}", "запросов ИИ", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("📤", "${list.sumOf { it.filesSent }}", "файлов отпр.", Modifier.weight(1f))
                Stat("📥", "${list.sumOf { it.filesReceived }}", "файлов пол.", Modifier.weight(1f))
                Stat("💬", "${list.sumOf { it.chatMessages }}", "сообщений", Modifier.weight(1f))
            }
        }
        item {
            Text("Данные приходят сами: через интернет/Wi-Fi (облако Firebase) или по Bluetooth от любого телефона класса.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list, key = { it.uuid }) { u ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (now - u.updatedAt < 600_000) "🟢 " else "⚪ ")
                        Text(u.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("v${u.appVersion}", style = MaterialTheme.typography.labelSmall)
                    }
                    if (u.email.isNotBlank()) Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("${u.device} · класс ${u.classCode}", style = MaterialTheme.typography.bodySmall)
                    Text("Открытий ${u.opens} · ИИ ${u.aiRequests} · 📤 ${u.filesSent} · 📥 ${u.filesReceived} · перевод ${u.translations} · калькулятор ${u.calcSolved} · чат ${u.chatMessages} · игры ${u.gamesPlayed}",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Обновлено ${relativeTime(u.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AccountsTab(vm: AdminViewModel) {
    val users by vm.users.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    var toBlock by remember { mutableStateOf<UserProfile?>(null) }
    val shown = users.filter { q.isBlank() || it.email.contains(q, true) || it.fullName.contains(q, true) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(q, { q = it }, label = { Text("Поиск по имени или почте") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Всего аккаунтов: ${users.size} · заблокировано: ${users.count { it.blocked }} · пароли хранятся только в виде хеша и не видны",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        items(shown, key = { it.uid }) { u ->
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.fullName.ifBlank { "— без имени —" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (u.isAdmin) AssistChip({}, { Text("админ") })
                        if (u.blocked) AssistChip({}, { Text("⛔ блок") })
                    }
                    Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("Вход: ${u.provider.ifBlank { "?" }} · ${u.device} · v${u.appVersion} · был(а) ${relativeTime(u.lastSeen)}", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (u.blocked) OutlinedButton(onClick = { vm.unblock(u.email) }) { Text("Разблокировать") }
                        else OutlinedButton(onClick = { toBlock = u }) { Text("Заблокировать") }
                        TextButton(onClick = { vm.setAdmin(u, u.role != "admin") }) { Text(if (u.role == "admin") "Снять админа" else "Сделать админом") }
                    }
                }
            }
        }
    }
    toBlock?.let { u -> BlockDialog(u.email, onDismiss = { toBlock = null }) { r -> vm.block(u.email, r); toBlock = null } }
}

@Composable
private fun BansTab(vm: AdminViewModel) {
    val bans by vm.bans.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Заблокировать по почте", style = MaterialTheme.typography.titleSmall)
                    Text("Работает даже если человек ещё не зарегистрирован.", style = MaterialTheme.typography.labelSmall)
                    OutlinedTextField(email, { email = it.trim() }, label = { Text("Почта") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { ask = true }, enabled = email.contains('@'), modifier = Modifier.fillMaxWidth()) { Text("Заблокировать") }
                }
            }
        }
        items(bans, key = { it.email }) { b ->
            ListItem(
                headlineContent = { Text(b.email) },
                supportingContent = { Text("${b.reason} · ${b.by} · ${relativeTime(b.at)}") },
                trailingContent = { TextButton(onClick = { vm.unblock(b.email) }) { Text("Снять") } },
            )
        }
    }
    if (ask) BlockDialog(email, onDismiss = { ask = false }) { r -> vm.block(email, r); ask = false; email = "" }
}

@Composable
private fun BlockDialog(email: String, onDismiss: () -> Unit, onBlock: (String) -> Unit) {
    var reason by remember { mutableStateOf("Ненастоящие имя и фамилия") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заблокировать $email?") },
        text = { OutlinedTextField(reason, { reason = it }, label = { Text("Причина") }) },
        confirmButton = { Button(onClick = { onBlock(reason) }) { Text("Заблокировать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun Stat(emoji: String, value: String, label: String, modifier: Modifier) {
    GlassCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji); Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

