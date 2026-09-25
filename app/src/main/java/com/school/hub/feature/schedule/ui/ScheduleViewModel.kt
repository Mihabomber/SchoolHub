package com.school.hub.feature.schedule.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.model.Subject
import com.school.hub.feature.schedule.data.NowStatus
import com.school.hub.feature.schedule.data.ScheduleData
import com.school.hub.feature.schedule.data.ScheduleRepository
import com.school.hub.feature.schedule.data.statusAt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class ScheduleUiState(
    val data: ScheduleData = ScheduleData(),
    val now: LocalDateTime = LocalDateTime.now(),
    val status: NowStatus = NowStatus.Free,
)

fun clockFlow(periodMs: Long = 30_000) = flow {
    while (true) { emit(LocalDateTime.now()); delay(periodMs) }
}

class ScheduleViewModel(
    private val repo: ScheduleRepository,
    val settings: SettingsStore,
) : ViewModel() {
    var selectedDay by mutableIntStateOf(LocalDate.now().dayOfWeek.value.let { if (it > 6) 1 else it })
        private set

    val state: StateFlow<ScheduleUiState> = combine(repo.observe(), clockFlow()) { data, now ->
        ScheduleUiState(data, now, data.statusAt(now))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScheduleUiState())

    fun selectDay(d: Int) { selectedDay = d }

    fun saveLesson(number: Int, subject: Subject, room: String, teacher: String) =
        viewModelScope.launch { repo.saveLesson(selectedDay, number, subject, room, teacher) }

    fun deleteLesson(number: Int) = viewModelScope.launch { repo.deleteLesson(selectedDay, number) }

    fun saveBell(number: Int, start: LocalTime, end: LocalTime) = viewModelScope.launch { repo.saveBell(number, start, end) }
    fun deleteBell(number: Int) = viewModelScope.launch { repo.deleteBell(number) }
}
