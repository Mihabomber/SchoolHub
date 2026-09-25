package com.school.hub.feature.cheatsheets.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.school.hub.core.data.ImageStorage
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import com.school.hub.feature.cheatsheets.model.CheatSheetDraft
import com.school.hub.feature.cheatsheets.model.Subject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditUiState(
    val isNew: Boolean = true,
    val isLoading: Boolean = false,
    val title: String = "",
    val content: String = "",
    val subject: Subject = Subject.MATH,
    val imagePath: String? = null,
    val author: String = "",
    val isImporting: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank() && (content.isNotBlank() || imagePath != null) && !isImporting
}

class CheatSheetEditViewModel(
    handle: SavedStateHandle,
    private val repo: CheatSheetRepository,
    private val images: ImageStorage,
    private val settings: SettingsStore,
) : ViewModel() {
    private val id: Long? = handle.get<Long>("id")?.takeIf { it > 0 }
    private var originalImage: String? = null

    private val _state = MutableStateFlow(
        EditUiState(isNew = id == null, isLoading = id != null, author = settings.userName.value)
    )
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        if (id != null) viewModelScope.launch {
            val s = repo.get(id)
            originalImage = s?.imagePath
            _state.update {
                if (s == null) it.copy(isLoading = false)
                else it.copy(
                    isLoading = false, title = s.title, content = s.content,
                    subject = s.subject, imagePath = s.imagePath, author = s.author,
                )
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onContent(v: String) = _state.update { it.copy(content = v) }
    fun onSubject(v: Subject) = _state.update { it.copy(subject = v) }
    fun onAuthor(v: String) = _state.update { it.copy(author = v) }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isImporting = true) }
            val path = images.import(uri)
            val prev = _state.value.imagePath
            if (path != null && prev != null && prev != originalImage) images.delete(prev)
            _state.update { it.copy(isImporting = false, imagePath = path ?: it.imagePath) }
        }
    }

    fun removeImage() {
        val prev = _state.value.imagePath
        if (prev != null && prev != originalImage) images.delete(prev)
        _state.update { it.copy(imagePath = null) }
    }

    fun save() {
        val s = _state.value
        if (!s.canSave || s.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            if (s.author.isNotBlank() && settings.userName.value.isBlank()) settings.setUserName(s.author.trim())
            repo.save(
                CheatSheetDraft(id, s.title.trim(), s.content.trim(), s.subject, s.imagePath, s.author.trim())
            )
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
