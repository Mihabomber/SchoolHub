package com.school.hub.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.school.hub.core.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
 * Стратегия: при открытом приложении есть интернет → синхронизируемся автоматически
 * (при старте, при появлении сети и после каждой правки) через бесплатный MQTT-брокер;
 * если задан свой сервер — дополнительно шлём и на него.
 * Нет интернета → пользователь включает обмен по Bluetooth/Wi-Fi Direct (NearbySyncManager).
 */
class SyncCoordinator(
    context: Context,
    private val mqtt: MqttSync,
    private val cloud: CloudSync,
    private val collections: List<SyncCollection>,
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
            combine(collections.map { it.observeDirtyCount() }) { counts -> counts.sum() }
                .debounce(2_000)
                .collect { if (it > 0) requestSync() }
        }
        requestSync()
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    private suspend fun syncNow() {
        if (!_online.value) {
            _status.update { it.copy(syncing = false, message = "Нет интернета — синхронизируемся при появлении сети или обменяйся по Bluetooth", isError = false) }
            return
        }
        if (!lock.tryLock()) return
        try {
            _status.update { it.copy(syncing = true, message = "Синхронизация…", isError = false) }
            val done = mutableListOf<String>()
            val errors = mutableListOf<String>()

            mqtt.sync()
                .onSuccess { done += it }
                .onFailure { errors += it.message ?: it.javaClass.simpleName }

            if (settings.serverUrl.value.isNotBlank()) {
                cloud.sync()
                    .onSuccess { done += "сервер: $it" }
                    .onFailure { errors += "сервер: " + (it.message ?: it.javaClass.simpleName) }
            }

            _status.value = when {
                done.isNotEmpty() && errors.isEmpty() ->
                    CloudStatus(false, "Готово: ${done.joinToString(" · ")}", false, System.currentTimeMillis())
                done.isNotEmpty() ->
                    CloudStatus(false, "Готово: ${done.joinToString(" · ")} (не получилось: ${errors.joinToString("; ")})", false, System.currentTimeMillis())
                else ->
                    CloudStatus(false, "Ошибка: ${errors.joinToString("; ")}", true, System.currentTimeMillis())
            }
        } finally {
            lock.unlock()
        }
    }
}
