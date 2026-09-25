package com.school.hub.feature.schedule.data

import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.sync.BellDto
import com.school.hub.sync.LessonDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class Lesson(val dayOfWeek: Int, val number: Int, val subject: Subject, val room: String, val teacher: String)
data class Bell(val number: Int, val start: LocalTime, val end: LocalTime)

data class ScheduleData(val lessons: List<Lesson> = emptyList(), val bells: List<Bell> = emptyList()) {
    fun bell(number: Int): Bell? = bells.firstOrNull { it.number == number }
    fun lessonsFor(day: Int): List<Lesson> = lessons.filter { it.dayOfWeek == day }.sortedBy { it.number }
    val maxNumber: Int get() = maxOf(bells.maxOfOrNull { it.number } ?: 0, lessons.maxOfOrNull { it.number } ?: 0)
}

sealed interface NowStatus {
    data class InLesson(val lesson: Lesson, val bell: Bell, val minutesLeft: Long, val progress: Float) : NowStatus
    data class Break(val next: Lesson, val bell: Bell, val minutesUntil: Long) : NowStatus
    data class Later(val next: Lesson, val bell: Bell, val date: LocalDate) : NowStatus
    data object Free : NowStatus
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
fun LocalTime.hhmm(): String = format(TIME)
fun parseTime(s: String): LocalTime? = runCatching { LocalTime.parse(s.trim().padStart(5, '0'), TIME) }.getOrNull()

class ScheduleRepository(private val dao: ScheduleDao) {
    private val lock = Mutex()

    fun observe(): Flow<ScheduleData> = combine(dao.observeLessons(), dao.observeBells()) { l, b ->
        ScheduleData(
            lessons = l.map { Lesson(it.dayOfWeek, it.number, Subject.from(it.subject), it.room, it.teacher) },
            bells = b.mapNotNull { e ->
                val s = parseTime(e.start); val en = parseTime(e.end)
                if (s != null && en != null) Bell(e.number, s, en) else null
            },
        )
    }

    suspend fun saveLesson(day: Int, number: Int, subject: Subject, room: String, teacher: String) = lock.withLock {
        dao.upsertLesson(
            LessonEntity("lesson-$day-$number", day, number, subject.name, room.trim(), teacher.trim(), System.currentTimeMillis())
        )
    }

    suspend fun deleteLesson(day: Int, number: Int) = lock.withLock {
        val e = dao.lesson("lesson-$day-$number") ?: return@withLock
        dao.upsertLesson(e.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
    }

    suspend fun saveBell(number: Int, start: LocalTime, end: LocalTime) = lock.withLock {
        dao.upsertBell(BellEntity("bell-$number", number, start.hhmm(), end.hhmm(), System.currentTimeMillis()))
    }

    suspend fun deleteBell(number: Int) = lock.withLock {
        val e = dao.bell("bell-$number") ?: return@withLock
        dao.upsertBell(e.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
    }

    suspend fun seedIfEmpty() {
        if (dao.bellCount() > 0) return
        val t = 1735689600000L
        listOf("08:30" to "09:15", "09:25" to "10:10", "10:25" to "11:10", "11:30" to "12:15",
            "12:25" to "13:10", "13:20" to "14:05", "14:15" to "15:00", "15:10" to "15:55")
            .forEachIndexed { i, (s, e) ->
                dao.upsertBell(BellEntity("bell-${i + 1}", i + 1, s, e, updatedAt = t, dirty = false))
            }
    }

    /** Ближайшая дата (начиная с завтра), когда в расписании есть этот предмет. */
    suspend fun nextLessonDate(subject: Subject, from: LocalDate = LocalDate.now()): LocalDate? {
        val lessons = dao.lessons().filter { it.subject == subject.name }
        if (lessons.isEmpty()) return null
        for (i in 1..14) {
            val d = from.plusDays(i.toLong())
            if (lessons.any { it.dayOfWeek == d.dayOfWeek.value }) return d
        }
        return null
    }

    // ---------- синхронизация ----------
    suspend fun exportLessons(dirtyOnly: Boolean) = (if (dirtyOnly) dao.dirtyLessons() else dao.allLessonsRaw()).map {
        LessonDto(it.uuid, it.dayOfWeek, it.number, it.subject, it.room, it.teacher, it.updatedAt, it.deleted)
    }

    suspend fun exportBells(dirtyOnly: Boolean) = (if (dirtyOnly) dao.dirtyBells() else dao.allBellsRaw()).map {
        BellDto(it.uuid, it.number, it.start, it.end, it.updatedAt, it.deleted)
    }

    fun observeDirtyLessons() = dao.observeDirtyLessons()
    fun observeDirtyBells() = dao.observeDirtyBells()
    suspend fun cleanLessons(items: List<LessonDto>) = items.forEach { dao.cleanLesson(it.uuid, it.updatedAt) }
    suspend fun cleanBells(items: List<BellDto>) = items.forEach { dao.cleanBell(it.uuid, it.updatedAt) }

    suspend fun mergeLessons(items: List<LessonDto>, markDirty: Boolean): Int = lock.withLock {
        var n = 0
        for (d in items) runCatching {
            val local = dao.lesson(d.uuid)
            if (local == null || d.updatedAt > local.updatedAt) {
                dao.upsertLesson(LessonEntity(d.uuid, d.dayOfWeek, d.number, d.subject, d.room ?: "", d.teacher ?: "", d.updatedAt, d.deleted, markDirty))
                n++
            }
        }
        n
    }

    suspend fun mergeBells(items: List<BellDto>, markDirty: Boolean): Int = lock.withLock {
        var n = 0
        for (d in items) runCatching {
            val local = dao.bell(d.uuid)
            if (local == null || d.updatedAt > local.updatedAt) {
                dao.upsertBell(BellEntity(d.uuid, d.number, d.start, d.end, d.updatedAt, d.deleted, markDirty))
                n++
            }
        }
        n
    }
}

/** Что сейчас: урок, перемена или ближайший урок в другой день. */
fun ScheduleData.statusAt(now: LocalDateTime): NowStatus {
    val today = now.toLocalDate()
    val time = now.toLocalTime()
    val todayLessons = lessonsFor(today.dayOfWeek.value).mapNotNull { l -> bell(l.number)?.let { l to it } }
    todayLessons.firstOrNull { (_, b) -> !time.isBefore(b.start) && time.isBefore(b.end) }?.let { (l, b) ->
        val total = java.time.Duration.between(b.start, b.end).toMinutes().coerceAtLeast(1)
        val passed = java.time.Duration.between(b.start, time).toMinutes()
        return NowStatus.InLesson(l, b, total - passed, passed.toFloat() / total)
    }
    todayLessons.firstOrNull { (_, b) -> time.isBefore(b.start) }?.let { (l, b) ->
        return NowStatus.Break(l, b, java.time.Duration.between(time, b.start).toMinutes())
    }
    for (i in 1..7) {
        val d = today.plusDays(i.toLong())
        val first = lessonsFor(d.dayOfWeek.value).firstNotNullOfOrNull { l -> bell(l.number)?.let { l to it } }
        if (first != null) return NowStatus.Later(first.first, first.second, d)
    }
    return NowStatus.Free
}
