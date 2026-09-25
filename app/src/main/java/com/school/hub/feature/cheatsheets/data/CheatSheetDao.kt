package com.school.hub.feature.cheatsheets.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CheatSheetDao {
    @Query("SELECT * FROM cheatsheets WHERE deleted = 0 ORDER BY isFavorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<CheatSheetEntity>>

    @Query("SELECT * FROM cheatsheets WHERE id = :id")
    fun observeById(id: Long): Flow<CheatSheetEntity?>

    @Query("SELECT COUNT(*) FROM cheatsheets WHERE dirty = 1")
    fun observeDirtyCount(): Flow<Int>

    @Query("SELECT * FROM cheatsheets WHERE id = :id")
    suspend fun getById(id: Long): CheatSheetEntity?

    @Query("SELECT * FROM cheatsheets WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): CheatSheetEntity?

    @Query("SELECT * FROM cheatsheets")
    suspend fun getAllRaw(): List<CheatSheetEntity>

    @Query("SELECT * FROM cheatsheets WHERE dirty = 1")
    suspend fun getDirty(): List<CheatSheetEntity>

    @Insert
    suspend fun insert(entity: CheatSheetEntity): Long

    @Update
    suspend fun update(entity: CheatSheetEntity)

    @Query("UPDATE cheatsheets SET isFavorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE cheatsheets SET dirty = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun markClean(uuid: String, updatedAt: Long)
}
