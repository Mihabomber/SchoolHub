package com.school.hub

import android.content.Context
import com.school.hub.core.data.AppDatabase
import com.school.hub.core.data.ImageStorage
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.ai.engine.LlmEngine
import com.school.hub.feature.ai.models.ModelDownloader
import com.school.hub.feature.cheatsheets.data.CheatSheetRepository
import com.school.hub.feature.focus.FocusTimer
import com.school.hub.feature.grades.GradesRepository
import com.school.hub.feature.homework.data.HomeworkRepository
import com.school.hub.feature.schedule.data.ScheduleRepository
import com.school.hub.feature.translator.TranslatorRepository
import com.school.hub.feature.admin.AdminRepository
import com.school.hub.feature.admin.StatsUploader
import com.school.hub.feature.auth.AuthRepository
import com.school.hub.feature.social.ChatDto
import com.school.hub.feature.social.JsonStore
import com.school.hub.feature.social.RequestDto
import com.school.hub.feature.social.SocialRepository
import com.school.hub.feature.social.StatsDto
import com.school.hub.feature.social.StatsTracker
import com.school.hub.reminders.ReminderScheduler
import com.school.hub.sync.BellDto
import com.school.hub.sync.CheatSheetDto
import com.school.hub.sync.CloudSync
import com.school.hub.sync.GradeDto
import com.school.hub.sync.HomeworkDto
import com.school.hub.sync.LessonDto
import com.school.hub.sync.MqttSync
import com.school.hub.sync.NearbySyncManager
import com.school.hub.sync.SyncCollection
import com.school.hub.sync.SyncCoordinator
import com.school.hub.sync.TypedSyncCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Ручной DI: один контейнер зависимостей на всё приложение. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings by lazy { SettingsStore(appContext) }
    val database by lazy { AppDatabase.build(appContext) }
    val imageStorage by lazy { ImageStorage(appContext) }

    val cheatSheetRepository by lazy { CheatSheetRepository(database.cheatSheetDao(), imageStorage, settings) }
    val scheduleRepository by lazy { ScheduleRepository(database.scheduleDao()) }
    val homeworkRepository by lazy { HomeworkRepository(database.homeworkDao(), settings) }
    val gradesRepository by lazy { GradesRepository(database.gradeDao(), settings) }

    val reminders by lazy { ReminderScheduler(appContext, scheduleRepository, homeworkRepository, settings, appScope) }
    val focusTimer by lazy { FocusTimer(appContext, appScope) }
    val llmEngine by lazy { LlmEngine() }
    val modelDownloader by lazy { ModelDownloader(appContext, settings) }
    val translatorRepository by lazy { TranslatorRepository() }
    val speaker by lazy { com.school.hub.feature.translator.Speaker(appContext) }

    // Соцчасть: статистика, срочные запросы шпор, общий чат (всё расходится по интернету и Bluetooth)
    private val socialDir by lazy { java.io.File(appContext.filesDir, "social").apply { mkdirs() } }
    val statsStore by lazy { JsonStore(java.io.File(socialDir, "stats.json"), StatsDto::class.java, "stats", appScope, 500) }
    val requestStore by lazy { JsonStore(java.io.File(socialDir, "requests.json"), RequestDto::class.java, "requests", appScope, 300) }
    val chatStore by lazy { JsonStore(java.io.File(socialDir, "chat.json"), ChatDto::class.java, "chat", appScope, 800) }
    val stats by lazy { StatsTracker(appContext, settings, statsStore, appScope) }
    val socialRepository by lazy { SocialRepository(settings, requestStore, chatStore, stats) }

    // Аккаунты (Firebase) и админка
    val auth by lazy { AuthRepository(appContext, settings, stats) }
    val adminRepository by lazy { AdminRepository(auth) }
    private val statsUploader by lazy { StatsUploader(appContext, statsStore, auth, appScope) }

    /** Текст, который надо подставить в чат ИИ (например, «Объясни шпаргалку»). */
    @Volatile var pendingAiPrompt: String? = null

    val syncCollections: List<SyncCollection> by lazy {
        val c = cheatSheetRepository
        val s = scheduleRepository
        val h = homeworkRepository
        val g = gradesRepository
        listOf(
            TypedSyncCollection("cheats", CheatSheetDto::class.java, { c.observeDirtyCount() },
                { c.exportAll() }, { c.exportDirty() }, { c.markClean(it) }, { l, d -> c.mergeRemote(l, d) }),
            TypedSyncCollection("lessons", LessonDto::class.java, { s.observeDirtyLessons() },
                { s.exportLessons(false) }, { s.exportLessons(true) }, { s.cleanLessons(it) }, { l, d -> s.mergeLessons(l, d) }),
            TypedSyncCollection("bells", BellDto::class.java, { s.observeDirtyBells() },
                { s.exportBells(false) }, { s.exportBells(true) }, { s.cleanBells(it) }, { l, d -> s.mergeBells(l, d) }),
            TypedSyncCollection("homework", HomeworkDto::class.java, { h.observeDirtyCount() },
                { h.export(false) }, { h.export(true) }, { h.markClean(it) }, { l, d -> h.merge(l, d) }),
            TypedSyncCollection("grades", GradeDto::class.java, { g.observeDirtyCount() },
                { g.export(false) }, { g.export(true) }, { g.markClean(it) }, { l, d -> g.merge(l, d) }),
            statsStore, requestStore, chatStore,
        )
    }

    val cloudSync by lazy { CloudSync(syncCollections, settings) }
    val mqttSync by lazy { MqttSync(syncCollections, settings) }
    val syncCoordinator by lazy { SyncCoordinator(appContext, mqttSync, cloudSync, syncCollections, settings, appScope) }
    val nearbySync by lazy { NearbySyncManager(appContext, syncCollections, settings, appScope) }

    @OptIn(FlowPreview::class)
    fun start() {
        auth.start()
        stats.start()
        statsUploader.start()
        nearbySync.onFileSent = { stats.inc("filesSent") }
        nearbySync.onFileReceived = { stats.inc("filesReceived") }
        appScope.launch {
            runCatching { cheatSheetRepository.seedIfNeeded() }
            runCatching { scheduleRepository.seedIfEmpty() }
            syncCoordinator.start()
            reminders.reschedule()
        }
        // Любое изменение расписания, ДЗ или настроек напоминаний → перепланировать будильник
        appScope.launch {
            merge(
                scheduleRepository.observe(), homeworkRepository.observe(),
                settings.lessonReminders, settings.remindMinutes, settings.homeworkReminders,
            ).drop(1).debounce(1_500).collect { runCatching { reminders.reschedule() } }
        }
    }
}
