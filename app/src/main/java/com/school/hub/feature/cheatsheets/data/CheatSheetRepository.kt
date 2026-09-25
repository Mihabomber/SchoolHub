package com.school.hub.feature.cheatsheets.data

import com.school.hub.core.data.ImageStorage
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.model.CheatSheet
import com.school.hub.feature.cheatsheets.model.CheatSheetDraft
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.sync.CheatSheetDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Offline-first репозиторий: источник правды — Room, сеть только догоняет. */
class CheatSheetRepository(
    private val dao: CheatSheetDao,
    private val images: ImageStorage,
    private val settings: SettingsStore,
) {
    private val writeLock = Mutex()

    fun observeAll(): Flow<List<CheatSheet>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(id: Long): Flow<CheatSheet?> = dao.observeById(id).map { e -> e?.takeUnless { it.deleted }?.toModel() }

    fun observeDirtyCount(): Flow<Int> = dao.observeDirtyCount()

    suspend fun get(id: Long): CheatSheet? = dao.getById(id)?.takeUnless { it.deleted }?.toModel()

    suspend fun save(draft: CheatSheetDraft): Long = writeLock.withLock {
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getById(it) }
        if (existing == null) {
            dao.insert(
                CheatSheetEntity(
                    uuid = UUID.randomUUID().toString(),
                    title = draft.title, content = draft.content, subject = draft.subject.name,
                    imagePath = draft.imagePath, author = draft.author,
                    createdAt = now, updatedAt = now, dirty = true, originDevice = settings.deviceId,
                )
            )
        } else {
            if (existing.imagePath != null && existing.imagePath != draft.imagePath) images.delete(existing.imagePath)
            dao.update(
                existing.copy(
                    title = draft.title, content = draft.content, subject = draft.subject.name,
                    imagePath = draft.imagePath, author = draft.author, updatedAt = now, dirty = true,
                )
            )
            existing.id
        }
    }

    suspend fun delete(id: Long) = writeLock.withLock {
        val e = dao.getById(id) ?: return@withLock
        e.imagePath?.let(images::delete)
        dao.update(
            e.copy(deleted = true, content = "", imagePath = null, updatedAt = System.currentTimeMillis(), dirty = true)
        )
    }

    suspend fun toggleFavorite(id: Long) {
        val e = dao.getById(id) ?: return
        dao.setFavorite(id, !e.isFavorite)
    }

    // ---------- Синхронизация ----------

    suspend fun exportAll(): List<CheatSheetDto> = withContext(Dispatchers.IO) { dao.getAllRaw().map { it.toDto() } }

    suspend fun exportDirty(): List<CheatSheetDto> = withContext(Dispatchers.IO) { dao.getDirty().map { it.toDto() } }

    suspend fun markClean(items: List<CheatSheetDto>) {
        items.forEach { dao.markClean(it.uuid, it.updatedAt) }
    }

    /**
     * Сливает чужие шпаргалки с локальными (Last-Writer-Wins).
     * @param markDirty true для P2P: полученное по Bluetooth потом уйдёт и на сервер,
     *        так шпаргалки "эпидемически" расходятся по всему классу.
     * @return сколько записей добавлено/обновлено.
     */
    suspend fun mergeRemote(items: List<CheatSheetDto>, markDirty: Boolean): Int = writeLock.withLock {
        withContext(Dispatchers.IO) {
            var changed = 0
            for (dto in items) {
                runCatching {
                    if (dto.uuid.isBlank()) return@runCatching
                    val local = dao.getByUuid(dto.uuid)
                    if (local != null && local.updatedAt >= dto.updatedAt) return@runCatching
                    val newImage = if (dto.deleted) null else dto.imageBase64?.let { images.fromBase64(it) }
                    if (local == null) {
                        dao.insert(
                            CheatSheetEntity(
                                uuid = dto.uuid, title = dto.title, content = dto.content,
                                subject = dto.subject, imagePath = newImage, author = dto.author ?: "",
                                createdAt = dto.createdAt, updatedAt = dto.updatedAt, deleted = dto.deleted,
                                dirty = markDirty, originDevice = dto.originDevice ?: "",
                            )
                        )
                    } else {
                        local.imagePath?.let(images::delete)
                        dao.update(
                            local.copy(
                                title = dto.title, content = dto.content, subject = dto.subject,
                                imagePath = newImage, author = dto.author ?: "", updatedAt = dto.updatedAt,
                                deleted = dto.deleted, dirty = markDirty,
                            )
                        )
                    }
                    changed++
                }
            }
            changed
        }
    }

    suspend fun seedIfNeeded() {
        if (settings.seeded) return
        SeedData.items.forEach { dto -> if (dao.getByUuid(dto.uuid) == null) dao.insert(dto.copy(dirty = false)) }
        settings.seeded = true
    }

    private fun CheatSheetEntity.toModel() = CheatSheet(
        id = id, uuid = uuid, title = title, content = content, subject = Subject.from(subject),
        imagePath = imagePath, author = author, isFavorite = isFavorite,
        createdAt = createdAt, updatedAt = updatedAt, isMine = originDevice == settings.deviceId,
    )

    private fun CheatSheetEntity.toDto() = CheatSheetDto(
        uuid = uuid, title = title, content = content, subject = subject, author = author,
        createdAt = createdAt, updatedAt = updatedAt, deleted = deleted,
        imageBase64 = imagePath?.let { images.toBase64(it) }, originDevice = originDevice,
    )
}
