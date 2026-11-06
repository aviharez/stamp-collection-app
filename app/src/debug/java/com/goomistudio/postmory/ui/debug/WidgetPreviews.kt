package com.goomistudio.postmory.ui.debug

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.goomistudio.postmory.R
import com.goomistudio.postmory.data.image.StampRenderer
import com.goomistudio.postmory.domain.model.PerforationStyle
import com.goomistudio.postmory.domain.model.Place
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.domain.usecase.Streak
import com.goomistudio.postmory.ui.components.PillButton
import com.goomistudio.postmory.ui.components.PillStyle
import com.goomistudio.postmory.ui.components.PreviewArtKind
import com.goomistudio.postmory.ui.components.drawPreviewArt
import com.goomistudio.postmory.ui.theme.AirmailPalette
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.widget.AlbumStripWidgetReceiver
import com.goomistudio.postmory.widget.EnvelopeWidgetReceiver
import com.goomistudio.postmory.widget.StampOfDayWidgetReceiver
import com.goomistudio.postmory.widget.WidgetArt
import com.goomistudio.postmory.widget.WidgetSnapshot
import com.goomistudio.postmory.widget.WidgetStamp
import com.goomistudio.postmory.widget.WidgetStyle
import java.io.File
import java.time.LocalDate

/**
 * The three home-screen widgets drawn by the real [WidgetArt] from sample art (never a user's
 * photos), on a stand-in wallpaper: waiting / stamped, 2×2 and 4×2, plus the free (locked) look.
 * "Save previews" writes the launcher picker images to files/widget_previews (pulled into
 * res/drawable-nodpi by hand).
 */
@Composable
fun WidgetPreviews() {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val art = remember { WidgetArt(context.applicationContext) }
    val samples = remember { WidgetSamples(context) }
    var saved by remember { mutableStateOf<String?>(null) }
    val s = StampTheme.spacing
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(Wallpaper))
            .padding(horizontal = s.m, vertical = s.l),
        verticalArrangement = Arrangement.spacedBy(s.s),
    ) {
        WidgetStyle.entries.forEach { style ->
            Text(style.name, style = StampTheme.type.cardTitle, color = StampTheme.colors.textOnDarkHigh)
            Row(horizontalArrangement = Arrangement.spacedBy(s.l)) {
                Rendered(art, style, samples.waiting, SmallDp, SmallDp, density)
                Rendered(art, style, samples.stamped, SmallDp, SmallDp, density)
            }
            Rendered(art, style, samples.waiting, WideDp, SmallDp, density)
            Rendered(art, style, samples.stamped, WideDp, SmallDp, density)
            Rendered(art, style, samples.stamped.copy(isPro = false), WideDp, SmallDp, density)
        }
        PillButton(stringResource(R.string.debug_widget_save), onClick = {
            saved = samples.savePreviews(art, density)
        }, style = PillStyle.Outline)
        saved?.let { Text(it, style = StampTheme.type.label, color = StampTheme.colors.textOnDarkHigh) }
        // Places a real widget on the home screen (the launcher asks first).
        listOf(
            WidgetStyle.Stamp to StampOfDayWidgetReceiver::class.java,
            WidgetStyle.Envelope to EnvelopeWidgetReceiver::class.java,
            WidgetStyle.Strip to AlbumStripWidgetReceiver::class.java,
        ).forEach { (style, receiver) ->
            PillButton(stringResource(R.string.debug_widget_pin, style.name), onClick = {
                AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, receiver), null, null)
            }, style = PillStyle.Outline)
        }
    }
}

@Composable
private fun Rendered(art: WidgetArt, style: WidgetStyle, snap: WidgetSnapshot, w: Float, h: Float, density: Float) {
    val bitmap = remember(style, snap, w, h) { art.render(style, snap, w, h, density, Long.MAX_VALUE).asImageBitmap() }
    Image(bitmap, null, Modifier.size(w.dp, h.dp))
}

private class WidgetSamples(private val context: Context) {
    private val today = LocalDate.now()
    private val kyoto = Place("Kyoto", "Japan", "JP", null, null)
    private val sea = stamp("sea", PreviewArtKind.Sea, today.minusDays(2))
    private val night = stamp("night", PreviewArtKind.Night, today.minusDays(1))
    private val sunset = stamp("sunset", PreviewArtKind.Sunset, today)

    val stamped = WidgetSnapshot(today, sunset, listOf(sea, night), Streak(12, 12, true), isPro = true)
    val waiting = stamped.copy(todayStamp = null, streak = Streak(12, 12, false))

    private fun stamp(id: String, kind: PreviewArtKind, day: LocalDate): WidgetStamp {
        val photo = createBitmap(PhotoW, PhotoH)
        CanvasDrawScope().draw(
            Density(1f), LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(photo.asImageBitmap()),
            Size(PhotoW.toFloat(), PhotoH.toFloat()),
        ) { drawPreviewArt(kind, AirmailPalette) }
        val full = StampRenderer().drawStamp(photo, StampShape.Portrait, PerforationStyle.Classic, FrameShortDp)
        photo.recycle()
        val small = full.scale(SampleW, SampleW * full.height / full.width)
        if (small !== full) full.recycle()
        return WidgetStamp(id, 1L, day, kyoto, small)
    }

    /** The picker previews: each widget stamped, at its default size. */
    fun savePreviews(art: WidgetArt, density: Float): String {
        val dir = File(context.filesDir, "widget_previews").apply { mkdirs() }
        listOf(
            Triple(WidgetStyle.Stamp, SmallDp, "widget_preview_stamp.png"),
            Triple(WidgetStyle.Envelope, WideDp, "widget_preview_envelope.png"),
            Triple(WidgetStyle.Strip, WideDp, "widget_preview_strip.png"),
        ).forEach { (style, w, name) ->
            val bitmap = art.render(style, stamped, w, SmallDp, density, Long.MAX_VALUE)
            File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        return dir.path
    }
}

private const val SmallDp = 156f
private const val WideDp = 336f
private const val PhotoW = 600
private const val PhotoH = 800
private const val SampleW = 360
private const val FrameShortDp = 220f
private val Wallpaper = listOf(
    androidx.compose.ui.graphics.Color(0xFF5B7A8C),
    androidx.compose.ui.graphics.Color(0xFF2E3D52),
    androidx.compose.ui.graphics.Color(0xFF6E8F6A),
)
