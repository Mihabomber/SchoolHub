package com.school.hub.sync

import com.google.gson.Gson
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Формат шпаргалки "на проводе" — одинаковый для Bluetooth/Wi-Fi Direct и для сервера. */
data class CheatSheetDto(
    val uuid: String,
    val title: String,
    val content: String,
    val subject: String,
    val author: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean,
    val imageBase64: String?,
    val originDevice: String?,
)

data class SyncPacket(val version: Int, val fromDevice: String, val classCode: String, val items: List<CheatSheetDto>)

data class PullResponse(val serverTime: Long, val items: List<CheatSheetDto>?)
data class PushRequest(val items: List<CheatSheetDto>)
data class PushResponse(val accepted: Int, val serverTime: Long)

/** Упаковка снимка базы в gzip-JSON файл для передачи по Nearby. */
object SyncCodec {
    private val gson = Gson()

    fun write(packet: SyncPacket, file: File): File {
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { gson.toJson(packet, it) }
        return file
    }

    fun read(input: InputStream): SyncPacket =
        GZIPInputStream(input).bufferedReader().use { gson.fromJson(it, SyncPacket::class.java) }
}
