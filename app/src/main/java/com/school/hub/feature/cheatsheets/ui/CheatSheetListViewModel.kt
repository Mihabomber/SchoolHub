package com.school.hub.feature.cheatsheets.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import com.school.hub.feature.cheatsheets.model.CheatSheet
import com.school.hub.feature.cheatsheets.model.Subject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CheatListUiState(
    val items: List<CheatSheet> = emptyList(),
    val counts: Map<Subject, Int> = emptyMap(),
    val total: Int = 0,
    val isLoading: Boolean = true,
)

class CheatSheetListViewModel(private val repo: CheatSheetRepository) : ViewModel() {
    // Состояние поля ввода держим в Compose-state, чтобы курсор не прыгал.
    var query by mutableStateOf("")
        private set
    var selectedSubject by mutableStateOf<Subject?>(null)
        private set

    val state: StateFlow<CheatListUiState> = combine(
        repo.observeAll(),
        snapshotFlow { query },
        snapshotFlow { selectedSubject },
    ) { all, q, subject ->
        val needle = q.trim()
        CheatListUiState(
            items = all.filter { s ->
                (subject == null || s.subject == subject) &&
                    (needle.isEmpty() || s.title.contains(needle, ignoreCase = true) ||
                        s.content.contains(needle, ignoreCase = true) || s.author.contains(needle, ignoreCase = true))
            },
            counts = all.groupingBy { it.subject }.eachCount(),
            total = all.size,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheatListUiState())

    fun onQueryChange(v: String) { query = v }
    fun onSubjectSelected(s: Subject?) { selectedSubject = if (selectedSubject == s) null else s }
}
