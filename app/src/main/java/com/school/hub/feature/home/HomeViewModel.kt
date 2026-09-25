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
)

class HomeViewModel(
    repo: CheatSheetRepository,
    coordinator: SyncCoordinator,
    nearby: NearbySyncManager,
    settings: SettingsStore,
) : ViewModel() {
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
