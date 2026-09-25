package com.school.hub.feature.homework.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.feature.homework.data.Homework
import com.school.hub.feature.homework.data.HomeworkRepository
import com.school.hub.feature.schedule.data.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class HwFilter(val title: String) { TODAY("Сегодня"), TOMORROW("Завтра"), WEEK("Неделя"), ALL("Все"), OVERDUE("Просрочено") }

data class HomeworkUiState(
    val groups: List<Pair<LocalDate, List<Homework>>> = emptyList(),
    val doneCount: Int = 0,
    val totalCount: Int = 0,
    val overdue: Int = 0,
    val isLoading: Boolean = true,
)

class HomeworkViewModel(
    private val repo: HomeworkRepository,
    private val schedule: ScheduleRepository,
) : ViewModel() {
    var filter by mutableStateOf(HwFilter.WEEK)
        private set

    val state: StateFlow<HomeworkUiState> = combine(repo.observe(), snapshotFlow { filter }) { all, f ->
        val today = LocalDate.now()
        val filtered = all.filter { h ->
            when (f) {
                HwFilter.TODAY -> h.dueDate == today
                HwFilter.TOMORROW -> h.dueDate == today.plusDays(1)
                HwFilter.WEEK -> !h.dueDate.isBefore(today) && !h.dueDate.isAfter(today.plusDays(7))
                HwFilter.ALL -> true
                HwFilter.OVERDUE -> h.dueDate.isBefore(today) && !h.done
            }
        }
        HomeworkUiState(
            groups = filtered.groupBy { it.dueDate }.toSortedMap().map { it.key to it.value },
            doneCount = filtered.count { it.done },
            totalCount = filtered.size,
            overdue = all.count { it.dueDate.isBefore(today) && !it.done },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeworkUiState())

    fun setFilter(f: HwFilter) { filter = f }
    fun toggle(uuid: String) = viewModelScope.launch { repo.toggleDone(uuid) }
    fun delete(uuid: String) = viewModelScope.launch { repo.delete(uuid) }

    fun save(existing: Homework?, subject: Subject, text: String, due: LocalDate) = viewModelScope.launch {
        if (existing == null) repo.add(subject, text, due) else repo.update(existing.uuid, subject, text, due)
    }

    suspend fun nextLessonDate(subject: Subject): LocalDate? = schedule.nextLessonDate(subject)
}
