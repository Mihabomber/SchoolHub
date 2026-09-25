package com.school.hub.feature.social

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.school.hub.BuildConfig
import com.school.hub.core.data.SettingsStore
import com.school.hub.sync.SyncCollection
import com.school.hub.sync.SyncJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID

interface Syncable {
    val uuid: String
    val updatedAt: Long
}

/** Статистика одного устройства — уходит админу по интернету, Wi-Fi или Bluetooth вместе с остальной синхронизацией. */
data class StatsDto(
    override val uuid: String,
    val name: String = "",
    val classCode: String = "",
    val device: String = "",
    val appVersion: String = "",
    val opens: Int = 0,
    val filesSent: Int = 0,
    val filesReceived: Int = 0,
    val aiRequests: Int = 0,
    val translations: Int = 0,
    val calcSolved: Int = 0,
    val chatMessages: Int = 0,
    val gamesPlayed: Int = 0,
    val email: String = "",
    val firstSeen: Long = 0,
    override val updatedAt: Long = 0,
) : Syncable

data class RequestDto(
    override val uuid: String,
    val fromDevice: String,
    val fromName: String,
    val subject: String,
    val grade: String,
    val text: String,
    val createdAt: Long,
    val closed: Boolean = false,
    override val updatedAt: Long = 0,
) : Syncable

data class ChatDto(
    override val uuid: String,
    val fromDevice: String,
    val fromName: String,
    val text: String,
    /** JPEG в base64, до ~150 КБ. */
    val image: String? = null,
    val replyTo: String? = null,
    val replyPreview: String? = null,
    val requestId: String? = null,
    val createdAt: Long,
    val deleted: Boolean = false,
    override val updatedAt: Long = 0,
) : Syncable

/** Маленькое хранилище «LWW-словарь в JSON-файле» + синхронизация. Не требует миграций БД. */
class JsonStore<T : Syncable>(
    private val file: File,
    private val type: Class<T>,
    override val name: String,
    private val scope: CoroutineScope,
    private val maxItems: Int = 2000,
) : SyncCollection {
    private val lock = Mutex()
    private val _items = MutableStateFlow<Map<String, T>>(emptyMap())
    val items: StateFlow<Map<String, T>> = _items.asStateFlow()
    private val dirty = MutableStateFlow<Set<String>>(emptySet())

    private data class Disk<T>(val items: List<T>, val dirty: Set<String>)

    init {
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (file.exists()) {
                    val listType = TypeToken.getParameterized(List::class.java, type).type
                    val root = SyncJson.gson.fromJson(file.readText(), JsonObject::class.java)
                    val list: List<T> = SyncJson.gson.fromJson(root.getAsJsonArray("items"), listType)
                    val d: Set<String> = root.getAsJsonArray("dirty")?.map { it.asString }?.toSet().orEmpty()
                    _items.value = list.associateBy { it.uuid }
                    dirty.value = d
                }
            }
        }
    }

    private fun save() {
        val snapshot = _items.value.values.sortedByDescending { it.updatedAt }.take(maxItems)
        val d = dirty.value
        scope.launch(Dispatchers.IO) {
            runCatching {
                val tmp = File(file.path + ".tmp")
                tmp.writeText(SyncJson.gson.toJson(mapOf("items" to snapshot, "dirty" to d)))
                tmp.renameTo(file)
            }
        }
    }

    suspend fun put(item: T) = lock.withLock {
        _items.value = _items.value + (item.uuid to item)
        dirty.value = dirty.value + item.uuid
        save()
    }

    fun get(id: String): T? = _items.value[id]

    override fun observeDirtyCount(): Flow<Int> = dirty.map { it.size }
    override suspend fun exportAll(): List<JsonObject> = _items.value.values.map { SyncJson.gson.toJsonTree(it).asJsonObject }
    override suspend fun exportDirty(): List<JsonObject> =
        dirty.value.mapNotNull { _items.value[it] }.map { SyncJson.gson.toJsonTree(it).asJsonObject }

    override suspend fun markClean(items: List<JsonObject>) = lock.withLock {
        val ids = items.mapNotNull { it.get("uuid")?.asString }.toSet()
        dirty.value = dirty.value - ids
        save()
    }

    override suspend fun merge(items: List<JsonObject>, markDirty: Boolean): Int = lock.withLock {
        var changed = 0
        val map = _items.value.toMutableMap()
        val d = dirty.value.toMutableSet()
        for (j in items) {
            val it = runCatching { SyncJson.gson.fromJson(j, type) }.getOrNull() ?: continue
            val old = map[it.uuid]
            if (old == null || it.updatedAt > old.updatedAt) {
                map[it.uuid] = it; changed++
                if (markDirty) d += it.uuid
            }
        }
        if (changed > 0) { _items.value = map; dirty.value = d; save() }
        changed
    }
}

fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(("parta-salt:" + s).toByteArray())
    .joinToString("") { "%02x".format(it) }

/** Счётчики использования. Храним локально, раз в 30 с публикуем снимок в коллекцию "stats". */
class StatsTracker(context: Context, private val settings: SettingsStore, private val store: JsonStore<StatsDto>, private val scope: CoroutineScope) {
    private val prefs = context.getSharedPreferences("stats", Context.MODE_PRIVATE)
    @Volatile private var changed = true
    /** Почта вошедшего аккаунта — чтобы админ видел, чья это статистика. */
    @Volatile var email: String = ""
        set(v) { field = v; changed = true }

    fun inc(key: String) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
        changed = true
    }

    fun start() {
        if (prefs.getLong("first", 0L) == 0L) prefs.edit().putLong("first", System.currentTimeMillis()).apply()
        inc("opens")
        scope.launch {
            while (true) {
                if (changed) { changed = false; runCatching { publish() } }
                delay(30_000)
            }
        }
    }

    suspend fun publish() {
        store.put(
            StatsDto(
                uuid = settings.deviceId,
                name = settings.userName.value.ifBlank { "Без имени" },
                classCode = settings.effectiveClassCode,
                device = "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}",
                appVersion = BuildConfig.VERSION_NAME,
                opens = prefs.getInt("opens", 0),
                filesSent = prefs.getInt("filesSent", 0),
                filesReceived = prefs.getInt("filesReceived", 0),
                aiRequests = prefs.getInt("ai", 0),
                translations = prefs.getInt("translations", 0),
                calcSolved = prefs.getInt("calc", 0),
                chatMessages = prefs.getInt("chat", 0),
                gamesPlayed = prefs.getInt("games", 0),
                email = email,
                firstSeen = prefs.getLong("first", 0L),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }
}

class SocialRepository(
    private val settings: SettingsStore,
    val requests: JsonStore<RequestDto>,
    val chat: JsonStore<ChatDto>,
    private val stats: StatsTracker,
) {
    val requestList: Flow<List<RequestDto>> = requests.items.map { m ->
        m.values.filter { !it.closed && System.currentTimeMillis() - it.createdAt < 3 * 86_400_000L }.sortedByDescending { it.createdAt }
    }
    val messages: Flow<List<ChatDto>> = chat.items.map { m -> m.values.filter { !it.deleted }.sortedBy { it.createdAt } }
    val myId get() = settings.deviceId
    private val myName get() = settings.userName.value.ifBlank { "Ученик" }

    suspend fun sendRequest(subject: String, grade: String, text: String): RequestDto {
        val now = System.currentTimeMillis()
        val r = RequestDto(UUID.randomUUID().toString(), myId, myName, subject, grade, text, now, updatedAt = now)
        requests.put(r)
        // Запрос дублируется в общий чат, чтобы все увидели
        send("🆘 Срочно нужна шпаргалка: $subject, $grade класс" + if (text.isNotBlank()) "\n$text" else "", null, null, r.uuid)
        return r
    }

    suspend fun closeRequest(r: RequestDto) = requests.put(r.copy(closed = true, updatedAt = System.currentTimeMillis()))

    suspend fun send(text: String, image: Bitmap?, replyTo: ChatDto?, requestId: String? = null) {
        val now = System.currentTimeMillis()
        chat.put(
            ChatDto(
                UUID.randomUUID().toString(), myId, myName, text.trim(), image?.let(::encodeImage),
                replyTo?.uuid, replyTo?.let { "${it.fromName}: ${it.text.take(60).ifBlank { "📷 Фото" }}" },
                requestId ?: replyTo?.requestId, now, updatedAt = now,
            ),
        )
        stats.inc("chat")
    }

    suspend fun delete(m: ChatDto) = chat.put(m.copy(deleted = true, image = null, updatedAt = System.currentTimeMillis()))

    private fun encodeImage(b: Bitmap): String {
        val k = 900f / maxOf(b.width, b.height)
        val s = if (k < 1f) Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true) else b
        var q = 80
        var bytes: ByteArray
        do {
            val out = ByteArrayOutputStream(); s.compress(Bitmap.CompressFormat.JPEG, q, out); bytes = out.toByteArray(); q -= 15
        } while (bytes.size > 150_000 && q > 20)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
