package com.school.hub.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import com.school.hub.feature.cheatsheets.model.CheatSheet
import com.school.hub.sync.NearbySyncManager
import com.school.hub.sync.SyncCoordinator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import com.school.hub.feature.homework.data.HomeworkRepository
import com.school.hub.feature.schedule.data.NowStatus
import com.school.hub.feature.schedule.data.ScheduleRepository
import com.school.hub.feature.schedule.data.hhmm
import com.school.hub.feature.schedule.data.statusAt
import com.school.hub.feature.schedule.ui.clockFlow
import java.time.LocalDate

data class HomeUiState(
    val userName: String = "",
    val total: Int = 0,
    val subjects: Int = 0,
    val favorites: Int = 0,
    val fromClassmates: Int = 0,
    val recent: List<CheatSheet> = emptyList(),
    val online: Boolean = false,
    val nearbyRunning: Boolean = false,
    val nearbyPeers: Int = 0,
    val nowText: String = "",
    val hwPending: Int = 0,
    val hwTomorrow: Int = 0,
)

class HomeViewModel(
    repo: CheatSheetRepository,
    coordinator: SyncCoordinator,
    nearby: NearbySyncManager,
    settings: SettingsStore,
    schedule: ScheduleRepository,
    homework: HomeworkRepository,
) : ViewModel() {
    private val today = combine(schedule.observe(), homework.observe(), clockFlow()) { sch, hw, now ->
        val text = when (val st = sch.statusAt(now)) {
            is NowStatus.InLesson -> "Сейчас ${st.lesson.subject.emoji} ${st.lesson.subject.title} · ещё ${st.minutesLeft} мин"
            is NowStatus.Break -> "Перемена · дальше ${st.next.subject.emoji} ${st.next.subject.title} в ${st.bell.start.hhmm()}"
            is NowStatus.Later -> "Следующий урок: ${st.next.subject.emoji} ${st.next.subject.title} в ${st.bell.start.hhmm()}"
            else -> "Расписание пустое — заполни его во вкладке «Уроки»"
        }
        val tomorrow = LocalDate.now().plusDays(1)
        Triple(text, hw.count { !it.done }, hw.count { !it.done && it.dueDate == tomorrow })
    }

    val state: StateFlow<HomeUiState> = combine(
        repo.observeAll(), coordinator.online, nearby.state, settings.userName,
    ) { list, online, nb, name ->
        HomeUiState(
            userName = name,
            total = list.size,
            subjects = list.map { it.subject }.distinct().size,
            favorites = list.count { it.isFavorite },
            fromClassmates = list.count { !it.isMine },
            recent = list.sortedByDescending { it.updatedAt }.take(3),
            online = online,
            nearbyRunning = nb.running,
            nearbyPeers = nb.peers.size,
        )
    }.combine(today) { st, t -> st.copy(nowText = t.first, hwPending = t.second, hwTomorrow = t.third) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
