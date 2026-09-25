package com.school.hub.feature.homework.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.sync.HomeworkDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

/** Задание общее для класса, а отметка "выполнено" (done) — личная и не синхронизируется. */
@Entity(tableName = "homework")
data class HomeworkEntity(
    @PrimaryKey val uuid: String,
    val subject: String,
    val text: String,
    val dueDate: Long,
    val author: String = "",
    val done: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
    val originDevice: String = "",
)

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework WHERE deleted = 0 ORDER BY dueDate, done, createdAt")
    fun observeAll(): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE deleted = 0 AND done = 0 AND dueDate = :day")
    suspend fun pendingOn(day: Long): List<HomeworkEntity>

    @Query("SELECT * FROM homework WHERE uuid = :uuid") suspend fun get(uuid: String): HomeworkEntity?
    @Query("SELECT * FROM homework") suspend fun allRaw(): List<HomeworkEntity>
    @Query("SELECT * FROM homework WHERE dirty = 1") suspend fun dirty(): List<HomeworkEntity>
    @Query("SELECT COUNT(*) FROM homework WHERE dirty = 1") fun observeDirtyCount(): Flow<Int>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: HomeworkEntity)
    @Query("UPDATE homework SET done = :done WHERE uuid = :uuid") suspend fun setDone(uuid: String, done: Boolean)
    @Query("UPDATE homework SET dirty = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt") suspend fun markClean(uuid: String, updatedAt: Long)
}

data class Homework(
    val uuid: String,
    val subject: Subject,
    val text: String,
    val dueDate: LocalDate,
    val done: Boolean,
    val author: String,
    val isMine: Boolean,
)

class HomeworkRepository(private val dao: HomeworkDao, private val settings: SettingsStore) {
    private val lock = Mutex()

    fun observe(): Flow<List<Homework>> = dao.observeAll().map { list ->
        list.map {
            Homework(it.uuid, Subject.from(it.subject), it.text, LocalDate.ofEpochDay(it.dueDate), it.done, it.author, it.originDevice == settings.deviceId)
        }
    }

    suspend fun pendingOn(date: LocalDate): Int = dao.pendingOn(date.toEpochDay()).size

    suspend fun add(subject: Subject, text: String, due: LocalDate) = lock.withLock {
        val now = System.currentTimeMillis()
        dao.upsert(
            HomeworkEntity(
                uuid = UUID.randomUUID().toString(), subject = subject.name, text = text.trim(),
                dueDate = due.toEpochDay(), author = settings.userName.value, createdAt = now, updatedAt = now,
                originDevice = settings.deviceId,
            )
        )
    }

    suspend fun update(uuid: String, subject: Subject, text: String, due: LocalDate) = lock.withLock {
        val e = dao.get(uuid) ?: return@withLock
        dao.upsert(e.copy(subject = subject.name, text = text.trim(), dueDate = due.toEpochDay(), updatedAt = System.currentTimeMillis(), dirty = true))
    }

    suspend fun delete(uuid: String) = lock.withLock {
        val e = dao.get(uuid) ?: return@withLock
        dao.upsert(e.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
    }

    suspend fun toggleDone(uuid: String) {
        val e = dao.get(uuid) ?: return
        dao.setDone(uuid, !e.done)
    }

    // ---------- синхронизация ----------
    fun observeDirtyCount() = dao.observeDirtyCount()

    suspend fun export(dirtyOnly: Boolean) = (if (dirtyOnly) dao.dirty() else dao.allRaw()).map {
        HomeworkDto(it.uuid, it.subject, it.text, it.dueDate, it.author, it.createdAt, it.updatedAt, it.deleted, it.originDevice)
    }

    suspend fun markClean(items: List<HomeworkDto>) = items.forEach { dao.markClean(it.uuid, it.updatedAt) }

    suspend fun merge(items: List<HomeworkDto>, markDirty: Boolean): Int = lock.withLock {
        var n = 0
        for (d in items) runCatching {
            val local = dao.get(d.uuid)
            if (local == null || d.updatedAt > local.updatedAt) {
                dao.upsert(
                    HomeworkEntity(
                        uuid = d.uuid, subject = d.subject, text = d.text, dueDate = d.dueDate, author = d.author ?: "",
                        done = local?.done ?: false, createdAt = d.createdAt, updatedAt = d.updatedAt,
                        deleted = d.deleted, dirty = markDirty, originDevice = d.originDevice ?: "",
                    )
                )
                n++
            }
        }
        n
    }
}
