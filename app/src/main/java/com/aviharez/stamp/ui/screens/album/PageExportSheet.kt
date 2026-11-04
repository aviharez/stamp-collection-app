package com.aviharez.stamp.ui.screens.album

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.domain.model.Stamp
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.ImageLoads
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.LocalImageLoads
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.previewStamps
import com.aviharez.stamp.ui.screens.export.SaveShareRow
import com.aviharez.stamp.ui.screens.export.WatermarkRow
import com.aviharez.stamp.ui.screens.export.rememberSaveToPhotos
import com.aviharez.stamp.ui.screens.export.sharePng
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.tintedShadow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What one exported page shows. */
data class PageExportContent(
    val albumName: String,
    val page: Int,
    val pageCount: Int,
    val stamps: List<Stamp>,
    val stampCount: Int,
    val dayCount: Int,
)

/**
 * "Share this page": the kraft page as a PNG (1440 × 1920), the same layout as on screen without
 * the "next stamp…" slot, with the album name on top. Free tier: a small app-name mark at the foot
 * (Pro or a short ad removes it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageExportSheet(
    content: PageExportContent,
    onDismiss: () -> Unit,
    onGetPro: () -> Unit,
    viewModel: PageExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    val loads = remember(content.stamps) { ImageLoads() }
    val fileName = remember(content) { pageFileName(content) }

    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(state.watermark) { if (state.watermark) viewModel.preloadAd() }
    LaunchedEffect(viewModel) { viewModel.share.collect { context.sharePng(it) } }

    /** Waits for every picture, then for the redraw that includes it, then copies the capture. */
    fun capture(then: (Bitmap) -> Unit) {
        scope.launch {
            viewModel.setBusy(true)
            val paths = content.stamps.mapTo(HashSet()) { it.imagePath }
            withTimeoutOrNull(ImageWaitMs) { loads.done.first { it.containsAll(paths) } }
            repeat(2) { withFrameNanos { } }
            val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            viewModel.setBusy(false)
            if (bitmap != null) then(bitmap) else viewModel.failed()
        }
    }
    val save = rememberSaveToPhotos { capture { viewModel.save(it, fileName) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StampTheme.colors.paper,
        contentColor = StampTheme.colors.textHigh,
    ) {
        // The full-size picture, laid out and recorded off screen (never drawn here).
        OffscreenCapture(layer) {
            CompositionLocalProvider(LocalImageLoads provides loads) {
                PageExportCard(content, watermark = state.watermark)
            }
        }
        PageExportSheetContent(
            content = content,
            state = state,
            onSave = save,
            onShare = { capture { viewModel.share(it, fileName) } },
            onGetPro = onGetPro,
            onWatchAd = { activity?.let(viewModel::watchAd) },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
fun PageExportSheetContent(
    content: PageExportContent,
    state: PageExportUiState,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onGetPro: () -> Unit,
    onWatchAd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    Column(
        modifier.fillMaxWidth().padding(horizontal = s.screen).padding(bottom = s.l),
        verticalArrangement = Arrangement.spacedBy(s.m),
    ) {
        Text(stringResource(R.string.page_export_title), style = StampTheme.type.cardTitle)
        PagePreview(content, state.watermark)
        when {
            state.isPro -> HandNote(stringResource(R.string.export_pro_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            state.adUnlocked -> HandNote(stringResource(R.string.page_export_unlocked_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            else -> WatermarkRow(
                state.ad,
                onGetPro,
                onWatchAd,
                note = stringResource(R.string.page_export_watermark_note, stringResource(R.string.app_name)),
            )
        }
        if (state.failed) Text(stringResource(R.string.export_failed), style = StampTheme.type.label, color = c.airmailRed)
        SaveShareRow(state.save, state.sharing, onSave, onShare, Modifier.padding(top = s.xs))
    }
}

/** The page at preview size: the same card, laid out at the export's design width and scaled down. */
@Composable
private fun PagePreview(content: PageExportContent, watermark: Boolean) {
    val description = stringResource(R.string.export_preview_cd)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(PreviewHeight).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val w = min(maxWidth, maxHeight * ExportAspect)
        val density = LocalDensity.current
        val shape = StampTheme.shapes.plate
        // A lifted print, so the page's own paper reads apart from the sheet's.
        Box(
            Modifier
                .size(w, w / ExportAspect)
                .tintedShadow(Elevation.Card, shape, StampTheme.colors.shadowWarm)
                .clip(shape)
                .clearAndSetSemantics { },
        ) {
            CompositionLocalProvider(LocalDensity provides Density(density.density * w.value / DesignWidth.value, fontScale = 1f)) {
                PageExportCard(content, watermark)
            }
        }
    }
}

/**
 * Lays [content] out at the export size (ExportWidthPx × ExportHeightPx, fixed density and font
 * scale so it never depends on the phone) and records it into [layer] without drawing it.
 */
@Composable
private fun OffscreenCapture(layer: GraphicsLayer, content: @Composable () -> Unit) {
    Box(
        Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(ExportWidthPx, ExportHeightPx))
                layout(0, 0) { placeable.place(0, 0) }
            }
            .drawWithContent { layer.record { this@drawWithContent.drawContent() } }
            .clearAndSetSemantics { },
    ) {
        CompositionLocalProvider(LocalDensity provides Density(ExportWidthPx / DesignWidth.value, fontScale = 1f)) {
            content()
        }
    }
}

/** The exported picture: paper, album name + counts, the kraft page, and the free-tier mark. */
@Composable
fun PageExportCard(content: PageExportContent, watermark: Boolean, modifier: Modifier = Modifier) {
    val s = StampTheme.spacing
    val c = StampTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier.fillMaxSize().background(c.paper).padding(s.m)) {
        Text(
            stringResource(R.string.album_page_of, content.page, content.pageCount).uppercase(locale),
            style = StampTheme.type.overline,
            color = c.textMedium,
        )
        Row(Modifier.padding(top = s.xxs), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(s.s)) {
            Text(
                content.albumName,
                style = StampTheme.type.screenTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            HandNote(
                stringResource(
                    R.string.album_stats,
                    pluralStringResource(R.plurals.album_stamp_count, content.stampCount, content.stampCount),
                    pluralStringResource(R.plurals.album_day_count, content.dayCount, content.dayCount),
                ),
                style = StampTheme.type.noteLarge,
                rotation = Tilt.note(content.page.toLong()),
                maxLines = 1,
                modifier = Modifier.padding(bottom = s.xxs),
            )
        }
        AlbumSheet(
            stamps = content.stamps,
            hasNextSlot = false,
            onNextStamp = {},
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = s.s),
        )
        Box(Modifier.fillMaxWidth().height(s.l), contentAlignment = Alignment.BottomEnd) {
            if (watermark) {
                Text(stringResource(R.string.app_name).uppercase(locale), style = StampTheme.type.overline, color = c.textMedium)
            }
        }
    }
}

private fun pageFileName(content: PageExportContent): String {
    val slug = content.albumName.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), "-").trim('-').take(SlugChars).ifEmpty { "album" }
    return "$slug-page-${content.page}.png"
}

/** Phone-independent layout width of the exported page; ExportWidthPx / this = its density. */
private val DesignWidth = 400.dp
private const val ExportWidthPx = 1440
private const val ExportHeightPx = 1920
private const val ExportAspect = ExportWidthPx.toFloat() / ExportHeightPx
private val PreviewHeight = 360.dp
private const val ImageWaitMs = 5_000L
private const val SlugChars = 40

@PaperPreview
@Composable
private fun PageExportSheetPreview() {
    val stamps = previewStamps(7)
    StampAppTheme {
        PageExportSheetContent(
            content = PageExportContent("Japan trip", 2, 2, stamps, 16, 9),
            state = PageExportUiState(),
            onSave = {}, onShare = {}, onGetPro = {}, onWatchAd = {},
        )
    }
}
