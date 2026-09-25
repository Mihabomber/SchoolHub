package com.school.hub.feature.focus

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.school.hub.core.ui.theme.AppGradients
import com.school.hub.reminders.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class FocusMode(val title: String, val minutes: Int, val emoji: String) {
    FOCUS("Фокус", 25, "🎯"), SHORT("Перерыв", 5, "☕"), LONG("Длинный перерыв", 15, "🌴")
}

data class FocusState(
    val mode: FocusMode = FocusMode.FOCUS,
    val remainingSec: Int = FocusMode.FOCUS.minutes * 60,
    val running: Boolean = false,
    val sessionsDone: Int = 0,
) {
    val progress: Float get() = 1f - remainingSec.toFloat() / (mode.minutes * 60)
}

/** Таймер живёт в AppContainer, поэтому не сбрасывается при уходе с экрана. */
class FocusTimer(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(FocusState())
    val state: StateFlow<FocusState> = _state.asStateFlow()
    private var job: Job? = null

    fun toggle() { if (_state.value.running) pause() else start() }

    fun start() {
        if (_state.value.running) return
        val endAt = System.currentTimeMillis() + _state.value.remainingSec * 1000L
        _state.update { it.copy(running = true) }
        job = scope.launch {
            while (true) {
                val left = ((endAt - System.currentTimeMillis()) / 1000L).toInt()
                if (left <= 0) { finish(); break }
                _state.update { it.copy(remainingSec = left) }
                delay(250)
            }
        }
    }

    fun pause() { job?.cancel(); _state.update { it.copy(running = false) } }

    fun reset() { job?.cancel(); _state.update { it.copy(running = false, remainingSec = it.mode.minutes * 60) } }

    fun select(mode: FocusMode) { job?.cancel(); _state.update { it.copy(mode = mode, running = false, remainingSec = mode.minutes * 60) } }

    fun skip() { finish() }

    private fun finish() {
        job?.cancel()
        val s = _state.value
        val wasFocus = s.mode == FocusMode.FOCUS
        val done = s.sessionsDone + if (wasFocus) 1 else 0
        val next = when {
            !wasFocus -> FocusMode.FOCUS
            done % 4 == 0 -> FocusMode.LONG
            else -> FocusMode.SHORT
        }
        _state.value = FocusState(next, next.minutes * 60, false, done)
        runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_BEEP2, 600) }
        Notifications.show(
            context,
            if (wasFocus) "Фокус-сессия завершена 🎉" else "Перерыв окончен",
            if (wasFocus) "Отдохни ${next.minutes} минут" else "Пора снова за дело!",
            id = 4242,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(timer: FocusTimer, onBack: () -> Unit) {
    val s by timer.state.collectAsStateWithLifecycle()
    val animated by animateFloatAsState(s.progress, label = "progress")
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Фокус-таймер") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                FocusMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.mode == m,
                        onClick = { timer.select(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, FocusMode.entries.size),
                    ) { Text(m.emoji + " " + m.minutes) }
                }
            }
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 22.dp.toPx()
                    val d = size.minDimension - stroke
                    val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
                    drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(AppGradients.Violet + AppGradients.Candy),
                        -90f, 360f * animated, false, topLeft, Size(d, d),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.mode.emoji, fontSize = 36.sp)
                    Text(
                        "%02d:%02d".format(s.remainingSec / 60, s.remainingSec % 60),
                        fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = primary,
                    )
                    Text(s.mode.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = timer::reset, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Refresh, "Сброс") }
                Button(
                    onClick = { timer.toggle() },
                    modifier = Modifier.height(72.dp).width(160.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Icon(if (s.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.running) "Пауза" else "Старт", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalIconButton(onClick = timer::skip, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.SkipNext, "Пропустить") }
            }
            Text(
                "Сессий сегодня: ${s.sessionsDone}  " + "🍅".repeat(s.sessionsDone.coerceAtMost(12)),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "Метод помидора: 25 минут работы без телефона, 5 минут отдыха, после 4 сессий — длинный перерыв.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
