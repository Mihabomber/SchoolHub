package com.school.hub.sync

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.school.hub.core.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PeerStatus(val label: String) {
    FOUND("найден"), CONNECTING("подключаюсь…"), CONNECTED("обмен…"), SYNCED("синхронизирован ✓"), FAILED("ошибка")
}

data class Peer(val endpointId: String, val name: String, val status: PeerStatus)

data class NearbyState(
    val running: Boolean = false,
    val peers: List<Peer> = emptyList(),
    val log: List<String> = emptyList(),
    val receivedTotal: Int = 0,
    val sentTotal: Int = 0,
)


/**
 * Обмен шпаргалками без интернета через Google Nearby Connections.
 * Сам выбирает канал: Bluetooth Classic, BLE, Wi-Fi Direct / точка доступа, LAN.
 *
 * Топология P2P_CLUSTER: каждый с каждым. Устройства одного класса (одинаковый код класса)
 * находят друг друга, обмениваются полными снимками базы, сливают их (LWW),
 * а новые изменения пересылают остальным подключённым — "сарафанное радио".
 *
 * Надёжность: перед стартом сбрасываем старые сессии; устройство с меньшим id подключается сразу,
 * второе — запасным запросом через 4 с, если первое не смогло. Неудачные попытки повторяются до 3 раз.
 */
class NearbySyncManager(
    private val context: Context,
    private val collections: List<SyncCollection>,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    private val client by lazy { Nearby.getConnectionsClient(context) }
    private val strategy = Strategy.P2P_CLUSTER
    private val incoming = mutableMapOf<Long, Pair<String, Payload>>()
    private val outgoing = mutableMapOf<Long, Pair<String, File>>()
    private val connected = mutableSetOf<String>()
    private val requested = mutableSetOf<String>()
    private val retries = mutableMapOf<String, Int>()
    private val names = mutableMapOf<String, String>()
    private var activeServiceId = ""

    /** Счётчики для админ-статистики. */
    var onFileSent: (() -> Unit)? = null
    var onFileReceived: (() -> Unit)? = null

    private val serviceId get() = "com.school.hub.sync.${settings.effectiveClassCode}"
    private val localName get() = "${settings.userName.value.ifBlank { "Ученик" }.take(24).replace("|", " ")}|${settings.deviceId}"

    fun start() {
        if (_state.value.running) return
        // Сбрасываем зависшие сессии от прошлого запуска — иначе старт может молча не сработать.
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        }
        connected.clear(); incoming.clear(); outgoing.clear(); requested.clear(); retries.clear(); names.clear()
        activeServiceId = serviceId
        _state.update { it.copy(running = true, peers = emptyList()) }
        log("Ищу одноклассников с кодом «${settings.effectiveClassCode}»…")
        var failures = 0
        fun failed() {
            failures++
            if (failures >= 2) {
                _state.update { it.copy(running = false) }
                log("Обмен не запустился. Проверь Bluetooth, Wi‑Fi и разрешения.")
            }
        }
        client.startAdvertising(
            localName, activeServiceId, connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { e ->
            val code = (e as? ApiException)?.statusCode
            if (code == 8001) return@addOnFailureListener // уже видим
            log("Не удалось стать видимым: ${explain(e)}")
            failed()
        }
        client.startDiscovery(
            activeServiceId, discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(strategy).build(),
        ).addOnFailureListener { e ->
            val code = (e as? ApiException)?.statusCode
            if (code == 8002) return@addOnFailureListener // уже ищем
            log("Не удалось начать поиск: ${explain(e)}")
            failed()
        }
    }

    fun stop() {
        if (!_state.value.running) return
        runCatching {
            client.stopAdvertising()
            client.stopDiscovery()
            client.stopAllEndpoints()
        }
        connected.clear(); incoming.clear(); outgoing.clear(); requested.clear(); retries.clear()
        _state.update { it.copy(running = false, peers = emptyList()) }
        log("Обмен остановлен")
    }

    private fun explain(e: Exception): String = when ((e as? ApiException)?.statusCode) {
        8003 -> "уже подключено"
        8007 -> "ошибка Bluetooth — выключи и включи Bluetooth"
        8012 -> "ошибка связи — подойди ближе и попробуй снова"
        8032, 8033, 8034, 8035, 8036, 8037, 8038, 8039 -> "нет разрешения — выдай его в настройках приложения"
        17 -> "обнови сервисы Google Play"
        else -> e.message ?: "неизвестная ошибка"
    }

    private fun requestTo(endpointId: String, name: String) {
        if (!_state.value.running || endpointId in connected || endpointId in requested) return
        requested += endpointId
        setPeer(endpointId, name, PeerStatus.CONNECTING)
        client.requestConnection(localName, endpointId, connectionCallback)
            .addOnFailureListener { e ->
                requested -= endpointId
                if ((e as? ApiException)?.statusCode == 8003) return@addOnFailureListener
                log("Не подключился к $name: ${explain(e)}")
                setPeer(endpointId, name, PeerStatus.FAILED)
                retryLater(endpointId, name)
            }
    }

    private fun retryLater(endpointId: String, name: String) {
        val n = (retries[endpointId] ?: 0) + 1
        if (n > 3) return
        retries[endpointId] = n
        scope.launch {
            delay(3000L * n)
            withContext(Dispatchers.Main) {
                if (_state.value.running && endpointId !in connected && endpointId !in requested && names.containsKey(endpointId)) {
                    log("Повторяю подключение к $name ($n/3)…")
                    requestTo(endpointId, name)
                }
            }
        }
    }

    // ---------------- callbacks ----------------

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (name, remoteId) = parseName(info.endpointName)
            if (remoteId == settings.deviceId) return
            names[endpointId] = name
            setPeer(endpointId, name, PeerStatus.FOUND)
            // Тай-брейк: соединение инициирует устройство с "меньшим" id — без встречных запросов.
            if (settings.deviceId < remoteId) {
                requestTo(endpointId, name)
            } else {
                // Запасной путь: если другое устройство нас не видит (асимметричный поиск), подключаемся сами.
                scope.launch {
                    delay(4000)
                    withContext(Dispatchers.Main) {
                        val peer = _state.value.peers.firstOrNull { it.endpointId == endpointId }
                        if (_state.value.running && endpointId !in connected && endpointId !in requested && peer?.status == PeerStatus.FOUND) {
                            requestTo(endpointId, name)
                        }
                    }
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            names.remove(endpointId)
            if (endpointId !in connected) { requested -= endpointId; removePeer(endpointId) }
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (name, _) = parseName(info.endpointName)
            names[endpointId] = name
            setPeer(endpointId, name, PeerStatus.CONNECTING)
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { e -> log("Не принял соединение от $name: ${explain(e)}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val name = peerName(endpointId)
            requested -= endpointId
            if (result.status.isSuccess) {
                connected += endpointId
                retries.remove(endpointId)
                setPeer(endpointId, name, PeerStatus.CONNECTED)
                log("🔗 Подключено: $name")
                sendSnapshot(endpointId)
            } else {
                setPeer(endpointId, name, PeerStatus.FAILED)
                log("Соединение с $name не удалось (${result.status.statusCode})")
                retryLater(endpointId, name)
            }
        }

        override fun onDisconnected(endpointId: String) {
            val name = peerName(endpointId)
            connected -= endpointId
            requested -= endpointId
            log("Отключился: $name")
            removePeer(endpointId)
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.FILE) incoming[payload.id] = endpointId to payload
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    outgoing.remove(update.payloadId)?.let { (_, file) -> file.delete() }
                    val entry = incoming.remove(update.payloadId) ?: return
                    scope.launch { handleIncoming(entry.first, entry.second) }
                }
                PayloadTransferUpdate.Status.FAILURE, PayloadTransferUpdate.Status.CANCELED -> {
                    incoming.remove(update.payloadId)
                    outgoing.remove(update.payloadId)?.let { (_, file) -> file.delete() }
                    log("Передача прервана (${peerName(endpointId)})")
                }
                else -> Unit
            }
        }
    }

    // ---------------- обмен данными ----------------

    private fun sendSnapshot(endpointId: String) {
        scope.launch {
            runCatching {
                val data = collections.associate { it.name to it.exportAll() }
                val count = data.values.sumOf { it.size }
                val file = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "nearby_out").apply { mkdirs() }
                    SyncCodec.write(
                        SyncPacket(2, settings.deviceId, settings.effectiveClassCode, data),
                        File(dir, "snapshot_${System.nanoTime()}.json.gz"),
                    )
                }
                val payload = Payload.fromFile(file)
                withContext(Dispatchers.Main) {
                    outgoing[payload.id] = endpointId to file
                    client.sendPayload(endpointId, payload)
                }
                _state.update { it.copy(sentTotal = it.sentTotal + count) }
                onFileSent?.invoke()
                log("📤 Отправил $count записей → ${peerName(endpointId)}")
            }.onFailure { log("Ошибка отправки: ${it.message}") }
        }
    }

    private suspend fun handleIncoming(endpointId: String, payload: Payload) {
        runCatching {
            val packet = withContext(Dispatchers.IO) {
                openPayload(payload)?.use { SyncCodec.read(it) }
            } ?: error("пустой файл")
            if (packet.classCode != settings.effectiveClassCode) {
                log("Пропущено: другой класс (${packet.classCode})"); return@runCatching
            }
            onFileReceived?.invoke()
            var changed = 0
            val incomingData = packet.collections.orEmpty()
            for (c in collections) {
                incomingData[c.name]?.let { changed += c.merge(it, markDirty = true) }
            }
            _state.update { it.copy(receivedTotal = it.receivedTotal + changed) }
            setPeer(endpointId, peerName(endpointId), PeerStatus.SYNCED)
            log("📥 От ${peerName(endpointId)}: новых/обновлённых $changed")
            // Пересылаем свежие данные остальным подключённым — изменения расходятся по всей сети.
            if (changed > 0) {
                withContext(Dispatchers.Main) { connected.filter { it != endpointId }.toList() }
                    .forEach { sendSnapshot(it) }
            }
        }.onFailure { log("Не удалось прочитать данные: ${it.message}") }
        cleanupPayload(payload)
    }

    @Suppress("DEPRECATION")
    private fun openPayload(payload: Payload): InputStream? {
        val f = payload.asFile() ?: return null
        val uri = runCatching { f.asUri() }.getOrNull()
        if (uri != null) return context.contentResolver.openInputStream(uri)
        return f.asJavaFile()?.inputStream()
    }

    @Suppress("DEPRECATION")
    private fun cleanupPayload(payload: Payload) {
        runCatching {
            val f = payload.asFile() ?: return
            val uri = runCatching { f.asUri() }.getOrNull()
            if (uri != null) context.contentResolver.delete(uri, null, null) else f.asJavaFile()?.delete()
        }
    }

    // ---------------- helpers ----------------

    private fun parseName(raw: String): Pair<String, String> {
        val idx = raw.lastIndexOf('|')
        return if (idx > 0) raw.substring(0, idx) to raw.substring(idx + 1) else raw to raw
    }

    private fun peerName(endpointId: String) =
        _state.value.peers.firstOrNull { it.endpointId == endpointId }?.name ?: names[endpointId] ?: "устройство"

    private fun setPeer(endpointId: String, name: String, status: PeerStatus) {
        _state.update { s ->
            val others = s.peers.filter { it.endpointId != endpointId }
            s.copy(peers = others + Peer(endpointId, name, status))
        }
    }

    private fun removePeer(endpointId: String) {
        _state.update { s -> s.copy(peers = s.peers.filter { it.endpointId != endpointId }) }
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _state.update { it.copy(log = (listOf("$time  $msg") + it.log).take(30)) }
    }

    companion object {
        fun requiredPermissions(): Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.NEARBY_WIFI_DEVICES)
            if (Build.VERSION.SDK_INT <= 32) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }.toTypedArray()

        /** Геолокация на Android 13+ не обязательна, но ускоряет поиск на части телефонов. */
        fun optionalPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            else emptyArray()

        fun needsLocation(): Boolean = Build.VERSION.SDK_INT <= 32

        /** null — в телефоне нет Bluetooth. */
        fun isBluetoothOn(ctx: Context): Boolean? = runCatching {
            val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return@runCatching null
            adapter.isEnabled
        }.getOrNull()

        fun isLocationOn(ctx: Context): Boolean = runCatching {
            val lm = ctx.getSystemService(LocationManager::class.java) ?: return@runCatching true
            if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
            else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(true)
    }
}
