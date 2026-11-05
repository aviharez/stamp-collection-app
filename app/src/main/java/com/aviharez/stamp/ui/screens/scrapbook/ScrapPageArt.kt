package com.aviharez.stamp.ui.screens.scrapbook

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.ScrapItem
import com.aviharez.stamp.domain.model.ScrapKind
import com.aviharez.stamp.domain.model.ScrapRules
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.ScrapStickerArt
import com.aviharez.stamp.ui.components.description
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.screens.export.ScaledArt
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import java.io.File

/** Layout width of a scrapbook page: every piece is sized in these dp, whatever the screen. */
val ScrapDesignWidth = 360.dp

/** Base widths (scale 1) in page dp. Stickers keep their own natural size. */
internal val StampBaseWidth = 140.dp
internal val NoteWidth = 180.dp
internal val TitleWidth = 300.dp

/** Editor-only extras: which piece is selected, and where each piece's size goes for hit-testing. */
class ScrapEditing(val selectedId: String?, val onMeasured: (id: String, size: IntSize) -> Unit)

/**
 * A scrapbook page's pieces, laid out on a [ScrapDesignWidth]-wide 3 : 4 page (use [ScaledArt]).
 * Each piece's centre sits at its fraction of the page, then it is scaled and turned around that
 * centre. Stamps that no longer exist are skipped; so are empty writing, except in the editor.
 */
@Composable
fun ScrapPageArt(
    items: List<ScrapItem>,
    stamps: Map<String, Stamp>,
    modifier: Modifier = Modifier,
    editing: ScrapEditing? = null,
) {
    val visible = items.filter { item ->
        when {
            item.kind == ScrapKind.Stamp -> item.ref in stamps
            item.kind == ScrapKind.Sticker -> item.sticker != null
            else -> editing != null || !item.isBlankWriting
        }
    }
    Layout(
        content = {
            visible.forEach { item ->
                key(item.id) { ScrapPiece(item, stamps, editing) }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val loose = Constraints(maxWidth = w * 2, maxHeight = h * 2)
        val placeables = measurables.map { it.measure(loose) }
        layout(w, h) {
            placeables.forEachIndexed { i, p ->
                val item = visible[i]
                p.place((item.x * w - p.width / 2f).toInt(), (item.y * h - p.height / 2f).toInt())
            }
        }
    }
}

@Composable
private fun ScrapPiece(item: ScrapItem, stamps: Map<String, Stamp>, editing: ScrapEditing?) {
    val selected = editing?.selectedId == item.id
    val outline = StampTheme.colors.ink
    val dash = with(androidx.compose.ui.platform.LocalDensity.current) { SelectionDash.toPx() }
    val stroke = with(androidx.compose.ui.platform.LocalDensity.current) { SelectionStroke.toPx() }
    val pad = with(androidx.compose.ui.platform.LocalDensity.current) { SelectionPad.toPx() }
    val modifier = Modifier
        .then(if (editing != null) Modifier.onSizeChanged { editing.onMeasured(item.id, it) } else Modifier)
        .graphicsLayer {
            scaleX = item.scale
            scaleY = item.scale
            rotationZ = item.rotation
        }
        .then(
            if (selected) {
                Modifier.drawWithContent {
                    drawContent()
                    // Same thickness on screen whatever the piece's scale.
                    val s = item.scale.coerceAtLeast(ScrapRules.MinScale)
                    val p = pad / s
                    drawRoundRect(
                        color = outline,
                        topLeft = Offset(-p, -p),
                        size = Size(size.width + 2 * p, size.height + 2 * p),
                        cornerRadius = CornerRadius(p),
                        style = Stroke(width = stroke / s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash / s, dash / s))),
                    )
                }
            } else {
                Modifier
            },
        )
    when (item.kind) {
        ScrapKind.Stamp -> {
            val stamp = stamps.getValue(item.ref)
            RenderedStamp(
                file = File(stamp.imagePath),
                shape = stamp.shape,
                contentDescription = stamp.description(),
                perforation = stamp.perforation,
                elevation = Elevation.Card,
                modifier = modifier.width(StampBaseWidth),
            )
        }
        ScrapKind.Sticker -> ScrapStickerArt(item.sticker ?: return, item.text, modifier)
        ScrapKind.Note -> {
            val blank = item.text.isBlank()
            HandNote(
                if (blank) stringResource(R.string.scrap_note_placeholder) else item.text,
                style = StampTheme.type.noteLarge,
                strength = if (blank) InkStrength.Soft else InkStrength.Strong,
                textAlign = TextAlign.Center,
                modifier = modifier.width(NoteWidth),
            )
        }
        ScrapKind.Title -> {
            val blank = item.text.isBlank()
            Text(
                if (blank) stringResource(R.string.scrap_title_placeholder) else item.text,
                style = scrapTitleStyle(),
                color = if (blank) StampTheme.colors.textLow else StampTheme.colors.textHigh,
                textAlign = TextAlign.Center,
                modifier = modifier.width(TitleWidth),
            )
        }
    }
}

@Composable
internal fun scrapTitleStyle() = StampTheme.type.screenTitle.copy(
    fontSize = StampTheme.type.albumName.fontSize * TitleScale,
    lineHeight = StampTheme.type.albumName.lineHeight * TitleScale,
)

/**
 * A finished scrapbook page as it sits in an album: a kraft sheet the size of the album's other
 * pages, with the 3 : 4 page drawn as large as fits in its middle.
 */
@Composable
fun ScrapSheet(items: List<ScrapItem>, stamps: Map<String, Stamp>, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val shape = StampTheme.shapes.card
    GrainedSurface(c.kraft, modifier.tintedShadow(Elevation.Card, shape, c.shadowWarm), shape) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val w = min(maxWidth, maxHeight * ScrapRules.Aspect)
            ScaledArt(w, ScrapRules.Aspect, ScrapDesignWidth) { ScrapPageArt(items, stamps) }
        }
    }
}

private const val TitleScale = 1.3f
private val SelectionDash = 5.dp
private val SelectionStroke = 1.5.dp
private val SelectionPad = 6.dp

@PaperPreview
@Composable
private fun ScrapSheetPreview() {
    val stamps = previewStamps(4)
    val items = ScrapLayout.Diary.build(
        ScrapLayoutInput(stamps, albumName = "Japan trip", postmark = "KYOTO\n05 NOV 26 · JP", sealLetter = "J", ticketDate = "05 NOV"),
    )
    StampAppTheme {
        ScrapSheet(items, stamps.associateBy { it.id }, Modifier.width(ScrapDesignWidth).height(ScrapDesignWidth / ScrapRules.Aspect))
    }
}
