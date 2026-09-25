package com.school.hub.feature.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.feature.schedule.data.Bell
import com.school.hub.feature.schedule.data.Lesson
import com.school.hub.feature.schedule.data.NowStatus
import com.school.hub.feature.schedule.data.hhmm
import com.school.hub.feature.schedule.data.parseTime
import com.school.hub.navigation.AppViewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
val RU: Locale = Locale.forLanguageTag("ru")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    onOpenSettings: () -> Unit,
    vm: ScheduleViewModel = viewModel(factory = AppViewModelFactory.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val remindersOn by vm.settings.lessonReminders.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Int?>(null) }
    var bellsOpen by remember { mutableStateOf(false) }
    val today = LocalDate.now().dayOfWeek.value
    val lessons = s.data.lessonsFor(vm.selectedDay)
    val slots = maxOf(s.data.maxNumber, 6)

    Scaffold { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Расписание", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Общее для класса · правь, если что-то поменялось",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(if (remindersOn) Icons.Filled.Notifications else Icons.Filled.NotificationsOff, "Уведомления")
                    }
                    FilledTonalIconButton(onClick = { bellsOpen = true }) { Icon(Icons.Filled.Schedule, "Звонки") }
                }
            }
            item { NowCard(s.status) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((1..6).toList()) { d ->
                        val selected = d == vm.selectedDay
                        val count = s.data.lessonsFor(d).size
                        Column(
                            Modifier.clip(RoundedCornerShape(18.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow)
                                .then(if (d == today && !selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)) else Modifier)
                                .clickable { vm.selectDay(d) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                DAY_SHORT[d - 1], fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "$count ур.", style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
            items((1..slots).toList(), key = { "slot-${vm.selectedDay}-$it" }) { n ->
                val lesson = lessons.firstOrNull { it.number == n }
                val bell = s.data.bell(n)
                val isNow = vm.selectedDay == today && s.status is NowStatus.InLesson &&
                    (s.status as NowStatus.InLesson).lesson.number == n
                LessonSlot(n, lesson, bell, isNow) { editing = n }
            }
        }
    }

    editing?.let { n ->
        LessonEditorSheet(
            number = n,
            day = vm.selectedDay,
            existing = lessons.firstOrNull { it.number == n },
            bell = s.data.bell(n),
            onDismiss = { editing = null },
            onSave = { subj, room, teacher -> vm.saveLesson(n, subj, room, teacher); editing = null },
            onDelete = { vm.deleteLesson(n); editing = null },
        )
    }
    if (bellsOpen) {
        BellsDialog(
            bells = s.data.bells,
            onDismiss = { bellsOpen = false },
            onSave = { n, st, en -> vm.saveBell(n, st, en) },
            onDelete = { vm.deleteBell(it) },
        )
    }
}

@Composable
private fun NowCard(status: NowStatus) {
    val (title, subtitle, progress) = when (status) {
        is NowStatus.InLesson -> Triple(
            "Сейчас: ${status.lesson.subject.emoji} ${status.lesson.subject.title}",
            "до ${status.bell.end.hhmm()} · осталось ${status.minutesLeft} мин" + roomSuffix(status.lesson),
            status.progress,
        )
        is NowStatus.Break -> Triple(
            "Перемена ☕",
            "Через ${status.minutesUntil} мин: ${status.next.subject.emoji} ${status.next.subject.title} в ${status.bell.start.hhmm()}" + roomSuffix(status.next),
            null,
        )
        is NowStatus.Later -> Triple(
            "Уроки закончились 🎉",
            "Дальше: ${status.date.format(DateTimeFormatter.ofPattern("EEEE", RU))} — ${status.next.subject.title} в ${status.bell.start.hhmm()}",
            null,
        )
        NowStatus.Free -> Triple("Расписание пустое", "Нажми на урок ниже, чтобы заполнить", null)
    }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(AppGradients.Ocean)).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            if (progress != null) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}

private fun roomSuffix(l: Lesson) = if (l.room.isNotBlank()) " · каб. ${l.room}" else ""

@Composable
private fun LessonSlot(n: Int, lesson: Lesson?, bell: Bell?, isNow: Boolean, onClick: () -> Unit) {
    val color = lesson?.let { Color(it.subject.color) } ?: MaterialTheme.colorScheme.outline
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNow) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = if (isNow) androidx.compose.foundation.BorderStroke(2.dp, color) else null,
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = MaterialTheme.typography.titleLarge, color = color)
                Text(bell?.start?.hhmm() ?: "--:--", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                Text(bell?.end?.hhmm() ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Spacer(Modifier.width(8.dp))
            if (lesson == null) {
                Text(
                    "Свободно — нажми, чтобы добавить",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.outline)
            } else {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Text(lesson.subject.emoji, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(lesson.subject.title, style = MaterialTheme.typography.titleMedium)
                    val meta = listOfNotNull(
                        lesson.room.takeIf { it.isNotBlank() }?.let { "каб. $it" },
                        lesson.teacher.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isNow) Text("идёт", color = color, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LessonEditorSheet(
    number: Int,
    day: Int,
    existing: Lesson?,
    bell: Bell?,
    onDismiss: () -> Unit,
    onSave: (Subject, String, String) -> Unit,
    onDelete: () -> Unit,
) {
    var subject by remember { mutableStateOf(existing?.subject ?: Subject.MATH) }
    var room by remember { mutableStateOf(existing?.room ?: "") }
    var teacher by remember { mutableStateOf(existing?.teacher ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "${DAY_SHORT[day - 1]}, $number-й урок" + (bell?.let { " · ${it.start.hhmm()}–${it.end.hhmm()}" } ?: ""),
                style = MaterialTheme.typography.titleLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { subj ->
                    FilterChip(
                        selected = subject == subj, onClick = { subject = subj },
                        label = { Text(subj.title) }, leadingIcon = { Text(subj.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(subj.color).copy(alpha = 0.22f)),
                    )
                }
            }
            OutlinedTextField(room, { room = it }, label = { Text("Кабинет") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(teacher, { teacher = it }, label = { Text("Учитель") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (existing != null) {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                        Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(6.dp)); Text("Убрать")
                    }
                }
                Button(onClick = { onSave(subject, room, teacher) }, modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Сохранить")
                }
            }
        }
    }
}

@Composable
private fun BellsDialog(
    bells: List<Bell>,
    onDismiss: () -> Unit,
    onSave: (Int, java.time.LocalTime, java.time.LocalTime) -> Unit,
    onDelete: (Int) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Schedule, null) },
        title = { Text("Расписание звонков") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                bells.forEach { b -> BellRow(b.number, b.start.hhmm(), b.end.hhmm(), onSave, onDelete) }
                val next = (bells.maxOfOrNull { it.number } ?: 0) + 1
                TextButton(onClick = {
                    val last = bells.maxByOrNull { it.number }
                    val st = last?.end?.plusMinutes(10) ?: java.time.LocalTime.of(8, 30)
                    onSave(next, st, st.plusMinutes(45))
                }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Добавить $next-й урок") }
                Text(
                    "Время в формате ЧЧ:ММ. Изменения увидит весь класс.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun BellRow(
    number: Int,
    start: String,
    end: String,
    onSave: (Int, java.time.LocalTime, java.time.LocalTime) -> Unit,
    onDelete: (Int) -> Unit,
) {
    var st by remember(start) { mutableStateOf(start) }
    var en by remember(end) { mutableStateOf(end) }
    fun commit() {
        val a = parseTime(st); val b = parseTime(en)
        if (a != null && b != null && a.isBefore(b)) onSave(number, a, b)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$number", modifier = Modifier.width(20.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
        OutlinedTextField(st, { st = it; commit() }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
        Text("–")
        OutlinedTextField(en, { en = it; commit() }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
        IconButton(onClick = { onDelete(number) }) { Icon(Icons.Filled.Delete, "Удалить") }
    }
}
