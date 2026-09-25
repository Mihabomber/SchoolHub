package com.school.hub.sync

import com.school.hub.core.data.SettingsStore
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocketFactory

/**
 * Синхронизация через публичный MQTT-брокер: бесплатно, на любом расстоянии,
 * без регистрации и своего сервера.
 *
 * Как это работает:
 *  - тема  = sh/v1/<sha256(код класса)[0..16]>/<коллекция>/<uuid записи>;
 *  - сообщение = gzip(JSON) зашифрованный AES-256-GCM ключом из кода класса (PBKDF2);
 *  - каждая запись публикуется RETAINED — брокер хранит последнее состояние,
 *    поэтому любое устройство при входе мгновенно получает полный снимок класса,
 *    даже если оно заходит через неделю и устройства никогда не были онлайн вместе.
 */
class MqttSync(
    private val collections: List<SyncCollection>,
    private val settings: SettingsStore,
) {
    private val random = SecureRandom()

    /** Код класса → ключ (кэш, чтобы не считать PBKDF2 на каждый цикл). */
    @Volatile private var cachedKey: Pair<String, ByteArray>? = null

    suspend fun sync(): Result<String> = withContext(Dispatchers.IO) { runCatching { doSync() } }

    private suspend fun doSync(): String {
        val code = settings.effectiveClassCode
        require(code.isNotBlank()) { "Не задан код класса" }
        val room = sha256Hex(code).take(ROOM_LEN)
        val key = deriveKey(code, room)
        val brokers = buildList {
            settings.mqttBroker.value.trim().takeIf { it.isNotBlank() }?.let { add(normalizeBroker(it)) }
            addAll(DEFAULT_BROKERS)
        }.distinct()

        var lastError: Exception? = null
        for (broker in brokers) {
            try {
                return syncVia(broker, room, key)
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("нет связи")
    }

    private suspend fun syncVia(broker: String, room: String, key: ByteArray): String {
        val clientId = "sh-${settings.deviceId.takeLast(6)}-${randomHex(4)}"
        val client = MqttClient(broker, clientId, MemoryPersistence()).apply { timeToWait = 30_000L }
        val received = ConcurrentHashMap<String, ByteArray>()
        val lastIn = AtomicLong(System.currentTimeMillis())
        try {
            client.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {}
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    if (topic != null && message != null) {
                        received[topic] = message.payload
                        lastIn.set(System.currentTimeMillis())
                    }
                }
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            val opts = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 30
                mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
                socketFactory = SSLSocketFactory.getDefault()
                isHttpsHostnameVerificationEnabled = true
            }
            client.connect(opts)
            client.subscribe("$ROOT/$room/#", 1)
            lastIn.set(System.currentTimeMillis())

            // Ждём полный снимок: тишина QUIET_MS после последнего сообщения, но не дольше таймаута.
            val deadline = System.currentTimeMillis() + SNAPSHOT_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline &&
                System.currentTimeMillis() - lastIn.get() < QUIET_MS
            ) Thread.sleep(100)

            var receivedCount = 0
            val merged = mutableSetOf<String>()
            suspend fun drainReceived(): Int {
                var n = 0
                for (topic in received.keys.toList()) {
                    if (!merged.add(topic)) continue
                    val parts = topic.split("/")
                    if (parts.size != 5 || parts[0] != "sh" || parts[1] != "v1" || parts[2] != room) continue
                    val col = collections.find { it.name == parts[3] } ?: continue
                    val payload = received[topic] ?: continue
                    val item = runCatching { decodeItem(payload, key) }.getOrNull() ?: continue
                    n += col.merge(listOf(item), markDirty = false)
                }
                return n
            }
            receivedCount += drainReceived()

            // Отправляем свои изменения (только dirty), по одной записи с небольшой паузой.
            var sent = 0
            for (col in collections) {
                val dirty = col.exportDirty()
                if (dirty.isEmpty()) continue
                for (item in dirty) {
                    val uuid = item.get("uuid")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                    var payload = encodeItem(item, key)
                    if (payload.size > MAX_PAYLOAD && item.has("imageBase64")) {
                        item.remove("imageBase64")
                        payload = encodeItem(item, key)
                    }
                    // Синхронный publish блокируется, пока брокер не подтвердит (QoS 1).
                    client.publish(topicFor(room, col.name, uuid), payload, 1, true)
                    sent++
                    Thread.sleep(PACING_MS)
                }
                col.markClean(dirty)
            }

            // Короткое окно «вживую»: устройства, которые онлайн прямо сейчас, пришлют ответы.
            val liveDeadline = System.currentTimeMillis() + LIVE_WINDOW_MS
            while (System.currentTimeMillis() < liveDeadline &&
                System.currentTimeMillis() - lastIn.get() < QUIET_MS
            ) Thread.sleep(100)
            receivedCount += drainReceived()

            runCatching { client.disconnect(2_000) }
            runCatching { client.close() }
            return "отправлено $sent, получено $receivedCount"
        } catch (e: Exception) {
            runCatching { if (client.isConnected) client.disconnect(1_000) }
            runCatching { client.close() }
            throw e
        }
    }

    // ---------- криптография и формат ----------

    private fun encodeItem(item: JsonObject, key: ByteArray): ByteArray =
        encrypt(gzip(SyncJson.gson.toJson(item).toByteArray(Charsets.UTF_8)), key)

    private fun decodeItem(payload: ByteArray, key: ByteArray): JsonObject {
        require(payload.size > 1 + IV_LEN && payload[0] == FORMAT_V1) { "неизвестный формат" }
        val iv = payload.copyOfRange(1, 1 + IV_LEN)
        val ct = payload.copyOfRange(1 + IV_LEN, payload.size)
        val json = gunzip(decrypt(ct, key, iv))
        return SyncJson.gson.fromJson(String(json, Charsets.UTF_8), JsonObject::class.java)
    }

    private fun encrypt(plain: ByteArray, key: ByteArray): ByteArray {
        val iv = ByteArray(IV_LEN).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return byteArrayOf(FORMAT_V1) + iv + cipher.doFinal(plain)
    }

    private fun decrypt(ct: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(ct)
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { bos -> GZIPOutputStream(bos).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    private fun deriveKey(code: String, room: String): ByteArray {
        cachedKey?.let { if (it.first == code) return it.second }
        val spec = PBEKeySpec(code.toCharArray(), ("schoolhub-sync-v1|$room").toByteArray(Charsets.UTF_8), KEY_ITER, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        cachedKey = code to key
        return key
    }

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun randomHex(n: Int): String {
        val bytes = ByteArray(n).also(random::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun topicFor(room: String, collection: String, uuid: String): String = "$ROOT/$room/$collection/$uuid"

    private fun normalizeBroker(raw: String): String {
        var s = raw.trim().trimEnd('/')
        if (!s.contains("://")) {
            val host = s.substringBefore('/')
            val hasPort = host.contains(':')
            s = "ssl://$s" + if (hasPort) "" else ":8883"
        }
        if (s.startsWith("tcp://")) s = "ssl://" + s.removePrefix("tcp://")
        return s
    }

    companion object {
        private const val ROOT = "sh/v1"
        private const val ROOM_LEN = 16
        private const val FORMAT_V1: Byte = 1
        private const val IV_LEN = 12
        private const val KEY_ITER = 50_000
        private const val QUIET_MS = 1_500L
        private const val SNAPSHOT_TIMEOUT_MS = 15_000L
        private const val LIVE_WINDOW_MS = 3_000L
        private const val PACING_MS = 30L
        private const val MAX_PAYLOAD = 12L * 1024 * 1024

        /** Открытые бесплатные брокеры (анонимное подключение, TLS). */
        private val DEFAULT_BROKERS = listOf(
            "ssl://broker.hivemq.com:8883",
            "ssl://broker.emqx.io:8883",
        )
    }
}
