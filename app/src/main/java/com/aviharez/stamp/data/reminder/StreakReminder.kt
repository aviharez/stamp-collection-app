package com.aviharez.stamp.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aviharez.stamp.MainActivity
import com.aviharez.stamp.R
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.data.settings.ReminderSetting
import com.aviharez.stamp.data.settings.SettingsRepository
import com.aviharez.stamp.di.AppScope
import com.aviharez.stamp.domain.usecase.Streak
import com.aviharez.stamp.widget.WidgetLink
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One evening note when today isn't stamped yet (CLAUDE.md V1.1). An inexact alarm at the chosen
 * time; when it rings we look at the streak and stay quiet if today already has a stamp. The alarm
 * is re-armed after every ring, at app start, after a reboot and when the clock or zone changes.
 */
@Singleton
class StreakReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val stamps: StampRepository,
    private val clock: Clock,
    @AppScope private val scope: CoroutineScope,
) {
    /** App start: follow the setting (re-arm / cancel) and pull the note once today is stamped. */
    fun start() {
        scope.launch {
            settings.reminder.collect { schedule(it) }
        }
        scope.launch {
            stamps.observeStreak()
                .map { it.stampedToday }
                .distinctUntilChanged()
                .filter { it }
                .collect { NotificationManagerCompat.from(context).cancel(NotificationId) }
        }
    }

    suspend fun reschedule() = schedule(settings.reminder.first())

    /** The alarm rang for [day]: post the note if it is still that day and nothing is stamped. */
    suspend fun ring(day: LocalDate?) {
        val setting = settings.reminder.first()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(clock.withZone(zone))
        val streak = stamps.observeStreak().first()
        // A ring Android held back past midnight belongs to a day that is over: skip it.
        if (setting.on && day == today && !streak.stampedToday) post(streak)
        schedule(setting)
    }

    /** Debug gallery: show the note now with [streak], skipping every check except the permission. */
    fun showSample(streak: Streak) = post(streak)

    private fun schedule(setting: ReminderSetting) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        if (!setting.on) {
            alarms.cancel(ringIntent(day = null))
            return
        }
        val next = nextRing(setting.minuteOfDay)
        // Inexact on purpose (no exact-alarm permission): "around 8" is fine for a nudge.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), ringIntent(next.toLocalDate()))
    }

    /** The next time the clock shows [minuteOfDay], strictly after now. */
    private fun nextRing(minuteOfDay: Int): ZonedDateTime {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(clock.withZone(zone))
        val time = LocalTime.of(minuteOfDay / MinutesPerHour, minuteOfDay % MinutesPerHour)
        val todayAt = now.toLocalDate().atTime(time).atZone(zone)
        return if (todayAt.isAfter(now)) todayAt else now.toLocalDate().plusDays(1).atTime(time).atZone(zone)
    }

    /** One PendingIntent (same request code) so a new schedule replaces the old one. */
    private fun ringIntent(day: LocalDate?): PendingIntent = PendingIntent.getBroadcast(
        context,
        AlarmRequestCode,
        Intent(context, ReminderReceiver::class.java)
            .setAction(ActionRing)
            .apply { if (day != null) putExtra(ExtraDay, day.toString()) },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun post(streak: Streak) {
        if (!canPost(context)) return
        ensureChannel(context)
        val res = context.resources
        val (title, body) = if (streak.current > 0) {
            res.getQuantityString(R.plurals.reminder_title_streak, streak.current, streak.current) to
                res.getString(R.string.reminder_body_streak)
        } else {
            res.getString(R.string.reminder_title_start) to res.getString(R.string.reminder_body_start)
        }
        val open = PendingIntent.getActivity(
            context,
            OpenRequestCode,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(WidgetLink.Camera.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val note = NotificationCompat.Builder(context, ChannelId)
            .setSmallIcon(R.drawable.ic_flame)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NotificationId, note)
        } catch (e: SecurityException) {
            Log.w("StreakReminder", "Notification permission was taken away", e)
        }
    }

    companion object {
        const val ChannelId = "streak_reminder"
        private const val NotificationId = 1
        private const val AlarmRequestCode = 1
        private const val OpenRequestCode = 2
        private const val MinutesPerHour = 60
        internal const val ActionRing = "com.aviharez.stamp.action.REMINDER_RING"
        internal const val ExtraDay = "day"

        /** The app may show notifications and the user hasn't muted the reminder channel. */
        fun canPost(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            val channel = manager.getNotificationChannelCompat(ChannelId) ?: return true
            return channel.importance != NotificationManagerCompat.IMPORTANCE_NONE
        }

        fun ensureChannel(context: Context) {
            val channel = NotificationChannelCompat.Builder(ChannelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel_name))
                .setDescription(context.getString(R.string.reminder_channel_desc))
                .build()
            NotificationManagerCompat.from(context).createNotificationChannel(channel)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun streakReminder(): StreakReminder
}

/** The reminder alarm (our own PendingIntent only; not exported). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != StreakReminder.ActionRing) return
        val day = intent.getStringExtra(StreakReminder.ExtraDay)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        runAsync(context) { ring(day) }
    }
}

/** Re-arms the alarm after a reboot, an app update or a clock / time zone change (system broadcasts). */
class ReminderRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RearmActions) return
        runAsync(context) { reschedule() }
    }

    private companion object {
        val RearmActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

private fun BroadcastReceiver.runAsync(context: Context, block: suspend StreakReminder.() -> Unit) {
    val reminder = EntryPointAccessors.fromApplication(context.applicationContext, ReminderEntryPoint::class.java).streakReminder()
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try {
            reminder.block()
        } finally {
            pending.finish()
        }
    }
}
