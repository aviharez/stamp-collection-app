package com.aviharez.stamp.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import com.aviharez.stamp.R
import com.aviharez.stamp.ui.components.drawPostmark
import com.aviharez.stamp.ui.components.postmarkLines
import com.aviharez.stamp.ui.components.postmarkWidth
import com.aviharez.stamp.ui.theme.AirmailPalette
import com.aviharez.stamp.ui.theme.AirmailType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

/** The three looks reviewed in the widget concepts (A, B, C). */
enum class WidgetStyle { Stamp, Envelope, Strip }

/**
 * Draws a whole widget as one bitmap. Home-screen widgets (RemoteViews) can't use the app's fonts
 * or custom drawing, so the Airmail art (Fraunces, Caveat, perforated stamps, postmarks, stripes,
 * grain) is painted here with the same tokens and the shared [drawPostmark], and the widget just
 * shows the picture. Layouts are drawn in a reference frame (156 × 156 dp, or 336 × 156 dp when
 * the widget is wide) scaled to fit and centred; backgrounds fill the whole widget.
 */
@Singleton
class WidgetArt @Inject constructor(@ApplicationContext private val context: Context) {

    /** Text measured at density 1, so text sizes are in the same dp units as the layouts. */
    private val measurer by lazy { TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr) }
    private val grain by lazy { BitmapFactory.decodeResource(context.resources, R.drawable.paper_grain)?.asImageBitmap() }

    private val c = AirmailPalette
    private val t = AirmailType
    private val locale: Locale get() = context.resources.configuration.locales[0] ?: Locale.getDefault()

    /**
     * @param widthDp / heightDp the widget's size; [maxBytes] caps the bitmap (RemoteViews limit),
     *   lowering the resolution when needed.
     */
    fun render(style: WidgetStyle, snap: WidgetSnapshot, widthDp: Float, heightDp: Float, density: Float, maxBytes: Long): Bitmap {
        val w = widthDp.coerceAtLeast(MinSideDp)
        val h = heightDp.coerceAtLeast(MinSideDp)
        var scale = density
        val bytes = w * h * scale * scale * BytesPerPixel
        if (bytes > maxBytes) scale *= sqrt(maxBytes / bytes)
        val out = createBitmap(ceil(w * scale).toInt(), ceil(h * scale).toInt())
        val canvas = androidx.compose.ui.graphics.Canvas(out.asImageBitmap())
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(out.width.toFloat(), out.height.toFloat())) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                val locked = !snap.isPro
                if (locked) {
                    // Free: the real widget, faded, with a "Pro" tag (tap opens the paywall).
                    drawContext.canvas.nativeCanvas.saveLayerAlpha(0f, 0f, w, h, (LockedAlpha * 255).toInt())
                }
                when (style) {
                    WidgetStyle.Stamp -> Unit // loose on the wallpaper, no card
                    WidgetStyle.Envelope -> envelope(w, h)
                    WidgetStyle.Strip -> kraft(w, h, scale)
                }
                val wide = w / h >= WideRatio
                val refW = if (wide) WideRefW else SmallRef
                val u = min(w / refW, h / SmallRef).coerceAtMost(MaxLayoutScale)
                withTransform({
                    translate((w - refW * u) / 2f, (h - SmallRef * u) / 2f)
                    scale(u, u, pivot = Offset.Zero)
                }) {
                    when (style) {
                        WidgetStyle.Stamp -> if (wide) looseWide(snap) else looseSmall(snap)
                        WidgetStyle.Envelope -> if (wide) envelopeWide(snap) else envelopeSmall(snap)
                        WidgetStyle.Strip -> if (wide) stripWide(snap) else stripSmall(snap)
                    }
                }
                if (locked) {
                    drawContext.canvas.nativeCanvas.restore()
                    proTag(w)
                }
            }
        }
        return out
    }

    /** What the widget says to TalkBack (the picture has no text of its own). */
    fun describe(snap: WidgetSnapshot): String {
        val r = context.resources
        val parts = mutableListOf<String>()
        val stamp = snap.todayStamp
        parts += if (stamp == null) r.getString(R.string.widget_cd_waiting) else r.getString(R.string.widget_cd_stamped, whereWhen(stamp))
        if (snap.streak.current > 0) parts += r.getQuantityString(R.plurals.streak_days, snap.streak.current, snap.streak.current)
        if (!snap.isPro) parts += r.getString(R.string.widget_cd_locked)
        return parts.joinToString(". ")
    }

    // ---------- A · loose stamp on the wallpaper ----------

    private fun DrawScope.looseSmall(snap: WidgetSnapshot) {
        val today = snap.todayStamp
        if (today != null) {
            val drawn = stamp(today, Rect(30f, 4f, 126f, 128f), tilt = 4f)
            postmarkOver(today, drawn, d = 50f, waves = false)
        } else {
            slot(Rect(32f, 6f, 124f, 118f), red = false)
        }
        paperTag(tagText(snap), centreX = SmallRef / 2f, bottom = SmallRef - 2f)
    }

    private fun DrawScope.looseWide(snap: WidgetSnapshot) {
        // Earlier days on the left (nearest day closest to today), today on the right.
        val boxes = listOf(Rect(8f, 20f, 84f, 120f), Rect(104f, 14f, 180f, 114f))
        val tilts = listOf(-5f, 3f)
        snap.previous.reversed().forEachIndexed { i, s ->
            val slotIndex = boxes.lastIndex - i
            stamp(s, boxes[slotIndex], tilts[slotIndex])
        }
        val today = snap.todayStamp
        if (today != null) {
            val drawn = stamp(today, Rect(224f, 6f, 320f, 130f), tilt = -2f)
            postmarkOver(today, drawn, d = 52f, waves = true)
        } else {
            slot(Rect(228f, 12f, 320f, 124f), red = true)
        }
        // Waiting: the tag labels the empty slot; stamped: the streak sits under the whole row.
        if (today == null) paperTag(tagText(snap), centreX = 274f, bottom = SmallRef - 2f)
        else paperTag(tagText(snap), startX = 24f, bottom = SmallRef - 2f)
    }

    private fun tagText(snap: WidgetSnapshot): String {
        val r = context.resources
        return when {
            snap.todayStamp == null -> r.getString(R.string.widget_today_slot)
            snap.streak.current > 0 -> r.getQuantityString(R.plurals.streak_days_short, snap.streak.current, snap.streak.current)
            else -> r.getString(R.string.widget_stamped)
        }
    }

    // ---------- B · the Home airmail envelope ----------

    private fun DrawScope.envelopeSmall(snap: WidgetSnapshot) {
        val inner = Rect(18f, 18f, 138f, 138f)
        val today = snap.todayStamp
        if (today != null) {
            val drawn = stamp(today, Rect(47f, inner.top, 103f, 92f), tilt = 3f)
            postmarkOver(today, drawn, d = 40f, waves = false)
        } else {
            val title = measure(context.getString(R.string.widget_stamp_today), t.albumName, c.textHigh, maxWidth = inner.width)
            drawText(title, topLeft = Offset(inner.center.x - title.size.width / 2f, inner.top))
            cameraDot(Offset(inner.center.x, inner.top + 52f), 44f)
        }
        streakChip(snap, centreX = inner.center.x, bottom = inner.bottom)
    }

    private fun DrawScope.envelopeWide(snap: WidgetSnapshot) {
        val left = Rect(22f, 22f, 106f, 134f)
        val today = snap.todayStamp
        if (today != null) {
            val drawn = stamp(today, left.deflate(2f), tilt = -3f)
            postmarkOver(today, drawn, d = 46f, waves = false)
        } else {
            slot(Rect(24f, 26f, 104f, 130f), red = true)
        }
        // Right column, centred vertically: title, (where · when), streak + "Stamp it".
        val x = 122f
        val maxW = WideRefW - 22f - x
        val title = measure(
            context.getString(if (today != null) R.string.widget_stamped else R.string.widget_waiting),
            t.albumName, c.textHigh, maxWidth = maxW, maxLines = 2,
        )
        val note = today?.let { measure(whereWhen(it), t.noteSmall, c.handInkSoft, maxWidth = maxW) }
        val rowH = ChipHeight
        val gap = 8f
        val total = title.size.height + (note?.let { it.size.height + gap } ?: 0f) + gap + rowH
        var y = (SmallRef - total) / 2f
        drawText(title, topLeft = Offset(x, y))
        y += title.size.height + gap
        if (note != null) {
            drawText(note, topLeft = Offset(x, y))
            y += note.size.height + gap
        }
        val chipW = streakChip(snap, startX = x, bottom = y + rowH)
        if (today == null) redPill(context.getString(R.string.widget_stamp_it), startX = x + chipW + gap, centreY = y + rowH / 2f)
    }

    // ---------- C · a strip of the kraft album page ----------

    private fun DrawScope.stripSmall(snap: WidgetSnapshot) {
        streakCount(snap, Offset(12f, 10f), stacked = true)
        val today = snap.todayStamp
        if (today != null) {
            val drawn = stamp(today, Rect(76f, 30f, 146f, 122f), tilt = 5f)
            redRing(drawn.center, RingSize)
        } else {
            slot(Rect(78f, 34f, 146f, 122f), red = true)
        }
        handLabel(context.getString(R.string.widget_today), centreX = 112f, top = 124f)
    }

    private fun DrawScope.stripWide(snap: WidgetSnapshot) {
        streakCount(snap, Offset(WideRefW - 12f, 10f), stacked = false)
        val bottom = 122f
        var x = 14f
        val dayFormat = DateTimeFormatter.ofPattern(context.getString(R.string.pattern_short_date), locale)
        snap.previous.forEachIndexed { i, s ->
            val drawn = stamp(s, Rect(x, bottom - 78f, x + 58f, bottom), tilt = if (i % 2 == 0) -4f else 3f)
            handLabel(dayFormat.format(s.day).lowercase(locale), centreX = drawn.center.x, top = bottom + 2f)
            x += 58f + 14f
        }
        val today = snap.todayStamp
        val centre = if (today != null) {
            val drawn = stamp(today, Rect(x, bottom - 92f, x + 70f, bottom), tilt = -2f)
            redRing(drawn.center, RingSize)
            drawn.center.x
        } else {
            slot(Rect(x, bottom - 86f, x + 66f, bottom), red = true)
            x + 33f
        }
        handLabel(context.getString(R.string.widget_today), centreX = centre, top = bottom + 2f)
    }

    /** "12" in Fraunces with "days" (stacked) or "days in a row" beside it; "start today" at 0. */
    private fun DrawScope.streakCount(snap: WidgetSnapshot, anchor: Offset, stacked: Boolean) {
        val r = context.resources
        val n = snap.streak.current
        if (n == 0) {
            val note = measure(r.getString(R.string.widget_start), t.noteSmall, c.handInkSoft)
            val x = if (stacked) anchor.x else anchor.x - note.size.width
            drawText(note, topLeft = Offset(x, anchor.y))
            return
        }
        val number = measure(n.toString(), t.cardTitle, c.textHigh)
        val words = measure(
            r.getQuantityString(if (stacked) R.plurals.widget_days else R.plurals.widget_days_in_row, n),
            t.noteSmall, c.handInkSoft,
        )
        if (stacked) {
            drawText(number, topLeft = anchor)
            drawText(words, topLeft = Offset(anchor.x, anchor.y + number.size.height - 2f))
        } else {
            val gap = 4f
            val wordsX = anchor.x - words.size.width
            val numberX = wordsX - gap - number.size.width
            val baseline = anchor.y + number.firstBaseline
            drawText(number, topLeft = Offset(numberX, anchor.y))
            drawText(words, topLeft = Offset(wordsX, baseline - words.firstBaseline))
        }
    }

    // ---------- shared pieces ----------

    /** A stamp picture fitted into [box] (kept whole, centred), tilted; returns where it landed (untilted). */
    private fun DrawScope.stamp(s: WidgetStamp, box: Rect, tilt: Float): Rect {
        val img = s.image
        val aspect = if (img != null) img.width.toFloat() / img.height else MissingAspect
        val dw = min(box.width, box.height * aspect)
        val dh = dw / aspect
        val rect = Rect(Offset(box.center.x - dw / 2f, box.center.y - dh / 2f), Size(dw, dh))
        rotate(tilt, pivot = rect.center) {
            val native = drawContext.canvas.nativeCanvas
            val dst = RectF(rect.left, rect.top, rect.right, rect.bottom)
            run {
                // A soft blurred card under the stamp (blurring the perforated outline itself streaks).
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = c.shadowWarm.copy(alpha = ShadowAlpha).toArgb()
                    maskFilter = BlurMaskFilter(ShadowBlur, BlurMaskFilter.Blur.NORMAL)
                }
                val inset = ShadowInset
                native.drawRect(dst.left + inset, dst.top + inset + ShadowDy, dst.right - inset, dst.bottom - inset + ShadowDy, paint)
            }
            if (img != null) {
                native.drawBitmap(img, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            } else {
                // Picture file gone: plain stamp paper, like the faded mark in the app.
                drawRoundRect(c.paperWhite, rect.topLeft, rect.size, CornerRadius(PlateCorner))
                drawRoundRect(c.outline, rect.topLeft, rect.size, CornerRadius(PlateCorner), style = Stroke(1f))
            }
        }
        return rect
    }

    /** The stamp's own postmark over its bottom-end corner, as on Stamped!. */
    private fun DrawScope.postmarkOver(s: WidgetStamp, stamp: Rect, d: Float, waves: Boolean) {
        val lines = postmarkLines(s.place, s.day, context.resources, locale)
        val width = if (waves) postmarkWidth(d) else d
        val left = stamp.right + d * PostmarkOutX - width
        val top = stamp.bottom + d * PostmarkOutY - d
        translate(left, top) {
            drawPostmark(measurer, lines.top, lines.bottom, t.overline, c.postmarkOnLight, d, PostmarkTilt, waves, dashedOuter = false)
        }
    }

    /** Today's empty slot: pale paper, pencilled dashes (red = today), red camera button. */
    private fun DrawScope.slot(rect: Rect, red: Boolean) {
        drawRoundRect(c.paperWhite.copy(alpha = SlotFillAlpha), rect.topLeft, rect.size, CornerRadius(TileCorner))
        val inset = SlotStroke / 2f
        drawRoundRect(
            if (red) c.airmailRed else c.ink.copy(alpha = SlotInkAlpha),
            Offset(rect.left + inset, rect.top + inset),
            Size(rect.width - inset * 2, rect.height - inset * 2),
            CornerRadius(TileCorner),
            style = Stroke(SlotStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(SlotDash, SlotDash))),
        )
        cameraDot(rect.center, min(CameraDot, rect.width * 0.45f))
    }

    private fun DrawScope.cameraDot(centre: Offset, size: Float) {
        drawCircle(c.airmailRed, size / 2f, centre)
        icon(R.drawable.ic_camera, centre, size * 0.5f, c.paperWhite)
    }

    private fun DrawScope.icon(@DrawableRes id: Int, centre: Offset, size: Float, tint: Color) {
        val drawable = ContextCompat.getDrawable(context, id)?.mutate() ?: return
        drawable.setTint(tint.toArgb())
        drawable.setBounds(0, 0, IconUnits, IconUnits)
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.translate(centre.x - size / 2f, centre.y - size / 2f)
        native.scale(size / IconUnits, size / IconUnits)
        drawable.draw(native)
        native.restore()
    }

    /** A little paper label in handwriting (Caveat), slightly crooked. */
    private fun DrawScope.paperTag(text: String, bottom: Float, centreX: Float? = null, startX: Float? = null) {
        val layout = measure(text, t.noteSmall, c.handInk, maxWidth = SmallRef - 16f)
        val w = layout.size.width + TagPadX * 2
        val h = layout.size.height + TagPadY * 2
        val left = startX ?: ((centreX ?: 0f) - w / 2f)
        val rect = Rect(left, bottom - h, left + w, bottom)
        rotate(TagTilt, pivot = rect.center) {
            drawRoundRect(c.shadowWarm.copy(alpha = TagShadowAlpha), rect.topLeft + Offset(0f, 1.5f), rect.size, CornerRadius(h / 2f))
            drawRoundRect(c.paperWhite, rect.topLeft, rect.size, CornerRadius(h / 2f))
            drawText(layout, topLeft = Offset(rect.left + TagPadX, rect.top + TagPadY))
        }
    }

    /** Compact Ink streak pill ("🔥 12 days", or "start today"); returns its width. */
    private fun DrawScope.streakChip(snap: WidgetSnapshot, bottom: Float, centreX: Float? = null, startX: Float? = null): Float {
        val r = context.resources
        val n = snap.streak.current
        val text = if (n > 0) r.getQuantityString(R.plurals.streak_days_short, n, n) else r.getString(R.string.widget_start)
        val layout = measure(text, t.chip.copy(fontWeight = FontWeight.Bold), c.textOnDarkHigh)
        val w = ChipPadX + FlameSize + ChipGap + layout.size.width + ChipPadX
        val left = startX ?: ((centreX ?: 0f) - w / 2f)
        val top = bottom - ChipHeight
        drawRoundRect(c.ink, Offset(left, top), Size(w, ChipHeight), CornerRadius(ChipHeight / 2f))
        icon(R.drawable.ic_flame, Offset(left + ChipPadX + FlameSize / 2f, top + ChipHeight / 2f), FlameSize, c.ember)
        drawText(layout, topLeft = Offset(left + ChipPadX + FlameSize + ChipGap, top + (ChipHeight - layout.size.height) / 2f))
        return w
    }

    private fun DrawScope.redPill(text: String, startX: Float, centreY: Float) {
        val layout = measure(text, t.button, c.textOnDarkHigh)
        val w = layout.size.width + PillPadX * 2
        val h = ChipHeight
        drawRoundRect(c.airmailRed, Offset(startX, centreY - h / 2f), Size(w, h), CornerRadius(h / 2f))
        drawText(layout, topLeft = Offset(startX + PillPadX, centreY - layout.size.height / 2f))
    }

    /** Calendar's "today" mark: a red dashed postmark ring around today's stamp. */
    private fun DrawScope.redRing(centre: Offset, size: Float) {
        drawCircle(
            c.postmarkOnLight, size / 2f, centre,
            style = Stroke(RingStroke * 1.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(RingDash, RingGap))),
        )
        drawCircle(c.postmarkOnLight.copy(alpha = RingInnerAlpha), size / 2f * RingInner, centre, style = Stroke(RingStroke))
    }

    private fun DrawScope.handLabel(text: String, centreX: Float, top: Float) {
        val layout = measure(text, t.noteSmall, c.handInkSoft)
        drawText(layout, topLeft = Offset(centreX - layout.size.width / 2f, top))
    }

    /** Envelope: airmail stripes all round a PaperWhite card. Drawn in widget dp, grain-free. */
    private fun DrawScope.envelope(w: Float, h: Float) {
        val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(CardCorner))) }
        clipPath(outer) {
            drawRect(c.paperWhite)
            val stripes = listOf(Path(), Path())
            var x = -h - (h % (StripeWidth * 4))
            var i = 0
            while (x < w + StripeWidth) {
                val target = when (i % 4) {
                    0 -> stripes[0]
                    2 -> stripes[1]
                    else -> null
                }
                target?.apply {
                    moveTo(x + h, 0f)
                    lineTo(x + h + StripeWidth, 0f)
                    lineTo(x + StripeWidth, h)
                    lineTo(x, h)
                    close()
                }
                x += StripeWidth
                i++
            }
            drawPath(stripes[0], c.airmailRed)
            drawPath(stripes[1], c.cobalt)
            drawRoundRect(
                c.paperWhite,
                Offset(EnvelopeBorder, EnvelopeBorder),
                Size(w - EnvelopeBorder * 2, h - EnvelopeBorder * 2),
                CornerRadius(TileCorner),
            )
        }
    }

    /** Kraft album paper with the grain tile at its real pixel size (×1/scale in dp). */
    private fun DrawScope.kraft(w: Float, h: Float, scale: Float) {
        val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(CardCorner))) }
        clipPath(outer) {
            drawRect(c.kraft)
            val tile = grain ?: return@clipPath
            withTransform({ scale(1f / scale, 1f / scale, pivot = Offset.Zero) }) {
                drawRect(
                    ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated)),
                    size = Size(w * scale, h * scale),
                    alpha = GrainAlpha,
                    blendMode = BlendMode.Multiply,
                )
            }
        }
    }

    /** Red "Pro" pill in the top-end corner of a locked (free) widget. */
    private fun DrawScope.proTag(w: Float) {
        val layout = measure(context.getString(R.string.widget_pro).uppercase(locale), t.overline, c.textOnDarkHigh)
        val pw = layout.size.width + ProPadX * 2
        val ph = layout.size.height + ProPadY * 2
        val left = w - ProInset - pw
        drawRoundRect(c.airmailRed, Offset(left, ProInset), Size(pw, ph), CornerRadius(ph / 2f))
        drawText(layout, topLeft = Offset(left + ProPadX, ProInset + ProPadY))
    }

    private fun whereWhen(s: WidgetStamp): String {
        val date = DateTimeFormatter.ofPattern(context.getString(R.string.pattern_short_date), locale).format(s.day)
        val name = s.place.city ?: s.place.country
        return if (name != null) context.getString(R.string.camera_header_place, name, date) else date
    }

    private fun measure(text: String, style: TextStyle, color: Color, maxWidth: Float = Float.MAX_VALUE, maxLines: Int = 1): TextLayoutResult =
        measurer.measure(
            AnnotatedString(text),
            style.copy(color = color),
            overflow = TextOverflow.Ellipsis,
            maxLines = maxLines,
            constraints = if (maxWidth == Float.MAX_VALUE) Constraints() else Constraints(maxWidth = maxWidth.toInt()),
        )

    private companion object {
        const val BytesPerPixel = 4f
        const val MinSideDp = 40f
        const val LockedAlpha = 0.55f

        // Reference frames (dp): a 2×2 cell and a 4×2 strip, as in the reviewed concepts.
        const val SmallRef = 156f
        const val WideRefW = 336f
        const val WideRatio = 1.6f
        const val MaxLayoutScale = 1.5f

        // Tokens in dp (match Shape.kt / components).
        const val CardCorner = 16f
        const val TileCorner = 12f
        const val PlateCorner = 4f
        const val StripeWidth = 8f
        const val EnvelopeBorder = 8f
        const val GrainAlpha = 0.3f

        const val ShadowAlpha = 0.32f
        const val ShadowBlur = 4f
        const val ShadowInset = 2f
        const val ShadowDy = 2.5f
        const val MissingAspect = 0.78f

        const val PostmarkTilt = -12f
        const val PostmarkOutX = 0.28f
        const val PostmarkOutY = 0.24f

        const val SlotFillAlpha = 0.55f
        const val SlotInkAlpha = 0.5f
        const val SlotStroke = 1.5f
        const val SlotDash = 6f
        const val CameraDot = 36f
        const val IconUnits = 96

        const val TagPadX = 10f
        const val TagPadY = 1f
        const val TagTilt = -2f
        const val TagShadowAlpha = 0.18f

        const val ChipHeight = 30f
        const val ChipPadX = 12f
        const val ChipGap = 4f
        const val FlameSize = 16f
        const val PillPadX = 14f

        const val RingSize = 78f
        const val RingStroke = 1.5f
        const val RingDash = 5f
        const val RingGap = 3.5f
        const val RingInner = 0.82f
        const val RingInnerAlpha = 0.6f

        const val ProInset = 8f
        const val ProPadX = 8f
        const val ProPadY = 3f
    }
}
