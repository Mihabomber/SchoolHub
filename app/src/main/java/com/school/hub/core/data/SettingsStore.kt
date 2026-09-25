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

    private fun str(key: String, def: String) = MutableStateFlow(prefs.getString(key, def) ?: def)
    private fun bool(key: String, def: Boolean) = MutableStateFlow(prefs.getBoolean(key, def))
    private fun int(key: String, def: Int) = MutableStateFlow(prefs.getInt(key, def))

    private val _userName = str(KEY_NAME, "")
    val userName: StateFlow<String> = _userName.asStateFlow()
    private val _classCode = str(KEY_CLASS, DEFAULT_CLASS)
    val classCode: StateFlow<String> = _classCode.asStateFlow()
    private val _serverUrl = str(KEY_SERVER, "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    /** 0 — как в системе, 1 — светлая, 2 — тёмная */
    private val _themeMode = int("theme_mode", 0)
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()
    private val _dynamicColor = bool("dynamic_color", false)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _lessonReminders = bool("lesson_reminders", true)
    val lessonReminders: StateFlow<Boolean> = _lessonReminders.asStateFlow()
    private val _remindMinutes = int("remind_minutes", 5)
    val remindMinutes: StateFlow<Int> = _remindMinutes.asStateFlow()
    private val _homeworkReminders = bool("homework_reminders", true)
    val homeworkReminders: StateFlow<Boolean> = _homeworkReminders.asStateFlow()

    private val _wifiOnly = bool("wifi_only", true)
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    fun setUserName(v: String) { prefs.edit().putString(KEY_NAME, v).apply(); _userName.value = v }

    fun setClassCode(v: String) {
        val clean = v.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(32)
        prefs.edit().putString(KEY_CLASS, clean).apply()
        _classCode.value = clean
        resetSyncMarks()
    }

    fun setServerUrl(v: String) {
        prefs.edit().putString(KEY_SERVER, v.trim()).apply()
        _serverUrl.value = v.trim()
        resetSyncMarks()
    }

    fun setThemeMode(v: Int) { prefs.edit().putInt("theme_mode", v).apply(); _themeMode.value = v }
    fun setDynamicColor(v: Boolean) { prefs.edit().putBoolean("dynamic_color", v).apply(); _dynamicColor.value = v }
    fun setLessonReminders(v: Boolean) { prefs.edit().putBoolean("lesson_reminders", v).apply(); _lessonReminders.value = v }
    fun setRemindMinutes(v: Int) { prefs.edit().putInt("remind_minutes", v).apply(); _remindMinutes.value = v }
    fun setHomeworkReminders(v: Boolean) { prefs.edit().putBoolean("homework_reminders", v).apply(); _homeworkReminders.value = v }
    fun setWifiOnly(v: Boolean) { prefs.edit().putBoolean("wifi_only", v).apply(); _wifiOnly.value = v }

    val effectiveClassCode: String get() = _classCode.value.ifBlank { DEFAULT_CLASS }

    fun lastSync(collection: String): Long = prefs.getLong("last_sync_$collection", 0L)
    fun setLastSync(collection: String, v: Long) { prefs.edit().putLong("last_sync_$collection", v).apply() }

    private fun resetSyncMarks() {
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith("last_sync_") }.forEach { e.remove(it) }
        e.apply()
    }

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(v) { prefs.edit().putBoolean(KEY_SEEDED, v).apply() }

    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(v) { prefs.edit().putBoolean("asked_notifications", v).apply() }

    fun getLong(key: String): Long = prefs.getLong(key, -1L)
    fun putLong(key: String, v: Long) { prefs.edit().putLong(key, v).apply() }
    fun remove(key: String) { prefs.edit().remove(key).apply() }

    companion object {
        const val DEFAULT_CLASS = "my-class"
        private const val KEY_DEVICE = "device_id"
        private const val KEY_NAME = "user_name"
        private const val KEY_CLASS = "class_code"
        private const val KEY_SERVER = "server_url"
        private const val KEY_SEEDED = "seeded_v2"
    }
}
