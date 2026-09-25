package com.school.hub.feature.homework.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.school.hub.core.ui.components.EmptyState
import com.school.hub.core.ui.components.SubjectBadge
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.feature.homework.data.Homework
import com.school.hub.feature.schedule.ui.RU
import com.school.hub.navigation.AppViewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    val base = d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", RU))
    return when (d) {
        today -> "Сегодня · $base"
        today.plusDays(1) -> "Завтра · $base"
        today.minusDays(1) -> "Вчера · $base"
        else -> base.replaceFirstChar { it.uppercase() }
    }
}

@Composable
fun HomeworkScreen(vm: HomeworkViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    var editor by remember { mutableStateOf<EditorTarget?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editor = EditorTarget(null) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Задание") },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Домашка", style = MaterialTheme.typography.headlineMedium)
            }
            item { ProgressCard(s) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(HwFilter.entries) { f ->
                        FilterChip(
                            selected = vm.filter == f, onClick = { vm.selectFilter(f) },
                            label = { Text(if (f == HwFilter.OVERDUE && s.overdue > 0) "${f.title} · ${s.overdue}" else f.title) },
                        )
                    }
                }
            }
            if (!s.isLoading && s.groups.isEmpty()) {
                item { EmptyState("🎉", "Заданий нет", "Добавь ДЗ — оно появится у всего класса") }
            }
            s.groups.forEach { (date, list) ->
                item(key = "h-$date") {
                    Text(
                        dayLabel(date), style = MaterialTheme.typography.titleSmall,
                        color = if (date.isBefore(LocalDate.now())) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(list, key = { it.uuid }) { hw ->
                    HomeworkItem(
                        hw,
                        onToggle = { vm.toggle(hw.uuid) },
                        onEdit = { editor = EditorTarget(hw) },
                        onDelete = { vm.delete(hw.uuid) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    editor?.let { target ->
        HomeworkEditorSheet(
            existing = target.hw,
            nextLesson = { vm.nextLessonDate(it) },
            onDismiss = { editor = null },
            onSave = { subj, text, due -> vm.save(target.hw, subj, text, due); editor = null },
        )
    }
}

private data class EditorTarget(val hw: Homework?)

@Composable
private fun ProgressCard(s: HomeworkUiState) {
    val p = if (s.totalCount == 0) 0f else s.doneCount.toFloat() / s.totalCount
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(AppGradients.Sunset)).padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (s.totalCount == 0) "Свободен! 🎈" else "Сделано ${s.doneCount} из ${s.totalCount}",
                style = MaterialTheme.typography.titleLarge, color = Color.White,
            )
            Text(
                when {
                    s.totalCount == 0 -> "В этом списке заданий нет"
                    p >= 1f -> "Всё готово — ты красавчик 💪"
                    p >= 0.5f -> "Больше половины позади!"
                    else -> "Начни с самого короткого задания"
                },
                color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { p },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
            )
        }
    }
}

@Composable
private fun HomeworkItem(hw: Homework, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (hw.done) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = hw.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SubjectBadge(hw.subject)
                Text(
                    hw.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (hw.done) TextDecoration.LineThrough else null,
                    color = if (hw.done) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                )
                if (hw.author.isNotBlank()) {
                    Text("✍️ ${hw.author}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Изменить") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Удалить у всех") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HomeworkEditorSheet(
    existing: Homework?,
    nextLesson: suspend (Subject) -> LocalDate?,
    onDismiss: () -> Unit,
    onSave: (Subject, String, LocalDate) -> Unit,
) {
    var subject by remember { mutableStateOf(existing?.subject ?: Subject.MATH) }
    var text by remember { mutableStateOf(existing?.text ?: "") }
    var due by remember { mutableStateOf(existing?.dueDate ?: LocalDate.now().plusDays(1)) }
    var nextLessonDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickDate by remember { mutableStateOf(false) }

    LaunchedEffect(subject) { nextLessonDate = nextLesson(subject) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (existing == null) "Новое задание" else "Изменить задание", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { subj ->
                    FilterChip(
                        selected = subject == subj, onClick = { subject = subj },
                        label = { Text(subj.title) }, leadingIcon = { Text(subj.emoji) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(subj.color).copy(alpha = 0.22f)),
                    )
                }
            }
            OutlinedTextField(
                text, { text = it }, label = { Text("Что задали") },
                placeholder = { Text("§12, упр. 3–5, выучить правило") },
                minLines = 3, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
            )
            Text("Срок: ${dayLabel(due)}", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now()
                nextLessonDate?.let { d ->
                    AssistChip(onClick = { due = d }, label = { Text("К след. уроку (${d.format(DateTimeFormatter.ofPattern("EE d", RU))})") }, leadingIcon = { Text("📅") })
                }
                AssistChip(onClick = { due = today.plusDays(1) }, label = { Text("Завтра") })
                AssistChip(onClick = { due = today.plusDays(2) }, label = { Text("Послезавтра") })
                AssistChip(onClick = { due = today.plusDays(7) }, label = { Text("Через неделю") })
                AssistChip(onClick = { pickDate = true }, label = { Text("Дата…") }, leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) })
            }
            Button(
                onClick = { if (text.isNotBlank()) onSave(subject, text, due) },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
            ) { Text("Сохранить и поделиться с классом") }
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = due.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { due = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = state) }
    }
}
