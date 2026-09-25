package com.school.hub.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.school.hub.MainActivity
import com.school.hub.R
import com.school.hub.SchoolApp
import com.school.hub.core.data.SettingsStore
import com.school.hub.feature.homework.data.HomeworkRepository
import com.school.hub.feature.schedule.data.ScheduleRepository
import com.school.hub.feature.schedule.data.hhmm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object Notifications {
    private const val CHANNEL = "reminders"

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, title: String, text: String, id: Int = 1) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Уроки, домашка и таймер", NotificationManager.IMPORTANCE_HIGH)
        )
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

private data class Reminder(val at: LocalDateTime, val title: String, val text: String)

/**
 * Планирует ОДИН ближайший будильник (урок через N минут или вечернее напоминание о ДЗ).
 * После срабатывания ReminderReceiver планирует следующий.
 */
class ReminderScheduler(
    private val context: Context,
    private val schedule: ScheduleRepository,
    private val homework: HomeworkRepository,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    fun requestReschedule() { scope.launch { reschedule() } }

    suspend fun reschedule() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(null, null)
        am.cancel(pi)

        val now = LocalDateTime.now().plusSeconds(20)
        val candidates = mutableListOf<Reminder>()

        if (settings.lessonReminders.value) {
            val data = schedule.observe().first()
            val offset = settings.remindMinutes.value.toLong()
            for (d in 0..7) {
                val date = LocalDate.now().plusDays(d.toLong())
                for (l in data.lessonsFor(date.dayOfWeek.value)) {
                    val bell = data.bell(l.number) ?: continue
                    val at = date.atTime(bell.start).minusMinutes(offset)
                    if (at.isAfter(now)) {
                        val room = if (l.room.isNotBlank()) " · каб. ${l.room}" else ""
                        candidates += Reminder(
                            at,
                            if (offset == 0L) "Начинается урок: ${l.subject.emoji} ${l.subject.title}"
                            else "Через $offset мин: ${l.subject.emoji} ${l.subject.title}",
                            "${l.number}-й урок · ${bell.start.hhmm()}–${bell.end.hhmm()}$room",
                        )
                    }
                }
                if (candidates.isNotEmpty()) break
            }
        }

        if (settings.homeworkReminders.value) {
            for (d in 0..7) {
                val date = LocalDate.now().plusDays(d.toLong())
                val at = date.atTime(LocalTime.of(18, 0))
                if (!at.isAfter(now)) continue
                val count = homework.pendingOn(date.plusDays(1))
                if (count > 0) {
                    candidates += Reminder(at, "Домашка на завтра 📚", "Осталось сделать заданий: $count")
                    break
                }
            }
        }

        val next = candidates.minByOrNull { it.at } ?: return
        val trigger = next.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmPi = pendingIntent(next.title, next.text)
        runCatching {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmPi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmPi)
            }
        }.onFailure {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, alarmPi)
        }
    }

    private fun pendingIntent(title: String?, text: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            if (title != null) putExtra(ReminderReceiver.EXTRA_TITLE, title)
            if (text != null) putExtra(ReminderReceiver.EXTRA_TEXT, text)
        }
        return PendingIntent.getBroadcast(
            context, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE)
        if (title != null) {
            Notifications.show(context, title, intent.getStringExtra(EXTRA_TEXT) ?: "", id = title.hashCode())
        }
        val pending = goAsync()
        val container = (context.applicationContext as SchoolApp).container
        container.appScope.launch {
            try { container.reminders.reschedule() } finally { pending.finish() }
        }
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as SchoolApp).container
        val pending = goAsync()
        container.appScope.launch {
            try { container.reminders.reschedule() } finally { pending.finish() }
        }
    }
}
