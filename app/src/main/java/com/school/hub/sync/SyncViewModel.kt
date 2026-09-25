package com.school.hub.sync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.school.hub.core.data.SettingsStore

class SyncViewModel(
    private val settings: SettingsStore,
    private val coordinator: SyncCoordinator,
    private val nearby: NearbySyncManager,
) : ViewModel() {
    var userName by mutableStateOf(settings.userName.value)
        private set
    var classCode by mutableStateOf(settings.classCode.value)
        private set
    var serverUrl by mutableStateOf(settings.serverUrl.value)
        private set

    val nearbyState = nearby.state
    val online = coordinator.online
    val cloudStatus = coordinator.status

    fun onUserName(v: String) { userName = v; settings.setUserName(v) }

    fun onClassCode(v: String) {
        settings.setClassCode(v)
        classCode = settings.classCode.value
        if (nearby.state.value.running) nearby.stop()
    }

    fun onServerUrl(v: String) { serverUrl = v; settings.setServerUrl(v) }

    fun syncCloud() = coordinator.requestSync()
    fun startNearby() = nearby.start()
    fun stopNearby() = nearby.stop()
}
