package com.school.hub.feature.games

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.first

/** Общая пауза для всех мини-игр. */
internal object GamePause {
    var paused by mutableStateOf(false)
}

/**
 * Замена kotlinx.coroutines.delay для игр: после ожидания стоит, пока включена пауза.
 * Игровые циклы и таймеры замирают, когда приложение уходит в фон или нажата пауза.
 */
internal suspend fun delay(timeMillis: Long) {
    kotlinx.coroutines.delay(timeMillis)
    if (GamePause.paused) snapshotFlow { GamePause.paused }.first { !it }
}

/** Рекорд, который сохраняется между запусками. */
@Composable
internal fun rememberBest(key: String): MutableIntState {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("games_best", Context.MODE_PRIVATE) }
    val state = remember(key) { mutableIntStateOf(prefs.getInt(key, 0)) }
    LaunchedEffect(key) {
        snapshotFlow { state.intValue }.collect { prefs.edit().putInt(key, it).apply() }
    }
    return state
}

/** Рекорд типа Long (например, лучшее время реакции в мс). */
@Composable
internal fun rememberBestLong(key: String): MutableState<Long> {
    val context = LocalContext.current
    val prefs = remember { context.applicationContext.getSharedPreferences("games_best", Context.MODE_PRIVATE) }
    val state = remember(key) { mutableStateOf(prefs.getLong(key, 0L)) }
    LaunchedEffect(key) {
        snapshotFlow { state.value }.collect { prefs.edit().putLong(key, it).apply() }
    }
    return state
}
