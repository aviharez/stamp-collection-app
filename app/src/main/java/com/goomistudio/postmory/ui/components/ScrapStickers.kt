package com.goomistudio.postmory.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.ScrapSticker
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What a sticker is called (tray buttons, TalkBack). */
@get:StringRes
val ScrapSticker.label: Int
    get() = when (this) {
        ScrapSticker.ParAvion -> R.string.scrap_sticker_par_avion
        ScrapSticker.Received -> R.string.scrap_sticker_received
        ScrapSticker.Fragile -> R.string.scrap_sticker_fragile
        ScrapSticker.WaxSeal -> R.string.scrap_sticker_wax_seal
        ScrapSticker.Ticket -> R.string.scrap_sticker_ticket
        ScrapSticker.Heart -> R.string.scrap_sticker_heart
        ScrapSticker.Star -> R.string.scrap_sticker_star
        ScrapSticker.Postmark -> R.string.scrap_sticker_postmark
        ScrapSticker.TapeRed -> R.string.scrap_sticker_tape_red
        ScrapSticker.TapeCobalt -> R.string.scrap_sticker_tape_cobalt
        ScrapSticker.TapeSage -> R.string.scrap_sticker_tape_sage
    }

/**
 * One paper sticker at its natural size (scrapbook pages scale and turn it).
 *
 * @param text the wax seal's letter, the ticket's date, or the postmark's two lines ("top\nbottom").
 */
@Composable
fun ScrapStickerArt(sticker: ScrapSticker, text: String, modifier: Modifier = Modifier) {
    when (sticker) {
        ScrapSticker.ParAvion -> ParAvionLabel(modifier)
        ScrapSticker.Received -> RubberStamp(stringResource(R.string.scrap_rubber_received), modifier)
        ScrapSticker.Fragile -> RubberStamp(stringResource(R.string.scrap_rubber_fragile), modifier)
        ScrapSticker.WaxSeal -> WaxSeal(text, modifier)
        ScrapSticker.Ticket -> Ticket(text, modifier)
        ScrapSticker.Heart -> InkHeart(modifier.size(StickerIcon))
        ScrapSticker.Star -> InkStar(modifier.size(StickerIcon))
        ScrapSticker.Postmark -> {
            val top = text.substringBefore('\n')
            val bottom = text.substringAfter('\n', "")
            Postmark(top, bottom, modifier, diameter = PostmarkDiameter, rotation = 0f)
        }
        ScrapSticker.TapeRed -> WashiTape(modifier, TapeColor.Red, width = TapeWidth, height = TapeHeight)
        ScrapSticker.TapeCobalt -> WashiTape(modifier, TapeColor.Cobalt, width = TapeWidth, height = TapeHeight)
        ScrapSticker.TapeSage -> WashiTape(modifier, TapeColor.Sage, width = TapeWidth, height = TapeHeight)
    }
}

/** Blue airmail label: "PAR AVION / by air mail" inside a white keyline. */
@Composable
private fun ParAvionLabel(modifier: Modifier) {
    val c = StampTheme.colors
    Column(
        modifier
            .background(c.cobalt, StampTheme.shapes.plate)
            .padding(StickerKeyline)
            .border(1.dp, c.paperWhite, StampTheme.shapes.plate)
            .padding(horizontal = StampTheme.spacing.xs, vertical = StickerKeyline),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scrap_par_avion), style = StampTheme.type.overline.copy(fontWeight = FontWeight.Bold), color = c.paperWhite)
        Text(stringResource(R.string.scrap_by_air_mail), style = StampTheme.type.noteSmall, color = c.textOnDarkMedium)
    }
}

/** A red rubber-stamp word in a box, ink slightly see-through like the postmark. */
@Composable
private fun RubberStamp(text: String, modifier: Modifier) {
    val ink = StampTheme.colors.postmarkOnLight
    Text(
        text,
        style = StampTheme.type.overline.copy(fontWeight = FontWeight.Bold),
        color = ink,
        modifier = modifier
            .border(RubberBorder, ink, StampTheme.shapes.plate)
            .padding(horizontal = StampTheme.spacing.xs, vertical = StampTheme.spacing.xxs),
    )
}

@Composable
private fun WaxSeal(letter: String, modifier: Modifier) {
    val c = StampTheme.colors
    Box(
        modifier
            .size(SealSize)
            .background(c.airmailRed, CircleShape)
            .padding(StickerKeyline)
            .border(1.5.dp, c.textOnDarkLow, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter.take(1), style = StampTheme.type.albumName, color = c.paperWhite)
    }
}

/** Sage cinema/train ticket: "ADMIT ONE" + a date. */
@Composable
private fun Ticket(date: String, modifier: Modifier) {
    val c = StampTheme.colors
    Column(
        modifier
            .background(c.sage, CutCornerShape(TicketCut))
            .padding(horizontal = StampTheme.spacing.s, vertical = StampTheme.spacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.scrap_admit_one), style = StampTheme.type.overline.copy(fontWeight = FontWeight.Bold), color = c.paperWhite)
        Text(date, style = StampTheme.type.noteSmall, color = c.textOnDarkMedium)
    }
}

/** A heart drawn in one red pen stroke. */
@Composable
private fun InkHeart(modifier: Modifier) {
    val red = StampTheme.colors.airmailRed
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.05f, h * 0.55f, w * 0.0f, h * 0.15f, w * 0.3f, h * 0.12f)
            cubicTo(w * 0.42f, h * 0.1f, w * 0.5f, h * 0.22f, w * 0.5f, h * 0.3f)
            cubicTo(w * 0.52f, h * 0.18f, w * 0.62f, h * 0.08f, w * 0.75f, h * 0.12f)
            cubicTo(w * 1.02f, h * 0.2f, w * 0.92f, h * 0.6f, w * 0.52f, h * 0.9f)
        }
        drawPath(path, red, style = Stroke(width = PenStroke.toPx(), cap = StrokeCap.Round))
    }
}

/** A five-point star in cobalt pen. */
@Composable
private fun InkStar(modifier: Modifier) {
    val blue = StampTheme.colors.cobalt
    Canvas(modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val outer = size.minDimension * 0.46f
        val inner = outer * 0.45f
        val path = Path()
        for (i in 0 until StarPoints * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = -PI / 2 + i * PI / StarPoints
            val x = cx + (r * cos(a)).toFloat()
            val y = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, blue, style = Stroke(width = PenStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val StickerKeyline = 2.dp
private val RubberBorder = 2.dp
private val SealSize = 48.dp
private val TicketCut = 6.dp
private val StickerIcon = 40.dp
private val PenStroke = 2.5.dp
private val PostmarkDiameter = 64.dp
private val TapeWidth = 96.dp
private val TapeHeight = 26.dp
private const val StarPoints = 5

@PaperPreview
@Composable
private fun ScrapStickersPreview() {
    StampAppTheme {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
            verticalArrangement = Arrangement.spacedBy(StampTheme.spacing.m),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            ScrapSticker.entries.forEach { sticker ->
                val text = when (sticker) {
                    ScrapSticker.WaxSeal -> "J"
                    ScrapSticker.Ticket -> "05 NOV"
                    ScrapSticker.Postmark -> "KYOTO\n05 NOV 26 · JP"
                    else -> ""
                }
                ScrapStickerArt(sticker, text)
            }
        }
    }
}
