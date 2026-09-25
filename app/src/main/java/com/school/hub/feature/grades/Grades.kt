package com.school.hub.feature.grades

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.school.hub.core.ui.components.EmptyState
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.navigation.AppViewModelFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.ceil

// ---------------- data (личное, не синхронизируется) ----------------

@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val value: Int,
    val weight: Int = 1,
    val date: Long,
)

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades ORDER BY date DESC, id DESC") fun observeAll(): Flow<List<GradeEntity>>
    @Insert suspend fun insert(e: GradeEntity): Long
    @Query("DELETE FROM grades WHERE id = :id") suspend fun delete(id: Long)
}

class GradesRepository(private val dao: GradeDao) {
    fun observe(): Flow<List<GradeEntity>> = dao.observeAll()
    suspend fun add(subject: Subject, value: Int, weight: Int) =
        dao.insert(GradeEntity(subject = subject.name, value = value, weight = weight, date = LocalDate.now().toEpochDay()))
    suspend fun delete(id: Long) = dao.delete(id)
}

// ---------------- logic ----------------

data class SubjectGrades(val subject: Subject, val grades: List<GradeEntity>) {
    val average: Double get() {
        val w = grades.sumOf { it.weight }
        return if (w == 0) 0.0 else grades.sumOf { it.value * it.weight }.toDouble() / w
    }

    /** Сколько пятёрок нужно, чтобы средний балл дошёл до target. */
    fun fivesNeeded(target: Double): Int {
        val sum = grades.sumOf { it.value * it.weight }.toDouble()
        val w = grades.sumOf { it.weight }.toDouble()
        if (w > 0 && sum / w >= target) return 0
        return ceil((target * w - sum) / (5 - target)).toInt().coerceAtLeast(1)
    }
}

fun gradeColor(v: Double): Color = when {
    v >= 4.5 -> Color(0xFF2ECC71)
    v >= 3.5 -> Color(0xFF3498DB)
    v >= 2.5 -> Color(0xFFF39C12)
    v > 0 -> Color(0xFFE74C3C)
    else -> Color(0xFF9E9E9E)
}

data class GradesUiState(val subjects: List<SubjectGrades> = emptyList(), val overall: Double = 0.0, val count: Int = 0)

class GradesViewModel(private val repo: GradesRepository) : ViewModel() {
    val state: StateFlow<GradesUiState> = repo.observe().map { list ->
        val subjects = list.groupBy { Subject.from(it.subject) }
            .map { (s, g) -> SubjectGrades(s, g) }
            .sortedBy { it.subject.ordinal }
        GradesUiState(
            subjects = subjects,
            overall = subjects.map { it.average }.takeIf { it.isNotEmpty() }?.average() ?: 0.0,
            count = list.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GradesUiState())

    fun add(subject: Subject, value: Int, weight: Int) = viewModelScope.launch { repo.add(subject, value, weight) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

// ---------------- UI ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(onBack: () -> Unit, vm: GradesViewModel = viewModel(factory = AppViewModelFactory.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf<Subject?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оценки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = null; sheetOpen = true },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Оценка") },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .background(Brush.linearGradient(AppGradients.Mint)).padding(20.dp),
                ) {
                    Column {
                        Text("Средний балл", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                        Text(
                            if (s.count == 0) "—" else "%.2f".format(s.overall),
                            color = Color.White, style = MaterialTheme.typography.headlineLarge,
                        )
                        Text(
                            "${s.count} оценок · ${s.subjects.size} предметов · видишь только ты",
                            color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (s.subjects.isEmpty()) {
                item { EmptyState("📊", "Оценок пока нет", "Добавляй оценки — посчитаем средний балл и сколько пятёрок нужно до цели") }
            }
            items(s.subjects, key = { it.subject.name }) { sg ->
                SubjectGradesCard(sg, onAdd = { adding = sg.subject; sheetOpen = true }, onDelete = { vm.delete(it) })
            }
        }
    }

    if (sheetOpen) {
        AddGradeSheet(
            initial = adding,
            onDismiss = { sheetOpen = false },
            onSave = { subj, v, w -> vm.add(subj, v, w); sheetOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun SubjectGradesCard(sg: SubjectGrades, onAdd: () -> Unit, onDelete: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val avg = sg.average
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sg.subject.emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(sg.subject.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(CircleShape).background(gradeColor(avg).copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 4.dp),
                ) { Text("%.2f".format(avg), color = gradeColor(avg), fontWeight = FontWeight.Bold) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sg.grades.take(if (expanded) 100 else 12).forEach { g ->
                    Box(
                        Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                            .background(gradeColor(g.value.toDouble()).copy(alpha = if (g.weight > 1) 0.4f else 0.18f))
                            .combinedClickable(onClick = {}, onLongClick = { onDelete(g.id) }),
                        contentAlignment = Alignment.Center,
                    ) { Text("${g.value}", fontWeight = FontWeight.Bold, color = gradeColor(g.value.toDouble())) }
                }
            }
            if (expanded) {
                val to5 = sg.fivesNeeded(4.5)
                val to4 = sg.fivesNeeded(3.5)
                Text(
                    buildString {
                        append(if (to5 == 0) "✅ Выходит «5»" else "🎯 До «5»: нужно ещё $to5 пятёр${if (to5 in 2..4) "ки" else if (to5 == 1) "ка" else "ок"}")
                        if (avg < 3.5) append("\n🎯 До «4»: ещё $to4")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Долгое нажатие на оценку — удалить. Яркие — контрольные (×2).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                FilledTonalButton(onClick = onAdd, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Filled.Add, null); Text(" Оценка по предмету") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddGradeSheet(initial: Subject?, onDismiss: () -> Unit, onSave: (Subject, Int, Int) -> Unit) {
    var subject by remember { mutableStateOf(initial ?: Subject.MATH) }
    var weight by remember { mutableIntStateOf(1) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Новая оценка", style = MaterialTheme.typography.titleLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Subject.entries.forEach { subj ->
                    FilterChip(selected = subject == subj, onClick = { subject = subj }, label = { Text(subj.title) }, leadingIcon = { Text(subj.emoji) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = weight == 1, onClick = { weight = 1 }, label = { Text("Обычная") })
                FilterChip(selected = weight == 2, onClick = { weight = 2 }, label = { Text("Контрольная ×2") })
            }
            Text("Нажми на оценку, чтобы сохранить", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (2..5).forEach { v ->
                    val c = gradeColor(v.toDouble())
                    Button(
                        onClick = { onSave(subject, v, weight) },
                        modifier = Modifier.weight(1f).height(64.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c, contentColor = Color.White),
                    ) { Text("$v", style = MaterialTheme.typography.headlineSmall) }
                }
            }
        }
    }
}
