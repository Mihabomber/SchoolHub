package com.school.hub.navigation

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.hub.AppContainer
import com.school.hub.SchoolApp
import com.school.hub.feature.cheatsheets.ui.CheatSheetDetailViewModel
import com.school.hub.feature.cheatsheets.ui.CheatSheetEditViewModel
import com.school.hub.feature.cheatsheets.ui.CheatSheetListViewModel
import com.school.hub.feature.home.HomeViewModel
import com.school.hub.sync.SyncViewModel

private fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as SchoolApp).container

object AppViewModelFactory {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            HomeViewModel(c.cheatSheetRepository, c.syncCoordinator, c.nearbySync, c.settings)
        }
        initializer { CheatSheetListViewModel(container().cheatSheetRepository) }
        initializer { CheatSheetDetailViewModel(createSavedStateHandle(), container().cheatSheetRepository) }
        initializer {
            val c = container()
            CheatSheetEditViewModel(createSavedStateHandle(), c.cheatSheetRepository, c.imageStorage, c.settings)
        }
        initializer {
            val c = container()
            SyncViewModel(c.settings, c.syncCoordinator, c.nearbySync)
        }
    }
}
