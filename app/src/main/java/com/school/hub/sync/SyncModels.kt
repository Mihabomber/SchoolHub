package com.school.hub.sync

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SyncJson { val gson = Gson() }

// ---------- DTO "на проводе" (одинаковые для Bluetooth/Wi-Fi Direct и сервера) ----------

data class CheatSheetDto(
    val uuid: String, val title: String, val content: String, val subject: String, val author: String?,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean, val imageBase64: String?, val originDevice: String?,
)

data class LessonDto(
    val uuid: String, val dayOfWeek: Int, val number: Int, val subject: String,
    val room: String?, val teacher: String?, val updatedAt: Long, val deleted: Boolean,
)

data class BellDto(
    val uuid: String, val number: Int, val start: String, val end: String, val updatedAt: Long, val deleted: Boolean,
)

data class HomeworkDto(
    val uuid: String, val subject: String, val text: String, val dueDate: Long, val author: String?,
    val createdAt: Long, val updatedAt: Long, val deleted: Boolean, val originDevice: String?,
)

/** Снимок всех коллекций для P2P-обмена. */
data class SyncPacket(
    val version: Int,
    val fromDevice: String,
    val classCode: String,
    val collections: Map<String, List<JsonObject>>,
)

data class PullResponse(val serverTime: Long, val items: List<JsonObject>?)
data class PushRequest(val items: List<JsonObject>)
data class PushResponse(val accepted: Int, val serverTime: Long)

/** Любая синхронизируемая коллекция (шпаргалки, уроки, звонки, домашка). */
interface SyncCollection {
    val name: String
    fun observeDirtyCount(): Flow<Int>
    suspend fun exportAll(): List<JsonObject>
    suspend fun exportDirty(): List<JsonObject>
    suspend fun markClean(items: List<JsonObject>)
    suspend fun merge(items: List<JsonObject>, markDirty: Boolean): Int
}

class TypedSyncCollection<T : Any>(
    override val name: String,
    private val type: Class<T>,
    private val dirtyFlow: () -> Flow<Int>,
    private val all: suspend () -> List<T>,
    private val dirty: suspend () -> List<T>,
    private val clean: suspend (List<T>) -> Unit,
    private val mergeFn: suspend (List<T>, Boolean) -> Int,
) : SyncCollection {
    override fun observeDirtyCount(): Flow<Int> = dirtyFlow()
    override suspend fun exportAll(): List<JsonObject> = all().map { encode(it) }
    override suspend fun exportDirty(): List<JsonObject> = dirty().map { encode(it) }
    override suspend fun markClean(items: List<JsonObject>) = clean(decode(items))
    override suspend fun merge(items: List<JsonObject>, markDirty: Boolean): Int = mergeFn(decode(items), markDirty)

    private fun encode(item: T): JsonObject = SyncJson.gson.toJsonTree(item).asJsonObject
    private fun decode(items: List<JsonObject>): List<T> =
        items.mapNotNull { runCatching { SyncJson.gson.fromJson(it, type) }.getOrNull() }
}

/** Упаковка снимка в gzip-JSON файл для передачи по Nearby. */
object SyncCodec {
    fun write(packet: SyncPacket, file: File): File {
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { SyncJson.gson.toJson(packet, it) }
        return file
    }

    fun read(input: InputStream): SyncPacket =
        GZIPInputStream(input).bufferedReader().use { SyncJson.gson.fromJson(it, SyncPacket::class.java) }
}
