package com.aviharez.stamp.ui.screens.map

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aviharez.stamp.R
import com.aviharez.stamp.data.analytics.ExportKind
import com.aviharez.stamp.ui.components.AirmailStripe
import com.aviharez.stamp.ui.components.FitText
import com.aviharez.stamp.ui.components.GrainedSurface
import com.aviharez.stamp.ui.components.HandNote
import com.aviharez.stamp.ui.components.ImageLoads
import com.aviharez.stamp.ui.components.InkStrength
import com.aviharez.stamp.ui.components.LocalImageLoads
import com.aviharez.stamp.ui.components.PaperChip
import com.aviharez.stamp.ui.components.Tilt
import com.aviharez.stamp.ui.components.VisaStamp
import com.aviharez.stamp.ui.components.rememberDateText
import com.aviharez.stamp.ui.screens.album.PageExportUiState
import com.aviharez.stamp.ui.screens.album.PageExportViewModel
import com.aviharez.stamp.ui.screens.export.OffscreenCapture
import com.aviharez.stamp.ui.screens.export.SaveShareRow
import com.aviharez.stamp.ui.screens.export.ScaledArt
import com.aviharez.stamp.ui.screens.export.WatermarkRow
import com.aviharez.stamp.ui.screens.export.rememberSaveToPhotos
import com.aviharez.stamp.ui.screens.export.sharePng
import com.aviharez.stamp.ui.theme.Elevation
import com.aviharez.stamp.ui.theme.PaperPreview
import com.aviharez.stamp.ui.theme.StampAppTheme
import com.aviharez.stamp.ui.theme.StampTheme
import com.aviharez.stamp.ui.theme.tintedShadow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** The two pictures "Share your world" can make. */
enum class MapLook { World, Passport }

/**
 * Share the Map tab: a 1440 × 1920 picture, either the World postcard (the kraft map with its pins,
 * three big numbers, the countries in handwriting) or a Passport page (a visa per country).
 * Free for everyone; the free tier gets a small "made with" mark (Pro or a short ad removes it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapExportSheet(
    state: MapUiState,
    onDismiss: () -> Unit,
    onGetPro: () -> Unit,
    viewModel: PageExportViewModel = hiltViewModel(),
) {
    val export by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var look by rememberSaveable { mutableStateOf(MapLook.World) }
    val loads = remember(state) { ImageLoads() }
    val fileName = if (look == MapLook.World) WorldFile else PassportFile

    LaunchedEffect(Unit) { viewModel.reset() }
    LaunchedEffect(export.watermark) { if (export.watermark) viewModel.preloadAd() }
    LaunchedEffect(viewModel) { viewModel.share.collect { context.sharePng(it) } }

    /** Waits for the pinned stamps (World only), then for the redraw that shows them, then copies the capture. */
    fun capture(then: (Bitmap) -> Unit) {
        scope.launch {
            viewModel.setBusy(true)
            val paths = if (look == MapLook.World) state.pinImagePaths else emptySet()
            withTimeoutOrNull(ImageWaitMs) { loads.done.first { it.containsAll(paths) } }
            repeat(2) { withFrameNanos { } }
            val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
            viewModel.setBusy(false)
            if (bitmap != null) then(bitmap) else viewModel.failed()
        }
    }
    val save = rememberSaveToPhotos { capture { viewModel.save(it, fileName, ExportKind.Map) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = StampTheme.colors.paper,
        contentColor = StampTheme.colors.textHigh,
    ) {
        // The full-size picture, laid out and recorded off screen (never drawn here).
        OffscreenCapture(layer, ExportWidthPx, ExportHeightPx, DesignWidth) {
            CompositionLocalProvider(LocalImageLoads provides loads) {
                MapExportCard(state, look, watermark = export.watermark)
            }
        }
        MapExportSheetContent(
            state = state,
            look = look,
            export = export,
            onLook = { look = it },
            onSave = save,
            onShare = { capture { viewModel.share(it, fileName, ExportKind.Map) } },
            onGetPro = onGetPro,
            onWatchAd = { activity?.let(viewModel::watchAd) },
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}

@Composable
fun MapExportSheetContent(
    state: MapUiState,
    look: MapLook,
    export: PageExportUiState,
    onLook: (MapLook) -> Unit,
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
        Text(stringResource(R.string.map_export_title), style = StampTheme.type.cardTitle)
        Row(horizontalArrangement = Arrangement.spacedBy(s.xs)) {
            PaperChip(stringResource(R.string.map_export_world), look == MapLook.World, { onLook(MapLook.World) })
            PaperChip(stringResource(R.string.map_export_passport), look == MapLook.Passport, { onLook(MapLook.Passport) })
        }
        MapPreview(state, look, export.watermark)
        when {
            export.isPro -> HandNote(stringResource(R.string.export_pro_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            export.adUnlocked -> HandNote(stringResource(R.string.map_export_unlocked_note), style = StampTheme.type.noteSmall, strength = InkStrength.Soft)
            else -> WatermarkRow(
                export.ad,
                onGetPro,
                onWatchAd,
                note = stringResource(R.string.map_export_watermark_note, stringResource(R.string.app_name)),
            )
        }
        if (export.failed) Text(stringResource(R.string.export_failed), style = StampTheme.type.label, color = c.airmailRed)
        SaveShareRow(export.save, export.sharing, onSave, onShare, Modifier.padding(top = s.xs))
    }
}

/** The picture at preview size: the same card, laid out at the export's design width and scaled down. */
@Composable
private fun MapPreview(state: MapUiState, look: MapLook, watermark: Boolean) {
    val description = stringResource(R.string.export_preview_cd)
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(PreviewHeight).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        val w = min(maxWidth, maxHeight * ExportAspect)
        val shape = StampTheme.shapes.plate
        ScaledArt(
            w,
            ExportAspect,
            DesignWidth,
            Modifier
                .tintedShadow(Elevation.Card, shape, StampTheme.colors.shadowWarm)
                .clip(shape)
                .clearAndSetSemantics { },
        ) { MapExportCard(state, look, watermark) }
    }
}

/** The exported picture in the chosen look. */
@Composable
fun MapExportCard(state: MapUiState, look: MapLook, watermark: Boolean, modifier: Modifier = Modifier) {
    when (look) {
        MapLook.World -> WorldPostcard(state, watermark, modifier)
        MapLook.Passport -> PassportPage(state, watermark, modifier)
    }
}

/** Look A: paper, airmail stripe, "Stamped in N countries", the kraft world, the numbers, the countries. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorldPostcard(state: MapUiState, watermark: Boolean, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val countries = state.countries.size
    GrainedSurface(c.paper, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AirmailStripe(Modifier.fillMaxWidth().height(StripeHeight))
            Column(Modifier.fillMaxSize().padding(s.m), verticalArrangement = Arrangement.spacedBy(s.s)) {
                Text(stringResource(R.string.map_export_overline, yearsText(state)).uppercase(locale()), style = StampTheme.type.overline, color = c.postmarkOnLight)
                FitText(
                    pluralStringResource(R.plurals.map_export_headline, countries, countries),
                    StampTheme.type.screenTitle,
                    color = c.textHigh,
                    maxLines = 2,
                )
                WorldCard(state, onOpenCountry = {})
                Stats(state, Modifier.padding(vertical = s.xs))
                // The countries in handwriting, as many as fit; the rest are counted.
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    val shown = state.countries.take(ListedCountries)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(s.s, Alignment.CenterHorizontally),
                    ) {
                        shown.forEach { country ->
                            HandNote(
                                stringResource(R.string.map_export_country, countryName(country.code, country.postmarkName), country.stampCount),
                                rotation = Tilt.note(country.code.hashCode().toLong()),
                                style = StampTheme.type.noteSmall,
                                maxLines = 1,
                            )
                        }
                        val more = countries - shown.size
                        if (more > 0) {
                            HandNote(stringResource(R.string.map_export_more, more), style = StampTheme.type.noteSmall, strength = InkStrength.Soft, maxLines = 1)
                        }
                    }
                }
                MadeWith(watermark, c.textMedium)
            }
        }
    }
}

/** Three big numbers: countries, places, stamps. */
@Composable
private fun Stats(state: MapUiState, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val t = StampTheme.type
    val countries = state.countries.size
    val places = state.places.size
    val stamps = state.stampCount
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(
            countries to pluralStringResource(R.plurals.map_export_stat_countries, countries),
            places to pluralStringResource(R.plurals.map_export_stat_places, places),
            stamps to pluralStringResource(R.plurals.map_export_stat_stamps, stamps),
        ).forEach { (n, label) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$n", style = t.screenTitle, color = c.textHigh)
                Text(label.uppercase(locale()), style = t.overline, color = c.textMedium)
            }
        }
    }
}

/** Look B: a kraft passport page, one visa per country (as many as fit), the rest counted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PassportPage(state: MapUiState, watermark: Boolean, modifier: Modifier = Modifier) {
    val c = StampTheme.colors
    val s = StampTheme.spacing
    val countries = state.countries.size
    val places = state.places.size
    val stamps = state.stampCount
    GrainedSurface(c.kraft, modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(s.m), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.map_export_passport_overline).uppercase(locale()), style = StampTheme.type.overline, color = c.textMedium)
            FitText(pluralStringResource(R.plurals.map_country_count, countries, countries), StampTheme.type.screenTitle, color = c.textHigh, textAlign = TextAlign.Center)
            HandNote(
                stringResource(
                    R.string.map_export_counts,
                    pluralStringResource(R.plurals.map_place_count, places, places),
                    pluralStringResource(R.plurals.album_stamp_count, stamps, stamps),
                ),
                rotation = Tilt.note(PassportSeed),
                maxLines = 1,
            )
            // The visas sit in the middle of the page; a lone first visa is stamped big.
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val shown = state.countries.take(PassportVisas)
                val big = shown.size == 1
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    // Rows overlap a little, like visas stamped in a hurry (and so a "+N more" fits).
                    verticalArrangement = Arrangement.spacedBy(-VisaOverlap),
                    maxItemsInEachRow = VisasPerRow,
                ) {
                    shown.forEach { country -> PassportVisa(country, if (big) LoneVisaScale else VisaScale) }
                }
                val more = countries - shown.size
                if (more > 0) {
                    HandNote(stringResource(R.string.map_export_more, more), style = StampTheme.type.noteSmall, strength = InkStrength.Soft, rotation = Tilt.note(more.toLong()))
                }
            }
            MadeWith(watermark, c.textMedium)
        }
    }
}

/** One country's visa (the same one its country page shows) at [scale], in a cell that fits it. */
@Composable
private fun PassportVisa(country: MapCountry, scale: Float) {
    val name = countryName(country.code, country.postmarkName)
    val since = rememberDateText(R.string.pattern_passport_since, country.firstDay)
    val stamps = pluralStringResource(R.plurals.album_stamp_count, country.stampCount, country.stampCount)
    val places = pluralStringResource(R.plurals.map_place_count, country.places.size, country.places.size)
    Box(Modifier.size(VisaCellWidth * scale / VisaScale, VisaCellHeight * scale / VisaScale), contentAlignment = Alignment.Center) {
        VisaStamp(
            code = country.code,
            name = name,
            lines = listOf(stringResource(R.string.visa_first, since), "$stamps · $places"),
            description = stringResource(R.string.visa_cd, name, since, stamps, places),
            modifier = Modifier.wrapContentSize(unbounded = true).scale(scale),
            tilt = Tilt.postmark(country.code.hashCode().toLong()) / 2,
        )
    }
}

/** The free tier's mark at the foot of the picture (the space stays so both tiers lay out alike). */
@Composable
private fun MadeWith(watermark: Boolean, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxWidth().height(StampTheme.spacing.l), contentAlignment = Alignment.BottomCenter) {
        if (watermark) {
            Text(
                stringResource(R.string.postcard_made_with, stringResource(R.string.app_name)),
                style = StampTheme.type.label,
                color = color,
            )
        }
    }
}

@Composable
private fun locale() = LocalConfiguration.current.locales[0]

/** "2026", or "2024–2026" when the first stamp on the map is from an earlier year. */
private fun yearsText(state: MapUiState): String {
    val now = LocalDate.now().year
    val first = state.countries.minOfOrNull { it.firstDay.year } ?: now
    return if (first < now) "$first–$now" else "$now"
}

/** The pictures the World postcard must wait for: the newest stamp of each pinned country. */
private val MapUiState.pinImagePaths: Set<String>
    get() = countries.take(MaxPins).mapTo(HashSet()) { it.newest.imagePath }

/** Phone-independent layout width of the picture; ExportWidthPx / this = its density. */
private val DesignWidth = 400.dp
private const val ExportWidthPx = 1440
private const val ExportHeightPx = 1920
private const val ExportAspect = ExportWidthPx.toFloat() / ExportHeightPx
private val PreviewHeight = 360.dp
private const val ImageWaitMs = 5_000L
private const val WorldFile = "my-world.png"
private const val PassportFile = "my-passport.png"
private val StripeHeight = 8.dp
/** Handwritten countries on the World postcard; two or three lines at the design width. */
private const val ListedCountries = 8
private const val PassportSeed = 45L
/** Three rows of two visas fill the passport page at the design width. */
private const val PassportVisas = 6
private const val VisasPerRow = 2
private val VisaCellWidth = 170.dp
private val VisaCellHeight = 124.dp
private const val VisaScale = 0.82f
private const val LoneVisaScale = 1.6f
private val VisaOverlap = 14.dp

/* ---------------------------------------------------------------- previews */

@PaperPreview
@Composable
private fun MapExportSheetPreview() {
    StampAppTheme {
        MapExportSheetContent(
            state = MapUiState(loaded = true, countries = previewMapCountries()),
            look = MapLook.World,
            export = PageExportUiState(),
            onLook = {}, onSave = {}, onShare = {}, onGetPro = {}, onWatchAd = {},
        )
    }
}

@PaperPreview
@Composable
private fun PassportPagePreview() {
    StampAppTheme {
        ScaledArt(320.dp, ExportAspect, DesignWidth) {
            MapExportCard(MapUiState(loaded = true, countries = previewMapCountries()), MapLook.Passport, watermark = true)
        }
    }
}
