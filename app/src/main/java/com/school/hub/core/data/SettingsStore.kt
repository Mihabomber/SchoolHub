package com.school.hub.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString(KEY_DEVICE, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE, it).apply() }

    private val _userName = MutableStateFlow(prefs.getString(KEY_NAME, "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _classCode = MutableStateFlow(prefs.getString(KEY_CLASS, DEFAULT_CLASS) ?: DEFAULT_CLASS)
    val classCode: StateFlow<String> = _classCode.asStateFlow()

    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER, "") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun setUserName(v: String) { prefs.edit().putString(KEY_NAME, v).apply(); _userName.value = v }

    fun setClassCode(v: String) {
        val clean = v.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(32)
        prefs.edit().putString(KEY_CLASS, clean).putLong(KEY_LAST_SYNC, 0L).apply()
        _classCode.value = clean
    }

    fun setServerUrl(v: String) {
        prefs.edit().putString(KEY_SERVER, v.trim()).putLong(KEY_LAST_SYNC, 0L).apply()
        _serverUrl.value = v.trim()
    }

    /** Код класса для сети; пустой заменяем на значение по умолчанию. */
    val effectiveClassCode: String get() = _classCode.value.ifBlank { DEFAULT_CLASS }

    var lastServerSync: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(v) { prefs.edit().putLong(KEY_LAST_SYNC, v).apply() }

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(v) { prefs.edit().putBoolean(KEY_SEEDED, v).apply() }

    companion object {
        const val DEFAULT_CLASS = "my-class"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_CLASS = "class_code"
        private const val KEY_SERVER = "server_url"
        private const val KEY_LAST_SYNC = "last_server_sync"
        private const val KEY_SEEDED = "seeded_v1"
    }
}
