package com.school.hub.feature.schedule.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** uuid = "lesson-<день>-<номер>": правки одного и того же урока у разных людей сливаются. */
@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val uuid: String,
    val dayOfWeek: Int,
    val number: Int,
    val subject: String,
    val room: String = "",
    val teacher: String = "",
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** uuid = "bell-<номер>" */
@Entity(tableName = "bells")
data class BellEntity(
    @PrimaryKey val uuid: String,
    val number: Int,
    val start: String,
    val end: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM lessons WHERE deleted = 0")
    fun observeLessons(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM bells WHERE deleted = 0 ORDER BY number")
    fun observeBells(): Flow<List<BellEntity>>

    @Query("SELECT * FROM lessons WHERE deleted = 0")
    suspend fun lessons(): List<LessonEntity>

    @Query("SELECT * FROM bells WHERE deleted = 0 ORDER BY number")
    suspend fun bells(): List<BellEntity>

    @Query("SELECT * FROM lessons") suspend fun allLessonsRaw(): List<LessonEntity>
    @Query("SELECT * FROM bells") suspend fun allBellsRaw(): List<BellEntity>
    @Query("SELECT * FROM lessons WHERE dirty = 1") suspend fun dirtyLessons(): List<LessonEntity>
    @Query("SELECT * FROM bells WHERE dirty = 1") suspend fun dirtyBells(): List<BellEntity>
    @Query("SELECT COUNT(*) FROM lessons WHERE dirty = 1") fun observeDirtyLessons(): Flow<Int>
    @Query("SELECT COUNT(*) FROM bells WHERE dirty = 1") fun observeDirtyBells(): Flow<Int>
    @Query("SELECT * FROM lessons WHERE uuid = :uuid") suspend fun lesson(uuid: String): LessonEntity?
    @Query("SELECT * FROM bells WHERE uuid = :uuid") suspend fun bell(uuid: String): BellEntity?
    @Query("SELECT COUNT(*) FROM bells") suspend fun bellCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLesson(e: LessonEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBell(e: BellEntity)

    @Query("UPDATE lessons SET dirty = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun cleanLesson(uuid: String, updatedAt: Long)

    @Query("UPDATE bells SET dirty = 0 WHERE uuid = :uuid AND updatedAt = :updatedAt")
    suspend fun cleanBell(uuid: String, updatedAt: Long)
}
