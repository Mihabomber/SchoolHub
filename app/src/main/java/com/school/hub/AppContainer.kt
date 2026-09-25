package com.school.hub

import android.content.Context
import com.school.hub.core.data.AppDatabase
import com.school.hub.core.data.ImageStorage
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import com.school.hub.sync.CloudSync
import com.school.hub.sync.NearbySyncManager
import com.school.hub.sync.SyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Ручной DI: один контейнер зависимостей на всё приложение. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsStore(appContext) }
    val database by lazy { AppDatabase.build(appContext) }
    val imageStorage by lazy { ImageStorage(appContext) }
    val cheatSheetRepository by lazy {
        CheatSheetRepository(database.cheatSheetDao(), imageStorage, settings)
    }
    val cloudSync by lazy { CloudSync(cheatSheetRepository, settings) }
    val syncCoordinator by lazy {
        SyncCoordinator(appContext, cloudSync, cheatSheetRepository, settings, appScope)
    }
    val nearbySync by lazy {
        NearbySyncManager(appContext, cheatSheetRepository, settings, appScope)
    }

    fun start() {
        appScope.launch {
            cheatSheetRepository.seedIfNeeded()
            syncCoordinator.start()
        }
    }
}
