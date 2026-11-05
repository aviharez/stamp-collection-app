package com.aviharez.stamp.ui.screens.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.components.AirmailEnvelope
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.Postmark
import com.aviharez.stamp.ui.components.RenderedStamp
import com.aviharez.stamp.ui.components.TapeColor
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.WashiTape
import com.aviharez.stamp.ui.components.description
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.components.rememberPostmarkLines
import com.aviharez.stamp.ui.components.seed
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import java.io.File

/**
 * The three "send to a friend" looks. Each is laid out at a phone-independent design width and
 * exported at [widthPx] × [heightPx] (4 px per dp).
 */
/** [free]: the Envelope for everyone; the other looks are Pro (free users can try them on, not send them). */
enum class PostcardStyle(val widthPx: Int, val heightPx: Int, val free: Boolean) {
    Envelope(1800, 1200, free = true),
    Postcard(1800, 1200, free = false),
    Greetings(1440, 1920, free = false);

    val aspect: Float get() = widthPx.toFloat() / heightPx
    val designWidth: Dp get() = (widthPx / PxPerDp).dp
}

/** What the sender wrote in the sheet (both may be blank). */
data class PostcardText(val to: String, val message: String)

/** The picture for [style]: [stamp] with its postmark, the "To" name and the message. */
@Composable
fun PostcardArt(style: PostcardStyle, stamp: Stamp, text: PostcardText, watermark: Boolean, modifier: Modifier = Modifier) {
    when (style) {
        PostcardStyle.Envelope -> EnvelopeArt(stamp, text, watermark, modifier)
        PostcardStyle.Postcard -> PostcardBackArt(stamp, text, watermark, modifier)
        PostcardStyle.Greetings -> GreetingsArt(stamp, text, watermark, modifier)
    }
}

/** A: airmail envelope. Stamp + postmark top-right, "To" on a dashed line, the message bottom-left. */
@Composable
private fun EnvelopeArt(stamp: Stamp, text: PostcardText, watermark: Boolean, modifier: Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    AirmailEnvelope(modifier.fillMaxSize(), shape = StampTheme.shapes.tile) {
        Box(Modifier.fillMaxSize().background(c.paper).padding(s.l)) {
            Text(
                stringResource(R.string.postcard_par_avion).uppercase(LocalConfiguration.current.locales[0]),
                style = StampTheme.type.overline,
                color = c.cobalt,
                modifier = Modifier.align(Alignment.TopStart),
            )
            PostedStamp(
                stamp,
                tilt = Tilt.stamp(stamp.seed) / 2,
                postmarkDiameter = 72.dp,
                modifier = Modifier.align(Alignment.TopEnd).size(EnvelopeStampBox),
            )
            Column(Modifier.align(Alignment.CenterStart).width(AddressWidth).padding(top = s.xl)) {
                ToLine(text.to, StampTheme.type.noteLarge)
                AddressLine(Modifier.fillMaxWidth())
            }
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Message(text.message, Modifier.weight(1f), maxLines = 2)
                Watermark(watermark, Modifier.padding(start = s.m))
            }
        }
    }
}

/** B: the back of a picture postcard. Message left, a rule, stamp + address right. */
@Composable
private fun PostcardBackArt(stamp: Stamp, text: PostcardText, watermark: Boolean, modifier: Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    GrainedSurface(c.paper, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(s.l)) {
            Text(
                stringResource(R.string.postcard_post_card).uppercase(LocalConfiguration.current.locales[0]),
                style = StampTheme.type.overline,
                color = c.textMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(s.s))
            Row(Modifier.weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    Message(text.message, maxLines = 5, style = StampTheme.type.noteLarge)
                    Watermark(watermark)
                }
                Box(Modifier.padding(horizontal = s.m).width(DividerWidth).fillMaxHeight().background(c.outline))
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PostedStamp(
                        stamp,
                        tilt = 0f,
                        postmarkDiameter = 64.dp,
                        modifier = Modifier.align(Alignment.TopEnd).size(PostcardStampBox),
                    )
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(s.s)) {
                        ToLine(text.to, StampTheme.type.noteLarge)
                        AddressLine(Modifier.fillMaxWidth())
                        AddressLine(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

/** C: tall "Greetings from <city>" card: big taped stamp on kraft, a note card below. */
@Composable
private fun GreetingsArt(stamp: Stamp, text: PostcardText, watermark: Boolean, modifier: Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val place = stamp.place.city ?: stamp.place.country
    val date = rememberDateText(R.string.pattern_day_cd, stamp.day)
    GrainedSurface(c.kraft, modifier.fillMaxSize()) {
        AirmailStripe(Modifier.fillMaxWidth().height(s.xs).align(Alignment.TopCenter))
        Column(
            Modifier.fillMaxSize().padding(horizontal = s.l).padding(top = s.xl, bottom = s.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(if (place != null) R.string.postcard_greetings_from else R.string.postcard_greetings).uppercase(locale),
                style = StampTheme.type.overline,
                color = c.textMedium,
            )
            Text(
                place ?: date,
                style = StampTheme.type.screenTitle,
                color = c.textHigh,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // As big as the card allows; room at the sides for the postmark and the tilt.
            PostedStamp(
                stamp,
                tilt = Tilt.stamp(stamp.seed),
                postmarkDiameter = 80.dp,
                tape = TapeColor.forSeed(stamp.seed),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = s.xl, vertical = s.m),
            )
            if (text.to.isNotBlank() || text.message.isNotBlank()) {
                Column(
                    Modifier
                        .rotate(Tilt.note(stamp.seed))
                        .fillMaxWidth()
                        .background(c.paperWhite, StampTheme.shapes.tile)
                        .padding(horizontal = s.m, vertical = s.s),
                ) {
                    if (text.to.isNotBlank()) ToLine(text.to, StampTheme.type.noteSmall, InkStrength.Soft)
                    Message(text.message, maxLines = 3, style = StampTheme.type.noteLarge)
                }
            }
            Watermark(watermark, Modifier.align(Alignment.End).padding(top = s.s))
        }
    }
}

/**
 * The stamp fitted inside the space [modifier] gives it (any shape keeps its proportions), with its postmark cancelling
 * the bottom-start corner as on Stamped!, and optional washi tape on top.
 */
@Composable
private fun PostedStamp(
    stamp: Stamp,
    tilt: Float,
    postmarkDiameter: Dp,
    modifier: Modifier = Modifier,
    tape: TapeColor? = null,
) {
    val lines = rememberPostmarkLines(stamp.place, stamp.day)
    Box(modifier, contentAlignment = Alignment.Center) {
        Box {
            RenderedStamp(
                file = File(stamp.imagePath),
                shape = stamp.shape,
                perforation = stamp.perforation,
                tilt = tilt,
                contentDescription = stamp.description(),
            )
            if (tape != null) {
                WashiTape(Modifier.align(Alignment.TopStart).offset(x = -TapeInset, y = -TapeInset), color = tape, rotation = TapeAngle)
            }
            Postmark(
                city = lines.top,
                date = lines.bottom,
                diameter = postmarkDiameter,
                rotation = Tilt.postmark(stamp.seed),
                modifier = Modifier.align(Alignment.BottomStart).offset(x = -postmarkDiameter / 2, y = postmarkDiameter / 4),
            )
        }
    }
}

@Composable
private fun ToLine(to: String, style: TextStyle, strength: InkStrength = InkStrength.Strong) {
    // Blank "To" keeps the line's height so the address lines stay put.
    HandNote(
        if (to.isBlank()) " " else stringResource(R.string.postcard_to, to.trim()),
        style = style,
        strength = strength,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Message(
    message: String,
    modifier: Modifier = Modifier,
    maxLines: Int,
    style: TextStyle = StampTheme.type.note,
) {
    if (message.isBlank()) return
    HandNote(message.trim(), style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Free tier: "made with <app>" small in a corner (Pro or a short ad removes it). */
@Composable
private fun Watermark(show: Boolean, modifier: Modifier = Modifier) {
    if (!show) return
    Text(
        stringResource(R.string.postcard_made_with, stringResource(R.string.app_name)),
        style = StampTheme.type.label,
        color = StampTheme.colors.textMedium,
        maxLines = 1,
        modifier = modifier,
    )
}

/** A dashed hand-ruled address line. */
@Composable
private fun AddressLine(modifier: Modifier = Modifier) {
    val color = StampTheme.colors.textLow
    Canvas(modifier.height(LineHeight)) {
        val y = size.height / 2
        drawLine(
            color,
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = size.height / 2,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(LineDash.toPx(), LineGap.toPx())),
        )
    }
}

private const val PxPerDp = 4
private val EnvelopeStampBox = 132.dp
private val PostcardStampBox = 112.dp
private val AddressWidth = 200.dp
private val DividerWidth = 1.dp
private val LineHeight = 2.dp
private val LineDash = 6.dp
private val LineGap = 4.dp
private val TapeInset = 12.dp
private const val TapeAngle = -24f

@PaperPreview
@Composable
private fun PostcardArtPreview() {
    val stamp = previewStamps(1).first()
    val text = PostcardText(to = "Mia", message = "sunrise hike!! wish you were here")
    StampAppTheme {
        Column(verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m)) {
            PostcardStyle.entries.forEach { style ->
                Box(Modifier.size(style.designWidth, style.designWidth / style.aspect)) {
                    PostcardArt(style, stamp, text, watermark = true)
                }
            }
        }
    }
}
