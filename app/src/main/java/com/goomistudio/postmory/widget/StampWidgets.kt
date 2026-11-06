package com.goomistudio.postmory.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import com.goomistudio.postmory.MainActivity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun widgetData(): WidgetData
    fun widgetArt(): WidgetArt
}

/**
 * A home-screen widget that is one picture drawn by [WidgetArt] at the widget's exact size. Tap:
 * free → the paywall; today still waiting → camera; stamped → today's stamp in its album.
 */
abstract class StampWidget(private val style: WidgetStyle) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val data = entry.widgetData()
        val art = entry.widgetArt()
        val first = data.observe().first()
        // Exact mode draws a portrait and a landscape size: each gets a share of the bitmap budget.
        val maxBytes = widgetMemoryCapBytes(context) / BitmapBudgetShare
        val density = context.resources.displayMetrics.density
        provideContent {
            val flow = remember { data.observe() }
            val snap by flow.collectAsState(first)
            val size = LocalSize.current
            val bitmap = remember(snap, size) { art.render(style, snap, size.width.value, size.height.value, density, maxBytes) }
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = art.describe(snap),
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(tapIntent(context, snap))),
            )
        }
    }

    private fun tapIntent(context: Context, snap: WidgetSnapshot): Intent {
        val today = snap.todayStamp
        val link = when {
            !snap.isPro -> WidgetLink.Pro
            today == null -> WidgetLink.Camera
            else -> WidgetLink.Stamp(today.id, today.albumId)
        }
        return Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(link.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    companion object {
        /** Portrait + landscape bitmaps, with headroom for the launcher's own copy. */
        private const val BitmapBudgetShare = 4L
    }
}

class StampOfDayWidget : StampWidget(WidgetStyle.Stamp)
class EnvelopeWidget : StampWidget(WidgetStyle.Envelope)
class AlbumStripWidget : StampWidget(WidgetStyle.Strip)

/** Each receiver also (re)arms the midnight refresh: launchers send an update after a reboot. */
abstract class StampWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onUpdate(context: Context, appWidgetManager: android.appwidget.AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MidnightRefresh.schedule(context)
    }
}

class StampOfDayWidgetReceiver : StampWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StampOfDayWidget()
}

class EnvelopeWidgetReceiver : StampWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EnvelopeWidget()
}

class AlbumStripWidgetReceiver : StampWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlbumStripWidget()
}

/**
 * RemoteViews bitmap limit: 6 × screen width × height bytes, fixed at boot. Estimated from the
 * current window (API 30+) or the display metrics.
 */
internal fun widgetMemoryCapBytes(context: Context): Long {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
        if (bounds != null && bounds.width() > 0 && bounds.height() > 0) return 6L * bounds.width() * bounds.height()
    }
    val dm = context.resources.displayMetrics
    return 6L * dm.widthPixels * dm.heightPixels
}
