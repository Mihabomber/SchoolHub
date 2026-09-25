package com.school.hub.feature.cheatsheets.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import com.school.hub.feature.cheatsheets.model.CheatSheet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(val isLoading: Boolean = true, val sheet: CheatSheet? = null)

class CheatSheetDetailViewModel(handle: SavedStateHandle, private val repo: CheatSheetRepository) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: 0L

    val state: StateFlow<DetailUiState> = repo.observe(id)
        .map { DetailUiState(isLoading = false, sheet = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    fun toggleFavorite() = viewModelScope.launch { repo.toggleFavorite(id) }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
}
