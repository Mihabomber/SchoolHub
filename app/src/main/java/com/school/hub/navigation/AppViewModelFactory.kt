package com.school.hub.navigation

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.school.hub.AppContainer
import com.school.hub.SchoolApp
import com.school.hub.feature.ai.ui.AiChatViewModel
import com.school.hub.feature.ai.ui.AiModelsViewModel
import com.school.hub.feature.cheatsheets.ui.CheatSheetDetailViewModel
import com.school.hub.feature.cheatsheets.ui.CheatSheetEditViewModel
import com.school.hub.feature.cheatsheets.ui.CheatSheetListViewModel
import com.school.hub.feature.grades.GradesViewModel
import com.school.hub.feature.home.HomeViewModel
import com.school.hub.feature.homework.ui.HomeworkViewModel
import com.school.hub.feature.schedule.ui.ScheduleViewModel
import com.school.hub.feature.settings.SettingsViewModel
import com.school.hub.feature.translator.TranslatorViewModel
import com.school.hub.sync.SyncViewModel

fun CreationExtras.container(): AppContainer = (this[APPLICATION_KEY] as SchoolApp).container

object AppViewModelFactory {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            HomeViewModel(c.cheatSheetRepository, c.syncCoordinator, c.nearbySync, c.settings, c.scheduleRepository, c.homeworkRepository)
        }
        initializer { CheatSheetListViewModel(container().cheatSheetRepository) }
        initializer { CheatSheetDetailViewModel(createSavedStateHandle(), container().cheatSheetRepository) }
        initializer {
            val c = container()
            CheatSheetEditViewModel(createSavedStateHandle(), c.cheatSheetRepository, c.imageStorage, c.settings)
        }
        initializer { val c = container(); SyncViewModel(c.settings, c.syncCoordinator, c.nearbySync) }
        initializer { val c = container(); ScheduleViewModel(c.scheduleRepository, c.settings) }
        initializer { val c = container(); HomeworkViewModel(c.homeworkRepository, c.scheduleRepository) }
        initializer { GradesViewModel(container().gradesRepository) }
        initializer {
            val c = container()
            AiModelsViewModel(this[APPLICATION_KEY]!!, c.modelDownloader, c.llmEngine, c.settings)
        }
        initializer {
            val c = container()
            AiChatViewModel(createSavedStateHandle(), c.llmEngine, c.modelDownloader, c)
        }
        initializer { val c = container(); TranslatorViewModel(c.translatorRepository, c.imageStorage) }
        initializer { val c = container(); SettingsViewModel(c.settings, c.reminders) }
    }
}
