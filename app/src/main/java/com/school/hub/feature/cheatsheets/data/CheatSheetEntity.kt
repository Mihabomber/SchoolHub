package com.school.hub.feature.cheatsheets.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * uuid — глобальный идентификатор для синхронизации между устройствами.
 * updatedAt — правило "побеждает последняя правка" (Last-Writer-Wins).
 * deleted — "надгробие": удаление тоже распространяется по сети.
 * dirty — изменение ещё не отправлено на сервер.
 */
@Entity(tableName = "cheatsheets", indices = [Index(value = ["uuid"], unique = true)])
data class CheatSheetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val title: String,
    val content: String,
    val subject: String,
    val imagePath: String? = null,
    val author: String = "",
    val isFavorite: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    val originDevice: String = "",
)
