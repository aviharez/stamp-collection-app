package com.aviharez.stamp.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.aviharez.stamp.data.billing.ProStatus
import com.aviharez.stamp.data.repo.StampRepository
import com.aviharez.stamp.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Redraws every placed widget. */
suspend fun updateStampWidgets(context: Context) {
    runCatching {
        StampOfDayWidget().updateAll(context)
        EnvelopeWidget().updateAll(context)
        AlbumStripWidget().updateAll(context)
    }.onFailure { Log.w("Widgets", "Widget update failed", it) }
}

/**
 * While the app runs, redraws the widgets whenever what they show changes (a new, moved, reframed
 * or deleted stamp, the streak, Pro). Cheap: compares ids and paths, no pictures decoded.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stamps: StampRepository,
    private val pro: ProStatus,
    @AppScope private val scope: CoroutineScope,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        MidnightRefresh.schedule(context)
        scope.launch {
            combine(
                stamps.observeRecent(RecentKeyLimit).map { list -> list.map { listOf(it.id, it.albumId, it.day, it.imagePath, it.place) } },
                stamps.observeStreak(),
                pro.isPro,
            ) { recent, streak, isPro -> Triple(recent, streak, isPro) }
                .distinctUntilChanged()
                .debounce(DebounceMillis)
                .collectLatest { updateStampWidgets(context) }
        }
    }

    private companion object {
        const val RecentKeyLimit = 40
        const val DebounceMillis = 400L
    }
}

/**
 * At midnight "today" changes (slot opens again, streak may end), but no data does, so an inexact
 * alarm redraws the widgets then. Re-armed by every widget update and every app start.
 */
object MidnightRefresh {
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()
        val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() + SlackMillis
        alarms.set(AlarmManager.RTC, next, pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, MidnightReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** A little past midnight, so "now" is surely the new day. */
    private const val SlackMillis = 60_000L
}

class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                updateStampWidgets(context.applicationContext)
                MidnightRefresh.schedule(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
