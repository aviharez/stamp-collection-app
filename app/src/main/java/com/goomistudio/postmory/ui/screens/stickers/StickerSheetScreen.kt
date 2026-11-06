package com.goomistudio.postmory.ui.screens.stickers

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goomistudio.postmory.R
import com.goomistudio.postmory.domain.model.ScrapSticker
import com.goomistudio.postmory.domain.model.StampShape
import com.goomistudio.postmory.ui.components.CircleIconButton
import com.goomistudio.postmory.ui.components.HandNote
import com.goomistudio.postmory.ui.components.ImageLoads
import com.goomistudio.postmory.ui.components.InkStrength
import com.goomistudio.postmory.ui.components.LocalImageLoads
import com.goomistudio.postmory.ui.components.PaperChip
import com.goomistudio.postmory.ui.components.PillButton
import com.goomistudio.postmory.ui.components.PillStyle
import com.goomistudio.postmory.ui.components.Postmark
import com.goomistudio.postmory.ui.components.RenderedStamp
import com.goomistudio.postmory.ui.components.ScrapStickerArt
import com.goomistudio.postmory.ui.components.SwitchRow
import com.goomistudio.postmory.ui.components.Tilt
import com.goomistudio.postmory.ui.components.description
import com.goomistudio.postmory.ui.components.previewStamps
import com.goomistudio.postmory.ui.components.rememberPostmarkLines
import com.goomistudio.postmory.ui.components.seed
import com.goomistudio.postmory.ui.screens.export.OffscreenCapture
import com.goomistudio.postmory.ui.screens.export.ScaledArt
import com.goomistudio.postmory.ui.theme.Elevation
import com.goomistudio.postmory.ui.theme.PaperPreview
import com.goomistudio.postmory.ui.theme.StampAppTheme
import com.goomistudio.postmory.ui.theme.StampTheme
import com.goomistudio.postmory.ui.theme.tintedShadow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.min

/** "Print stickers": the album's stamps laid out on printer pages, printed or saved as a PDF. */
@Composable
fun StickerSheetScreen(
    onBack: () -> Unit,
    onGetPro: () -> Unit,
    viewModel: StickerSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val loads = remember { ImageLoads() }
    /** The page being drawn for the PDF, or -1. */
    var capturing by remember { mutableIntStateOf(-1) }
    val jobName = stringResource(R.string.stickers_job, state.albumName)

    fun print() {
        if (!state.isPro) return onGetPro()
        val pages = state.pages
        val choice = state.choice
        if (pages.isEmpty() || state.progress != null || activity == null) return
        scope.launch {
            try {
                viewModel.startPdf(pages.size)
                for ((i, page) in pages.withIndex()) {
                    capturing = i
                    repeat(2) { withFrameNanos { } } // let the page compose
                    val paths = page.pieces.mapNotNull { (it as? SheetPiece.StampPiece)?.stamp?.imagePath }.toSet()
                    withTimeoutOrNull(ImageWaitMs) { loads.done.first { it.containsAll(paths) } }
                    repeat(2) { withFrameNanos { } }
                    val drawn = layer.toImageBitmap().asAndroidBitmap()
                    viewModel.addPage(drawn.copy(Bitmap.Config.ARGB_8888, false), pages.size)
                }
                capturing = -1
                val (file, count) = viewModel.finishPdf() ?: return@launch
                printPdf(activity, file, jobName, count, choice.paper)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                capturing = -1
                viewModel.failed()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        val page = state.pages.getOrNull(capturing)
        if (page != null) {
            val paper = state.choice.paper
            // The full-size page, laid out and recorded off screen (never drawn here).
            OffscreenCapture(layer, StickerPdf.widthPx(paper), StickerPdf.heightPx(paper), paper.designWidth) {
                CompositionLocalProvider(LocalImageLoads provides loads) {
                    StickerSheetPage(page, paper, state.albumName, state.choice.postmarks, capturing + 1, state.pages.size)
                }
            }
        }
        StickerSheetContent(
            state = state,
            onBack = onBack,
            onLook = viewModel::setLook,
            onSize = viewModel::setSize,
            onPaper = viewModel::setPaper,
            onPostmarks = viewModel::setPostmarks,
            onPrint = ::print,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StickerSheetContent(
    state: StickerSheetUiState,
    onBack: () -> Unit,
    onLook: (SheetLook) -> Unit,
    onSize: (StickerSize) -> Unit,
    onPaper: (SheetPaper) -> Unit,
    onPostmarks: (Boolean) -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val t = StampTheme.type
    val choice = state.choice
    val busy = state.progress != null

    Column(
        modifier
            .fillMaxSize()
            .background(c.paper)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = s.screenFullBleed, vertical = s.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            CircleIconButton(R.drawable.ic_back, stringResource(R.string.cd_back), onBack)
            Text(
                stringResource(R.string.stickers_overline, state.albumName).uppercase(),
                style = t.overline,
                color = c.textMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }

        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageWidth = maxWidth - s.screen * 2
            LazyColumn(
                contentPadding = PaddingValues(start = s.screen, end = s.screen, bottom = s.l),
                verticalArrangement = Arrangement.spacedBy(s.m),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "head", contentType = "head") {
                    Column(verticalArrangement = Arrangement.spacedBy(s.xxs)) {
                        Text(stringResource(R.string.stickers_title), style = t.screenTitle, color = c.textHigh)
                        HandNote(
                            if (state.stampCount == 0 && state.loaded) {
                                stringResource(R.string.stickers_empty)
                            } else {
                                stringResource(
                                    R.string.stickers_counts,
                                    pluralStringResource(R.plurals.album_stamp_count, state.stampCount, state.stampCount),
                                    pluralStringResource(R.plurals.stickers_page_count, state.pages.size, state.pages.size),
                                )
                            },
                            strength = InkStrength.Soft,
                        )
                    }
                }
                item(key = "controls", contentType = "controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(s.s)) {
                        ChoiceRow(stringResource(R.string.stickers_look)) {
                            SheetLook.entries.forEach { look ->
                                PaperChip(stringResource(look.label), selected = choice.look == look, onClick = { onLook(look) })
                            }
                        }
                        if (choice.look == SheetLook.Grid) {
                            ChoiceRow(stringResource(R.string.stickers_size)) {
                                StickerSize.entries.forEach { size ->
                                    PaperChip(stringResource(size.label), selected = choice.size == size, onClick = { onSize(size) })
                                }
                            }
                        }
                        ChoiceRow(stringResource(R.string.stickers_paper)) {
                            SheetPaper.entries.forEach { paper ->
                                PaperChip(stringResource(paper.label), selected = choice.paper == paper, onClick = { onPaper(paper) })
                            }
                        }
                        SwitchRow(stringResource(R.string.stickers_postmarks), checked = choice.postmarks, onCheckedChange = onPostmarks)
                    }
                }
                itemsIndexed(state.pages, key = { i, _ -> "page$i" }, contentType = { _, _ -> "page" }) { i, page ->
                    val description = stringResource(R.string.stickers_page_cd, i + 1, state.pages.size, state.albumName)
                    Column(verticalArrangement = Arrangement.spacedBy(s.xs)) {
                        Text(
                            stringResource(R.string.stickers_page_of, i + 1, state.pages.size).uppercase(),
                            style = t.overline,
                            color = c.textLow,
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                        ScaledArt(
                            width = pageWidth,
                            aspect = choice.paper.widthMm / choice.paper.heightMm,
                            designWidth = choice.paper.designWidth,
                            modifier = Modifier
                                .tintedShadow(Elevation.Card, RoundedCornerShape(PreviewCorner), c.shadowWarm)
                                .clearAndSetSemantics { contentDescription = description },
                        ) {
                            StickerSheetPage(page, choice.paper, state.albumName, choice.postmarks, i + 1, state.pages.size)
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = s.screen, vertical = s.s),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(s.xs),
        ) {
            when {
                state.failed -> Text(stringResource(R.string.stickers_failed), style = t.label, color = c.airmailRed, textAlign = TextAlign.Center)
                !state.isPro && state.loaded -> Text(stringResource(R.string.stickers_pro_note), style = t.label, color = c.textMedium, textAlign = TextAlign.Center)
            }
            val progress = state.progress
            PillButton(
                when {
                    progress != null -> stringResource(R.string.stickers_preparing, progress.page, progress.pages)
                    state.isPro -> stringResource(R.string.stickers_print)
                    else -> stringResource(R.string.stickers_print_pro)
                },
                onClick = onPrint,
                leadingIcon = R.drawable.ic_print,
                style = PillStyle.Red,
                enabled = !busy && state.pages.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(label: String, chips: @Composable () -> Unit) {
    val s = StampTheme.spacing
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(s.xs),
        verticalArrangement = Arrangement.spacedBy(s.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = StampTheme.type.label, color = StampTheme.colors.textMedium, modifier = Modifier.padding(end = s.xxs))
        chips()
    }
}

/**
 * One printer page at its design size ([SheetPaper.designWidth], 1 mm = [DpPerMm] dp): white paper,
 * the album name and "cut along the dashed lines" on top, then every piece with its cut line.
 */
@Composable
internal fun StickerSheetPage(page: SheetPage, paper: SheetPaper, albumName: String, postmarks: Boolean, number: Int, pages: Int) {
    val c = StampTheme.colors
    val t = StampTheme.type
    Box(
        Modifier
            .size(paper.widthMm.mm, paper.heightMm.mm)
            .background(c.paperWhite),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = StickerSheets.SideMm.mm)
                .offset(y = HeaderTopMm.mm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(albumName, style = t.albumName, color = c.textHigh, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            HandNote(stringResource(R.string.stickers_cut_hint), style = t.noteSmall, strength = InkStrength.Soft, rotation = 0f)
        }
        page.pieces.forEach { piece -> SheetPieceArt(piece, postmarks) }
        Text(
            stringResource(R.string.stickers_page_number, number, pages),
            style = t.label,
            color = c.textLow,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FooterBottomMm.mm),
        )
    }
}

/** A piece at its place on the page, tilted, inside its dashed cut line. */
@Composable
private fun SheetPieceArt(piece: SheetPiece, postmarks: Boolean) {
    val c = StampTheme.colors
    val cut = StickerSheets.CutMm
    val round = piece is SheetPiece.StampPiece && piece.stamp.shape == StampShape.Circle
    val ink = c.textLow
    Box(
        Modifier
            .offset((piece.x - cut).mm, (piece.y - cut).mm)
            .size((piece.w + cut * 2).mm, (piece.h + cut * 2).mm)
            .rotate(piece.tilt)
            .drawBehind {
                val stroke = Stroke(
                    width = CutStrokeMm * DpPerMm.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(CutDashMm * DpPerMm.dp.toPx(), CutGapMm * DpPerMm.dp.toPx())),
                )
                val inset = stroke.width / 2
                if (round) {
                    drawCircle(ink, radius = size.minDimension / 2 - inset, style = stroke)
                } else {
                    val r = CutCornerMm * DpPerMm.dp.toPx()
                    drawRoundRect(ink, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), CornerRadius(r), style = stroke)
                }
            }
            .padding(cut.mm),
        contentAlignment = Alignment.Center,
    ) {
        when (piece) {
            is SheetPiece.StampPiece -> {
                val stamp = piece.stamp
                Box(Modifier.size(piece.w.mm, piece.h.mm)) {
                    RenderedStamp(
                        File(stamp.imagePath),
                        stamp.shape,
                        contentDescription = stamp.description(),
                        elevation = 0.dp,
                        perforation = stamp.perforation,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (postmarks) {
                        val lines = rememberPostmarkLines(stamp.place, stamp.day)
                        val diameter = (min(piece.w, piece.h) * PostmarkShare).mm
                        Postmark(
                            lines.top,
                            lines.bottom,
                            diameter = diameter,
                            showWaves = false,
                            rotation = Tilt.postmark(stamp.seed),
                            modifier = Modifier
                                .align(if (stamp.shape == StampShape.Circle) Alignment.BottomCenter else Alignment.BottomEnd)
                                .padding(PostmarkInsetMm.mm),
                        )
                    }
                }
            }
            is SheetPiece.StickerPiece -> {
                val anchor = piece.stamp
                val text = if (piece.sticker == ScrapSticker.Postmark && anchor != null) {
                    val lines = rememberPostmarkLines(anchor.place, anchor.day)
                    "${lines.top}\n${lines.bottom}"
                } else {
                    ""
                }
                FitInside(Modifier.size(piece.w.mm, piece.h.mm)) { ScrapStickerArt(piece.sticker, text) }
            }
        }
    }
}

/** Draws [content] at its natural size, scaled down or up to just fit the box (stickers on the sheet). */
@Composable
private fun FitInside(modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.semantics { }) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val scale = min(w / placeable.width.coerceAtLeast(1).toFloat(), h / placeable.height.coerceAtLeast(1).toFloat())
        layout(w, h) {
            placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

/** The sheet's design units: 1 mm of paper = 4 dp, so text sizes read like real print. */
private const val DpPerMm = 4f
private val Float.mm: Dp get() = (this * DpPerMm).dp
internal val SheetPaper.designWidth: Dp get() = widthMm.mm

private const val HeaderTopMm = 9f
private const val FooterBottomMm = 4f
private const val CutStrokeMm = 0.3f
private const val CutDashMm = 1.6f
private const val CutGapMm = 1.1f
private const val CutCornerMm = 1.5f
/** Postmark diameter as a share of the stamp's short side. */
private const val PostmarkShare = 0.5f
private const val PostmarkInsetMm = 1.5f
private const val ImageWaitMs = 8_000L
private val PreviewCorner = 2.dp

private val SheetLook.label: Int
    get() = when (this) {
        SheetLook.Grid -> R.string.stickers_look_grid
        SheetLook.Mix -> R.string.stickers_look_mix
    }

private val StickerSize.label: Int
    get() = when (this) {
        StickerSize.Small -> R.string.stickers_size_small
        StickerSize.Medium -> R.string.stickers_size_medium
        StickerSize.Large -> R.string.stickers_size_large
    }

private val SheetPaper.label: Int
    get() = when (this) {
        SheetPaper.A4 -> R.string.stickers_paper_a4
        SheetPaper.Letter -> R.string.stickers_paper_letter
    }

@PaperPreview
@Composable
private fun StickerSheetGridPreview() {
    StampAppTheme {
        StickerSheetContent(previewSheetState(previewStamps(14)), {}, {}, {}, {}, {}, {})
    }
}

@PaperPreview
@Composable
private fun StickerSheetMixPreview() {
    StampAppTheme {
        StickerSheetContent(
            previewSheetState(previewStamps(14), StickerChoice(look = SheetLook.Mix)).copy(isPro = true),
            {}, {}, {}, {}, {}, {},
        )
    }
}
