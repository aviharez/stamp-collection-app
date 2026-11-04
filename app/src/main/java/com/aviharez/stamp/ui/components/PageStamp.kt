package com.aviharez.stamp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.PerforationStyle
import com.aviharez.stamp.domain.model.Place
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.domain.model.StampShape
import com.aviharez.stamp.domain.model.StampSource
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Stable seed for a stamp's hand-placed look (tilt, tape colour, which one gets tape). */
val Stamp.seed: Long get() = id.hashCode().toLong()

/** What to write next to a stamp: its title, else the note on the back, else the city. */
val Stamp.handLabel: String? get() = title ?: caption ?: place.city

/** Spoken description of a stamp: "Stamp, Kyoto, 4 Nov". */
@Composable
fun Stamp.description(): String {
    val date = rememberDateText(R.string.pattern_short_date, day)
    val where = handLabel
    return stringResource(R.string.stamp_cd, if (where != null) "$where, $date" else date)
}

/**
 * Hand-written line next to a stamp: "4 nov · sunrise hike!!" (date lower-cased, then the title,
 * the note on the back, or the city). [withDate] = false drops the date when there is a label.
 */
@Composable
fun Stamp.pageNote(withDate: Boolean = true): String {
    val locale = LocalConfiguration.current.locales[0]
    val date = rememberDateText(R.string.pattern_short_date, day).lowercase(locale)
    val label = title ?: caption ?: place.city?.lowercase(locale)
    return when {
        label == null -> date
        withDate -> stringResource(R.string.stamp_page_note, date, label)
        else -> label
    }
}

/**
 * A saved stamp stuck onto an album page: tilted by its seed, optionally held by washi tape on
 * the top edge. Fits inside the space it is given, keeping the stamp's aspect ratio.
 */
@Composable
fun PageStamp(
    stamp: Stamp,
    modifier: Modifier = Modifier,
    tape: TapeColor? = null,
    tilt: Float = Tilt.stamp(stamp.seed),
    alignment: Alignment = Alignment.Center,
) {
    Box(modifier, contentAlignment = alignment) {
        Box {
            RenderedStamp(
                file = File(stamp.imagePath),
                shape = stamp.shape,
                perforation = stamp.perforation,
                tilt = tilt,
                contentDescription = stamp.description(),
            )
            if (tape != null) {
                WashiTape(
                    Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = -TapeOverlap),
                    color = tape,
                    rotation = Tilt.tape(stamp.seed),
                )
            }
        }
    }
}

private val TapeOverlap = 6.dp

/** Sample stamps for @Preview: files don't exist, RenderedStamp draws stand-in art. */
internal fun previewStamps(count: Int): List<Stamp> {
    val shapes = listOf(StampShape.Portrait, StampShape.Portrait, StampShape.Landscape, StampShape.Square, StampShape.Circle)
    val labels = listOf("sunrise hike!!", null, "matcha #3", null, "tokyo nights", null)
    return List(count) { i ->
        Stamp(
            id = "preview-$i",
            albumId = 1,
            shape = shapes[i % shapes.size],
            perforation = PerforationStyle.Classic,
            imagePath = "preview-$i.png",
            originalPath = "",
            takenAt = Instant.EPOCH,
            day = LocalDate.of(2026, 11, 4).minusDays(i.toLong()),
            place = if (i % 2 == 0) Place("Kyoto", "Japan", "JP", null, null) else Place.Unknown,
            title = null,
            caption = labels[i % labels.size],
            source = StampSource.Camera,
        )
    }
}

@PaperPreview
@Composable
private fun PageStampPreview() {
    StampAppTheme {
        Row(
            Modifier.padding(StampTheme.spacing.l),
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.l),
        ) {
            previewStamps(3).forEachIndexed { i, stamp ->
                PageStamp(stamp, Modifier.width(88.dp), tape = if (i != 1) TapeColor.forSeed(i.toLong()) else null)
            }
        }
    }
}
