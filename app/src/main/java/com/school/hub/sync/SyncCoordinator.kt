package com.school.hub.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class CloudStatus(
    val syncing: Boolean = false,
    val message: String = "",
    val isError: Boolean = false,
    val lastSuccessAt: Long = 0L,
)

/**
 * Стратегия: есть интернет и сервер → синхронизируемся автоматически
 * (при старте, при появлении сети и после каждой правки).
 * Нет интернета → пользователь включает обмен по Bluetooth/Wi-Fi Direct (NearbySyncManager).
 */
class SyncCoordinator(
    context: Context,
    private val cloud: CloudSync,
    private val repo: CheatSheetRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private val lock = Mutex()

    private val _online = MutableStateFlow(isOnlineNow())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _status = MutableStateFlow(CloudStatus())
    val status: StateFlow<CloudStatus> = _status.asStateFlow()

    private fun isOnlineNow(): Boolean = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    @OptIn(FlowPreview::class)
    fun start() {
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val ok = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    val was = _online.value
                    _online.value = ok
                    if (ok && !was) requestSync()
                }

                override fun onLost(network: Network) {
                    _online.value = isOnlineNow()
                }
            })
        }
        scope.launch {
            repo.observeDirtyCount().debounce(2_000).collect { if (it > 0) requestSync() }
        }
        requestSync()
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    private suspend fun syncNow() {
        if (settings.serverUrl.value.isBlank()) {
            _status.update { it.copy(syncing = false, message = "Сервер не указан — работает обмен по Bluetooth", isError = false) }
            return
        }
        if (!_online.value) {
            _status.update { it.copy(syncing = false, message = "Нет интернета — используй обмен рядом", isError = false) }
            return
        }
        if (!lock.tryLock()) return
        try {
            _status.update { it.copy(syncing = true, message = "Синхронизация…", isError = false) }
            cloud.sync()
                .onSuccess { msg ->
                    _status.value = CloudStatus(false, "Готово: $msg", false, System.currentTimeMillis())
                }
                .onFailure { e ->
                    _status.update { it.copy(syncing = false, message = "Ошибка: ${e.message ?: e.javaClass.simpleName}", isError = true) }
                }
        } finally {
            lock.unlock()
        }
    }
}
